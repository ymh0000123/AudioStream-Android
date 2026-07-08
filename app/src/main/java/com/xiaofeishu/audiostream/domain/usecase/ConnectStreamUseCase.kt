package com.xiaofeishu.audiostream.domain.usecase

import com.xiaofeishu.audiostream.domain.model.ServerInfo
import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import javax.inject.Inject

/**
 * 连接到流服务器。
 */
class ConnectStreamUseCase @Inject constructor(
    private val streamRepository: StreamRepository
) {
    operator fun invoke(server: ServerInfo) = streamRepository.connect(server)
}
