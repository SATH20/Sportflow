package com.sportflow.app.data.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.sportflow.app.data.model.MatchStatus
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationManager — Firestore-driven real-time notification bridge
 * with Smart Notification Intelligence.
 *
 * Enhancements over base implementation:
 *   1. **Seen/Unseen Filter**: Only triggers OS notification if the event's
 *      timestamp is newer than the user's lastCheckedTimestamp.
 *   2. **Frequency Capping**: "Spot Available" alerts are limited to once
 *      every 30 minutes per matchId to prevent spam.
 *   3. **Persistence**: Every notification shown is persisted to the user's
 *      notification sub-collection for in-app Notification Center.
 *
 * Lifecycle: Call [start] once the user is authenticated.
 *            Call [stop] on sign-out.
 */
@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val repository: SportFlowRepository
) {

    private val listeners = mutableListOf<ListenerRegistration>()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /** SharedPreferences for frequency capping */
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("gnits_notification_caps", Context.MODE_PRIVATE)
    }

    companion object {
        /** Frequency cap window for "spot_opened" alerts: 30 minutes */
        private const val SPOT_ALERT_CAP_MS = 30 * 60 * 1000L // 30 minutes
    }

    /** Begin listening. Safe to call multiple times — stops previous listeners first. */
    fun start() {
        stop()
        listenToMatchStatusChanges()
        listenToMyRegistrations()
        listenToNotificationTriggers()
    }

    /** Remove all active Firestore listeners. */
    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    // ── 1. Match status changes (SCHEDULED → LIVE, LIVE → COMPLETED) ─────────

    private fun listenToMatchStatusChanges() {
        val listener = firestore.collection(SportFlowRepository.MATCHES_COLLECTION)
            .whereIn("status", listOf(MatchStatus.LIVE.name, MatchStatus.COMPLETED.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val doc       = change.document
                    val status    = doc.getString("status") ?: return@forEach
                    val teamA     = doc.getString("teamA")  ?: "Team A"
                    val teamB     = doc.getString("teamB")  ?: "Team B"
                    val sport     = doc.getString("sportType") ?: "Match"
                    val venue     = doc.getString("venue")  ?: "GNITS"
                    val matchId   = doc.id
                    val eventTime = doc.getTimestamp("scheduledTime")

                    when (status) {
                        MatchStatus.LIVE.name -> showSmartNotification(
                            context     = context,
                            channelId   = GnitsMessagingService.CHANNEL_MATCH_START,
                            title       = "🏁 $sport is LIVE now!",
                            body        = "$teamA vs $teamB kicked off at $venue",
                            matchId     = matchId,
                            type        = "match_start",
                            eventTimestamp = eventTime
                        )
                        MatchStatus.COMPLETED.name -> {
                            val scoreA  = doc.getLong("scoreA")?.toInt() ?: 0
                            val scoreB  = doc.getLong("scoreB")?.toInt() ?: 0
                            val winner  = doc.getString("winnerId") ?: ""
                            val result  = if (winner == "DRAW") "It's a Draw!"
                                          else "🏆 $winner wins!"
                            showSmartNotification(
                                context     = context,
                                channelId   = GnitsMessagingService.CHANNEL_TOURNAMENT,
                                title       = "Full Time · $sport",
                                body        = "$teamA $scoreA – $scoreB $teamB · $result",
                                matchId     = matchId,
                                type        = "match_end",
                                eventTimestamp = eventTime
                            )
                        }
                    }
                }
            }
        listeners.add(listener)
    }

    // ── 2. User's own registrations ──────────────────────────────────────────

    private fun listenToMyRegistrations() {
        val uid = auth.currentUser?.uid ?: return

        val listener = firestore.collectionGroup(SportFlowRepository.REGISTRATIONS_COLLECTION)
            .whereEqualTo("uid", uid)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .limit(1) // only care about the newest
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val doc      = change.document
                    val matchId  = doc.getString("matchId").orEmpty()
                    val status   = doc.getString("status")  ?: return@forEach

                    if (status == "CONFIRMED") {
                        val fixtureUnitName = doc.getString("fixtureUnitName").orEmpty()
                        val teamName = doc.getString("teamName").orEmpty()
                        val tournamentId = doc.getString("tournamentId").orEmpty()
                        val tournamentName = doc.getString("tournamentName").orEmpty()

                        if (matchId.isBlank()) {
                            val entryName = fixtureUnitName.ifBlank { teamName.ifBlank { "your entry" } }
                            val tournamentLabel = tournamentName.ifBlank { "the tournament" }
                            showSmartNotification(
                                context   = context,
                                channelId = GnitsMessagingService.CHANNEL_PAYMENT,
                                title     = "🎫 Registration Confirmed!",
                                body      = "$entryName is confirmed for $tournamentLabel",
                                matchId   = tournamentId,
                                type      = "registration_success"
                            )
                        } else {
                            // Fetch match name for friendly text when this is a direct match registration.
                            firestore.collection(SportFlowRepository.MATCHES_COLLECTION)
                                .document(matchId)
                                .get()
                                .addOnSuccessListener { matchDoc ->
                                    val teamA = matchDoc.getString("teamA") ?: "Match"
                                    val teamB = matchDoc.getString("teamB") ?: ""
                                    val venue = matchDoc.getString("venue") ?: "GNITS"
                                    showSmartNotification(
                                        context   = context,
                                        channelId = GnitsMessagingService.CHANNEL_PAYMENT,
                                        title     = "🎫 Registration Confirmed!",
                                        body      = "You're in for $teamA vs $teamB at $venue",
                                        matchId   = matchId,
                                        type      = "registration_success"
                                    )
                                }
                        }
                    }
                }
            }
        listeners.add(listener)
    }

    // ── 3. Unprocessed notification triggers (created by repository) ──────────

    private fun listenToNotificationTriggers() {
        val listener = firestore.collection(SportFlowRepository.NOTIFICATION_TRIGGERS)
            .whereEqualTo("processed", false)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val doc     = change.document
                    val type    = doc.getString("type")    ?: return@forEach
                    val title   = doc.getString("title")   ?: "GNITS Sports"
                    val body    = doc.getString("body")    ?: ""
                    val matchId = doc.getString("matchId") ?: ""
                    val eventTime = doc.getTimestamp("sentAt")

                    val channelId = when (type) {
                        "score_update"        -> GnitsMessagingService.CHANNEL_LIVE_SCORE
                        "match_start"         -> GnitsMessagingService.CHANNEL_MATCH_START
                        "match_end"           -> GnitsMessagingService.CHANNEL_TOURNAMENT
                        "registration_success"-> GnitsMessagingService.CHANNEL_PAYMENT
                        "spot_opened"         -> GnitsMessagingService.CHANNEL_SQUAD_MGMT
                        "squad_closed"        -> GnitsMessagingService.CHANNEL_SQUAD_MGMT
                        else                  -> GnitsMessagingService.CHANNEL_MATCH_START
                    }

                    // ── Frequency Capping for "spot_opened" ──────────────────
                    if (type == "spot_opened" && isSpotAlertThrottled(matchId)) {
                        // Still mark as processed, but don't show notification
                        doc.reference.update("processed", true)
                        return@forEach
                    }

                    showSmartNotification(context, channelId, title, body, matchId, type, eventTime)

                    // Mark as processed so we don't show it again
                    doc.reference.update("processed", true)

                    // Record timestamp for frequency capping
                    if (type == "spot_opened") {
                        recordSpotAlertTimestamp(matchId)
                    }
                }
            }
        listeners.add(listener)
    }

    // ── Frequency Capping — "Spot Available" throttle ────────────────────────

    /**
     * Check if a "spot_opened" alert for this matchId should be suppressed.
     * Returns true if the last alert was within [SPOT_ALERT_CAP_MS] (30 min).
     */
    private fun isSpotAlertThrottled(matchId: String): Boolean {
        val lastTime = prefs.getLong("last_spot_alert_$matchId", 0L)
        return System.currentTimeMillis() - lastTime < SPOT_ALERT_CAP_MS
    }

    /** Record when the last "spot_opened" notification was shown for a match */
    private fun recordSpotAlertTimestamp(matchId: String) {
        prefs.edit().putLong("last_spot_alert_$matchId", System.currentTimeMillis()).apply()
    }

    // ── Smart Notification — Seen/Unseen Filter + Persistence ────────────────

    /**
     * Shows a notification only if the event is newer than the user's
     * lastCheckedTimestamp (Seen/Unseen filter). Also persists the notification
     * to the user's notification sub-collection for the in-app bell badge.
     */
    private fun showSmartNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        matchId: String,
        type: String,
        eventTimestamp: com.google.firebase.Timestamp? = null
    ) {
        serviceScope.launch {
            try {
                // ── Persist to user's notification sub-collection ────────────
                repository.persistNotification(type, title, body, matchId)

                // ── Seen/Unseen filter: suppress OS notification if already checked ─
                val lastChecked = repository.getLastCheckedTimestamp()
                if (lastChecked != null && eventTimestamp != null) {
                    if (eventTimestamp.seconds <= lastChecked.seconds) {
                        // User already checked after this event — skip OS notification
                        return@launch
                    }
                }

                // ── Show the OS notification ─────────────────────────────────
                showLocalNotification(context, channelId, title, body, matchId)
            } catch (_: Exception) {
                // Fallback: show notification anyway if any smart logic fails
                showLocalNotification(context, channelId, title, body, matchId)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showLocalNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        matchId: String
    ) {
        // Ensure channels exist in case this is called before Service.onCreate
        GnitsMessagingService.createChannels(context)

        val notificationManager = context.getSystemService(
            android.app.NotificationManager::class.java
        )

        val intent = android.content.Intent(context, com.sportflow.app.MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("matchId", matchId)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.sportflow.app.R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
