package app.naviplayer.desktop

import org.json.JSONArray
import org.json.JSONObject

data class Credentials(val server: String, val username: String, val password: String)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val durationSeconds: Int,
    val suffix: String = "",
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverArt: String?,
    val year: Int?,
)

data class Playlist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val songCount: Int,
)

data class SearchResult(val albums: List<Album>, val songs: List<Song>)

internal inline fun <T> JSONArray?.mapObjects(mapper: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { mapper(getJSONObject(it)) }

internal fun JSONObject.toSong() = Song(
    id = getString("id"),
    title = optString("title").ifBlank { "Untitled" },
    artist = optString("artist"),
    album = optString("album"),
    coverArt = optString("coverArt").ifBlank { null },
    durationSeconds = optInt("duration"),
    suffix = optString("suffix").lowercase(),
)

internal fun JSONObject.toAlbum() = Album(
    id = getString("id"),
    title = optString("name").ifBlank { optString("title", "Untitled") },
    artist = optString("artist"),
    coverArt = optString("coverArt").ifBlank { null },
    year = optInt("year").takeIf { it > 0 },
)

internal fun JSONObject.toPlaylist() = Playlist(
    id = getString("id"),
    name = optString("name").ifBlank { "Playlist" },
    coverArt = optString("coverArt").ifBlank { null },
    songCount = optInt("songCount"),
)
