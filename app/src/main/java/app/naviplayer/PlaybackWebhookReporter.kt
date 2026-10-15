package app.naviplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.media3.common.Player
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackWebhookReporter : AutoCloseable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "naviplayer-now-playing").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val updateLock = Any()
    private var pendingSnapshot: Snapshot? = null
    private var updateWorkerRunning = false
    private var cachedArtworkUrl: String? = null
    private var cachedArtworkDataUri: String? = null

    fun report(player: Player) {
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val artwork = metadata.artworkUri
        val auth = artwork?.toWebhookAuth() ?: item.localConfiguration?.uri?.toWebhookAuth() ?: return
        if (closed.get()) return
        val snapshot = Snapshot(
            source = "android",
            isPlaying = player.isPlaying,
            trackId = item.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            artworkUrl = artwork?.toString(),
            progressMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            auth = auth,
        )
        var startWorker = false
        synchronized(updateLock) {
            pendingSnapshot = snapshot
            if (!updateWorkerRunning) {
                updateWorkerRunning = true
                startWorker = true
            }
        }
        if (startWorker) runCatching { worker.execute(::drainUpdates) }.onFailure {
            synchronized(updateLock) { updateWorkerRunning = false }
        }
    }

    private fun drainUpdates() {
        while (!closed.get()) {
            val snapshot = synchronized(updateLock) {
                pendingSnapshot.also { pendingSnapshot = null }.also {
                    if (it == null) updateWorkerRunning = false
                }
            } ?: return
            send(snapshot)
        }
    }

    private fun send(snapshot: Snapshot) {
        if (closed.get()) return
        val artwork = snapshot.artworkUrl?.let(::artworkDataUri)
        if (closed.get()) return
        val payload = JSONObject()
            .put("source", snapshot.source)
            .put("isPlaying", snapshot.isPlaying)
            .put("trackId", snapshot.trackId)
            .put("title", snapshot.title)
            .put("artist", snapshot.artist)
            .put("album", snapshot.album)
            // The public webhook cannot reach private LAN/Tailscale Navidrome
            // URLs. Send a small self-contained image without URL credentials.
            .put("albumArtUrl", artwork ?: JSONObject.NULL)
            .put("progressMs", snapshot.progressMs)
            .put("durationMs", snapshot.durationMs)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .header("X-Navidrome-User", snapshot.auth.user)
            .header("X-Navidrome-Token", snapshot.auth.token)
            .header("X-Navidrome-Salt", snapshot.auth.salt)
            .post(payload)
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
                val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                val scale = minOf(1f, ARTWORK_SIZE.toFloat() / maxOf(source.width, source.height))
                val width = maxOf(1, (source.width * scale).toInt())
                val height = maxOf(1, (source.height * scale).toInt())
                val resized = if (width == source.width && height == source.height) source
                else Bitmap.createScaledBitmap(source, width, height, true)
                val output = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
                if (resized !== source) resized.recycle()
                source.recycle()
                "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
            }
        }.getOrNull()
        return cachedArtworkDataUri
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(updateLock) { pendingSnapshot = null }
        worker.shutdownNow()
        http.dispatcher.cancelAll()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun Uri.toWebhookAuth(): Auth? {
        val user = getQueryParameter("u")?.takeIf { it.isNotBlank() } ?: return null
        val token = getQueryParameter("t")?.takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) } ?: return null
        val salt = getQueryParameter("s")?.takeIf { it.isNotBlank() } ?: return null
        return Auth(user, token, salt)
    }

    private data class Auth(val user: String, val token: String, val salt: String)

    private data class Snapshot(
        val source: String,
        val isPlaying: Boolean,
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String?,
        val progressMs: Long,
        val durationMs: Long,
        val auth: Auth,
    )

    private companion object {
        const val WEBHOOK_URL = "https://feepiwakura.lol/api/now-playing"
        const val ARTWORK_SIZE = 192
        const val ARTWORK_JPEG_QUALITY = 78
        const val MAX_ARTWORK_DOWNLOAD_BYTES = 2 * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
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
