package com.sportflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sportflow.app.data.service.NotificationManager
import com.sportflow.app.ui.navigation.SportFlowNavHost
import com.sportflow.app.ui.theme.SportFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Injected by Hilt — listens for Firestore match status / registration changes */
    @Inject lateinit var notificationManager: NotificationManager

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result — user either granted or denied. Firebase handles the rest. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+ (API 33+)
        requestNotificationPermissionIfNeeded()

        // Start Firestore real-time listeners for foreground notifications
        // (match status changes, registration confirmations)
        notificationManager.start()

        setContent {
            SportFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SportFlowTheme.colors.background
                ) {
                    SportFlowNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop listeners cleanly — they are re-created on next launch
        notificationManager.stop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (status != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Android 12 and below — push notifications are allowed by default.
    }
}
