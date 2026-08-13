package com.xiaofeishu.audiostream.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaofeishu.audiostream.domain.model.DarkMode
import com.xiaofeishu.audiostream.domain.model.ThemeMode
import com.xiaofeishu.audiostream.ui.component.LocalEnableBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 当前生效的深色模式（含"跟随系统"解析后的结果），供语义色等跨主题组件读取。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * App 主题：Miuix（HyperOS 风格）+ 系统动态取色。
 *
 * Android 12+ 从系统色板（Material You）取品牌色注入 Miuix 配色，
 * Android 11 及以下回退品牌靛蓝紫，见 [DynamicColors]。
 *
 * @param darkMode 深色模式：SYSTEM 跟随系统，DARK 强制深色，LIGHT 强制浅色。
 * @param themeMode 配色方案（强调色）。
 */
@Composable
fun AudioStreamTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
    }
    val colorScheme = DynamicColors.scheme(themeMode, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 现代做法：启用 edge-to-edge，状态栏透明，由系统按 isAppearanceLightStatusBars 着色图标
            (view.context as? ComponentActivity)?.enableEdgeToEdge()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalEnableBlur provides true
    ) {
        MiuixTheme(
            colors = colorScheme,
            content = content
        )
    }
}

/** 语义色：Miuix 配色表没有 error/warning 槽位，集中在此定义供各屏复用。 */
object AppColors {
    val error: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0xFFF87171) else Color(0xFFEF4444)

    val warning: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0xFFFBBF24) else Color(0xFFF9A825)

    val success: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0xFF4ADE80) else Color(0xFF16A34A)
}
