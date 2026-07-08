package com.xiaofeishu.audiostream.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.xiaofeishu.audiostream.MainActivity
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.domain.model.ConnectionState
import com.xiaofeishu.audiostream.domain.model.MediaAction
import com.xiaofeishu.audiostream.domain.model.MediaState
import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioStreamService : Service() {

    companion object {
        const val CHANNEL_ID = "audio_stream_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.xiaofeishu.audiostream.STOP"
        const val ACTION_PLAY_PAUSE = "com.xiaofeishu.audiostream.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.xiaofeishu.audiostream.PREVIOUS"
        const val ACTION_NEXT = "com.xiaofeishu.audiostream.NEXT"
        private const val WAKE_LOCK_TAG = "AudioStream::PlaybackLock"
        private const val WIFI_LOCK_TAG = "AudioStream::NetworkLock"
        private const val REQUEST_CODE_PLAY_PAUSE = 10
        private const val REQUEST_CODE_PREV = 11
        private const val REQUEST_CODE_NEXT = 12
    }

    @Inject lateinit var streamRepository: StreamRepository

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamService = this@AudioStreamService
    }
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var mediaSession: MediaSessionCompat

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            requestPlay()
        }

        override fun onPause() {
            requestPause()
        }

        override fun onSkipToNext() {
            streamRepository.sendCommand(MediaAction.NEXT)
        }

        override fun onSkipToPrevious() {
            streamRepository.sendCommand(MediaAction.PREVIOUS)
        }

        override fun onSeekTo(pos: Long) {
            streamRepository.seekTo(pos)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            return handleMediaButtonIntent(mediaButtonEvent) || super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "AudioStream").apply {
            setCallback(mediaSessionCallback)
            setMediaButtonReceiver(buildMediaButtonPendingIntent())
            isActive = true
        }
        serviceScope.launch {
            streamRepository.state.collectLatest { state ->
                updateNotification(state)
                manageLocks(state.connectionState)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                streamRepository.sendCommand(MediaAction.PLAY_PAUSE)
                return START_STICKY
            }
            ACTION_PREVIOUS -> {
                streamRepository.sendCommand(MediaAction.PREVIOUS)
                return START_STICKY
            }
            ACTION_NEXT -> {
                streamRepository.sendCommand(MediaAction.NEXT)
                return START_STICKY
            }
            Intent.ACTION_MEDIA_BUTTON -> {
                if (!handleMediaButtonIntent(intent)) {
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
                return START_STICKY
            }
            ACTION_STOP -> {
                streamRepository.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat(buildNotification("正在准备连接..."))
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(state: com.xiaofeishu.audiostream.domain.model.PlaybackState) {
        val text = when (state.connectionState) {
            ConnectionState.PLAYING -> state.server?.let { "正在播放 - ${it.display}" }
                ?: getString(R.string.notification_playing)
            ConnectionState.CONNECTED -> state.server?.let { "已连接 - ${it.display}" }
                ?: "已连接"
            ConnectionState.CONNECTING ->
                if (state.reconnectAttempt > 0) "重连中（第 ${state.reconnectAttempt} 次）..."
                else "正在连接..."
            ConnectionState.ERROR -> state.error ?: getString(R.string.notification_disconnected)
            else -> getString(R.string.notification_disconnected)
        }
        updateMediaSession(state)
        val notification = buildNotification(text, state.mediaState)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun updateMediaSession(state: com.xiaofeishu.audiostream.domain.model.PlaybackState) {
        val ms = state.mediaState
        // 连接已建立（CONNECTED 或 PLAYING）时，根据服务端媒体状态同步播放状态
        if (state.connectionState == ConnectionState.PLAYING ||
            state.connectionState == ConnectionState.CONNECTED) {
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    ms?.title?.ifEmpty { null } ?: "未知曲目"
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                    ms?.artist?.ifEmpty { null } ?: state.server?.display
                )
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, ms?.durationMs ?: 0)
            mediaSession.setMetadata(metadataBuilder.build())

            val pbState = if (ms?.playing == true)
                PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(pbState, ms?.positionMs ?: 0, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .build()
            )
        } else {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1f)
                    .build()
            )
        }
    }

    private fun handleMediaButtonIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
        val keyEvent = getMediaButtonKeyEvent(intent) ?: return false
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return true
        if (keyEvent.repeatCount > 0) return true
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> streamRepository.sendCommand(MediaAction.PLAY_PAUSE)
            KeyEvent.KEYCODE_MEDIA_PLAY -> requestPlay()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> requestPause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> streamRepository.sendCommand(MediaAction.NEXT)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> streamRepository.sendCommand(MediaAction.PREVIOUS)
            else -> return false
        }
        return true
    }

    private fun getMediaButtonKeyEvent(intent: Intent): KeyEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    private fun requestPlay() {
        if (streamRepository.state.value.mediaState?.playing != true) {
            streamRepository.sendCommand(MediaAction.PLAY_PAUSE)
        }
    }

    private fun requestPause() {
        if (streamRepository.state.value.mediaState?.playing == true) {
            streamRepository.sendCommand(MediaAction.PLAY_PAUSE)
        }
    }

    private fun buildNotification(text: String, mediaState: MediaState? = null): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (mediaState?.playing == true) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (mediaState?.playing == true) getString(R.string.pause) else getString(R.string.play)
        val playPauseIntent = buildMediaPendingIntent(MediaAction.PLAY_PAUSE, REQUEST_CODE_PLAY_PAUSE)
        val prevIntent = buildMediaPendingIntent(MediaAction.PREVIOUS, REQUEST_CODE_PREV)
        val nextIntent = buildMediaPendingIntent(MediaAction.NEXT, REQUEST_CODE_NEXT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(mediaState?.title?.ifEmpty { null } ?: getString(R.string.app_name))
            .setContentText(mediaState?.artist?.ifEmpty { null } ?: text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopIntent)
            )
            .addAction(R.drawable.ic_skip_previous, getString(R.string.skip_previous), prevIntent)
            .addAction(playPauseIcon, playPauseLabel, playPauseIntent)
            .addAction(R.drawable.ic_skip_next, getString(R.string.skip_next), nextIntent)
            .addAction(R.drawable.ic_notification, getString(R.string.disconnect), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildMediaPendingIntent(action: MediaAction, requestCode: Int): PendingIntent {
        val actionStr = when (action) {
            MediaAction.PLAY_PAUSE -> ACTION_PLAY_PAUSE
            MediaAction.PREVIOUS -> ACTION_PREVIOUS
            MediaAction.NEXT -> ACTION_NEXT
            MediaAction.SEEK_TO -> "" // 通知栏不需要 seek 动作
            MediaAction.GET_STATE -> "" // 通知栏不需要 get_state 动作
        }
        val intent = Intent(this, AudioStreamService::class.java).apply {
            this.action = actionStr
        }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMediaButtonPendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, AudioStreamService::class.java)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun manageLocks(state: ConnectionState) {
        if (state == ConnectionState.PLAYING || state == ConnectionState.CONNECTING) {
            acquireWakeLock()
            acquireWifiLock()
        } else {
            releaseWifiLock()
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        if (wifiLock != null) return
        runCatching {
            val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务列表划掉 app 时，前台服务不自动停止
        // 只有用户主动点击"断开连接"才会停止服务
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
        releaseWifiLock()
        releaseWakeLock()
        super.onDestroy()
    }
}
