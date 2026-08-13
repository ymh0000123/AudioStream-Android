package com.xiaofeishu.audiostream.domain.model

/** 应用配色方案。SYSTEM 在不支持动态取色的系统上回退为 INDIGO。 */
enum class ThemeMode(val wireValue: String, val displayName: String) {
    SYSTEM("system", "系统取色"),
    INDIGO("indigo", "靛蓝紫"),
    OCEAN("ocean", "海洋蓝"),
    TEAL("teal", "青绿色"),
    ORANGE("orange", "活力橙"),
    PINK("pink", "樱花粉");

    companion object {
        fun fromWire(value: String?): ThemeMode = entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}
