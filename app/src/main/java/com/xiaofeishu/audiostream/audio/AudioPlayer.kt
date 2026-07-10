package com.xiaofeishu.audiostream.audio

import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.xiaofeishu.audiostream.domain.model.AudioFormat as StreamFormat

class AudioPlayer(
    private val lowLatency: Boolean = true
) {

    fun interface WriteErrorListener {
        fun onWriteError(errorCode: Int, audioTrack: AudioTrack)
    }

    var writeErrorListener: WriteErrorListener? = null

    private var audioTrack: AudioTrack? = null
    private var _isPlaying = false
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
    @Volatile var totalWrittenBytes: Long = 0L
        private set

    val isPlaying: Boolean get() = _isPlaying
    val negotiationNote: String? get() = negotiatedTo

    fun initialize(format: StreamFormat) {
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
     */
    fun playWithCatchup(pcmData: ByteArray, format: com.xiaofeishu.audiostream.domain.model.AudioFormat, catchupThresholdMs: Int) {
        if (!_isPlaying || catchupThresholdMs <= 0) {
            play(pcmData)
            return
        }
        recoverLongRunningTrackIfNeeded()

        val headFrames = playbackHeadPositionFrames()
        val writtenFrames = totalWrittenBytes / format.bytesPerFrame
        val bufferedMs = ((writtenFrames - headFrames) * 1000 / format.sampleRate).toInt()

        if (bufferedMs > catchupThresholdMs && pcmData.size >= format.bytesPerFrame) {
            val framesToSkip = ((bufferedMs - catchupThresholdMs) * format.sampleRate / 1000).toInt()
            val bytesToSkip = (framesToSkip * format.bytesPerFrame)
                .coerceAtMost(pcmData.size - format.bytesPerFrame)
                .let { it - (it % format.bytesPerFrame) }
            if (bytesToSkip > 0) {
                totalWrittenBytes += bytesToSkip
                val remaining = pcmData.copyOfRange(bytesToSkip, pcmData.size)
                writeLoop(remaining)
                return
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
        try {
            audioTrack?.pause()
        } catch (_: IllegalStateException) {}
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

    fun release() {
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
        // 低延迟优先：取系统建议下限，仅在不低于 20ms 的安全下限时使用。
        // 旧的 100ms 下限叠加 getMinBufferSize() 会在部分设备产生 200-400ms 延迟，
        // 20ms 足以覆盖大多数设备最小缓冲需求，配合 PERFORMANCE_MODE_LOW_LATENCY 生效。
        val safeFloorBytes = (format.sampleRate * format.bytesPerFrame * 20L / 1000).toInt()
            .coerceAtLeast(1)
        val bufferSize = minBufferSize.coerceAtLeast(safeFloorBytes)

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
        return track
    }

    private fun recoverTrack(): Boolean {
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

    private fun writeLoop(pcmData: ByteArray) {
        var offset = 0
        var zeroWriteCount = 0
        while (offset < pcmData.size) {
            val track = currentOrRecoveredTrack() ?: return
            val written = try {
                track.write(pcmData, offset, pcmData.size - offset)
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
        if (offset > 0) {
            try {
                audioTrack?.play()
            } catch (_: IllegalStateException) {}
            recoverStalledPlaybackIfNeeded()
        }
    }

    private fun currentOrRecoveredTrack(): AudioTrack? {
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

    private fun resetTrackCounters() {
        trackStartedAtMs = SystemClock.elapsedRealtime()
        playbackHeadWraps = 0L
        lastPlaybackHeadRaw = 0L
        lastObservedHeadFrames = 0L
        lastObservedWrittenBytes = 0L
        lastObservedAtMs = 0L
        playbackHeadStallStartedAtMs = 0L
    }

    companion object {
        private const val MAX_ZERO_WRITES = 5
        private const val WRITE_RETRY_DELAY_MS = 2L
        private const val STALL_CHECK_INTERVAL_MS = 1_000L
        private const val PLAYBACK_STALL_MS = 3_000L
        private const val MAX_TRACK_LIFETIME_MS = 2 * 60 * 60 * 1000L
        private const val PLAYBACK_HEAD_MASK = 0xffffffffL
        private const val PLAYBACK_HEAD_WRAP = 1L shl 32
    }
}
