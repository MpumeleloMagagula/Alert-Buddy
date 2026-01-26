package com.altron.alertbuddy.data

import androidx.room.*

/**
 * Severity levels for alerts.
 * Used to visually distinguish alert importance and trigger different behaviors.
 */
enum class Severity {
    CRITICAL,  // Red - Requires immediate attention, loudest beep
    WARNING,   // Orange - Important but not urgent
    INFO       // Blue - Informational only
}

/**
 * User entity for authentication and profile management.
 *
 * Stores user credentials and profile information locally.
 * This is the single source of truth for the logged-in user.
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,

    // Authentication
    val email: String,

    // Profile Information
    val displayName: String? = null,
    val position: String? = null,  // e.g., "Senior DevOps Engineer", "Support Analyst"
    val department: String? = null, // e.g., "Infrastructure", "Support"
    val phoneNumber: String? = null, // For 2FA SMS verification

    // Profile Picture - stores URI or null for initials-based avatar
    val profilePictureUri: String? = null,

    // Two-Factor Authentication
    val is2FAEnabled: Boolean = false,
    val twoFASecret: String? = null, // TOTP secret for authenticator apps

    // Preferences
    val beepIntervalSeconds: Int = 60, // Default 60 seconds
    val vibrationEnabled: Boolean = true,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

/**
 * Channel entity representing an alert source/category.
 *
 * Channels are different monitoring systems or alert sources:
 * - Infinity DAL MS
 * - Nemo
 * - VSA IT Crisis War Room
 */
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Message/Alert entity - the core data model.
 *
 * Represents a single alert from a monitoring system.
 * Contains all information needed to display and track the alert.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Channel::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("channelId")]
)
data class Message(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val message: String,
    val severity: Severity,
    val timestamp: Long,
    val isRead: Boolean = false,
    val acknowledgedAt: Long? = null,
    val acknowledgedBy: String? = null, // User ID who acknowledged
    val metadata: String? = null // JSON string for extra data (server, region, etc.)
)

/**
 * Data class for channel with aggregated unread count.
 * Used in the channel list screen to show unread badges.
 */
data class ChannelWithUnreadCount(
    val id: String,
    val name: String,
    val unreadCount: Int,
    val lastMessageTime: Long?
)

/**
 * User preferences for settings that persist independently.
 * These are stored in DataStore, not Room, for quick access.
 */
data class UserPreferences(
    val beepIntervalSeconds: Int = 60,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val notificationSoundUri: String? = null,
    val darkModeEnabled: Boolean = false
)

/**
 * Dashboard statistics for the home screen.
 * Provides a summary of alert counts and status.
 */
data class DashboardStats(
    val totalAlerts: Int = 0,
    val unreadAlerts: Int = 0,
    val criticalAlerts: Int = 0,
    val warningAlerts: Int = 0,
    val infoAlerts: Int = 0,
    val todayAlerts: Int = 0,
    val acknowledgedAlerts: Int = 0
) {
    val acknowledgeRate: Float
        get() = if (totalAlerts > 0) acknowledgedAlerts.toFloat() / totalAlerts else 0f
}
