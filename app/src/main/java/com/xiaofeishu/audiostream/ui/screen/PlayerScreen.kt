package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.ui.component.ConnectionStatus
import com.xiaofeishu.audiostream.ui.component.QualityIndicator
import com.xiaofeishu.audiostream.ui.component.StatsBar
import com.xiaofeishu.audiostream.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val mediaState = state.mediaState

    var currentPositionMs by remember { mutableLongStateOf(mediaState?.positionMs ?: 0L) }
    var lastKnownPositionMs by remember { mutableLongStateOf(mediaState?.positionMs ?: 0L) }
    var lastUpdatedTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(mediaState) {
        if (mediaState != null) {
            lastKnownPositionMs = mediaState.positionMs
            lastUpdatedTimeMs = System.currentTimeMillis()
            currentPositionMs = mediaState.positionMs
        }
    }

    LaunchedEffect(mediaState?.playing) {
        if (mediaState?.playing == true && mediaState.durationMs > 0) {
            while (true) {
                delay(250)
                val elapsed = System.currentTimeMillis() - lastUpdatedTimeMs
                val interpolated = lastKnownPositionMs + elapsed
                currentPositionMs = interpolated.coerceAtMost(mediaState.durationMs)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConnectionStatus(
            state = state.connectionState,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        state.error?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = err,
                color = if (state.connectionState == ConnectionState.CONNECTING)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        state.audioFormat?.let { fmt ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("音频格式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        QualityIndicator(quality = state.stats.quality)
                    }
                    InfoRow("采样率", "${fmt.sampleRate}Hz")
                    InfoRow("通道", "${fmt.channels}ch")
                    InfoRow("位深", "${fmt.bitsPerSample}bit")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            StatsBar(stats = state.stats, receivedBytes = state.receivedBytes)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "媒体控制",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val ms = state.mediaState
                    if (ms != null && (ms.title.isNotEmpty() || ms.artist.isNotEmpty())) {
                        Text(
                            text = ms.title.ifEmpty { "未知曲目" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        if (ms.artist.isNotEmpty()) {
                            Text(
                                text = ms.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (ms != null && ms.durationMs > 0) {
                        Slider(
                            value = currentPositionMs.toFloat().coerceIn(0f, ms.durationMs.toFloat()),
                            onValueChange = { currentPositionMs = it.toLong() },
                            onValueChangeFinished = {
                                viewModel.seekTo(currentPositionMs)
                            },
                            valueRange = 0f..ms.durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentPositionMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatDuration(ms.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.sendCommand(MediaAction.PREVIOUS) }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "上一曲")
                            Spacer(Modifier.width(4.dp))
                            Text("上一曲")
                        }
                        IconButton(onClick = { viewModel.sendCommand(MediaAction.PLAY_PAUSE) }) {
                            Icon(
                                if (ms?.playing == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (ms?.playing == true) "暂停" else "播放",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        OutlinedButton(onClick = { viewModel.sendCommand(MediaAction.NEXT) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "下一曲")
                            Spacer(Modifier.width(4.dp))
                            Text("下一曲")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "目标码率",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val presets = listOf(3072, 2048, 1536, 1024, 768, 512, 384, 256, 192, 128, 96, 64)
            presets.forEach { preset ->
                FilterChip(
                    selected = state.currentBitrate == preset,
                    onClick = { viewModel.setBitrate(preset) },
                    label = { Text("${preset}k") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "音量",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = state.volume,
            onValueChange = viewModel::setVolume,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("自动重连", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                Button(
                    onClick = viewModel::reconnectPending,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始连接") }
            }
            ConnectionState.CONNECTING -> {
                Button(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("取消连接") }
            }
            else -> {
                Button(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("断开连接") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
