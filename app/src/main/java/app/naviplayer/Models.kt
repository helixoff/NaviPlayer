package app.naviplayer

import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val durationSec: Int,
    val track: Int,
    val suffix: String = "",
    val bitRate: Int = 0,
)

data class Album(val id: String, val name: String, val artist: String, val coverArt: String?, val year: Int?)
data class Artist(val id: String, val name: String, val coverArt: String?)
data class Playlist(val id: String, val name: String, val coverArt: String?, val songCount: Int)
data class Mix(val title: String, val subtitle: String, val songs: List<Song>)
data class SearchResult(val albums: List<Album>, val songs: List<Song>, val artists: List<Artist>)
data class HomeData(val recent: List<Album>, val mixes: List<Mix>, val albumsToday: List<Album>, val artists: List<Artist>)
data class Credentials(val url: String, val user: String, val password: String)

private val placeholderAlbums = setOf(
    "unknown", "unknown album", "untitled", "untitled album", "no album", "none", "n/a",
    "soundcloud", "неизвестно", "неизвестный альбом", "без альбома",
)

private fun normalizedName(value: String) = value.trim().lowercase()
    .trim('[', ']', '(', ')', '<', '>')
    .replace(Regex("\\s+"), " ")
    .trim()

fun isPlaceholderAlbum(album: String, title: String = ""): Boolean {
    val a = normalizedName(album)
    val t = normalizedName(title)
    return a.isEmpty() || a in placeholderAlbums || (t.isNotEmpty() && a == t)
}

fun Album.isPlaceholder() = isPlaceholderAlbum(name)

// ---------- JSON-помощники ----------

internal fun JSONObject.str(k: String): String? = optString(k).ifEmpty { null }

internal inline fun <T> JSONArray?.mapObjs(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

/** Navidrome may expose every per-track artist as one comma-separated album artist. */
private fun compactAlbumArtist(value: String): String {
    val clean = value.trim()
    val artists = clean.split(Regex("\\s*[,;]\\s*")).filter { it.isNotBlank() }
    return if (artists.size >= 3) artists.first() else clean
}

internal fun JSONObject.toAlbum() = Album(
    id = getString("id"),
    name = str("name") ?: str("title") ?: "untitled",
    artist = compactAlbumArtist(str("displayArtist") ?: str("artist") ?: ""),
    coverArt = str("coverArt"),
    year = optInt("year").takeIf { it > 0 },
)

internal fun JSONObject.toSong() = Song(
    id = getString("id"),
    title = str("title") ?: "untitled",
    artist = str("artist") ?: "",
    album = str("album") ?: "",
    coverArt = str("coverArt"),
    durationSec = optInt("duration"),
    track = optInt("track"),
    suffix = str("suffix") ?: "",
    bitRate = optInt("bitRate"),
)

internal fun JSONObject.toArtist() = Artist(getString("id"), str("name") ?: "unknown", str("coverArt"))

internal fun JSONObject.toPlaylist() =
    Playlist(getString("id"), str("name") ?: "playlist", str("coverArt"), optInt("songCount"))

internal fun JSONObject.toMix() =
    Mix(getString("title"), optString("subtitle"), optJSONArray("songs").mapObjs { it.toSong() })

internal fun Song.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("artist", artist).put("album", album)
    .put("coverArt", coverArt ?: "").put("duration", durationSec).put("track", track)
    .put("suffix", suffix).put("bitRate", bitRate)

internal fun Album.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("artist", artist)
    .put("coverArt", coverArt ?: "").put("year", year ?: 0)

internal fun Mix.toJson(): JSONObject = JSONObject()
    .put("title", title).put("subtitle", subtitle)
    .put("songs", JSONArray(songs.map { it.toJson() }))

/** Трек без нормальных тегов (скачан с SoundCloud и т.п.): нет исполнителя/альбома или альбом = название трека. */
fun Song.isLoose(): Boolean {
    val ar = artist.trim().lowercase()
    return ar.isEmpty() || ar.startsWith("[unknown") || isPlaceholderAlbum(album, title)
}

data class LyricLine(val startMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/** Убирает дубликаты (тот же исполнитель + название, длительность ±3 с), оставляя лучшее качество. */
fun List<Song>.dedupe(): List<Song> {
    val lossless = setOf("flac", "wav", "ape", "wv", "aiff", "aif")
    val out = ArrayList<Song>()
    val groups = groupBy { it.artist.trim().lowercase() + "\u0000" + it.title.trim().lowercase() }
    for ((key, g) in groups) {
        if (key.startsWith("\u0000") || g.size == 1) { out.addAll(g); continue } // без исполнителя не склеиваем
        var cluster = ArrayList<Song>()
        for (s in g.sortedBy { it.durationSec }) {
            if (cluster.isNotEmpty() && s.durationSec - cluster.last().durationSec > 3) {
                out.add(cluster.maxBy { (if (it.suffix.lowercase() in lossless) 1000 else 0) + it.bitRate + (if (it.coverArt != null) 5 else 0) })
                cluster = ArrayList()
            }
            cluster.add(s)
        }
        out.add(cluster.maxBy { (if (it.suffix.lowercase() in lossless) 1000 else 0) + it.bitRate + (if (it.coverArt != null) 5 else 0) })
    }
    return out
}
