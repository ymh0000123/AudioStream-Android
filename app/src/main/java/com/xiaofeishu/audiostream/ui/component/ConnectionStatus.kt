package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.ui.theme.AppColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 连接状态指示器。颜色取自主题，不再硬编码 hex。
 */
@Composable
fun ConnectionStatus(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (state) {
        ConnectionState.DISCONNECTED -> AppColors.error to "未连接"
        ConnectionState.CONNECTING -> AppColors.warning to "连接中…"
        ConnectionState.CONNECTED -> MiuixTheme.colorScheme.primary to "已连接"
        ConnectionState.PLAYING -> MiuixTheme.colorScheme.primary to "播放中"
        ConnectionState.ERROR -> AppColors.error to "错误"
    }

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = MiuixTheme.colorScheme.onSurface)
    }
}
