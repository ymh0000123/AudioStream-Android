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
 * 修复：用 Map 跟踪每个已注册的 ServiceInfoCallback，stopDiscovery 时全部 unregister，
 * 避免旧版用单一字段覆盖导致早期回调泄漏。
 * 多播锁（MulticastLock）由上层 DiscoveryRepository 管理。
 */
class MdnsDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val serviceInfoCallbacks = mutableMapOf<NsdServiceInfo, NsdManager.ServiceInfoCallback>()

    fun discover(serviceType: String = DEFAULT_SERVICE_TYPE): Flow<ServerInfo> = callbackFlow {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                        } else {
                            @Suppress("DEPRECATION")
                            serviceInfo.host?.hostAddress
                        }
                        if (!host.isNullOrEmpty()) {
                            trySend(
                                ServerInfo(
                                    name = serviceInfo.serviceName,
                                    address = host,
                                    port = serviceInfo.port
                                )
                            )
                        }
                    }

                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                synchronized(serviceInfoCallbacks) {
                    serviceInfoCallbacks[serviceInfo] = callback
                }
                nsdManager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
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
        synchronized(serviceInfoCallbacks) {
            serviceInfoCallbacks.values.forEach { cb ->
                try { nsdManager.unregisterServiceInfoCallback(cb) } catch (_: Exception) {}
            }
            serviceInfoCallbacks.clear()
        }
    }

    companion object {
        const val DEFAULT_SERVICE_TYPE = "_audiostream._tcp."
    }
}
