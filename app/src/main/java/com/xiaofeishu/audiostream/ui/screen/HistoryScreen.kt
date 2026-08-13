package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.contextClick
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史与收藏页。列出收藏服务器（可一键连接/取消收藏）和连接历史（可一键连接）。
 */
@Composable
fun HistoryScreen(
    onConnect: (ServerInfo) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val savedServers by viewModel.savedServers.collectAsState()
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current

    fun withHaptic(action: () -> Unit): () -> Unit = {
        haptic.contextClick(hapticEnabled)
        action()
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (savedServers.isNotEmpty()) {
                item { SmallTitle(text = "收藏的服务器") }
                items(savedServers.size, key = { savedServers[it].key }) { index ->
                    val server = savedServers[index]
                    HistoryCard(
                        title = server.name.ifBlank { server.display },
                        subtitle = "${server.display} · ${server.protocol.displayName}",
                        trailing = "已收藏",
                        onClick = withHaptic { onConnect(viewModel.savedToServer(server)) },
                        actionIcon = Icons.Filled.Delete,
                        actionContentDescription = "取消收藏",
                        onAction = withHaptic { viewModel.removeSaved(server) }
                    )
                }
            }

            item { SmallTitle(text = "连接历史") }

            if (history.isEmpty()) {
                item {
                    Text(
                        text = "暂无连接历史",
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(
                    history.size,
                    key = { "${history[it].address}:${history[it].port}:${history[it].lastConnected}" }
                ) { index ->
                    val record = history[index]
                    HistoryCard(
                        title = record.display,
                        subtitle = "${record.protocol.displayName} · 连接 ${record.connectCount} 次",
                        trailing = dateFmt.format(Date(record.lastConnected)),
                        onClick = withHaptic { onConnect(viewModel.recordToServer(record)) },
                        actionIcon = Icons.Filled.PlayArrow,
                        actionContentDescription = "连接",
                        onAction = withHaptic { onConnect(viewModel.recordToServer(record)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
    actionIcon: ImageVector,
    actionContentDescription: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        insideMargin = PaddingValues(16.dp, 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    trailing,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onAction) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    }
}
