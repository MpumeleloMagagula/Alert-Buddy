package com.altron.alertbuddy.data

import android.content.Context
import androidx.room.*

/**
 * Type converters for Room database.
 * Converts complex types to/from primitive types that SQLite can store.
 */
class Converters {
    @TypeConverter
    fun fromSeverity(severity: Severity): String = severity.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)
}

/**
 * Data Access Object for Message/Alert operations.
 * Provides all database operations for alerts.
 */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp DESC")
    suspend fun getMessagesForChannel(channelId: String): List<Message>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): Message?

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    suspend fun getTotalUnreadCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE channelId = :channelId AND isRead = 0")
    suspend fun getUnreadCountForChannel(channelId: String): Int

    @Query("UPDATE messages SET isRead = 1, acknowledgedAt = :timestamp WHERE id = :messageId")
    suspend fun markAsRead(messageId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET isRead = 0, acknowledgedAt = NULL WHERE id = :messageId")
    suspend fun markAsUnread(messageId: String)

    @Query("UPDATE messages SET isRead = 1, acknowledgedAt = :timestamp WHERE channelId = :channelId")
    suspend fun markAllAsReadForChannel(channelId: String, timestamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE severity = :severity")
    suspend fun getCountBySeverity(severity: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :sinceTimestamp")
    suspend fun getAlertsCountSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 1")
    suspend fun getAcknowledgedCount(): Int

    @Query("SELECT * FROM messages WHERE isRead = 1 ORDER BY acknowledgedAt DESC, timestamp DESC")
    suspend fun getAcknowledgedAlerts(): List<Message>
}

/**
 * Data Access Object for Channel operations.
 * Channels represent different alert sources (Nemo, Infinity, etc.)
 */
@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY name")
    suspend fun getAllChannels(): List<Channel>

    @Query("SELECT * FROM channels WHERE id = :channelId")
    suspend fun getChannel(channelId: String): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: Channel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Query("""
        SELECT 
            c.id,
            c.name,
            COALESCE(SUM(CASE WHEN m.isRead = 0 THEN 1 ELSE 0 END), 0) as unreadCount,
            MAX(m.timestamp) as lastMessageTime
        FROM channels c
        LEFT JOIN messages m ON c.id = m.channelId
        GROUP BY c.id, c.name
        ORDER BY unreadCount DESC, lastMessageTime DESC
    """)
    suspend fun getChannelsWithUnreadCount(): List<ChannelWithUnreadCount>
}

/**
 * Data Access Object for User operations.
 * Handles user authentication and profile data.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    // Profile updates
    @Query("UPDATE users SET displayName = :displayName, position = :position, department = :department WHERE id = :userId")
    suspend fun updateProfile(userId: String, displayName: String?, position: String?, department: String?)

    // 2FA toggle
    @Query("UPDATE users SET is2FAEnabled = :enabled WHERE id = :userId")
    suspend fun update2FAStatus(userId: String, enabled: Boolean)

    // Beep interval update
    @Query("UPDATE users SET beepIntervalSeconds = :intervalSeconds WHERE id = :userId")
    suspend fun updateBeepInterval(userId: String, intervalSeconds: Int)

    // Vibration setting update
    @Query("UPDATE users SET vibrationEnabled = :enabled WHERE id = :userId")
    suspend fun updateVibrationSetting(userId: String, enabled: Boolean)
}

/**
 * Room Database for Alert Buddy.
 *
 * This is the main database class that provides access to all DAOs.
 * Uses singleton pattern to ensure only one instance exists.
 *
 * MIGRATION NOTE:
 * When adding new columns to entities, increment the version number
 * and add a migration object to preserve existing data.
 */
@Database(
    entities = [User::class, Channel::class, Message::class],
    version = 2,  // Incremented for new User fields
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AlertBuddyDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun channelDao(): ChannelDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AlertBuddyDatabase? = null

        fun getDatabase(context: Context): AlertBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlertBuddyDatabase::class.java,
                    "alert_buddy_database"
                )
                    .fallbackToDestructiveMigration()  // For development - recreates DB on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
