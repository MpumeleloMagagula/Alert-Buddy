package com.altron.alertbuddy.ui.theme.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.User
import com.altron.alertbuddy.service.AlertService
import com.altron.alertbuddy.ui.theme.AlertBuddyColors
import com.altron.alertbuddy.ui.theme.ThemeMode
import com.altron.alertbuddy.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * SettingsScreen.kt - User Profile and App Settings
 * ============================================================================
 *
 * PURPOSE:
 * Comprehensive settings screen for user profile management and app configuration.
 *
 * FEATURES:
 * - Profile section with avatar (initials-based), name, position
 * - Account settings (email, 2FA toggle)
 * - Notification settings (beep interval, vibration)
 * - Data management (reset demo data)
 * - App info (version, company)
 * - Logout functionality
 *
 * SECTIONS:
 * 1. Profile Card - Avatar, display name, position, department
 * 2. Account - Email, 2FA settings, Sign Out
 * 3. Notifications - Beep interval, vibration toggle
 * 4. Data - Reset demo data
 * 5. About - Version, company info
 *
 * SIGN OUT PROCESS:
 * 1. Stop AlertService - Stops beeping immediately
 * 2. Clear SharedPreferences - Prevents BootReceiver restart
 * 3. Clear database - Removes user record
 * 4. Navigate to login screen
 *
 * ============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AlertRepository,  // Repository for database operations
    onBackClick: () -> Unit,      // Callback to navigate back
    onLogout: () -> Unit,         // Callback to navigate to login screen
    onHistoryClick: () -> Unit = {},  // Callback to navigate to alert history
    onThemeChanged: (ThemeMode) -> Unit = {}  // Callback when theme is changed
) {
    // ========================================================================
    // STATE VARIABLES
    // ========================================================================

    // User data loaded from database
    var user by remember { mutableStateOf<User?>(null) }

    // Dialog visibility states
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var show2FADialog by remember { mutableStateOf(false) }
    var showBeepIntervalDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }

    // Loading state
    var isLoggingOut by remember { mutableStateOf(false) }

    // Local state for toggles (synced with user data)
    var vibrationEnabled by remember { mutableStateOf(true) }
    var beepInterval by remember { mutableStateOf(60) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Theme mode state (must be after context is defined)
    var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(context)) }

    // ========================================================================
    // LOAD USER DATA
    // ========================================================================
    LaunchedEffect(Unit) {
        user = repository.getCurrentUser()
        user?.let {
            vibrationEnabled = it.vibrationEnabled
            beepInterval = it.beepIntervalSeconds
        }
    }

    // ========================================================================
    // LOGOUT CONFIRMATION DIALOG
    // ========================================================================
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? You will stop receiving alert notifications.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isLoggingOut = true

                            // STEP 1: Stop AlertService
                            AlertService.stopService(context)

                            // STEP 2: Clear SharedPreferences
                            val prefs = context.getSharedPreferences(
                                "alert_buddy_prefs",
                                Context.MODE_PRIVATE
                            )
                            prefs.edit().putBoolean("is_logged_in", false).apply()

                            // STEP 3: Sign out from repository
                            repository.signOut()

                            isLoggingOut = false
                            showLogoutDialog = false

                            // STEP 4: Navigate to login
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
                        Text("Sign Out", color = AlertBuddyColors.Error)
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

    // ========================================================================
    // RESET DEMO DATA DIALOG
    // ========================================================================
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Demo Data") },
            text = { Text("This will reset all alerts to their initial state. Your profile and settings will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.initializeDemoData()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = AlertBuddyColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ========================================================================
    // EDIT PROFILE DIALOG
    // ========================================================================
    if (showEditProfileDialog) {
        EditProfileDialog(
            user = user,
            onDismiss = { showEditProfileDialog = false },
            onSave = { displayName, position, department ->
                scope.launch {
                    user?.let {
                        repository.updateUserProfile(
                            userId = it.id,
                            displayName = displayName,
                            position = position,
                            department = department
                        )
                        user = repository.getCurrentUser()
                    }
                    showEditProfileDialog = false
                }
            }
        )
    }

    // ========================================================================
    // 2FA SETUP DIALOG
    // ========================================================================
    if (show2FADialog) {
        TwoFactorAuthDialog(
            isEnabled = user?.is2FAEnabled ?: false,
            onDismiss = { show2FADialog = false },
            onToggle2FA = { enable ->
                scope.launch {
                    user?.let {
                        repository.toggle2FA(it.id, enable)
                        user = repository.getCurrentUser()
                    }
                    show2FADialog = false
                }
            }
        )
    }

    // ========================================================================
    // BEEP INTERVAL SELECTION DIALOG
    // ========================================================================
    if (showBeepIntervalDialog) {
        BeepIntervalDialog(
            currentInterval = beepInterval,
            onDismiss = { showBeepIntervalDialog = false },
            onSelect = { interval ->
                scope.launch {
                    beepInterval = interval
                    user?.let {
                        repository.updateBeepInterval(it.id, interval)
                        user = repository.getCurrentUser()
                    }
                    // Notify AlertService to refresh settings immediately
                    AlertService.refreshSettings(context)
                    showBeepIntervalDialog = false
                }
            }
        )
    }

    // ========================================================================
    // THEME MODE SELECTION DIALOG
    // ========================================================================
    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeModeDialog = false },
            onSelect = { mode ->
                themeMode = mode
                ThemePreferences.setThemeMode(context, mode)
                showThemeModeDialog = false
                // Notify MainActivity to update theme immediately
                onThemeChanged(mode)
            }
        )
    }

    // ========================================================================
    // MAIN UI
    // ========================================================================
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
            // ----------------------------------------------------------------
            // PROFILE CARD SECTION
            // Shows avatar with initials, name, position, department
            // ----------------------------------------------------------------
            ProfileCard(
                user = user,
                onEditClick = { showEditProfileDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // ACCOUNT SECTION
            // Email, 2FA, Sign Out
            // ----------------------------------------------------------------
            SettingsSection(title = "Account") {
                // Email row
                SettingsRow(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = user?.email ?: "Not signed in"
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))

                // Two-Factor Authentication row
                SettingsRow(
                    icon = Icons.Default.Lock,
                    label = "Two-Factor Authentication",
                    value = if (user?.is2FAEnabled == true) "Enabled" else "Disabled",
                    showChevron = true,
                    onClick = { show2FADialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))

                // Sign Out row
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Sign Out",
                    isDestructive = true,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // NOTIFICATIONS SECTION
            // Beep interval and vibration settings
            // ----------------------------------------------------------------
            SettingsSection(title = "Notifications") {
                // Beep interval row
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    label = "Beep Interval",
                    value = formatBeepInterval(beepInterval),
                    showChevron = true,
                    onClick = { showBeepIntervalDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))

                // Vibration toggle row
                SettingsRowWithSwitch(
                    icon = Icons.Default.Phone,
                    label = "Vibration",
                    checked = vibrationEnabled,
                    onCheckedChange = { enabled ->
                        vibrationEnabled = enabled
                        scope.launch {
                            user?.let {
                                repository.updateVibrationSetting(it.id, enabled)
                            }
                            // Notify AlertService to refresh settings immediately
                            AlertService.refreshSettings(context)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // APPEARANCE SECTION
            // Dark mode / theme settings
            // ----------------------------------------------------------------
            SettingsSection(title = "Appearance") {
                SettingsRow(
                    icon = when (themeMode) {
                        ThemeMode.DARK -> Icons.Default.Star
                        ThemeMode.LIGHT -> Icons.Default.Face
                        ThemeMode.SYSTEM -> Icons.Default.Settings
                    },
                    label = "Theme",
                    value = when (themeMode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    showChevron = true,
                    onClick = { showThemeModeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // DATA SECTION
            // Alert history and reset demo data
            // ----------------------------------------------------------------
            SettingsSection(title = "Data") {
                SettingsRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Alert History",
                    value = "View acknowledged alerts",
                    showChevron = true,
                    onClick = onHistoryClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    label = "Reset Demo Data",
                    showChevron = true,
                    onClick = { showResetDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // ABOUT SECTION
            // Version and company info
            // ----------------------------------------------------------------
            SettingsSection(title = "About") {
                SettingsRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = "1.0.0 (Build 1)"
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                SettingsRow(
                    icon = Icons.Default.Star,
                    label = "Company",
                    value = "Altron Digital"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------------------
            // FOOTER
            // ----------------------------------------------------------------
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
 * ============================================================================
 * ProfileCard - User profile display with avatar
 * ============================================================================
 * Shows:
 * - Initials-based avatar (generated from name or email)
 * - Display name
 * - Position/job title
 * - Department
 * - Edit button
 */
@Composable
private fun ProfileCard(
    user: User?,
    onEditClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar - Initials-based (no profile picture upload yet)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(user?.displayName ?: user?.email),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User Info Column
            Column(modifier = Modifier.weight(1f)) {
                // Display name (or email username if no display name)
                Text(
                    text = user?.displayName ?: user?.email?.substringBefore("@") ?: "User",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Position/Job Title
                if (user?.position != null) {
                    Text(
                        text = user.position,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                // Department
                if (user?.department != null) {
                    Text(
                        text = user.department,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            // Edit Button
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * ============================================================================
 * EditProfileDialog - Dialog for editing user profile
 * ============================================================================
 * Allows user to edit:
 * - Display name
 * - Position/job title
 * - Department
 */
@Composable
private fun EditProfileDialog(
    user: User?,
    onDismiss: () -> Unit,
    onSave: (displayName: String?, position: String?, department: String?) -> Unit
) {
    var displayName by remember { mutableStateOf(user?.displayName ?: "") }
    var position by remember { mutableStateOf(user?.position ?: "") }
    var department by remember { mutableStateOf(user?.department ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Position / Job Title") },
                    placeholder = { Text("e.g., Senior DevOps Engineer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department") },
                    placeholder = { Text("e.g., Infrastructure, Support") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        displayName.ifBlank { null },
                        position.ifBlank { null },
                        department.ifBlank { null }
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * ============================================================================
 * TwoFactorAuthDialog - Dialog for 2FA toggle
 * ============================================================================
 * Shows information about 2FA and allows enabling/disabling.
 * Note: Full TOTP implementation is for future enhancement.
 */
@Composable
private fun TwoFactorAuthDialog(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggle2FA: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Two-Factor Authentication") },
        text = {
            Column {
                if (isEnabled) {
                    Text("Two-factor authentication is currently enabled. This adds an extra layer of security to your account.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Disabling 2FA will make your account less secure.",
                        color = AlertBuddyColors.Error
                    )
                } else {
                    Text("Enable two-factor authentication to add an extra layer of security to your account.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "You will need to enter a verification code from your authenticator app when signing in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onToggle2FA(!isEnabled) }) {
                Text(
                    if (isEnabled) "Disable 2FA" else "Enable 2FA",
                    color = if (isEnabled) AlertBuddyColors.Error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * ============================================================================
 * BeepIntervalDialog - Dialog for selecting beep interval
 * ============================================================================
 * Options:
 * - 30 seconds
 * - 1 minute (default)
 * - 2 minutes
 * - 5 minutes
 */
@Composable
private fun BeepIntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val intervals = listOf(
        30 to "30 seconds",
        60 to "1 minute",
        120 to "2 minutes",
        300 to "5 minutes"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beep Interval") },
        text = {
            Column {
                Text(
                    "How often should Alert Buddy beep when there are unread alerts?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                intervals.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentInterval == seconds,
                            onClick = { onSelect(seconds) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * ============================================================================
 * ThemeModeDialog - Dialog for selecting theme mode
 * ============================================================================
 * Options:
 * - System (follows device setting)
 * - Light (always light)
 * - Dark (always dark)
 */
@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    val modes = listOf(
        ThemeMode.SYSTEM to "System default" to "Follow device dark mode setting",
        ThemeMode.LIGHT to "Light" to "Always use light theme",
        ThemeMode.DARK to "Dark" to "Always use dark theme"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                Text(
                    "Choose your preferred theme:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // System option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ThemeMode.SYSTEM) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == ThemeMode.SYSTEM,
                        onClick = { onSelect(ThemeMode.SYSTEM) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("System default")
                        Text(
                            "Follow device dark mode setting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Light option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ThemeMode.LIGHT) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == ThemeMode.LIGHT,
                        onClick = { onSelect(ThemeMode.LIGHT) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Light")
                        Text(
                            "Always use light theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dark option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ThemeMode.DARK) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == ThemeMode.DARK,
                        onClick = { onSelect(ThemeMode.DARK) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Dark")
                        Text(
                            "Always use dark theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * ============================================================================
 * SettingsSection - Container for grouping settings
 * ============================================================================
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
 * ============================================================================
 * SettingsRow - Single settings item
 * ============================================================================
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
    val contentColor = if (isDestructive)
        AlertBuddyColors.Error
    else
        MaterialTheme.colorScheme.onSurface

    val iconColor = if (isDestructive)
        AlertBuddyColors.Error
    else
        MaterialTheme.colorScheme.onSurfaceVariant

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

/**
 * ============================================================================
 * SettingsRowWithSwitch - Settings item with toggle switch
 * ============================================================================
 */
@Composable
private fun SettingsRowWithSwitch(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * ============================================================================
 * Helper Functions
 * ============================================================================
 */

/**
 * Get initials from name or email.
 * Examples:
 * - "John Doe" -> "JD"
 * - "john.doe@altron.com" -> "JD"
 * - "john" -> "JO"
 */
private fun getInitials(nameOrEmail: String?): String {
    if (nameOrEmail == null) return "?"

    // If it's an email, use the part before @
    val name = if (nameOrEmail.contains("@")) {
        nameOrEmail.substringBefore("@")
    } else {
        nameOrEmail
    }

    // Split by spaces or dots and get first letters
    val parts = name.split(Regex("[\\s.]+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

/**
 * Format beep interval for display.
 */
private fun formatBeepInterval(seconds: Int): String {
    return when (seconds) {
        30 -> "30 seconds"
        60 -> "1 minute"
        120 -> "2 minutes"
        300 -> "5 minutes"
        else -> "$seconds seconds"
    }
}
