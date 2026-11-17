package com.tshk.hlthhbbty.data.db

import androidx.room.ColumnInfo
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
    /** Дни недели битовой маской: бит 0 — понедельник … бит 6 — воскресенье. */
    @ColumnInfo(defaultValue = "127") val scheduleMask: Int = HabitKind.EVERY_DAY,
    /** Время напоминания в минутах от полуночи. */
    val reminderMinutes: Int? = null,
    /** GOOD — полезная привычка, QUIT — отказ от вредной. */
    @ColumnInfo(defaultValue = "GOOD") val kind: String = HabitKind.GOOD,
    /** Для QUIT: момент, с которого идёт отсчёт. */
    val quitSince: Long? = null,
    /** Автоотметка из Health Connect: STEPS или SLEEP. */
    val healthMetric: String? = null,
    /** Порог: шагов или минут сна. */
    val healthTarget: Int? = null,
    /** Длительность таймера фокуса в минутах. */
    val focusMinutes: Int? = null,
    /** Активность с трекером: WALK — прогулка с картой, GPS и шагами. */
    val activity: String? = null,
) {
    val isQuit get() = kind == HabitKind.QUIT
}

object HabitKind {
    const val GOOD = "GOOD"
    const val QUIT = "QUIT"
    const val EVERY_DAY = 0b1111111
    const val METRIC_STEPS = "STEPS"
    const val METRIC_SLEEP = "SLEEP"
    /** Автоотметка «лечь спать до N»: снимается, если телефон разблокировали позже. */
    const val METRIC_BEDTIME = "BEDTIME"
    const val ACTIVITY_WALK = "WALK"
}

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

@Entity(
    tableName = "habit_freezes",
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
data class FreezeEntity(val habitId: Long, val epochDay: Long)

@Entity(
    tableName = "day_notes",
    primaryKeys = ["userId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DayNoteEntity(
    val userId: Long,
    val epochDay: Long,
    /** 1..5, 0 — не указано. */
    val mood: Int,
    val note: String = "",
)

@Entity(
    tableName = "walks",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId")],
)
data class WalkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val habitId: Long?,
    val startedAt: Long,
    val durationMs: Long,
    val distanceM: Double,
    val steps: Int,
)

@Entity(
    tableName = "walk_points",
    primaryKeys = ["walkId", "idx"],
    foreignKeys = [
        ForeignKey(entity = WalkEntity::class, parentColumns = ["id"], childColumns = ["walkId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class WalkPointEntity(
    val walkId: Long,
    val idx: Int,
    val lat: Double,
    val lon: Double,
    val time: Long,
)

/** Расшифрованное сообщение мессенджера (хранится только на этом устройстве). */
@Entity(tableName = "chat_messages", indices = [Index(value = ["owner", "peer", "serverId"])])
data class ChatMessageEntity(
    @PrimaryKey val clientId: String,
    /** Логин владельца на этом устройстве (на устройстве может быть несколько аккаунтов). */
    val owner: String,
    val peer: String,
    val outgoing: Boolean,
    val text: String,
    val createdAt: Long,
    /** 0 — ещё не доставлено на сервер. */
    val serverId: Long = 0,
    /** false — не удалось расшифровать. */
    val readable: Boolean = true,
)

data class ConversationRow(
    val peer: String,
    val lastText: String,
    val lastAt: Long,
    val lastOutgoing: Boolean,
    val maxServerId: Long,
)

data class DayTotal(val epochDay: Long, val total: Int)
