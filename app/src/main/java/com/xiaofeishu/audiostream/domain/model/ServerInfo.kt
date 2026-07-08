package com.xiaofeishu.audiostream.domain.model

/**
 * 一个可连接的流服务器。可来自 mDNS 发现或手动输入。
 * @param protocol 该服务器使用的协议；发现的服务器默认 WebSocket，收藏时可覆盖。
 */
data class ServerInfo(
    val name: String,
    val address: String,
    val port: Int,
    val protocol: Protocol = Protocol.WEBSOCKET,
    val saved: Boolean = false
) {
    val display: String get() = "$address:$port"
    val key: String get() = "$address:$port"
}
