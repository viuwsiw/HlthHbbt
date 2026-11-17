package com.tshk.hlthhbbty.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun all(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observe(id: Long): Flow<UserEntity?>

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habits WHERE userId = :userId AND archived = 0 ORDER BY createdAt")
    fun observeActive(userId: Long): Flow<List<HabitEntity>>

    @Query(
        """
        SELECT c.* FROM habit_checks c
        INNER JOIN habits h ON h.id = c.habitId
        WHERE h.userId = :userId AND c.epochDay BETWEEN :fromDay AND :toDay
        """
    )
    fun observeChecks(userId: Long, fromDay: Long, toDay: Long): Flow<List<HabitCheckEntity>>

    @Query("SELECT count FROM habit_checks WHERE habitId = :habitId AND epochDay = :day")
    suspend fun countFor(habitId: Long, day: Long): Int?

    @Upsert
    suspend fun upsertCheck(check: HabitCheckEntity)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND epochDay = :day")
    suspend fun deleteCheck(habitId: Long, day: Long)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun findById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE userId = :userId AND archived = 0")
    suspend fun activeOnce(userId: Long): List<HabitEntity>

    @Query(
        """
        SELECT f.* FROM habit_freezes f
        INNER JOIN habits h ON h.id = f.habitId
        WHERE h.userId = :userId AND f.epochDay BETWEEN :fromDay AND :toDay
        """
    )
    fun observeFreezes(userId: Long, fromDay: Long, toDay: Long): Flow<List<FreezeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreeze(freeze: FreezeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecks(checks: List<HabitCheckEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreezes(freezes: List<FreezeEntity>)

    // --- для экспорта / импорта ---
    @Query("SELECT * FROM habits WHERE userId = :userId")
    suspend fun allForUser(userId: Long): List<HabitEntity>

    @Query("SELECT c.* FROM habit_checks c INNER JOIN habits h ON h.id = c.habitId WHERE h.userId = :userId")
    suspend fun allChecks(userId: Long): List<HabitCheckEntity>

    @Query("SELECT f.* FROM habit_freezes f INNER JOIN habits h ON h.id = f.habitId WHERE h.userId = :userId")
    suspend fun allFreezes(userId: Long): List<FreezeEntity>

    @Query("DELETE FROM habits WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    // Обновление стандартных привычек у существующих пользователей (1.0.1)
    @Query("UPDATE habits SET title = 'Хобби', emoji = '🎨' WHERE title = 'Чтение 30 минут'")
    suspend fun renameReadingToHobby()

    @Query("UPDATE habits SET healthMetric = 'BEDTIME', healthTarget = 1380 WHERE title = 'Лечь спать до 23:00' AND healthMetric IS NULL")
    suspend fun enableBedtimeAuto()

    @Query("UPDATE habits SET activity = 'WALK' WHERE title = 'Прогулка на свежем воздухе' AND activity IS NULL")
    suspend fun enableWalkActivity()
}

@Dao
interface WalkDao {
    @Insert
    suspend fun insert(walk: WalkEntity): Long

    @Insert
    suspend fun insertPoints(points: List<WalkPointEntity>)

    @Query("SELECT * FROM walks WHERE userId = :userId ORDER BY startedAt DESC LIMIT 50")
    fun observeWalks(userId: Long): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walk_points WHERE walkId = :walkId ORDER BY idx")
    suspend fun points(walkId: Long): List<WalkPointEntity>

    @Query("DELETE FROM walks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChatDao {
    @Upsert
    suspend fun upsert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE owner = :owner AND peer = :peer ORDER BY createdAt")
    fun observeChat(owner: String, peer: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT peer, text AS lastText, createdAt AS lastAt, outgoing AS lastOutgoing, serverId AS maxServerId
        FROM chat_messages c WHERE owner = :owner AND createdAt = (
            SELECT MAX(createdAt) FROM chat_messages WHERE owner = :owner AND peer = c.peer)
        GROUP BY peer ORDER BY lastAt DESC
        """
    )
    fun observeConversations(owner: String): Flow<List<ConversationRow>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE owner = :owner AND peer = :peer AND outgoing = 0 AND serverId > :afterId")
    fun observeUnread(owner: String, peer: String, afterId: Long): Flow<Int>

    @Query("SELECT peer, COUNT(*) AS n FROM chat_messages WHERE owner = :owner AND outgoing = 0 AND serverId > 0 GROUP BY peer")
    suspend fun incomingCounts(owner: String): List<PeerCount>

    @Query("SELECT * FROM chat_messages WHERE owner = :owner AND outgoing = 0 AND serverId > :afterId ORDER BY serverId")
    suspend fun incomingAfter(owner: String, afterId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE owner = :owner")
    suspend fun deleteAll(owner: String)
}

data class PeerCount(val peer: String, val n: Int)

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: DayNoteEntity)

    @Query("SELECT * FROM day_notes WHERE userId = :userId AND epochDay = :day")
    fun observe(userId: Long, day: Long): Flow<DayNoteEntity?>

    @Query("SELECT * FROM day_notes WHERE userId = :userId AND epochDay BETWEEN :fromDay AND :toDay")
    fun observeRange(userId: Long, fromDay: Long, toDay: Long): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes WHERE userId = :userId")
    suspend fun all(userId: Long): List<DayNoteEntity>

    @Query("DELETE FROM day_notes WHERE userId = :userId")
    suspend fun deleteAll(userId: Long)
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Query("DELETE FROM water_logs WHERE id = (SELECT id FROM water_logs WHERE userId = :userId AND epochDay = :day ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLast(userId: Long, day: Long)

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE userId = :userId AND epochDay = :day")
    fun observeTotal(userId: Long, day: Long): Flow<Int>

    @Query(
        """
        SELECT epochDay, SUM(amountMl) AS total FROM water_logs
        WHERE userId = :userId AND epochDay BETWEEN :fromDay AND :toDay
        GROUP BY epochDay
        """
    )
    fun observeTotals(userId: Long, fromDay: Long, toDay: Long): Flow<List<DayTotal>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE userId = :userId")
    fun observeAllTime(userId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE userId = :userId AND epochDay = :day")
    suspend fun totalOnce(userId: Long, day: Long): Int

    @Query("SELECT * FROM water_logs WHERE userId = :userId")
    suspend fun all(userId: Long): List<WaterLogEntity>

    @Query("DELETE FROM water_logs WHERE userId = :userId")
    suspend fun deleteAll(userId: Long)

    @Insert
    suspend fun insertAll(logs: List<WaterLogEntity>)
}
