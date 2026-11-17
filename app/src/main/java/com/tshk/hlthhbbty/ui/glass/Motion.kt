package com.tshk.hlthhbbty.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Каскадное появление карточки: лёгкий подъём, проявление и «доувеличение» на пружине.
 * Проигрывается один раз — при прокрутке туда-обратно не повторяется.
 */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index.coerceAtMost(8) * 55L)
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
            played = true
        }
    }
    return graphicsLayer {
        val v = progress.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 48f
        val s = 0.94f + 0.06f * v
        scaleX = s
        scaleY = s
    }
}
