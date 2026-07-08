package com.xiaofeishu.audiostream.domain.model

import com.google.gson.JsonParser

/**
 * 音频流格式握手信息。服务器在连接后发送的第一条消息中包含此信息。
 *
 * 解析逻辑（fromJson）保留在此：协议层共用一个解析入口。
 */
data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int
) {
    val bytesPerSample: Int get() = bitsPerSample / 8
    val bytesPerFrame: Int get() = bytesPerSample * channels

    override fun toString(): String = "${sampleRate}Hz / ${channels}ch / ${bitsPerSample}bit"

    companion object {
        /**
         * 从握手 JSON 解析。要求 sample_rate / channels / bits_per_sample 三字段齐全；
         * 忽略额外字段（如 type:"format"）。解析失败返回 null。
         */
        fun fromJson(json: String): AudioFormat? = try {
            val obj = JsonParser.parseString(json).asJsonObject
            if (!obj.has("sample_rate") || !obj.has("channels") || !obj.has("bits_per_sample")) {
                null
            } else {
                AudioFormat(
                    sampleRate = obj.get("sample_rate").asInt,
                    channels = obj.get("channels").asInt,
                    bitsPerSample = obj.get("bits_per_sample").asInt
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
