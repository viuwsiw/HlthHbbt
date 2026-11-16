package com.tshk.hlthhbbty.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Плашка «BETA TEST» в стиле стекла. */
@Composable
fun BetaBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        "BETA TEST",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        modifier = modifier
            .glass(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFE52E71))), shape)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f))), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
