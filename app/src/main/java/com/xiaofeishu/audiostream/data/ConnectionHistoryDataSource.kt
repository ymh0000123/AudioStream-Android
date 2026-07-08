package com.xiaofeishu.audiostream.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaofeishu.audiostream.data.dto.ConnectionRecordDto
import com.xiaofeishu.audiostream.domain.model.ConnectionRecord
import com.xiaofeishu.audiostream.domain.model.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_HISTORY = stringPreferencesKey("connection_history")
private val KEY_HISTORY_V2 = stringPreferencesKey("connection_history_v2")

/**
 * 连接历史数据源。用 Gson JSON 序列化，根治旧版用 ":" 分隔导致 IPv6 地址被拆碎的 Bug。
 * 启动时若检测到旧格式历史，迁移到 V2（JSON）一次。
 */
@Singleton
class ConnectionHistoryDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    private val type = object : TypeToken<List<ConnectionRecordDto>>() {}.type

    val history: Flow<List<ConnectionRecord>> = dataStore.data.map { prefs ->
        val v2 = prefs[KEY_HISTORY_V2]
        val dtos: List<ConnectionRecordDto> = if (!v2.isNullOrEmpty()) {
            gson.fromJson(v2, type) ?: emptyList()
        } else {
            // 迁移：尝试解析旧 ":"/"|" 格式（仅 IPv4 安全），转写为 V2。
            val legacy = parseLegacy(prefs[KEY_HISTORY])
            if (legacy.isNotEmpty()) legacy else emptyList()
        }
        dtos.sortedByDescending { it.lastConnected }.take(MAX_HISTORY).map { it.toDomain() }
    }

    suspend fun addConnection(record: ConnectionRecord) {
        dataStore.edit { prefs ->
            val current: List<ConnectionRecordDto> = readCurrent(prefs)
            val existing = current.indexOfFirst { it.address == record.address && it.port == record.port }
            val updated = current.toMutableList()
            if (existing >= 0) {
                updated[existing] = updated[existing].copy(
                    lastConnected = record.lastConnected,
                    connectCount = updated[existing].connectCount + 1,
                    protocol = record.protocol.wireValue
                )
            } else {
                updated.add(
                    ConnectionRecordDto(
                        address = record.address,
                        port = record.port,
                        protocol = record.protocol.wireValue,
                        lastConnected = record.lastConnected,
                        connectCount = 1
                    )
                )
            }
            val sorted = updated.sortedByDescending { it.lastConnected }.take(MAX_HISTORY)
            prefs[KEY_HISTORY_V2] = gson.toJson(sorted)
            prefs.remove(KEY_HISTORY)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs[KEY_HISTORY_V2] = "[]"
            prefs.remove(KEY_HISTORY)
        }
    }

    private fun readCurrent(prefs: Preferences): List<ConnectionRecordDto> {
        val v2 = prefs[KEY_HISTORY_V2]
        if (!v2.isNullOrEmpty()) {
            return runCatching { gson.fromJson<List<ConnectionRecordDto>>(v2, type) ?: emptyList() }
                .getOrDefault(emptyList())
        }
        return parseLegacy(prefs[KEY_HISTORY])
    }

    /** 解析旧版 "address:port:protocol:last:count|..." 格式。IPv6 会因 ":" 冲突而失败，按失败处理。 */
    private fun parseLegacy(raw: String?): List<ConnectionRecordDto> {
        if (raw.isNullOrEmpty() || raw == "[]") return emptyList()
        return runCatching {
            raw.split("|").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size >= 5) {
                    ConnectionRecordDto(
                        address = parts[0],
                        port = parts[1].toIntOrNull() ?: return@mapNotNull null,
                        protocol = parts[2],
                        lastConnected = parts[3].toLongOrNull() ?: 0L,
                        connectCount = parts[4].toIntOrNull() ?: 1
                    )
                } else null
            }
        }.getOrDefault(emptyList())
    }

    private fun ConnectionRecordDto.toDomain() = ConnectionRecord(
        address = address,
        port = port,
        protocol = Protocol.fromWire(protocol),
        lastConnected = lastConnected,
        connectCount = connectCount
    )

    companion object {
        private const val MAX_HISTORY = 20
    }
}
