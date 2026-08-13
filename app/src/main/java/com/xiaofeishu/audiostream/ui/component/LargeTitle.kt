package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面大标题：作为页面内容的第一个元素展示（不用顶栏标题，见各页面布局）。
 *
 * 字号/字重与 About 页 Hero 大标题保持一致（32sp Bold），左右边距与
 * miuix SmallTitle 对齐（28dp）。
 */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
    )
}
