package com.xiaofeishu.audiostream.domain.usecase

import com.xiaofeishu.audiostream.domain.repository.DiscoveryRepository
import javax.inject.Inject

/**
 * 启动 mDNS 服务扫描。
 */
class ScanServersUseCase @Inject constructor(
    private val discoveryRepository: DiscoveryRepository
) {
    operator fun invoke() = discoveryRepository.startScan()
}
