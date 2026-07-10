package com.xiaofeishu.audiostream.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * mDNS 服务发现封装。发现 _audiostream._tcp. 服务并解析为 [ServerInfo]。
 *
 * 版本适配：API 29+ 用 [NsdManager.registerServiceInfoCallback]（可多次回调、需 unregister）；
 * API < 29 回退到 [NsdManager.resolveService]（一次性回调）。旧 API 没有 ServiceInfoCallback 类，
 * 直接引用会 NoClassDefFoundError，故把对 ServiceInfoCallback 的所有引用隔离进独立方法
 * [registerServiceInfoCallbackQ] / [unregisterAllQ]——它们仅在 SDK_INT >= Q 时被调用，
 * Android 9 的 ART 永不 verify 这些方法、永不解析 ServiceInfoCallback。
 * 多播锁（MulticastLock）由上层 DiscoveryRepository 管理。
 */
class MdnsDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    // 仅 API 29+ 使用；用 Any 持有避免本类字段类型直接引用 ServiceInfoCallback
    private val serviceInfoCallbacks = mutableMapOf<NsdServiceInfo, Any>()

    private fun emit(info: NsdServiceInfo, trySend: (ServerInfo) -> Unit) {
        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            info.host?.hostAddress
        }
        if (!host.isNullOrEmpty()) {
            trySend(ServerInfo(name = info.serviceName, address = host, port = info.port))
        }
    }

    fun discover(serviceType: String = DEFAULT_SERVICE_TYPE): Flow<ServerInfo> = callbackFlow {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    registerServiceInfoCallbackQ(serviceInfo) { trySend(it) }
                } else {
                    // API < 29：旧 resolveService（一次性）。同一服务多次发现会重复 resolve，
                    // 忽略重复 resolve 的异常即可，不影响可用性。
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) = emit(serviceInfo) { trySend(it) }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            close()
        }

        awaitClose {
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            unregisterAllQ()
        }
    }

    /** 仅 API 29+ 调用。隔离对 ServiceInfoCallback 的引用，避免 Android 9 类解析失败。 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun registerServiceInfoCallbackQ(serviceInfo: NsdServiceInfo, trySend: (ServerInfo) -> Unit) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = emit(serviceInfo, trySend)
            override fun onServiceLost() {}
            override fun onServiceInfoCallbackUnregistered() {}
        }
        synchronized(serviceInfoCallbacks) {
            serviceInfoCallbacks[serviceInfo] = callback
        }
        nsdManager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
    }

    /** 仅 API 29+ 调用。 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun unregisterAllQ() {
        synchronized(serviceInfoCallbacks) {
            serviceInfoCallbacks.values.forEach { cb ->
                try { nsdManager.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) } catch (_: Exception) {}
            }
            serviceInfoCallbacks.clear()
        }
    }

    companion object {
        const val DEFAULT_SERVICE_TYPE = "_audiostream._tcp."
    }
}
