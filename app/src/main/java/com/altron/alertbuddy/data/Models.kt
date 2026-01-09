package com.altron.alertbuddy.data

import androidx.room.*

// Severity enum for alert types
enum class Severity {
    CRITICAL,
    WARNING,
    INFO
}

// User entity for authentication
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Channel entity
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Message/Alert entity
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
    val metadata: String? = null // JSON string for extra data
)

// Data class for channel with unread count
data class ChannelWithUnreadCount(
    val id: String,
    val name: String,
    val unreadCount: Int,
    val lastMessageTime: Long?
)
