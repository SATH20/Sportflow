package com.sportflow.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldPath
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
        const val SCORECARDS_COLLECTION  = "scorecards"      // sub-collection under gnits_matches
        const val NOTIFICATION_TRIGGERS  = "gnits_notification_triggers"
        /** Top-level registrations collection for Admin Data Bridge */
        const val GNITS_REGISTRATIONS    = "gnits_registrations"
        /** Per-user notification sub-collection under gnits_users/{uid} */
        const val USER_NOTIFICATIONS     = "notifications"

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

    /** Live/Halftime → Completed, set winner + send result notification.
     *  For set-based sports (Badminton/Volleyball/TT) compares setsWonA vs setsWonB.
     *  For all other sports compares scoreA vs scoreB.
     */
    suspend fun completeMatch(matchId: String) {
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .get()
            .await()

        val match = doc.toObject(Match::class.java) ?: return

        // Determine winner based on sport type
        val sport = com.sportflow.app.data.model.SportType.fromString(match.sportType)
        val winnerId = when (sport) {
            com.sportflow.app.data.model.SportType.BADMINTON,
            com.sportflow.app.data.model.SportType.VOLLEYBALL,
            com.sportflow.app.data.model.SportType.TABLE_TENNIS -> when {
                match.setsWonA > match.setsWonB -> match.teamA
                match.setsWonB > match.setsWonA -> match.teamB
                else -> "DRAW"
            }
            else -> when {
                match.scoreA > match.scoreB -> match.teamA
                match.scoreB > match.scoreA -> match.teamB
                else -> "DRAW"
            }
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

    /** Update user profile with registration data */
    suspend fun updateUserProfile(
        uid: String,
        rollNumber: String? = null,
        department: String? = null,
        yearOfStudy: String? = null,
        preferredSportRole: String? = null
    ) {
        val updates = mutableMapOf<String, Any>()
        rollNumber?.let { updates["rollNumber"] = it }
        department?.let { updates["department"] = it }
        yearOfStudy?.let { updates["yearOfStudy"] = it }
        preferredSportRole?.let { updates["preferredSportRole"] = it }
        
        if (updates.isNotEmpty()) {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()
        }
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
     * Guards:
     *  - Already registered check
     *  - Squad full check  (maxSquadSize, denormalized via currentSquadCount)
     *  - Department eligibility check (Match.allowedDepartments)
     *  - Academic year eligibility check (Match.allowedYears)
     * After a successful write:
     *  - Scenario B: if the new count == maxSquadSize, broadcasts "Squad Closed" FCM trigger
     * @throws IllegalStateException if user is not authenticated
     * @throws IllegalArgumentException on any eligibility or capacity violation
     */
    suspend fun registerForMatch(match: com.sportflow.app.data.model.Match) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val matchRef  = firestore.collection(MATCHES_COLLECTION).document(match.id)
        val regRef    = matchRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)
        val userRef   = firestore.collection(USERS_COLLECTION).document(user.uid)

        // Snapshot needed before transaction for eligibility data
        val userSnap = userRef.get().await()
        val userDept = userSnap.getString("department") ?: ""
        val userYear = userSnap.getString("yearOfStudy") ?: ""

        // ── Eligibility checks (outside transaction — read-only) ────────────
        if (match.allowedDepartments.isNotEmpty() &&
            userDept.isNotBlank() &&
            match.allowedDepartments.none { it.equals(userDept, ignoreCase = true) }
        ) {
            val allowed = match.allowedDepartments.joinToString(", ")
            throw IllegalArgumentException(
                "You are not eligible: only $allowed department(s) may register for this event"
            )
        }

        if (match.allowedYears.isNotEmpty() &&
            userYear.isNotBlank() &&
            match.allowedYears.none { it.equals(userYear, ignoreCase = true) }
        ) {
            val allowed = match.allowedYears
                .mapNotNull { com.sportflow.app.data.model.GnitsYear.fromCode(it)?.displayName }
                .joinToString(", ")
            throw IllegalArgumentException(
                "You are not eligible: only $allowed student(s) may register for this event"
            )
        }

        // ── Transactional write ─────────────────────────────────────────────
        var newSquadCount = 0
        firestore.runTransaction { tx ->
            val matchSnap = tx.get(matchRef)
            val regSnap   = tx.get(regRef)

            // Guard: already registered
            if (regSnap.exists()) {
                throw IllegalArgumentException("Already registered for this event")
            }

            // Guard: squad full — prefer maxSquadSize / currentSquadCount;
            //        fall back to legacy maxRegistrations / registrationCount
            val maxSquad  = matchSnap.getLong("maxSquadSize")?.toInt() ?: 0
            val curSquad  = matchSnap.getLong("currentSquadCount")?.toInt() ?: 0
            val maxReg    = matchSnap.getLong("maxRegistrations")?.toInt() ?: 0
            val curCount  = matchSnap.getLong("registrationCount")?.toInt() ?: 0

            val effectiveMax = if (maxSquad > 0) maxSquad else maxReg
            val effectiveCur = if (maxSquad > 0) curSquad else curCount

            if (effectiveMax > 0 && effectiveCur >= effectiveMax) {
                throw IllegalArgumentException("Squad is full ($effectiveMax/${effectiveMax} slots taken)")
            }

            val userName   = userSnap.getString("displayName") ?: user.displayName ?: ""
            val rollNumber = userSnap.getString("rollNumber") ?: ""

            val sportRole    = userSnap.getString("preferredSportRole") ?: ""
            val matchName    = "${matchSnap.getString("teamA") ?: ""} vs ${matchSnap.getString("teamB") ?: ""}"
            val matchSport   = matchSnap.getString("sportType") ?: ""

            val registration = hashMapOf(
                "id"           to user.uid,
                "uid"          to user.uid,
                "matchId"      to match.id,
                "tournamentId" to match.tournamentId,
                "userName"     to userName,
                "email"        to (user.email ?: ""),     // populated from FirebaseAuth
                "department"   to userDept,
                "yearOfStudy"  to userYear,              // persist for eligibility audit trail
                "rollNumber"   to rollNumber,
                "sportRole"    to sportRole,              // Sport-specific role e.g. Bowler, Defender
                "sportType"    to matchSport,             // Denormalized for admin quick-view
                "matchName"    to matchName,              // Denormalized for admin list display
                "status"       to com.sportflow.app.data.model.RegistrationStatus.CONFIRMED.name,
                "registeredAt" to com.google.firebase.Timestamp.now(),
                "seen"         to false  // Admin "New Entry" badge flag
            )

            tx.set(regRef, registration)
            // Increment both counters so dashboards and slot-checks stay in sync
            tx.update(matchRef, "currentSquadCount",
                com.google.firebase.firestore.FieldValue.increment(1))
            tx.update(matchRef, "registrationCount",
                com.google.firebase.firestore.FieldValue.increment(1))

            newSquadCount = effectiveCur + 1
        }.await()

        // ── Non-critical notifications ──────────────────────────────────
        val dept = userDept.ifBlank {
            try { firestore.collection(USERS_COLLECTION).document(user.uid)
                .get().await().getString("department") ?: "" } catch (_: Exception) { "" }
        }

        pushNotificationTrigger(
            type    = "registration_success",
            title   = "🎫 Registration Confirmed!",
            body    = "You are registered for ${match.teamA} vs ${match.teamB} at ${match.venue}",
            matchId = match.id,
            topic   = if (dept.isNotBlank()) "dept_$dept" else FCM_TOPIC_ALL
        )

        // ── Write to top-level gnits_registrations for Admin Data Bridge ──────
        try {
            val userSnap2 = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
            val sportRoleFinal = userSnap2.getString("preferredSportRole") ?: ""
            val bridgeDoc = hashMapOf(
                "uid"          to user.uid,
                "matchId"      to match.id,
                "tournamentId" to match.tournamentId,
                "userName"     to (userSnap2.getString("displayName") ?: user.displayName ?: ""),
                "email"        to (user.email ?: ""),
                "department"   to (userSnap2.getString("department") ?: ""),
                "yearOfStudy"  to (userSnap2.getString("yearOfStudy") ?: ""),
                "rollNumber"   to (userSnap2.getString("rollNumber") ?: ""),
                "sportRole"    to sportRoleFinal,
                "sportType"    to match.sportType,
                "matchName"    to "${match.teamA} vs ${match.teamB}",
                "status"       to com.sportflow.app.data.model.RegistrationStatus.CONFIRMED.name,
                "registeredAt" to com.google.firebase.Timestamp.now(),
                "seen"         to false  // Admin "New Entry" badge flag
            )
            firestore.collection(GNITS_REGISTRATIONS).document("${user.uid}_${match.id}").set(bridgeDoc).await()
        } catch (_: Exception) {
            // Non-critical — admin bridge failure should not block player registration
        }

        // Scenario B: squad just became full — broadcast closure alert
        val maxSquadFinal = match.maxSquadSize.takeIf { it > 0 } ?: match.maxRegistrations
        if (maxSquadFinal > 0 && newSquadCount >= maxSquadFinal) {
            val matchName = "${match.teamA} vs ${match.teamB}"
            pushNotificationTrigger(
                type    = "squad_closed",
                title   = "🚫 Squad Full — ${match.sportType}",
                body    = "Registrations for $matchName are now closed. The squad is full!",
                matchId = match.id,
                topic   = FCM_TOPIC_ALL
            )
        }
    }

    /**
     * Cancel an existing registration.
     * - Deletes the registration doc
     * - Decrements currentSquadCount (and registrationCount) on the match doc (floor 0)
     * - Scenario A: if the squad was previously full, broadcasts "spot opened" FCM trigger
     */
    suspend fun cancelRegistration(matchId: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val matchRef = firestore.collection(MATCHES_COLLECTION).document(matchId)
        val regRef   = matchRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)

        var previousCount  = 0
        var maxSquadSize   = 0
        var matchName      = ""
        var sportType      = ""

        // Read match metadata before transaction
        val matchSnap = matchRef.get().await()
        previousCount = (matchSnap.getLong("currentSquadCount")?.toInt()
            ?: matchSnap.getLong("registrationCount")?.toInt() ?: 0)
        maxSquadSize  = (matchSnap.getLong("maxSquadSize")?.toInt()
            ?: matchSnap.getLong("maxRegistrations")?.toInt() ?: 0)
        matchName     = "${matchSnap.getString("teamA") ?: ""} vs ${matchSnap.getString("teamB") ?: ""}"
        sportType     = matchSnap.getString("sportType") ?: ""
        val venue     = matchSnap.getString("venue") ?: "GNITS"

        firestore.runTransaction { tx ->
            val snap     = tx.get(matchRef)
            val regSnap  = tx.get(regRef)
            val curSquad = snap.getLong("currentSquadCount")?.toInt() ?: 0
            val curReg   = snap.getLong("registrationCount")?.toInt() ?: 0

            if (!regSnap.exists()) {
                throw IllegalArgumentException("No active registration found for this match")
            }

            tx.delete(regRef)
            if (curSquad > 0) {
                tx.update(matchRef, "currentSquadCount",
                    com.google.firebase.firestore.FieldValue.increment(-1))
            }
            if (curReg > 0) {
                tx.update(matchRef, "registrationCount",
                    com.google.firebase.firestore.FieldValue.increment(-1))
            }
        }.await()

        // Scenario A: a spot has just opened up in a previously-full squad
        if (maxSquadSize > 0 && previousCount >= maxSquadSize) {
            pushNotificationTrigger(
                type    = "spot_opened",
                title   = "\uD83D\uDFE2 Spot Available \u2014 $sportType",
                body    = "A spot has opened up in $matchName at $venue \u2014 register now!",
                matchId = matchId,
                topic   = FCM_TOPIC_ALL
            )
        }

        // Keep Admin Data Bridge in sync when a player cancels.
        try {
            firestore.collection(GNITS_REGISTRATIONS)
                .document("${user.uid}_${matchId}")
                .delete()
                .await()
        } catch (_: Exception) {
            // Non-critical — cancellation succeeded even if bridge cleanup failed.
        }
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
     * Real-time Flow<Boolean> — true if the current user has an active registration
     * document for [matchId], false otherwise.
     *
     * This is the SINGLE SOURCE OF TRUTH for Home Screen button state.
     * When a registration is deleted (cancel), this emits `false` instantly,
     * causing the Home Screen button to flip back to "Register" with zero delay.
     */
    fun observeRegistrationStatus(matchId: String): Flow<Boolean> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val regDocRef = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(REGISTRATIONS_COLLECTION)
            .document(uid)
        val listener = regDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(false)
                return@addSnapshotListener
            }
            trySend(snapshot?.exists() == true)
        }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time Flow<Set<String>> — emits the full set of matchIds the current
     * user is registered for. Used by RegistrationViewModel to keep a live
     * registry of registration state that is shared across Home and My Matches.
     *
     * Listens to the collectionGroup snapshot so any external deletion (e.g.
     * admin removes a registration) is also reflected immediately.
     */
    fun observeMyRegisteredMatchIds(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collectionGroup(REGISTRATIONS_COLLECTION)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("matchId") }
                    ?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Fetch the full SportUser profile for the current user (one-shot, not reactive).
     * Used during eligibility pre-checks inside the registration ViewModel.
     */
    suspend fun getCurrentUserProfile(): com.sportflow.app.data.model.SportUser? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        return snap.toObject(com.sportflow.app.data.model.SportUser::class.java)
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

    /**
     * Real-time stream of full Match documents for all matches the current user is
     * registered in. This uses TWO snapshot listeners as a single source of truth:
     *   1) registrations collectionGroup for membership
     *   2) gnits_matches collection for match document updates
     *
     * Result: admin manual edits (venue/team/time) reflect instantly on My Matches
     * without requiring any registration mutation.
     */
    fun observeMyRegisteredMatchesRealtime(): Flow<List<com.sportflow.app.data.model.Match>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var registeredIds: Set<String> = emptySet()
        var allMatches: List<com.sportflow.app.data.model.Match> = emptyList()

        fun emitFiltered() {
            val filtered = if (registeredIds.isEmpty()) {
                emptyList()
            } else {
                allMatches
                    .filter { it.id in registeredIds }
                    .sortedByDescending { it.scheduledTime }
            }
            trySend(filtered)
        }

        val registrationListener = firestore.collectionGroup(REGISTRATIONS_COLLECTION)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                registeredIds = snapshot?.documents
                    ?.mapNotNull { it.getString("matchId") }
                    ?.toSet()
                    ?: emptySet()
                emitFiltered()
            }

        val matchesListener = firestore.collection(MATCHES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                allMatches = snapshot?.documents?.mapNotNull {
                    it.toObject(com.sportflow.app.data.model.Match::class.java)?.copy(id = it.id)
                } ?: emptyList()
                emitFiltered()
            }

        awaitClose {
            registrationListener.remove()
            matchesListener.remove()
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MANUAL FIXTURE EDITING — Admin override for drag-and-drop changes
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Apply a manual fixture edit from the Admin Dashboard.
     * Only non-null fields in [edit] are updated in Firestore.
     * The snapshot listener on getAllMatches() triggers an immediate StateFlow
     * update across all student devices (Home Screen re-sorts "Upcoming Matches").
     */
    suspend fun applyManualFixtureEdit(edit: com.sportflow.app.data.model.ManualFixtureEdit) {
        val updates = mutableMapOf<String, Any>()
        edit.newScheduledTime?.let { updates["scheduledTime"] = it }
        edit.newVenue?.let { updates["venue"] = it }
        edit.newTeamA?.let { updates["teamA"] = it }
        edit.newTeamB?.let { updates["teamB"] = it }
        edit.newTeamADepartment?.let { updates["teamADepartment"] = it }
        edit.newTeamBDepartment?.let { updates["teamBDepartment"] = it }

        if (updates.isNotEmpty()) {
            firestore.collection(MATCHES_COLLECTION)
                .document(edit.matchId)
                .update(updates)
                .await()

            // Broadcast venue change notification
            val doc = firestore.collection(MATCHES_COLLECTION).document(edit.matchId).get().await()
            val teamA = doc.getString("teamA") ?: ""
            val teamB = doc.getString("teamB") ?: ""
            val venue = doc.getString("venue") ?: ""
            
            pushNotificationTrigger(
                type = "fixture_change",
                title = "⚠️ Fixture Updated",
                body = "$teamA vs $teamB - Venue changed to $venue",
                matchId = edit.matchId,
                topic = FCM_TOPIC_ALL
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ADMIN APPROVAL SYSTEM — Accept/Deny registrations
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Real-time stream of all registrations (Admin Data Bridge).
     */
    fun observeAllRegistrations(): Flow<List<Registration>> = callbackFlow {
        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val registrations = snapshot?.documents?.mapNotNull {
                    it.toObject(Registration::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(registrations)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time count of unseen registrations (for Admin "New Entry" badge).
     */
    fun observeNewRegistrationCount(): Flow<Int> = callbackFlow {
        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("seen", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Mark a registration as seen by admin.
     */
    suspend fun markRegistrationAsSeen(registrationId: String) {
        firestore.collection(GNITS_REGISTRATIONS)
            .document(registrationId)
            .update("seen", true)
            .await()
    }

    /**
     * Admin accepts a registration.
     */
    suspend fun acceptRegistration(registrationId: String) {
        firestore.collection(GNITS_REGISTRATIONS)
            .document(registrationId)
            .update(mapOf(
                "status" to RegistrationStatus.CONFIRMED.name,
                "seen" to true
            ))
            .await()

        // Send confirmation notification to student
        val doc = firestore.collection(GNITS_REGISTRATIONS).document(registrationId).get().await()
        val matchName = doc.getString("matchName") ?: ""
        val userId = doc.getString("uid") ?: ""
        
        pushNotificationTrigger(
            type = "registration_accepted",
            title = "✅ Registration Accepted",
            body = "Your registration for $matchName has been approved!",
            matchId = doc.getString("matchId") ?: "",
            topic = "user_$userId"
        )
    }

    /**
     * Admin denies a registration and removes it completely.
     * Reads uid and matchId from Firestore document fields (not via string split)
     * to avoid fragile parsing when UIDs or match IDs contain underscores.
     * Decrements squad count to reopen the spot.
     */
    suspend fun denyRegistration(registrationId: String, reason: String) {
        // Read registration document to get uid and matchId reliably
        val regDoc = firestore.collection(GNITS_REGISTRATIONS).document(registrationId).get().await()
        if (!regDoc.exists()) return

        val userId = regDoc.getString("uid") ?: return
        val matchId = regDoc.getString("matchId") ?: return
        val matchName = regDoc.getString("matchName") ?: ""

        // Delete from gnits_registrations (Admin Data Bridge)
        firestore.collection(GNITS_REGISTRATIONS)
            .document(registrationId)
            .delete()
            .await()

        // Delete from match sub-collection if it exists
        try {
            firestore.collection(MATCHES_COLLECTION)
                .document(matchId)
                .collection(REGISTRATIONS_COLLECTION)
                .document(userId)
                .delete()
                .await()
        } catch (_: Exception) {
            // Non-critical if sub-collection document is already gone
        }

        // Decrement counters atomically (floor 0 — read-modify-write)
        val matchRef = firestore.collection(MATCHES_COLLECTION).document(matchId)
        val matchSnap = matchRef.get().await()
        val curSquad = matchSnap.getLong("currentSquadCount")?.toInt() ?: 0
        val curReg   = matchSnap.getLong("registrationCount")?.toInt() ?: 0
        val updates = mutableMapOf<String, Any>()
        if (curSquad > 0) updates["currentSquadCount"] = FieldValue.increment(-1)
        if (curReg > 0)   updates["registrationCount"] = FieldValue.increment(-1)
        if (updates.isNotEmpty()) matchRef.update(updates).await()

        // Notify student of denial
        pushNotificationTrigger(
            type = "registration_denied",
            title = "❌ Registration Denied",
            body = "Your registration for $matchName was denied. Reason: $reason",
            matchId = matchId,
            topic = "user_$userId"
        )

        // Check if spot just opened in a previously full squad
        val freshSnap = matchRef.get().await()
        val maxSquad = freshSnap.getLong("maxSquadSize")?.toInt() ?: 0
        val currentCount = freshSnap.getLong("currentSquadCount")?.toInt() ?: 0
        if (maxSquad > 0 && currentCount == maxSquad - 1) {
            pushNotificationTrigger(
                type = "spot_opened",
                title = "🟢 Spot Available",
                body = "A spot has opened up in $matchName - register now!",
                matchId = matchId,
                topic = FCM_TOPIC_ALL
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PLAYER SCORECARDS — Sport-specific performance tracking
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Get all scorecards for a match (real-time).
     */
    fun getScorecardsForMatch(matchId: String): Flow<List<PlayerScorecard>> = callbackFlow {
        val listener = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(SCORECARDS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val scorecards = snapshot?.documents?.mapNotNull {
                    it.toObject(PlayerScorecard::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(scorecards)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Initialize blank scorecards for all registered players.
     */
    suspend fun initializeScorecardsForMatch(matchId: String, sportType: String) {
        val registrations = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(REGISTRATIONS_COLLECTION)
            .get()
            .await()

        val defaultData = PlayerScorecardStrategy.createDefault(sportType)

        registrations.documents.forEach { regDoc ->
            val userId = regDoc.id
            val userName = regDoc.getString("userName") ?: ""
            val department = regDoc.getString("department") ?: ""
            
            val scorecard = PlayerScorecard(
                id = userId,
                matchId = matchId,
                playerId = userId,
                playerName = userName,
                department = department,
                team = "", // Will be set by admin
                sportType = sportType,
                sportData = defaultData,
                updatedAt = com.google.firebase.Timestamp.now()
            )

            firestore.collection(MATCHES_COLLECTION)
                .document(matchId)
                .collection(SCORECARDS_COLLECTION)
                .document(userId)
                .set(scorecard)
                .await()
        }
    }

    /**
     * Increment a specific stat for a player.
     */
    suspend fun incrementScorecardStat(matchId: String, playerId: String, statKey: String, delta: Int) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(SCORECARDS_COLLECTION)
            .document(playerId)
            .update(mapOf(
                "sportData.$statKey" to FieldValue.increment(delta.toLong()),
                "updatedAt" to com.google.firebase.Timestamp.now()
            ))
            .await()
    }

    /**
     * Get player's own scorecard for a match.
     */
    fun getMyScorecard(matchId: String): Flow<PlayerScorecard?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(SCORECARDS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val scorecard = snapshot?.toObject(PlayerScorecard::class.java)?.copy(id = snapshot.id)
                trySend(scorecard)
            }
        awaitClose { listener.remove() }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PLAYER SCORECARDS — Extended Operations
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Create or update a player's scorecard for a match.
     * Called when Admin initializes scorecards for registered players,
     * or when updating stats via the Referee Panel.
     */
    suspend fun upsertPlayerScorecard(scorecard: com.sportflow.app.data.model.PlayerScorecard) {
        val docRef = firestore.collection(MATCHES_COLLECTION)
            .document(scorecard.matchId)
            .collection(SCORECARDS_COLLECTION)
            .document(scorecard.playerId)
        docRef.set(scorecard.copy(
            updatedAt = com.google.firebase.Timestamp.now()
        )).await()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NOTIFICATION INTELLIGENCE — Seen/Unseen Tracking + Persistence
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Persist a notification to the current user's notification sub-collection.
     * Called by NotificationManager when a notification is shown to the user.
     */
    suspend fun persistNotification(
        type: String,
        title: String,
        body: String,
        matchId: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val notification = hashMapOf(
                "type"      to type,
                "title"     to title,
                "body"      to body,
                "matchId"   to matchId,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "seen"      to false
            )
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_NOTIFICATIONS)
                .add(notification)
                .await()
        } catch (_: Exception) {
            // Non-critical — notification persistence failure should not crash the app
        }
    }

    /**
     * Real-time count of unseen notifications for the current user.
     * Drives the bell icon badge on the Home Screen.
     */
    fun observeUnseenNotificationCount(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(USER_NOTIFICATIONS)
            .whereEqualTo("seen", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time list of all notifications for the current user, newest first.
     * Used by the Notification Center screen/dialog.
     */
    fun observeNotifications(): Flow<List<com.sportflow.app.data.model.NotificationItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(USER_NOTIFICATIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(com.sportflow.app.data.model.NotificationItem::class.java)
                        ?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    /** Mark a single notification as seen */
    suspend fun markNotificationSeen(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_NOTIFICATIONS)
                .document(notificationId)
                .update("seen", true)
                .await()
        } catch (_: Exception) {}
    }

    /** Mark all notifications as seen (bell icon tap) */
    suspend fun markAllNotificationsSeen() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val unseen = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_NOTIFICATIONS)
                .whereEqualTo("seen", false)
                .get()
                .await()
            val batch = firestore.batch()
            unseen.documents.forEach { doc ->
                batch.update(doc.reference, "seen", true)
            }
            batch.commit().await()
        } catch (_: Exception) {}
    }

    /**
     * Get the user's lastCheckedTimestamp for notification dedup.
     * Stored as a field on the user document itself for simplicity.
     */
    suspend fun getLastCheckedTimestamp(): com.google.firebase.Timestamp? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
            doc.getTimestamp("lastCheckedTimestamp")
        } catch (_: Exception) { null }
    }

    /** Update the lastCheckedTimestamp to now */
    suspend fun updateLastCheckedTimestamp() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update("lastCheckedTimestamp", com.google.firebase.Timestamp.now())
                .await()
        } catch (_: Exception) {}
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NOTE: observeAllRegistrations() and observeNewRegistrationCount() are
    // defined above at lines 1183 and 1202 respectively. No duplicates here.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Mark a registration as seen by admin */
    suspend fun markRegistrationSeen(registrationId: String) {
        try {
            firestore.collection(GNITS_REGISTRATIONS)
                .document(registrationId)
                .update("seen", true)
                .await()
        } catch (_: Exception) {}
    }

    /** Mark all registrations as seen by admin */
    suspend fun markAllRegistrationsSeen() {
        try {
            val unseen = firestore.collection(GNITS_REGISTRATIONS)
                .whereEqualTo("seen", false)
                .get()
                .await()
            val batch = firestore.batch()
            unseen.documents.forEach { doc ->
                batch.update(doc.reference, "seen", true)
            }
            batch.commit().await()
        } catch (_: Exception) {}
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NOTIFICATION CENTER — Seen/Unseen tracking
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Get all notifications for a user (real-time).
     */
    fun getNotifications(userId: String): Flow<List<NotificationItem>> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_NOTIFICATIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull {
                    it.toObject(NotificationItem::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(notifications)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Mark a notification as seen.
     */
    suspend fun markNotificationAsSeen(userId: String, notificationId: String) {
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_NOTIFICATIONS)
            .document(notificationId)
            .update("seen", true)
            .await()
    }

    /**
     * Mark all notifications as seen.
     */
    suspend fun markAllNotificationsAsSeen(userId: String) {
        val notifications = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_NOTIFICATIONS)
            .whereEqualTo("seen", false)
            .get()
            .await()

        notifications.documents.forEach { doc ->
            doc.reference.update("seen", true).await()
        }
    }

    /**
     * Create a notification for a user (called by Cloud Functions or internally).
     */
    suspend fun createNotification(
        userId: String,
        type: String,
        title: String,
        body: String,
        matchId: String
    ) {
        val notification = NotificationItem(
            type = type,
            title = title,
            body = body,
            matchId = matchId,
            timestamp = com.google.firebase.Timestamp.now(),
            seen = false
        )

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_NOTIFICATIONS)
            .add(notification)
            .await()
    }
}
