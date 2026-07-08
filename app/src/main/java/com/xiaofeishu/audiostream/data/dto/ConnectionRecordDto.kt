package com.xiaofeishu.audiostream.data.dto

import com.google.gson.annotations.SerializedName

/**
 * 连接历史的 Gson 序列化 DTO。独立于 domain model，避免 IPv6 地址被分隔符拆碎。
 */
data class ConnectionRecordDto(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("last_connected") val lastConnected: Long,
    @SerializedName("connect_count") val connectCount: Int
)
