package com.altron.alertbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.ui.theme.navigation.AlertBuddyNavigation
import com.altron.alertbuddy.ui.theme.AlertBuddyTheme
import kotlinx.coroutines.launch

// Main entry point for Alert Buddy app
class MainActivity : ComponentActivity() {

    private lateinit var repository: AlertRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize data repository
        repository = AlertRepository(applicationContext)

        setContent {
            // Track login state: null = checking, true = logged in, false = not logged in
            var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            // Check if user is already logged in on app start
            LaunchedEffect(Unit) {
                val user = repository.getCurrentUser()
                isLoggedIn = user != null
            }

            AlertBuddyTheme {
                // Main container with status bar padding to avoid overlap
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Show navigation once login state is determined
                    if (isLoggedIn != null) {
                        AlertBuddyNavigation(
                            repository = repository,
                            isLoggedIn = isLoggedIn!!,
                            onLoginSuccess = { isLoggedIn = true },
                            onLogout = { isLoggedIn = false }
                        )
                    }
                }
            }
        }
    }
}