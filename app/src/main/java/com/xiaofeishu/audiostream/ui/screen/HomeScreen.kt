package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xiaofeishu.audiostream.domain.model.Protocol
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.ui.component.AppTopBar
import com.xiaofeishu.audiostream.ui.component.ServerCard
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil.Companion.dismissDialog

@Composable
fun HomeScreen(
    onConnect: (ServerInfo) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // 自动搜索跟随页面可见性：进入/回前台开始，离开/退后台停止（释放组播锁省电）
    LifecycleResumeEffect(Unit) {
        viewModel.startScan()
        onPauseOrDispose { viewModel.stopScan() }
    }

    val showDialog = remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("19730") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AudioStream",
                actions = {
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "重新扫描",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            SmallTitle(
                text = when {
                    servers.isNotEmpty() -> "发现的服务器 (${servers.size})"
                    isScanning -> "正在搜索服务器…"
                    else -> "未发现服务器"
                }
            )

            if (servers.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(servers.size, key = { servers[it].key }) { index ->
                        val server = servers[index]
                        ServerCard(
                            server = server,
                            onClick = { onConnect(server) },
                            onToggleSaved = { viewModel.toggleSaved(server) }
                        )
                    }
                }
            } else {
                Text(
                    text = "请确保服务端已启动，且手机与电脑在同一网络",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                Button(
                    text = "重新扫描",
                    onClick = { viewModel.startScan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                text = "手动连接",
                onClick = { showDialog.value = true },
                submit = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }

    SuperDialog(
        title = "连接服务器",
        show = showDialog,
        onDismissRequest = { dismissDialog(showDialog) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = address,
                onValueChange = { address = it },
                label = "服务器地址",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = port,
                onValueChange = { port = it },
                label = "端口",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "取消",
                    onClick = { dismissDialog(showDialog) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    text = "连接",
                    submit = true,
                    onClick = {
                        val portInt = port.toIntOrNull() ?: 19730
                        onConnect(ServerInfo(address, address, portInt, Protocol.WEBSOCKET))
                        dismissDialog(showDialog)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
