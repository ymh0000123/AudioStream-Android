package com.xiaofeishu.audiostream

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xiaofeishu.audiostream.ui.component.contextClick
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiaofeishu.audiostream.service.AudioStreamService
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.screen.AboutScreen
import com.xiaofeishu.audiostream.ui.screen.HistoryScreen
import com.xiaofeishu.audiostream.ui.screen.HomeScreen
import com.xiaofeishu.audiostream.ui.screen.PlayerScreen
import com.xiaofeishu.audiostream.ui.screen.SettingsScreen
import com.xiaofeishu.audiostream.ui.theme.AudioStreamTheme
import com.xiaofeishu.audiostream.viewmodel.PlayerViewModel
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var settingsRepository: SettingsRepository
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 通知权限结果忽略，缺权限时仅无通知，不影响播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        // 不在启动时绑定/启动前台服务——Android 14+ 在后台启动会抛
        // ForegroundServiceStartNotAllowedException。播放保活由用户点击连接时
        // 的 startForegroundService 驱动（见 ensureServiceRunning）。
        setContent {
            // miuix 0.9.x 的弹窗组件(OverlayDialog 等)依赖 LocalNavigationEventDispatcherOwner 做
            // 返回手势分发，此处创建根 dispatcher 并提供。注意：作为根节点 parent 必须显式传 null
            // (LocalNavigationEventDispatcherOwner.current 此时为 null 会触发库内断言)。
            val navEventOwner = rememberNavigationEventDispatcherOwner(
                enabled = true,
                parent = null,
            )
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navEventOwner,
            ) {
                val themeMode by settingsRepository.themeMode.collectAsState()
                AudioStreamTheme(themeMode = themeMode) {
                    val hapticFeedbackEnabled by settingsRepository.hapticFeedbackEnabled.collectAsState()
                    CompositionLocalProvider(
                        LocalHapticFeedbackEnabled provides hapticFeedbackEnabled
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MiuixTheme.colorScheme.background
                        ) {
                            AudioStreamApp()
                        }
                    }
                }
            }
        }
    }


    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @Composable
    private fun AudioStreamApp() {
        val navController = rememberNavController()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val haptic = LocalHapticFeedback.current
        val hapticEnabled = LocalHapticFeedbackEnabled.current

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // 关于页是设置页的二级页面，底栏仍高亮"设置"；找不到时兜底 0 避免 -1 越界。
        val selectedIndex = BottomNavItem.entries
            .indexOfFirst { it.route == currentRoute }
            .let { if (it >= 0) it else if (currentRoute == Route.ABOUT.path) BottomNavItem.SETTINGS.ordinal else 0 }

        Scaffold(
            // 顶部 inset 由各屏幕的 TopAppBar 自己吃（miuix TopAppBar 内含 statusBars padding），
            // 底部 inset 由 NavigationBar 自己吃。这里若保留默认 contentWindowInsets(statusBars)，
            // 外层无 topBar 时 Scaffold 会兜底把状态栏高度加到 body 上，与 TopAppBar 叠加成两倍，
            // 顶部就会多出一大片空白。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 关于页是二级页面，不显示底部导航栏；隐藏时 bottomBar 高度归零，
                // 关于页需自行处理 navigationBars inset（见 AboutScreen）。
                AnimatedVisibility(
                    visible = currentRoute != Route.ABOUT.path,
                    enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
                    exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically()
                ) {
                    NavigationBar {
                        BottomNavItem.entries.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = {
                                    haptic.contextClick(hapticEnabled)
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = item.icon,
                                label = item.label,
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Route.HOME.path,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(Route.HOME.path) {
                    HomeScreen(
                        onConnect = { server ->
                            playerViewModel.connect(server)
                            ensureServiceRunning()
                            navController.navigate(Route.PLAYER.path) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Route.PLAYER.path) { PlayerScreen(viewModel = playerViewModel) }
                composable(Route.HISTORY.path) {
                    HistoryScreen(
                        onConnect = { server ->
                            playerViewModel.connect(server)
                            ensureServiceRunning()
                            navController.navigate(Route.PLAYER.path) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Route.SETTINGS.path) {
                    SettingsScreen(
                        onNavigateToAbout = {
                            navController.navigate(Route.ABOUT.path)
                        }
                    )
                }
                composable(Route.ABOUT.path) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    /** 播放时独立启动前台服务保活，避免 Activity 退到后台被回收导致播放中断。 */
    private fun ensureServiceRunning() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, AudioStreamService::class.java))
            } else {
                startService(Intent(this, AudioStreamService::class.java))
            }
        }
    }
}

private enum class Route(val path: String) {
    HOME("home"), PLAYER("player"), HISTORY("history"), SETTINGS("settings"), ABOUT("about");
    override fun toString() = path
}

private enum class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "主页", Icons.Filled.Home),
    PLAYER("player", "播放", Icons.Filled.PlayArrow),
    HISTORY("history", "历史", Icons.Filled.History),
    SETTINGS("settings", "设置", Icons.Filled.Settings);
}
