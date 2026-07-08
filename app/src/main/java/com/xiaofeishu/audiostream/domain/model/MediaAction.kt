package com.xiaofeishu.audiostream.domain.model

enum class MediaAction(val jsonAction: String) {
    PLAY_PAUSE("play_pause"),
    PREVIOUS("previous"),
    NEXT("next"),
    SEEK_TO("seek_to"),
    GET_STATE("get_state");

    fun toJson(positionMs: Long? = null): String {
        return when (this) {
            SEEK_TO -> """{"type":"command","action":"$jsonAction","position":$positionMs}"""
            else -> """{"type":"command","action":"$jsonAction"}"""
        }
    }
}
