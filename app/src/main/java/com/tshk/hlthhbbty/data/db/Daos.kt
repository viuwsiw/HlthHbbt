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
}

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
