package app.naviplayer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray

/** Собирает главную: недавнее, миксы дня (из твоей библиотеки) и альбомы на сегодня. */
object HomeRepo {

    private suspend fun <T> safe(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        default
    }

    suspend fun load(api: SubsonicApi, store: SessionStore): HomeData = coroutineScope {
        val recent = async { safe<List<Album>>(emptyList()) { loadRecent(api) } }
        val mixes = async { safe<List<Mix>>(emptyList()) { loadMixes(api, store) } }
        val today = async { safe<List<Album>>(emptyList()) { loadAlbumsToday(api, store) } }
        val artists = async {
            safe<List<Artist>>(emptyList()) { api.artists().shuffled().take(14) }
        }
        HomeData(recent.await(), mixes.await(), today.await(), artists.await())
    }

    private suspend fun loadRecent(api: SubsonicApi): List<Album> {
        val recent = api.albumList("recent", 5)
        if (recent.size >= 5) return recent
        return (recent + api.albumList("newest", 12)).distinctBy { it.id }.take(5)
    }

    private suspend fun loadAlbumsToday(api: SubsonicApi, store: SessionStore): List<Album> {
        store.cached("albums")?.let { s -> return JSONArray(s).mapObjs { it.toAlbum() }.filterNot { it.isPlaceholder() } }
        val albums = api.albumList("random", 12)
        if (albums.isNotEmpty()) store.putCache("albums", JSONArray(albums.map { it.toJson() }).toString())
        return albums
    }

    private suspend fun loadMixes(api: SubsonicApi, store: SessionStore): List<Mix> {
        store.cached("mixes")?.let { s -> return JSONArray(s).mapObjs { it.toMix() } }

        val result = ArrayList<Mix>()
        val discover = api.randomSongs(30)
        if (discover.isNotEmpty()) result.add(Mix("discover", "a different handful every day", discover))

        val genres = api.genres()
            .filter { it.first.isNotBlank() && it.second >= 6 }
            .sortedByDescending { it.second }
            .take(3)
        for ((name, _) in genres) {
            val songs = api.randomSongs(30, name)
            if (songs.size >= 6) result.add(Mix("${name.lowercase()} mix", artistsLine(songs), songs))
        }
        if (result.size < 2) {
            val extra = api.randomSongs(30)
            if (extra.isNotEmpty()) result.add(Mix("daily mix", artistsLine(extra), extra))
        }
        if (result.isNotEmpty()) store.putCache("mixes", JSONArray(result.map { it.toJson() }).toString())
        return result
    }

    private fun artistsLine(songs: List<Song>): String =
        songs.map { it.artist }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(3)
            .joinToString(", ") { it.key }
}
