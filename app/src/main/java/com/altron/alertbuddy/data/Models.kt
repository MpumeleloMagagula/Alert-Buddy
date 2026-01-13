package com.altron.alertbuddy.data

import androidx.room.*

// ==================== Enums ====================

/**
 * Severity levels for alert messages.
 * CRITICAL = Red, requires immediate attention
 * WARNING = Orange, needs monitoring
 * INFO = Blue, informational only
 */
enum class Severity {
    CRITICAL,
    WARNING,
    INFO
}

// ==================== Entities ====================

/**
 * User entity for authentication.
 * Only one user should exist at a time in the database.
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Channel entity representing an alert channel.
 * Channels group related alerts together.
 */
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Message/Alert entity representing an individual alert.
 * Messages belong to channels and can be marked as read/acknowledged.
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
    val severity: Severity = Severity.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val acknowledgedAt: Long? = null,
    val metadata: String? = null  // JSON string for extra data like server name, region
)

// ==================== Data Classes (non-Entity) ====================

/**
 * Data class combining channel info with unread message count and last message time.
 * Used for displaying channels in the list with badge counts.
 * Note: This is NOT an @Entity - it's a query result class.
 */
data class ChannelWithUnreadCount(
    val id: String,
    val name: String,
    val unreadCount: Int,
    val lastMessageTime: Long?
)