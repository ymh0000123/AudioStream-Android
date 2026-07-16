package com.xiaofeishu.audiostream.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xiaofeishu.audiostream.domain.model.ConnectionRecord
import com.xiaofeishu.audiostream.domain.model.Protocol
import com.xiaofeishu.audiostream.domain.model.SavedServer
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import com.xiaofeishu.audiostream.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_VOLUME_FLOAT = floatPreferencesKey("volume_float")
private val KEY_VOLUME_LEGACY = intPreferencesKey("volume")          // 旧 int 0-100，迁移后弃用
private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_bool")
private val KEY_AUTO_RECONNECT_LEGACY = stringPreferencesKey("auto_reconnect") // 旧 "true"/"false"
private val KEY_PROTOCOL = stringPreferencesKey("protocol")
private val KEY_TARGET_BITRATE = intPreferencesKey("target_bitrate")
private val KEY_LATENCY_MODE = intPreferencesKey("latency_mode")
private val KEY_HIDE_SINK_LATENCY_HINT = booleanPreferencesKey("hide_sink_latency_hint")

/**
 * 持久化设置仓库实现。
 *
 * 迁移策略（保留现有用户数据）：
 * - 音量：新 key `volume_float`（0..1）；旧 key `volume`（int 0-100）若存在则 /100f 迁移一次后清除。
 * - 自动重连：新 boolean key；旧 string "true"/"false" 迁移一次后清除。
 * - 目标码率：`target_bitrate`（kbps），默认 1536。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val historyDataSource: ConnectionHistoryDataSource,
    private val savedServersDataSource: SavedServersDataSource,
    @AppScope appScope: CoroutineScope
) : SettingsRepository {

    override val volume: StateFlow<Float> = dataStore.data
        .map { prefs -> readVolume(prefs) }
        .stateIn(appScope, SharingStarted.Eagerly, 0.8f)

    override val autoReconnect: StateFlow<Boolean> = dataStore.data
        .map { prefs -> readAutoReconnect(prefs) }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    override val targetBitrate: StateFlow<Int> = dataStore.data
        .map { prefs -> readTargetBitrate(prefs) }
        .stateIn(appScope, SharingStarted.Eagerly, DEFAULT_TARGET_BITRATE)

    override val latencyMode: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[KEY_LATENCY_MODE] ?: DEFAULT_LATENCY_MODE }
        .stateIn(appScope, SharingStarted.Eagerly, DEFAULT_LATENCY_MODE)

    override val hideSinkLatencyHint: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_HIDE_SINK_LATENCY_HINT] ?: false }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    override val preferredProtocol: Flow<Protocol> = dataStore.data
        .map { prefs -> Protocol.fromWire(prefs[KEY_PROTOCOL]) }

    override val history: Flow<List<ConnectionRecord>> = historyDataSource.history

    override val savedServers: Flow<List<SavedServer>> = savedServersDataSource.savedServers

    override suspend fun saveVolume(volume: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_VOLUME_FLOAT] = volume.coerceIn(0f, 1f)
            prefs.remove(KEY_VOLUME_LEGACY)
        }
    }

    override suspend fun saveAutoReconnect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_RECONNECT] = enabled
            prefs.remove(KEY_AUTO_RECONNECT_LEGACY)
        }
    }

    override suspend fun loadTargetBitrate(): Int {
        return readTargetBitrate(dataStore.data.first())
    }

    override suspend fun saveTargetBitrate(bitrate: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_TARGET_BITRATE] = normalizeTargetBitrate(bitrate)
        }
    }

    override suspend fun saveLatencyMode(mode: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LATENCY_MODE] = mode
        }
    }

    override suspend fun saveHideSinkLatencyHint(hidden: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDE_SINK_LATENCY_HINT] = hidden
        }
    }

    override suspend fun savePreferredProtocol(protocol: Protocol) {
        dataStore.edit { prefs -> prefs[KEY_PROTOCOL] = protocol.wireValue }
    }

    override suspend fun addConnection(record: ConnectionRecord) {
        historyDataSource.addConnection(record)
    }

    override suspend fun clearHistory() {
        historyDataSource.clearHistory()
    }

    override suspend fun saveServer(server: SavedServer) {
        savedServersDataSource.saveServer(server)
    }

    override suspend fun removeSavedServer(server: SavedServer) {
        savedServersDataSource.removeSavedServer(server)
    }

    private fun readVolume(prefs: Preferences): Float {
        prefs[KEY_VOLUME_FLOAT]?.let { return it.coerceIn(0f, 1f) }
        // 迁移旧 int 0-100 -> float 0-1（首次读到时迁移，写入时清除旧 key）
        prefs[KEY_VOLUME_LEGACY]?.let { return (it / 100f).coerceIn(0f, 1f) }
        return 0.8f
    }

    private fun readAutoReconnect(prefs: Preferences): Boolean {
        prefs[KEY_AUTO_RECONNECT]?.let { return it }
        // 迁移旧 string "true"/"false"
        prefs[KEY_AUTO_RECONNECT_LEGACY]?.let { return it.equals("true", ignoreCase = true) }
        return false
    }

    private fun readTargetBitrate(prefs: Preferences): Int {
        return normalizeTargetBitrate(prefs[KEY_TARGET_BITRATE] ?: DEFAULT_TARGET_BITRATE)
    }

    private fun normalizeTargetBitrate(bitrate: Int): Int {
        return if (bitrate > 0) bitrate else DEFAULT_TARGET_BITRATE
    }

    companion object {
        private const val DEFAULT_TARGET_BITRATE = 1536
        private const val DEFAULT_LATENCY_MODE = 150
    }
}
