package com.xiaofeishu.audiostream.domain.model

/**
 * 连接协议。替换原裸字符串，避免拼写错误静默回落到 WebSocket。
 */
enum class Protocol(val wireValue: String, val displayName: String) {
    WEBSOCKET("websocket", "WebSocket");

    companion object {
        fun fromWire(value: String?): Protocol =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: WEBSOCKET
    }
}
