package com.xiaofeishu.audiostream.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.Inet6Address
import kotlin.coroutines.resume

/** 发现事件：服务上线/更新，或按服务名下线。 */
sealed interface DiscoveryEvent {
    data class Found(val server: ServerInfo) : DiscoveryEvent
    data class Lost(val serviceName: String) : DiscoveryEvent
}

/** mDNS 发现启动失败（onStartDiscoveryFailed / discoverServices 抛异常），上层可据此退避重试。 */
class MdnsStartException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * mDNS 服务发现封装。发现 _audiostream._tcp. 服务并解析为 [DiscoveryEvent]。
 *
 * 版本适配：API 29+ 用 [NsdManager.registerServiceInfoCallback]（可多次回调、需 unregister）；
 * API < 29 回退到 [NsdManager.resolveService]（一次性回调）。旧 API 没有 ServiceInfoCallback 类，
 * 直接引用会 NoClassDefFoundError，故把对 ServiceInfoCallback 的所有引用隔离进独立方法
 * [registerServiceInfoCallbackQ] / [unregisterQ] / [unregisterAllQ]——它们仅在 SDK_INT >= Q
 * 时被调用，Android 9 的 ART 永不 verify 这些方法、永不解析 ServiceInfoCallback。
 *
 * API < 29 的 resolveService 同一时刻只允许一个在途请求（并发会 FAILURE_ALREADY_ACTIVE），
 * 因此发现的服务先进 Channel 串行 resolve，失败带间隔重试，避免同时发现多台时丢服务。
 *
 * 所有状态（listener、回调表、resolve 队列）均为每次 [discover] 调用的局部变量：
 * 旧流的取消与新流的启动互不干扰。生命周期完全由 Flow 承载（取消即停止发现并注销回调）。
 * 多播锁（MulticastLock）由上层 DiscoveryRepository 管理。
 */
class MdnsDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    fun discover(serviceType: String = DEFAULT_SERVICE_TYPE): Flow<DiscoveryEvent> = callbackFlow {
        // 仅 API 29+ 使用；serviceName -> ServiceInfoCallback，用 Any 持有避免字段类型引用该类
        val infoCallbacks = mutableMapOf<String, Any>()
        // 仅 API < 29 使用；待 resolve 的服务队列
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            launch {
                for (pending in resolveQueue) {
                    resolveLegacy(pending)?.let { resolved ->
                        toServerInfo(resolved)?.let { trySend(DiscoveryEvent.Found(it)) }
                    }
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    registerServiceInfoCallbackQ(infoCallbacks, serviceInfo) {
                        trySend(DiscoveryEvent.Found(it))
                    }
                } else {
                    resolveQueue.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // 注销该服务的跟踪回调，之后再次 found 时重新注册
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    unregisterQ(infoCallbacks, serviceInfo.serviceName)
                }
                trySend(DiscoveryEvent.Lost(serviceInfo.serviceName))
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(MdnsStartException("mDNS 发现启动失败 errorCode=$errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            close(MdnsStartException("mDNS 发现启动失败", e))
        }

        awaitClose {
            resolveQueue.close()
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                unregisterAllQ(infoCallbacks)
            }
        }
    }

    private fun toServerInfo(info: NsdServiceInfo): ServerInfo? {
        val host = hostAddressOf(info) ?: return null
        if (info.port <= 0) return null
        return ServerInfo(name = info.serviceName, address = host, port = info.port)
    }

    /**
     * 取服务地址。API 34+ 可能同时通告 IPv4/IPv6：优先 IPv4（URL 兼容性最好），
     * 其次非链路本地 IPv6；IPv6 的 hostAddress 可能带 %zone 后缀（URL 中非法），去掉。
     */
    private fun hostAddressOf(info: NsdServiceInfo): String? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val addrs = info.hostAddresses
            (addrs.firstOrNull { it is Inet4Address }
                ?: addrs.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
                ?: addrs.firstOrNull())?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            info.host?.hostAddress
        }
        return raw?.substringBefore('%')?.takeIf { it.isNotEmpty() }
    }

    /** 仅 API < 29 调用。串行 resolve 单个服务，瞬时失败重试兜底。失败返回 null。 */
    private suspend fun resolveLegacy(serviceInfo: NsdServiceInfo): NsdServiceInfo? {
        repeat(LEGACY_RESOLVE_ATTEMPTS) { attempt ->
            val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                @Suppress("DEPRECATION")
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(serviceInfo)
                        }
                    })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (resolved != null) return resolved
            if (attempt < LEGACY_RESOLVE_ATTEMPTS - 1) delay(LEGACY_RESOLVE_RETRY_DELAY_MS)
        }
        return null
    }

    /** 仅 API 29+ 调用。隔离对 ServiceInfoCallback 的引用，避免 Android 9 类解析失败。 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun registerServiceInfoCallbackQ(
        callbacks: MutableMap<String, Any>,
        serviceInfo: NsdServiceInfo,
        onFound: (ServerInfo) -> Unit
    ) {
        val name = serviceInfo.serviceName
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                // 注册失败则移除占位，下次 onServiceFound 重新注册
                synchronized(callbacks) { callbacks.remove(name) }
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                toServerInfo(serviceInfo)?.let(onFound)
            }

            override fun onServiceLost() {}
            override fun onServiceInfoCallbackUnregistered() {}
        }
        synchronized(callbacks) {
            // 已在跟踪的服务不重复注册（同一服务可能被多次 onServiceFound）
            if (callbacks.containsKey(name)) return
            callbacks[name] = callback
        }
        try {
            nsdManager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
        } catch (e: Exception) {
            synchronized(callbacks) { callbacks.remove(name) }
        }
    }

    /** 仅 API 29+ 调用。 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun unregisterQ(callbacks: MutableMap<String, Any>, serviceName: String) {
        val cb = synchronized(callbacks) { callbacks.remove(serviceName) } ?: return
        try { nsdManager.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) } catch (_: Exception) {}
    }

    /** 仅 API 29+ 调用。 */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun unregisterAllQ(callbacks: MutableMap<String, Any>) {
        val all = synchronized(callbacks) {
            callbacks.values.toList().also { callbacks.clear() }
        }
        all.forEach { cb ->
            try { nsdManager.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) } catch (_: Exception) {}
        }
    }

    companion object {
        const val DEFAULT_SERVICE_TYPE = "_audiostream._tcp."
        private const val LEGACY_RESOLVE_ATTEMPTS = 3
        private const val LEGACY_RESOLVE_RETRY_DELAY_MS = 300L
    }
}
