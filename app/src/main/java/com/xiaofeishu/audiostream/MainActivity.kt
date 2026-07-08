package com.xiaofeishu.audiostream

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.service.AudioStreamService
import com.xiaofeishu.audiostream.ui.screen.HistoryScreen
import com.xiaofeishu.audiostream.ui.screen.HomeScreen
import com.xiaofeishu.audiostream.ui.screen.PlayerScreen
import com.xiaofeishu.audiostream.ui.screen.SettingsScreen
import com.xiaofeishu.audiostream.ui.theme.AudioStreamTheme
import com.xiaofeishu.audiostream.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
            AudioStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AudioStreamApp()
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
        val snackbarHostState = remember { SnackbarHostState() }
        val playerViewModel: PlayerViewModel = hiltViewModel()

        // 错误上屏：监听播放状态中的 error
        val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(playbackState.error, playbackState.connectionState) {
            val err = playbackState.error
            if (err != null && (playbackState.connectionState == ConnectionState.ERROR)) {
                snackbarHostState.showSnackbar(err)
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    BottomNavItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
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
                composable(Route.SETTINGS.path) { SettingsScreen() }
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
    HOME("home"), PLAYER("player"), HISTORY("history"), SETTINGS("settings");
    override fun toString() = path
}

private enum class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "主页", Icons.Filled.Home),
    PLAYER("player", "播放", Icons.Filled.PlayArrow),
    HISTORY("history", "历史", Icons.Filled.History),
    SETTINGS("settings", "设置", Icons.Filled.Settings);
}
