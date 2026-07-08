package com.xiaofeishu.audiostream.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaofeishu.audiostream.data.dto.SavedServerDto
import com.xiaofeishu.audiostream.domain.model.SavedServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_SAVED_SERVERS = stringPreferencesKey("saved_servers")

/**
 * 收藏服务器数据源。Gson JSON 序列化，全新 key，无旧数据。
 */
@Singleton
class SavedServersDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    private val type = object : TypeToken<List<SavedServerDto>>() {}.type

    val savedServers: Flow<List<SavedServer>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SAVED_SERVERS]
        if (raw.isNullOrEmpty()) emptyList()
        else runCatching {
            (gson.fromJson<List<SavedServerDto>>(raw, type) ?: emptyList()).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun saveServer(server: SavedServer) {
        dataStore.edit { prefs ->
            val current = readCurrent(prefs).toMutableList()
            current.removeAll { it.address == server.address && it.port == server.port }
            current.add(SavedServerDto(server.name, server.address, server.port, server.protocol.wireValue))
            prefs[KEY_SAVED_SERVERS] = gson.toJson(current)
        }
    }

    suspend fun removeSavedServer(server: SavedServer) {
        dataStore.edit { prefs ->
            val current = readCurrent(prefs)
            prefs[KEY_SAVED_SERVERS] = gson.toJson(
                current.filterNot { it.address == server.address && it.port == server.port }
            )
        }
    }

    private fun readCurrent(prefs: Preferences): List<SavedServerDto> {
        val raw = prefs[KEY_SAVED_SERVERS]
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { gson.fromJson<List<SavedServerDto>>(raw, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private fun SavedServerDto.toDomain() = SavedServer(
        name = name,
        address = address,
        port = port,
        protocol = com.xiaofeishu.audiostream.domain.model.Protocol.fromWire(protocol)
    )
}
