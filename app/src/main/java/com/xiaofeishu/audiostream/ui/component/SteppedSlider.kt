package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 固定档位滑块：只能停在 [values] 给出的离散档位上（按索引等距布点，档位值本身可以不等距）。
 * 拖动中实时预览档位文案，松手才通过 [onValueCommitted] 提交，
 * 避免拖动过程反复触发持久化/网络请求（如 set_bitrate 命令）。
 */
@Composable
fun SteppedSlider(
    values: List<Int>,
    currentValue: Int,
    onValueCommitted: (Int) -> Unit,
    valueLabel: (Int) -> String,
    modifier: Modifier = Modifier
) {
    var sliderIndex by remember(values) {
        mutableFloatStateOf(nearestIndex(values, currentValue).toFloat())
    }
    // 外部值变化（如服务端单方下调码率、DataStore 异步加载完成）时同步到最近档位
    LaunchedEffect(currentValue, values) {
        sliderIndex = nearestIndex(values, currentValue).toFloat()
    }
    val previewValue = values[sliderIndex.roundToInt().coerceIn(0, values.lastIndex)]

    Column(modifier = modifier) {
        Text(
            text = valueLabel(previewValue),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = sliderIndex,
            onValueChange = { sliderIndex = it },
            onValueChangeFinished = { onValueCommitted(previewValue) },
            valueRange = 0f..values.lastIndex.toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun nearestIndex(values: List<Int>, target: Int): Int {
    var best = 0
    var bestDiff = Int.MAX_VALUE
    for (i in values.indices) {
        val diff = abs(values[i] - target)
        if (diff < bestDiff) {
            bestDiff = diff
            best = i
        }
    }
    return best
}
