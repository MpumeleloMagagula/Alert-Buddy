package com.altron.alertbuddy.ui.theme.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altron.alertbuddy.R
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.service.AlertService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * LoginScreen.kt - User Authentication Screen
 * ============================================================================
 *
 * PURPOSE:
 * This is the first screen users see when the app launches (if not logged in).
 * It handles email/password authentication for Altron Digital employees.
 *
 * FEATURES:
 * - Email input with validation
 * - Password input with show/hide toggle
 * - Loading state during authentication
 * - Error message display for invalid credentials
 * - Domain verification (optional - for restricting to @altron.com emails)
 *
 * AUTHENTICATION FLOW:
 * 1. User enters email and password
 * 2. Form validation checks email format and password length
 * 3. On submit: Signs in user via repository, saves login state to SharedPreferences
 * 4. Starts AlertService to begin monitoring for unread alerts
 * 5. Navigates to ChannelListScreen (home)
 *
 * SHARED PREFERENCES:
 * - Key: "is_logged_in" (Boolean)
 * - Used by BootReceiver to restart AlertService after device restart
 * - Cleared on logout in SettingsScreen
 *
 * DOMAIN VERIFICATION (PRODUCTION):
 * To restrict access to Altron employees only, uncomment the domain
 * verification block in the sign-in logic. This will only allow emails
 * ending with @altron.com, @altrondigital.com, or @altron.co.za
 *
 * ============================================================================
 */
@Composable
fun LoginScreen(
    repository: AlertRepository,    // Repository for database operations
    onLoginSuccess: () -> Unit      // Callback to navigate to home screen
) {
    // ========================================================================
    // FORM STATE
    // ========================================================================
    // These remember blocks hold the current values of the input fields.
    // They survive recomposition but reset when the screen is disposed.

    var email by remember { mutableStateOf("") }           // User's email input
    var password by remember { mutableStateOf("") }        // User's password input
    var showPassword by remember { mutableStateOf(false) } // Toggle password visibility
    var error by remember { mutableStateOf<String?>(null) } // Error message to display
    var isLoading by remember { mutableStateOf(false) }    // Loading state during sign-in

    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()

    // Focus manager for moving between input fields
    val focusManager = LocalFocusManager.current

    // Context needed for SharedPreferences and starting AlertService
    val context = LocalContext.current

    // ========================================================================
    // FORM VALIDATION
    // ========================================================================
    // The sign-in button is only enabled when:
    // 1. Email is not blank (user has typed something)
    // 2. Password has at least 4 characters (basic validation)
    val isFormValid = email.isNotBlank() && password.length >= 4

    // ========================================================================
    // UI LAYOUT
    // ========================================================================
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())  // Make scrollable for small screens
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ----------------------------------------------------------------
            // TOP SPACING
            // Pushes content down from camera notch/status bar area
            // ----------------------------------------------------------------
            Spacer(modifier = Modifier.height(60.dp))

            // ----------------------------------------------------------------
            // APP LOGO
            // Displays the Alert Buddy logo image
            // NOTE: Ensure alert_buddy.png exists in res/drawable/
            // ----------------------------------------------------------------
            Image(
                painter = painterResource(id = R.drawable.alert_buddy),
                contentDescription = "Alert Buddy Logo",
                modifier = Modifier.size(140.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------------------
            // APP TITLE
            // Large, bold title showing the app name
            // ----------------------------------------------------------------
            Text(
                text = "Alert Buddy",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ----------------------------------------------------------------
            // APP TAGLINE
            // Subtitle explaining the app's purpose
            // ----------------------------------------------------------------
            Text(
                text = "Critical alerts for Altron teams",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ----------------------------------------------------------------
            // EMAIL INPUT FIELD
            // - Uses email keyboard type for better UX
            // - ImeAction.Next moves focus to password field
            // - Shows error styling when error is not null
            // ----------------------------------------------------------------
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = "Email")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,  // Shows @ key on keyboard
                    imeAction = ImeAction.Next          // "Next" button on keyboard
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = error != null  // Red border when error
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------------------
            // PASSWORD INPUT FIELD
            // - Uses password keyboard type
            // - Trailing icon toggles password visibility
            // - ImeAction.Done dismisses keyboard
            // ----------------------------------------------------------------
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "Password")
                },
                trailingIcon = {
                    // Show/hide password toggle button
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            // Change color when password is visible
                            tint = if (showPassword)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                // Show dots or actual text based on showPassword state
                visualTransformation = if (showPassword)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done  // "Done" button on keyboard
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }  // Dismiss keyboard
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = error != null
            )

            // ----------------------------------------------------------------
            // ERROR MESSAGE DISPLAY
            // Shows validation or authentication errors to the user
            // ----------------------------------------------------------------
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------------------
            // SIGN IN BUTTON
            // Handles the authentication process when clicked
            // ----------------------------------------------------------------
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            // ==============================================
                            // STEP 1: VALIDATE EMAIL FORMAT
                            // ==============================================
                            if (!email.contains("@")) {
                                throw Exception("Please enter a valid email address")
                            }

                            // ==============================================
                            // STEP 2: DOMAIN VERIFICATION (PRODUCTION ONLY)
                            // ==============================================
                            // UNCOMMENT THIS BLOCK FOR PRODUCTION DEPLOYMENT
                            // This restricts login to Altron employees only
                            // by checking if email ends with allowed domains.
                            /*
                            val allowedDomains = listOf(
                                "@altron.com",
                                "@altrondigital.com",
                                "@altron.co.za"
                            )
                            val emailLower = email.lowercase().trim()
                            val isAllowedDomain = allowedDomains.any { domain ->
                                emailLower.endsWith(domain)
                            }
                            if (!isAllowedDomain) {
                                throw Exception(
                                    "Access restricted to Altron employees only. " +
                                    "Please use your @altron.com email address."
                                )
                            }
                            */
                            // END PRODUCTION DOMAIN VERIFICATION
                            // ==============================================

                            // ==============================================
                            // STEP 3: VALIDATE PASSWORD LENGTH
                            // ==============================================
                            if (password.length < 4) {
                                throw Exception("Password must be at least 4 characters")
                            }

                            // ==============================================
                            // STEP 4: SIGN IN USER
                            // Creates user record in local Room database
                            // ==============================================
                            repository.signIn(email.trim())

                            // ==============================================
                            // STEP 5: INITIALIZE DEMO DATA
                            // Creates sample channels and alerts for testing
                            // ==============================================
                            repository.initializeDemoData()

                            // ==============================================
                            // STEP 6: SAVE LOGIN STATE
                            // This is crucial for BootReceiver to know if
                            // it should restart AlertService after device
                            // reboot. Without this, alerts won't resume
                            // after the phone restarts.
                            // ==============================================
                            val prefs = context.getSharedPreferences(
                                "alert_buddy_prefs",
                                Context.MODE_PRIVATE
                            )
                            prefs.edit().putBoolean("is_logged_in", true).apply()

                            // Small delay to ensure database operations complete
                            delay(300)

                            // ==============================================
                            // STEP 7: START ALERT SERVICE
                            // Begins monitoring for unread alerts and will
                            // beep every 60 seconds until all are read
                            // ==============================================
                            AlertService.startService(context)

                            // ==============================================
                            // STEP 8: NAVIGATE TO HOME SCREEN
                            // ==============================================
                            onLoginSuccess()

                        } catch (e: Exception) {
                            // Display error message to user
                            error = e.message ?: "Sign in failed"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = isFormValid && !isLoading,  // Disable while loading or invalid
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (isLoading) {
                    // Show loading spinner while signing in
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Sign In",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------------------
            // FORGOT PASSWORD LINK
            // TODO: Implement password reset functionality
            // Could use Firebase Auth password reset or custom solution
            // ----------------------------------------------------------------
            TextButton(onClick = { /* TODO: Implement forgot password */ }) {
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Push footer to bottom of screen
            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------------------
            // FOOTER TEXT
            // Shows who the app is intended for
            // ----------------------------------------------------------------
            Text(
                text = "For Altron Employees",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
