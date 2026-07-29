package com.xiaofeishu.audiostream.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import com.xiaofeishu.audiostream.domain.model.AudioFormat as StreamFormat

/**
 * [bufferCapacityMs] 是 AudioTrack 缓冲的**容量**下限，不是延迟：实际延迟取决于其中
 * 积压多少未播 PCM（written - head），由上层水位控制。容量给足只是让抖动缓冲有地方涨，
 * 不会抬高稳态延迟；容量不足则水位物理上涨不上去，欠载后必然反复断音。
 */
class AudioPlayer(
    private val lowLatency: Boolean = false,
    private val bufferCapacityMs: Int = DEFAULT_BUFFER_CAPACITY_MS,
    startThresholdMs: Int = DEFAULT_START_THRESHOLD_MS
) {

    fun interface WriteErrorListener {
        fun onWriteError(errorCode: Int, audioTrack: AudioTrack)
    }

    /** 输出链路信息：实际路由的输出设备与链路在途延迟估计。 */
    data class OutputLinkInfo(
        val isBluetooth: Boolean,
        val deviceName: String,
        val sinkLatencyMs: Int?
    )

    var writeErrorListener: WriteErrorListener? = null

    /** 起播/欠载恢复门槛（ms）。外部可通过 setStartThresholdMs 动态调整。 */
    @Volatile var startThresholdMs: Int = startThresholdMs.coerceAtLeast(MIN_START_THRESHOLD_MS)
        private set

    private var audioTrack: AudioTrack? = null
    private var _isPlaying = false
    @Volatile private var released = false
    private var currentFormat: StreamFormat? = null
    private var negotiatedTo: String? = null
    private var currentVolume: Float = 1f
    private var trackStartedAtMs: Long = 0L
    private var playbackHeadWraps: Long = 0L
    private var lastPlaybackHeadRaw: Long = 0L
    private var lastObservedHeadFrames: Long = 0L
    private var lastObservedWrittenBytes: Long = 0L
    private var lastObservedAtMs: Long = 0L
    private var playbackHeadStallStartedAtMs: Long = 0L
    @Volatile private var cachedLinkInfo: OutputLinkInfo? = null
    @Volatile private var linkInfoCheckedAtMs: Long = 0L
    @Volatile var totalWrittenBytes: Long = 0L
        private set

    val isPlaying: Boolean get() = _isPlaying
    val negotiationNote: String? get() = negotiatedTo

    fun initialize(format: StreamFormat) {
        released = false
        releaseTrack()
        _isPlaying = false
        currentFormat = format
        negotiatedTo = null
        audioTrack = buildAudioTrack(format)
        audioTrack?.setVolume(currentVolume)
        resetTrackCounters()
    }

    fun play(pcmData: ByteArray) {
        if (!_isPlaying) return
        recoverLongRunningTrackIfNeeded()
        writeLoop(pcmData)
    }

    /**
     * 写入音频数据，当缓冲积压超过 catchupThresholdMs 时跳过旧数据追赶。
     * catchupThresholdMs=0 时禁用跳帧，等同于原 play()。
     *
     * [pendingBacklogBytes] 是播放队列中尚未写入 track 的积压字节，必须计入总积压：
     * 多数设备 AudioTrack 最小缓冲不足 150ms，只看 written-head 时阈值永远够不着，
     * 队列积压（最多几十至几百 ms）会永久叠加在延迟上、无法被追赶收敛。
     */
    fun playWithCatchup(
        pcmData: ByteArray,
        format: com.xiaofeishu.audiostream.domain.model.AudioFormat,
        catchupThresholdMs: Int,
        pendingBacklogBytes: Long = 0L
    ) {
        if (!_isPlaying || catchupThresholdMs <= 0) {
            play(pcmData)
            return
        }
        recoverLongRunningTrackIfNeeded()

        val headFrames = playbackHeadPositionFrames()
        val writtenFrames = totalWrittenBytes / format.bytesPerFrame
        val backlogFrames = pendingBacklogBytes.coerceAtLeast(0L) / format.bytesPerFrame
        val bufferedMs = ((writtenFrames - headFrames + backlogFrames) * 1000 / format.sampleRate).toInt()

        // 抽干方向的低水位线：低于它就在触底(underrun)前介入垫静音。取 catchupThresholdMs 之下
        // 留一段死区（不高于其半值），避免与跳帧在同一水位来回打架；死区内缓冲自由浮动、不干预。
        val lowWaterMs = minOf(LOW_WATER_MARK_MS, catchupThresholdMs / 2)

        if (bufferedMs > catchupThresholdMs && pcmData.size >= format.bytesPerFrame) {
            // 单包跳帧限幅：漂移积压每 20ms 只涨亚毫秒，限到 ~3ms/包即可边收边消、永不触大跳；
            // 瞬时抖动积压则跨多个包软追赶，避免一刀切掉几十 ms 造成的可听断音。
            val excessMs = bufferedMs - catchupThresholdMs
            val skipMs = minOf(excessMs, MAX_SKIP_MS_PER_PACKET)
            val framesToSkip = (skipMs * format.sampleRate / 1000).toInt()
            val bytesToSkip = (framesToSkip * format.bytesPerFrame)
                .coerceAtMost(pcmData.size - format.bytesPerFrame)
                .let { it - (it % format.bytesPerFrame) }
            if (bytesToSkip > 0) {
                writeLoop(pcmData, bytesToSkip, pcmData.size - bytesToSkip)
                return
            }
        } else if (bufferedMs < lowWaterMs) {
            // 抽干方向镜像跳帧：捕获声卡快于本机 DAC 时缓冲被单向抽干，旧逻辑只能等真正
            // underrun（AudioTrack 硬性插静音等待重蓄水）= 周期性可听"顿"、蓄满又"自己好"。
            // 改为触底前每包垫 ≤3ms 静音，把 DAC 多消费的部分用听不见的微静音补上，
            // 内容时间轴随之对齐 DAC 墙钟，永不触 underrun。与 catchup 完全对称：
            // 一个每包丢一点、一个每包垫一点，双向连续消化任意方向的时钟漂移。
            val deficitMs = lowWaterMs - bufferedMs
            val padMs = minOf(deficitMs, MAX_PAD_MS_PER_PACKET)
            val padFrames = padMs * format.sampleRate / 1000
            if (padFrames > 0) {
                writeSilenceFrames(padFrames, format)
            }
        }
        writeLoop(pcmData)
    }

    fun start() {
        if (!_isPlaying) {
            try {
                val track = currentOrRecoveredTrack() ?: return
                track.play()
                _isPlaying = true
            } catch (_: IllegalStateException) {}
        }
    }

    fun stop() {
        _isPlaying = false
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) {}
    }

    fun pause() {
        if (!_isPlaying) return
        _isPlaying = false
        // 暂停即丢弃未播数据并释放 track：MODE_STREAM 下暂停时残留在缓冲里的 PCM
        // 会在恢复时先播出（听到暂停前的声音），且 written-head 差值守恒，
        // 此后整个会话的缓冲延迟都会多这一截。恢复时由 currentOrRecoveredTrack 惰性重建。
        releaseTrack()
    }

    fun resume() {
        if (_isPlaying) return
        val track = currentOrRecoveredTrack() ?: return
        try {
            track.play()
            _isPlaying = true
        } catch (_: IllegalStateException) {}
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        currentVolume = clamped
        audioTrack?.setVolume(clamped)
    }

    // 必须加锁：播放线程（跳帧/回落）与统计线程（computeStats）并发调用，
    // 回绕检测读改写 lastPlaybackHeadRaw 时若读到对方刚更新的更大值，会把单调
    // 递增的 head 误判为回绕 +2^32 帧——此后 head 虚增，跳帧追赶永久失效、
    // 延迟统计归零，直到 track 重建才恢复。
    @Synchronized
    fun playbackHeadPositionFrames(): Long {
        val track = audioTrack ?: return 0
        val raw = runCatching {
            track.playbackHeadPosition.toLong() and PLAYBACK_HEAD_MASK
        }.getOrDefault(0L)
        if (raw < lastPlaybackHeadRaw) {
            playbackHeadWraps += PLAYBACK_HEAD_WRAP
        }
        lastPlaybackHeadRaw = raw
        return playbackHeadWraps + raw
    }

    fun playbackHeadPosition(): Int {
        return playbackHeadPositionFrames().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun currentStreamFormat(): StreamFormat? = currentFormat

    /**
     * 当前输出链路信息，约每 [LINK_INFO_REFRESH_MS] 刷新一次——getRoutedDevice/getTimestamp
     * 都是 binder 调用，不宜跟着每个 20ms 音频包高频查询。track 不存在（已释放/本地暂停）时返回 null。
     */
    fun outputLinkInfo(): OutputLinkInfo? {
        val now = SystemClock.elapsedRealtime()
        if (now - linkInfoCheckedAtMs < LINK_INFO_REFRESH_MS) return cachedLinkInfo
        linkInfoCheckedAtMs = now
        val track = audioTrack
        if (track == null) {
            cachedLinkInfo = null
            return null
        }
        val device = runCatching { track.routedDevice }.getOrNull()
        val isBluetooth = when (device?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
            else -> false
        }
        val info = OutputLinkInfo(
            isBluetooth = isBluetooth,
            deviceName = device?.productName?.toString().orEmpty(),
            sinkLatencyMs = estimateSinkLatencyMs(track)
        )
        cachedLinkInfo = info
        return info
    }

    /**
     * 估算链路在途延迟：已被系统从 track 缓冲取走、但尚未真正从耳机/扬声器发声的时长
     * （系统混音 + 蓝牙编码/空中传输/耳机端解码缓冲）。这段延迟无法用跳帧消除，
     * 单独测出来是为了把"app 内缓冲"和"系统/蓝牙链路"分开展示。
     *
     * 首选公开 API getTimestamp()：HAL 上报的 (framePosition, nanoTime) 呈现位置，
     * 蓝牙 HAL 通常已把链路延迟估计计入；head 减去外推到当前时刻的 presented 即在途帧数。
     * 部分设备蓝牙路由下时间戳不可用或跳变，回退隐藏 API getLatency()（Media3 同款做法），
     * 其返回值包含整个 track 缓冲时长，需减去 bufferSizeInFrames 才是 sink 侧延迟。
     * 两条路都不可用时返回 null（UI 显示为不可测）。
     */
    private fun estimateSinkLatencyMs(track: AudioTrack): Int? {
        val format = currentFormat ?: return null
        if (format.sampleRate <= 0) return null

        val ts = AudioTimestamp()
        val hasTimestamp = runCatching { track.getTimestamp(ts) }.getOrDefault(false)
        if (hasTimestamp) {
            val elapsedFrames =
                (System.nanoTime() - ts.nanoTime) * format.sampleRate / 1_000_000_000L
            val presentedFrames = ts.framePosition + elapsedFrames
            val ms = ((playbackHeadPositionFrames() - presentedFrames) * 1000 / format.sampleRate).toInt()
            // 外推抖动允许小幅为负；大幅为负或超出合理上限视为时间戳不可信，走 getLatency 兜底
            if (ms in -50..MAX_SINK_LATENCY_MS) return ms.coerceAtLeast(0)
        }

        val totalLatencyMs = runCatching {
            getLatencyMethod?.invoke(track) as? Int
        }.getOrNull() ?: return null
        val bufferMs = runCatching { track.bufferSizeInFrames }.getOrDefault(0)
            .toLong() * 1000 / format.sampleRate
        return (totalLatencyMs - bufferMs).toInt().coerceIn(0, MAX_SINK_LATENCY_MS)
    }

    fun release() {
        released = true
        stop()
        releaseTrack()
        totalWrittenBytes = 0L
        writeErrorListener = null
    }

    private fun buildAudioTrack(format: StreamFormat): AudioTrack {
        val channelConfig = when (format.channels) {
            1 -> AndroidAudioFormat.CHANNEL_OUT_MONO
            2 -> AndroidAudioFormat.CHANNEL_OUT_STEREO
            else -> {
                negotiatedTo = "${format.channels}ch→stereo"
                AndroidAudioFormat.CHANNEL_OUT_STEREO
            }
        }

        val encoding = when (format.bitsPerSample) {
            16 -> AndroidAudioFormat.ENCODING_PCM_16BIT
            8 -> AndroidAudioFormat.ENCODING_PCM_8BIT
            32 -> AndroidAudioFormat.ENCODING_PCM_FLOAT
            else -> {
                negotiatedTo = negotiatedTo?.plus(";") ?: ""
                negotiatedTo = "${negotiatedTo}${format.bitsPerSample}bit→16bit"
                AndroidAudioFormat.ENCODING_PCM_16BIT
            }
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            format.sampleRate,
            channelConfig,
            encoding
        )
        // 容量按 bufferCapacityMs 给足（不是延迟，见类注释）：旧实现取 20ms 下限，
        // track 物理上装不下抖动缓冲，任何一次欠载后水位都被钉死在起播门槛附近，
        // 且实时流 in=out 永远涨不回来——表现为"一顿一顿且延迟贴着 20ms"。
        val capacityBytes = (format.sampleRate.toLong() * format.bytesPerFrame *
            bufferCapacityMs / 1000).toInt().coerceAtLeast(1)
        val bufferSize = maxOf(minBufferSize, capacityBytes)

        val audioFormat = AndroidAudioFormat.Builder()
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(encoding)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (lowLatency) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = builder.build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            throw IllegalStateException("AudioTrack 初始化失败")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyStartThreshold(track, format)
        }
        android.util.Log.i(
            "AudioStreamTrack",
            "sampleRate=${format.sampleRate} bufferFrames=${track.bufferSizeInFrames}/" +
                "${track.bufferCapacityInFrames} performanceMode=${track.performanceMode}" +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    " startThreshold=${track.startThresholdInFrames}"
                } else {
                    ""
                }
        )
        return track
    }

    /**
     * API 31+: 设置起播门槛。MODE_STREAM 下 AudioTrack 在写入量达到此值后才开始播放，
     * 欠载后也会等重新攒到此量再恢复——这就是"重蓄水"的核心杠杆。
     * 门槛太小（旧 20ms）→ 欠载后立刻开播 → 立刻又空 → 反复断音。
     */
    @android.annotation.SuppressLint("NewApi")
    private fun applyStartThreshold(track: AudioTrack, format: StreamFormat) {
        val thresholdFrames = (format.sampleRate.toLong() * startThresholdMs / 1000)
            .coerceIn(1, track.bufferCapacityInFrames.toLong().coerceAtLeast(1))
            .toInt()
        runCatching { track.setStartThresholdInFrames(thresholdFrames) }
    }

    /**
     * 运行时动态调整起播门槛（自适应缓冲的核心）。
     * 上层检测到 underrun 时调高,平稳后调低,不需要重建 track。
     */
    fun setStartThresholdMs(ms: Int) {
        startThresholdMs = ms.coerceAtLeast(MIN_START_THRESHOLD_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val track = audioTrack ?: return
            val format = currentFormat ?: return
            applyStartThreshold(track, format)
        }
    }

    /**
     * 自 track 创建以来的累计欠载次数。API 24+ 可用（minSdk 26 满足）。
     * 返回 null 表示 track 不存在。上层据此判断是否需要扩大缓冲水位。
     */
    fun underrunCount(): Int? = runCatching { audioTrack?.underrunCount }.getOrNull()

    private fun recoverTrack(): Boolean {
        if (released) return false
        val format = currentFormat ?: return false
        releaseTrack()
        return runCatching {
            audioTrack = buildAudioTrack(format)
            audioTrack?.setVolume(currentVolume)
            resetTrackCounters()
            if (_isPlaying) {
                audioTrack?.play()
            }
        }.isSuccess
    }

    private fun recoverLongRunningTrackIfNeeded() {
        val startedAt = trackStartedAtMs
        if (startedAt == 0L) return
        if (SystemClock.elapsedRealtime() - startedAt >= MAX_TRACK_LIFETIME_MS) {
            recoverTrack()
        }
    }

    private fun recoverStalledPlaybackIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastObservedAtMs < STALL_CHECK_INTERVAL_MS) return

        val headFrames = playbackHeadPositionFrames()
        val writtenBytes = totalWrittenBytes
        if (headFrames != lastObservedHeadFrames) {
            lastObservedHeadFrames = headFrames
            playbackHeadStallStartedAtMs = 0L
        } else if (writtenBytes > lastObservedWrittenBytes) {
            if (playbackHeadStallStartedAtMs == 0L) {
                playbackHeadStallStartedAtMs = now
            } else if (now - playbackHeadStallStartedAtMs >= PLAYBACK_STALL_MS) {
                recoverTrack()
            }
        }
        lastObservedWrittenBytes = writtenBytes
        lastObservedAtMs = now
    }

    private fun isRecoverableWriteError(errorCode: Int): Boolean {
        return errorCode == AudioTrack.ERROR_DEAD_OBJECT ||
            errorCode == AudioTrack.ERROR_INVALID_OPERATION
    }

    private fun writeLoop(
        pcmData: ByteArray,
        startOffset: Int = 0,
        length: Int = pcmData.size - startOffset
    ) {
        var offset = startOffset
        val endOffset = (startOffset + length).coerceAtMost(pcmData.size)
        var zeroWriteCount = 0
        while (offset < endOffset) {
            val track = currentOrRecoveredTrack() ?: return
            val written = try {
                track.write(pcmData, offset, endOffset - offset)
            } catch (_: IllegalStateException) {
                if (recoverTrack()) continue else return
            }
            when {
                written > 0 -> {
                    offset += written
                    totalWrittenBytes += written
                    zeroWriteCount = 0
                }
                written == 0 -> {
                    zeroWriteCount += 1
                    if (zeroWriteCount >= MAX_ZERO_WRITES) {
                        if (recoverTrack()) {
                            zeroWriteCount = 0
                        } else {
                            break
                        }
                    } else {
                        Thread.sleep(WRITE_RETRY_DELAY_MS)
                    }
                }
                else -> {
                    if (isRecoverableWriteError(written) && recoverTrack()) {
                        zeroWriteCount = 0
                    } else {
                        writeErrorListener?.onWriteError(written, track)
                        break
                    }
                }
            }
        }
        if (offset > startOffset) {
            recoverStalledPlaybackIfNeeded()
        }
    }

    /**
     * 写入 [frames] 帧静音，用于抽干方向的漂移补偿（见 playWithCatchup）。
     * 8-bit PCM 在 AudioTrack 是无符号编码，静音电平是 0x80(128)——全 0 反而是最负峰值会爆音；
     * 16-bit signed 与 32-bit float 的静音都是全 0 字节（ByteArray 默认值，无需填充）。
     * 复用 writeLoop，故 totalWrittenBytes 一并累加，缓冲深度统计与后续判断自洽。
     */
    private fun writeSilenceFrames(
        frames: Int,
        format: com.xiaofeishu.audiostream.domain.model.AudioFormat
    ) {
        val bytesPerFrame = format.bytesPerFrame
        if (bytesPerFrame <= 0 || frames <= 0) return
        val silence = ByteArray(frames * bytesPerFrame)
        if (format.bitsPerSample == 8) silence.fill(0x80.toByte())
        writeLoop(silence)
    }

    private fun currentOrRecoveredTrack(): AudioTrack? {
        if (released) return null
        val track = audioTrack
        if (track != null) return track
        return if (recoverTrack()) audioTrack else null
    }

    private fun releaseTrack() {
        try { audioTrack?.pause() } catch (_: IllegalStateException) {}
        try { audioTrack?.flush() } catch (_: IllegalStateException) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        resetTrackCounters()
        totalWrittenBytes = 0L
    }

    @Synchronized
    private fun resetTrackCounters() {
        trackStartedAtMs = SystemClock.elapsedRealtime()
        playbackHeadWraps = 0L
        lastPlaybackHeadRaw = 0L
        lastObservedHeadFrames = 0L
        lastObservedWrittenBytes = 0L
        lastObservedAtMs = 0L
        playbackHeadStallStartedAtMs = 0L
        linkInfoCheckedAtMs = 0L  // track 重建后路由/延迟可能变化，下次查询强制刷新
    }

    companion object {
        private const val MAX_ZERO_WRITES = 5
        private const val WRITE_RETRY_DELAY_MS = 2L
        private const val STALL_CHECK_INTERVAL_MS = 1_000L
        private const val PLAYBACK_STALL_MS = 3_000L
        private const val DEFAULT_START_THRESHOLD_MS = 60
        private const val MIN_START_THRESHOLD_MS = 10
        private const val DEFAULT_BUFFER_CAPACITY_MS = 300
        /** 单包跳帧上限（ms）：把"攒到阈值再一刀切"的硬纠正摊成每包亚可察觉的微跳，
         *  边收漂移边消化，避免一次丢几十 ms 的可听断音。 */
        private const val MAX_SKIP_MS_PER_PACKET = 3
        /** 单包静音填充上限（ms）：抽干方向镜像 MAX_SKIP_MS_PER_PACKET，触底前每包最多垫 3ms
         *  静音，把单向漂移摊成不可察觉的微填充，替代可听的 underrun 断音。 */
        private const val MAX_PAD_MS_PER_PACKET = 3
        /** 抽干方向低水位线（ms）：缓冲深度低于此值即在 underrun 前介入垫静音。远高于 0 留出
         *  约 2 包安全余量（每包可垫 3ms 远快于亚毫秒/包的漂移，足以稳稳托住），又远低于典型
         *  catchup 阈值（≥100ms）以保证跳帧/填充之间有死区、不互相打架。 */
        private const val LOW_WATER_MARK_MS = 40
        private const val MAX_TRACK_LIFETIME_MS = 2 * 60 * 60 * 1000L
        private const val PLAYBACK_HEAD_MASK = 0xffffffffL
        private const val PLAYBACK_HEAD_WRAP = 1L shl 32
        private const val LINK_INFO_REFRESH_MS = 2_000L
        /** 链路延迟合理上限：LDAC 弱信号下可到 ~1s，超出视为测量值不可信。 */
        private const val MAX_SINK_LATENCY_MS = 2_000

        /** 隐藏 API AudioTrack.getLatency()：返回 track 缓冲 + HAL + 外设链路总延迟（ms）。
         *  greylist 可反射访问，Media3 AudioTrackPositionTracker 同样在用；失败返回 null。 */
        private val getLatencyMethod by lazy {
            runCatching { AudioTrack::class.java.getMethod("getLatency") }.getOrNull()
        }
    }
}
