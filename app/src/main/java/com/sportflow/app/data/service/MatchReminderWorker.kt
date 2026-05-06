package com.sportflow.app.data.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.sportflow.app.data.model.MatchStatus
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Background Worker: 1-Hour Match Reminder
 *
 * Runs periodically (every 15 minutes via WorkManager) and checks all SCHEDULED matches.
 * If any match's scheduledTime is exactly 1 hour away (within a 2-minute window),
 * and the reminderNotifSent flag is false, dispatches personalized reminder notifications
 * to both teams' participants.
 *
 * This is the ONLY way the 1-hour reminder fires — it is never triggered by UI actions.
 * The dedup guard (reminderNotifSent) ensures at most one reminder per match.
 */
@HiltWorker
class MatchReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SportFlowRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MatchReminderWorker"
        const val UNIQUE_WORK_NAME = "match_reminder_check"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting 1-hour reminder check...")

            val firestore = FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            val oneHourMs = 60 * 60 * 1000L
            val windowMs = 2 * 60 * 1000L // 2-minute tolerance window

            // Query all SCHEDULED matches
            val scheduled = firestore.collection(SportFlowRepository.MATCHES_COLLECTION)
                .whereEqualTo("status", MatchStatus.SCHEDULED.name)
                .get()
                .await()

            var remindersSent = 0

            scheduled.documents.forEach { doc ->
                val matchId = doc.id
                val reminderSent = doc.getBoolean("reminderNotifSent") ?: false
                val scheduledTime = doc.getTimestamp("scheduledTime")

                if (reminderSent || scheduledTime == null) return@forEach

                val millisUntilMatch = scheduledTime.toDate().time - now
                val isInWindow = millisUntilMatch in (oneHourMs - windowMs)..oneHourMs

                if (isInWindow) {
                    try {
                        repository.dispatchMatchReminderIfDue(matchId)
                        remindersSent++
                        Log.d(TAG, "✅ Dispatched 1-hour reminder for match $matchId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to dispatch reminder for $matchId: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Reminder check complete. $remindersSent reminder(s) sent.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
