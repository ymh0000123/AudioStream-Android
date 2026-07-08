package com.xiaofeishu.audiostream.domain.model

/**
 * 用户收藏的服务器（可带协议覆盖，弥补 mDNS 不携带协议信息）。
 */
data class SavedServer(
    val name: String,
    val address: String,
    val port: Int,
    val protocol: Protocol
) {
    val display: String get() = "$address:$port"

    /** 唯一性键，按地址+端口去重。 */
    val key: String get() = "$address:$port"
}
