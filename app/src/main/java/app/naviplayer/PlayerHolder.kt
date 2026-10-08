package app.naviplayer

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Цвета плеера, подобранные под обложку текущего трека. */
data class NowColors(val container: Color, val accent: Color, val top: Color, val bottom: Color) {
    companion object {
        val Default = from(0xFFB0485A.toInt())

        fun from(rgb: Int): NowColors {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(rgb, hsl)
            val h = hsl[0]
            val k = if (hsl[1] < 0.12f) 0.15f else 1f // серые обложки не красим в яркий цвет
            fun c(s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h, s * k, l)))
            return NowColors(
                container = c(0.38f, 0.38f),
                accent = c(0.55f, 0.64f),
                top = c(0.36f, 0.31f),
                bottom = c(0.42f, 0.26f),
            )
        }
    }
}

/** Мост между Compose и MediaController: всё, что читает UI, лежит в Compose-state. */
class PlayerHolder(private val context: Context) {
    private var controller: MediaController? = null
    @Volatile private var released = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var colorJob: Job? = null
    private var pending: (() -> Unit)? = null   // команда, пришедшая до подключения к сервису
    private var scrobbledId: String? = null

    var api: SubsonicApi? = null

    var current by mutableStateOf<MediaItem?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var currentIndex by mutableIntStateOf(0); private set
    var contextTitle by mutableStateOf(""); private set
    var colors by mutableStateOf(NowColors.Default); private set

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            // The service may still be starting (or Android may kill it). A failed future must not
            // crash the UI thread, and a late connection must not leak after Activity destruction.
            val c = try { future.get() } catch (_: Exception) { return@addListener }
            if (released) { c.release(); return@addListener }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    current = item
                    currentIndex = c.currentMediaItemIndex
                    scrobbledId = null
                    tick()
                    updateColors(item)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { this@PlayerHolder.isPlaying = isPlaying }
                override fun onPlaybackStateChanged(state: Int) = tick()
                override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
                override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
            })
            current = c.currentMediaItem
            currentIndex = c.currentMediaItemIndex
            isPlaying = c.isPlaying
            shuffle = c.shuffleModeEnabled
            repeatMode = c.repeatMode
            updateColors(current)
            pending?.invoke()
            pending = null
        }, ContextCompat.getMainExecutor(context))

        // прослушивание засчитываем, когда трек реально играет 30 сек (или половину), а не при скипе
        scope.launch {
            while (true) {
                delay(5_000)
                if (isPlaying) maybeScrobble()
            }
        }
    }

    private fun maybeScrobble() {
        val c = controller ?: return
        val id = current?.mediaId ?: return
        if (id == scrobbledId) return
        val dur = c.duration
        val need = if (dur > 0) minOf(30_000L, dur / 2) else 30_000L
        if (c.currentPosition < need) return
        scrobbledId = id
        val a = api ?: return
        scope.launch { try { a.scrobble(id) } catch (e: Exception) { } }
    }

    private fun updateColors(item: MediaItem?) {
        colorJob?.cancel()
        val url = item?.mediaMetadata?.artworkUri?.toString()
        if (url == null) { colors = NowColors.Default; return }
        colorJob = scope.launch {
            val c = withContext(Dispatchers.IO) { extractColors(url) }
            if (c != null) colors = c
        }
    }

    private suspend fun extractColors(url: String): NowColors? = try {
        val req = ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build()
        val bmp = (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
        if (bmp == null) null else {
            val p = Palette.from(bmp).maximumColorCount(24).generate()
            // берём не "самый яркий" оттенок, а тот, который занимает много места и достаточно цветной
            val best = p.swatches
                .filter { it.hsl[1] >= 0.12f && it.hsl[2] in 0.12f..0.88f }
                .maxByOrNull { it.population * (0.25f + it.hsl[1]) }
            val rgb = best?.rgb ?: p.dominantSwatch?.rgb
            if (rgb == null) null else NowColors.from(rgb)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun play(songs: List<Song>, index: Int, api: SubsonicApi, from: String, shuffled: Boolean = false) {
        if (songs.isEmpty() || released) return
        this.api = api
        val c = controller
        if (c == null) { pending = { play(songs, index, api, from, shuffled) }; return }
        contextTitle = from
        c.shuffleModeEnabled = shuffled
        // Слишком большая очередь дорого сериализуется через MediaController и ощущается
        // как зависание после нажатия. 250 треков — много часов музыки, но стартует заметно быстрее.
        val safeIndex = index.coerceIn(songs.indices)
        val big = songs.size > 250
        val queue = if (big) songs.drop(safeIndex).take(250) else songs
        c.setMediaItems(queue.map { it.toMediaItem(api) }, if (big) 0 else safeIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms); position = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun queue(): List<MediaItem> = controller?.let { c -> List(c.mediaItemCount) { c.getMediaItemAt(it) } } ?: emptyList()
    fun skipTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }

    fun tick() {
        controller?.let {
            position = it.currentPosition.coerceAtLeast(0)
            duration = it.duration.coerceAtLeast(0)
        }
    }

    fun release() {
        released = true
        pending = null
        colorJob?.cancel()
        controller?.release()
        controller = null
        scope.cancel()
    }
}

fun Song.toMediaItem(api: SubsonicApi): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(api.streamUrl(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(coverArt?.let { Uri.parse(api.coverUrl(it)) })
                .build()
        )
        .build()
