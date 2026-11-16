package com.tshk.hlthhbbty.data.repo

import com.tshk.hlthhbbty.data.db.DayNoteEntity
import com.tshk.hlthhbbty.data.db.NoteDao
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observe(userId: Long, day: Long): Flow<DayNoteEntity?> = dao.observe(userId, day)
    fun observeRange(userId: Long, from: Long, to: Long): Flow<List<DayNoteEntity>> = dao.observeRange(userId, from, to)
    suspend fun save(userId: Long, day: Long, mood: Int, note: String) =
        dao.upsert(DayNoteEntity(userId, day, mood, note.trim().take(500)))
}

val MoodEmoji = listOf("😣", "😕", "😐", "🙂", "😄")
