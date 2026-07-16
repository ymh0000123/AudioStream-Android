package com.xiaofeishu.audiostream.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaofeishu.audiostream.domain.model.ConnectionRecord
import com.xiaofeishu.audiostream.domain.model.Protocol
import com.xiaofeishu.audiostream.domain.model.SavedServer
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.repository.DiscoveryRepository
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主页 ViewModel：发现服务器、连接历史、收藏服务器。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** 合并发现与收藏：收藏的服务器标记 saved=true。 */
    val servers: StateFlow<List<ServerInfo>> = combine(
        discoveryRepository.servers,
        settingsRepository.savedServers
    ) { discovered, saved ->
        val savedKeys = saved.map { it.key }.toSet()
        val fromSaved = saved.map { ServerInfo(it.name, it.address, it.port, it.protocol, saved = true) }
        val fromDiscovered = discovered.filter { it.key !in savedKeys }
        (fromSaved + fromDiscovered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val isScanning: StateFlow<Boolean> = discoveryRepository.isScanning

    val history: StateFlow<List<ConnectionRecord>> = settingsRepository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val savedServers: StateFlow<List<SavedServer>> = settingsRepository.savedServers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val preferredProtocol: StateFlow<Protocol> = settingsRepository.preferredProtocol
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Protocol.WEBSOCKET
        )

    val latencyMode: StateFlow<Int> = settingsRepository.latencyMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 150
        )

    /** 是否忽略播放页的蓝牙链路延迟警告提示。 */
    val hideSinkLatencyHint: StateFlow<Boolean> = settingsRepository.hideSinkLatencyHint

    fun setHideSinkLatencyHint(hidden: Boolean) {
        viewModelScope.launch { settingsRepository.saveHideSinkLatencyHint(hidden) }
    }

    fun startScan() = discoveryRepository.startScan()
    fun stopScan() = discoveryRepository.stopScan()

    fun toggleSaved(server: ServerInfo) {
        viewModelScope.launch {
            if (server.saved) {
                settingsRepository.removeSavedServer(
                    SavedServer(server.name, server.address, server.port, server.protocol)
                )
            } else {
                settingsRepository.saveServer(
                    SavedServer(server.name, server.address, server.port, server.protocol)
                )
            }
        }
    }

    fun removeSaved(server: SavedServer) {
        viewModelScope.launch { settingsRepository.removeSavedServer(server) }
    }

    fun savePreferredProtocol(protocol: Protocol) {
        viewModelScope.launch { settingsRepository.savePreferredProtocol(protocol) }
    }

    fun saveLatencyMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveLatencyMode(mode) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsRepository.clearHistory() }
    }

    /** 历史记录转 ServerInfo（用于一键连接）。 */
    fun recordToServer(record: ConnectionRecord): ServerInfo =
        ServerInfo(name = record.display, address = record.address, port = record.port, protocol = record.protocol)

    /** 收藏服务器转 ServerInfo。 */
    fun savedToServer(server: SavedServer): ServerInfo =
        ServerInfo(server.name, server.address, server.port, server.protocol, saved = true)
}
