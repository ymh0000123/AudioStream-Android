package com.xiaofeishu.audiostream.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.xiaofeishu.audiostream.domain.model.ThemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 系统动态取色（Material You）和应用内置色板。
 *
 * 系统取色只读取 Android 12+ 的系统色板资源，不需要壁纸或存储权限；旧系统选择
 * “系统取色”时回退到靛蓝紫。手动色板不受系统版本限制。
 */
object DynamicColors {

    /** 系统色板需要 Android 12（S, API 31）。 */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Composable
    fun scheme(mode: ThemeMode, darkTheme: Boolean): Colors = when (mode) {
        ThemeMode.SYSTEM -> if (darkTheme) darkScheme() else lightScheme()
        else -> if (darkTheme) manualDarkScheme(mode) else manualLightScheme(mode)
    }

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

/** 系统色板的一组取样。数字越小越浅，越大越深。 */
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

/** 只覆盖品牌色相关槽位，保留 Miuix 默认的中性色层次。 */
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

private data class ManualPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightTertiary: Color,
    val lightOnTertiary: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkTertiary: Color,
    val darkOnTertiary: Color,
)

private fun palette(mode: ThemeMode): ManualPalette = when (mode) {
    ThemeMode.OCEAN -> ManualPalette(
        Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFFE0F2F1), Color(0xFF00695C),
        Color(0xFF82B1FF), Color(0xFF255CA8), Color(0xFF134E4A), Color(0xFF99F6E4),
    )
    ThemeMode.TEAL -> ManualPalette(
        Color(0xFF008577), Color(0xFF009688), Color(0xFFE3F2FD), Color(0xFF1565C0),
        Color(0xFF5EEAD4), Color(0xFF0F766E), Color(0xFF1E3A5F), Color(0xFFBFDBFE),
    )
    ThemeMode.ORANGE -> ManualPalette(
        Color(0xFFE65100), Color(0xFFF57C00), Color(0xFFE8F5E9), Color(0xFF2E7D32),
        Color(0xFFFFAB70), Color(0xFFB45309), Color(0xFF23452D), Color(0xFF86EFAC),
    )
    ThemeMode.PINK -> ManualPalette(
        Color(0xFFC2185B), Color(0xFFD81B60), Color(0xFFEDE7F6), Color(0xFF6A1B9A),
        Color(0xFFFF80AB), Color(0xFFAD1457), Color(0xFF3B2853), Color(0xFFD8B4FE),
    )
    else -> ManualPalette(
        Color(0xFF667EEA), Color(0xFF7B8FF0), Color(0xFFE8EAF6), Color(0xFF667EEA),
        Color(0xFF8B9CF7), Color(0xFF6C7FD8), Color(0xFF2A2A4A), Color(0xFF8B9CF7),
    )
}

private fun manualLightScheme(mode: ThemeMode): Colors {
    val p = palette(mode)
    return lightColorScheme(
        primary = p.lightPrimary,
        onPrimary = Color.White,
        primaryVariant = p.lightPrimary,
        primaryContainer = p.lightContainer,
        onPrimaryContainer = Color.White,
        disabledPrimarySlider = p.lightPrimary.copy(alpha = 0.35f),
        tertiaryContainer = p.lightTertiary,
        onTertiaryContainer = p.lightOnTertiary,
        tertiaryContainerVariant = p.lightTertiary,
    )
}

private fun manualDarkScheme(mode: ThemeMode): Colors {
    val p = palette(mode)
    return darkColorScheme(
        primary = p.darkPrimary,
        onPrimary = Color.Black,
        primaryVariant = p.darkPrimary,
        primaryContainer = p.darkContainer,
        onPrimaryContainer = Color.White,
        disabledPrimarySlider = p.darkPrimary.copy(alpha = 0.35f),
        tertiaryContainer = p.darkTertiary,
        onTertiaryContainer = p.darkOnTertiary,
        tertiaryContainerVariant = p.darkTertiary,
    )
}

/** Android 11 及以下选择系统取色、或动态取色失败时的回退配色。 */
internal val BrandLightColors: Colors = manualLightScheme(ThemeMode.INDIGO)
internal val BrandDarkColors: Colors = manualDarkScheme(ThemeMode.INDIGO)
