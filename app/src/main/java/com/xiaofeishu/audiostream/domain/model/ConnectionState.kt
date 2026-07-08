package com.xiaofeishu.audiostream.domain.model

/**
 * 连接/播放状态机。
 * - [DISCONNECTED] 未连接
 * - [CONNECTING] 连接中（握手前）
 * - [CONNECTED] 已握手，准备播放（瞬态）
 * - [PLAYING] 正在播放
 * - [ERROR] 出错（[PlaybackState.error] 携带详情）
 */
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, PLAYING, ERROR;

    val isActive: Boolean get() = this == CONNECTING || this == CONNECTED || this == PLAYING
}
