package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 固定档位滑块：只能停在 [values] 给出的离散档位上（按索引等距布点，档位值本身可以不等距）。
 *
 * 使用 Miuix 原生 [Slider]，拖动时把连续进度吸附到最近档位；
 * 只有吸附结果发生"档位变化"时才回调 [onValueCommitted]。
 * 一次拖动最多触发档位数次提交（而非每像素一次），
 * 对 set_bitrate 这类网络命令与 DataStore 持久化的压力可控。
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
        mutableIntStateOf(nearestIndex(values, currentValue))
    }
    // 外部值变化（如服务端单方下调码率、DataStore 异步加载完成）时同步到最近档位
    LaunchedEffect(currentValue, values) {
        sliderIndex = nearestIndex(values, currentValue)
    }
    val previewValue = values[sliderIndex.coerceIn(0, values.lastIndex)]

    Column(modifier = modifier) {
        Text(
            text = valueLabel(previewValue),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = sliderIndex.toFloat(),
            valueRange = 0f..values.lastIndex.toFloat().coerceAtLeast(1f),
            onValueChange = { raw ->
                val snapped = raw.roundToInt().coerceIn(0, values.lastIndex)
                if (snapped != sliderIndex) {
                    sliderIndex = snapped
                    onValueCommitted(values[snapped])
                }
            },
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
