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
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.LargeTitle
import com.xiaofeishu.audiostream.ui.component.contextClick
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
import com.xiaofeishu.audiostream.ui.component.ServerCard
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    onConnect: (ServerInfo) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current

    // 自动搜索跟随页面可见性：进入/回前台开始，离开/退后台停止（释放组播锁省电）
    LifecycleResumeEffect(Unit) {
        viewModel.startScan()
        onPauseOrDispose { viewModel.stopScan() }
    }

    val showDialog = remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("19730") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LargeTitle(text = "主页")
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
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                onConnect(server)
                            },
                            onToggleSaved = {
                                haptic.contextClick(hapticEnabled)
                                viewModel.toggleSaved(server)
                            }
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
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        viewModel.startScan()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text("重新扫描")
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    haptic.contextClick(hapticEnabled)
                    showDialog.value = true
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text("手动连接")
            }
        }
    }

    OverlayDialog(
        show = showDialog.value,
        title = "连接服务器",
        onDismissRequest = { showDialog.value = false }
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
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        showDialog.value = false
                    },
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        val portInt = port.toIntOrNull() ?: 19730
                        onConnect(ServerInfo(address, address, portInt, Protocol.WEBSOCKET))
                        showDialog.value = false
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("连接")
                }
            }
        }
    }
}
