package app.naviplayer

import kotlinx.coroutines.CancellationException
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

/**
 * Клиент Subsonic API (его понимает Navidrome).
 * Соль одна на экземпляр, поэтому URL обложек и стрима стабильны (кэш Coil работает).
 */
class SubsonicApi(creds: Credentials, private val salt: String = newSalt()) {
    private val base = creds.url.trim().trimEnd('/')
    private val user = creds.user
    private val token = md5(creds.password + salt)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun url(method: String, extra: Map<String, String> = emptyMap()): HttpUrl {
        val b = "$base/rest/$method".toHttpUrl().newBuilder()
        val params = mapOf("u" to user, "t" to token, "s" to salt, "v" to "1.16.1", "c" to "NaviPlayer", "f" to "json") + extra
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    // format=raw: отдавать файл как есть, без перекодирования (перемотка работает надёжнее)
    fun streamUrl(id: String) = url("stream", mapOf("id" to id, "format" to "raw")).toString()
    fun coverUrl(id: String, size: Int = 500) = url("getCoverArt", mapOf("id" to id, "size" to "$size")).toString()

    private suspend fun call(method: String, extra: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url(method, extra)).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("server answered ${r.code}")
                val payload = r.body?.string()?.takeIf { it.isNotBlank() } ?: error("server returned an empty response")
                val root = JSONObject(payload).getJSONObject("subsonic-response")
                if (root.optString("status") != "ok") {
                    error(root.optJSONObject("error")?.optString("message") ?: "server error")
                }
                root
            }
        }

    suspend fun ping() { call("ping") }

    /** type: newest | random | frequent | recent | alphabeticalByName */
    suspend fun albumList(type: String, size: Int = 60, offset: Int = 0): List<Album> =
        call("getAlbumList2", mapOf("type" to type, "size" to "$size", "offset" to "$offset"))
            .optJSONObject("albumList2")?.optJSONArray("album").mapObjs { it.toAlbum() }
            .filterNot { it.isPlaceholder() }

    suspend fun album(id: String): Pair<Album, List<Song>> {
        val a = call("getAlbum", mapOf("id" to id)).getJSONObject("album")
        return a.toAlbum() to a.optJSONArray("song").mapObjs { it.toSong() }
    }

    suspend fun search(q: String): SearchResult {
        val r = call("search3", mapOf("query" to q, "albumCount" to "20", "songCount" to "40", "artistCount" to "10"))
            .optJSONObject("searchResult3")
        return SearchResult(
            albums = r?.optJSONArray("album").mapObjs { it.toAlbum() }.filterNot { it.isPlaceholder() },
            songs = r?.optJSONArray("song").mapObjs { it.toSong() },
            artists = r?.optJSONArray("artist").mapObjs { it.toArtist() },
        )
    }

    /** Все песни библиотеки постранично (пустой запрос "" у Navidrome = "вернуть всё"). */
    suspend fun allSongs(offset: Int, size: Int = 500): List<Song> =
        call(
            "search3",
            mapOf("query" to "\"\"", "songCount" to "$size", "songOffset" to "$offset", "albumCount" to "0", "artistCount" to "0"),
        ).optJSONObject("searchResult3")?.optJSONArray("song").mapObjs { it.toSong() }

    /** Все песни библиотеки одним списком (поиск с пустым запросом). Если сервер так не умеет, берём случайные. */
    suspend fun allSongs(limit: Int = 6000): List<Song> {
        val all = ArrayList<Song>()
        var offset = 0
        while (all.size < limit) {
            val page = call(
                "search3",
                mapOf("query" to "", "songCount" to "500", "songOffset" to "$offset", "albumCount" to "0", "artistCount" to "0"),
            ).optJSONObject("searchResult3")?.optJSONArray("song").mapObjs { it.toSong() }
            all.addAll(page)
            if (page.size < 500) break
            offset += 500
        }
        return if (all.isEmpty()) randomSongs(500) else all
    }

    suspend fun randomSongs(size: Int, genre: String? = null): List<Song> {
        val extra = HashMap<String, String>()
        extra["size"] = "$size"
        if (genre != null) extra["genre"] = genre
        return call("getRandomSongs", extra).optJSONObject("randomSongs")?.optJSONArray("song").mapObjs { it.toSong() }
    }

    /** Список жанров: (название, число песен). */
    suspend fun genres(): List<Pair<String, Int>> =
        call("getGenres").optJSONObject("genres")?.optJSONArray("genre")
            .mapObjs { it.optString("value") to it.optInt("songCount") }

    /** Засчитывает прослушивание: от этого работают "недавние" и "частые". */
    suspend fun scrobble(id: String) { call("scrobble", mapOf("id" to id, "submission" to "true")) }

    suspend fun playlists(): List<Playlist> =
        call("getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist").mapObjs { it.toPlaylist() }

    suspend fun playlist(id: String): List<Song> =
        call("getPlaylist", mapOf("id" to id)).optJSONObject("playlist")?.optJSONArray("entry").mapObjs { it.toSong() }

    suspend fun artists(): List<Artist> {
        val index = call("getArtists").optJSONObject("artists")?.optJSONArray("index")
        return index.mapObjs { idx -> idx.optJSONArray("artist").mapObjs { a -> a.toArtist() } }.flatten()
    }

    suspend fun artistAlbums(id: String): List<Album> =
        call("getArtist", mapOf("id" to id)).optJSONObject("artist")?.optJSONArray("album").mapObjs { it.toAlbum() }

    /** Текст с таймкодами (OpenSubsonic), если есть синхронизированный, берём его. */
    suspend fun lyricsStructured(songId: String): Lyrics? = try {
        val arr = call("getLyricsBySongId", mapOf("id" to songId))
            .optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
        var pick: JSONObject? = null
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (pick == null || (o.optBoolean("synced") && !pick.optBoolean("synced"))) pick = o
            }
        }
        val lines = pick?.optJSONArray("line").mapObjs { LyricLine(it.optLong("start", 0L), it.optString("value")) }
        if (pick == null || lines.isEmpty()) null else Lyrics(lines, pick.optBoolean("synced"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Текст песни (OpenSubsonic). Если сервер/трек не поддерживает, вернёт null. */
    suspend fun lyrics(songId: String): String? = try {
        val lines = call("getLyricsBySongId", mapOf("id" to songId))
            .optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
            ?.optJSONObject(0)?.optJSONArray("line")
        if (lines == null) null
        else (0 until lines.length()).joinToString("\n") { lines.getJSONObject(it).optString("value") }.ifBlank { null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

fun newSalt(): String = UUID.randomUUID().toString().replace("-", "").take(12)
