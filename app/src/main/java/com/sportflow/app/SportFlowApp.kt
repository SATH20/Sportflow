package com.sportflow.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sportflow.app.data.service.GnitsMessagingService
import com.sportflow.app.data.service.MatchReminderWorker
import com.sportflow.app.data.service.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SportFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Create all FCM notification channels immediately on app launch
        // so they appear in Android Settings even before the first notification arrives
        GnitsMessagingService.createChannels(this)
        EntryPointAccessors.fromApplication(
            this,
            NotificationManagerEntryPoint::class.java
        ).notificationManager().start()

        // Schedule periodic 1-hour match reminder checks via WorkManager
        // Runs every 15 minutes to catch upcoming matches within the reminder window
        val reminderWork = PeriodicWorkRequestBuilder<MatchReminderWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MatchReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            reminderWork
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationManagerEntryPoint {
    fun notificationManager(): NotificationManager
}

