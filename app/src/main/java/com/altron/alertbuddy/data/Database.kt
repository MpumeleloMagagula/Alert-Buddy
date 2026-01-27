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

    // Milestone 4: User Role converter
    @TypeConverter
    fun fromUserRole(role: UserRole): String = role.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    // Milestone 4: Standby Status converter
    @TypeConverter
    fun fromStandbyStatus(status: StandbyStatus): String = status.name

    @TypeConverter
    fun toStandbyStatus(value: String): StandbyStatus = StandbyStatus.valueOf(value)
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

    // Milestone 4: Role update
    @Query("UPDATE users SET role = :role WHERE id = :userId")
    suspend fun updateUserRole(userId: String, role: String)

    // Milestone 4: Standby status update
    @Query("UPDATE users SET standbyStatus = :status WHERE id = :userId")
    suspend fun updateStandbyStatus(userId: String, status: String)
}

// ============================================================================
// MILESTONE 4: STANDBY HANDOVER DAOs
// ============================================================================

/**
 * Data Access Object for Team Member operations.
 * Manages team members for standby roster.
 */
@Dao
interface TeamMemberDao {
    @Query("SELECT * FROM team_members ORDER BY displayName")
    suspend fun getAllTeamMembers(): List<TeamMember>

    @Query("SELECT * FROM team_members WHERE id = :memberId")
    suspend fun getTeamMember(memberId: String): TeamMember?

    @Query("SELECT * FROM team_members WHERE standbyStatus = :status")
    suspend fun getTeamMembersByStatus(status: String): List<TeamMember>

    @Query("SELECT * FROM team_members WHERE standbyStatus = 'ON_STANDBY' LIMIT 1")
    suspend fun getCurrentStandbyMember(): TeamMember?

    @Query("SELECT * FROM team_members WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUserAsMember(): TeamMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMember(member: TeamMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMembers(members: List<TeamMember>)

    @Update
    suspend fun updateTeamMember(member: TeamMember)

    @Query("UPDATE team_members SET standbyStatus = :status WHERE id = :memberId")
    suspend fun updateStandbyStatus(memberId: String, status: String)

    @Query("UPDATE team_members SET standbyStatus = 'OFFLINE' WHERE standbyStatus = 'ON_STANDBY'")
    suspend fun clearAllOnStandby()

    @Query("UPDATE team_members SET lastActiveAt = :timestamp WHERE id = :memberId")
    suspend fun updateLastActive(memberId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTeamMember(member: TeamMember)

    @Query("DELETE FROM team_members WHERE id = :memberId")
    suspend fun deleteTeamMemberById(memberId: String)
}

/**
 * Data Access Object for Shift operations.
 * Manages standby shift schedules.
 */
@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    suspend fun getAllShifts(): List<Shift>

    @Query("SELECT * FROM shifts WHERE id = :shiftId")
    suspend fun getShift(shiftId: String): Shift?

    @Query("SELECT * FROM shifts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveShift(): Shift?

    @Query("SELECT * FROM shifts WHERE assignedToId = :memberId ORDER BY startTime DESC")
    suspend fun getShiftsForMember(memberId: String): List<Shift>

    @Query("SELECT * FROM shifts WHERE startTime >= :fromTime ORDER BY startTime ASC")
    suspend fun getUpcomingShifts(fromTime: Long = System.currentTimeMillis()): List<Shift>

    @Query("SELECT * FROM shifts WHERE startTime >= :fromTime ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextShift(fromTime: Long = System.currentTimeMillis()): Shift?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: Shift)

    @Update
    suspend fun updateShift(shift: Shift)

    @Query("UPDATE shifts SET isActive = 0")
    suspend fun deactivateAllShifts()

    @Query("UPDATE shifts SET isActive = 1 WHERE id = :shiftId")
    suspend fun activateShift(shiftId: String)

    @Query("UPDATE shifts SET handoverNotes = :notes WHERE id = :shiftId")
    suspend fun updateHandoverNotes(shiftId: String, notes: String?)

    @Delete
    suspend fun deleteShift(shift: Shift)

    @Query("DELETE FROM shifts WHERE id = :shiftId")
    suspend fun deleteShiftById(shiftId: String)
}

/**
 * Data Access Object for Handover Log operations.
 * Tracks shift transitions for audit trail.
 */
@Dao
interface HandoverLogDao {
    @Query("SELECT * FROM handover_logs ORDER BY handoverAt DESC")
    suspend fun getAllHandoverLogs(): List<HandoverLog>

    @Query("SELECT * FROM handover_logs ORDER BY handoverAt DESC LIMIT :limit")
    suspend fun getRecentHandovers(limit: Int = 10): List<HandoverLog>

    @Query("SELECT * FROM handover_logs WHERE fromUserId = :userId OR toUserId = :userId ORDER BY handoverAt DESC")
    suspend fun getHandoversForUser(userId: String): List<HandoverLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandoverLog(log: HandoverLog)

    @Query("DELETE FROM handover_logs WHERE handoverAt < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)
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
    entities = [
        User::class,
        Channel::class,
        Message::class,
        TeamMember::class,  // Milestone 4
        Shift::class,       // Milestone 4
        HandoverLog::class  // Milestone 4
    ],
    version = 3,  // Incremented for Milestone 4: Standby Handover entities
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AlertBuddyDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun channelDao(): ChannelDao
    abstract fun userDao(): UserDao

    // Milestone 4: Standby Handover DAOs
    abstract fun teamMemberDao(): TeamMemberDao
    abstract fun shiftDao(): ShiftDao
    abstract fun handoverLogDao(): HandoverLogDao

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
