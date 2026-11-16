package com.tshk.hlthhbbty.data.repo

import com.tshk.hlthhbbty.data.db.DayTotal
import com.tshk.hlthhbbty.data.db.WaterDao
import com.tshk.hlthhbbty.data.db.WaterLogEntity
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.Flow

class WaterRepository(private val dao: WaterDao) {
    fun observeToday(userId: Long, day: Long = today()): Flow<Int> = dao.observeTotal(userId, day)

    fun observeTotals(userId: Long, fromDay: Long, toDay: Long): Flow<List<DayTotal>> =
        dao.observeTotals(userId, fromDay, toDay)

    fun observeAllTime(userId: Long): Flow<Int> = dao.observeAllTime(userId)

    suspend fun add(userId: Long, amountMl: Int) {
        dao.insert(WaterLogEntity(userId = userId, amountMl = amountMl, epochDay = today()))
    }

    suspend fun undoLast(userId: Long) = dao.deleteLast(userId, today())
}
