package com.xiaofeishu.audiostream.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 服务器卡片。点击连接，星标收藏。
 * 使用 server.protocol 而非硬编码 websocket。
 */
@Composable
fun ServerCard(
    server: ServerInfo,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        insideMargin = DpSize(16.dp, 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.name, fontWeight = FontWeight.Bold)
                Text(
                    text = "${server.display} · ${server.protocol.displayName}",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (server.saved) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "收藏",
                    tint = if (server.saved) MiuixTheme.colorScheme.primary
                           else MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    }
}
