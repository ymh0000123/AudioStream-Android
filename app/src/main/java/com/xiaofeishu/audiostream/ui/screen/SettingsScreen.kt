package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.net.toUri
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.data.update.UpdateInfo
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import com.xiaofeishu.audiostream.viewmodel.UpdateUiState
import com.xiaofeishu.audiostream.viewmodel.UpdateViewModel

/** 播放延迟固定档位（ms 阈值）：0=关闭跳帧。 */
private val LATENCY_MODES = listOf(0, 100, 150, 200)

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val savedServers by viewModel.savedServers.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val latencyMode by viewModel.latencyMode.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    var downloadOptions by remember { mutableStateOf<UpdateInfo?>(null) }

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
            .verticalScroll(rememberScrollState())
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
                            "package:${context.packageName}".toUri()
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

        // 蓝牙链路延迟警告提示：忽略后可在此恢复
        val hideSinkLatencyHint by viewModel.hideSinkLatencyHint.collectAsState()
        ListItem(
            headlineContent = { Text("链路延迟警告提示") },
            supportingContent = {
                Text(
                    if (hideSinkLatencyHint) "已忽略：蓝牙输出时不再显示链路延迟警告"
                    else "蓝牙输出时在播放页显示链路延迟警告"
                )
            },
            trailingContent = {
                Switch(
                    checked = !hideSinkLatencyHint,
                    onCheckedChange = { show -> viewModel.setHideSinkLatencyHint(!show) }
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

        ListItem(
            headlineContent = { Text(context.getString(R.string.check_for_updates)) },
            supportingContent = {
                Text(
                    if (updateState == UpdateUiState.Checking) {
                        context.getString(R.string.update_checking)
                    } else {
                        context.getString(R.string.current_version, BuildConfig.VERSION_NAME)
                    }
                )
            },
            trailingContent = {
                OutlinedButton(
                    onClick = updateViewModel::checkForUpdates,
                    enabled = updateState != UpdateUiState.Checking,
                    modifier = Modifier.widthIn(min = 104.dp)
                ) {
                    if (updateState == UpdateUiState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(context.getString(R.string.check_now))
                    }
                }
            }
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

    val selectedDownload = downloadOptions
    if (selectedDownload != null) {
        AlertDialog(
            onDismissRequest = { downloadOptions = null },
            title = { Text(context.getString(R.string.download_method_title)) },
            text = {
                Column {
                    Text(context.getString(R.string.download_method_desc))
                    Spacer(modifier = Modifier.height(16.dp))
                    selectedDownload.mirrorDownloadUrl?.let { mirrorUrl ->
                        Button(
                            onClick = {
                                openUrl(context, mirrorUrl)
                                downloadOptions = null
                                updateViewModel.dismissResult()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.download_via_mirror))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            openUrl(context, selectedDownload.downloadUrl)
                            downloadOptions = null
                            updateViewModel.dismissResult()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.download_via_github))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { downloadOptions = null }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    } else when (val state = updateState) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = updateViewModel::dismissResult,
            title = { Text(context.getString(R.string.update_available_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(context.getString(R.string.latest_version, state.info.versionName))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = context.getString(R.string.update_notes),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.info.releaseNotes.ifBlank {
                            context.getString(R.string.no_release_notes)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.info.mirrorDownloadUrl == null) {
                        openUrl(context, state.info.downloadUrl)
                        updateViewModel.dismissResult()
                    } else {
                        downloadOptions = state.info
                    }
                }) { Text(context.getString(R.string.download_update)) }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismissResult) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )

        is UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = updateViewModel::dismissResult,
            title = { Text(context.getString(R.string.already_latest_title)) },
            text = {
                Text(context.getString(R.string.already_latest_desc, BuildConfig.VERSION_NAME))
            },
            confirmButton = {
                TextButton(onClick = updateViewModel::dismissResult) {
                    Text(context.getString(R.string.close))
                }
            }
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = updateViewModel::dismissResult,
            title = { Text(context.getString(R.string.update_check_failed_title)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = updateViewModel::checkForUpdates) {
                    Text(context.getString(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismissResult) {
                    Text(context.getString(R.string.close))
                }
            }
        )

        UpdateUiState.Checking,
        UpdateUiState.Idle -> Unit
    }
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
