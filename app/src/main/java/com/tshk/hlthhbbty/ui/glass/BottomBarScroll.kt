package com.tshk.hlthhbbty.ui.glass

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/** true — список долистан до конца (или не прокручивается): можно показать нижнюю панель. */
val LocalBottomBarAtEnd = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(true) }

/** Сообщает панели навигации, долистан ли список до самого низа. */
@Composable
fun ReportScrollEnd(state: LazyListState) {
    val atEnd = LocalBottomBarAtEnd.current
    LaunchedEffect(state) {
        snapshotFlow { !state.canScrollForward }.collect { atEnd.value = it }
    }
}
