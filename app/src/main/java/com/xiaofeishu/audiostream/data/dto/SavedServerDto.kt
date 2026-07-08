package com.xiaofeishu.audiostream.data.dto

import com.google.gson.annotations.SerializedName

/**
 * 收藏服务器的 Gson 序列化 DTO。
 */
data class SavedServerDto(
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("protocol") val protocol: String
)
