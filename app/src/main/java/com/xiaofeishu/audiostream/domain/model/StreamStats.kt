package com.xiaofeishu.audiostream.domain.model

/**
 * 实时流统计。
 * @param bitrateKbps 过去约 1 秒滑动窗口内的接收码率
 * @param bufferLatencyMs 客户端侧缓冲延迟（播放队列 + 已写入但未播放的音频时长）。PCM 流无时间戳，无法测网络延迟。
 * @param quality 连接质量评级（只评 app 可控的缓冲延迟，不含链路固有延迟）
 * @param sinkLatencyMs 系统+输出链路在途延迟估计（已交给系统但尚未发声，蓝牙下主要是编解码/传输/耳机缓冲），null=不可测
 * @param bluetoothDevice 蓝牙输出设备名（可能为空串），null=当前非蓝牙输出
 */
data class StreamStats(
    val bitrateKbps: Int = 0,
    val bufferLatencyMs: Int = 0,
    val quality: Quality = Quality.UNKNOWN,
    val sinkLatencyMs: Int? = null,
    val bluetoothDevice: String? = null
)

enum class Quality { GOOD, FAIR, POOR, UNKNOWN }
