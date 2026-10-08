package app.naviplayer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Офлайн-запас, как «smart cache» в Spotify: всё, что играет, оседает в кэше на диске,
 * а следующие треки очереди докачиваются заранее. Пропал интернет — текущий трек и
 * ещё несколько следующих продолжают играть; недавно слушанное тоже доступно без сети.
 */
@OptIn(UnstableApi::class)
object TrackCache {
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024   // 2 ГБ, старое вытесняется само
    const val PREFETCH_AHEAD = 5                            // сколько следующих треков качать заранее

    @Volatile private var cache: SimpleCache? = null

    /** Ключ кэша — id трека, а не URL: токен/соль в URL могут поменяться, трек тот же. */
    private val keyFactory = CacheKeyFactory { spec ->
        spec.uri.getQueryParameter("id")?.let { "track:$it" } ?: spec.uri.toString()
    }

    @Synchronized
    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val dir = File(context.cacheDir, "tracks")
        return SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES), StandaloneDatabaseProvider(context)).also { cache = it }
    }

    private fun upstream(): DataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("NaviPlayer")
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

    /** Источник данных для плеера: читает из кэша, чего нет — качает и кладёт в кэш. */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream())
            .setCacheKeyFactory(keyFactory)
            // ошибка кэша не должна ломать воспроизведение; без FLAG_BLOCK_ON_CACHE плеер не ждёт предзагрузчик, если тот пишет тот же трек
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // ───────── предзагрузка следующих треков ─────────

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "TrackPrefetch").apply { isDaemon = true } }
    private var job: Future<*>? = null
    private var writer: CacheWriter? = null

    /** Вызывать при смене трека: качает следующие [PREFETCH_AHEAD] треков очереди с учётом shuffle/repeat. */
    fun prefetchAround(context: Context, player: Player) {
        val uris = nextUris(player, PREFETCH_AHEAD)
        synchronized(this) {
            job?.cancel(true)
            writer?.cancel()
            writer = null
        }
        if (uris.isEmpty()) return
        val factory = dataSourceFactory(context) as CacheDataSource.Factory
        job = executor.submit {
            // Текущему треку хватает короткой форы; следующий должен успеть попасть в кэш
            // до нажатия Skip, иначе пользователь видит лишнюю сетевую паузу.
            try { Thread.sleep(750) } catch (e: InterruptedException) { return@submit }
            for (uri in uris) {
                if (Thread.currentThread().isInterrupted) return@submit
                val spec = DataSpec.Builder().setUri(uri).setKey(keyFactory.buildCacheKey(DataSpec(uri))).build()
                val w = CacheWriter(factory.createDataSource(), spec, null, null)
                synchronized(this) { writer = w }
                try { w.cache() } catch (e: Exception) { /* нет сети или отменили — попробуем на следующем треке */ }
            }
        }
    }

    private fun nextUris(player: Player, count: Int): List<Uri> {
        val timeline: Timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<Uri>(count)
        var idx = player.currentMediaItemIndex
        val window = Timeline.Window()
        // при «повторе одного» всё равно греем следующие — пользователь скорее всего скоро его выключит
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        repeat(count) {
            idx = timeline.getNextWindowIndex(idx, repeat, player.shuffleModeEnabled)
            if (idx == C.INDEX_UNSET) return out
            timeline.getWindow(idx, window).mediaItem.localConfiguration?.uri?.let { out.add(it) }
        }
        return out
    }
}
