package app.naviplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

class DesktopPlayer(private val discord: DiscordPresence) {
    var current by mutableStateOf<Song?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionSeconds by mutableDoubleStateOf(0.0)
        private set
    var durationSeconds by mutableDoubleStateOf(0.0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var queue: List<Song> = emptyList()
    private var queueIndex = -1
    private var activeClient: SubsonicClient? = null
    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        runCatching { Platform.startup {} }
    }

    fun play(song: Song, songs: List<Song>, client: SubsonicClient) {
        queue = songs
        queueIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        activeClient = client
        open(song)
    }

    fun toggle() = onFxThread {
        val player = mediaPlayer ?: return@onFxThread
        if (playing) {
            player.pause()
            current?.let { discord.update(it, false, positionSeconds) }
        } else {
            player.play()
            current?.let { discord.update(it, true, positionSeconds) }
        }
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
        if (queue.isEmpty() || activeClient == null) return
        queueIndex = (queueIndex + 1).mod(queue.size)
        open(queue[queueIndex])
    }

    fun previous() {
        if (queue.isEmpty() || activeClient == null) return
        queueIndex = (queueIndex - 1 + queue.size).mod(queue.size)
        open(queue[queueIndex])
    }

    fun seek(seconds: Double) = onFxThread {
        mediaPlayer?.seek(javafx.util.Duration.seconds(seconds.coerceAtLeast(0.0)))
    }

    fun close() = onFxThread {
        mediaPlayer?.dispose()
        mediaPlayer = null
        playing = false
        discord.clear()
        notifyStateChanged()
    }

    fun addStateListener(listener: () -> Unit): AutoCloseable {
        stateListeners += listener
        return AutoCloseable { stateListeners -= listener }
    }

    private fun open(song: Song) {
        val client = activeClient ?: return
        current = song
        playing = false
        error = null
        positionSeconds = 0.0
        durationSeconds = max(0, song.durationSeconds).toDouble()
        // Update immediately instead of leaving the previous track in Discord while media is opening.
        discord.update(song, false, 0.0)
        notifyStateChanged()
        onFxThread {
            runCatching {
                mediaPlayer?.dispose()
                val media = Media(client.streamUrl(song)).also { m ->
                    m.setOnError {
                        playing = false
                        error = "Media: ${m.error?.message ?: "unsupported or unreachable stream"}"
                        discord.clear()
                        notifyStateChanged()
                    }
                }
                MediaPlayer(media).also { player ->
                    mediaPlayer = player
                    player.setOnPlaying {
                        playing = true
                        discord.update(song, true, positionSeconds)
                        notifyStateChanged()
                    }
                    player.setOnPaused {
                        playing = false
                        discord.update(song, false, positionSeconds)
                        notifyStateChanged()
                    }
                    player.setOnStopped {
                        playing = false
                        discord.update(song, false, positionSeconds)
                        notifyStateChanged()
                    }
                    player.setOnEndOfMedia { next() }
                    player.setOnError {
                        playing = false
                        error = player.error?.message ?: "Playback failed (${song.suffix.ifBlank { "?" }})"
                        discord.clear()
                        notifyStateChanged()
                    }
                    player.currentTimeProperty().addListener { _, _, time ->
                        positionSeconds = time.toSeconds().takeIf(Double::isFinite) ?: 0.0
                    }
                    player.totalDurationProperty().addListener { _, _, time ->
                        durationSeconds = time.toSeconds().takeIf(Double::isFinite)
                            ?: song.durationSeconds.toDouble()
                    }
                    player.play()
                }
            }.onFailure {
                playing = false
                error = it.message ?: "Playback failed"
                discord.clear()
                notifyStateChanged()
            }
        }
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { listener -> runCatching(listener) }
    }

    private fun onFxThread(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    }
}
