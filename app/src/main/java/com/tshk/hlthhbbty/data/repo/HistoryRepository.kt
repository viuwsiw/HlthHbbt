package com.tshk.hlthhbbty.data.repo

import com.tshk.hlthhbbty.data.HistoryData
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Вся история пользователя одним потоком — для статистики и достижений. */
class HistoryRepository(
    private val users: UserRepository,
    private val habits: HabitRepository,
    private val water: WaterRepository,
    private val notes: NoteRepository,
) {
    fun observe(userId: Long, day: Long = today()): Flow<HistoryData> = combine(
        combine(habits.observeHabits(userId), habits.observeChecks(userId, 0, day), habits.observeFreezes(userId, 0, day)) { h, c, f -> Triple(h, c, f) },
        water.observeTotals(userId, 0, day),
        users.observe(userId),
        notes.observeRange(userId, 0, day),
    ) { (h, c, f), w, user, n ->
        HistoryData(day, h, c, f, w, user?.waterGoalMl ?: 2000, n)
    }

    suspend fun once(userId: Long) = observe(userId).first()
}
