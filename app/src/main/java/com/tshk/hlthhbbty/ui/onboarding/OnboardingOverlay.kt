package com.tshk.hlthhbbty.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

private data class Slide(val title: String, val text: String, val scene: Int)

private val slides = listOf(
    Slide("Свайпы между разделами", "Проведите влево или вправо по экрану — откроется соседний раздел. Работает, пока нижняя панель скрыта.", 0),
    Slide("Панель внизу страницы", "Нижняя панель появляется, когда вы долистали до конца — и не закрывает кнопки.", 1),
    Slide("Плеер в панели", "Когда играет музыка, потяните панель вверх — под разделами появится плеер.", 2),
    Slide("Главное — на главной", "Вода, шаги и сон всегда сверху. Привычки, настроение и музыка — в раскрывающемся блоке.", 3),
)

/** Обучение при первом входе: 4 коротких анимированных слайда. */
@Composable
fun OnboardingOverlay(onDone: () -> Unit) {
    val p = Liquid.palette
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { if (page > 0) page-- else onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null) {}, // перехватываем касания под оверлеем
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassCard(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
                },
                label = "slide",
            ) { i ->
                val slide = slides[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Scene(slide.scene, Modifier.fillMaxWidth().height(220.dp))
                    VSpace(16.dp)
                    Text(slide.title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, textAlign = TextAlign.Center)
                    VSpace(6.dp)
                    Text(slide.text, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, textAlign = TextAlign.Center)
                }
            }
            VSpace(16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                slides.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(width = if (i == page) 22.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape).background(if (i == page) p.accent else p.textSecondary.copy(alpha = 0.3f))
                    )
                }
            }
            VSpace(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Пропустить", onDone, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                GlassButton(
                    if (page == slides.lastIndex) "Начать" else "Далее",
                    { if (page == slides.lastIndex) onDone() else page++ },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Мини-телефон с анимированной демонстрацией жеста. */
@Composable
private fun Scene(scene: Int, modifier: Modifier) {
    val p = Liquid.palette
    val t by rememberInfiniteTransition(label = "demo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        val phoneW = size.height * 0.52f
        val phoneH = size.height
        val left = (size.width - phoneW) / 2
        val corner = CornerRadius(phoneW * 0.14f)
        val card = p.textSecondary.copy(alpha = 0.18f)
        val accent = p.accent
        // корпус
        drawRoundRect(p.textSecondary.copy(alpha = 0.10f), Offset(left, 0f), Size(phoneW, phoneH), corner)
        drawRoundRect(p.textSecondary.copy(alpha = 0.5f), Offset(left, 0f), Size(phoneW, phoneH), corner, style = Stroke(3.dp.toPx()))
        val pad = phoneW * 0.08f
        val innerL = left + pad
        val innerW = phoneW - pad * 2
        fun cardRow(y: Float, h: Float, color: Color = card, x: Float = innerL, w: Float = innerW) =
            drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h * 0.3f))

        clipRect(left, 0f, left + phoneW, phoneH) {
            when (scene) {
                0 -> { // свайп: страница уезжает влево, приезжает новая
                    val e = ease(t, 0.25f, 0.65f)
                    val shift = -e * phoneW
                    for (k in 0..1) {
                        val x = innerL + shift + k * phoneW
                        cardRow(phoneH * 0.10f, phoneH * 0.12f, if (k == 0) card else accent.copy(alpha = 0.35f), x)
                        cardRow(phoneH * 0.27f, phoneH * 0.22f, card, x)
                        cardRow(phoneH * 0.54f, phoneH * 0.16f, card, x)
                    }
                    finger(Offset(left + phoneW * (0.75f - 0.5f * e), phoneH * 0.6f), alpha = fingerAlpha(t), accent)
                }
                1 -> { // прокрутка вниз, панель появляется у конца
                    val e = ease(t, 0.15f, 0.6f)
                    val scroll = -e * phoneH * 0.35f
                    for (k in 0..4) cardRow(phoneH * (0.08f + k * 0.2f) + scroll, phoneH * 0.15f)
                    val barA = ease(t, 0.6f, 0.75f)
                    val barY = phoneH * 0.86f + (1 - barA) * phoneH * 0.1f
                    drawRoundRect(accent.copy(alpha = 0.55f * barA), Offset(innerL, barY), Size(innerW, phoneH * 0.09f), CornerRadius(phoneH * 0.045f))
                    finger(Offset(left + phoneW * 0.5f, phoneH * (0.7f - 0.3f * e)), fingerAlpha(t, 0.6f), accent)
                }
                2 -> { // свайп вверх по панели — раскрывается плеер
                    cardRow(phoneH * 0.08f, phoneH * 0.15f); cardRow(phoneH * 0.28f, phoneH * 0.22f)
                    val e = ease(t, 0.25f, 0.6f)
                    val barH = phoneH * (0.09f + 0.09f * e)
                    val barY = phoneH * 0.95f - barH
                    drawRoundRect(accent.copy(alpha = 0.5f), Offset(innerL, barY), Size(innerW, barH), CornerRadius(phoneH * 0.045f))
                    if (e > 0.5f) {
                        val a = (e - 0.5f) * 2
                        drawCircle(Color.White.copy(alpha = 0.8f * a), phoneH * 0.025f, Offset(innerL + innerW * 0.15f, barY + barH * 0.72f))
                        drawRoundRect(Color.White.copy(alpha = 0.6f * a), Offset(innerL + innerW * 0.28f, barY + barH * 0.66f), Size(innerW * 0.5f, phoneH * 0.02f), CornerRadius(4f))
                    }
                    finger(Offset(left + phoneW * 0.5f, phoneH * (0.9f - 0.2f * e)), fingerAlpha(t), accent)
                }
                else -> { // нажатие на блок — раскрываются строки
                    cardRow(phoneH * 0.06f, phoneH * 0.14f, accent.copy(alpha = 0.35f))
                    cardRow(phoneH * 0.23f, phoneH * 0.12f)
                    cardRow(phoneH * 0.38f, phoneH * 0.09f)
                    val e = ease(t, 0.35f, 0.6f)
                    for (k in 0..2) {
                        val a = (e * 3 - k).coerceIn(0f, 1f)
                        cardRow(phoneH * (0.51f + k * 0.13f), phoneH * 0.1f, card.copy(alpha = card.alpha * a))
                    }
                    val tap = if (t in 0.2f..0.35f) 0.85f else 1f
                    finger(Offset(left + phoneW * 0.6f, phoneH * 0.425f), fingerAlpha(t), accent, tap)
                }
            }
        }
    }
}

private fun ease(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

private fun fingerAlpha(t: Float, end: Float = 0.75f) = when {
    t < 0.1f -> t / 0.1f
    t > end + 0.1f -> 0f
    t > end -> 1f - (t - end) / 0.1f
    else -> 1f
}

private fun DrawScope.finger(center: Offset, alpha: Float, color: Color, scale: Float = 1f) {
    val r = 14.dp.toPx() * scale
    drawCircle(color.copy(alpha = 0.25f * alpha), r * 1.8f, center)
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), r, center)
    drawCircle(color.copy(alpha = alpha), r, center, style = Stroke(3.dp.toPx()))
}
