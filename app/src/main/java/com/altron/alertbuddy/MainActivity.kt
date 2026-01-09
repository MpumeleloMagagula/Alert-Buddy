package com.altron.alertbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.ui.theme.navigation.AlertBuddyNavigation
import com.altron.alertbuddy.ui.theme.AlertBuddyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: AlertRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = AlertRepository(applicationContext)

        setContent {
            var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val user = repository.getCurrentUser()
                isLoggedIn = user != null
            }

            AlertBuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
