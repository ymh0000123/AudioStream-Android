package com.xiaofeishu.audiostream.audio

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 创建 [AudioPlayer]。每次连接需要一个新实例（AudioTrack 不可跨会话复用）。
 * lowLatency 默认开启，以最小化端到端缓冲延迟。
 */
@Singleton
class AudioPlayerFactory @Inject constructor() {
    fun create(lowLatency: Boolean = true): AudioPlayer = AudioPlayer(lowLatency)
}
