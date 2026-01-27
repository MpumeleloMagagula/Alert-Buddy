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
 * User roles for permission management.
 * Determines what actions a user can perform in the app.
 */
enum class UserRole {
    ADMIN,    // Full access: manage team, shifts, all settings
    MANAGER,  // Can manage shifts and team members
    USER      // Basic access: view standby, receive alerts
}

/**
 * Standby status for team members.
 * Tracks whether a team member is available for alerts.
 */
enum class StandbyStatus {
    ON_STANDBY,   // Currently receiving alerts (green)
    AVAILABLE,    // Online but not on standby (blue)
    OFFLINE       // Not available (gray)
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

    // Role and Standby Status (Milestone 4)
    val role: UserRole = UserRole.USER,  // Permission level
    val standbyStatus: StandbyStatus = StandbyStatus.OFFLINE,  // Current availability

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

// ============================================================================
// MILESTONE 4: STANDBY HANDOVER ENTITIES
// ============================================================================

/**
 * Team Member entity for standby roster management.
 *
 * Represents a team member who can be assigned to standby shifts.
 * Separate from User entity to allow managing team without local login.
 */
@Entity(tableName = "team_members")
data class TeamMember(
    @PrimaryKey
    val id: String,

    val email: String,
    val displayName: String,
    val role: UserRole = UserRole.USER,
    val standbyStatus: StandbyStatus = StandbyStatus.OFFLINE,

    // Contact info for escalation
    val phoneNumber: String? = null,

    // Is this the currently logged-in user?
    val isCurrentUser: Boolean = false,

    // Last seen / activity timestamp
    val lastActiveAt: Long = System.currentTimeMillis(),

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Shift entity for standby schedule management.
 *
 * Represents a standby shift assignment.
 * Can be used for scheduled shifts or manual handovers.
 */
@Entity(
    tableName = "shifts",
    foreignKeys = [
        ForeignKey(
            entity = TeamMember::class,
            parentColumns = ["id"],
            childColumns = ["assignedToId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("assignedToId")]
)
data class Shift(
    @PrimaryKey
    val id: String,

    // Who is assigned to this shift
    val assignedToId: String?,
    val assignedToName: String,

    // Shift timing
    val startTime: Long,  // Epoch timestamp
    val endTime: Long,    // Epoch timestamp

    // Shift status
    val isActive: Boolean = false,  // Currently active shift

    // Handover notes from previous shift
    val handoverNotes: String? = null,

    // Who created this shift (Admin/Manager)
    val createdById: String,
    val createdByName: String,

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Handover Log entity for tracking shift transitions.
 *
 * Records when responsibility is passed from one person to another.
 * Useful for audit trail and handover history.
 */
@Entity(tableName = "handover_logs")
data class HandoverLog(
    @PrimaryKey
    val id: String,

    // Who handed over
    val fromUserId: String,
    val fromUserName: String,

    // Who received handover
    val toUserId: String,
    val toUserName: String,

    // Handover details
    val notes: String? = null,
    val pendingAlertsCount: Int = 0,

    // Timestamp
    val handoverAt: Long = System.currentTimeMillis()
)

/**
 * Data class for displaying team member with shift info.
 * Used in the standby status screen.
 */
data class TeamMemberWithShift(
    val member: TeamMember,
    val currentShift: Shift?,
    val isOnStandby: Boolean
)
