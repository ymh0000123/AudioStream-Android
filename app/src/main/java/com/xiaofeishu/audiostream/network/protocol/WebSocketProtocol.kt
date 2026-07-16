package com.xiaofeishu.audiostream.network.protocol

import com.google.gson.JsonParser
import com.xiaofeishu.audiostream.domain.model.AudioFormat
import com.xiaofeishu.audiostream.domain.model.MediaState
import com.xiaofeishu.audiostream.network.AudioEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
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
        // IPv6 字面量在 URL 中必须加方括号（mDNS 发现可能只通告 IPv6 地址）
        val host = if (address.contains(':') && !address.startsWith("[")) "[$address]" else address
        val url = "ws://$host:$port/ws"
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
                    trySendBlocking(AudioEvent.Connected(format))
                } else {
                    val mediaState = MediaState.fromJson(text)
                    if (mediaState != null) {
                        trySendBlocking(AudioEvent.StateUpdate(mediaState))
                    } else {
                        val bitrateChange = parseBitrateChanged(text)
                        if (bitrateChange != null) {
                            trySendBlocking(AudioEvent.BitrateChanged(bitrateChange.first, bitrateChange.second))
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                binaryFrames += 1
                // callbackFlow 中 launch { send(...) } 会在通道满后堆积挂起协程，绕过容量上限。
                // 阻塞交付确保协议层不再形成隐藏队列；音频丢帧策略由播放侧的独立队列处理。
                trySendBlocking(AudioEvent.AudioData(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("AudioStreamWS", "onClosing code=$code reason=$reason [文本帧=$textFrames 二进制帧=$binaryFrames]")
                webSocket.close(1000, null)
                _isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("AudioStreamWS", "onClosed code=$code reason=$reason [文本帧=$textFrames 二进制帧=$binaryFrames]")
                _isConnected = false
                trySendBlocking(AudioEvent.Disconnected(reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.w("AudioStreamWS", "onFailure [文本帧=$textFrames 二进制帧=$binaryFrames]", t)
                _isConnected = false
                trySendBlocking(AudioEvent.Error(t))
                close()
            }
        }

        webSocket = client.newWebSocket(request, listener)

        val handshakeJob = launch {
            delay(HANDSHAKE_TIMEOUT_MS)
            if (!handshakeCompleted.get()) {
                val msg = if (!_isConnected) "连接超时" else "握手超时：服务端未发送格式信息"
                send(AudioEvent.Error(SocketTimeoutException(msg)))
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
    }.buffer(capacity = Channel.RENDEZVOUS, onBufferOverflow = BufferOverflow.SUSPEND)

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
    }
}
