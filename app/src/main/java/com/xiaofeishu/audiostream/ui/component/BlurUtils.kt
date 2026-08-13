package com.xiaofeishu.audiostream.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 是否启用毛玻璃效果。默认关闭，由主题层提供。 */
val LocalEnableBlur = staticCompositionLocalOf { false }

/** 是否启用主要交互的触感反馈，默认开启。 */
val LocalHapticFeedbackEnabled = staticCompositionLocalOf { true }
/**
 * 创建可复用的毛玻璃 Backdrop：设备支持 RenderEffect 且开关打开时生效，
 * 否则返回 null(调用方回退为纯色背景)。
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 带毛玻璃的顶栏容器:backdrop 为 null 时不做任何处理。 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    if (backdrop != null) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f * LocalDensity.current.density,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = colorScheme.surface.copy(alpha = 0.87f)),
                    ),
                ),
            ),
        ) {
            content()
        }
    } else {
        content()
    }
}

/** 触发轻触反馈；全局关闭时不执行系统震动。 */
fun HapticFeedback.contextClick(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackType.ContextClick)
}
