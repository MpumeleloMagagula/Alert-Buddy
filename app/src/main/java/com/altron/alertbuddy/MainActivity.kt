package com.altron.alertbuddy

import android.os.Bundle
import android.util.Log
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

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var repository: AlertRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        enableEdgeToEdge()

        try {
            repository = AlertRepository(applicationContext)
            Log.d(TAG, "Repository initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
        }

        setContent {
            var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                try {
                    val user = repository.getCurrentUser()
                    isLoggedIn = user != null
                    Log.d(TAG, "Login check complete: isLoggedIn = $isLoggedIn")
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking login state", e)
                    isLoggedIn = false
                }
            }

            AlertBuddyTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    if (isLoggedIn != null) {
                        Log.d(TAG, "Showing navigation, isLoggedIn = $isLoggedIn")
                        AlertBuddyNavigation(
                            repository = repository,
                            isLoggedIn = isLoggedIn!!,
                            onLoginSuccess = { isLoggedIn = true },
                            onLogout = { isLoggedIn = false }
                        )

                    }  }
                }
            }
    }
}