package com.sportflow.app

import android.app.Application
import com.sportflow.app.data.service.GnitsMessagingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SportFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create all FCM notification channels immediately on app launch
        // so they appear in Android Settings even before the first notification arrives
        GnitsMessagingService.createChannels(this)
    }
}
