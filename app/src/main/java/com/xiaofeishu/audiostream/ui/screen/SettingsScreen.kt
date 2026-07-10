package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel

/** 播放延迟固定档位（ms 阈值）：0=关闭跳帧。 */
private val LATENCY_MODES = listOf(0, 100, 150, 200)

@Composable
fun SettingsScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val savedServers by viewModel.savedServers.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val latencyMode by viewModel.latencyMode.collectAsState()

    // 电池优化豁免状态
    var batteryIgnored by remember { mutableStateOf(checkBatteryIgnored(context)) }
    // 页面重新可见时刷新状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryIgnored = checkBatteryIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 后台保活 - 电池优化豁免
        ListItem(
            headlineContent = { Text(context.getString(R.string.battery_optimization)) },
            supportingContent = { Text(context.getString(R.string.battery_optimization_desc)) },
            trailingContent = {
                if (batteryIgnored) {
                    Text(
                        text = context.getString(R.string.battery_optimization_granted),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Button(onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }) {
                        Text(context.getString(R.string.battery_optimization_request))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 播放延迟模式
        ListItem(
            headlineContent = { Text(context.getString(R.string.latency_mode)) },
            supportingContent = {
                SteppedSlider(
                    values = LATENCY_MODES,
                    currentValue = latencyMode,
                    onValueCommitted = viewModel::saveLatencyMode,
                    valueLabel = { mode ->
                        when (mode) {
                            100 -> context.getString(R.string.latency_mode_low)
                            150 -> context.getString(R.string.latency_mode_balanced)
                            200 -> context.getString(R.string.latency_mode_stable)
                            else -> context.getString(R.string.latency_mode_disabled)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 收藏服务器数量
        ListItem(
            headlineContent = { Text("收藏的服务器") },
            supportingContent = { Text("${savedServers.size} 个") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 清除历史（真正调用 clearHistory，修复 saveVolume(80) bug）
        Button(
            onClick = { showClearConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) { Text("清除连接历史") }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除连接历史") },
            text = { Text("将删除全部连接历史记录，收藏的服务器不受影响。是否继续？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearHistory()
                    showClearConfirm = false
                }) { Text("清除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
