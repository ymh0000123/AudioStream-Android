package com.xiaofeishu.audiostream.domain.repository

import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.domain.model.PlaybackState
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 流连接/播放仓库。@Singleton 单例，Service 与 ViewModel 共享同一实例。
 * 承担：协议选择、连接、自动重连、码率统计、错误上屏。
 */
interface StreamRepository {

    /** 聚合播放状态。 */
    val state: StateFlow<PlaybackState>

    /** 连接到指定服务器。 */
    fun connect(server: ServerInfo)

    /** 用户主动断开。会阻止自动重连。 */
    fun disconnect()

    /** 设置音量（0..1）。 */
    fun setVolume(volume: Float)

    /** 启用/禁用自动重连。 */
    fun setAutoReconnect(enabled: Boolean)

    /** 发送媒体控制命令到服务器。 */
    fun sendCommand(action: MediaAction)

    /** 跳转到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long)

    /** 请求指定码率（kbps）。 */
    fun setBitrate(bitrate: Int)

    /** 静音/恢复服务端（电脑）扬声器。采集不受影响，本机串流照常播放。 */
    fun setServerMute(muted: Boolean)
}
