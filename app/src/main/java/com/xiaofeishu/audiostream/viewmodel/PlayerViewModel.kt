package com.xiaofeishu.audiostream.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaofeishu.audiostream.domain.model.AudioFormat
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.domain.model.MediaState
import com.xiaofeishu.audiostream.domain.model.PlaybackState
import com.xiaofeishu.audiostream.domain.model.Quality
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.model.StreamStats
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放页 ViewModel。映射 [StreamRepository.state] 为 UI 状态，转发连接/断开/音量命令。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = streamRepository.state
        .map { state -> state.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = streamRepository.state.value.toUiState()
        )

    /** 是否忽略蓝牙链路延迟警告提示（持久化，可在设置页恢复）。 */
    val hideSinkLatencyHint: StateFlow<Boolean> = settingsRepository.hideSinkLatencyHint

    fun ignoreSinkLatencyHint() {
        viewModelScope.launch { settingsRepository.saveHideSinkLatencyHint(true) }
    }

    fun connect(server: ServerInfo) = streamRepository.connect(server)
    fun disconnect() = streamRepository.disconnect()
    fun setServerMute(muted: Boolean) = streamRepository.setServerMute(muted)

    /** 重连到上一次选择的服务器（用于播放页的"开始连接"）。 */
    fun reconnectPending() {
        val server = streamRepository.state.value.server
        if (server != null) streamRepository.connect(server)
    }

    fun setVolume(volume: Float) = streamRepository.setVolume(volume)

    fun setAutoReconnect(enabled: Boolean) = streamRepository.setAutoReconnect(enabled)

    fun sendCommand(action: MediaAction) = streamRepository.sendCommand(action)

    fun seekTo(positionMs: Long) = streamRepository.seekTo(positionMs)

    fun setBitrate(bitrate: Int) = streamRepository.setBitrate(bitrate)

    /** 待连接信息（由 Home 页传入或从历史/收藏选取）。Activity 也可直接用 ServerInfo 调 connect。 */
    fun connectManual(address: String, port: Int, protocol: com.xiaofeishu.audiostream.domain.model.Protocol) {
        connect(ServerInfo(name = address, address = address, port = port, protocol = protocol))
    }
}

/** 播放页 UI 状态。 */
data class PlayerUiState(
    val connectionState: ConnectionState,
    val audioFormat: AudioFormat?,
    val formatNegotiatedNote: String?,
    val volume: Float,
    val receivedBytes: Long,
    val stats: StreamStats,
    val error: String?,
    val server: ServerInfo?,
    val autoReconnect: Boolean,
    val reconnectAttempt: Int,
    val mediaState: MediaState? = null,
    val currentBitrate: Int = 1536
) {
    val isConnected: Boolean get() = connectionState == ConnectionState.PLAYING || connectionState == ConnectionState.CONNECTED
    val isActive: Boolean get() = connectionState.isActive
    val quality: Quality get() = stats.quality
}

private fun PlaybackState.toUiState() = PlayerUiState(
    connectionState = connectionState,
    audioFormat = audioFormat,
    formatNegotiatedNote = null,
    volume = volume,
    receivedBytes = receivedBytes,
    stats = stats,
    error = error,
    server = server,
    autoReconnect = autoReconnect,
    reconnectAttempt = reconnectAttempt,
    mediaState = mediaState,
    currentBitrate = currentBitrate
)
