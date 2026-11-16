package com.tshk.hlthhbbty.ui.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.ui.theme.Liquid
import java.io.File

val PresetAvatars = listOf(
    R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4,
    R.drawable.avatar_5, R.drawable.avatar_6, R.drawable.avatar_7, R.drawable.avatar_8,
    R.drawable.avatar_9, R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
    R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16,
)

fun presetAvatar(index: Int) = "preset:$index"

/** Аватар пользователя со стеклянным ободком. */
@Composable
fun UserAvatar(avatar: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val ring = Brush.linearGradient(listOf(p.rimBright, p.accent.copy(alpha = 0.7f), p.rimFaint))
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .glass(CircleShape)
            .border(maxOf(2.dp, size / 32), ring, CircleShape)
            .padding(maxOf(3.dp, size / 28)),
        contentAlignment = Alignment.Center,
    ) {
        val mod = Modifier.fillMaxSize().clip(CircleShape)
        when {
            avatar?.startsWith("preset:") == true -> {
                val idx = avatar.removePrefix("preset:").toIntOrNull()?.coerceIn(PresetAvatars.indices) ?: 0
                Image(painterResource(PresetAvatars[idx]), name, mod, contentScale = ContentScale.Crop)
            }
            avatar?.startsWith("file:") == true -> AsyncImage(
                model = File(avatar.removePrefix("file:")),
                contentDescription = name,
                modifier = mod,
                contentScale = ContentScale.Crop,
            )
            else -> Box(
                mod.background(Brush.linearGradient(listOf(p.accent, p.accentSecondary))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = p.onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp,
                )
            }
        }
    }
}
