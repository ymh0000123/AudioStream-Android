package com.xiaofeishu.audiostream.network.protocol

import com.xiaofeishu.audiostream.domain.model.Protocol
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按 [Protocol] 枚举创建对应 [AudioProtocol] 实例。
 * 枚举 when 无 else 分支：新增协议时编译器会强制处理，修复旧版 typo 静默回落 WebSocket 的问题。
 */
@Singleton
class AudioProtocolFactory @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun create(protocol: Protocol): AudioProtocol = when (protocol) {
        Protocol.WEBSOCKET -> WebSocketProtocol(okHttpClient)
    }
}
