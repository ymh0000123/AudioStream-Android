package com.xiaofeishu.audiostream.domain.model

/**
 * 实时流统计。
 * @param bitrateKbps 过去约 1 秒滑动窗口内的接收码率
 * @param bufferLatencyMs 客户端侧缓冲延迟（已写入但未播放的音频时长）。PCM 流无时间戳，无法测网络延迟。
 * @param quality 连接质量评级
 */
data class StreamStats(
    val bitrateKbps: Int = 0,
    val bufferLatencyMs: Int = 0,
    val quality: Quality = Quality.UNKNOWN
)

enum class Quality { GOOD, FAIR, POOR, UNKNOWN }
