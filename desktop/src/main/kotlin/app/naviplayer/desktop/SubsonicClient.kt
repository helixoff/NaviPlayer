package app.naviplayer.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubsonicClient(credentials: Credentials, private val salt: String) {
    private val baseUrl = credentials.server.trim().trimEnd('/')
    private val username = credentials.username
    private val token = md5(credentials.password + salt)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun endpoint(method: String, parameters: Map<String, String> = emptyMap()): HttpUrl {
        val builder = "$baseUrl/rest/$method".toHttpUrl().newBuilder()
        val auth = mapOf(
            "u" to username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "NaviPlayerDesktop",
            "f" to "json",
        )
        (auth + parameters).forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private suspend fun request(method: String, parameters: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint(method, parameters)).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
                val body = response.body?.string()?.takeIf(String::isNotBlank)
                    ?: error("Server returned an empty response")
                val root = JSONObject(body).getJSONObject("subsonic-response")
                if (root.optString("status") != "ok") {
                    error(root.optJSONObject("error")?.optString("message") ?: "Navidrome error")
                }
                root
            }
        }

    suspend fun ping() {
        request("ping")
    }

    suspend fun newestAlbums(size: Int = 60): List<Album> =
        request("getAlbumList2", mapOf("type" to "newest", "size" to size.toString()))
            .optJSONObject("albumList2")?.optJSONArray("album").mapObjects { it.toAlbum() }

    suspend fun albumsAlphabetical(limit: Int = 2000): List<Album> {
        val albums = ArrayList<Album>()
        var offset = 0
        while (albums.size < limit) {
            val page = request(
                "getAlbumList2",
                mapOf("type" to "alphabeticalByName", "size" to "500", "offset" to offset.toString()),
            ).optJSONObject("albumList2")?.optJSONArray("album").mapObjects { it.toAlbum() }
            albums += page
            if (page.size < 500) break
            offset += page.size
        }
        return albums
    }

    suspend fun randomSongs(size: Int = 40): List<Song> =
        request("getRandomSongs", mapOf("size" to size.toString()))
            .optJSONObject("randomSongs")?.optJSONArray("song").mapObjects { it.toSong() }

    suspend fun albumSongs(albumId: String): List<Song> =
        request("getAlbum", mapOf("id" to albumId))
            .optJSONObject("album")?.optJSONArray("song").mapObjects { it.toSong() }

    suspend fun playlists(): List<Playlist> =
        request("getPlaylists")
            .optJSONObject("playlists")?.optJSONArray("playlist").mapObjects { it.toPlaylist() }

    suspend fun playlistSongs(playlistId: String): List<Song> =
        request("getPlaylist", mapOf("id" to playlistId))
            .optJSONObject("playlist")?.optJSONArray("entry").mapObjects { it.toSong() }

    suspend fun search(query: String): SearchResult {
        val result = request(
            "search3",
            mapOf("query" to query, "albumCount" to "30", "songCount" to "80", "artistCount" to "0"),
        ).optJSONObject("searchResult3")
        return SearchResult(
            albums = result?.optJSONArray("album").mapObjects { it.toAlbum() },
            songs = result?.optJSONArray("song").mapObjects { it.toSong() },
        )
    }

    // JavaFX Media plays only MP3, AAC/M4A, WAV and AIFF. Anything else (FLAC, Opus, OGG...)
    // is transcoded to MP3 by Navidrome (requires ffmpeg on the server).
    fun streamUrl(song: Song): String {
        val directPlay = song.suffix in JAVAFX_FORMATS
        val parameters = if (directPlay) {
            mapOf("id" to song.id, "format" to "raw")
        } else {
            mapOf("id" to song.id, "format" to "mp3", "maxBitRate" to "320")
        }
        return endpoint("stream", parameters).toString()
    }

    fun coverUrl(coverArt: String?, size: Int = 600): String? = coverArt?.let {
        endpoint("getCoverArt", mapOf("id" to it, "size" to size.toString())).toString()
    }

    companion object {
        private val JAVAFX_FORMATS = setOf("mp3", "m4a", "aac", "wav", "aif", "aiff")

        fun newSalt(): String = UUID.randomUUID().toString().replace("-", "").take(12)

        private fun md5(value: String): String = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
