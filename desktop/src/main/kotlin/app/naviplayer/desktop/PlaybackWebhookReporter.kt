package app.naviplayer.desktop

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class PlaybackWebhookReporter : AutoCloseable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "naviplayer-now-playing").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)

    @Volatile private var latest: Snapshot? = null

    init {
        worker.scheduleWithFixedDelay(::sendLatest, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS)
    }

    fun update(
        song: Song?,
        playing: Boolean,
        positionSeconds: Double,
        durationSeconds: Double,
        client: SubsonicClient?,
    ) {
        if (closed.get() || song == null || client == null) return
        latest = Snapshot(
            song = song,
            playing = playing,
            positionMs = max(0.0, positionSeconds).times(1000).toLong(),
            durationMs = max(0.0, durationSeconds).times(1000).toLong(),
            capturedAt = System.currentTimeMillis(),
            auth = client.playbackWebhookAuth(),
            albumArtUrl = client.coverUrl(song.coverArt, 600),
        )
        runCatching { worker.execute(::sendLatest) }
    }

    private fun sendLatest() {
        if (closed.get()) return
        val snapshot = latest ?: return
        val elapsed = if (snapshot.playing) System.currentTimeMillis() - snapshot.capturedAt else 0L
        val progress = (snapshot.positionMs + elapsed).coerceAtMost(snapshot.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val body = JSONObject()
            .put("source", "desktop")
            .put("isPlaying", snapshot.playing)
            .put("trackId", snapshot.song.id)
            .put("title", snapshot.song.title)
            .put("artist", snapshot.song.artist)
            .put("album", snapshot.song.album)
            .put("albumArtUrl", snapshot.albumArtUrl ?: JSONObject.NULL)
            .put("progressMs", progress)
            .put("durationMs", snapshot.durationMs)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .header("X-Navidrome-User", snapshot.auth.username)
            .header("X-Navidrome-Token", snapshot.auth.token)
            .header("X-Navidrome-Salt", snapshot.auth.salt)
            .post(body)
            .build()
        runCatching { http.newCall(request).execute().close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.shutdownNow()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private data class Snapshot(
        val song: Song,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val capturedAt: Long,
        val auth: PlaybackWebhookAuth,
        val albumArtUrl: String?,
    )

    private companion object {
        const val WEBHOOK_URL = "https://feepiwakura.lol/api/now-playing"
        const val HEARTBEAT_SECONDS = 15L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
