package com.sportflow.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.sportflow.app.data.model.*
import kotlin.math.pow
import kotlin.math.ceil
import kotlin.math.log2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportFlowRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    // ── Collection Names (GNITS namespace) ───────────────────────────────────
    companion object {
        const val MATCHES_COLLECTION = "gnits_matches"
        const val TOURNAMENTS_COLLECTION = "gnits_tournaments"
        const val USERS_COLLECTION = "gnits_users"
        const val PAYMENTS_COLLECTION = "gnits_payments"
    }

    // ── Matches — Real-time Listeners ────────────────────────────────────────

    fun getScheduledMatches(): Flow<List<Match>> = callbackFlow {
        val listener = firestore.collection(MATCHES_COLLECTION)
            .whereEqualTo("status", MatchStatus.SCHEDULED.name)
            .orderBy("scheduledTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val matches = snapshot?.documents?.mapNotNull {
                    it.toObject(Match::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(matches)
            }
        awaitClose { listener.remove() }
    }

    fun getLiveMatches(): Flow<List<Match>> = callbackFlow {
        val listener = firestore.collection(MATCHES_COLLECTION)
            .whereIn("status", listOf(MatchStatus.LIVE.name, MatchStatus.HALFTIME.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val matches = snapshot?.documents?.mapNotNull {
                    it.toObject(Match::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(matches)
            }
        awaitClose { listener.remove() }
    }

    fun getMatchById(matchId: String): Flow<Match?> = callbackFlow {
        val listener = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val match = snapshot?.toObject(Match::class.java)?.copy(id = snapshot.id)
                trySend(match)
            }
        awaitClose { listener.remove() }
    }

    fun getAllMatches(): Flow<List<Match>> = callbackFlow {
        val listener = firestore.collection(MATCHES_COLLECTION)
            .orderBy("scheduledTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val matches = snapshot?.documents?.mapNotNull {
                    it.toObject(Match::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(matches)
            }
        awaitClose { listener.remove() }
    }

    // ── Match CRUD (Admin) ──────────────────────────────────────────────────

    /** Create a new match document in the gnits_matches collection */
    suspend fun createMatch(match: Match): String {
        val data = hashMapOf(
            "sportType" to match.sportType,
            "teamA" to match.teamA,
            "teamB" to match.teamB,
            "teamADepartment" to match.teamADepartment,
            "teamBDepartment" to match.teamBDepartment,
            "scoreA" to 0,
            "scoreB" to 0,
            "status" to MatchStatus.SCHEDULED.name,
            "venue" to match.venue,
            "scheduledTime" to match.scheduledTime,
            "currentPeriod" to "",
            "elapsedTime" to "",
            "tournamentId" to match.tournamentId,
            "tournamentName" to match.tournamentName,
            "round" to match.round,
            "highlights" to emptyList<String>(),
            "winnerId" to "",
            "createdBy" to (auth.currentUser?.uid ?: "")
        )
        val docRef = firestore.collection(MATCHES_COLLECTION).add(data).await()
        return docRef.id
    }

    // ── Live Scoring Engine — Atomic Updates ────────────────────────────────

    /** Atomically increment a team's score — also publishes to FCM score topic */
    suspend fun incrementScore(matchId: String, team: String) {
        val field = if (team == "A") "scoreA" else "scoreB"
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(field, FieldValue.increment(1))
            .await()

        // Read updated doc to build a meaningful notification
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId).get().await()
        val teamAName = doc.getString("teamA") ?: "Team A"
        val teamBName = doc.getString("teamB") ?: "Team B"
        val scoreA = doc.getLong("scoreA")?.toInt() ?: 0
        val scoreB = doc.getLong("scoreB")?.toInt() ?: 0
        val scoringTeam = if (team == "A") teamAName else teamBName

        // Write a notification trigger document — Firebase Extensions or Cloud Functions
        // can pick this up and fan-out to FCM topic. For direct topic messaging,
        // we store in gnits_notifications so Cloud Functions can send it.
        pushNotificationTrigger(
            type = "score_update",
            title = "⚽ Goal! $scoringTeam scores!",
            body = "$teamAName $scoreA – $scoreB $teamBName",
            matchId = matchId,
            topic = FCM_TOPIC_SCORES
        )
    }

    /** Atomically decrement a team's score (min 0) */
    suspend fun decrementScore(matchId: String, team: String) {
        // Read current, then set — Firestore doesn't have atomic decrement-with-floor
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .get()
            .await()
        val field = if (team == "A") "scoreA" else "scoreB"
        val current = doc.getLong(field)?.toInt() ?: 0
        if (current > 0) {
            firestore.collection(MATCHES_COLLECTION)
                .document(matchId)
                .update(field, current - 1)
                .await()
        }
    }

    /** Add a highlight entry to match timeline */
    suspend fun addHighlight(matchId: String, highlight: String) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update("highlights", FieldValue.arrayUnion(highlight))
            .await()
    }

    // ── Match Lifecycle — State Machine ─────────────────────────────────────

    /** Scheduled → Live */
    suspend fun startMatch(matchId: String, period: String = "1st Half") {
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId).get().await()
        val teamA = doc.getString("teamA") ?: "Team A"
        val teamB = doc.getString("teamB") ?: "Team B"
        val sport = doc.getString("sportType") ?: "Match"
        val venue = doc.getString("venue") ?: "GNITS"

        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(
                mapOf(
                    "status" to MatchStatus.LIVE.name,
                    "currentPeriod" to period,
                    "elapsedTime" to "00:00"
                )
            )
            .await()

        pushNotificationTrigger(
            type = "match_start",
            title = "🏁 $sport is LIVE now!",
            body = "$teamA vs $teamB has kicked off at $venue. Follow live scores!",
            matchId = matchId,
            topic = FCM_TOPIC_MATCH_START
        )
    }

    /** Live → Halftime */
    suspend fun halfTimeMatch(matchId: String) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(
                mapOf(
                    "status" to MatchStatus.HALFTIME.name,
                    "currentPeriod" to "Half Time"
                )
            )
            .await()
    }

    /** Halftime → Live (2nd half) */
    suspend fun resumeMatch(matchId: String, period: String = "2nd Half") {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(
                mapOf(
                    "status" to MatchStatus.LIVE.name,
                    "currentPeriod" to period,
                    "elapsedTime" to "00:00"
                )
            )
            .await()
    }

    /** Live/Halftime → Completed, set winner + send result notification */
    suspend fun completeMatch(matchId: String) {
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .get()
            .await()

        val match = doc.toObject(Match::class.java) ?: return
        val winnerId = when {
            match.scoreA > match.scoreB -> match.teamA
            match.scoreB > match.scoreA -> match.teamB
            else -> "DRAW"
        }

        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(
                mapOf(
                    "status" to MatchStatus.COMPLETED.name,
                    "currentPeriod" to "Full Time",
                    "winnerId" to winnerId
                )
            )
            .await()

        // Send result notification
        val resultText = when (winnerId) {
            "DRAW" -> "${match.teamA} ${match.scoreA} – ${match.scoreB} ${match.teamB} · It's a Draw!"
            else   -> "🏆 $winnerId wins! ${match.teamA} ${match.scoreA} – ${match.scoreB} ${match.teamB}"
        }
        pushNotificationTrigger(
            type = "match_end",
            title = "Full Time! ${match.sportType} result",
            body = resultText,
            matchId = matchId,
            topic = FCM_TOPIC_TOURNAMENT
        )

        // Advance winner in bracket if this match is in a tournament
        if (match.tournamentId.isNotBlank() && winnerId != "DRAW") {
            advanceWinnerInBracket(match.tournamentId, matchId, winnerId)
        }
    }

    /** Cancel a match */
    suspend fun cancelMatch(matchId: String) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update("status", MatchStatus.CANCELLED.name)
            .await()
    }

    /** Update elapsed time */
    suspend fun updateElapsedTime(matchId: String, time: String) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update("elapsedTime", time)
            .await()
    }

    // ── Tournaments ──────────────────────────────────────────────────────────

    fun getTournaments(): Flow<List<Tournament>> = callbackFlow {
        val listener = firestore.collection(TOURNAMENTS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val tournaments = snapshot?.documents?.mapNotNull {
                    it.toObject(Tournament::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(tournaments)
            }
        awaitClose { listener.remove() }
    }

    fun getBracket(tournamentId: String): Flow<List<BracketNode>> = callbackFlow {
        val listener = firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .collection("bracket")
            .orderBy("round")
            .orderBy("position")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val nodes = snapshot?.documents?.mapNotNull {
                    it.toObject(BracketNode::class.java)?.copy(matchId = it.id)
                } ?: emptyList()
                trySend(nodes)
            }
        awaitClose { listener.remove() }
    }

    // ── Bracket Automation ──────────────────────────────────────────────────

    /** When a match completes, advance the winner to the next bracket node */
    private suspend fun advanceWinnerInBracket(
        tournamentId: String,
        matchId: String,
        winnerName: String
    ) {
        val bracketRef = firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .collection("bracket")

        // Find the node for this match
        val nodes = bracketRef.get().await()
        val currentNode = nodes.documents.find { doc ->
            val node = doc.toObject(BracketNode::class.java)
            node?.matchId == matchId
        } ?: return

        val node = currentNode.toObject(BracketNode::class.java) ?: return

        // Update winner on current node
        currentNode.reference.update("winner", winnerName).await()

        // Find the next round node
        val nextRound = node.round + 1
        val nextPosition = node.position / 2
        val isTopSlot = node.position % 2 == 0

        val nextNodes = bracketRef
            .whereEqualTo("round", nextRound)
            .whereEqualTo("position", nextPosition)
            .get()
            .await()

        if (nextNodes.documents.isNotEmpty()) {
            val nextDoc = nextNodes.documents[0]
            val field = if (isTopSlot) "teamA" else "teamB"
            nextDoc.reference.update(field, winnerName).await()
        }
    }

    // ── Bracket Generation ───────────────────────────────────────────────────

    suspend fun generateBracket(tournamentId: String) {
        val tournament = firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .get()
            .await()
            .toObject(Tournament::class.java) ?: return

        val teams = tournament.teams.shuffled()
        if (teams.isEmpty()) return
        val totalRounds = ceil(log2(teams.size.toDouble())).toInt()
        val bracketRef = firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .collection("bracket")

        // Clear old bracket
        val oldDocs = bracketRef.get().await()
        for (doc in oldDocs) {
            doc.reference.delete().await()
        }

        // Generate first round
        var position = 0
        for (i in teams.indices step 2) {
            val node = BracketNode(
                round = 1,
                position = position,
                teamA = teams.getOrNull(i),
                teamB = teams.getOrNull(i + 1)
            )
            bracketRef.add(node).await()
            position++
        }

        // Generate empty subsequent rounds
        for (round in 2..totalRounds) {
            val matchesInRound = ceil(teams.size / 2.0.pow(round.toDouble())).toInt()
            for (pos in 0 until matchesInRound) {
                val node = BracketNode(round = round, position = pos)
                bracketRef.add(node).await()
            }
        }

        // Mark bracket as generated
        firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .update("bracketGenerated", true)
            .await()
    }

    // ── Payments (Admin) ─────────────────────────────────────────────────────

    fun getPendingPayments(): Flow<List<Payment>> = callbackFlow {
        val listener = firestore.collection(PAYMENTS_COLLECTION)
            .whereEqualTo("status", PaymentStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val payments = snapshot?.documents?.mapNotNull {
                    it.toObject(Payment::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(payments)
            }
        awaitClose { listener.remove() }
    }

    suspend fun verifyPayment(paymentId: String) {
        firestore.collection(PAYMENTS_COLLECTION)
            .document(paymentId)
            .update("status", PaymentStatus.VERIFIED.name)
            .await()
    }

    suspend fun rejectPayment(paymentId: String) {
        firestore.collection(PAYMENTS_COLLECTION)
            .document(paymentId)
            .update("status", PaymentStatus.REJECTED.name)
            .await()
    }

    // ── Auth & User Profile ─────────────────────────────────────────────────

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /** Get the user profile document with role */
    fun getUserProfile(uid: String): Flow<SportUser?> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(SportUser::class.java)
                trySend(user)
            }
        awaitClose { listener.remove() }
    }

    /** Post-login role check */
    fun getUserRole(uid: String): Flow<UserRole?> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val roleStr = snapshot?.getString("role")
                val role = try {
                    roleStr?.let { UserRole.valueOf(it) }
                } catch (_: Exception) {
                    UserRole.PLAYER
                }
                trySend(role)
            }
        awaitClose { listener.remove() }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    /** Signup with GNITS student identity — collects roll number and department */
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        rollNumber: String,
        department: String
    ) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { user ->
            val sportUser = SportUser(
                uid = user.uid,
                email = email,
                displayName = name,
                role = UserRole.PLAYER,
                rollNumber = rollNumber,
                department = department
            )
            firestore.collection(USERS_COLLECTION).document(user.uid).set(sportUser).await()

            // Auto-subscribe to department topic for push notifications
            if (department.isNotBlank()) {
                subscribeToTopic("dept_$department")
            }
            // Subscribe to general sports topic
            subscribeToTopic("gnits_sports_all")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // ── FCM Topic Subscriptions ─────────────────────────────────────────────

    companion object {
        // FCM topics — must match what Cloud Functions publishes to
        const val FCM_TOPIC_ALL          = "gnits_sports_all"
        const val FCM_TOPIC_SCORES       = "gnits_score_updates"
        const val FCM_TOPIC_MATCH_START  = "gnits_match_start"
        const val FCM_TOPIC_TOURNAMENT   = "gnits_tournaments"
        const val FCM_TOPIC_PAYMENT      = "gnits_payment"
    }

    fun subscribeToTopic(topic: String) {
        messaging.subscribeToTopic(topic)
    }

    fun unsubscribeFromTopic(topic: String) {
        messaging.unsubscribeFromTopic(topic)
    }

    /** Subscribe to all default topics for a new user */
    fun subscribeToDefaultTopics(department: String) {
        subscribeToTopic(FCM_TOPIC_ALL)
        subscribeToTopic(FCM_TOPIC_MATCH_START)
        subscribeToTopic(FCM_TOPIC_SCORES)
        subscribeToTopic(FCM_TOPIC_TOURNAMENT)
        if (department.isNotBlank()) subscribeToTopic("dept_$department")
    }

    /** Called from Notifications settings — subscribe or unsubscribe per category */
    fun setNotificationCategory(category: String, enabled: Boolean) {
        if (enabled) subscribeToTopic(category) else unsubscribeFromTopic(category)
    }

    /**
     * Write a notification trigger to Firestore.
     * Firebase Cloud Functions (HTTP trigger) reads this and sends to FCM topic.
     * This is the standard pattern when client-side FCM send API is unavailable.
     */
    private suspend fun pushNotificationTrigger(
        type: String,
        title: String,
        body: String,
        matchId: String,
        topic: String
    ) {
        try {
            firestore.collection("gnits_notification_triggers").add(
                mapOf(
                    "type"      to type,
                    "title"     to title,
                    "body"      to body,
                    "matchId"   to matchId,
                    "topic"     to topic,
                    "sentAt"    to com.google.firebase.Timestamp.now(),
                    "processed" to false
                )
            ).await()
        } catch (_: Exception) {
            // Non-critical — notification failure should not crash the scoring flow
        }
    }
}
