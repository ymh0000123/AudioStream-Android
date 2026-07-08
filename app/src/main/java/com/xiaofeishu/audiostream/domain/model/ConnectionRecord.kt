package com.xiaofeishu.audiostream.domain.model

/**
 * 连接历史记录。
 */
data class ConnectionRecord(
    val address: String,
    val port: Int,
    val protocol: Protocol,
    val lastConnected: Long,
    val connectCount: Int
) {
    val display: String get() = "$address:$port"
}
