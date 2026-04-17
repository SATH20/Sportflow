package com.sportflow.app.data.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.sportflow.app.data.model.MatchStatus
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationManager — Firestore-driven real-time notification bridge.
 *
 * Responsibility:
 *   1. Listens for [MatchStatus] transitions on LIVE matches → triggers visible
 *      status-change notifications on-device while the app is in the foreground.
 *   2. Listens for new documents in the user's registration sub-collection →
 *      confirms registration success notifications.
 *
 * FCM handles background push via Cloud Functions reading the
 * gnits_notification_triggers collection. This manager adds foreground awareness
 * so users see immediate in-app feedback even without FCM delivery.
 *
 * Lifecycle: Call [start] once the user is authenticated (e.g. in MainActivity
 * or a Hilt-injected Application class). Call [stop] on sign-out.
 */
@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val listeners = mutableListOf<ListenerRegistration>()

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

                    when (status) {
                        MatchStatus.LIVE.name -> showLocalNotification(
                            context     = context,
                            channelId   = GnitsMessagingService.CHANNEL_MATCH_START,
                            title       = "🏁 $sport is LIVE now!",
                            body        = "$teamA vs $teamB kicked off at $venue",
                            matchId     = matchId
                        )
                        MatchStatus.COMPLETED.name -> {
                            val scoreA  = doc.getLong("scoreA")?.toInt() ?: 0
                            val scoreB  = doc.getLong("scoreB")?.toInt() ?: 0
                            val winner  = doc.getString("winnerId") ?: ""
                            val result  = if (winner == "DRAW") "It's a Draw!"
                                          else "🏆 $winner wins!"
                            showLocalNotification(
                                context     = context,
                                channelId   = GnitsMessagingService.CHANNEL_TOURNAMENT,
                                title       = "Full Time · $sport",
                                body        = "$teamA $scoreA – $scoreB $teamB · $result",
                                matchId     = matchId
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
                    val matchId  = doc.getString("matchId") ?: return@forEach
                    val status   = doc.getString("status")  ?: return@forEach

                    if (status == "CONFIRMED") {
                        // Fetch match name for friendly text
                        firestore.collection(SportFlowRepository.MATCHES_COLLECTION)
                            .document(matchId).get()
                            .addOnSuccessListener { matchDoc ->
                                val teamA = matchDoc.getString("teamA") ?: "Match"
                                val teamB = matchDoc.getString("teamB") ?: ""
                                val venue = matchDoc.getString("venue") ?: "GNITS"
                                showLocalNotification(
                                    context   = context,
                                    channelId = GnitsMessagingService.CHANNEL_PAYMENT,
                                    title     = "🎫 Registration Confirmed!",
                                    body      = "You're in for $teamA vs $teamB at $venue",
                                    matchId   = matchId
                                )
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

                    val channelId = when (type) {
                        "score_update"        -> GnitsMessagingService.CHANNEL_LIVE_SCORE
                        "match_start"         -> GnitsMessagingService.CHANNEL_MATCH_START
                        "match_end"           -> GnitsMessagingService.CHANNEL_TOURNAMENT
                        "registration_success"-> GnitsMessagingService.CHANNEL_PAYMENT
                        else                  -> GnitsMessagingService.CHANNEL_MATCH_START
                    }

                    showLocalNotification(context, channelId, title, body, matchId)

                    // Mark as processed so we don't show it again
                    doc.reference.update("processed", true)
                }
            }
        listeners.add(listener)
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
