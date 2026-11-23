package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.ThemePreferences
import com.example.ui.StarlightApp
import com.example.ui.theme.StarlightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { ThemePreferences(applicationContext) }
            val darkMode by prefs.darkModeFlow.collectAsStateWithLifecycle(initialValue = true)
            val notificationsEnabled by prefs.notificationsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
            StarlightTheme(darkTheme = darkMode) {
                StarlightApp(
                    darkMode = darkMode,
                    notificationsEnabled = notificationsEnabled,
                    themePreferences = prefs
                )
            }
        }
    }
}
