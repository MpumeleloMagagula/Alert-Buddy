package com.altron.alertbuddy.data

import android.content.Context
import androidx.room.*

// Type converters for Room
class Converters {
    @TypeConverter
    fun fromSeverity(severity: Severity): String = severity.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)
}

// Message DAO
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
}

// Channel DAO
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

// User DAO
@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

// Room Database
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
