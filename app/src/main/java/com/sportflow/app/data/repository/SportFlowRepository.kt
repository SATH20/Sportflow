package com.sportflow.app.data.repository

import com.google.firebase.Timestamp
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
    private fun isPairStyleSport(sport: SportType): Boolean {
        return sport == SportType.BADMINTON || sport == SportType.TABLE_TENNIS
    }

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
        const val ANNOUNCEMENTS_COLLECTION = "announcements"
        const val UPDATES_COLLECTION = "gnits_updates"

        // FCM topics — must match what Cloud Functions publishes to
        const val FCM_TOPIC_ALL         = "gnits_sports_all"
        const val FCM_TOPIC_SCORES      = "gnits_score_updates"
        const val FCM_TOPIC_MATCH_START = "gnits_match_start"
        const val FCM_TOPIC_TOURNAMENT  = "gnits_tournaments"
        const val FCM_TOPIC_PAYMENT     = "gnits_payment"
        const val FCM_TOPIC_ADMIN       = "gnits_admins"
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

    // ── Admin Fixture Creation ──────────────────────────────────────────────

    /** Get all registrations for a specific tournament to create fixtures */
    suspend fun getRegistrationsForTournament(tournamentId: String): List<Registration> {
        return firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("tournamentId", tournamentId)
            .whereEqualTo("status", RegistrationStatus.CONFIRMED.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Registration::class.java)?.copy(id = it.id) }
    }

    /** 
     * Link an existing match to a user by adding their UID to the participants list.
     * This is useful for retroactively fixing matches that were created without participant tracking.
     */
    suspend fun linkMatchToUser(matchId: String, userId: String, team: String) {
        val field = if (team == "A") "teamAParticipants" else "teamBParticipants"
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(field, FieldValue.arrayUnion(userId))
            .await()
    }

    /**
     * Automatically link matches to users based on name matching.
     * This helps fix existing matches that don't have participant tracking.
     */
    suspend fun autoLinkMatchesToUsers() {
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid
        
        // Get user's display name
        val userDoc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        val userName = userDoc.getString("displayName") ?: return
        
        if (userName.isBlank()) return
        
        // Find all matches where user's name appears
        val allMatches = firestore.collection(MATCHES_COLLECTION).get().await()
        
        allMatches.documents.forEach { doc ->
            val match = doc.toObject(Match::class.java)?.copy(id = doc.id) ?: return@forEach
            
            // Check if user's name is in teamA or teamB
            val isInTeamA = match.teamA.contains(userName, ignoreCase = true)
            val isInTeamB = match.teamB.contains(userName, ignoreCase = true)
            
            // Check if already linked
            val alreadyLinkedA = match.teamAParticipants.contains(uid)
            val alreadyLinkedB = match.teamBParticipants.contains(uid)
            
            // Link if needed
            if (isInTeamA && !alreadyLinkedA) {
                linkMatchToUser(match.id, uid, "A")
            }
            if (isInTeamB && !alreadyLinkedB) {
                linkMatchToUser(match.id, uid, "B")
            }
        }
    }

    /** Create fixtures between registered users for a tournament */
    suspend fun createFixturesBetweenRegisteredUsers(
        tournamentId: String,
        sportType: String,
        venue: String,
        startTime: Timestamp,
        intervalMinutes: Long = 60L
    ): List<String> {
        val registrations = getRegistrationsForTournament(tournamentId)
        val tournament = firestore.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .get()
            .await()
            .toObject(Tournament::class.java)

        if (registrations.size < 2) {
            throw IllegalStateException("Need at least 2 confirmed registrations to create fixtures")
        }

        val createdMatchIds = mutableListOf<String>()
        var timeOffset = 0L

        // Create matches between pairs of registered users
        for (i in registrations.indices step 2) {
            val userA = registrations[i]
            val userB = registrations.getOrNull(i + 1)

            if (userB != null) {
                val matchTime = Timestamp(startTime.seconds + timeOffset * 60, 0)
                val matchId = createMatchBetweenUsers(
                    userAId = userA.uid,
                    userBId = userB.uid,
                    userAName = userA.userName,
                    userBName = userB.userName,
                    sportType = sportType,
                    venue = venue,
                    scheduledTime = matchTime,
                    tournamentId = tournamentId,
                    tournamentName = tournament?.name ?: "",
                    round = "Round 1"
                )
                createdMatchIds.add(matchId)
                timeOffset += intervalMinutes

                // Update registrations to link them to the created match
                firestore.collection(GNITS_REGISTRATIONS)
                    .document(userA.id)
                    .update("matchId", matchId)
                    .await()
                firestore.collection(GNITS_REGISTRATIONS)
                    .document(userB.id)
                    .update("matchId", matchId)
                    .await()

                // Also create registration sub-documents under the match
                // so collectionGroup queries find them for My Matches
                val matchName = "${userA.userName} vs ${userB.userName}"
                try {
                    val regDataA = hashMapOf(
                        "uid" to userA.uid,
                        "matchId" to matchId,
                        "tournamentId" to tournamentId,
                        "userName" to userA.userName,
                        "email" to userA.email,
                        "department" to userA.department,
                        "status" to RegistrationStatus.CONFIRMED.name,
                        "registeredAt" to com.google.firebase.Timestamp.now(),
                        "sportType" to sportType,
                        "matchName" to matchName
                    )
                    firestore.collection(MATCHES_COLLECTION)
                        .document(matchId)
                        .collection(REGISTRATIONS_COLLECTION)
                        .document(userA.uid)
                        .set(regDataA)
                        .await()

                    val regDataB = hashMapOf(
                        "uid" to userB.uid,
                        "matchId" to matchId,
                        "tournamentId" to tournamentId,
                        "userName" to userB.userName,
                        "email" to userB.email,
                        "department" to userB.department,
                        "status" to RegistrationStatus.CONFIRMED.name,
                        "registeredAt" to com.google.firebase.Timestamp.now(),
                        "sportType" to sportType,
                        "matchName" to matchName
                    )
                    firestore.collection(MATCHES_COLLECTION)
                        .document(matchId)
                        .collection(REGISTRATIONS_COLLECTION)
                        .document(userB.uid)
                        .set(regDataB)
                        .await()
                } catch (_: Exception) {
                    // Non-critical — participants array is the primary tracking mechanism
                }
            }
        }

        return createdMatchIds
    }

    /** Create a new match document in the gnits_matches collection */
    suspend fun createMatch(match: Match): String {
        val data = hashMapOf(
            "sportType" to match.sportType,
            "teamA" to match.teamA,
            "teamB" to match.teamB,
            "teamADepartment" to match.teamADepartment,
            "teamBDepartment" to match.teamBDepartment,
            "teamAId" to match.teamAId,
            "teamBId" to match.teamBId,
            "teamAParticipants" to match.teamAParticipants,
            "teamBParticipants" to match.teamBParticipants,
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
            "createdBy" to (auth.currentUser?.uid ?: ""),
            "eligibilityText" to match.eligibilityText,
            "maxRegistrations" to match.maxRegistrations,
            "registrationCount" to match.registrationCount,
            "nextMatchId" to match.nextMatchId,
            "maxSquadSize" to match.maxSquadSize,
            "currentSquadCount" to match.currentSquadCount,
            "allowedDepartments" to match.allowedDepartments,
            "allowedYears" to match.allowedYears,
            "startNotifSent" to false,
            "reminderNotifSent" to false
        )
        val docRef = firestore.collection(MATCHES_COLLECTION).add(data).await()
        
        // MILESTONE NOTIFICATION: Schedule Creation (Global Broadcast)
        // Send to ALL users to announce new match availability
        if (match.scheduledTime != null) {
            val dateFormat = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
            val scheduleTime = dateFormat.format(match.scheduledTime.toDate())
            
            val tournamentInfo = if (match.tournamentName.isNotBlank()) " | ${match.tournamentName}" else ""
            
            pushNotificationTrigger(
                type = "match_scheduled",
                title = "📅 New ${match.sportType} Match Scheduled",
                body = "${match.teamA} vs ${match.teamB} at ${match.venue} on $scheduleTime$tournamentInfo",
                matchId = docRef.id,
                topic = FCM_TOPIC_ALL
            )
            
            android.util.Log.d("MatchCreated", "Sent global notification for match: ${match.teamA} vs ${match.teamB}")
        }
        
        return docRef.id
    }

    /** Create a match between two registered users - ensures both users see it in My Matches */
    suspend fun createMatchBetweenUsers(
        userAId: String,
        userBId: String,
        userAName: String,
        userBName: String,
        sportType: String,
        venue: String,
        scheduledTime: Timestamp,
        tournamentId: String = "",
        tournamentName: String = "",
        round: String = ""
    ): String {
        val match = Match(
            sportType = sportType,
            teamA = userAName,
            teamB = userBName,
            teamAId = userAId,  // Set teamAId
            teamBId = userBId,  // Set teamBId
            teamAParticipants = listOf(userAId),
            teamBParticipants = listOf(userBId),
            venue = venue,
            scheduledTime = scheduledTime,
            tournamentId = tournamentId,
            tournamentName = tournamentName,
            round = round,
            status = MatchStatus.SCHEDULED,
            eligibilityText = "Registered participants only"
        )
        return createMatch(match)
    }

    /** Delete a match document from the gnits_matches collection */
    suspend fun deleteMatch(matchId: String) {
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .delete()
            .await()
    }

    // ── Live Scoring Engine — Sport-Aware Atomic Updates ────────────────────

    /**
     * Universal scoring entry point. Admin calls this for every button tap.
     * The [action] carries the Firestore field name and delta from [SportScoreEngine].
     * 
     * STATUS GUARD: Only allows scoring when match status is LIVE or HALFTIME.
     */
    suspend fun applyScoringAction(matchId: String, action: com.sportflow.app.data.model.ScoringAction) {
        if (action.delta == 0) return // "special" actions like Dot Ball, Foul (just highlights)

        android.util.Log.d("ScoringAction", "Applying: ${action.label} | Field: ${action.field} | Delta: ${action.delta} | Team: ${action.team}")

        // STATUS GUARD: Check match status before allowing scoring
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val statusStr = doc.getString("status") ?: ""
        val status = try {
            MatchStatus.valueOf(statusStr)
        } catch (_: Exception) {
            MatchStatus.SCHEDULED
        }
        
        // Only allow scoring if match is LIVE or HALFTIME
        if (status != MatchStatus.LIVE && status != MatchStatus.HALFTIME) {
            android.util.Log.e("ScoringAction", "Cannot score: Match status is $status")
            throw IllegalStateException("Cannot score: Match must be LIVE or HALFTIME. Current status: $status")
        }

        android.util.Log.d("ScoringAction", "Updating Firestore field: ${action.field} with increment: ${action.delta}")
        
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(action.field, FieldValue.increment(action.delta.toLong()))
            .await()

        android.util.Log.d("ScoringAction", "Score updated successfully!")

        // Read back current scores for logging
        val updatedDoc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val currentScoreA = updatedDoc.getLong("scoreA")?.toInt() ?: 0
        val currentScoreB = updatedDoc.getLong("scoreB")?.toInt() ?: 0
        android.util.Log.d("ScoringAction", "Current scores after update: $currentScoreA - $currentScoreB")

        // SILENT SCORING: No push notifications for score updates
        // Scores sync via Firestore SnapshotListener for real-time UI updates
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

        android.util.Log.d("CompleteSet", "Completing set $currentSet: $curSetA-$curSetB, Winner: Team $winnerTeam")
        android.util.Log.d("CompleteSet", "Incrementing $setsField")

        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(mapOf(
                "completedSets" to newCompleted,
                setsField to FieldValue.increment(1),
                "currentSetScoreA" to 0,
                "currentSetScoreB" to 0,
                "currentSet" to (currentSet + 1)
            )).await()
        
        // Verify the update
        val verifyDoc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val setsWonA = verifyDoc.getLong("setsWonA")?.toInt() ?: 0
        val setsWonB = verifyDoc.getLong("setsWonB")?.toInt() ?: 0
        android.util.Log.d("CompleteSet", "✅ Set completed! Current sets won: A=$setsWonA, B=$setsWonB")
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
        
        // ONE-TIME NOTIFICATION CHECK: Prevent duplicate "Match Started" notifications
        val alreadySent = doc.getBoolean("startNotifSent") ?: false
        if (alreadySent) {
            android.util.Log.d("StartMatch", "Match start notification already sent for $matchId, skipping")
        }
        
        val teamA = doc.getString("teamA") ?: "Team A"
        val teamB = doc.getString("teamB") ?: "Team B"
        val sport = doc.getString("sportType") ?: "Match"
        val venue = doc.getString("venue") ?: "GNITS"
        
        // Get participant UIDs for personalized notifications
        val teamAParticipants = doc.get("teamAParticipants") as? List<String> ?: emptyList()
        val teamBParticipants = doc.get("teamBParticipants") as? List<String> ?: emptyList()
        val allParticipants = teamAParticipants + teamBParticipants

        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(
                mapOf(
                    "status" to MatchStatus.LIVE.name,
                    "currentPeriod" to period,
                    "elapsedTime" to "00:00",
                    "startNotifSent" to true  // Mark notification as sent
                )
            )
            .await()

        // MILESTONE NOTIFICATION: Match Start (only if not already sent)
        if (!alreadySent) {
            // Send personalized notifications to match participants only
            sendPersonalizedNotification(
                userIds = allParticipants,
                type = "match_start",
                title = "🏁 Your $sport Match is LIVE!",
                body = "$teamA vs $teamB has started at $venue. Good luck!",
                matchId = matchId
            )
            android.util.Log.d("StartMatch", "Sent match start notification to ${allParticipants.size} participants")
        }
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
     *  
     *  ALTERNATIVE FIX: Force fresh read from Firestore (bypass cache) before completing.
     */
    suspend fun completeMatch(matchId: String) {
        // CRITICAL: Force fresh read from server (not cache)
        val doc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .get(com.google.firebase.firestore.Source.SERVER) // Force server read
            .await()

        val match = doc.toObject(Match::class.java) ?: return

        android.util.Log.d("CompleteMatch", "=== COMPLETING MATCH ===")
        android.util.Log.d("CompleteMatch", "Match ID: ${match.id}")
        android.util.Log.d("CompleteMatch", "Teams: ${match.teamA} vs ${match.teamB}")
        android.util.Log.d("CompleteMatch", "Sport: ${match.sportType}")
        android.util.Log.d("CompleteMatch", "Status: ${match.status}")
        android.util.Log.d("CompleteMatch", "ScoreA from Firestore: ${match.scoreA}")
        android.util.Log.d("CompleteMatch", "ScoreB from Firestore: ${match.scoreB}")

        // If scores are still 0, something is wrong with scoring
        if (match.scoreA == 0 && match.scoreB == 0) {
            android.util.Log.e("CompleteMatch", "WARNING: Both scores are 0! Match was not scored properly.")
            android.util.Log.e("CompleteMatch", "Check if applyScoringAction() was called during the match.")
        }

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

        android.util.Log.d("CompleteMatch", "Calculated Winner: $winnerId")

        // ATOMIC UPDATE: Create ONE map with ALL final data
        val atomicUpdate = mutableMapOf<String, Any>(
            // Status and winner
            "status" to MatchStatus.COMPLETED.name,
            "currentPeriod" to "Full Time",
            "winnerId" to winnerId,
            
            // CRITICAL: Explicitly save current scores (even if 0)
            "scoreA" to match.scoreA,
            "scoreB" to match.scoreB
        )
        
        // Add sport-specific stats to the SAME update
        when (sport) {
            com.sportflow.app.data.model.SportType.CRICKET -> {
                atomicUpdate["wicketsA"] = match.wicketsA
                atomicUpdate["wicketsB"] = match.wicketsB
                atomicUpdate["oversA"] = match.oversA
                atomicUpdate["oversB"] = match.oversB
                atomicUpdate["extrasA"] = match.extrasA
                atomicUpdate["extrasB"] = match.extrasB
                android.util.Log.d("CompleteMatch", "Cricket: ${match.scoreA}/${match.wicketsA} vs ${match.scoreB}/${match.wicketsB}, Overs: ${match.oversA} | ${match.oversB}")
            }
            com.sportflow.app.data.model.SportType.BADMINTON,
            com.sportflow.app.data.model.SportType.VOLLEYBALL,
            com.sportflow.app.data.model.SportType.TABLE_TENNIS -> {
                atomicUpdate["setsWonA"] = match.setsWonA
                atomicUpdate["setsWonB"] = match.setsWonB
                atomicUpdate["completedSets"] = match.completedSets
                atomicUpdate["currentSetScoreA"] = match.currentSetScoreA
                atomicUpdate["currentSetScoreB"] = match.currentSetScoreB
                android.util.Log.d("CompleteMatch", "Sets: ${match.setsWonA} - ${match.setsWonB}, Completed: ${match.completedSets}")
            }
            com.sportflow.app.data.model.SportType.BASKETBALL -> {
                atomicUpdate["q1A"] = match.q1A
                atomicUpdate["q1B"] = match.q1B
                atomicUpdate["q2A"] = match.q2A
                atomicUpdate["q2B"] = match.q2B
                atomicUpdate["q3A"] = match.q3A
                atomicUpdate["q3B"] = match.q3B
                atomicUpdate["q4A"] = match.q4A
                atomicUpdate["q4B"] = match.q4B
                android.util.Log.d("CompleteMatch", "Basketball quarters saved")
            }
            else -> {
                android.util.Log.d("CompleteMatch", "Standard sport scores: ${match.scoreA} - ${match.scoreB}")
            }
        }

        // SINGLE ATOMIC UPDATE
        android.util.Log.d("CompleteMatch", "Executing atomic update with ${atomicUpdate.size} fields...")
        android.util.Log.d("CompleteMatch", "Update data: $atomicUpdate")
        
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .update(atomicUpdate)
            .await()
        
        android.util.Log.d("CompleteMatch", "✅ Match completed successfully!")
        android.util.Log.d("CompleteMatch", "======================")

        // Verify the update by reading back
        val verifyDoc = firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .get(com.google.firebase.firestore.Source.SERVER)
            .await()
        
        val verifyScoreA = verifyDoc.getLong("scoreA")?.toInt() ?: -1
        val verifyScoreB = verifyDoc.getLong("scoreB")?.toInt() ?: -1
        val verifyStatus = verifyDoc.getString("status") ?: "UNKNOWN"
        
        android.util.Log.d("CompleteMatch", "VERIFICATION: Status=$verifyStatus, ScoreA=$verifyScoreA, ScoreB=$verifyScoreB")

        // MILESTONE NOTIFICATION: Match End
        // Get participant UIDs for personalized notifications
        val teamAParticipants = doc.get("teamAParticipants") as? List<String> ?: emptyList()
        val teamBParticipants = doc.get("teamBParticipants") as? List<String> ?: emptyList()
        val allParticipants = teamAParticipants + teamBParticipants
        
        // Send personalized result notification to match participants only
        val resultText = when (winnerId) {
            "DRAW" -> "${match.teamA} ${match.scoreA} – ${match.scoreB} ${match.teamB} · It's a Draw!"
            else   -> "🏆 $winnerId wins! ${match.teamA} ${match.scoreA} – ${match.scoreB} ${match.teamB}"
        }
        
        sendPersonalizedNotification(
            userIds = allParticipants,
            type = "match_end",
            title = "Full Time! ${match.sportType} Result",
            body = resultText,
            matchId = matchId
        )
        
        android.util.Log.d("CompleteMatch", "Sent match end notification to ${allParticipants.size} participants")

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

    /** 
     * EMERGENCY FIX: Manually recalculate and update setsWonA/B from completedSets string
     * Use this to fix completed matches that have completedSets but setsWonA/B are 0
     */
    suspend fun fixCompletedMatchScores(matchId: String) {
        val doc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val completedSets = doc.getString("completedSets") ?: ""
        
        if (completedSets.isBlank()) {
            android.util.Log.e("FixMatch", "No completedSets data found for match $matchId")
            return
        }
        
        // Parse completedSets: "11-2,0-0,1-11" -> count who won each set
        val sets = completedSets.split(",")
        var setsWonA = 0
        var setsWonB = 0
        
        sets.forEach { set ->
            val scores = set.split("-")
            if (scores.size == 2) {
                val scoreA = scores[0].toIntOrNull() ?: 0
                val scoreB = scores[1].toIntOrNull() ?: 0
                
                when {
                    scoreA > scoreB -> setsWonA++
                    scoreB > scoreA -> setsWonB++
                    // If tied, don't count (shouldn't happen in real matches)
                }
            }
        }
        
        android.util.Log.d("FixMatch", "Calculated from '$completedSets': setsWonA=$setsWonA, setsWonB=$setsWonB")
        
        // Determine winner
        val teamA = doc.getString("teamA") ?: ""
        val teamB = doc.getString("teamB") ?: ""
        val winnerId = when {
            setsWonA > setsWonB -> teamA
            setsWonB > setsWonA -> teamB
            else -> "DRAW"
        }
        
        // Update Firestore
        firestore.collection(MATCHES_COLLECTION).document(matchId)
            .update(mapOf(
                "setsWonA" to setsWonA,
                "setsWonB" to setsWonB,
                "winnerId" to winnerId
            ))
            .await()
        
        android.util.Log.d("FixMatch", "✅ Fixed match $matchId: $teamA $setsWonA - $setsWonB $teamB, Winner: $winnerId")
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

    suspend fun createTournament(tournament: Tournament): String {
        val data = hashMapOf(
            "name" to tournament.name,
            "sport" to tournament.sport,
            "status" to TournamentStatus.REGISTRATION.name,
            "startDate" to tournament.startDate,
            "endDate" to tournament.endDate,
            "teams" to emptyList<String>(),
            "bracketGenerated" to false,
            "entryFee" to tournament.entryFee,
            "prizePool" to tournament.prizePool,
            "maxTeams" to tournament.maxTeams,
            "venue" to tournament.venue,
            "eligibilityText" to tournament.eligibilityText,
            "deptQuota" to tournament.deptQuota,
            "allowedDepartments" to tournament.allowedDepartments,
            "allowedYears" to tournament.allowedYears,
            "createdBy" to (auth.currentUser?.uid ?: ""),
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        val ref = firestore.collection(TOURNAMENTS_COLLECTION).add(data).await()
        
        // MILESTONE NOTIFICATION: Tournament Creation (Global Broadcast)
        // Send to ALL users regardless of registration status
        val prizeInfo = if (tournament.prizePool.isNotBlank()) " | Prize: ${tournament.prizePool}" else ""
        val eligibilityInfo = if (tournament.eligibilityText.isNotBlank()) " | ${tournament.eligibilityText}" else ""
        
        pushNotificationTrigger(
            type = "tournament_created",
            title = "🏆 New ${tournament.sport} Tournament!",
            body = "${tournament.name} is now open for registration${prizeInfo}${eligibilityInfo}",
            matchId = "",
            topic = FCM_TOPIC_ALL
        )
        
        // Also create announcement for in-app feed
        createAnnouncement(
            title = "🏆 ${tournament.name}",
            message = "${tournament.sport} tournament is now open for registration. ${tournament.eligibilityText}",
            category = AnnouncementCategory.GENERAL,
            tournamentId = ref.id
        )
        
        android.util.Log.d("TournamentCreated", "Sent global notification for tournament: ${tournament.name}")
        
        return ref.id
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
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.let { user ->
            val profile = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
                .toObject(SportUser::class.java)
            syncNotificationTopicsForUser(profile)
        }
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
            syncNotificationTopicsForUser(sportUser)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /** Update user profile with registration data */
    suspend fun updateUserProfile(
        uid: String,
        displayName: String? = null,
        rollNumber: String? = null,
        department: String? = null,
        yearOfStudy: String? = null,
        preferredSportRole: String? = null
    ) {
        val updates = mutableMapOf<String, Any>()
        displayName?.let { updates["displayName"] = it }
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
        if (!displayName.isNullOrBlank() && auth.currentUser?.uid == uid) {
            auth.currentUser
                ?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                )
                ?.await()
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

    private fun syncNotificationTopicsForUser(user: SportUser?) {
        GnitsDepartment.entries.forEach { dept ->
            unsubscribeFromTopic("dept_${dept.name}")
        }
        unsubscribeFromTopic(FCM_TOPIC_ADMIN)
        subscribeToTopic(FCM_TOPIC_ALL)
        subscribeToTopic(FCM_TOPIC_MATCH_START)
        subscribeToTopic(FCM_TOPIC_SCORES)
        subscribeToTopic(FCM_TOPIC_TOURNAMENT)
        subscribeToTopic(FCM_TOPIC_PAYMENT)
        when (user?.role) {
            UserRole.ADMIN -> subscribeToTopic(FCM_TOPIC_ADMIN)
            else -> if (!user?.department.isNullOrBlank()) {
                subscribeToTopic("dept_${user!!.department}")
            }
        }
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
            // For match_start notifications, deduplicate by type and matchId only
            // to prevent repeated "Match is LIVE now" notifications
            val dedupeKey = if (type == "match_start") {
                "$type|$matchId"
            } else {
                "$type|$matchId|$topic|${body.hashCode()}"
            }

            val existingUnseen = firestore.collection(NOTIFICATION_TRIGGERS)
                .whereEqualTo("dedupeKey", dedupeKey)
                .whereEqualTo("seen", false)
                .limit(1)
                .get()
                .await()

            if (!existingUnseen.isEmpty) return

            firestore.collection(NOTIFICATION_TRIGGERS).add(
                mapOf(
                    "type"      to type,
                    "title"     to title,
                    "body"      to body,
                    "matchId"   to matchId,
                    "topic"     to topic,
                    "dedupeKey" to dedupeKey,
                    "seen"      to false,
                    "sentAt"    to com.google.firebase.Timestamp.now(),
                    "processed" to false
                )
            ).await()
        } catch (_: Exception) {
            // Non-critical — notification failure should not crash the scoring flow
        }
    }

    /**
     * PERSONALIZED NOTIFICATIONS: Send direct FCM notifications to specific users.
     * Used for match milestones (Start, End) to notify only the participants.
     */
    private suspend fun sendPersonalizedNotification(
        userIds: List<String>,
        type: String,
        title: String,
        body: String,
        matchId: String
    ) {
        if (userIds.isEmpty()) return
        
        try {
            userIds.forEach { uid ->
                firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .collection(USER_NOTIFICATIONS)
                    .add(mapOf(
                        "type" to type,
                        "title" to title,
                        "body" to body,
                        "matchId" to matchId,
                        "seen" to false,
                        "sentAt" to com.google.firebase.Timestamp.now()
                    ))
                    .await()
            }
            
            firestore.collection(NOTIFICATION_TRIGGERS).add(
                mapOf(
                    "type" to type,
                    "title" to title,
                    "body" to body,
                    "matchId" to matchId,
                    "targetUserIds" to userIds,
                    "dedupeKey" to "$type|$matchId|${userIds.sorted().joinToString(",")}",
                    "seen" to false,
                    "sentAt" to com.google.firebase.Timestamp.now(),
                    "processed" to false
                )
            ).await()
        } catch (e: Exception) {
            android.util.Log.e("PersonalizedNotif", "Failed: ${e.message}")
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

    suspend fun registerForMatch(
        match: com.sportflow.app.data.model.Match,
        payload: RegistrationPayload
    ) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val userRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val userSnap = userRef.get().await()
        val role = userSnap.getString("role") ?: UserRole.PLAYER.name
        if (role.equals(UserRole.ADMIN.name, ignoreCase = true)) {
            throw IllegalArgumentException("Admins cannot register teams or player entries")
        }

        val kind = payload.registrationKind
        val isBadminton = SportType.fromString(match.sportType) == SportType.BADMINTON
        val requiresTeamRoster = kind == RegistrationKind.TEAM
        val requiresPair = kind == RegistrationKind.BADMINTON_DOUBLES

        if (requiresTeamRoster && payload.teamName.isBlank()) {
            throw IllegalArgumentException("Team name is required")
        }
        if (requiresTeamRoster && payload.roster.isEmpty()) {
            throw IllegalArgumentException("Add at least one player to the squad roster")
        }
        if (requiresTeamRoster && payload.roster.any { it.name.isBlank() || it.rollNumber.isBlank() || it.role.isBlank() }) {
            throw IllegalArgumentException("Every squad player needs a name, roll number, and role")
        }
        if (requiresPair && (payload.partnerName.isBlank() || payload.partnerRollNumber.isBlank())) {
            throw IllegalArgumentException("Doubles registration requires partner name and roll number")
        }
        if (isBadminton && kind == RegistrationKind.TEAM) {
            throw IllegalArgumentException("Badminton supports singles or doubles pair registration")
        }

        val matchRef = firestore.collection(MATCHES_COLLECTION).document(match.id)
        val regRef = matchRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)
        val bridgeRef = firestore.collection(GNITS_REGISTRATIONS).document("${user.uid}_${match.id}")
        val userDept = payload.department.ifBlank { userSnap.getString("department") ?: "" }
        val userYear = payload.yearOfStudy.ifBlank { userSnap.getString("yearOfStudy") ?: "" }

        if (match.allowedDepartments.isNotEmpty() &&
            userDept.isNotBlank() &&
            match.allowedDepartments.none { it.equals(userDept, ignoreCase = true) }
        ) {
            throw IllegalArgumentException("You are not eligible for this department-restricted event")
        }
        if (match.allowedYears.isNotEmpty() &&
            userYear.isNotBlank() &&
            match.allowedYears.none { it.equals(userYear, ignoreCase = true) }
        ) {
            throw IllegalArgumentException("You are not eligible for this year-restricted event")
        }

        val userName = userSnap.getString("displayName") ?: user.displayName ?: payload.captainName
        val rollNumber = payload.rollNumber.ifBlank { userSnap.getString("rollNumber") ?: "" }
        val email = payload.email.ifBlank { user.email ?: userSnap.getString("email") ?: "" }
        val fixtureUnitName = when (kind) {
            RegistrationKind.TEAM -> payload.teamName
            RegistrationKind.BADMINTON_DOUBLES -> "$userName / ${payload.partnerName}"
            RegistrationKind.BADMINTON_SINGLES -> userName
            RegistrationKind.INDIVIDUAL -> userName
        }
        val matchName = "${match.teamA} vs ${match.teamB}"
        var newSquadCount = 0

        val registration = Registration(
            id = bridgeRef.id,
            uid = user.uid,
            matchId = match.id,
            tournamentId = match.tournamentId,
            userName = userName,
            email = email,
            department = userDept,
            yearOfStudy = userYear,
            rollNumber = rollNumber,
            status = RegistrationStatus.PENDING,
            registeredAt = com.google.firebase.Timestamp.now(),
            seen = false,
            squadName = payload.teamName,
            captainName = payload.captainName.ifBlank { userName },
            captainPhone = payload.captainPhone,
            squadSize = when {
                kind == RegistrationKind.TEAM -> payload.roster.size
                kind == RegistrationKind.BADMINTON_DOUBLES -> 2
                else -> 1
            },
            sportRole = payload.sportRole,
            sportType = match.sportType,
            matchName = matchName,
            registrationKind = kind,
            teamName = payload.teamName,
            roster = payload.roster,
            partnerName = payload.partnerName,
            partnerRollNumber = payload.partnerRollNumber,
            partnerRole = payload.partnerRole,
            fixtureUnitName = fixtureUnitName
        )

        firestore.runTransaction { tx ->
            val matchSnap = tx.get(matchRef)
            if (tx.get(regRef).exists()) {
                throw IllegalArgumentException("Already registered for this event")
            }

            val maxSquad = matchSnap.getLong("maxSquadSize")?.toInt() ?: 0
            val curSquad = matchSnap.getLong("currentSquadCount")?.toInt() ?: 0
            val maxReg = matchSnap.getLong("maxRegistrations")?.toInt() ?: 0
            val curCount = matchSnap.getLong("registrationCount")?.toInt() ?: 0
            val effectiveMax = if (maxSquad > 0) maxSquad else maxReg
            val effectiveCur = if (maxSquad > 0) curSquad else curCount

            if (effectiveMax > 0 && effectiveCur >= effectiveMax) {
                throw IllegalArgumentException("Registration capacity is full")
            }

            tx.set(regRef, registration.copy(id = user.uid))
            tx.set(bridgeRef, registration)
            tx.update(matchRef, "currentSquadCount", FieldValue.increment(1))
            tx.update(matchRef, "registrationCount", FieldValue.increment(1))
            newSquadCount = effectiveCur + 1
        }.await()

        pushNotificationTrigger(
            type = "admin_registration_pending",
            title = "New ${kind.name.lowercase().replace('_', ' ')} registration",
            body = "$fixtureUnitName is waiting for approval in $matchName",
            matchId = match.id,
            topic = FCM_TOPIC_ADMIN
        )

        val maxSquadFinal = match.maxSquadSize.takeIf { it > 0 } ?: match.maxRegistrations
        if (maxSquadFinal > 0 && newSquadCount >= maxSquadFinal) {
            pushNotificationTrigger(
                type = "squad_closed",
                title = "Registration Full - ${match.sportType}",
                body = "Registrations for $matchName are now closed.",
                matchId = match.id,
                topic = FCM_TOPIC_ALL
            )
        }
    }

    suspend fun registerForTournament(
        tournament: Tournament,
        payload: RegistrationPayload
    ) {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not authenticated")

        val userRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val userSnap = userRef.get().await()
        val role = userSnap.getString("role") ?: UserRole.PLAYER.name
        if (role.equals(UserRole.ADMIN.name, ignoreCase = true)) {
            throw IllegalArgumentException("Admins can create tournaments only; they cannot register teams")
        }

        val sport = SportType.fromString(tournament.sport)
        val kind = payload.registrationKind
        val pairStyleSport = isPairStyleSport(sport)
        val requiresTeamRoster = !pairStyleSport && kind == RegistrationKind.TEAM
        val requiresPair = pairStyleSport && kind == RegistrationKind.BADMINTON_DOUBLES

        if (pairStyleSport && kind == RegistrationKind.TEAM) {
            throw IllegalArgumentException("${tournament.sport} tournaments accept singles or doubles pair entries")
        }
        if (!pairStyleSport && kind != RegistrationKind.TEAM) {
            throw IllegalArgumentException("This tournament requires a captain-led team entry")
        }
        if (requiresTeamRoster && payload.teamName.isBlank()) {
            throw IllegalArgumentException("Team name is required")
        }
        if (requiresTeamRoster && payload.roster.isEmpty()) {
            throw IllegalArgumentException("Add at least one player to the squad roster")
        }
        if (requiresTeamRoster && payload.roster.any { it.name.isBlank() || it.rollNumber.isBlank() || it.role.isBlank() }) {
            throw IllegalArgumentException("Every squad player needs a name, roll number, and role")
        }
        if (requiresPair && (payload.partnerName.isBlank() || payload.partnerRollNumber.isBlank())) {
            throw IllegalArgumentException("Doubles registration requires partner name and roll number")
        }

        val tournamentRef = firestore.collection(TOURNAMENTS_COLLECTION).document(tournament.id)
        val regRef = tournamentRef.collection(REGISTRATIONS_COLLECTION).document(user.uid)
        val bridgeRef = firestore.collection(GNITS_REGISTRATIONS).document("${user.uid}_${tournament.id}")
        val userDept = payload.department.ifBlank { userSnap.getString("department") ?: "" }
        val userYear = payload.yearOfStudy.ifBlank { userSnap.getString("yearOfStudy") ?: "" }

        if (tournament.allowedDepartments.isNotEmpty() &&
            userDept.isNotBlank() &&
            tournament.allowedDepartments.none { it.equals(userDept, ignoreCase = true) }
        ) {
            throw IllegalArgumentException("You are not eligible for this department-restricted tournament")
        }
        if (tournament.allowedYears.isNotEmpty() &&
            userYear.isNotBlank() &&
            tournament.allowedYears.none { it.equals(userYear, ignoreCase = true) }
        ) {
            throw IllegalArgumentException("You are not eligible for this year-restricted tournament")
        }

        val userName = userSnap.getString("displayName") ?: user.displayName ?: payload.captainName
        val rollNumber = payload.rollNumber.ifBlank { userSnap.getString("rollNumber") ?: "" }
        val email = payload.email.ifBlank { user.email ?: userSnap.getString("email") ?: "" }
        val fixtureUnitName = when (kind) {
            RegistrationKind.TEAM -> payload.teamName
            RegistrationKind.BADMINTON_DOUBLES -> "$userName / ${payload.partnerName}"
            RegistrationKind.BADMINTON_SINGLES -> userName
            RegistrationKind.INDIVIDUAL -> userName
        }
        val registration = Registration(
            id = bridgeRef.id,
            uid = user.uid,
            matchId = "",
            tournamentId = tournament.id,
            userName = userName,
            email = email,
            department = userDept,
            yearOfStudy = userYear,
            rollNumber = rollNumber,
            status = RegistrationStatus.PENDING,
            registeredAt = com.google.firebase.Timestamp.now(),
            seen = false,
            squadName = payload.teamName,
            captainName = payload.captainName.ifBlank { userName },
            captainPhone = payload.captainPhone,
            squadSize = when {
                kind == RegistrationKind.TEAM -> payload.roster.size
                kind == RegistrationKind.BADMINTON_DOUBLES -> 2
                else -> 1
            },
            sportRole = payload.sportRole,
            sportType = tournament.sport,
            matchName = tournament.name,
            registrationKind = kind,
            teamName = payload.teamName,
            roster = payload.roster,
            partnerName = payload.partnerName,
            partnerRollNumber = payload.partnerRollNumber,
            partnerRole = payload.partnerRole,
            fixtureUnitName = fixtureUnitName
        )

        firestore.runTransaction { tx ->
            val tournamentSnap = tx.get(tournamentRef)
            if (tx.get(regRef).exists()) {
                throw IllegalArgumentException("Already registered for this tournament")
            }
            val maxTeams = tournamentSnap.getLong("maxTeams")?.toInt() ?: 0
            val currentTeams = (tournamentSnap.get("teams") as? List<*>)?.size ?: 0
            if (maxTeams > 0 && currentTeams >= maxTeams) {
                throw IllegalArgumentException("Tournament registration capacity is full")
            }
            tx.set(regRef, registration.copy(id = user.uid))
            tx.set(bridgeRef, registration)
        }.await()

        pushNotificationTrigger(
            type = "admin_registration_pending",
            title = "New ${kind.name.lowercase().replace('_', ' ')} registration",
            body = "$fixtureUnitName is waiting for approval in ${tournament.name}",
            matchId = "",
            topic = FCM_TOPIC_ADMIN
        )
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

    fun observeMyRegisteredTournamentIds(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("tournamentId") }
                    ?.filter { it.isNotBlank() }
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
     * SIMPLE REAL-TIME QUERY: Get all matches where current user is teamAId or teamBId.
     * This is the CLEAN approach - matches are directly linked to user UIDs.
     * Real-time updates via snapshot listener ensure instant tab switching when admin changes status.
     * 
     * DEBUGGING: Logs all matches found for the user to help diagnose filtering issues.
     */
    fun observeMyRegisteredMatchesRealtime(): Flow<List<com.sportflow.app.data.model.Match>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        android.util.Log.d("MyMatches", "Starting match listener for user: $uid")

        // Listen to ALL matches and filter client-side (Firestore doesn't support OR queries on different fields)
        val matchListener = firestore.collection(MATCHES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MyMatches", "Error listening to matches", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val userMatches = snapshot?.documents?.mapNotNull { doc ->
                    // ALTERNATIVE FIX: Manually construct Match object from Firestore document
                    // This bypasses toObject() deserialization issues
                    try {
                        val match = com.sportflow.app.data.model.Match(
                            id = doc.id,
                            sportType = doc.getString("sportType") ?: "",
                            teamA = doc.getString("teamA") ?: "",
                            teamB = doc.getString("teamB") ?: "",
                            teamADepartment = doc.getString("teamADepartment") ?: "",
                            teamBDepartment = doc.getString("teamBDepartment") ?: "",
                            teamAId = doc.getString("teamAId") ?: "",
                            teamBId = doc.getString("teamBId") ?: "",
                            teamAParticipants = doc.get("teamAParticipants") as? List<String> ?: emptyList(),
                            teamBParticipants = doc.get("teamBParticipants") as? List<String> ?: emptyList(),
                            scoreA = doc.getLong("scoreA")?.toInt() ?: 0,
                            scoreB = doc.getLong("scoreB")?.toInt() ?: 0,
                            wicketsA = doc.getLong("wicketsA")?.toInt() ?: 0,
                            wicketsB = doc.getLong("wicketsB")?.toInt() ?: 0,
                            oversA = doc.getString("oversA") ?: "0.0",
                            oversB = doc.getString("oversB") ?: "0.0",
                            setsWonA = doc.getLong("setsWonA")?.toInt() ?: 0,
                            setsWonB = doc.getLong("setsWonB")?.toInt() ?: 0,
                            currentSetScoreA = doc.getLong("currentSetScoreA")?.toInt() ?: 0,
                            currentSetScoreB = doc.getLong("currentSetScoreB")?.toInt() ?: 0,
                            currentSet = doc.getLong("currentSet")?.toInt() ?: 1,
                            completedSets = doc.getString("completedSets") ?: "",
                            status = try {
                                MatchStatus.valueOf(doc.getString("status") ?: "SCHEDULED")
                            } catch (_: Exception) {
                                MatchStatus.SCHEDULED
                            },
                            venue = doc.getString("venue") ?: "",
                            scheduledTime = doc.getTimestamp("scheduledTime"),
                            currentPeriod = doc.getString("currentPeriod") ?: "",
                            elapsedTime = doc.getString("elapsedTime") ?: "",
                            tournamentId = doc.getString("tournamentId") ?: "",
                            tournamentName = doc.getString("tournamentName") ?: "",
                            round = doc.getString("round") ?: "",
                            winnerId = doc.getString("winnerId") ?: "",
                            eligibilityText = doc.getString("eligibilityText") ?: "All GNITS students",
                            maxRegistrations = doc.getLong("maxRegistrations")?.toInt() ?: 0,
                            registrationCount = doc.getLong("registrationCount")?.toInt() ?: 0
                        )
                        
                        // Check if user is in this match via teamAId, teamBId, or participants arrays
                        val isUserMatch = match.teamAId == uid || 
                                         match.teamBId == uid ||
                                         match.teamAParticipants.contains(uid) ||
                                         match.teamBParticipants.contains(uid)
                        
                        if (isUserMatch) {
                            android.util.Log.d("MyMatches", "Found match: ${match.id} | ${match.teamA} vs ${match.teamB}")
                            android.util.Log.d("MyMatches", "  Status: ${match.status}")
                            android.util.Log.d("MyMatches", "  Sport: ${match.sportType}")
                            android.util.Log.d("MyMatches", "  scoreA: ${match.scoreA}, scoreB: ${match.scoreB}")
                            android.util.Log.d("MyMatches", "  setsWonA: ${match.setsWonA}, setsWonB: ${match.setsWonB}")
                            android.util.Log.d("MyMatches", "  completedSets: ${match.completedSets}")
                            android.util.Log.d("MyMatches", "  winnerId: ${match.winnerId}")
                            match
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MyMatches", "Error parsing match document ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                android.util.Log.d("MyMatches", "Total user matches found: ${userMatches.size}")
                userMatches.forEach { match ->
                    android.util.Log.d("MyMatches", "  - ${match.status}: ${match.teamA} ${match.scoreA} - ${match.scoreB} ${match.teamB}")
                }

                // Sort: live first, then scheduled, then completed
                val sorted = userMatches.sortedWith(
                    compareBy<com.sportflow.app.data.model.Match> {
                        when (it.status) {
                            MatchStatus.LIVE, MatchStatus.HALFTIME -> 0
                            MatchStatus.SCHEDULED -> 1
                            MatchStatus.COMPLETED -> 2
                            MatchStatus.CANCELLED -> 3
                        }
                    }.thenByDescending { it.scheduledTime }
                )
                trySend(sorted)
            }

        awaitClose { matchListener.remove() }
    }

    /**
     * Real-time map of registration statuses for the current user's registered matches.
     * Returns a map of matchId -> RegistrationStatus (PENDING, CONFIRMED, CANCELLED).
     * Used by My Matches screen to display approval status badges.
     */
    fun observeMyRegistrationStatuses(): Flow<Map<String, RegistrationStatus>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val statusMap = snapshot?.documents?.mapNotNull { doc ->
                    val matchId = doc.getString("matchId") ?: return@mapNotNull null
                    val statusStr = doc.getString("status") ?: return@mapNotNull null
                    val status = try {
                        RegistrationStatus.valueOf(statusStr)
                    } catch (_: Exception) {
                        RegistrationStatus.PENDING
                    }
                    matchId to status
                }?.toMap() ?: emptyMap()
                trySend(statusMap)
            }
        awaitClose { listener.remove() }
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
            createAnnouncement(
                title = if (edit.newVenue != null) "Venue Shift" else if (edit.newScheduledTime != null) "Schedule Change" else "Fixture Updated",
                message = if (edit.newVenue != null) "$teamA vs $teamB shifted to $venue." else "$teamA vs $teamB fixture details were updated.",
                category = if (edit.newVenue != null) AnnouncementCategory.VENUE_SHIFT else if (edit.newScheduledTime != null) AnnouncementCategory.SCHEDULE_CHANGE else AnnouncementCategory.FIXTURE_UPDATE,
                matchId = edit.matchId,
                tournamentId = doc.getString("tournamentId") ?: ""
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ADMIN APPROVAL SYSTEM — Accept/Deny registrations
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Real-time stream of all registrations (Admin Data Bridge).
     */
    fun observeAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = firestore.collection(ANNOUNCEMENTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val updates = snapshot?.documents?.mapNotNull {
                    it.toObject(Announcement::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(updates)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createAnnouncement(
        title: String,
        message: String,
        category: AnnouncementCategory = AnnouncementCategory.GENERAL,
        matchId: String = "",
        tournamentId: String = ""
    ): String {
        val data = hashMapOf(
            "title" to title,
            "message" to message,
            "category" to category.name,
            "matchId" to matchId,
            "tournamentId" to tournamentId,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "createdBy" to (auth.currentUser?.uid ?: "")
        )
        val ref = firestore.collection(ANNOUNCEMENTS_COLLECTION).add(data).await()
        pushNotificationTrigger(
            type = "announcement",
            title = title,
            body = message,
            matchId = matchId,
            topic = FCM_TOPIC_ALL
        )
        return ref.id
    }

    fun observeLastUpdatesOpenedAt(): Flow<com.google.firebase.Timestamp?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getTimestamp("lastUpdatesOpenedAt"))
            }
        awaitClose { listener.remove() }
    }

    suspend fun markUpdatesOpened() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .update("lastUpdatesOpenedAt", com.google.firebase.Timestamp.now())
            .await()
    }

    // ── Manual Broadcast Updates (Admin → Player) ────────────────────────────────

    /**
     * Create a manual broadcast update from Admin.
     * Supports global broadcasts (all users) or sport-specific filtering.
     * 
     * @param title Update title
     * @param body Update message body
     * @param targetSport Empty string = All Users, otherwise specific sport name
     */
    suspend fun createBroadcastUpdate(
        title: String,
        body: String,
        targetSport: String = ""
    ): String {
        val data = hashMapOf(
            "title" to title,
            "body" to body,
            "targetSport" to targetSport,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "createdBy" to (auth.currentUser?.uid ?: "")
        )
        val ref = firestore.collection(UPDATES_COLLECTION).add(data).await()
        
        // Send FCM push notification
        val topic = if (targetSport.isBlank()) {
            FCM_TOPIC_ALL
        } else {
            "sport_${targetSport.lowercase().replace(" ", "_")}"
        }
        
        pushNotificationTrigger(
            type = "broadcast_update",
            title = title,
            body = body,
            matchId = "",
            topic = topic
        )
        
        android.util.Log.d("BroadcastUpdate", "Created update: $title | Target: ${if (targetSport.isBlank()) "All Users" else targetSport}")
        
        return ref.id
    }

    /**
     * Observe updates for the current player.
     * Filters to show:
     * - All global updates (targetSport = "")
     * - Sport-specific updates only if user is registered for that sport
     * 
     * Limited to last 20 updates for performance.
     */
    fun observePlayerUpdates(): Flow<List<com.sportflow.app.data.model.Update>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Get user's registered sports
        val userDoc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        val userSports = mutableSetOf<String>()
        
        // Get sports from user's registrations
        val registrations = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("uid", uid)
            .whereEqualTo("status", RegistrationStatus.CONFIRMED.name)
            .get()
            .await()
        
        registrations.documents.forEach { doc ->
            val sport = doc.getString("sportType")
            if (!sport.isNullOrBlank()) {
                userSports.add(sport)
            }
        }
        
        android.util.Log.d("PlayerUpdates", "User registered sports: $userSports")

        val listener = firestore.collection(UPDATES_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)  // Performance: limit to last 20 updates
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("PlayerUpdates", "Error observing updates", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val allUpdates = snapshot?.documents?.mapNotNull {
                    it.toObject(com.sportflow.app.data.model.Update::class.java)?.copy(id = it.id)
                } ?: emptyList()
                
                // Filter: show global updates + sport-specific updates for user's sports
                val filteredUpdates = allUpdates.filter { update ->
                    update.targetSport.isBlank() || userSports.contains(update.targetSport)
                }
                
                android.util.Log.d("PlayerUpdates", "Showing ${filteredUpdates.size} of ${allUpdates.size} updates")
                trySend(filteredUpdates)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe all updates (Admin view - no filtering).
     * Limited to last 20 updates for performance.
     */
    fun observeAllUpdates(): Flow<List<com.sportflow.app.data.model.Update>> = callbackFlow {
        val listener = firestore.collection(UPDATES_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val updates = snapshot?.documents?.mapNotNull {
                    it.toObject(com.sportflow.app.data.model.Update::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(updates)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Delete a broadcast update (Admin only).
     */
    suspend fun deleteBroadcastUpdate(updateId: String) {
        firestore.collection(UPDATES_COLLECTION)
            .document(updateId)
            .delete()
            .await()
        android.util.Log.d("BroadcastUpdate", "Deleted update: $updateId")
    }

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
        val docRef = firestore.collection(GNITS_REGISTRATIONS).document(registrationId)
        val doc = docRef.get().await()
        if (!doc.exists()) return

        val matchId = doc.getString("matchId") ?: ""
        val tournamentId = doc.getString("tournamentId") ?: ""
        val userId = doc.getString("uid") ?: ""
        val matchName = doc.getString("matchName") ?: ""
        val fixtureUnitName = doc.getString("fixtureUnitName")
            ?: doc.getString("teamName")
            ?: doc.getString("userName")
            ?: ""

        docRef
            .update(mapOf(
                "status" to RegistrationStatus.CONFIRMED.name,
                "seen" to true
            ))
            .await()

        if (matchId.isNotBlank() && userId.isNotBlank()) {
            try {
                firestore.collection(MATCHES_COLLECTION)
                    .document(matchId)
                    .collection(REGISTRATIONS_COLLECTION)
                    .document(userId)
                    .update("status", RegistrationStatus.CONFIRMED.name)
                    .await()
            } catch (_: Exception) {}
        }
        if (tournamentId.isNotBlank() && userId.isNotBlank()) {
            try {
                firestore.collection(TOURNAMENTS_COLLECTION)
                    .document(tournamentId)
                    .collection(REGISTRATIONS_COLLECTION)
                    .document(userId)
                    .update("status", RegistrationStatus.CONFIRMED.name)
                    .await()
                if (fixtureUnitName.isNotBlank()) {
                    firestore.collection(TOURNAMENTS_COLLECTION)
                        .document(tournamentId)
                        .update("teams", FieldValue.arrayUnion(fixtureUnitName))
                        .await()
                }
            } catch (_: Exception) {}
        }
        
        pushNotificationTrigger(
            type = "registration_accepted",
            title = "✅ Registration Accepted",
            body = "Your registration for $matchName has been approved!",
            matchId = matchId,
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
        val matchId = regDoc.getString("matchId") ?: ""
        val tournamentId = regDoc.getString("tournamentId") ?: ""
        val matchName = regDoc.getString("matchName") ?: ""

        // Delete from gnits_registrations (Admin Data Bridge)
        firestore.collection(GNITS_REGISTRATIONS)
            .document(registrationId)
            .delete()
            .await()

        // Delete from match sub-collection if it exists
        if (matchId.isNotBlank()) try {
            firestore.collection(MATCHES_COLLECTION)
                .document(matchId)
                .collection(REGISTRATIONS_COLLECTION)
                .document(userId)
                .delete()
                .await()
        } catch (_: Exception) {
            // Non-critical if sub-collection document is already gone
        }
        if (tournamentId.isNotBlank()) try {
            firestore.collection(TOURNAMENTS_COLLECTION)
                .document(tournamentId)
                .collection(REGISTRATIONS_COLLECTION)
                .document(userId)
                .delete()
                .await()
        } catch (_: Exception) {}

        // Decrement counters atomically (floor 0 — read-modify-write)
        val matchRef = if (matchId.isNotBlank()) firestore.collection(MATCHES_COLLECTION).document(matchId) else null
        if (matchRef != null) {
            val matchSnap = matchRef.get().await()
            val curSquad = matchSnap.getLong("currentSquadCount")?.toInt() ?: 0
            val curReg   = matchSnap.getLong("registrationCount")?.toInt() ?: 0
            val updates = mutableMapOf<String, Any>()
            if (curSquad > 0) updates["currentSquadCount"] = FieldValue.increment(-1)
            if (curReg > 0)   updates["registrationCount"] = FieldValue.increment(-1)
            if (updates.isNotEmpty()) matchRef.update(updates).await()
        }

        // Notify student of denial
        pushNotificationTrigger(
            type = "registration_denied",
            title = "❌ Registration Denied",
            body = "Your registration for $matchName was denied. Reason: $reason",
            matchId = matchId,
            topic = "user_$userId"
        )

        // Check if spot just opened in a previously full squad
        val freshSnap = matchRef?.get()?.await()
        val maxSquad = freshSnap?.getLong("maxSquadSize")?.toInt() ?: 0
        val currentCount = freshSnap?.getLong("currentSquadCount")?.toInt() ?: 0
        if (matchRef != null && maxSquad > 0 && currentCount == maxSquad - 1) {
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

    suspend fun generateFixturesFromApprovedRegistrations(matchId: String): Int {
        val seedMatchDoc = firestore.collection(MATCHES_COLLECTION).document(matchId).get().await()
        val seedMatch = seedMatchDoc.toObject(Match::class.java)?.copy(id = seedMatchDoc.id)
            ?: throw IllegalArgumentException("Match not found")

        val approved = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("matchId", matchId)
            .whereEqualTo("status", RegistrationStatus.CONFIRMED.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Registration::class.java)?.copy(id = it.id) }

        val units = if (isPairStyleSport(SportType.fromString(seedMatch.sportType))) {
            val doubles = approved.filter {
                it.registrationKind == RegistrationKind.BADMINTON_DOUBLES &&
                    it.partnerName.isNotBlank() &&
                    it.partnerRollNumber.isNotBlank()
            }
            val singles = approved.filter {
                it.registrationKind == RegistrationKind.BADMINTON_SINGLES ||
                    it.registrationKind == RegistrationKind.INDIVIDUAL
            }
            when {
                doubles.size >= 2 -> doubles
                singles.size >= 2 -> singles
                else -> emptyList()
            }
        } else {
            approved.filter { it.registrationKind == RegistrationKind.TEAM || it.teamName.isNotBlank() }
        }

        if (units.size < 2) {
            throw IllegalArgumentException("Need at least two approved fixture units")
        }

        var created = 0
        units.chunked(2).forEachIndexed { index, pair ->
            if (pair.size < 2) return@forEachIndexed
            val teamA = pair[0].fixtureUnitName.ifBlank { pair[0].teamName.ifBlank { pair[0].userName } }
            val teamB = pair[1].fixtureUnitName.ifBlank { pair[1].teamName.ifBlank { pair[1].userName } }
            val createdMatchId = createMatch(
                seedMatch.copy(
                    id = "",
                    teamA = teamA,
                    teamB = teamB,
                    teamADepartment = pair[0].department,
                    teamBDepartment = pair[1].department,
                    teamAParticipants = listOf(pair[0].uid),
                    teamBParticipants = listOf(pair[1].uid),
                    scoreA = 0,
                    scoreB = 0,
                    status = MatchStatus.SCHEDULED,
                    scheduledTime = com.google.firebase.Timestamp.now(),
                    round = seedMatch.round.ifBlank { "Round ${index + 1}" },
                    registrationCount = 0,
                    currentSquadCount = 0,
                    maxRegistrations = 0,
                    maxSquadSize = 0
                )
            )
            linkRegistrationToGeneratedMatch(createdMatchId, pair[0], seedMatch.sportType, seedMatch.tournamentName, "A")
            linkRegistrationToGeneratedMatch(createdMatchId, pair[1], seedMatch.sportType, seedMatch.tournamentName, "B")
            created++
        }

        return created
    }

    suspend fun generateFixturesFromApprovedTournamentRegistrations(tournamentId: String): Int {
        val tournamentDoc = firestore.collection(TOURNAMENTS_COLLECTION).document(tournamentId).get().await()
        val tournament = tournamentDoc.toObject(Tournament::class.java)?.copy(id = tournamentDoc.id)
            ?: throw IllegalArgumentException("Tournament not found")

        val approved = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("tournamentId", tournamentId)
            .whereEqualTo("status", RegistrationStatus.CONFIRMED.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Registration::class.java)?.copy(id = it.id) }

        val units = if (isPairStyleSport(SportType.fromString(tournament.sport))) {
            val doubles = approved.filter {
                it.registrationKind == RegistrationKind.BADMINTON_DOUBLES &&
                    it.partnerName.isNotBlank() &&
                    it.partnerRollNumber.isNotBlank()
            }
            val singles = approved.filter {
                it.registrationKind == RegistrationKind.BADMINTON_SINGLES ||
                    it.registrationKind == RegistrationKind.INDIVIDUAL
            }
            when {
                doubles.size >= 2 -> doubles
                singles.size >= 2 -> singles
                else -> emptyList()
            }
        } else {
            approved.filter { it.registrationKind == RegistrationKind.TEAM || it.teamName.isNotBlank() }
        }

        if (units.size < 2) {
            throw IllegalArgumentException("Need at least two approved teams or pairs")
        }

        var created = 0
        units.chunked(2).forEachIndexed { index, pair ->
            if (pair.size < 2) return@forEachIndexed
            val teamA = pair[0].fixtureUnitName.ifBlank { pair[0].teamName.ifBlank { pair[0].userName } }
            val teamB = pair[1].fixtureUnitName.ifBlank { pair[1].teamName.ifBlank { pair[1].userName } }
            val createdMatchId = createMatch(
                Match(
                    sportType = tournament.sport,
                    teamA = teamA,
                    teamB = teamB,
                    teamADepartment = pair[0].department,
                    teamBDepartment = pair[1].department,
                    teamAParticipants = listOf(pair[0].uid),
                    teamBParticipants = listOf(pair[1].uid),
                    status = MatchStatus.SCHEDULED,
                    venue = tournament.venue,
                    scheduledTime = com.google.firebase.Timestamp.now(),
                    tournamentId = tournament.id,
                    tournamentName = tournament.name,
                    round = "Round ${index + 1}",
                    eligibilityText = tournament.eligibilityText,
                    allowedDepartments = tournament.allowedDepartments,
                    allowedYears = tournament.allowedYears
                )
            )
            linkRegistrationToGeneratedMatch(createdMatchId, pair[0], tournament.sport, tournament.name, "A")
            linkRegistrationToGeneratedMatch(createdMatchId, pair[1], tournament.sport, tournament.name, "B")
            created++
        }
        return created
    }

    private suspend fun linkRegistrationToGeneratedMatch(
        matchId: String,
        registration: Registration,
        sportType: String,
        tournamentName: String,
        team: String = ""
    ) {
        if (matchId.isBlank() || registration.uid.isBlank()) return

        val matchRegistration = registration.copy(
            matchId = matchId,
            matchName = tournamentName.ifBlank { registration.matchName },
            sportType = sportType,
            status = RegistrationStatus.CONFIRMED
        )

        // Write to match sub-collection registrations
        firestore.collection(MATCHES_COLLECTION)
            .document(matchId)
            .collection(REGISTRATIONS_COLLECTION)
            .document(registration.uid)
            .set(matchRegistration.copy(id = registration.uid))
            .await()

        // Also add this user's UID to the match participants array
        // so observeMyRegisteredMatchesRealtime picks it up reliably
        if (team.isNotBlank()) {
            val participantsField = if (team == "A") "teamAParticipants" else "teamBParticipants"
            firestore.collection(MATCHES_COLLECTION)
                .document(matchId)
                .update(participantsField, FieldValue.arrayUnion(registration.uid))
                .await()
        }

        // Update the bridge collection
        if (registration.id.isNotBlank()) {
            firestore.collection(GNITS_REGISTRATIONS)
                .document(registration.id)
                .set(
                    matchRegistration.copy(id = registration.id),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }
    }

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
            val existing = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_NOTIFICATIONS)
                .whereEqualTo("type", type)
                .whereEqualTo("title", title)
                .whereEqualTo("body", body)
                .whereEqualTo("matchId", matchId)
                .whereEqualTo("seen", false)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) return
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

    fun observeMyRegistrations(): Flow<List<Registration>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Log error but don't close - keep listening
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val registrations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Registration::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null // Skip malformed documents
                    }
                } ?: emptyList()
                trySend(registrations)
            }
        awaitClose { listener.remove() }
    }

    fun observePendingRegistrations(): Flow<List<Registration>> = callbackFlow {
        val listener = firestore.collection(GNITS_REGISTRATIONS)
            .whereEqualTo("status", RegistrationStatus.PENDING.name)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
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
