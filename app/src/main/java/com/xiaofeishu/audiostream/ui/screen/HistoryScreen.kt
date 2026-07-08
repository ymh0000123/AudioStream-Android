package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.domain.model.ConnectionRecord
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (savedServers.isNotEmpty()) {
            item { SectionHeader("收藏的服务器") }
            items(savedServers, key = { it.key }) { server ->
                HistoryCard(
                    title = server.name.ifBlank { server.display },
                    subtitle = "${server.display} · ${server.protocol.displayName}",
                    trailing = "已收藏",
                    onClick = { onConnect(viewModel.savedToServer(server)) },
                    actionIcon = Icons.Filled.Delete,
                    actionContentDescription = "取消收藏",
                    onAction = { viewModel.removeSaved(server) }
                )
            }
        }

        item { SectionHeader("连接历史") }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "暂无连接历史",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(history, key = { "${it.address}:${it.port}:${it.lastConnected}" }) { record ->
                HistoryCard(
                    title = record.display,
                    subtitle = "${record.protocol.displayName} · 连接 ${record.connectCount} 次",
                    trailing = dateFmt.format(Date(record.lastConnected)),
                    onClick = { onConnect(viewModel.recordToServer(record)) },
                    actionIcon = Icons.Filled.PlayArrow,
                    actionContentDescription = "连接",
                    onAction = { onConnect(viewModel.recordToServer(record)) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionContentDescription: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = actionContentDescription)
            }
        }
    }
}
