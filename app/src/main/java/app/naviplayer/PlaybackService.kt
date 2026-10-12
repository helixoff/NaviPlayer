package app.naviplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Фоновое воспроизведение: медиа-уведомление с обложкой и кнопками,
 * управление с экрана блокировки и из шторки (в т.ч. Samsung).
 * Треки идут через дисковый кэш ([TrackCache]) с предзагрузкой очереди — при пропаже сети музыка не обрывается.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private lateinit var playbackWebhook: PlaybackWebhookReporter
    private val heartbeat = object : Runnable {
        override fun run() {
            session?.player?.let(playbackWebhook::report)
            heartbeatHandler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.notif_channel)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)

        // Быстрый старт важнее огромного RAM-буфера: диск-кэш отдельно сохраняет весь поток.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 180_000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 750,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(TrackCache.dataSourceFactory(this)))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        playbackWebhook = PlaybackWebhookReporter()

        // Как только сменился трек или очередь — греем следующие несколько треков в кэш.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                TrackCache.prefetchAround(this@PlaybackService, player)
                playbackWebhook.report(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = playbackWebhook.report(player)
            override fun onPlaybackStateChanged(playbackState: Int) = playbackWebhook.report(player)
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = playbackWebhook.report(player)
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = TrackCache.prefetchAround(this@PlaybackService, player)
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) TrackCache.prefetchAround(this@PlaybackService, player)
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        heartbeatHandler.postDelayed(heartbeat, 15_000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeat)
        session?.player?.let(playbackWebhook::report)
        session?.run { player.release(); release() }
        session = null
        playbackWebhook.close()
        super.onDestroy()
    }
}
