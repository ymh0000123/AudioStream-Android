package com.xiaofeishu.audiostream.domain.model

/** 深色模式：SYSTEM 跟随系统，DARK 强制深色，LIGHT 强制浅色。 */
enum class DarkMode(val wireValue: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    DARK("dark", "深色"),
    LIGHT("light", "浅色");

    companion object {
        fun fromWire(value: String?): DarkMode = entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}
