package com.xiaofeishu.audiostream.data

import android.media.AudioTrack
import android.os.SystemClock
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
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
 * - 积压回落（窗口最小积压超出目标水位即丢弃净冗余，卡顿后延迟可回落）
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
    private var watchdogJob: Job? = null
    private var playbackJob: Job? = null
    @Volatile private var playbackQueue: PlaybackQueue? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var autoReconnectEnabled = false
    @Volatile private var activeProtocol: AudioProtocol? = null
    @Volatile private var localPlaybackAllowed = true
    @Volatile private var lastAudioDataAtMs = 0L
    @Volatile private var lastServerActivityAtMs = 0L
    @Volatile private var sessionStartedAtMs = 0L
    @Volatile private var audioFramesReceived = 0L
    @Volatile private var currentServer: ServerInfo? = null
    @Volatile private var currentAttempt = 0

    init {
        // 跟随设置中的自动重连开关
        appScope.launch {
            settingsRepository.autoReconnect.collect { enabled ->
                autoReconnectEnabled = enabled
                _state.update { it.copy(autoReconnect = enabled) }
            }
        }
        // 跟随音量设置并应用到播放器
        appScope.launch {
            settingsRepository.volume.collect { vol ->
                _state.update { it.copy(volume = vol) }
            }
        }
        appScope.launch {
            settingsRepository.targetBitrate.collect { bitrate ->
                _state.update { it.copy(currentBitrate = bitrate) }
            }
        }
    }

    override fun connect(server: ServerInfo) {
        if (_state.value.connectionState == ConnectionState.CONNECTING) return
        manualDisconnect = false
        localPlaybackAllowed = true
        lastAudioDataAtMs = 0L
        lastServerActivityAtMs = 0L
        _state.update { it.copy(
            connectionState = ConnectionState.CONNECTING,
            server = server,
            receivedBytes = 0L,
            error = null,
            reconnectAttempt = 0,
            stats = StreamStats()
        ) }
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
        watchdogJob?.cancel()
        // 新会话重置本地播放闸门：connect() 会重置，但 watchdog/自动重连直接走 startSession，
        // 若沿用上次会话的本地暂停（localPlaybackAllowed=false），重连后所有 PCM 会被静默丢弃，
        // 表现为"状态显示暂停且永远无声"。
        localPlaybackAllowed = true
        currentServer = server
        currentAttempt = attempt
        val protocol = protocolFactory.create(server.protocol)
        activeProtocol = protocol
        collectJob = appScope.launch {
            try {
                val targetBitrate = settingsRepository.loadTargetBitrate()
                _state.update { it.copy(currentBitrate = targetBitrate) }
                // 协议层已有握手超时（WebSocket handshakeTimeout），
                // 会及时 emit Error/Disconnected 事件，此处 collect 只需正常分发。
                protocol.connect(server.address, server.port, targetBitrate).collect { event ->
                    when (event) {
                        is AudioEvent.Connected -> onConnected(server, event)
                        is AudioEvent.AudioData -> onAudioData(event)
                        is AudioEvent.Disconnected -> onDisconnected(server, event.reason)
                        is AudioEvent.Error -> onError(server, event.exception)
                        is AudioEvent.StateUpdate -> onStateUpdate(event.state)
                        is AudioEvent.BitrateChanged -> onBitrateChanged(event)
                    }
                }
            } catch (e: CancellationException) {
                // 取消不是错误：watchdog 超时、disconnect、用户重连都会 cancel collectJob，
                // 此时清理与重连已由发起方（startWatchdog / disconnect / startSession）负责，
                // 绝不能落进下面的 onError——否则会把 JobCancellationException 当成连接异常
                // 上屏，并触发第二次 maybeReconnect 与 watchdog 的重连竞态。
                throw e
            } catch (e: Exception) {
                onError(server, e)
            }
        }
    }

    private fun onConnected(server: ServerInfo, event: AudioEvent.Connected) {
        teardownPlayer()
        val player = playerFactory.create(settingsRepository.latencyMode.value)
        player.initialize(event.format)
        player.setVolume(_state.value.volume)
        player.writeErrorListener = AudioPlayer.WriteErrorListener { errorCode, _ ->
            if (errorCode == AudioTrack.ERROR_DEAD_OBJECT) {
                throw RuntimeException("AudioTrack 已失效，需重建连接")
            }
        }
        currentPlayer = player
        startPlaybackQueue(player, event.format)
        val targetBitrate = _state.value.currentBitrate
        bitrateTracker.reset()
        sessionStartedAtMs = System.currentTimeMillis()
        audioFramesReceived = 0L
        lastAudioDataAtMs = 0L  // 每个会话独立计时：重连会话不复用上次会话的音频时间戳
        // 握手文本帧本身就是服务端活性证据，从连接成功起看门狗即有效
        lastServerActivityAtMs = System.currentTimeMillis()
        // 连接成功即恢复退避基数：currentAttempt 是 onDisconnected/onError/watchdog 断开时
        // maybeReconnect 的起点。不清零的话历史重连次数会一直累积，退避永远停在 8s 封顶
        // ——表现为每次看门狗断开后都要等满 8 秒才重连。
        currentAttempt = 0
        _state.update { it.copy(
            connectionState = ConnectionState.CONNECTED,
            audioFormat = event.format,
            server = server,
            currentBitrate = targetBitrate,
            error = null,
            reconnectAttempt = 0
        ) }
        appScope.launch {
            delay(STATE_REFRESH_DELAY_MS)
            if (activeProtocol?.isConnected == true) {
                activeProtocol?.send(MediaAction.GET_STATE.toJson())
            }
        }
        startWatchdog()
    }

    /**
     * 连接健康看门狗：锁屏后 WiFi 抖动会让 socket 失活，但 OkHttp reader 会一直阻塞在
     * socketRead 上（最长 30+ 秒才被 OS abort），这期间无声且无重连。看门狗每秒检查
     * [lastServerActivityAtMs]，超过 [WATCHDOG_STALL_MS] 未收到任何服务端消息即主动断开
     * 并立即重连，把无声从 30+ 秒压到 ~10 秒。
     *
     * 判据必须是"任何服务端消息"而非"音频帧"：服务端静音期会跳过音频广播（省带宽），
     * 但媒体状态轮询仍每 500ms 推送文本帧。只盯音频会把"桌面端暂停超过 10 秒"误判为
     * 断线（静音误杀）；socket 真失活时文本流同样停止，压断死链的语义不变。
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = appScope.launch {
            while (true) {
                delay(WATCHDOG_CHECK_MS)
                val lastActivity = lastServerActivityAtMs
                // 仅在开启自动重连时主动断开重连：未开启时让连接维持原状（用户不想自动恢复）。
                if (autoReconnectEnabled && lastActivity > 0 &&
                    System.currentTimeMillis() - lastActivity > WATCHDOG_STALL_MS
                ) {
                    android.util.Log.w("AudioStreamWatchdog",
                        "无服务端消息 ${System.currentTimeMillis() - lastActivity}ms 超过 ${WATCHDOG_STALL_MS}ms，主动断开重连 ${idleDiagnostic()}")
                    val server = currentServer ?: return@launch
                    val attempt = currentAttempt
                    // 取消 collectJob 触发 awaitClose 关闭 socket；collect 的 catch 分支会 rethrow
                    // CancellationException 正常结束，不再走 onError，故重连只由下方 maybeReconnect 负责。
                    collectJob?.cancel()
                    reconnectJob?.cancel()
                    teardownPlayer()
                    _state.update { it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        error = "连接失活（${WATCHDOG_STALL_MS / 1000} 秒未收到服务端数据）",
                        mediaState = null
                    ) }
                    maybeReconnect(server, attempt)
                    return@launch
                }
            }
        }
    }

    private suspend fun onAudioData(event: AudioEvent.AudioData) {
        audioFramesReceived += 1
        val player = currentPlayer ?: return
        val now = System.currentTimeMillis()
        lastAudioDataAtMs = now
        lastServerActivityAtMs = now
        bitrateTracker.add(event.data.size, now)
        if (localPlaybackAllowed) {
            enqueueAudio(event.data)
        }
        val stats = computeStats(now)
        val streamPlaying = localPlaybackAllowed && player.isPlaying
        _state.update { s ->
            s.copy(
                connectionState = if (streamPlaying) ConnectionState.PLAYING else s.connectionState,
                receivedBytes = s.receivedBytes + event.data.size,
                stats = stats,
                mediaState = if (streamPlaying) {
                    s.mediaState?.copy(playing = true) ?: MediaState(playing = true)
                } else {
                    s.mediaState
                }
            )
        }
    }

    // 断开/出错的重连起点统一读 currentAttempt：onConnected 成功后会清零，
    // 保证"连过再断"从 1s 退避起步；只有连续失败（onConnected 未执行）才递增退避。
    // 不能用 startSession 闭包捕获的 attempt——它是本次会话发起时的历史值，不随成功清零。
    private fun onDisconnected(server: ServerInfo, reason: String) {
        watchdogJob?.cancel()
        teardownPlayer()
        android.util.Log.w("AudioStreamRepo", "onDisconnected: $reason ${idleDiagnostic()}")
        _state.update { it.copy(
            connectionState = ConnectionState.DISCONNECTED,
            error = reason.ifBlank { "连接已断开" },
            mediaState = null
        ) }
        maybeReconnect(server, currentAttempt)
    }

    private fun onError(server: ServerInfo, error: Throwable) {
        watchdogJob?.cancel()
        teardownPlayer()
        val reason = error.message ?: error::class.java.simpleName
        android.util.Log.w("AudioStreamRepo", "onError: ${error::class.java.name}: $reason ${idleDiagnostic()}", error)
        _state.update { it.copy(
            connectionState = ConnectionState.ERROR,
            error = reason,
            mediaState = null
        ) }
        maybeReconnect(server, currentAttempt)
    }

    /** 断连诊断：会话已存活多久 + 距上次收到音频/任意消息多久 + 本会话收到的音频帧数。区分
     *  服务端读空闲超时（idle 大）、服务端在正常推流中突然裸关连接（idle≈0，frames>0）、
     *  静音期看门狗（距音频大但距消息小=误杀，两者都大=真死链）、
     *  与握手成功后从未收到音频（frames=0 → onConnected 后无 AudioData，疑似 player 异常）。 */
    private fun idleDiagnostic(): String {
        val now = System.currentTimeMillis()
        val sessionMs = if (sessionStartedAtMs > 0) now - sessionStartedAtMs else -1
        val idleMs = if (lastAudioDataAtMs > 0) now - lastAudioDataAtMs else -1
        val activityMs = if (lastServerActivityAtMs > 0) now - lastServerActivityAtMs else -1
        return "[会话 ${sessionMs}ms, 距上次音频 ${idleMs}ms, 距上次消息 ${activityMs}ms, 收到音频帧 ${audioFramesReceived}]"
    }

    private fun maybeReconnect(server: ServerInfo, attempt: Int) {
        if (manualDisconnect || !autoReconnectEnabled) return
        val nextAttempt = attempt + 1
        val baseDelay = min(RECONNECT_MAX_DELAY_MS, RECONNECT_BASE_DELAY_MS shl (nextAttempt - 1))
        // 用 attempt 派生抖动（避免受限环境使用随机）：±10% 内确定值
        val jitter = (baseDelay / 10) * ((nextAttempt % 3) - 1)
        val delayMs = max(RECONNECT_BASE_DELAY_MS, baseDelay + jitter)
        // 保留上次断连原因，仅追加重连进度，便于定位“锁屏长播断连”根因
        // （旧逻辑直接覆盖成“重连中…”，真实异常被吞，无法诊断）。
        val prevReason = _state.value.error ?: "连接已断开"
        _state.update { it.copy(
            connectionState = ConnectionState.CONNECTING,
            error = "$prevReason（重连中第 $nextAttempt 次，${delayMs / 1000}s 后重试）",
            reconnectAttempt = nextAttempt
        ) }
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
        lastServerActivityAtMs = 0L
        watchdogJob?.cancel()
        reconnectJob?.cancel()
        collectJob?.cancel()
        activeProtocol = null
        teardownPlayer()
        _state.update { it.copy(
            connectionState = ConnectionState.DISCONNECTED,
            audioFormat = null,
            receivedBytes = 0L,
            stats = StreamStats(),
            error = null,
            reconnectAttempt = 0,
            mediaState = null
        ) }
    }

    override fun sendCommand(action: MediaAction) {
        if (action == MediaAction.PLAY_PAUSE) {
            // 切换基准必须与 UI/通知栏展示的状态同源（mediaState.playing / PLAYING），
            // 不能掺入 player.isPlaying：修复后 AudioTrack 在服务端"暂停"时仍保持 play 态，
            // 若据其判断，界面显示"暂停"时按播放会被误判为"正在播放"而把声音关掉。
            val isCurrentlyPlaying = _state.value.mediaState?.playing == true ||
                _state.value.connectionState == ConnectionState.PLAYING
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

    override fun setServerMute(muted: Boolean) {
        val protocol = activeProtocol ?: return
        if (!protocol.isConnected) return
        // 服务端执行后立即广播新状态（含 muted），UI 由 onStateUpdate 自然刷新
        protocol.send("""{"type":"command","action":"set_mute","mute":$muted}""")
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        currentPlayer?.setVolume(clamped)
        appScope.launch { settingsRepository.saveVolume(clamped) }
    }

    override fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        _state.update { it.copy(autoReconnect = enabled) }
        appScope.launch { settingsRepository.saveAutoReconnect(enabled) }
    }

    override fun setBitrate(bitrate: Int) {
        val normalized = normalizeTargetBitrate(bitrate)
        activeProtocol?.setBitrate(normalized)
        appScope.launch { settingsRepository.saveTargetBitrate(normalized) }
    }

    private suspend fun onBitrateChanged(event: AudioEvent.BitrateChanged) {
        lastServerActivityAtMs = System.currentTimeMillis()
        val player = currentPlayer
        // 是否续播只看本地播放闸门：服务端"暂停"元数据与 player.isPlaying 瞬时值都不可靠
        val shouldPlay = localPlaybackAllowed
        if (player != null) {
            // 仅当 PCM 格式（采样率/通道/位深）真正变化才重建 AudioTrack。
            // 服务端同格式降比特率不重建，避免 flush 掉已缓冲 PCM 造成断帧/杂音；
            // 重建后强制 start 并自检，AudioPlayer.start() 静默吞 IllegalStateException 会导致
            // _isPlaying 没置上 → 后续 play 丢数据无声，这里兜一次 recover。
            val current = player.currentStreamFormat()
            if (current == null || !sameFormat(current, event.format)) {
                stopPlaybackQueue()
                withContext(playbackDispatcher) {
                    player.initialize(event.format)
                    player.setVolume(_state.value.volume)
                    if (shouldPlay) {
                        player.start()
                        if (!player.isPlaying) player.resume()
                    }
                }
                startPlaybackQueue(player, event.format)
            }
        }
        // currentBitrate 只反映用户意图（持久化在 DataStore 里的目标码率），不被服务端事件覆盖。
        // 旧逻辑把 event.bitrate 写入 state 但不写 DataStore，导致 StateFlow 去重后 init
        // collector 无法纠正——用户切页回来看到的是服务端值（常为 1536 默认），不是自己设的值。
        // 实际编码码率从 audioFormat + stats.bitrateKbps 可观测，slider 应展示目标值。
        _state.update { it.copy(audioFormat = event.format) }
    }

    private fun sameFormat(a: com.xiaofeishu.audiostream.domain.model.AudioFormat, b: com.xiaofeishu.audiostream.domain.model.AudioFormat): Boolean =
        a.sampleRate == b.sampleRate && a.channels == b.channels && a.bitsPerSample == b.bitsPerSample

    private fun onStateUpdate(mediaState: MediaState) {
        lastServerActivityAtMs = System.currentTimeMillis()
        if (mediaState.playing) {
            localPlaybackAllowed = true
        }
        // 服务端媒体状态只是 SMTC 元数据：桌面端很多声音源（游戏/浏览器等）不注册 SMTC，
        // 状态为"暂停"时 PCM 流仍可能在正常推送。本地是否出声只取决于用户是否本地暂停
        // （localPlaybackAllowed），绝不因服务端"暂停"状态拉停本地 AudioTrack——
        // 旧逻辑在这里 pause 播放器，宽限判断又依赖 player.isPlaying 的瞬时值，
        // 竞态下会造成"状态是暂停就没声音"。
        syncPlayerPlayback(localPlaybackAllowed)
        val hasRecentAudio = System.currentTimeMillis() - lastAudioDataAtMs <= STREAM_ACTIVE_GRACE_MS
        val effectivePlaying = localPlaybackAllowed && (mediaState.playing || hasRecentAudio)
        _state.update { it.copy(
            connectionState = if (effectivePlaying) ConnectionState.PLAYING else ConnectionState.CONNECTED,
            mediaState = mediaState.copy(playing = effectivePlaying)
        ) }
    }

    private fun updateLocalMediaState(playing: Boolean) {
        _state.update { it.copy(
            connectionState = if (playing) ConnectionState.PLAYING else ConnectionState.CONNECTED,
            mediaState = it.mediaState?.copy(playing = playing) ?: MediaState(playing = playing)
        ) }
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

    private fun startPlaybackQueue(
        player: AudioPlayer,
        format: com.xiaofeishu.audiostream.domain.model.AudioFormat
    ) {
        stopPlaybackQueue()
        val latencyMode = settingsRepository.latencyMode.value
        val queuedBytes = AtomicLong(0L)
        // 所有档位统一 DROP_OLDEST：旧逻辑"禁用"档用 SUSPEND 反压，播放端一慢就把
        // OkHttp reader 挂起，积压全部转移进 TCP 收发缓冲（几百 KB ≈ 秒级延迟），
        // 只增不减且对 stats 不可见。改为本地大队列丢最旧兜底，延迟有界且可观测。
        val queue = Channel<ByteArray>(
            capacity = audioQueueCapacity(latencyMode),
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { data -> removeQueuedAudio(queuedBytes, data) }
        )
        playbackQueue = PlaybackQueue(queue, queuedBytes)
        playbackJob = appScope.launch(playbackDispatcher) {
            // ════════════════════════════════════════════════════════════════════
            // 自适应水位：用 AudioTrack.getUnderrunCount() 做反馈
            // ════════════════════════════════════════════════════════════════════
            // 核心思路：平时贴着低延迟跑（startThreshold = 档位初始值），一旦检测到新欠载，
            // 就抬高 startThreshold（让 track 欠载后攒更多再起播，吸收抖动），同时通知
            // playWithCatchup 的目标水位同步上移（不要把刚蓄的缓冲又削回去）。
            // 一段时间无新欠载则逐步降回初始值——做到"能低延迟时低延迟，该扛抖动时扛得住"。
            //
            // 为什么不直接暂停出队等蓄水？因为 MODE_STREAM 的 startThreshold 已经提供了
            // 欠载重蓄水语义（写入量不够门槛时 track 自动静音等待），比应用层暂停更精确、
            // 不引入额外调度延迟。应用层只需要在检测到欠载时把门槛调高就行。
            val baseThresholdMs = player.startThresholdMs  // 档位初始值
            val maxThresholdMs = when (latencyMode) {
                100 -> 120   // 低延迟档：上限不超过平衡档初始值
                150 -> 150   // 平衡档：与档位值持平
                200 -> 200   // 稳定档
                else -> 160  // 禁用跳帧
            }
            var adaptiveThresholdMs = baseThresholdMs
            var lastUnderrunCount = player.underrunCount() ?: 0
            var lastUnderrunCheckMs = SystemClock.elapsedRealtime()
            var stableStreakMs = 0L  // 无新欠载的连续时长

            // ════════════════════════════════════════════════════════════════════
            // 积压回落（保留，目标水位跟随自适应阈值）
            // ════════════════════════════════════════════════════════════════════
            var trimWindowStartMs = 0L
            var trimWindowMinMs = Int.MAX_VALUE
            var trimBytesRemaining = 0L
            var iterEndAtMs = 0L
            var iterEndTrackLagMs = 0
            var iterEndQueueEmpty = false

            fun trackLagMsNow(): Int {
                if (format.bytesPerFrame <= 0 || format.sampleRate <= 0) return 0
                val lagFrames = (player.totalWrittenBytes / format.bytesPerFrame -
                    player.playbackHeadPositionFrames()).coerceAtLeast(0L)
                return (lagFrames * 1000 / format.sampleRate)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }

            try {
                for (data in queue) {
                    try {
                        if (!localPlaybackAllowed) {
                            trimWindowStartMs = 0L
                            trimBytesRemaining = 0L
                            iterEndAtMs = 0L
                            // 暂停期间重置自适应状态，恢复后从基准重新探测
                            adaptiveThresholdMs = baseThresholdMs
                            player.setStartThresholdMs(baseThresholdMs)
                            lastUnderrunCount = player.underrunCount() ?: 0
                            stableStreakMs = 0L
                            continue
                        }
                        if (trimBytesRemaining >= data.size) {
                            trimBytesRemaining -= data.size
                            continue
                        }
                        trimBytesRemaining = 0L
                        if (!player.isPlaying) player.resume()
                        val threshold = settingsRepository.latencyMode.value

                        // ── 自适应水位更新（每包检测，代价仅 1 次 JNI 调用） ──
                        if (format.bytesPerFrame > 0 && format.sampleRate > 0) {
                            val nowMs = SystemClock.elapsedRealtime()
                            val currentUnderruns = player.underrunCount() ?: lastUnderrunCount
                            val elapsed = nowMs - lastUnderrunCheckMs
                            if (currentUnderruns > lastUnderrunCount) {
                                // 检测到新欠载：立即抬高门槛
                                val boost = ADAPTIVE_BOOST_MS * (currentUnderruns - lastUnderrunCount)
                                    .coerceAtMost(3)  // 单次最多抬 3 步，防暴涨
                                adaptiveThresholdMs = min(adaptiveThresholdMs + boost, maxThresholdMs)
                                player.setStartThresholdMs(adaptiveThresholdMs)
                                stableStreakMs = 0L
                                android.util.Log.i("AudioStreamAdaptive",
                                    "欠载 +${currentUnderruns - lastUnderrunCount}，" +
                                        "startThreshold 升至 ${adaptiveThresholdMs}ms " +
                                        "(基准=${baseThresholdMs}ms 上限=${maxThresholdMs}ms)")
                                lastUnderrunCount = currentUnderruns
                            } else if (adaptiveThresholdMs > baseThresholdMs) {
                                // 无新欠载：累计稳定时长，超过冷却期后逐步回降
                                stableStreakMs += elapsed
                                if (stableStreakMs >= ADAPTIVE_COOLDOWN_MS) {
                                    adaptiveThresholdMs = max(
                                        adaptiveThresholdMs - ADAPTIVE_DECAY_MS,
                                        baseThresholdMs
                                    )
                                    player.setStartThresholdMs(adaptiveThresholdMs)
                                    stableStreakMs = 0L
                                    android.util.Log.d("AudioStreamAdaptive",
                                        "稳定 ${ADAPTIVE_COOLDOWN_MS / 1000}s，" +
                                            "startThreshold 降至 ${adaptiveThresholdMs}ms")
                                }
                            }
                            lastUnderrunCheckMs = nowMs

                            // ── 积压回落（目标水位跟随自适应阈值） ──
                            if (trimWindowStartMs == 0L) {
                                trimWindowStartMs = nowMs
                                trimWindowMinMs = Int.MAX_VALUE
                            }
                            if (iterEndAtMs > 0L && iterEndQueueEmpty) {
                                val blockedMs = (nowMs - iterEndAtMs).toInt()
                                if (blockedMs > 0) {
                                    val dipMs = (iterEndTrackLagMs - blockedMs).coerceAtLeast(0)
                                    trimWindowMinMs = min(trimWindowMinMs, dipMs)
                                }
                            }
                            val queuedFrames = (queuedBytes.get() - data.size).coerceAtLeast(0L) /
                                format.bytesPerFrame
                            val steadyMs = trackLagMsNow() +
                                (queuedFrames * 1000 / format.sampleRate)
                                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            trimWindowMinMs = min(trimWindowMinMs, steadyMs)
                            if (nowMs - trimWindowStartMs >= TRIM_WINDOW_MS) {
                                // 回落目标跟随自适应阈值：不能把自适应刚蓄的缓冲又削回去
                                val targetMs = if (threshold > 0) {
                                    max(threshold, adaptiveThresholdMs)
                                } else {
                                    max(TRIM_FLOOR_DISABLED_MS, adaptiveThresholdMs)
                                }
                                val excessMs = trimWindowMinMs - targetMs
                                if (excessMs >= TRIM_MIN_EXCESS_MS) {
                                    // 单次清理限幅：catchup 已按 3ms/包持续消化漂移，
                                    // trim 只兜底真实严重积压；一次清太多会丢整包 = 可听空洞。
                                    val trimMs = minOf(excessMs, TRIM_MAX_PER_CYCLE_MS)
                                    trimBytesRemaining = trimMs.toLong() *
                                        format.sampleRate / 1000 * format.bytesPerFrame
                                    android.util.Log.i("AudioStreamRepo",
                                        "积压回落：${TRIM_WINDOW_MS / 1000}s 窗口最小积压 ${trimWindowMinMs}ms " +
                                            "超出目标 ${targetMs}ms，本轮丢弃 ${trimMs}ms（超出量 ${excessMs}ms）")
                                }
                                trimWindowStartMs = nowMs
                                trimWindowMinMs = Int.MAX_VALUE
                            }
                        }

                        if (threshold > 0) {
                            // catchup 目标水位也跟随自适应阈值：取 max(档位值, 当前自适应门槛)
                            val effectiveThreshold = max(threshold, adaptiveThresholdMs)
                            val pendingBytes = (queuedBytes.get() - data.size).coerceAtLeast(0L)
                            player.playWithCatchup(data, format, effectiveThreshold, pendingBytes)
                        } else {
                            player.play(data)
                        }
                        iterEndTrackLagMs = trackLagMsNow()
                        iterEndQueueEmpty = (queuedBytes.get() - data.size) <= 0L
                        iterEndAtMs = SystemClock.elapsedRealtime()
                    } finally {
                        removeQueuedAudio(queuedBytes, data)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (playbackQueue?.channel !== queue) return@launch
                val server = currentServer ?: return@launch
                appScope.launch {
                    collectJob?.cancel()
                    onError(server, e)
                }
            }
        }
    }

    private fun enqueueAudio(data: ByteArray) {
        val queue = playbackQueue ?: return
        queue.queuedBytes.addAndGet(data.size.toLong())
        if (queue.channel.trySend(data).isFailure) {
            removeQueuedAudio(queue.queuedBytes, data)
        }
    }

    private fun removeQueuedAudio(queuedBytes: AtomicLong, data: ByteArray) {
        queuedBytes.updateAndGet { current ->
            (current - data.size).coerceAtLeast(0L)
        }
    }

    private fun stopPlaybackQueue() {
        val queue = playbackQueue
        playbackQueue = null
        playbackJob?.cancel()
        playbackJob = null
        queue?.channel?.cancel()
    }

    private fun teardownPlayer() {
        stopPlaybackQueue()
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
            val trackLagFrames = (writtenFrames - headFrames).coerceAtLeast(0L)
            val queuedFrames = (playbackQueue?.queuedBytes?.get() ?: 0L)
                .coerceAtLeast(0L) / format.bytesPerFrame
            val lagFrames = trackLagFrames + queuedFrames
            (lagFrames * 1000 / format.sampleRate)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else 0
        // 评级相对所选延迟档位：档位值本身就是目标稳态延迟，
        // 固定阈值会把 150/200 档的正常稳态误评为"一般"。
        val threshold = settingsRepository.latencyMode.value
        val fairAbove = if (threshold > 0) threshold + 100 else 250
        val poorAbove = if (threshold > 0) threshold + 350 else 600
        val quality = when {
            bitrateKbps <= 0 -> Quality.UNKNOWN
            bufferLatencyMs > poorAbove -> Quality.POOR
            bufferLatencyMs > fairAbove -> Quality.FAIR
            else -> Quality.GOOD
        }
        // 输出链路信息（AudioPlayer 内部按 ~2s 节流，此处每包调用无额外开销）：
        // 链路延迟是编解码/传输固有，不参与 quality 评级——app 无法通过跳帧消除它。
        val link = player?.outputLinkInfo()
        return StreamStats(
            bitrateKbps = bitrateKbps,
            bufferLatencyMs = bufferLatencyMs,
            quality = quality,
            sinkLatencyMs = link?.sinkLatencyMs,
            bluetoothDevice = link?.takeIf { it.isBluetooth }?.deviceName
        )
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

    private data class PlaybackQueue(
        val channel: Channel<ByteArray>,
        val queuedBytes: AtomicLong
    )

    companion object {
        private const val WINDOW_MS = 1000L
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 8_000L
        private const val DEFAULT_TARGET_BITRATE = 1536
        private const val STATE_REFRESH_DELAY_MS = 500L
        private const val STREAM_ACTIVE_GRACE_MS = 2_000L
        /** 看门狗：无音频超过此时长即判定 socket 失活，主动断开重连。
         *  10s 平衡误杀风险与恢复速度——锁屏后 WiFi 抖动常让 socket 在 30+ 秒才被 OS abort，
         *  此前无声。略大于正常帧间隔抖动，避免服务端短暂卡顿误触发。 */
        private const val WATCHDOG_STALL_MS = 10_000L
        private const val WATCHDOG_CHECK_MS = 1_000L

        /** 积压回落观察窗口：窗口内总积压最小值减目标水位 = 从未被抖动消耗的净冗余，可安全丢弃。 */
        private const val TRIM_WINDOW_MS = 4_000L

        /** 禁用跳帧档的回落目标水位（ms）：保留可观抖动余量，只清除卡顿留下的永久积压。 */
        private const val TRIM_FLOOR_DISABLED_MS = 160

        /** 回落执行门槛（ms，2 包）：低于此值的"冗余"属采样噪声，切割只会产生咔哒声。 */
        private const val TRIM_MIN_EXCESS_MS = 40

        /** 单次回落清理上限（ms）：catchup 已按 3ms/包消化漂移，trim 仅兜底严重积压且每轮只清一小截，
         *  避免一次丢整包造成的可听空洞；剩余积压留待下一窗口继续。 */
        private const val TRIM_MAX_PER_CYCLE_MS = 60

        /** 自适应水位：检测到欠载时单步抬高的门槛量（ms）。约 2 个包，够吸收一次典型抖动。 */
        private const val ADAPTIVE_BOOST_MS = 40

        /** 自适应水位：无新欠载达此时长后开始回降，避免一抖就长期停在高延迟。 */
        private const val ADAPTIVE_COOLDOWN_MS = 5_000L

        /** 自适应水位：每次回降步长（ms）。比 BOOST 小，遵循"快升慢降"避免震荡。 */
        private const val ADAPTIVE_DECAY_MS = 40

        /** 播放队列容量（包，每包 ~10ms——服务端按 WASAPI 10ms 周期分包）。
         *  按 AudioTrack 缓冲容量覆盖目标的 1.5 倍给，确保队列 + track 合起来能装下
         *  自适应水位的上限。容量给足不影响延迟（延迟由水位控制），但容量不足会让水位
         *  物理上涨不上去，欠载后无法重蓄水。 */
        private fun audioQueueCapacity(latencyMode: Int): Int = when (latencyMode) {
            100 -> 30   // 200ms cap / 10ms = 20, ×1.5 = 30
            150 -> 45   // 300ms / 10ms = 30, ×1.5 = 45
            200 -> 60   // 400ms / 10ms = 40, ×1.5 = 60
            else -> 75  // 500ms / 10ms = 50, ×1.5 = 75
        }

        private fun normalizeTargetBitrate(bitrate: Int): Int {
            return if (bitrate > 0) bitrate else DEFAULT_TARGET_BITRATE
        }
    }
}
