package com.sportflow.app.data.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.sportflow.app.data.model.UserRole
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val repository: SportFlowRepository
) {

    private val listeners = mutableListOf<ListenerRegistration>()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("gnits_notification_caps", Context.MODE_PRIVATE)
    }

    companion object {
        private const val SPOT_ALERT_CAP_MS = 30 * 60 * 1000L
    }

    fun start() {
        stop()
        listenToNotificationTriggers()
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun listenToNotificationTriggers() {
        val listener = firestore.collection(SportFlowRepository.NOTIFICATION_TRIGGERS)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        serviceScope.launch {
                            handleNotificationTrigger(change.document.id)
                        }
                    }
                }
            }
        listeners.add(listener)
    }

    private suspend fun handleNotificationTrigger(triggerId: String) {
        if (isTriggerHandled(triggerId)) return

        val doc = firestore.collection(SportFlowRepository.NOTIFICATION_TRIGGERS)
            .document(triggerId)
            .get()
            .await()
        if (!doc.exists()) return

        val type = doc.getString("type") ?: return
        val topic = doc.getString("topic").orEmpty()
        val targetUserIds = (doc.get("targetUserIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        if (!shouldHandleNotification(topic, targetUserIds, type)) return

        val title = doc.getString("title") ?: "GNITS Sports"
        val body = doc.getString("body") ?: ""
        val matchId = doc.getString("matchId") ?: ""
        val eventTime = doc.getTimestamp("sentAt")

        if (type == "spot_opened" && isSpotAlertThrottled(matchId)) {
            markTriggerHandled(triggerId)
            return
        }

        val channelId = when (type) {
            "score_update" -> {
                // SILENT: Score updates NEVER generate a push notification.
                // Scores sync via Firestore SnapshotListener for real-time UI only.
                markTriggerHandled(triggerId)
                return
            }
            "match_start" -> GnitsMessagingService.CHANNEL_MATCH_START
            "match_end", "match_scheduled", "fixture_change", "announcement", "tournament_created" ->
                GnitsMessagingService.CHANNEL_TOURNAMENT
            "registration_success", "registration_accepted", "registration_denied" -> GnitsMessagingService.CHANNEL_PAYMENT
            "spot_opened", "squad_closed" -> GnitsMessagingService.CHANNEL_SQUAD_MGMT
            "admin_registration_pending", "tournament_full", "venue_conflict" -> GnitsMessagingService.CHANNEL_ADMIN
            "match_reminder" -> GnitsMessagingService.CHANNEL_MATCH_START
            else -> GnitsMessagingService.CHANNEL_MATCH_START
        }

        markTriggerHandled(triggerId)
        showSmartNotification(context, channelId, title, body, matchId, type, eventTime)
        if (type == "spot_opened") {
            recordSpotAlertTimestamp(matchId)
        }
    }

    private fun isSpotAlertThrottled(matchId: String): Boolean {
        val lastTime = prefs.getLong("last_spot_alert_$matchId", 0L)
        return System.currentTimeMillis() - lastTime < SPOT_ALERT_CAP_MS
    }

    private fun recordSpotAlertTimestamp(matchId: String) {
        prefs.edit().putLong("last_spot_alert_$matchId", System.currentTimeMillis()).apply()
    }

    private fun isTriggerHandled(triggerId: String): Boolean {
        return prefs.getBoolean("handled_trigger_$triggerId", false)
    }

    private fun markTriggerHandled(triggerId: String) {
        prefs.edit().putBoolean("handled_trigger_$triggerId", true).apply()
    }

    private suspend fun shouldHandleNotification(
        topic: String,
        targetUserIds: List<String>,
        type: String
    ): Boolean {
        val currentUser = auth.currentUser ?: return false
        val profile = repository.getCurrentUserProfile()
        val role = profile?.role ?: UserRole.PLAYER

        if (!isTypeAllowedForRole(type, role)) return false
        if (targetUserIds.contains(currentUser.uid)) return true

        return when {
            topic.isBlank() -> false
            topic == SportFlowRepository.FCM_TOPIC_GENERAL || topic == SportFlowRepository.FCM_TOPIC_ALL ->
                role != UserRole.ADMIN
            topic == SportFlowRepository.FCM_TOPIC_ADMIN ->
                role == UserRole.ADMIN
            topic == "user_${currentUser.uid}" -> true
            topic.startsWith("tournament_") ->
                role != UserRole.ADMIN &&
                    repository.getApprovedTournamentIdsForCurrentUser()
                        .contains(topic.removePrefix("tournament_"))
            topic.startsWith("dept_") ->
                profile?.department?.let { "dept_$it" == topic } == true
            else -> false
        }
    }

    private fun isTypeAllowedForRole(type: String, role: UserRole): Boolean {
        // Score updates are handled via SnapshotListener only — never generate push
        if (type == "score_update") return false

        val adminOnly = setOf("admin_registration_pending", "tournament_full", "venue_conflict")
        val playerOnly = setOf(
            "registration_success",
            "registration_accepted",
            "registration_denied",
            "match_scheduled",
            "match_reminder",
            "match_start",
            "match_end",
            "tournament_created",
            "fixture_change",
            "announcement",
            "spot_opened",
            "squad_closed"
        )
        return when (role) {
            UserRole.ADMIN -> type in adminOnly
            else -> type in playerOnly
        }
    }

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
                repository.persistNotification(type, title, body, matchId)

                val lastChecked = repository.getLastCheckedTimestamp()
                if (lastChecked != null && eventTimestamp != null) {
                    if (eventTimestamp.seconds <= lastChecked.seconds) {
                        return@launch
                    }
                }

                showLocalNotification(context, channelId, title, body, matchId)
            } catch (_: Exception) {
                showLocalNotification(context, channelId, title, body, matchId)
            }
        }
    }

    private fun showLocalNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        matchId: String
    ) {
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
