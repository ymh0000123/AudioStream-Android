package com.xiaofeishu.audiostream.domain.repository

import com.xiaofeishu.audiostream.domain.model.ServerInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * mDNS 服务发现仓库。封装 NsdManager + MulticastLock。
 */
interface DiscoveryRepository {

    /** 当前发现的服务器列表。 */
    val servers: StateFlow<List<ServerInfo>>

    /** 是否正在扫描。 */
    val isScanning: StateFlow<Boolean>

    /** 开始扫描（重复调用会重置）。 */
    fun startScan()

    /** 停止扫描。 */
    fun stopScan()
}
