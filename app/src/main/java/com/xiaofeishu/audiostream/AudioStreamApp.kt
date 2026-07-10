package com.xiaofeishu.audiostream

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.xiaofeishu.audiostream.crash.CrashHandler
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。Hilt 生成依赖图。
 * 安装全局崩溃处理器：捕获未处理异常后展示自定义崩溃页（[com.xiaofeishu.audiostream.crash.CrashActivity]）。
 *
 * 另注册全局网络回调：锁屏长播断连诊断用。任何网络可用/丢失/切换（WiFi↔移动数据）都会打日志，
 * 与 onFailure 时间戳对照即可判断断连是否由“客户端侧网络瞬断”引起。
 */
@HiltAndroidApp
class AudioStreamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        registerNetworkMonitor()
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        // DEFAULT_NETWORK 监听系统当前生效的默认网络：锁屏后默认网络切换/丢失会回调
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                android.util.Log.i("AudioStreamNet", "onAvailable $network")
            }
            override fun onLost(network: Network) {
                android.util.Log.w("AudioStreamNet", "onLost $network")
            }
            override fun onUnavailable() {
                android.util.Log.w("AudioStreamNet", "onUnavailable")
            }
        })
    }
}
