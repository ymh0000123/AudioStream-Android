package com.xiaofeishu.audiostream.network

import com.xiaofeishu.audiostream.domain.model.AudioFormat
import com.xiaofeishu.audiostream.domain.model.MediaState

/**
 * 协议层产生的事件。StreamRepository 收集后映射为 PlaybackState。
 */
sealed class AudioEvent {
    data class Connected(val format: AudioFormat) : AudioEvent()
    data class AudioData(val data: ByteArray) : AudioEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioData) return false
            return data.contentEquals(other.data)
        }
        override fun hashCode(): Int = data.contentHashCode()
    }
    data class Disconnected(val reason: String) : AudioEvent()
    data class Error(val exception: Throwable) : AudioEvent()
    data class StateUpdate(val state: MediaState) : AudioEvent()
    data class BitrateChanged(val bitrate: Int, val format: AudioFormat) : AudioEvent()
}
