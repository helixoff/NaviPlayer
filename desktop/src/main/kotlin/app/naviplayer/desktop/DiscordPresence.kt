package app.naviplayer.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Best-effort Discord Rich Presence. It is deliberately isolated from playback:
 * a missing Discord client, IPC socket or native RPC library must never stop music.
 *
 * Set NAVIPLAYER_DISCORD_APP_ID to the application id created in Discord's developer portal.
 * Add an image asset named `naviplayer` there to show the large application artwork.
 */
class DiscordPresence(
    private val applicationId: String = System.getenv("NAVIPLAYER_DISCORD_APP_ID").orEmpty(),
) : AutoCloseable {
    private val rpc = if (applicationId.isBlank()) null else runCatching { DiscordRPC.INSTANCE }.getOrNull()
    private val callbacks = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "naviplayer-discord-rpc").apply { isDaemon = true }
    }
    private var initialized = false

    init {
        rpc?.let { client ->
            runCatching {
                client.Discord_Initialize(applicationId, DiscordEventHandlers(), true, "")
                initialized = true
                callbacks.scheduleWithFixedDelay(
                    { runCatching { client.Discord_RunCallbacks() } },
                    0,
                    2,
                    TimeUnit.SECONDS,
                )
            }
        }
    }

    fun update(song: Song, playing: Boolean, positionSeconds: Double) {
        val client = rpc ?: return
        if (!initialized) return
        runCatching {
            val presence = DiscordRichPresence().apply {
                details = song.title.take(128)
                state = (song.artist.ifBlank { song.album }).take(128)
                largeImageKey = "naviplayer"
                largeImageText = song.album.ifBlank { "NaviPlayer" }.take(128)
                smallImageKey = if (playing) "playing" else "paused"
                smallImageText = if (playing) "Playing" else "Paused"
                if (playing) {
                    startTimestamp = System.currentTimeMillis() / 1000L - positionSeconds.toLong()
                    if (song.durationSeconds > 0) endTimestamp = startTimestamp + song.durationSeconds
                }
            }
            client.Discord_UpdatePresence(presence)
        }
    }

    fun clear() {
        if (initialized) rpc?.let { client -> runCatching { client.Discord_ClearPresence() } }
    }

    override fun close() {
        callbacks.shutdownNow()
        if (initialized) rpc?.let { client -> runCatching { client.Discord_Shutdown() } }
        initialized = false
    }
}
