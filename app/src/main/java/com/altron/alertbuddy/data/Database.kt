package com.altron.alertbuddy.data

import android.content.Context
import androidx.room.*

// Type converters for Room to handle Severity enum
class Converters {
    @TypeConverter
    fun fromSeverity(severity: Severity): String = severity.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)
}

// DAO for message/alert operations
@Dao
interface MessageDao {
    // Get all messages for a channel, newest first
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp DESC")
    suspend fun getMessagesForChannel(channelId: String): List<Message>

    // Get single message by ID
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): Message?

    // Get total unread count - used by AlertService to check if beeping should continue
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    suspend fun getTotalUnreadCount(): Int

    // Get unread count for a specific channel
    @Query("SELECT COUNT(*) FROM messages WHERE channelId = :channelId AND isRead = 0")
    suspend fun getUnreadCountForChannel(channelId: String): Int

    // Mark a single message as read
    @Query("UPDATE messages SET isRead = 1, acknowledgedAt = :timestamp WHERE id = :messageId")
    suspend fun markAsRead(messageId: String, timestamp: Long = System.currentTimeMillis())

    // Mark all messages in a channel as read
    @Query("UPDATE messages SET isRead = 1, acknowledgedAt = :timestamp WHERE channelId = :channelId")
    suspend fun markAllAsReadForChannel(channelId: String, timestamp: Long = System.currentTimeMillis())

    // Insert a new message (from FCM)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    // Insert multiple messages (for demo data)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Delete
    suspend fun deleteMessage(message: Message)

    // Clean up old messages
    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)
}

// DAO for channel operations
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

    // Get channels with unread counts, sorted by unread count then last message time
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

// DAO for user operations
@Dao
interface UserDao {
    // Get current logged in user
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Clear all users (logout)
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

// Room Database - main database class
@Database(
    entities = [User::class, Channel::class, Message::class],
    version = 1,
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

        // Singleton pattern for database instance
        fun getDatabase(context: Context): AlertBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlertBuddyDatabase::class.java,
                    "alert_buddy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}