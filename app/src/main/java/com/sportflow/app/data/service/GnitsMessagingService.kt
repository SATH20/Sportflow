package com.sportflow.app.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sportflow.app.MainActivity
import com.sportflow.app.R

class GnitsMessagingService : FirebaseMessagingService() {

    companion object {
        // Channel IDs — one per category so users can control each independently
        const val CHANNEL_LIVE_SCORE  = "gnits_live_scores"
        const val CHANNEL_MATCH_START = "gnits_match_start"
        const val CHANNEL_TOURNAMENT  = "gnits_tournament"
        const val CHANNEL_PAYMENT     = "gnits_payment"
        const val CHANNEL_SQUAD_MGMT  = "gnits_squad_management"

        /**
         * Create all notification channels. Call this once from Application.onCreate()
         * and also here in onMessageReceived to guarantee channels exist.
         */
        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            data class Ch(val id: String, val name: String, val desc: String, val importance: Int)

            listOf(
                Ch(CHANNEL_LIVE_SCORE,
                    "⚽ Live Score Updates",
                    "Notified on every goal or point scored during a live GNITS match",
                    NotificationManager.IMPORTANCE_HIGH),
                Ch(CHANNEL_MATCH_START,
                    "🏁 Match Start Alerts",
                    "Notified when a scheduled match goes live",
                    NotificationManager.IMPORTANCE_HIGH),
                Ch(CHANNEL_TOURNAMENT,
                    "🏆 Tournament Announcements",
                    "Bracket draws, new fixtures and results",
                    NotificationManager.IMPORTANCE_DEFAULT),
                Ch(CHANNEL_PAYMENT,
                    "💳 Payment Verification",
                    "Status updates on your tournament registration payment",
                    NotificationManager.IMPORTANCE_HIGH),
                Ch(CHANNEL_SQUAD_MGMT,
                    "🔐 Squad Slot Alerts",
                    "Notified when a squad fills up or a spot opens. Register fast when a slot opens!",
                    NotificationManager.IMPORTANCE_HIGH)
            ).forEach { ch ->
                if (manager.getNotificationChannel(ch.id) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(ch.id, ch.name, ch.importance).apply {
                            description = ch.desc
                            enableVibration(true)
                        }
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    /** FCM sends this when the device gets a new registration token */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token refresh — Firebase handles this automatically;
        // if you have a custom backend you'd send it there here.
    }

    /**
     * Handles incoming FCM data messages.
     *
     * Expected data payload keys:
     *   type        → "score_update" | "match_start" | "match_end" | "tournament" | "payment"
     *   title       → Notification title (fallback to notification.title)
     *   body        → Notification body  (fallback to notification.body)
     *   matchId     → (optional) ID of the relevant match
     *   teamA / teamB / scoreA / scoreB → (optional) for score updates
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val title  = data["title"]  ?: notif?.title  ?: "GNITS Sports"
        val body   = data["body"]   ?: notif?.body   ?: "You have a new update"
        val type   = data["type"]   ?: "general"
        val matchId = data["matchId"]

        val channelId = when (type) {
            "score_update"  -> CHANNEL_LIVE_SCORE
            "match_start"   -> CHANNEL_MATCH_START
            "match_end",
            "tournament"    -> CHANNEL_TOURNAMENT
            "squad_closed",
            "spot_opened",
            "registration_success" -> CHANNEL_SQUAD_MGMT
            "payment"       -> CHANNEL_PAYMENT
            else            -> CHANNEL_MATCH_START
        }

        showNotification(title, body, channelId, matchId)
    }

    private fun showNotification(
        title: String,
        body: String,
        channelId: String,
        matchId: String?
    ) {
        // Deep-link intent — opens the app and passes matchId if available
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            matchId?.let { putExtra("matchId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
