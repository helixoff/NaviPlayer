package app.naviplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MiniPlayer(holder: PlayerHolder, onOpen: () -> Unit) {
    val meta = holder.current?.mediaMetadata
    val col = holder.colors
    val progress = if (holder.duration > 0) holder.position.toFloat() / holder.duration else 0f
    val smoothProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(ProgressAnimationMillis, easing = WavyProgressEasing),
        label = "miniProgress",
    )
    val wavePhase = rememberWavePhase(holder.isPlaying)

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(28.dp)).background(col.container).clickable(onClick = onOpen)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -50f) onOpen() },
                ) { _, dy -> total += dy }
            },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverUrl(meta?.artworkUri?.toString(), Modifier.size(52.dp), 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(meta?.title?.toString() ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta?.artist?.toString() ?: "", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(col.accent).clickable { holder.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (holder.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "play/pause", Modifier.size(26.dp), tint = Color(0xFF1E0A10))
            }
            IconButton(onClick = { holder.next() }) {
                Icon(Icons.Default.SkipNext, "next", Modifier.size(28.dp), tint = Color.White)
            }
        }
        Canvas(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp).height(12.dp),
        ) {
            drawWavyProgress(
                progress = smoothProgress.value,
                accent = col.accent,
                trackColor = Color.White.copy(alpha = 0.22f),
                strokeWidth = 2.dp.toPx(),
                amplitude = 1.6.dp.toPx(),
                wavelength = 26.dp.toPx(),
                gap = 5.dp.toPx(),
                stopRadius = 1.5.dp.toPx(),
                phase = wavePhase.floatValue,
            )
        }
    }
}

@Composable
private fun SeekBar(
    progress: Float,
    accent: Color,
    trackColor: Color,
    isPlaying: Boolean,
    isSeeking: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val smoothProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isSeeking) snap() else tween(ProgressAnimationMillis, easing = WavyProgressEasing),
        label = "playerProgress",
    )
    val wavePhase = rememberWavePhase(isPlaying)
    Canvas(
        Modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    change((o.x / size.width).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> change((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { pc, _ ->
                    pc.consume()
                    change((pc.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        drawWavyProgress(
            progress = smoothProgress.value,
            accent = accent,
            trackColor = trackColor,
            strokeWidth = 4.dp.toPx(),
            amplitude = 4.dp.toPx(),
            wavelength = 42.dp.toPx(),
            gap = 9.dp.toPx(),
            stopRadius = 2.2.dp.toPx(),
            phase = wavePhase.floatValue,
        )
    }
}

private const val ProgressAnimationMillis = 500
private val WavyProgressEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun rememberWavePhase(isAnimating: Boolean): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect

        var previousFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTime ->
                val elapsedSeconds = ((frameTime - previousFrame) / 1_000_000_000f)
                    .coerceIn(0f, 0.05f)
                phase.floatValue = (phase.floatValue + elapsedSeconds) % 1f
                previousFrame = frameTime
            }
        }
    }
    return phase
}

private fun DrawScope.drawWavyProgress(
    progress: Float,
    accent: Color,
    trackColor: Color,
    strokeWidth: Float,
    amplitude: Float,
    wavelength: Float,
    gap: Float,
    stopRadius: Float,
    phase: Float,
) {
    val centerY = size.height / 2f
    val endX = size.width * progress.coerceIn(0f, 1f)

    val trackEnd = size.width - stopRadius * 3f
    val trackStart = (endX + gap).coerceAtMost(trackEnd)
    if (trackStart < trackEnd) {
        drawLine(
            color = trackColor,
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
    if (endX < size.width - stopRadius * 1.5f) {
        drawCircle(accent, stopRadius, Offset(size.width - stopRadius, centerY))
    }

    if (endX > 0f) {
        val safeAmplitude = minOf(amplitude, size.height * 0.32f)
        val phaseRadians = phase * 2f * PI.toFloat()
        val amplitudeProgress = ((endX - strokeWidth) / (wavelength * 0.65f)).coerceIn(0f, 1f)
        val easedAmplitude = amplitudeProgress * amplitudeProgress * (3f - 2f * amplitudeProgress)
        val visibleAmplitude = safeAmplitude * easedAmplitude
        fun waveY(x: Float) = centerY +
            sin(x / wavelength * 2f * PI.toFloat() - phaseRadians) * visibleAmplitude

        val path = Path().apply {
            moveTo(0f, waveY(0f))
            val step = 1.5.dp.toPx().coerceAtLeast(1f)
            var x = step
            while (x < endX) {
                lineTo(x, waveY(x))
                x += step
            }
            lineTo(endX, waveY(endX))
        }
        drawPath(path, accent, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(api: SubsonicApi, holder: PlayerHolder, onBack: () -> Unit) {
    val col = holder.colors
    val meta = holder.current?.mediaMetadata
    val songId = holder.current?.mediaId
    var dragging by remember { mutableStateOf<Float?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragActive by remember { mutableStateOf(false) }
    val shownY by animateFloatAsState(targetValue = dragY, animationSpec = if (dragActive) snap() else spring(), label = "dragY")
    val closeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dragY > 0f && available.y < 0f) {
                    val d = maxOf(available.y, -dragY)
                    dragY += d
                    return Offset(0f, d)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    dragActive = true
                    dragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                dragActive = false
                if (dragY > 260f || available.y > 1800f) onBack() else dragY = 0f
                return available
            }
        }
    }
    val progress = if (holder.duration > 0) holder.position.toFloat() / holder.duration else 0f
    val white = Color.White

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, shownY.roundToInt()) }
            .nestedScroll(closeConnection)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) },
                onDragStarted = { dragActive = true },
                onDragStopped = { v ->
                    dragActive = false
                    if (dragY > 260f || v > 1800f) onBack() else dragY = 0f
                },
            )
            .background(Brush.verticalGradient(listOf(col.top, col.container, col.bottom))),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "close", Modifier.size(32.dp), tint = white) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("playing from", color = white.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Text(holder.contextTitle, color = white, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.size(48.dp))
            }

            if (showLyrics) {
                LyricsPane(api, songId, holder, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
            } else {
                PlayerArtwork(meta?.artworkUri?.toString(), col, Modifier.weight(1f))
            }

            TrackHeading(meta?.title?.toString().orEmpty(), meta?.artist?.toString().orEmpty())

            Spacer(Modifier.height(6.dp))
            SeekBar(
                progress = dragging ?: progress,
                accent = col.accent,
                trackColor = white.copy(alpha = 0.22f),
                isPlaying = holder.isPlaying,
                isSeeking = dragging != null,
                onChange = { dragging = it },
                onFinish = {
                    dragging?.let { holder.seekTo((it * holder.duration).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(dragging?.let { (it * holder.duration).toLong() } ?: holder.position), color = white, style = MaterialTheme.typography.labelMedium)
                Text(fmt(holder.duration), color = white, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(10.dp))
            TransportControls(holder, col.accent, Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(10.dp))
            PlayerActionBar(
                holder = holder,
                accent = col.accent,
                showLyrics = showLyrics,
                onToggleLyrics = { showLyrics = !showLyrics },
                onOpenQueue = { sheet = "queue" },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (sheet != null) {
            ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Pal.CardLow) {
                Text("queue", Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                QueueList(holder) { sheet = null }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PlayerArtwork(url: String?, colors: NowColors, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize().offset(y = 18.dp)
                    .graphicsLayer { scaleX = 0.88f; scaleY = 0.88f; alpha = 0.56f }
                    .blur(30.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(colors.accent, RoundedCornerShape(38.dp)),
            )
            CoverUrl(url, Modifier.matchParentSize(), 32.dp)
        }
    }
}

@Composable
private fun TrackHeading(title: String, artist: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            artist,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransportControls(holder: PlayerHolder, accent: Color, modifier: Modifier = Modifier) {
    val secondaryColor = Color.White.copy(alpha = 0.14f)

    Row(
        modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerButton(
            icon = Icons.Default.SkipPrevious,
            description = "previous",
            background = secondaryColor,
            modifier = Modifier.weight(1f),
            onClick = holder::previous,
        )
        PlayerButton(
            icon = if (holder.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            description = "play/pause",
            background = accent,
            tint = Color(0xFF1E0A10),
            iconSize = 36.dp,
            modifier = Modifier.weight(1.55f),
            onClick = holder::togglePlay,
        )
        PlayerButton(
            icon = Icons.Default.SkipNext,
            description = "next",
            background = secondaryColor,
            modifier = Modifier.weight(1f),
            onClick = holder::next,
        )
    }
}

@Composable
private fun PlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp = 30.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
private fun PlayerActionBar(
    holder: PlayerHolder,
    accent: Color,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(36.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = holder::toggleShuffle, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Shuffle, "shuffle", tint = if (holder.shuffle) accent else Color.White)
        }
        IconButton(onClick = holder::cycleRepeat, modifier = Modifier.size(44.dp)) {
            Icon(
                if (holder.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "repeat",
                tint = if (holder.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White,
            )
        }
        IconButton(onClick = onToggleLyrics, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.Subject, "lyrics", tint = if (showLyrics) accent else Color.White)
        }
        IconButton(onClick = onOpenQueue, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, "queue", tint = Color.White)
        }
    }
}

@Composable
private fun QueueList(holder: PlayerHolder, onPicked: () -> Unit) {
    val items = remember(holder.current, holder.currentIndex) { holder.queue() }
    LazyColumn(Modifier.heightIn(max = 460.dp)) {
        itemsIndexed(items) { i, item ->
            val playing = i == holder.currentIndex
            Row(
                Modifier.fillMaxWidth().clickable { holder.skipTo(i); onPicked() }.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverUrl(item.mediaMetadata.artworkUri?.toString(), Modifier.size(44.dp), 10.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = if (playing) Pal.Accent else Pal.Text,
                    )
                    Text(item.mediaMetadata.artist?.toString() ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Subtle)
                }
                if (playing) Icon(Icons.Default.GraphicEq, null, tint = Pal.Accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LyricsPane(api: SubsonicApi, songId: String?, holder: PlayerHolder, modifier: Modifier) {
    var lyrics by remember(songId) { mutableStateOf<Lyrics?>(null) }
    var done by remember(songId) { mutableStateOf(false) }
    LaunchedEffect(songId) {
        lyrics = if (songId == null) null else api.lyricsStructured(songId)
        done = true
    }
    val ly = lyrics
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!done) {
            CircularProgressIndicator(color = Color.White)
        } else if (ly == null) {
            Text("no lyrics for this track", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
        } else {
            val listState = rememberLazyListState()
            val pos = holder.position + 250
            val current = if (ly.synced) ly.lines.indexOfLast { it.startMs <= pos } else -1
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem(current, -(listState.layoutInfo.viewportSize.height / 4))
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 24.dp)) {
                itemsIndexed(ly.lines) { i, line ->
                    val color by animateColorAsState(
                        if (!ly.synced || i == current) Color.White else Color.White.copy(alpha = 0.42f),
                        label = "lyric",
                    )
                    Text(
                        line.text.ifBlank { "♪" },
                        color = color, fontSize = 28.sp, lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = ly.synced) { holder.seekTo(line.startMs) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
