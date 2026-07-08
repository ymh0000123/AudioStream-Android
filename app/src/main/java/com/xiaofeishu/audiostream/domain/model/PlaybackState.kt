package com.xiaofeishu.audiostream.domain.model

/**
 * 聚合播放状态，作为 Service / Repository 的单一对外状态源。
 * ViewModel 据此映射为 UI 状态。
 */
data class PlaybackState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val audioFormat: AudioFormat? = null,
    val volume: Float = 0.8f,
    val receivedBytes: Long = 0L,
    val stats: StreamStats = StreamStats(),
    val error: String? = null,
    val server: ServerInfo? = null,
    val autoReconnect: Boolean = false,
    /** 自动重连尝试序号（0 表示非重连场景）。 */
    val reconnectAttempt: Int = 0,
    val mediaState: MediaState? = null,
    /** 客户端当前请求的目标码率（kbps）。 */
    val currentBitrate: Int = 1536
)
