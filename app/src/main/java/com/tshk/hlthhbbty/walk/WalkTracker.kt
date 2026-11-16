package com.tshk.hlthhbbty.walk

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.WalkDao
import com.tshk.hlthhbbty.data.db.WalkEntity
import com.tshk.hlthhbbty.data.db.WalkPointEntity
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackPoint(val lat: Double, val lon: Double, val time: Long)

/** Текущая прогулка. Время считается по «чистому» движению — паузы не входят. */
data class WalkSession(
    val habitId: Long?,
    val userId: Long,
    val title: String,
    val startedAt: Long,
    val accumulatedMs: Long = 0,
    /** Момент последнего старта/продолжения, null — на паузе. */
    val runningSince: Long? = startedAt,
    val points: List<TrackPoint> = emptyList(),
    val distanceM: Double = 0.0,
    val steps: Int = 0,
    val hasStepSensor: Boolean = true,
    val lastAccuracy: Float? = null,
    /** Текущее положение (маячок) — обновляется и на паузе. */
    val current: TrackPoint? = null,
) {
    val paused get() = runningSince == null
    fun elapsedMs(now: Long = System.currentTimeMillis()) = accumulatedMs + (runningSince?.let { now - it } ?: 0)
    /** Шаги: с датчика, а если его нет — оценка по дистанции (~0,75 м на шаг). */
    val displaySteps get() = if (hasStepSensor) steps else (distanceM / 0.75).toInt()
}

/** Итог сохранённой прогулки — для экрана результата. */
data class WalkSummary(val walk: WalkEntity, val points: List<TrackPoint>)

class WalkTracker(
    private val context: Context,
    private val dao: WalkDao,
    private val habits: HabitRepository,
) {
    private val _session = MutableStateFlow<WalkSession?>(null)
    val session: StateFlow<WalkSession?> = _session.asStateFlow()

    private var stepBase: Float? = null

    fun observeHistory(userId: Long): Flow<List<WalkEntity>> = dao.observeWalks(userId)

    suspend fun points(walkId: Long): List<TrackPoint> = dao.points(walkId).map { TrackPoint(it.lat, it.lon, it.time) }

    suspend fun delete(walkId: Long) = dao.delete(walkId)

    fun start(userId: Long, habit: HabitEntity?) {
        stepBase = null
        _session.value = WalkSession(habit?.id, userId, habit?.title ?: "Прогулка", System.currentTimeMillis())
        ContextCompat.startForegroundService(context, Intent(context, WalkTrackingService::class.java))
    }

    fun pause() = _session.update { s ->
        if (s == null || s.paused) s else s.copy(accumulatedMs = s.elapsedMs(), runningSince = null)
    }

    fun resume() = _session.update { s ->
        if (s == null || !s.paused) s else s.copy(runningSince = System.currentTimeMillis())
    }

    /** Новая точка GPS. На паузе двигаем только маячок. */
    internal fun onLocation(loc: Location) {
        val point = TrackPoint(loc.latitude, loc.longitude, loc.time)
        _session.update { s ->
            if (s == null) return@update null
            val base = s.copy(current = point, lastAccuracy = loc.accuracy)
            if (s.paused || loc.accuracy > 35f) return@update base
            val last = s.points.lastOrNull()
            if (last == null) return@update base.copy(points = listOf(point))
            val d = FloatArray(1)
            Location.distanceBetween(last.lat, last.lon, point.lat, point.lon, d)
            // Фильтр дрожания GPS: игнорируем сдвиги меньше 3 м и нереальные скачки.
            if (d[0] < 3f || d[0] > 200f) base
            else base.copy(points = s.points + point, distanceM = s.distanceM + d[0])
        }
    }

    internal fun onStepCounter(totalSinceBoot: Float) {
        val s = _session.value ?: return
        if (s.paused) {
            // На паузе шаги не считаем: сдвигаем базу.
            stepBase = totalSinceBoot - s.steps
            return
        }
        val base = stepBase ?: (totalSinceBoot - s.steps).also { stepBase = it }
        _session.update { it?.copy(steps = (totalSinceBoot - base).toInt().coerceAtLeast(0)) }
    }

    internal fun setNoStepSensor() = _session.update { it?.copy(hasStepSensor = false) }

    /** Завершить: сохранить маршрут и отметить привычку. Возвращает итог или null (если отменено). */
    suspend fun finish(save: Boolean): WalkSummary? {
        val s = _session.value ?: return null
        _session.value = null
        context.stopService(Intent(context, WalkTrackingService::class.java))
        if (!save) return null
        val walk = WalkEntity(
            userId = s.userId, habitId = s.habitId, startedAt = s.startedAt,
            durationMs = s.elapsedMs(), distanceM = s.distanceM, steps = s.displaySteps,
        )
        val id = dao.insert(walk)
        dao.insertPoints(s.points.mapIndexed { i, p -> WalkPointEntity(id, i, p.lat, p.lon, p.time) })
        s.habitId?.let { hid -> habits.find(hid)?.let { habits.complete(it, today()) } }
        return WalkSummary(walk.copy(id = id), s.points)
    }
}

/** Ссылка на маршрут в Google Картах (до 9 промежуточных точек). */
fun googleMapsRoute(points: List<TrackPoint>): String? {
    if (points.size < 2) return null
    val o = points.first()
    val d = points.last()
    val mids = if (points.size > 2) {
        val inner = points.subList(1, points.size - 1)
        val step = maxOf(1, inner.size / 9)
        inner.filterIndexed { i, _ -> i % step == 0 }.take(9)
    } else emptyList()
    return buildString {
        append("https://www.google.com/maps/dir/?api=1&travelmode=walking")
        append("&origin=${o.lat},${o.lon}&destination=${d.lat},${d.lon}")
        if (mids.isNotEmpty()) append("&waypoints=" + mids.joinToString("%7C") { "${it.lat},${it.lon}" })
    }
}
