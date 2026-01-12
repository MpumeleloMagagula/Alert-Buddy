package com.altron.alertbuddy.ui.theme.screens

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

// Login screen for user authentication
@Composable
fun LoginScreen(
    repository: AlertRepository,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val isFormValid = email.isNotBlank() && password.length >= 4

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top spacing to push content down from camera
            Spacer(modifier = Modifier.height(60.dp))

            // App logo
            Image(
                painter = painterResource(id = R.drawable.alert_buddy),
                contentDescription = "Alert Buddy Logo",
                modifier = Modifier.size(140.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Alert Buddy",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Critical alerts for Altron teams",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Email input field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = "Email")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = error != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password input field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "Password")
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = if (showPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = error != null
            )

            // Error message display
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

            // Sign in button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            // Validate email format
                            if (!email.contains("@")) {
                                throw Exception("Please enter a valid email address")
                            }
                            // Validate password length
                            if (password.length < 4) {
                                throw Exception("Password must be at least 4 characters")
                            }

                            // Sign in and initialize demo data
                            repository.signIn(email.trim())
                            repository.initializeDemoData()

                            // Small delay to ensure database is ready
                            delay(300)

                            // Start alert service to begin beeping for unread alerts
                            AlertService.startService(context)

                            onLoginSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "Sign in failed"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = isFormValid && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (isLoading) {
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

            // Forgot password link
            TextButton(onClick = { /* TODO: Implement forgot password */ }) {
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Text(
                text = "For Altron Employees",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}