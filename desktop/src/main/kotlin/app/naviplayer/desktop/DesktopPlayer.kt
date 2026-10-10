package app.naviplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
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

    init {
        runCatching { Platform.startup {} }
    }

    fun play(song: Song, songs: List<Song>, client: SubsonicClient) {
        queue = songs
        queueIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        open(song, client)
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

    fun next(client: SubsonicClient) {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex + 1).mod(queue.size)
        open(queue[queueIndex], client)
    }

    fun previous(client: SubsonicClient) {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex - 1 + queue.size).mod(queue.size)
        open(queue[queueIndex], client)
    }

    fun seek(seconds: Double) = onFxThread {
        mediaPlayer?.seek(javafx.util.Duration.seconds(seconds.coerceAtLeast(0.0)))
    }

    fun close() = onFxThread {
        mediaPlayer?.dispose()
        mediaPlayer = null
    }

    private fun open(song: Song, client: SubsonicClient) {
        current = song
        error = null
        positionSeconds = 0.0
        durationSeconds = max(0, song.durationSeconds).toDouble()
        onFxThread {
            runCatching {
                mediaPlayer?.dispose()
                val media = Media(client.streamUrl(song)).also { m ->
                    m.setOnError { error = "Media: ${m.error?.message ?: "unsupported or unreachable stream"}" }
                }
                MediaPlayer(media).also { player ->
                    mediaPlayer = player
                    player.setOnPlaying {
                        playing = true
                        discord.update(song, true, positionSeconds)
                    }
                    player.setOnPaused {
                        playing = false
                        discord.update(song, false, positionSeconds)
                    }
                    player.setOnStopped { playing = false }
                    player.setOnEndOfMedia { next(client) }
                    player.setOnError {
                        playing = false
                        error = player.error?.message ?: "Playback failed (${song.suffix.ifBlank { "?" }})"
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
            }
        }
    }

    private fun onFxThread(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    }
}
