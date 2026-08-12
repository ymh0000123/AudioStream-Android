package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 紧凑顶栏的内容行高度（不含状态栏 inset）。 */
val AppTopBarHeight: Dp = 56.dp

/**
 * 紧凑单行顶栏：标题与操作按钮同一行。
 *
 * 替代 miuix 自带的 [top.yukonga.miuix.kmp.basic.TopAppBar]——后者是 HyperOS 大标题样式，
 * 展开态固定 104dp（56dp 空行 + 48dp 大标题区），首屏顶部留白过多。这里压成 56dp 单行，
 * 字号/字重/左右边距沿用 miuix 折叠态顶栏（title3 + Medium + 28dp），保证风格不跳。
 *
 * 状态栏 inset 由本组件自己吃（与 miuix 的"栏自己吃 inset"约定一致，因此外层 Scaffold
 * 不应再把 statusBars 兜底加到内容上，见 MainActivity 的 contentWindowInsets）。
 * 背景仍延伸到状态栏之后，滚动内容不会从标题旁露出。
 *
 * @param title 标题文本。
 * @param modifier 作用于顶栏容器。
 * @param color 顶栏背景色。
 * @param horizontalPadding 标题起始边距，默认与 [top.yukonga.miuix.kmp.basic.SmallTitle] 对齐。
 * @param actions 右侧操作区，通常放 IconButton。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.background,
    horizontalPadding: Dp = 28.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = color,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(AppTopBarHeight)
                .padding(start = horizontalPadding, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f, fill = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}
