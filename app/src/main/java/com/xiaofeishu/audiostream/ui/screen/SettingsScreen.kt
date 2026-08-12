package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.data.update.UpdateInfo
import com.xiaofeishu.audiostream.ui.component.AppTopBar
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import com.xiaofeishu.audiostream.viewmodel.UpdateUiState
import com.xiaofeishu.audiostream.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil.Companion.dismissDialog

/** 播放延迟固定档位（ms 阈值）：0=关闭跳帧。 */
private val LATENCY_MODES = listOf(0, 100, 150, 200)

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onNavigateToAbout: () -> Unit = {}
) {
    val savedServers by viewModel.savedServers.collectAsState()
    val showClearConfirm = remember { mutableStateOf(false) }
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
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnored = checkBatteryIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hideSinkLatencyHint by viewModel.hideSinkLatencyHint.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "设置"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
        ) {
            SmallTitle(text = "后台保活")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                SuperArrow(
                    title = context.getString(R.string.battery_optimization),
                    summary = context.getString(R.string.battery_optimization_desc),
                    rightText = if (batteryIgnored) {
                        context.getString(R.string.battery_optimization_granted)
                    } else {
                        context.getString(R.string.battery_optimization_request)
                    },
                    onClick = if (batteryIgnored) {
                        null
                    } else {
                        {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "播放")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = DpSize(16.dp, 16.dp)
            ) {
                Text(
                    text = context.getString(R.string.latency_mode),
                    fontSize = MiuixTheme.textStyles.main.fontSize
                )
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
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                // 蓝牙链路延迟警告提示：忽略后可在此恢复
                SuperSwitch(
                    title = "链路延迟警告提示",
                    summary = if (hideSinkLatencyHint) {
                        "已忽略：蓝牙输出时不再显示链路延迟警告"
                    } else {
                        "蓝牙输出时在播放页显示链路延迟警告"
                    },
                    checked = !hideSinkLatencyHint,
                    onCheckedChange = { show -> viewModel.setHideSinkLatencyHint(!show) }
                )
                SuperArrow(
                    title = "收藏的服务器",
                    summary = "${savedServers.size} 个",
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "关于与更新")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                SuperArrow(
                    title = context.getString(R.string.check_for_updates),
                    summary = if (updateState == UpdateUiState.Checking) {
                        context.getString(R.string.update_checking)
                    } else {
                        context.getString(R.string.current_version, BuildConfig.VERSION_NAME)
                    },
                    rightText = if (updateState == UpdateUiState.Checking) {
                        null
                    } else {
                        context.getString(R.string.check_now)
                    },
                    enabled = updateState != UpdateUiState.Checking,
                    onClick = updateViewModel::checkForUpdates
                )
                SuperArrow(
                    title = "关于",
                    summary = "应用版本与开源信息",
                    onClick = onNavigateToAbout
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 清除历史（真正调用 clearHistory，修复 saveVolume(80) bug）
            Button(
                text = "清除连接历史",
                onClick = { showClearConfirm.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    SuperDialog(
        title = "清除连接历史",
        summary = "将删除全部连接历史记录，收藏的服务器不受影响。是否继续？",
        show = showClearConfirm,
        onDismissRequest = { dismissDialog(showClearConfirm) }
    ) {
        DialogButtonRow(
            cancelText = "取消",
            confirmText = "清除",
            onCancel = { dismissDialog(showClearConfirm) },
            onConfirm = {
                viewModel.clearHistory()
                dismissDialog(showClearConfirm)
            }
        )
    }

    UpdateDialogs(
        context = context,
        updateState = updateState,
        downloadOptions = downloadOptions,
        onDownloadOptionsChange = { downloadOptions = it },
        updateViewModel = updateViewModel
    )
}

/**
 * 更新相关弹窗。
 *
 * 关键约束：Miuix 的对话框由 [top.yukonga.miuix.kmp.utils.MiuixPopupUtil] 的
 * **进程级单例** 承载（isDialogShowing / dialogContext），且 showDialog 在已显示时会直接早退。
 * 因此这里必须满足两点，否则会出现"弹窗关不掉"和"叠两层弹窗"：
 *
 * 1. 全程只使用**一个** SuperDialog 与**一个** show 状态，内容按 [UpdateUiState] 分支渲染，
 *    绝不能为每种状态各建一个 SuperDialog（那样多个 show 会在同一帧争抢单例）。
 * 2. 任何关闭路径都必须走 [dismissDialog] 把 show 置为 false，
 *    只调 ViewModel 的 dismissResult() 不会复位单例，弹窗会永久卡住。
 */
@Composable
private fun UpdateDialogs(
    context: Context,
    updateState: UpdateUiState,
    downloadOptions: UpdateInfo?,
    onDownloadOptionsChange: (UpdateInfo?) -> Unit,
    updateViewModel: UpdateViewModel
) {
    val show = remember { mutableStateOf(false) }

    // 有内容可展示时才打开：下载方式选择优先于版本详情
    val hasContent = downloadOptions != null ||
        updateState is UpdateUiState.Available ||
        updateState is UpdateUiState.UpToDate ||
        updateState is UpdateUiState.Error
    LaunchedEffect(hasContent) {
        show.value = hasContent
    }

    // 统一关闭入口：先复位 Miuix 单例，再清业务状态
    val dismissAll: () -> Unit = {
        dismissDialog(show)
        onDownloadOptionsChange(null)
        updateViewModel.dismissResult()
    }

    if (!hasContent) return

    val selectedDownload = downloadOptions
    val title = when {
        selectedDownload != null -> context.getString(R.string.download_method_title)
        updateState is UpdateUiState.Available ->
            context.getString(R.string.update_available_title)
        updateState is UpdateUiState.UpToDate ->
            context.getString(R.string.already_latest_title)
        else -> context.getString(R.string.update_check_failed_title)
    }

    SuperDialog(
        title = title,
        show = show,
        onDismissRequest = dismissAll
    ) {
        when {
            selectedDownload != null -> DownloadMethodContent(
                context = context,
                info = selectedDownload,
                onPicked = { url ->
                    openUrl(context, url)
                    dismissAll()
                },
                onCancel = dismissAll
            )

            updateState is UpdateUiState.Available -> AvailableContent(
                context = context,
                info = updateState.info,
                onCancel = dismissAll,
                onConfirm = {
                    if (updateState.info.mirrorDownloadUrl == null) {
                        openUrl(context, updateState.info.downloadUrl)
                        dismissAll()
                    } else {
                        // 切到下载方式选择：复用同一个弹窗，不新建 SuperDialog
                        onDownloadOptionsChange(updateState.info)
                    }
                }
            )

            updateState is UpdateUiState.UpToDate -> Column {
                Text(
                    text = context.getString(
                        R.string.already_latest_desc,
                        BuildConfig.VERSION_NAME
                    ),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    text = context.getString(R.string.close),
                    submit = true,
                    onClick = dismissAll,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            updateState is UpdateUiState.Error -> Column {
                Text(
                    text = updateState.message,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    cancelText = context.getString(R.string.close),
                    confirmText = context.getString(R.string.retry),
                    onCancel = dismissAll,
                    onConfirm = {
                        dismissDialog(show)
                        updateViewModel.checkForUpdates()
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadMethodContent(
    context: Context,
    info: UpdateInfo,
    onPicked: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = context.getString(R.string.download_method_desc),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        info.mirrorDownloadUrl?.let { mirrorUrl ->
            Button(
                text = context.getString(R.string.download_via_mirror),
                submit = true,
                onClick = { onPicked(mirrorUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            text = context.getString(R.string.download_via_github),
            onClick = { onPicked(info.downloadUrl) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            text = context.getString(R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AvailableContent(
    context: Context,
    info: UpdateInfo,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(context.getString(R.string.latest_version, info.versionName))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.update_notes),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = info.releaseNotes.ifBlank {
                    context.getString(R.string.no_release_notes)
                },
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DialogButtonRow(
            cancelText = context.getString(R.string.cancel),
            confirmText = context.getString(R.string.download_update),
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

/** SuperDialog 没有内置按钮区，统一封装左取消右确认的按钮行。 */
@Composable
private fun DialogButtonRow(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            text = cancelText,
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        Button(
            text = confirmText,
            submit = true,
            onClick = onConfirm,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
