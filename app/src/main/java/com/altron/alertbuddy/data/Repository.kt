package com.altron.alertbuddy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * AlertRepository - Data Access Layer
 *
 * This repository provides a clean API for all data operations.
 * It abstracts the underlying Room database and handles threading.
 *
 * RESPONSIBILITIES:
 * - User authentication (sign in, sign out, profile management)
 * - Channel operations (list, create)
 * - Message/Alert operations (CRUD, mark as read)
 * - Settings/preferences storage
 *
 * THREADING:
 * All database operations run on Dispatchers.IO to avoid blocking the main thread.
 */
class AlertRepository(context: Context) {
    private val database = AlertBuddyDatabase.getDatabase(context)
    private val messageDao = database.messageDao()
    private val channelDao = database.channelDao()
    private val userDao = database.userDao()

    // =====================================================
    // USER OPERATIONS
    // =====================================================

    /**
     * Get the currently logged-in user.
     * Returns null if no user is signed in.
     */
    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        userDao.getCurrentUser()
    }

    /**
     * Sign in a user with the given email.
     * Creates a new user record in the local database.
     *
     * @param email User's email address
     * @return The created User object
     */
    suspend fun signIn(email: String): User = withContext(Dispatchers.IO) {
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email,
            lastLoginAt = System.currentTimeMillis()
        )
        userDao.insertUser(user)
        user
    }

    /**
     * Sign out the current user.
     * Deletes all user records from the local database.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        userDao.deleteAllUsers()
    }

    /**
     * Update user profile information.
     *
     * @param userId User's unique ID
     * @param displayName User's display name (shown in UI)
     * @param position User's job title (e.g., "Senior DevOps Engineer")
     * @param department User's department (e.g., "Infrastructure")
     */
    suspend fun updateUserProfile(
        userId: String,
        displayName: String?,
        position: String?,
        department: String?
    ) = withContext(Dispatchers.IO) {
        userDao.updateProfile(userId, displayName, position, department)
    }

    /**
     * Toggle Two-Factor Authentication for a user.
     *
     * @param userId User's unique ID
     * @param enabled Whether 2FA should be enabled
     */
    suspend fun toggle2FA(userId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        userDao.update2FAStatus(userId, enabled)
    }

    /**
     * Update the beep interval setting.
     * This controls how often AlertService beeps when there are unread alerts.
     *
     * @param userId User's unique ID
     * @param intervalSeconds Beep interval in seconds (30, 60, 120, 300)
     */
    suspend fun updateBeepInterval(userId: String, intervalSeconds: Int) = withContext(Dispatchers.IO) {
        userDao.updateBeepInterval(userId, intervalSeconds)
    }

    /**
     * Update vibration setting.
     *
     * @param userId User's unique ID
     * @param enabled Whether vibration should be enabled with alerts
     */
    suspend fun updateVibrationSetting(userId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        userDao.updateVibrationSetting(userId, enabled)
    }

    // =====================================================
    // CHANNEL OPERATIONS
    // =====================================================

    /**
     * Get all channels with their unread message counts.
     * Used to display the channel list with badges.
     */
    suspend fun getChannelsWithUnreadCount(): List<ChannelWithUnreadCount> = withContext(Dispatchers.IO) {
        channelDao.getChannelsWithUnreadCount()
    }

    /**
     * Initialize default channels for Altron's monitoring systems.
     * Called on first sign-in or when resetting demo data.
     */
    suspend fun initializeDefaultChannels() = withContext(Dispatchers.IO) {
        val channels = listOf(
            Channel("infinity-dal-ms", "Infinity DAL MS", "Database and middleware alerts"),
            Channel("infinity-online", "Infinity Online Incidents", "Online service incidents"),
            Channel("nemo", "Nemo", "Nemo monitoring system alerts"),
            Channel("online-dal", "Online DAL Interaction", "DAL interaction alerts"),
            Channel("vsa-crisis", "VSA IT Crisis War Room", "Critical crisis alerts")
        )
        channelDao.insertChannels(channels)
    }

    // =====================================================
    // MESSAGE/ALERT OPERATIONS
    // =====================================================

    /**
     * Get all messages for a specific channel.
     * Messages are sorted by timestamp (newest first).
     */
    suspend fun getMessagesForChannel(channelId: String): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getMessagesForChannel(channelId)
    }

    /**
     * Get a single message by ID.
     */
    suspend fun getMessage(messageId: String): Message? = withContext(Dispatchers.IO) {
        messageDao.getMessage(messageId)
    }

    /**
     * Get the total count of unread messages across all channels.
     * Used by AlertService to determine if beeping should continue.
     */
    suspend fun getTotalUnreadCount(): Int = withContext(Dispatchers.IO) {
        messageDao.getTotalUnreadCount()
    }

    /**
     * Mark a single message as read.
     * Also records the acknowledgement timestamp.
     */
    suspend fun markAsRead(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.markAsRead(messageId)
    }

    /**
     * Mark a single message as read (alias for markAsRead).
     * Used by swipe-to-acknowledge gesture.
     *
     * @param messageId The ID of the message to acknowledge
     */
    suspend fun markMessageAsRead(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.markAsRead(messageId)
    }

    /**
     * Mark a single message as unread.
     * Used when user wants to revisit an alert later.
     *
     * @param messageId The ID of the message to mark as unread
     */
    suspend fun markMessageAsUnread(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.markAsUnread(messageId)
    }

    /**
     * Mark all messages in a channel as read.
     * Used for "Mark All Read" functionality.
     */
    suspend fun markAllAsReadForChannel(channelId: String) = withContext(Dispatchers.IO) {
        messageDao.markAllAsReadForChannel(channelId)
    }

    /**
     * Insert a new message/alert.
     * Called by FCM service when new alerts arrive.
     */
    suspend fun insertMessage(message: Message) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    // =====================================================
    // STATISTICS & DASHBOARD
    // =====================================================

    /**
     * Get dashboard statistics for the home screen.
     * Returns counts of total alerts, unread, and breakdown by severity.
     */
    suspend fun getDashboardStats(): DashboardStats = withContext(Dispatchers.IO) {
        val total = messageDao.getTotalMessageCount()
        val unread = messageDao.getTotalUnreadCount()
        val critical = messageDao.getCountBySeverity(Severity.CRITICAL.name)
        val warning = messageDao.getCountBySeverity(Severity.WARNING.name)
        val info = messageDao.getCountBySeverity(Severity.INFO.name)
        val todayAlerts = messageDao.getAlertsCountSince(getTodayStartTimestamp())
        val acknowledged = messageDao.getAcknowledgedCount()

        DashboardStats(
            totalAlerts = total,
            unreadAlerts = unread,
            criticalAlerts = critical,
            warningAlerts = warning,
            infoAlerts = info,
            todayAlerts = todayAlerts,
            acknowledgedAlerts = acknowledged
        )
    }

    private fun getTodayStartTimestamp(): Long {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L
        return now - (now % dayInMillis)
    }

    /**
     * Get all acknowledged (read) alerts for history view.
     * Sorted by acknowledged time, most recent first.
     */
    suspend fun getAcknowledgedAlerts(): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getAcknowledgedAlerts()
    }

    // =====================================================
    // DEMO DATA
    // =====================================================

    /**
     * Initialize demo data for testing purposes.
     * Creates sample alerts across different channels.
     */
    suspend fun initializeDemoData() = withContext(Dispatchers.IO) {
        initializeDefaultChannels()

        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        val messages = listOf(
            // Infinity DAL MS - Critical CPU Alert
            Message(
                id = "msg-1",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "CPU Alert - CRITICAL",
                message = "CPU usage has exceeded 80% threshold on production database server. Current usage: 87%. Immediate action required to prevent service degradation.",
                severity = Severity.CRITICAL,
                timestamp = now - 2 * 60 * 1000,
                metadata = """{"server": "prod-db-01", "region": "Cape Town", "cpu": 87}"""
            ),
            // Infinity DAL MS - Warning Disk Space
            Message(
                id = "msg-2",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "Disk Space Low",
                message = "/var partition at 95% capacity. Consider cleanup or expansion. Estimated time to full: 4 hours.",
                severity = Severity.WARNING,
                timestamp = now - 5 * 60 * 1000,
                metadata = """{"server": "prod-app-02", "partition": "/var", "usage": 95}"""
            ),
            // Infinity DAL MS - Info Memory
            Message(
                id = "msg-3",
                channelId = "infinity-dal-ms",
                channelName = "Infinity DAL MS",
                title = "Memory Usage Normal",
                message = "Memory usage stabilized at 70%. Monitoring continues.",
                severity = Severity.INFO,
                timestamp = now - 10 * 60 * 1000,
                isRead = true
            ),
            // Infinity Online - Critical Response Time
            Message(
                id = "msg-4",
                channelId = "infinity-online",
                channelName = "Infinity Online Incidents",
                title = "Response Time Spike",
                message = "API response time exceeded 2000ms threshold. Current average: 3500ms. Customer impact detected.",
                severity = Severity.CRITICAL,
                timestamp = now - 15 * 60 * 1000
            ),
            // Infinity Online - Warning DB Pool
            Message(
                id = "msg-5",
                channelId = "infinity-online",
                channelName = "Infinity Online Incidents",
                title = "Database Connection Pool",
                message = "Connection pool at 85% capacity. Consider scaling if load continues.",
                severity = Severity.WARNING,
                timestamp = now - 30 * 60 * 1000
            ),
            // Nemo - Critical Health Check
            Message(
                id = "msg-6",
                channelId = "nemo",
                channelName = "Nemo",
                title = "Service Health Check Failed",
                message = "Health check endpoint not responding on nemo-worker-03. Service may be down. Auto-restart initiated.",
                severity = Severity.CRITICAL,
                timestamp = now - hour
            ),
            // Online DAL - Warning Cache
            Message(
                id = "msg-7",
                channelId = "online-dal",
                channelName = "Online DAL Interaction",
                title = "Cache Miss Rate High",
                message = "Cache miss rate at 45%. Performance may be affected. Consider cache warming strategy.",
                severity = Severity.WARNING,
                timestamp = now - 2 * hour
            ),
            // Online DAL - Info Maintenance
            Message(
                id = "msg-8",
                channelId = "online-dal",
                channelName = "Online DAL Interaction",
                title = "Scheduled Maintenance",
                message = "Scheduled maintenance window tomorrow at 02:00 SAST. Expected duration: 2 hours.",
                severity = Severity.INFO,
                timestamp = now - day,
                isRead = true
            ),
            // VSA Crisis - Critical
            Message(
                id = "msg-9",
                channelId = "vsa-crisis",
                channelName = "VSA IT Crisis War Room",
                title = "Network Outage Detected",
                message = "Major network outage affecting Johannesburg datacenter. Multiple services impacted. Crisis team assembled.",
                severity = Severity.CRITICAL,
                timestamp = now - 45 * 60 * 1000
            )
        )
        messageDao.insertMessages(messages)
    }
}
