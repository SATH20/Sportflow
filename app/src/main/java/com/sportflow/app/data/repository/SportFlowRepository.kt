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
    // ── Collection Names & FCM Topics (GNITS namespace) ─────────────────────
    companion object {
        // Firestore collections
        const val MATCHES_COLLECTION     = "gnits_matches"
        const val TOURNAMENTS_COLLECTION = "gnits_tournaments"
        const val USERS_COLLECTION       = "gnits_users"
        const val PAYMENTS_COLLECTION    = "gnits_payments"
        const val REGISTRATIONS_COLLECTION = "registrations" // sub-collection under gnits_matches
        const val NOTIFICATION_TRIGGERS  = "gnits_notification_triggers"

        // FCM topics — must match what Cloud Functions publishes to
        const val FCM_TOPIC_ALL         = "gnits_sports_all"
        const val FCM_TOPIC_SCORES      = "gnits_score_updates"
        const val FCM_TOPIC_MATCH_START = "gnits_match_start"
        const val FCM_TOPIC_TOURNAMENT  = "gnits_tournaments"
        const val FCM_TOPIC_PAYMENT     = "gnits_payment"
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

    // ── Live Scoring Engine — Sport-Aware Atomic Updates ────────────────────

    /**
     * Universal scoring entry point. Admin calls this for every button tap.
     * The [action] carries the Firestore field name and delta from [SportScoreEngine].
     */
    suspend fun applyScoringAction(matchId: String, action: com.sportflow.app.data.model.ScoringAction) {
        if (action.delta == 0) return // "special" actions like Dot Ball, Foul (just highlights)

        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(action.field, FieldValue.increment(action.delta.toLong()))
            .await()

        // Also keep scoreA/scoreB in sync for set-based sports
        // (they represent sets won, updated separately via newSetWon())

        // Read back for notification
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val teamA = doc.getString("teamA") ?: "Team A"
        val teamB = doc.getString("teamB") ?: "Team B"
        val sport = doc.getString("sportType") ?: ""
        val sa = doc.getLong("scoreA")?.toInt() ?: 0
        val sb = doc.getLong("scoreB")?.toInt() ?: 0
        val wA = doc.getLong("wicketsA")?.toInt() ?: 0
        val wB = doc.getLong("wicketsB")?.toInt() ?: 0
        val curSetA = doc.getLong("currentSetScoreA")?.toInt() ?: 0
        val curSetB = doc.getLong("currentSetScoreB")?.toInt() ?: 0

        val scoreText = when (com.sportflow.app.data.model.SportType.fromString(sport)) {
            com.sportflow.app.data.model.SportType.CRICKET ->
                "${action.label} by ${if(action.team=="A") teamA else teamB} | $teamA $sa/$wA vs $teamB $sb/$wB"
            com.sportflow.app.data.model.SportType.BADMINTON,
            com.sportflow.app.data.model.SportType.VOLLEYBALL,
            com.sportflow.app.data.model.SportType.TABLE_TENNIS ->
                "${action.emoji} ${action.label} by ${if(action.team=="A") teamA else teamB} | Set: $curSetA-$curSetB"
            else -> "${action.emoji} ${action.label} by ${if(action.team=="A") teamA else teamB} | $teamA $sa – $sb $teamB"
        }

        pushNotificationTrigger(
            type = "score_update",
            title = "🔴 ${action.emoji} $sport Update",
            body = scoreText,
            matchId = matchId,
            topic = FCM_TOPIC_SCORES
        )
    }

    /** For set-based sports: complete current set and start next one */
    suspend fun completeSet(matchId: String, winnerTeam: String) {
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val curSetA = doc.getLong("currentSetScoreA")?.toInt() ?: 0
        val curSetB = doc.getLong("currentSetScoreB")?.toInt() ?: 0
        val currentSet = doc.getLong("currentSet")?.toInt() ?: 1
        val existing = doc.getString("completedSets") ?: ""
        val newCompleted = if (existing.isBlank()) "$curSetA-$curSetB" else "$existing,$curSetA-$curSetB"
        val setsField = if (winnerTeam == "A") "setsWonA" else "setsWonB"

        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(mapOf(
                "completedSets" to newCompleted,
                setsField to FieldValue.increment(1),
                "currentSetScoreA" to 0,
                "currentSetScoreB" to 0,
                "currentSet" to (currentSet + 1)
            )).await()
    }

    /** For cricket: advance overs after 6 legal deliveries */
    suspend fun advanceOver(matchId: String, inning: Int) {
        val field = if (inning == 1) "oversA" else "oversB"
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val current = doc.getString(field) ?: "0.0"
        val parts = current.split(".")
        val overs = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val balls = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val newOvers = if (balls >= 5) "${overs + 1}.0" else "$overs.${balls + 1}"
        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(field, newOvers).await()
    }

    /** For cricket: start 2nd inning — set target */
    suspend fun startSecondInning(matchId: String) {
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val firstInningRuns = doc.getLong("scoreA")?.toInt() ?: 0
        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(mapOf(
                "currentInning" to 2,
                "targetRuns" to (firstInningRuns + 1),
                "currentPeriod" to "2nd Innings"
            )).await()
    }

    /** For basketball: advance to next quarter */
    suspend fun nextQuarter(matchId: String) {
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val q = (doc.getLong("currentQuarter")?.toInt() ?: 1) + 1
        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(mapOf(
                "currentQuarter" to q,
                "currentPeriod" to "Q$q"
            )).await()
    }

    /** Legacy increment — still used for football/kabaddi direct scoreA/B changes */
    suspend fun incrementScore(matchId: String, team: String) {
        val field = if (team == "A") "scoreA" else "scoreB"
        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(field, FieldValue.increment(1)).await()
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
            firestore.collection(NOTIFICATION_TRIGGERS).add(
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

    // ── Registration : transactional write ────────────────────────────────────────

    /**
     * Atomically registers the current user for a match.
     * - Writes a Registration doc to gnits_matches/{matchId}/registrations
     * - Increments the denormalized registrationCount on the match doc
     * - Triggers a push notification to the user's department topic
     * @throws IllegalStateException if user is not authenticated
     * @throws IllegalArgumentException if the event is full or already registered
     */
    suspend fun registerForMatch(match: com.sportflow.app.data.model.Match) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val matchRef  = firestore.collection(MATCHES_COLLECTION).document(match.id)
        val regRef    = matchRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)
        val userRef   = firestore.collection(USERS_COLLECTION).document(user.uid)

        firestore.runTransaction { tx ->
            val matchSnap = tx.get(matchRef)
            val regSnap   = tx.get(regRef)
            val userSnap  = tx.get(userRef)

            // Guard: already registered
            if (regSnap.exists()) {
                throw IllegalArgumentException("Already registered for this event")
            }

            // Guard: event full (maxRegistrations == 0 means unlimited)
            val maxReg  = matchSnap.getLong("maxRegistrations")?.toInt() ?: 0
            val curCount = matchSnap.getLong("registrationCount")?.toInt() ?: 0
            if (maxReg > 0 && curCount >= maxReg) {
                throw IllegalArgumentException("Event is full (${maxReg} max registrations)")
            }

            val userName    = userSnap.getString("displayName") ?: user.displayName ?: ""
            val department  = userSnap.getString("department") ?: ""
            val rollNumber  = userSnap.getString("rollNumber") ?: ""

            val registration = hashMapOf(
                "id"           to user.uid,
                "uid"          to user.uid,
                "matchId"      to match.id,
                "tournamentId" to match.tournamentId,
                "userName"     to userName,
                "department"   to department,
                "rollNumber"   to rollNumber,
                "status"       to com.sportflow.app.data.model.RegistrationStatus.CONFIRMED.name,
                "registeredAt" to com.google.firebase.Timestamp.now()
            )

            tx.set(regRef, registration)
            tx.update(matchRef, "registrationCount",
                com.google.firebase.firestore.FieldValue.increment(1))
        }.await()

        // Non-critical: push notification trigger
        val dept = try {
            firestore.collection(USERS_COLLECTION).document(user.uid)
                .get().await().getString("department") ?: ""
        } catch (_: Exception) { "" }

        pushNotificationTrigger(
            type    = "registration_success",
            title   = "🎫 Registration Confirmed!",
            body    = "You are registered for ${match.teamA} vs ${match.teamB} at ${match.venue}",
            matchId = match.id,
            topic   = if (dept.isNotBlank()) "dept_$dept" else FCM_TOPIC_ALL
        )
    }

    /**
     * Cancel an existing registration.
     * - Deletes the registration doc
     * - Decrements the registrationCount on the match doc (floor 0)
     */
    suspend fun cancelRegistration(matchId: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val matchRef = firestore.collection(MATCHES_COLLECTION).document(matchId)
        val regRef   = matchRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)

        firestore.runTransaction { tx ->
            val matchSnap = tx.get(matchRef)
            val curCount  = matchSnap.getLong("registrationCount")?.toInt() ?: 0

            tx.delete(regRef)
            if (curCount > 0) {
                tx.update(matchRef, "registrationCount",
                    com.google.firebase.firestore.FieldValue.increment(-1))
            }
        }.await()
    }

    /**
     * Check if the current user is registered for a specific match.
     * Returns null if user is not authenticated.
     */
    suspend fun getRegistrationStatus(matchId: String): com.sportflow.app.data.model.Registration? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(REGISTRATIONS_COLLECTION)
            .document(uid)
            .get()
            .await()
        return if (snap.exists()) snap.toObject(com.sportflow.app.data.model.Registration::class.java) else null
    }

    /**
     * Real-time flow of all matches the current user has registered for.
     * Queries the root gnits_matches collection for docs where the current user
     * has a registration sub-document.
     *
     * Strategy: listen to the user's registration collection-group.
     */
    fun getMyRegisteredMatches(): Flow<List<com.sportflow.app.data.model.Match>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Use collectionGroup to query all "registrations" docs with this uid
        val listener = firestore.collectionGroup(REGISTRATIONS_COLLECTION)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val matchIds = snapshot?.documents?.mapNotNull { it.getString("matchId") }
                    ?: emptyList()

                if (matchIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                // Batch-fetch match docs (Firestore `whereIn` max 30 items)
                firestore.collection(MATCHES_COLLECTION)
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(),
                        matchIds.take(30))
                    .get()
                    .addOnSuccessListener { matchSnap ->
                        val matches = matchSnap.documents.mapNotNull {
                            it.toObject(com.sportflow.app.data.model.Match::class.java)?.copy(id = it.id)
                        }.sortedByDescending { it.scheduledTime }
                        trySend(matches)
                    }
                    .addOnFailureListener { /* best-effort */ }
            }
        awaitClose { listener.remove() }
    }
}
