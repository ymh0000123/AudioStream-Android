package com.xiaofeishu.audiostream.domain.repository

import com.xiaofeishu.audiostream.domain.model.ConnectionRecord
import com.xiaofeishu.audiostream.domain.model.Protocol
import com.xiaofeishu.audiostream.domain.model.SavedServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 持久化设置仓库：音量、自动重连、默认协议、连接历史、收藏服务器。
 */
interface SettingsRepository {

    /** 音量 0..1。 */
    val volume: StateFlow<Float>

    /** 是否启用自动重连。 */
    val autoReconnect: StateFlow<Boolean>

    /** 目标码率（kbps）。 */
    val targetBitrate: StateFlow<Int>

    /** 播放延迟模式：0=禁用跳帧，100=低延迟，150=平衡，200=稳定。 */
    val latencyMode: StateFlow<Int>

    /** 是否忽略播放页的蓝牙链路延迟警告提示。 */
    val hideSinkLatencyHint: StateFlow<Boolean>

    /** 默认协议偏好。 */
    val preferredProtocol: Flow<Protocol>

    /** 连接历史（按 lastConnected 降序，最多 20 条）。 */
    val history: Flow<List<ConnectionRecord>>

    /** 收藏服务器列表。 */
    val savedServers: Flow<List<SavedServer>>

    suspend fun saveVolume(volume: Float)
    suspend fun saveAutoReconnect(enabled: Boolean)
    suspend fun loadTargetBitrate(): Int
    suspend fun saveTargetBitrate(bitrate: Int)
    suspend fun saveLatencyMode(mode: Int)
    suspend fun saveHideSinkLatencyHint(hidden: Boolean)
    suspend fun savePreferredProtocol(protocol: Protocol)
    suspend fun addConnection(record: ConnectionRecord)
    suspend fun clearHistory()
    suspend fun saveServer(server: SavedServer)
    suspend fun removeSavedServer(server: SavedServer)
}
