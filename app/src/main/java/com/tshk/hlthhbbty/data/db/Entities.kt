package com.tshk.hlthhbbty.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Gender { MALE, FEMALE }

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Логин, всегда в нижнем регистре. */
    val username: String,
    val passwordHash: String,
    val passwordSalt: String,
    val displayName: String,
    val gender: Gender? = null,
    val age: Int? = null,
    /** "preset:N" — встроенный аватар, "file:/path" — фото из галереи. */
    val avatar: String? = null,
    val waterGoalMl: Int = 2000,
    val profileCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "habits",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val emoji: String,
    val colorIndex: Int = 0,
    /** Сколько раз в день нужно выполнить привычку. */
    val goalPerDay: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

@Entity(
    tableName = "habit_checks",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HabitCheckEntity(
    val habitId: Long,
    val epochDay: Long,
    val count: Int,
)

@Entity(
    tableName = "water_logs",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId", "epochDay"])],
)
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val amountMl: Int,
    val epochDay: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class DayTotal(val epochDay: Long, val total: Int)
