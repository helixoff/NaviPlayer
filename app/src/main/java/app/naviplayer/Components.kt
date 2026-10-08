package app.naviplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ───────── навигация ─────────

sealed interface Dest {
    data class AlbumD(val id: String, val title: String, val artist: String, val cover: String?) : Dest
    data class PlaylistD(val id: String, val name: String) : Dest
    data class ArtistD(val id: String, val name: String) : Dest
    data class MixD(val mix: Mix) : Dest
    data object AllTracks : Dest
    data object Loose : Dest
    data object Profile : Dest
}

fun Album.dest() = Dest.AlbumD(id, name, artist, coverArt)

// ───────── мелочи ─────────

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun timeLabel(): String {
    val h = java.time.LocalTime.now().hour
    return when {
        h < 5 -> "late night"
        h < 12 -> "good morning"
        h < 17 -> "good afternoon"
        h < 22 -> "good evening"
        else -> "late night"
    }
}

/** Единые отступы и радиусы, чтобы все экраны выглядели одинаково. */
object Dim {
    val Page = 20.dp
    val RowCover = 60.dp
    val RowRadius = 18.dp
    val CardRadius = 28.dp
    val HeroRadius = 36.dp
}

/** Quiet two-tone canvas shared by every screen. It keeps Material You colours without a flat backdrop. */
@Composable
fun NaviBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(
                        cs.primaryContainer.copy(alpha = 0.14f).compositeOver(cs.surface),
                        cs.surface,
                        cs.tertiaryContainer.copy(alpha = 0.08f).compositeOver(cs.surface),
                    ),
                ),
            ),
            content = content,
        )
    }
}

/** Signature asymmetric silhouette used by the expressive surfaces. */
fun expressiveShape(large: Dp = 36.dp, small: Dp = 14.dp): Shape =
    RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = small, bottomStart = large)

/** Круг с волнистым краем (аватар профиля). */
class ScallopShape(private val lobes: Int = 8, private val amp: Float = 0.05f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val steps = 240
        for (i in 0..steps) {
            val t = i.toFloat() / steps * 2f * PI.toFloat()
            val rr = r * (1f - amp + amp * cos(lobes * t))
            val x = cx + rr * cos(t)
            val y = cy + rr * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

// ───────── обложки ─────────

@Composable
fun CoverUrl(url: String?, modifier: Modifier, radius: Dp = Dim.RowRadius) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Pal.Disc),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Album, null, tint = Pal.Accent.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}

@Composable
fun Cover(api: SubsonicApi, coverArt: String?, modifier: Modifier, radius: Dp = Dim.RowRadius) =
    CoverUrl(coverArt?.let { api.coverUrl(it) }, modifier, radius)

/** Мозаика 2x2 из обложек (для миксов и плейлистов). */
@Composable
fun Mosaic(api: SubsonicApi, ids: List<String?>, modifier: Modifier, radius: Dp = Dim.CardRadius) {
    val covers = ids.filterNotNull()
    val cells = when (covers.size) {
        0 -> List<String?>(4) { null }
        1 -> List(4) { covers[0] }
        2 -> listOf(covers[0], covers[1], covers[1], covers[0])
        3 -> listOf(covers[0], covers[1], covers[2], covers[0])
        else -> covers.take(4)
    }
    Column(modifier.clip(RoundedCornerShape(radius))) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..1) {
                    val id = cells[row * 2 + col]
                    Box(Modifier.weight(1f).fillMaxHeight().background(Pal.Disc), contentAlignment = Alignment.Center) {
                        if (id != null) {
                            AsyncImage(model = api.coverUrl(id, 300), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Album, null, tint = Pal.Accent.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.4f))
                        }
                    }
                }
            }
        }
    }
}

/** Квадрат с иконкой — основной контейнер темы. */
@Composable
fun IconBox(icon: ImageVector, size: Dp, radius: Dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.primary.copy(alpha = 0.55f).compositeOver(cs.primaryContainer)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(size * 0.45f))
    }
}

/** Круглый аватар с первой буквой (артист без картинки, профиль). */
@Composable
fun LetterAvatar(text: String, size: Dp, shape: Shape = RoundedCornerShape(50)) {
    Box(Modifier.size(size).clip(shape).background(Pal.Avatar), contentAlignment = Alignment.Center) {
        Text(
            text.take(1).uppercase(), color = Pal.OnAvatar,
            style = if (size > 120.dp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
        )
    }
}

/** User-selected picture with the letter avatar kept as a reliable fallback. */
@Composable
fun UserAvatar(
    user: String,
    avatarUri: String?,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(50),
    large: Boolean = false,
) {
    var imageFailed by remember(avatarUri) { mutableStateOf(false) }
    Box(modifier.clip(shape).background(Pal.Avatar), contentAlignment = Alignment.Center) {
        if (!avatarUri.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else {
            Text(
                user.take(1).uppercase(),
                color = Pal.OnAvatar,
                style = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
            )
        }
    }
}


// ───────── заголовки ─────────

@Composable
fun SectionHeader(text: String) =
    Row(
        Modifier.fillMaxWidth().padding(start = Dim.Page, end = Dim.Page, top = 32.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Pal.Text, style = MaterialTheme.typography.titleLarge)
    }

@Composable
fun PageTitle(text: String) =
    Text(text, Modifier.padding(horizontal = Dim.Page, vertical = 12.dp), style = MaterialTheme.typography.displaySmall)

/** Верх вложенного экрана: кнопка «назад» в тональном кружке + опциональные действия справа. */
@Composable
fun ScreenTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Тональная карточка-контейнер (MD3 surfaceContainerHigh). */
@Composable
fun TonalCard(modifier: Modifier = Modifier, radius: Dp = Dim.CardRadius, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    val base = modifier.clip(shape).background(Pal.Card)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

/** Текст-подсказка «ничего нет» одинакового вида на всех экранах. */
@Composable
fun EmptyHint(text: String) =
    Text(text, Modifier.padding(Dim.Page), color = Pal.Subtle, style = MaterialTheme.typography.bodyLarge)

@Composable
fun MessageCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Dim.Page, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = Pal.Card,
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.Start) {
            IconBox(icon, 54.dp, 18.dp)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** Static loading shapes: cheap on low-end phones and less visually jumpy than a lone spinner. */
@Composable
fun LoadingCards() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.Page, vertical = 18.dp)) {
        repeat(3) { i ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(if (i == 0) 70.dp else 58.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Pal.Disc),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i == 1) 0.64f else 0.82f).height(15.dp).clip(RoundedCornerShape(50)).background(Pal.Card))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(0.46f).height(11.dp).clip(RoundedCornerShape(50)).background(Pal.CardLow))
                }
            }
        }
    }
}

// ───────── строки и карточки ─────────

/** Базовая строка списка: слева произвольный «ведущий» элемент, заголовок, подзаголовок, справа трейлинг. */
@Composable
fun BaseRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    titleColor: Color = Pal.Text,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dim.Page, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = titleColor, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun ListRow(api: SubsonicApi, cover: String?, title: String, subtitle: String, onClick: () -> Unit) =
    BaseRow(title, subtitle, onClick, leading = { Cover(api, cover, Modifier.size(Dim.RowCover)) })

@Composable
fun SongRow(api: SubsonicApi, song: Song, playing: Boolean, number: Int? = null, onClick: () -> Unit) {
    BaseRow(
        title = song.title,
        subtitle = song.artist,
        onClick = onClick,
        titleColor = if (playing) Pal.Accent else Pal.Text,
        leading = {
            if (number != null) {
                Text("$number", Modifier.width(36.dp), color = if (playing) Pal.Accent else Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Cover(api, song.coverArt, Modifier.size(Dim.RowCover))
                    if (playing) {
                        Surface(color = Pal.Accent, shape = RoundedCornerShape(50), modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.GraphicEq, null, tint = Pal.AccentDark, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        },
        trailing = { Text(fmt(song.durationSec * 1000L), color = Pal.Subtle, style = MaterialTheme.typography.labelMedium) },
    )
}

@Composable
fun AlbumCard(api: SubsonicApi, album: Album, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Cover(api, album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), Dim.CardRadius)
        Spacer(Modifier.height(8.dp))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text, style = MaterialTheme.typography.titleSmall)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Subtle, style = MaterialTheme.typography.bodySmall)
    }
}
