package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.StreamStats

/**
 * 实时统计条：码率、已接收、缓冲延迟；蓝牙输出时额外显示链路延迟与降延迟提示。
 * 缓冲延迟是 app 可控部分（跳帧追赶收敛到档位值）；链路延迟是系统混音+蓝牙
 * 编解码/传输/耳机缓冲的固有部分，只能靠换编解码器/耳机低延迟模式降低。
 */
@Composable
fun StatsBar(
    stats: StreamStats,
    receivedBytes: Long,
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
            stats.bluetoothDevice?.let { device ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "蓝牙输出（${device.ifBlank { "未知设备" }}）：链路延迟为编解码器/耳机固有，" +
                        "不计入缓冲延迟；开启耳机低延迟（游戏）模式或在系统中切换 aptX/LE Audio 可降低。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
