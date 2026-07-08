package com.xiaofeishu.audiostream.network.protocol

import com.xiaofeishu.audiostream.network.AudioEvent
import kotlinx.coroutines.flow.Flow

interface AudioProtocol {
    suspend fun connect(address: String, port: Int, initialBitrate: Int? = null): Flow<AudioEvent>
    suspend fun disconnect()
    val isConnected: Boolean
    fun send(text: String) {}
    fun setBitrate(bitrate: Int) {}
}
