package app.naviplayer.desktop

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
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
    private var cachedArtworkUrl: String? = null
    private var cachedArtworkDataUri: String? = null

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
            albumArtUrl = client.coverUrl(song.coverArt, 256),
        )
        runCatching { worker.execute(::sendLatest) }
    }

    private fun sendLatest() {
        if (closed.get()) return
        val snapshot = latest ?: return
        val elapsed = if (snapshot.playing) System.currentTimeMillis() - snapshot.capturedAt else 0L
        val progress = (snapshot.positionMs + elapsed).coerceAtMost(snapshot.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val artwork = snapshot.albumArtUrl?.let(::artworkDataUri)
        val body = JSONObject()
            .put("source", "desktop")
            .put("isPlaying", snapshot.playing)
            .put("trackId", snapshot.song.id)
            .put("title", snapshot.song.title)
            .put("artist", snapshot.song.artist)
            .put("album", snapshot.song.album)
            // Upload a small self-contained image. The public webhook server cannot
            // reach Navidrome instances that live on LAN/Tailscale, and the private
            // authenticated cover URL must not be exposed in the public JSON state.
            .put("albumArtUrl", artwork ?: JSONObject.NULL)
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

    private fun artworkDataUri(url: String): String? {
        if (url == cachedArtworkUrl && cachedArtworkDataUri != null) return cachedArtworkDataUri
        cachedArtworkUrl = url
        cachedArtworkDataUri = runCatching {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.byteStream()?.use { it.readLimited(MAX_ARTWORK_DOWNLOAD_BYTES) }
                    ?: return@use null
                val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@use null
                val scale = minOf(1.0, ARTWORK_SIZE.toDouble() / max(source.width, source.height))
                val width = max(1, (source.width * scale).toInt())
                val height = max(1, (source.height * scale).toInt())
                val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                resized.createGraphics().use { graphics ->
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    graphics.drawImage(source, 0, 0, width, height, null)
                }
                val output = ByteArrayOutputStream()
                if (!ImageIO.write(resized, "jpeg", output)) return@use null
                "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(output.toByteArray())}"
            }
        }.getOrNull()
        return cachedArtworkDataUri
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
        const val ARTWORK_SIZE = 192
        const val MAX_ARTWORK_DOWNLOAD_BYTES = 2 * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun java.awt.Graphics2D.use(block: (java.awt.Graphics2D) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}

private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) return output.toByteArray()
        total += read
        require(total <= limit) { "Artwork response is too large" }
        output.write(buffer, 0, read)
    }
}
