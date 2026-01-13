package com.altron.alertbuddy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for all data operations - alerts, channels, and users.
 * Acts as a single source of truth for the app's data layer.
 */
class AlertRepository(context: Context) {
    private val database = AlertBuddyDatabase.getDatabase(context)
    private val messageDao = database.messageDao()
    private val channelDao = database.channelDao()
    private val userDao = database.userDao()

    // ==================== User Operations ====================

    /**
     * Get the currently logged in user (null if not logged in)
     */
    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        userDao.getCurrentUser()
    }

    /**
     * Sign in user with email
     */
    suspend fun signIn(email: String): User = withContext(Dispatchers.IO) {
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email
        )
        userDao.insertUser(user)
        user
    }

    /**
     * Sign out - clears all user data
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        userDao.deleteAllUsers()
    }

    // ==================== Channel Operations ====================

    /**
     * Get all channels with their unread message counts
     */
    suspend fun getChannelsWithUnreadCount(): List<ChannelWithUnreadCount> = withContext(Dispatchers.IO) {
        channelDao.getChannelsWithUnreadCount()
    }

    /**
     * Initialize default alert channels
     */
    suspend fun initializeDefaultChannels() = withContext(Dispatchers.IO) {
        val channels = listOf(
            Channel("infinity-dal-ms", "Infinity DAL MS"),
            Channel("infinity-online", "Infinity Online Incidents"),
            Channel("nemo", "Nemo"),
            Channel("online-dal", "Online DAL Interaction"),
            Channel("vsa-crisis", "VSA IT Crisis War Room")
        )
        channelDao.insertChannels(channels)
    }

    // ==================== Message Operations ====================

    /**
     * Get all messages for a specific channel
     */
    suspend fun getMessagesForChannel(channelId: String): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getMessagesForChannel(channelId)
    }

    /**
     * Get a single message by ID
     */
    suspend fun getMessage(messageId: String): Message? = withContext(Dispatchers.IO) {
        messageDao.getMessage(messageId)
    }

    /**
     * Get total count of unread messages across all channels.
     * Used by AlertService to determine if beeping should continue.
     */
    suspend fun getTotalUnreadCount(): Int = withContext(Dispatchers.IO) {
        messageDao.getTotalUnreadCount()
    }

    /**
     * Mark a single message as read with acknowledgment timestamp
     */
    suspend fun markAsRead(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.markAsRead(messageId, System.currentTimeMillis())
    }

    /**
     * Mark all messages in a channel as read with acknowledgment timestamp
     */
    suspend fun markAllAsReadForChannel(channelId: String) = withContext(Dispatchers.IO) {
        messageDao.markAllAsReadForChannel(channelId, System.currentTimeMillis())
    }

    /**
     * Insert a new message (used by FCM service)
     */
    suspend fun insertMessage(message: Message) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    /**
     * Delete old messages before a certain timestamp
     */
    suspend fun deleteOldMessages(beforeTimestamp: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteOldMessages(beforeTimestamp)
    }

    // ==================== Demo Data ====================

    /**
     * Initialize demo data for testing
     */
    suspend fun initializeDemoData() = withContext(Dispatchers.IO) {
        initializeDefaultChannels()

        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        val messages = listOf(
            Message(
                id = "msg-1",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "CPU Alert",
                message = "CPU usage has exceeded 80% threshold. Current usage: 87%. Immediate action required.",
                severity = Severity.CRITICAL,
                timestamp = now - 2 * 60 * 1000,
                metadata = """{"server": "prod-db-01", "region": "Cape Town"}"""
            ),
            Message(
                id = "msg-2",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "Disk Space Low",
                message = "/var partition at 95% capacity. Consider cleanup or expansion.",
                severity = Severity.WARNING,
                timestamp = now - 5 * 60 * 1000,
                metadata = """{"server": "prod-app-02", "partition": "/var"}"""
            ),
            Message(
                id = "msg-3",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "Memory Warning",
                message = "Memory usage 70%. Monitoring recommended.",
                severity = Severity.INFO,
                timestamp = now - 10 * 60 * 1000,
                isRead = true
            ),
            Message(
                id = "msg-4",
                channelId = "infinity-online",
                channelName = "Infinity Online Incidents",
                title = "Response Time Spike",
                message = "API response time exceeded 2000ms threshold. Current: 3500ms average.",
                severity = Severity.CRITICAL,
                timestamp = now - 15 * 60 * 1000
            ),
            Message(
                id = "msg-5",
                channelId = "infinity-online",
                channelName = "Infinity Online Incidents",
                title = "Database Connection Pool",
                message = "Connection pool at 85% capacity.",
                severity = Severity.WARNING,
                timestamp = now - 30 * 60 * 1000
            ),
            Message(
                id = "msg-6",
                channelId = "nemo",
                channelName = "Nemo",
                title = "Service Health Check Failed",
                message = "Health check endpoint not responding on nemo-worker-03.",
                severity = Severity.CRITICAL,
                timestamp = now - hour
            ),
            Message(
                id = "msg-7",
                channelId = "online-dal",
                channelName = "Online DAL Interaction",
                title = "Cache Miss Rate High",
                message = "Cache miss rate at 45%. Consider cache warming.",
                severity = Severity.WARNING,
                timestamp = now - 2 * hour
            ),
            Message(
                id = "msg-8",
                channelId = "online-dal",
                channelName = "Online DAL Interaction",
                title = "Scheduled Maintenance",
                message = "Scheduled maintenance window tomorrow at 02:00 UTC.",
                severity = Severity.INFO,
                timestamp = now - day,
                isRead = true
            )
        )
        messageDao.insertMessages(messages)
    }
}