package com.sportflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sportflow.app.ui.navigation.SportFlowNavHost
import com.sportflow.app.ui.theme.SportFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
