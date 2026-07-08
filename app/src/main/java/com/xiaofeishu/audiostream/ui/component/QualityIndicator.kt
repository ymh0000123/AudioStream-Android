package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.Quality

/**
 * 连接质量指示徽章。
 */
@Composable
fun QualityIndicator(
    quality: Quality,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (quality) {
        Quality.GOOD -> "好" to Color(0xFF4ADE80)
        Quality.FAIR -> "一般" to Color(0xFFFBBF24)
        Quality.POOR -> "差" to Color(0xFFF87171)
        Quality.UNKNOWN -> "—" to MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "质量: $text",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
