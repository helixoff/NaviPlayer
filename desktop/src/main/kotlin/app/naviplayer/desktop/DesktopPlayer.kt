package app.naviplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

class DesktopPlayer(
    private val discord: DiscordPresence,
    private val playbackWebhook: PlaybackWebhookReporter,
) {
    var current by mutableStateOf<Song?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionSeconds by mutableDoubleStateOf(0.0)
        private set
    var durationSeconds by mutableDoubleStateOf(0.0)
        private set
    var volume by mutableDoubleStateOf(1.0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var queue: List<Song> = emptyList()
    private var queueIndex = -1
    private var activeClient: SubsonicClient? = null
    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()
    @Volatile private var playbackGeneration = 0L
    private var lastPositionUpdateNanos = 0L
    @Volatile private var latestPositionSeconds = 0.0
    @Volatile private var latestPositionGeneration = 0L
    private val positionUpdateQueued = AtomicBoolean(false)

    init {
        runCatching { Platform.startup {} }
    }

    fun play(song: Song, songs: List<Song>, client: SubsonicClient) {
        onUiThread {
            queue = songs
            queueIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            activeClient = client
            open(song)
        }
    }

    fun toggle() = onFxThread {
        val player = mediaPlayer ?: return@onFxThread
        if (player.status == MediaPlayer.Status.PLAYING) player.pause() else player.play()
    }

    fun play() = onFxThread {
        mediaPlayer?.play()
    }

    fun pause() = onFxThread {
        mediaPlayer?.pause()
    }

    fun stop() = onFxThread {
        mediaPlayer?.stop()
    }

    fun next() {
        onUiThread {
            if (queue.isEmpty() || activeClient == null) return@onUiThread
            queueIndex = (queueIndex + 1).mod(queue.size)
            open(queue[queueIndex])
        }
    }

    fun previous() {
        onUiThread {
            if (queue.isEmpty() || activeClient == null) return@onUiThread
            queueIndex = (queueIndex - 1 + queue.size).mod(queue.size)
            open(queue[queueIndex])
        }
    }

    fun seek(seconds: Double) = onFxThread {
        val target = seconds.coerceAtLeast(0.0)
        mediaPlayer?.seek(javafx.util.Duration.seconds(target))
        onUiThread {
            positionSeconds = target
            notifyStateChanged()
        }
    }

    fun changeVolume(value: Double) {
        volume = value.coerceIn(0.0, 1.0)
        previewVolume(volume)
        notifyStateChanged()
    }

    fun previewVolume(value: Double) = onFxThread {
        mediaPlayer?.volume = value.coerceIn(0.0, 1.0)
    }

    fun close() {
        val closed = CountDownLatch(1)
        val release = {
            try {
                playbackGeneration++
                mediaPlayer?.stop()
                mediaPlayer?.dispose()
                mediaPlayer = null
                playing = false
                notifyStateChanged()
            } finally {
                closed.countDown()
            }
        }
        if (Platform.isFxApplicationThread()) {
            release()
        } else {
            runCatching { Platform.runLater(release) }.onFailure { closed.countDown() }
            runCatching { closed.await(2, TimeUnit.SECONDS) }
        }
        runCatching { Platform.exit() }
    }

    fun addStateListener(listener: () -> Unit): AutoCloseable {
        stateListeners += listener
        return AutoCloseable { stateListeners -= listener }
    }

    private fun open(song: Song) {
        val client = activeClient ?: return
        val generation = playbackGeneration + 1
        playbackGeneration = generation
        current = song
        playing = false
        error = null
        positionSeconds = 0.0
        lastPositionUpdateNanos = 0L
        durationSeconds = max(0, song.durationSeconds).toDouble()
        // Update immediately instead of leaving the previous track in Discord while media is opening.
        discord.update(song, false)
        notifyStateChanged()
        onFxThread {
            runCatching {
                mediaPlayer?.dispose()
                val media = Media(client.streamUrl(song)).also { m ->
                    m.setOnError {
                        onUiThread {
                            if (generation != playbackGeneration) return@onUiThread
                            playing = false
                            error = "Media: ${m.error?.message ?: "unsupported or unreachable stream"}"
                            discord.clear()
                            notifyStateChanged()
                        }
                    }
                }
                MediaPlayer(media).also { player ->
                    mediaPlayer = player
                    player.volume = volume
                    player.setOnPlaying {
                        onUiThread {
                            if (generation != playbackGeneration) return@onUiThread
                            playing = true
                            discord.update(song, true)
                            notifyStateChanged()
                        }
                    }
                    player.setOnPaused {
                        onUiThread {
                            if (generation != playbackGeneration) return@onUiThread
                            playing = false
                            discord.update(song, false)
                            notifyStateChanged()
                        }
                    }
                    player.setOnStopped {
                        onUiThread {
                            if (generation != playbackGeneration) return@onUiThread
                            playing = false
                            discord.update(song, false)
                            notifyStateChanged()
                        }
                    }
                    player.setOnEndOfMedia {
                        if (generation == playbackGeneration) next()
                    }
                    player.setOnError {
                        onUiThread {
                            if (generation != playbackGeneration) return@onUiThread
                            playing = false
                            error = player.error?.message ?: "Playback failed (${song.suffix.ifBlank { "?" }})"
                            discord.clear()
                            notifyStateChanged()
                        }
                    }
                    player.currentTimeProperty().addListener { _, _, time ->
                        val now = System.nanoTime()
                        if (now - lastPositionUpdateNanos >= POSITION_UPDATE_INTERVAL_NANOS) {
                            latestPositionSeconds = time.toSeconds().takeIf(Double::isFinite) ?: 0.0
                            latestPositionGeneration = generation
                            lastPositionUpdateNanos = now
                            publishLatestPosition()
                        }
                    }
                    player.totalDurationProperty().addListener { _, _, time ->
                        val duration = time.toSeconds().takeIf(Double::isFinite)
                            ?: song.durationSeconds.toDouble()
                        onUiThread {
                            if (generation == playbackGeneration) durationSeconds = duration
                        }
                    }
                    player.play()
                }
            }.onFailure {
                onUiThread {
                    if (generation != playbackGeneration) return@onUiThread
                    playing = false
                    error = it.message ?: "Playback failed"
                    discord.clear()
                    notifyStateChanged()
                }
            }
        }
    }

    private fun publishLatestPosition() {
        if (!positionUpdateQueued.compareAndSet(false, true)) return
        onUiThread {
            positionUpdateQueued.set(false)
            if (latestPositionGeneration != playbackGeneration) return@onUiThread
            val position = latestPositionSeconds
            if (abs(positionSeconds - position) >= MINIMUM_POSITION_CHANGE_SECONDS) {
                positionSeconds = position
            }
        }
    }

    private fun notifyStateChanged() {
        playbackWebhook.update(current, playing, positionSeconds, durationSeconds, activeClient)
        stateListeners.forEach { listener -> runCatching(listener) }
    }

    private fun onFxThread(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    }

    private fun onUiThread(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }

    private companion object {
        // Four UI updates per second keep the seek bar fluid without making the whole
        // player chrome fight JavaFX's decoder for CPU time on Windows and Linux.
        const val POSITION_UPDATE_INTERVAL_NANOS = 250_000_000L
        const val MINIMUM_POSITION_CHANGE_SECONDS = 0.15
    }
}
