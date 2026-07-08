package com.xiaofeishu.audiostream.data

import android.media.AudioTrack
import com.xiaofeishu.audiostream.audio.AudioPlayer
import com.xiaofeishu.audiostream.audio.AudioPlayerFactory
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.domain.model.MediaState
import com.xiaofeishu.audiostream.domain.model.PlaybackState
import com.xiaofeishu.audiostream.domain.model.Quality
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.model.StreamStats
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import com.xiaofeishu.audiostream.di.AppScope
import com.xiaofeishu.audiostream.di.PlaybackDispatcher
import com.xiaofeishu.audiostream.network.AudioEvent
import com.xiaofeishu.audiostream.network.protocol.AudioProtocol
import com.xiaofeishu.audiostream.network.protocol.AudioProtocolFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 流连接/播放仓库实现（@Singleton）。Service 与 ViewModel 共享同一实例。
 *
 * 职责：
 * - 协议选择（枚举 when，无 else）
 * - 连接/断开、音量
 * - 自动重连（指数退避 1s→30s，仅在 autoReconnect 且非用户主动断开时触发）
 * - 码率滑动窗口统计、客户端缓冲延迟、连接质量评级
 * - 错误上屏（PlaybackState.error）
 */
@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val protocolFactory: AudioProtocolFactory,
    private val playerFactory: AudioPlayerFactory,
    private val settingsRepository: SettingsRepository,
    @AppScope private val appScope: CoroutineScope,
    @PlaybackDispatcher private val playbackDispatcher: ExecutorCoroutineDispatcher
) : StreamRepository {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var collectJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var autoReconnectEnabled = false
    @Volatile private var activeProtocol: AudioProtocol? = null
    @Volatile private var localPlaybackAllowed = true
    @Volatile private var lastAudioDataAtMs = 0L

    init {
        // 跟随设置中的自动重连开关
        appScope.launch {
            settingsRepository.autoReconnect.collect { enabled ->
                autoReconnectEnabled = enabled
                _state.value = _state.value.copy(autoReconnect = enabled)
            }
        }
        // 跟随音量设置并应用到播放器
        appScope.launch {
            settingsRepository.volume.collect { vol ->
                _state.value = _state.value.copy(volume = vol)
            }
        }
        appScope.launch {
            settingsRepository.targetBitrate.collect { bitrate ->
                _state.value = _state.value.copy(currentBitrate = bitrate)
            }
        }
    }

    override fun connect(server: ServerInfo) {
        if (_state.value.connectionState == ConnectionState.CONNECTING) return
        manualDisconnect = false
        localPlaybackAllowed = true
        lastAudioDataAtMs = 0L
        _state.value = _state.value.copy(
            connectionState = ConnectionState.CONNECTING,
            server = server,
            receivedBytes = 0L,
            error = null,
            reconnectAttempt = 0,
            stats = StreamStats()
        )
        // 记录历史
        appScope.launch {
            settingsRepository.addConnection(
                com.xiaofeishu.audiostream.domain.model.ConnectionRecord(
                    address = server.address,
                    port = server.port,
                    protocol = server.protocol,
                    lastConnected = System.currentTimeMillis(),
                    connectCount = 1
                )
            )
        }
        startSession(server, attempt = 0)
    }

    private fun startSession(server: ServerInfo, attempt: Int) {
        collectJob?.cancel()
        val protocol = protocolFactory.create(server.protocol)
        activeProtocol = protocol
        collectJob = appScope.launch {
            try {
                val targetBitrate = settingsRepository.loadTargetBitrate()
                _state.value = _state.value.copy(currentBitrate = targetBitrate)
                // 协议层已有握手超时（WebSocket handshakeTimeout），
                // 会及时 emit Error/Disconnected 事件，此处 collectLatest 只需正常分发。
                protocol.connect(server.address, server.port, targetBitrate).collect { event ->
                    when (event) {
                        is AudioEvent.Connected -> onConnected(server, event)
                        is AudioEvent.AudioData -> onAudioData(event)
                        is AudioEvent.Disconnected -> onDisconnected(server, event.reason, attempt)
                        is AudioEvent.Error -> onError(server, event.exception, attempt)
                        is AudioEvent.StateUpdate -> onStateUpdate(event.state)
                        is AudioEvent.BitrateChanged -> onBitrateChanged(event)
                    }
                }
            } catch (e: Exception) {
                onError(server, e, attempt)
            }
        }
    }

    private fun onConnected(server: ServerInfo, event: AudioEvent.Connected) {
        val player = playerFactory.create()
        player.initialize(event.format)
        player.setVolume(_state.value.volume)
        player.writeErrorListener = AudioPlayer.WriteErrorListener { errorCode, _ ->
            if (errorCode == AudioTrack.ERROR_DEAD_OBJECT) {
                throw RuntimeException("AudioTrack 已失效，需重建连接")
            }
        }
        currentPlayer = player
        val targetBitrate = _state.value.currentBitrate
        bitrateTracker.reset()
        _state.value = _state.value.copy(
            connectionState = ConnectionState.CONNECTED,
            audioFormat = event.format,
            server = server,
            currentBitrate = targetBitrate,
            error = null,
            reconnectAttempt = 0
        )
        appScope.launch {
            delay(STATE_REFRESH_DELAY_MS)
            if (activeProtocol?.isConnected == true) {
                activeProtocol?.send(MediaAction.GET_STATE.toJson())
            }
        }
    }

    private suspend fun onAudioData(event: AudioEvent.AudioData) {
        val player = currentPlayer ?: return
        if (localPlaybackAllowed) {
            // AudioTrack.write 是阻塞调用，独占播放线程，避免与 Default 调度器上的
            // 状态/flow 逻辑抢线程导致调度抖动 → 背压 → 丢帧（长播卡顿根因）。
            withContext(playbackDispatcher) {
                if (!player.isPlaying) player.resume()
                player.play(event.data)
            }
        }
        val now = System.currentTimeMillis()
        lastAudioDataAtMs = now
        bitrateTracker.add(event.data.size, now)
        val received = _state.value.receivedBytes + event.data.size
        val stats = computeStats(now)
        val currentMediaState = _state.value.mediaState
        val streamPlaying = localPlaybackAllowed && player.isPlaying
        _state.value = _state.value.copy(
            connectionState = if (streamPlaying) ConnectionState.PLAYING else _state.value.connectionState,
            receivedBytes = received,
            stats = stats,
            mediaState = if (streamPlaying) {
                currentMediaState?.copy(playing = true) ?: MediaState(playing = true)
            } else {
                currentMediaState
            }
        )
    }

    private fun onDisconnected(server: ServerInfo, reason: String, attempt: Int) {
        teardownPlayer()
        _state.value = _state.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            error = reason,
            mediaState = null
        )
        maybeReconnect(server, attempt)
    }

    private fun onError(server: ServerInfo, error: Throwable, attempt: Int) {
        teardownPlayer()
        _state.value = _state.value.copy(
            connectionState = ConnectionState.ERROR,
            error = error.message ?: error::class.java.simpleName,
            mediaState = null
        )
        maybeReconnect(server, attempt)
    }

    private fun maybeReconnect(server: ServerInfo, attempt: Int) {
        if (manualDisconnect || !autoReconnectEnabled) return
        val nextAttempt = attempt + 1
        val baseDelay = min(RECONNECT_MAX_DELAY_MS, RECONNECT_BASE_DELAY_MS shl (nextAttempt - 1))
        // 用 attempt 派生抖动（避免受限环境使用随机）：±10% 内确定值
        val jitter = (baseDelay / 10) * ((nextAttempt % 3) - 1)
        val delayMs = max(RECONNECT_BASE_DELAY_MS, baseDelay + jitter)
        _state.value = _state.value.copy(
            connectionState = ConnectionState.CONNECTING,
            error = "重连中（第 $nextAttempt 次，${delayMs / 1000}s 后重试）",
            reconnectAttempt = nextAttempt
        )
        reconnectJob?.cancel()
        reconnectJob = appScope.launch {
            delay(delayMs)
            if (!manualDisconnect && autoReconnectEnabled) {
                startSession(server, nextAttempt)
            }
        }
    }

    override fun disconnect() {
        manualDisconnect = true
        localPlaybackAllowed = true
        lastAudioDataAtMs = 0L
        reconnectJob?.cancel()
        collectJob?.cancel()
        activeProtocol = null
        teardownPlayer()
        _state.value = _state.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            audioFormat = null,
            receivedBytes = 0L,
            stats = StreamStats(),
            error = null,
            reconnectAttempt = 0,
            mediaState = null
        )
    }

    override fun sendCommand(action: MediaAction) {
        if (action == MediaAction.PLAY_PAUSE) {
            val isCurrentlyPlaying = _state.value.mediaState?.playing == true || currentPlayer?.isPlaying == true
            val shouldPlay = !isCurrentlyPlaying
            localPlaybackAllowed = shouldPlay
            updateLocalMediaState(shouldPlay)
            syncPlayerPlayback(shouldPlay)
        }
        val protocol = activeProtocol
        if (protocol?.isConnected == true) {
            protocol.send(action.toJson())
        }
    }

    override fun seekTo(positionMs: Long) {
        val protocol = activeProtocol ?: return
        if (!protocol.isConnected) return
        protocol.send(MediaAction.SEEK_TO.toJson(positionMs))
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = clamped)
        currentPlayer?.setVolume(clamped)
        appScope.launch { settingsRepository.saveVolume(clamped) }
    }

    override fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        _state.value = _state.value.copy(autoReconnect = enabled)
        appScope.launch { settingsRepository.saveAutoReconnect(enabled) }
    }

    override fun setBitrate(bitrate: Int) {
        val normalized = normalizeTargetBitrate(bitrate)
        activeProtocol?.setBitrate(normalized)
        _state.value = _state.value.copy(currentBitrate = normalized)
        appScope.launch { settingsRepository.saveTargetBitrate(normalized) }
    }

    private fun onBitrateChanged(event: AudioEvent.BitrateChanged) {
        val player = currentPlayer
        val shouldPlay = localPlaybackAllowed && (_state.value.mediaState?.playing == true || player?.isPlaying == true)
        if (player != null) {
            // 仅当 PCM 格式（采样率/通道/位深）真正变化才重建 AudioTrack。
            // 服务端同格式降比特率不重建，避免 flush 掉已缓冲 PCM 造成断帧/杂音；
            // 重建后强制 start 并自检，AudioPlayer.start() 静默吞 IllegalStateException 会导致
            // _isPlaying 没置上 → 后续 play 丢数据无声，这里兜一次 recover。
            val current = player.currentStreamFormat()
            if (current == null || !sameFormat(current, event.format)) {
                player.initialize(event.format)
                player.setVolume(_state.value.volume)
                if (shouldPlay) {
                    player.start()
                    if (!player.isPlaying) player.resume()
                }
            }
        }
        _state.value = _state.value.copy(
            audioFormat = event.format,
            currentBitrate = event.bitrate
        )
        // 仅在用户主动 setBitrate 时持久化，服务端单方降速不写回 DataStore，
        // 避免临时降速永久拉低下次连接的起步码率。
    }

    private fun sameFormat(a: com.xiaofeishu.audiostream.domain.model.AudioFormat, b: com.xiaofeishu.audiostream.domain.model.AudioFormat): Boolean =
        a.sampleRate == b.sampleRate && a.channels == b.channels && a.bitsPerSample == b.bitsPerSample

    private fun onStateUpdate(mediaState: MediaState) {
        if (mediaState.playing) {
            localPlaybackAllowed = true
        }
        // Some desktop apps do not expose playback to MSTC/SMTC, so their state can be
        // "not playing" while the PCM stream is still active. Keep local audio alive in that case.
        val hasRecentAudio = System.currentTimeMillis() - lastAudioDataAtMs <= STREAM_ACTIVE_GRACE_MS
        val effectivePlaying = mediaState.playing ||
            (localPlaybackAllowed && hasRecentAudio && currentPlayer?.isPlaying == true)
        syncPlayerPlayback(effectivePlaying)
        _state.value = _state.value.copy(
            connectionState = if (effectivePlaying) ConnectionState.PLAYING else ConnectionState.CONNECTED,
            mediaState = mediaState.copy(playing = effectivePlaying)
        )
    }

    private fun updateLocalMediaState(playing: Boolean) {
        _state.value = _state.value.copy(
            connectionState = if (playing) ConnectionState.PLAYING else ConnectionState.CONNECTED,
            mediaState = _state.value.mediaState?.copy(playing = playing) ?: MediaState(playing = playing)
        )
    }

    private fun syncPlayerPlayback(playing: Boolean) {
        val player = currentPlayer ?: return
        if (playing) {
            if (!player.isPlaying) player.resume()
        } else {
            if (player.isPlaying) player.pause()
        }
    }

    private var currentPlayer: AudioPlayer? = null

    private fun teardownPlayer() {
        currentPlayer?.release()
        currentPlayer = null
    }

    /** 计算码率/缓冲延迟/质量。 */
    private fun computeStats(now: Long): StreamStats {
        val bitrateKbps = bitrateTracker.bitrateKbps(now)
        val player = currentPlayer
        val format = _state.value.audioFormat
        val bufferLatencyMs = if (player != null && format != null && format.bytesPerFrame > 0 && format.sampleRate > 0) {
            val headFrames = player.playbackHeadPositionFrames()
            val writtenFrames = player.totalWrittenBytes / format.bytesPerFrame
            val lagFrames = (writtenFrames - headFrames).coerceAtLeast(0L)
            (lagFrames * 1000 / format.sampleRate)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else 0
        val quality = when {
            bitrateKbps <= 0 -> Quality.UNKNOWN
            bufferLatencyMs > 500 -> Quality.POOR
            bufferLatencyMs > 150 -> Quality.FAIR
            else -> Quality.GOOD
        }
        return StreamStats(bitrateKbps, bufferLatencyMs, quality)
    }

    /** ~1 秒滑动窗口码率跟踪。 */
    private val bitrateTracker = object {
        private val window = ArrayDeque<Pair<Long, Int>>()  // timestampMs -> bytes
        private var windowBytes = 0
        @Synchronized fun reset() { window.clear(); windowBytes = 0 }
        @Synchronized fun add(bytes: Int, now: Long) {
            window.addLast(now to bytes)
            windowBytes += bytes
            val cutoff = now - WINDOW_MS
            while (window.isNotEmpty() && window.first().first < cutoff) {
                windowBytes -= window.removeFirst().second
            }
        }
        @Synchronized fun bitrateKbps(now: Long): Int {
            val cutoff = now - WINDOW_MS
            while (window.isNotEmpty() && window.first().first < cutoff) {
                windowBytes -= window.removeFirst().second
            }
            return if (window.isEmpty()) 0 else (windowBytes * 8 / WINDOW_MS).toInt()
        }
    }

    companion object {
        private const val WINDOW_MS = 1000L
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val DEFAULT_TARGET_BITRATE = 1536
        private const val STATE_REFRESH_DELAY_MS = 500L
        private const val STREAM_ACTIVE_GRACE_MS = 2_000L

        private fun normalizeTargetBitrate(bitrate: Int): Int {
            return if (bitrate > 0) bitrate else DEFAULT_TARGET_BITRATE
        }
    }
}
