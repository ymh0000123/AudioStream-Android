package com.xiaofeishu.audiostream.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * App 主题：Miuix（HyperOS 风格）+ 系统动态取色。
 *
 * Android 12+ 从系统色板（Material You）取品牌色注入 Miuix 配色，
 * Android 11 及以下回退品牌靛蓝紫，见 [DynamicColors]。
 */
@Composable
fun AudioStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DynamicColors.darkScheme() else DynamicColors.lightScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 现代做法：启用 edge-to-edge，状态栏透明，由系统按 isAppearanceLightStatusBars 着色图标
            (view.context as? ComponentActivity)?.enableEdgeToEdge()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MiuixTheme(
        colors = colorScheme,
        content = content
    )
}

/** 语义色：Miuix 配色表没有 error/warning 槽位，集中在此定义供各屏复用。 */
object AppColors {
    val error: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFEF4444)

    val warning: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFBBF24) else Color(0xFFF9A825)

    val success: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF16A34A)
}
