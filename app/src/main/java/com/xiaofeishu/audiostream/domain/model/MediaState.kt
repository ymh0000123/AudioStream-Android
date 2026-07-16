package com.xiaofeishu.audiostream.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class MediaState(
    @SerializedName("playing") val playing: Boolean = false,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("album") val album: String = "",
    @SerializedName("position") val positionMs: Long = 0,
    @SerializedName("duration") val durationMs: Long = 0,
    /** 服务端（电脑）扬声器是否静音。服务端 loopback 采集不受端点静音影响，串流照常。 */
    @SerializedName("muted") val muted: Boolean = false
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): MediaState? {
            return try {
                val raw = gson.fromJson(json, Map::class.java)
                if (raw["type"] == "state") {
                    MediaState(
                        playing = raw["playing"] as? Boolean ?: false,
                        title = raw["title"] as? String ?: "",
                        artist = raw["artist"] as? String ?: "",
                        album = raw["album"] as? String ?: "",
                        positionMs = (raw["position"] as? Number)?.toLong() ?: 0L,
                        durationMs = (raw["duration"] as? Number)?.toLong() ?: 0L,
                        muted = raw["muted"] as? Boolean ?: false
                    )
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
