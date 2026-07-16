package com.xiaofeishu.audiostream.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import com.xiaofeishu.audiostream.di.AppScope
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.repository.DiscoveryRepository
import com.xiaofeishu.audiostream.network.discovery.DiscoveryEvent
import com.xiaofeishu.audiostream.network.discovery.MdnsDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发现仓库实现。封装 [MdnsDiscovery] + MulticastLock + 网络切换感知。
 *
 * - [startScan] 重复调用会重置（清空结果、重启 mDNS 发现），与接口语义一致；
 *   发现启动失败（NSD 偶发内部错误）按 1s/2s/4s 退避重试，用尽后收尾复位。
 * - 服务下线（Lost 事件）实时从列表移除，避免用户点到已停机的服务器。
 * - 监听默认网络：扫描中切换网络（或打开 App 时 Wi-Fi 迟到）自动重扫，
 *   断网时清空列表——旧网络的结果必然不可达。
 * - MulticastLock 在扫描期间持有（部分网络/路由器下不获取会收不到 mDNS 包），
 *   扫描结束释放。锁由本仓库管理，UI 层无需关心。
 */
@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mdnsDiscovery: MdnsDiscovery,
    @AppScope private val appScope: CoroutineScope
) : DiscoveryRepository {

    private val _servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    override val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // key = mDNS 服务实例名（网络内唯一），Lost 事件按名移除
    private val found = LinkedHashMap<String, ServerInfo>()
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var currentNetwork: Network? = null

    init {
        registerNetworkCallback()
    }

    @Synchronized
    override fun startScan() {
        scanJob?.cancel()
        synchronized(found) { found.clear() }
        _servers.value = emptyList()
        acquireMulticastLock()
        _isScanning.value = true
        scanJob = appScope.launch {
            val self = coroutineContext.job
            var attempt = 0
            while (true) {
                try {
                    mdnsDiscovery.discover().collect { event ->
                        attempt = 0  // 有事件说明发现已正常运转，重置重试计数
                        onEvent(event)
                    }
                    break  // 发现流正常完成（实际不会发生，防御性退出）
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    if (attempt > MAX_START_RETRY) break
                    delay(START_RETRY_BASE_MS shl (attempt - 1))
                }
            }
            finishScan(self)  // 走到这里 = 重试用尽或流终止：收尾复位
        }
    }

    @Synchronized
    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        releaseMulticastLock()
        _isScanning.value = false
    }

    /** 扫描 job 自然结束时收尾；若已被新一轮 startScan 接管则不动新状态。 */
    @Synchronized
    private fun finishScan(job: Job) {
        if (scanJob !== job) return
        scanJob = null
        releaseMulticastLock()
        _isScanning.value = false
    }

    private fun onEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.Found -> synchronized(found) {
                found[event.server.name] = event.server
                publish()
            }
            is DiscoveryEvent.Lost -> synchronized(found) {
                if (found.remove(event.serviceName) != null) publish()
            }
        }
    }

    private fun publish() {
        // 同名服务覆盖更新；不同名但同地址端口（如服务端改名重启、旧名尚未收到 Lost）按地址端口去重
        _servers.value = found.values.distinctBy { it.key }
    }

    /**
     * 默认网络监听（应用生命周期内常驻）。解决两类"搜不到"：
     * 打开 App 时 Wi-Fi 尚未连上，发现启动在无网络时刻，之后永远空列表；
     * 扫描中切换 Wi-Fi，旧网络的发现会话与结果残留。
     */
    private fun registerNetworkCallback() {
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val changed = currentNetwork != network
                    currentNetwork = network
                    if (changed && _isScanning.value) startScan()
                }

                override fun onLost(network: Network) {
                    if (currentNetwork == network) currentNetwork = null
                    if (_isScanning.value) {
                        synchronized(found) {
                            found.clear()
                            publish()
                        }
                    }
                }
            })
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock(MULTICAST_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    companion object {
        private const val MULTICAST_LOCK_TAG = "AudioStream::MdnsScan"
        private const val MAX_START_RETRY = 3
        private const val START_RETRY_BASE_MS = 1_000L
    }
}
