package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.contextClick
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.ui.component.ConnectionStatus
import com.xiaofeishu.audiostream.ui.component.QualityIndicator
import com.xiaofeishu.audiostream.ui.component.StatsBar
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.ui.theme.AppColors
import com.xiaofeishu.audiostream.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 码率固定档位（kbps），从低到高。 */
private val BITRATE_PRESETS = listOf(64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072)

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
        ) {
            ConnectionStatus(
                state = state.connectionState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )

            state.error?.let { err ->
                Text(
                    text = err,
                    color = if (state.connectionState == ConnectionState.CONNECTING) {
                        AppColors.warning
                    } else {
                        AppColors.error
                    },
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp)
                )
            }

            state.audioFormat?.let { fmt ->
                SmallTitle(text = "音频格式")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp, 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("音频格式", fontWeight = FontWeight.Bold)
                        QualityIndicator(quality = state.stats.quality)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("采样率", "${fmt.sampleRate}Hz")
                    InfoRow("通道", "${fmt.channels}ch")
                    InfoRow("位深", "${fmt.bitsPerSample}bit")
                }

                Spacer(modifier = Modifier.height(8.dp))
                val hideHint by viewModel.hideSinkLatencyHint.collectAsState()
                StatsBar(
                    stats = state.stats,
                    receivedBytes = state.receivedBytes,
                    showSinkLatencyHint = !hideHint,
                    onIgnoreSinkLatencyHint = viewModel::ignoreSinkLatencyHint,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            if (state.isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                SmallTitle(text = "媒体控制")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp, 16.dp)
                ) {
                    val ms = state.mediaState
                    if (ms != null && (ms.title.isNotEmpty() || ms.artist.isNotEmpty())) {
                        Text(
                            text = ms.title.ifEmpty { "未知曲目" },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        if (ms.artist.isNotEmpty()) {
                            Text(
                                text = ms.artist,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (ms != null && ms.durationMs > 0) {
                        val duration = ms.durationMs.toFloat().coerceAtLeast(1f)
                        Slider(
                            value = currentPositionMs.toFloat().coerceIn(0f, duration),
                            valueRange = 0f..duration,
                            // Miuix 的 Slider 没有 onValueChangeFinished，进度条改为拖动即定位；
                            // 服务端 seek 有节流，短时间多次请求不会造成压力。
                            onValueChange = { value ->
                                currentPositionMs = value.toLong()
                                viewModel.seekTo(currentPositionMs)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentPositionMs),
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize
                            )
                            Text(
                                text = formatDuration(ms.durationMs),
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            haptic.contextClick(hapticEnabled)
                            viewModel.sendCommand(MediaAction.PREVIOUS)
                        }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一曲",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                viewModel.sendCommand(MediaAction.PLAY_PAUSE)
                            },
                            backgroundColor = MiuixTheme.colorScheme.primary,
                            minWidth = 56.dp,
                            minHeight = 56.dp
                        ) {
                            Icon(
                                imageVector = if (ms?.playing == true) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = if (ms?.playing == true) "暂停" else "播放",
                                tint = MiuixTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = {
                            haptic.contextClick(hapticEnabled)
                            viewModel.sendCommand(MediaAction.NEXT)
                        }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一曲",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 服务端静音：只关电脑扬声器。采集在端点静音之前，手机端串流照常播放。
                    val serverMuted = ms?.muted == true
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (serverMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = if (serverMuted) AppColors.error else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                viewModel.setServerMute(!serverMuted)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (serverMuted) "电脑已静音，点击恢复" else "静音电脑（手机继续播放）")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "目标码率")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp, 16.dp)
            ) {
                SteppedSlider(
                    values = BITRATE_PRESETS,
                    currentValue = state.currentBitrate,
                    onValueCommitted = { bitrate ->
                        haptic.contextClick(hapticEnabled)
                        viewModel.setBitrate(bitrate)
                    },
                    valueLabel = { "$it kbps" },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "音量")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp, 16.dp)
            ) {
                Slider(
                    value = state.volume,
                    valueRange = 0f..1f,
                    onValueChange = viewModel::setVolume,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                SwitchPreference(
                    checked = state.autoReconnect,
                    onCheckedChange = { enabled ->
                        haptic.contextClick(hapticEnabled)
                        viewModel.setAutoReconnect(enabled)
                    },
                    title = "自动重连",
                    summary = "断开后自动尝试重新连接"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (state.connectionState) {
                ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                    Button(
                        onClick = {
                            haptic.contextClick(hapticEnabled)
                            viewModel.reconnectPending()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text("开始连接")
                    }
                }
                ConnectionState.CONNECTING -> {
                    Button(
                        onClick = {
                            haptic.contextClick(hapticEnabled)
                            viewModel.disconnect()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text("取消连接")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            haptic.contextClick(hapticEnabled)
                            viewModel.disconnect()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text("断开连接")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
