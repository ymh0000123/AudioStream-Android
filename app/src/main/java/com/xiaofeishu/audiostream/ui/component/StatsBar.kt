package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.StreamStats

/** 黄色警告色（amber），浅色/深色主题下均可读。 */
private val WarningColor = Color(0xFFF9A825)

/**
 * 实时统计条：码率、已接收、缓冲延迟；蓝牙输出时额外显示链路延迟与降延迟警告。
 * 缓冲延迟是 app 可控部分（跳帧追赶收敛到档位值）；链路延迟是系统混音+蓝牙
 * 编解码/传输/耳机缓冲的固有部分，只能靠换编解码器/耳机低延迟模式降低。
 * 警告可通过"忽略"隐藏（持久化），在设置页可恢复显示。
 */
@Composable
fun StatsBar(
    stats: StreamStats,
    receivedBytes: Long,
    showSinkLatencyHint: Boolean = true,
    onIgnoreSinkLatencyHint: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("码率", "${stats.bitrateKbps} kbps")
                StatItem("已接收", formatBytes(receivedBytes))
                StatItem("缓冲延迟", "${stats.bufferLatencyMs} ms")
                if (stats.bluetoothDevice != null) {
                    StatItem("链路延迟", stats.sinkLatencyMs?.let { "$it ms" } ?: "—")
                }
            }
            val device = stats.bluetoothDevice
            if (device != null && showSinkLatencyHint) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = WarningColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "蓝牙输出（${device.ifBlank { "未知设备" }}）：链路延迟为编解码器/耳机固有，" +
                                "不计入缓冲延迟；开启耳机低延迟（游戏）模式或在系统中切换 aptX/LE Audio 可降低。",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningColor
                        )
                    }
                    TextButton(
                        onClick = onIgnoreSinkLatencyHint,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("忽略", style = MaterialTheme.typography.labelMedium, color = WarningColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.2f MB", bytes / 1024.0 / 1024.0)
}
