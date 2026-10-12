package app.naviplayer

import android.net.Uri
import androidx.media3.common.Player
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PlaybackWebhookReporter : AutoCloseable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun report(player: Player) {
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val artwork = metadata.artworkUri
        val auth = artwork?.toWebhookAuth() ?: item.localConfiguration?.uri?.toWebhookAuth() ?: return
        val duration = player.duration.coerceAtLeast(0)
        val payload = JSONObject()
            .put("source", "android")
            .put("isPlaying", player.isPlaying)
            .put("trackId", item.mediaId)
            .put("title", metadata.title?.toString().orEmpty())
            .put("artist", metadata.artist?.toString().orEmpty())
            .put("album", metadata.albumTitle?.toString().orEmpty())
            .put("albumArtUrl", artwork?.toString() ?: JSONObject.NULL)
            .put("progressMs", player.currentPosition.coerceAtLeast(0))
            .put("durationMs", duration)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .header("X-Navidrome-User", auth.user)
            .header("X-Navidrome-Token", auth.token)
            .header("X-Navidrome-Salt", auth.salt)
            .post(payload)
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
        })
    }

    override fun close() {
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

    private companion object {
        const val WEBHOOK_URL = "https://feepiwakura.lol/api/now-playing"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
