package com.tshk.hlthhbbty.ui.walk

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.WalkEntity
import com.tshk.hlthhbbty.walk.TrackPoint
import com.tshk.hlthhbbty.walk.WalkSession
import com.tshk.hlthhbbty.walk.WalkSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WalkViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val habitId = handle.get<String>("id")?.toLongOrNull()?.takeIf { it > 0 }
    private val tracker = c.walks

    val session: StateFlow<WalkSession?> = tracker.session

    val history: StateFlow<List<WalkEntity>> = c.auth.currentUserId.filterNotNull()
        .flatMapLatest { tracker.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _habit = MutableStateFlow<HabitEntity?>(null)
    val habit: StateFlow<HabitEntity?> = _habit.asStateFlow()

    /** Итог только что завершённой прогулки. */
    private val _summary = MutableStateFlow<WalkSummary?>(null)
    val summary: StateFlow<WalkSummary?> = _summary.asStateFlow()

    /** Маршрут выбранной прошлой прогулки. */
    private val _viewing = MutableStateFlow<Pair<WalkEntity, List<TrackPoint>>?>(null)
    val viewing: StateFlow<Pair<WalkEntity, List<TrackPoint>>?> = _viewing.asStateFlow()

    init {
        viewModelScope.launch { _habit.value = habitId?.let { c.habits.find(it) } }
    }

    fun start() = viewModelScope.launch {
        _summary.value = null
        _viewing.value = null
        tracker.start(c.auth.currentUserId.filterNotNull().first(), _habit.value)
    }

    fun pause() = tracker.pause()
    fun resume() = tracker.resume()

    fun finish(save: Boolean) = viewModelScope.launch { _summary.value = tracker.finish(save) }

    fun view(walk: WalkEntity) = viewModelScope.launch {
        _summary.value = null
        _viewing.value = walk to tracker.points(walk.id)
    }

    fun closeView() { _viewing.value = null; _summary.value = null }

    fun delete(walk: WalkEntity) = viewModelScope.launch {
        tracker.delete(walk.id)
        if (_viewing.value?.first?.id == walk.id) _viewing.value = null
    }
}
