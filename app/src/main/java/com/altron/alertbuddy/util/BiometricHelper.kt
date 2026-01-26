package com.altron.alertbuddy.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * ============================================================================
 * BiometricHelper.kt - Biometric Authentication Utility
 * ============================================================================
 *
 * PURPOSE:
 * Provides biometric authentication (fingerprint/face recognition) for quick
 * and secure access to the Alert Buddy app.
 *
 * FEATURES:
 * - Check if device supports biometric authentication
 * - Check if user has enrolled biometrics
 * - Display biometric prompt for authentication
 * - Handle success, error, and cancellation callbacks
 *
 * USAGE:
 * 1. Call canAuthenticate() to check if biometrics are available
 * 2. Call authenticate() to show the biometric prompt
 * 3. Handle callbacks for success/failure
 *
 * SECURITY:
 * - Uses Android's BiometricPrompt API (secure hardware-backed)
 * - Falls back to device credentials if biometrics fail
 * - Works with fingerprint, face recognition, and iris scanning
 *
 * ============================================================================
 */
object BiometricHelper {

    /**
     * Biometric authentication result.
     */
    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val errorCode: Int, val errorMessage: String) : AuthResult()
        object Cancelled : AuthResult()
        object NotAvailable : AuthResult()
    }

    /**
     * Check if biometric authentication is available on this device.
     *
     * @param context Application context
     * @return BiometricAvailability indicating the status
     */
    fun canAuthenticate(context: Context): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricAvailability.UNKNOWN
            else -> BiometricAvailability.UNKNOWN
        }
    }

    /**
     * Check if device credential (PIN/pattern/password) is available as fallback.
     *
     * @param context Application context
     * @return true if device credentials are set up
     */
    fun canAuthenticateWithDeviceCredential(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show biometric authentication prompt.
     *
     * @param activity FragmentActivity to attach the prompt to
     * @param title Title shown on the biometric prompt
     * @param subtitle Subtitle shown on the biometric prompt
     * @param negativeButtonText Text for the cancel/negative button
     * @param onResult Callback with authentication result
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint or face to sign in",
        negativeButtonText: String = "Use Password",
        onResult: (AuthResult) -> Unit
    ) {
        // Check if biometrics are available
        val availability = canAuthenticate(activity)
        if (availability != BiometricAvailability.AVAILABLE) {
            onResult(AuthResult.NotAvailable)
            return
        }

        // Create executor for callback
        val executor = ContextCompat.getMainExecutor(activity)

        // Create authentication callback
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onResult(AuthResult.Cancelled)
                    }
                    else -> {
                        onResult(AuthResult.Error(errorCode, errString.toString()))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // This is called when biometric is valid but not recognized
                // User can try again, no need to call onResult here
            }
        }

        // Create BiometricPrompt
        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        // Build prompt info
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        // Show the prompt
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Show biometric authentication with device credential fallback.
     * If biometrics fail, user can use PIN/pattern/password.
     *
     * @param activity FragmentActivity to attach the prompt to
     * @param title Title shown on the biometric prompt
     * @param subtitle Subtitle shown on the biometric prompt
     * @param onResult Callback with authentication result
     */
    fun authenticateWithFallback(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Verify your identity to continue",
        onResult: (AuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onResult(AuthResult.Cancelled)
                    }
                    else -> {
                        onResult(AuthResult.Error(errorCode, errString.toString()))
                    }
                }
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        // Allow both biometrics and device credentials
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

/**
 * Enum representing the availability status of biometric authentication.
 */
enum class BiometricAvailability {
    /** Biometric authentication is available and ready to use */
    AVAILABLE,

    /** Device has no biometric hardware */
    NO_HARDWARE,

    /** Biometric hardware is currently unavailable */
    HARDWARE_UNAVAILABLE,

    /** User has not enrolled any biometrics (no fingerprint/face registered) */
    NOT_ENROLLED,

    /** Security vulnerability discovered, update required */
    SECURITY_UPDATE_REQUIRED,

    /** Biometric authentication is not supported */
    UNSUPPORTED,

    /** Status is unknown */
    UNKNOWN
}
