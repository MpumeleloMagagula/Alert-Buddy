package com.altron.alertbuddy.ui.theme.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.User
import com.altron.alertbuddy.service.AlertService
import kotlinx.coroutines.launch
import androidx.compose.material3.*


// Color constants for error/destructive actions
private val ErrorRed = Color(0xFFDC2626)

/**
 * Settings screen for app configuration and user account management.
 * Provides options to view user info, reset demo data, and sign out.
 * Sign out properly clears SharedPreferences, stops AlertService, and clears user data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AlertRepository,
    onBackClick: () -> Unit,
    onLogout: () -> Unit
) {
    // State for current user and dialog visibility
    var user by remember { mutableStateOf<User?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load current user on screen launch
    LaunchedEffect(Unit) {
        user = repository.getCurrentUser()
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isLoggingOut = true

                            // Step 1: Stop the alert service to stop beeping
                            AlertService.stopService(context)

                            // Step 2: Clear login state from SharedPreferences
                            // This prevents BootReceiver from starting service on device restart
                            val prefs = context.getSharedPreferences("alert_buddy_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("is_logged_in", false).apply()

                            // Step 3: Sign out from repository (clears user from database)
                            repository.signOut()

                            isLoggingOut = false
                            showLogoutDialog = false

                            // Step 4: Navigate back to login screen
                            onLogout()
                        }
                    },
                    enabled = !isLoggingOut
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign Out", color = ErrorRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset data confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Demo Data") },
            text = { Text("This will reset all alerts to their initial state. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.initializeDemoData()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Account Section
            SettingsSection(title = "Account") {
                SettingsRow(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = user?.email ?: "Not signed in"
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Sign Out",
                    isDestructive = true,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Data Section
            SettingsSection(title = "Data") {
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    label = "Reset Demo Data",
                    showChevron = true,
                    onClick = { showResetDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            SettingsSection(title = "About") {
                SettingsRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = "1.0.0"
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                SettingsRow(
                    icon = Icons.Filled.Build,
                    label = "Company",
                    value = "Altron Digital"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Alert Buddy",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Critical alerts for Altron teams",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A section container for grouping related settings items.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            letterSpacing = 0.5.sp
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

/**
 * A single settings row item with icon, label, optional value, and optional click action.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    isDestructive: Boolean = false,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    // Use red color for destructive actions like Sign Out
    val contentColor = if (isDestructive) ErrorRed else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}