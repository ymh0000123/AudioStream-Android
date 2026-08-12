package com.xiaofeishu.audiostream.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 系统动态取色（Material You）。
 *
 * Miuix 自身没有动态取色能力，这里读取 Android 12+ 由系统从壁纸生成的色板资源
 * （android.R.color.system_accent1_*），注入 Miuix 配色表的品牌色槽位。
 *
 * 取色只走系统色板资源，不调用 WallpaperManager.getWallpaperColors()，
 * 因此不需要任何存储权限、不会弹授权框；Android 11 及以下直接回退品牌色。
 */
object DynamicColors {

    /** 系统色板需要 Android 12（S, API 31）。 */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Composable
    fun lightScheme(): Colors {
        if (!isSupported) return BrandLightColors
        val context = LocalContext.current
        return runCatching { buildLight(context.systemPalette()) }.getOrElse { BrandLightColors }
    }

    @Composable
    fun darkScheme(): Colors {
        if (!isSupported) return BrandDarkColors
        val context = LocalContext.current
        return runCatching { buildDark(context.systemPalette()) }.getOrElse { BrandDarkColors }
    }
}

/**
 * 系统色板的一组取样。数字越小越浅（accent1_50 接近白），越大越深。
 * 命名沿用 AOSP 的 tone 语义，便于对照系统资源。
 */
private data class SystemPalette(
    val accent100: Color,
    val accent200: Color,
    val accent300: Color,
    val accent500: Color,
    val accent600: Color,
    val accent700: Color,
    val neutralVariant: Color,
)

@RequiresApi(Build.VERSION_CODES.S)
private fun android.content.Context.systemPalette(): SystemPalette {
    fun color(resId: Int) = Color(ContextCompat.getColor(this, resId))
    return SystemPalette(
        accent100 = color(android.R.color.system_accent1_100),
        accent200 = color(android.R.color.system_accent1_200),
        accent300 = color(android.R.color.system_accent1_300),
        accent500 = color(android.R.color.system_accent1_500),
        accent600 = color(android.R.color.system_accent1_600),
        accent700 = color(android.R.color.system_accent1_700),
        neutralVariant = color(android.R.color.system_neutral2_500),
    )
}

/**
 * 只覆盖与品牌色强相关的槽位（primary / primaryContainer / tertiaryContainer），
 * 其余留用 Miuix 默认值，避免破坏 HyperOS 的中性灰层次与控件观感。
 */
private fun buildLight(p: SystemPalette): Colors = lightColorScheme(
    primary = p.accent600,
    onPrimary = Color.White,
    primaryVariant = p.accent600,
    primaryContainer = p.accent500,
    onPrimaryContainer = Color.White,
    disabledPrimarySlider = p.accent200,
    tertiaryContainer = p.accent100,
    onTertiaryContainer = p.accent700,
    tertiaryContainerVariant = p.accent100,
    onBackgroundVariant = p.neutralVariant,
)

private fun buildDark(p: SystemPalette): Colors = darkColorScheme(
    primary = p.accent200,
    onPrimary = Color.Black,
    primaryVariant = p.accent200,
    primaryContainer = p.accent300,
    onPrimaryContainer = Color.Black,
    disabledPrimarySlider = p.accent700,
    tertiaryContainer = p.accent700,
    onTertiaryContainer = p.accent200,
    tertiaryContainerVariant = p.accent700,
    onBackgroundVariant = p.neutralVariant,
)

/** Android 11 及以下、或取色失败时的回退配色：品牌靛蓝紫。 */
internal val BrandLightColors: Colors = lightColorScheme(
    primary = Color(0xFF667EEA),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF667EEA),
    primaryContainer = Color(0xFF7B8FF0),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFFE8EAF6),
    onTertiaryContainer = Color(0xFF667EEA),
    tertiaryContainerVariant = Color(0xFFE8EAF6),
)

internal val BrandDarkColors: Colors = darkColorScheme(
    primary = Color(0xFF8B9CF7),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF8B9CF7),
    primaryContainer = Color(0xFF6C7FD8),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFF2A2A4A),
    onTertiaryContainer = Color(0xFF8B9CF7),
    tertiaryContainerVariant = Color(0xFF2A2A4A),
)
