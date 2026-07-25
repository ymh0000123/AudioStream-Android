package com.xiaofeishu.audiostream.audio

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerFactory @Inject constructor() {
    fun create(latencyMode: Int = 150): AudioPlayer {
        val lowLatency = latencyMode == 100
        val bufferCapacityMs = when (latencyMode) {
            100 -> 200
            150 -> 300
            200 -> 400
            else -> 500
        }
        val startThresholdMs = when (latencyMode) {
            100 -> 40
            150 -> 60
            200 -> 80
            else -> 60
        }
        return AudioPlayer(
            lowLatency = lowLatency,
            bufferCapacityMs = bufferCapacityMs,
            startThresholdMs = startThresholdMs
        )
    }
}
