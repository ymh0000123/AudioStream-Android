package com.xiaofeishu.audiostream.data

import android.content.Context
import android.net.wifi.WifiManager
import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.repository.DiscoveryRepository
import com.xiaofeishu.audiostream.di.AppScope
import com.xiaofeishu.audiostream.network.discovery.MdnsDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发现仓库实现。封装 [MdnsDiscovery] + MulticastLock。
 *
 * MulticastLock 在扫描期间持有（部分网络/路由器下不获取会收不到 mDNS 包），扫描结束释放。
 * 锁由本仓库管理，UI 层无需关心。
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

    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _servers.value = emptyList()
        acquireMulticastLock()
        scanJob?.cancel()
        scanJob = appScope.launch {
            val seen = LinkedHashMap<String, ServerInfo>()  // key = address:port，去重
            mdnsDiscovery.discover().collectLatest { server ->
                val key = "${server.address}:${server.port}"
                seen[key] = server
                _servers.value = seen.values.toList()
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        mdnsDiscovery.stopDiscovery()
        releaseMulticastLock()
        _isScanning.value = false
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
    }
}
