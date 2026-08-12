package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.Quality
import com.xiaofeishu.audiostream.ui.theme.AppColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 连接质量指示徽章。
 */
@Composable
fun QualityIndicator(
    quality: Quality,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (quality) {
        Quality.GOOD -> "好" to AppColors.success
        Quality.FAIR -> "一般" to AppColors.warning
        Quality.POOR -> "差" to AppColors.error
        Quality.UNKNOWN -> "—" to MiuixTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "质量: $text",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = color
        )
    }
}
