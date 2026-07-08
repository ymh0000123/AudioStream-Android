package com.xiaofeishu.audiostream.domain.usecase

import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import javax.inject.Inject

/**
 * 用户主动断开连接（会阻止自动重连）。
 */
class DisconnectStreamUseCase @Inject constructor(
    private val streamRepository: StreamRepository
) {
    operator fun invoke() = streamRepository.disconnect()
}
