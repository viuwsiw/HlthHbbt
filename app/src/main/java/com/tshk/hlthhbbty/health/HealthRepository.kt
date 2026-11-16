package com.tshk.hlthhbbty.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthSnapshot(val steps: Long, val sleepMinutes: Long)

enum class HealthStatus { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/** Чтение шагов и сна из Health Connect и автоотметка привязанных привычек. */
class HealthRepository(private val context: Context, private val habits: HabitRepository) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.UNAVAILABLE
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        status() == HealthStatus.AVAILABLE &&
            runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }.getOrDefault(false)

    suspend fun snapshot(): HealthSnapshot? {
        if (!hasPermissions()) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
        val steps = client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startOfDay, now))
        )[StepsRecord.COUNT_TOTAL] ?: 0L
        val sleepFrom = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleep = client.aggregate(
            AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(sleepFrom, now))
        )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L
        return HealthSnapshot(steps, sleep)
    }

    /** Отмечает привычки с автоцелью, если цель достигнута. Возвращает актуальные данные. */
    suspend fun syncHabits(userId: Long): HealthSnapshot? {
        val snap = snapshot() ?: return null
        val day = today()
        habits.activeOnce(userId)
            .filter { it.healthMetric != null && it.healthTarget != null && it.scheduledOn(day) }
            .forEach { h ->
                val value = when (h.healthMetric) {
                    HabitKind.METRIC_STEPS -> snap.steps
                    HabitKind.METRIC_SLEEP -> snap.sleepMinutes
                    else -> 0L
                }
                if (value >= (h.healthTarget ?: Int.MAX_VALUE)) habits.complete(h, day)
            }
        return snap
    }
}
