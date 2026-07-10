package com.xiaofeishu.audiostream.network.protocol

import com.google.gson.JsonParser
import com.xiaofeishu.audiostream.domain.model.AudioFormat
import com.xiaofeishu.audiostream.domain.model.MediaState
import com.xiaofeishu.audiostream.network.AudioEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketProtocol(
    private val client: OkHttpClient
) : AudioProtocol {

    private var webSocket: WebSocket? = null
    private var _isConnected = false

    override val isConnected: Boolean get() = _isConnected

    override suspend fun connect(address: String, port: Int, initialBitrate: Int?): Flow<AudioEvent> = callbackFlow {
        val url = "ws://$address:$port/ws"
        val request = Request.Builder().url(url).build()
        val handshakeCompleted = AtomicBoolean(false)
        // 帧计数：区分“reader 5 分钟没收到任何帧（socket 实际不通）”与“收到了文本/控制帧但无二进制音频帧（服务端没推音频流）”
        var textFrames = 0
        var binaryFrames = 0

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected = true
                initialBitrate
                    ?.takeIf { it > 0 }
                    ?.let { webSocket.send(bitrateCommand(it)) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                textFrames += 1
                val format = AudioFormat.fromJson(text)
                if (format != null) {
                    handshakeCompleted.set(true)
                    // 握手事件走 trySend：必须立即入队，不能因下游慢而挂起，
                    // 否则握手超时检测与后续初始化时序会被背压拖延。
                    trySend(AudioEvent.Connected(format))
                } else {
                    val mediaState = MediaState.fromJson(text)
                    if (mediaState != null) {
                        launch { send(AudioEvent.StateUpdate(mediaState)) }
                    } else {
                        val bitrateChange = parseBitrateChanged(text)
                        if (bitrateChange != null) {
                            launch { send(AudioEvent.BitrateChanged(bitrateChange.first, bitrateChange.second)) }
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                binaryFrames += 1
                // 音频数据用挂起 send：下游（AudioTrack.write）消费不过来时挂起回调线程，
                // 把背压传导回网络读取，而不是 DROP_OLDEST 丢帧——长时间播放的卡顿/杂音
                // 根因正是丢帧后码率塌陷+缓冲累积。OkHttp 单连接单回调线程被挂起=停读 socket，
                // 这是期望的背压，安全。
                launch { send(AudioEvent.AudioData(bytes.toByteArray())) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("AudioStreamWS", "onClosing code=$code reason=$reason [文本帧=$textFrames 二进制帧=$binaryFrames]")
                webSocket.close(1000, null)
                _isConnected = false
                trySend(AudioEvent.Disconnected(reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("AudioStreamWS", "onClosed code=$code reason=$reason [文本帧=$textFrames 二进制帧=$binaryFrames]")
                _isConnected = false
                trySend(AudioEvent.Disconnected(reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.w("AudioStreamWS", "onFailure [文本帧=$textFrames 二进制帧=$binaryFrames]", t)
                _isConnected = false
                trySend(AudioEvent.Error(t))
                close()
            }
        }

        webSocket = client.newWebSocket(request, listener)

        val handshakeJob = launch {
            delay(HANDSHAKE_TIMEOUT_MS)
            if (!handshakeCompleted.get()) {
                val msg = if (!_isConnected) "连接超时" else "握手超时：服务端未发送格式信息"
                trySend(AudioEvent.Error(SocketTimeoutException(msg)))
                webSocket?.close(1000, "Handshake timeout")
                close()
            }
        }

        awaitClose {
            handshakeJob.cancel()
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            _isConnected = false
        }
    }.buffer(capacity = FLOW_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)

    override suspend fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
    }

    override fun send(text: String) {
        webSocket?.send(text)
    }

    override fun setBitrate(bitrate: Int) {
        send(bitrateCommand(bitrate))
    }

    private fun bitrateCommand(bitrate: Int): String =
        """{"type":"command","action":"set_bitrate","bitrate":$bitrate}"""

    private fun parseBitrateChanged(json: String): Pair<Int, AudioFormat>? = try {
        val obj = JsonParser.parseString(json).asJsonObject
        if (obj.get("type")?.asString != "bitrate_changed") null
        else {
            val bitrate = obj.get("bitrate").asInt
            val format = AudioFormat(
                sampleRate = obj.get("sample_rate").asInt,
                channels = obj.get("channels").asInt,
                bitsPerSample = obj.get("bits_per_sample").asInt
            )
            bitrate to format
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        /**
         * Flow 内部 Channel 容量：仅用于平滑短时调度抖动（~180ms）。
         * 下游 AudioTrack.write 消费不过来时用 SUSPEND 挂起 OkHttp 回调线程做背压，绝不丢帧。
         * 旧值 64 在恢复后会产生 ~1.5s 积压，8 帧足够平滑抖动且积压可忽略。
         */
        private const val FLOW_BUFFER_CAPACITY = 8
    }
}
