@file:OptIn(ExperimentalMaterial3Api::class)

package app.naviplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DesktopPage { Home, Search, Library, Settings }

private const val UPDATE_CHECK_INTERVAL_MS = 15 * 60 * 1_000L

private enum class TrackCollection(val title: String, val subtitle: String) {
    AllTracks("All tracks", "One shuffle for this app session"),
    LooseTracks("Loose tracks", "Same app-session order, without proper album tags"),
}

private data class ShellTarget(
    val page: DesktopPage,
    val album: Album?,
    val playlist: Playlist?,
    val collection: TrackCollection?,
    val loading: Boolean,
)

@Composable
fun DesktopApp(
    store: SessionStore,
    player: DesktopPlayer,
    appearance: AppearanceSettings,
    onExitForUpdate: () -> Unit,
    onAppearanceChange: (AppearanceSettings) -> Unit,
) {
    var credentials by remember { mutableStateOf(store.load()) }
    val current = credentials
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (current == null) {
            LoginScreen { value ->
                store.save(value)
                credentials = value
            }
        } else {
            val client = remember(current) { SubsonicClient(current, store.salt()) }
            PlayerShell(
                client = client,
                credentials = current,
                player = player,
                appearance = appearance,
                onExitForUpdate = onExitForUpdate,
                onAppearanceChange = onAppearanceChange,
                onLogout = {
                    player.close()
                    store.clear()
                    credentials = null
                },
            )
        }
    }
}

@Composable
private fun LoginScreen(onLogin: (Credentials) -> Unit) {
    var server by remember { mutableStateOf("http://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = .22f),
                    MaterialTheme.colorScheme.background,
                ),
                radius = 900f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(520.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp,
            shadowElevation = 20.dp,
        ) {
            Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(Modifier.size(72.dp), RoundedCornerShape(24.dp), MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(34.dp))
                    }
                }
                Text("NaviPlayer", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Your Navidrome library, at home on the desktop.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    password,
                    { password = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !busy && server.length > 7 && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        val value = Credentials(server.trim(), username.trim(), password)
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                SubsonicClient(value, SubsonicClient.newSalt()).ping()
                                onLogin(value)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not connect"
                                busy = false
                            }
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun PlayerShell(
    client: SubsonicClient,
    credentials: Credentials,
    player: DesktopPlayer,
    appearance: AppearanceSettings,
    onExitForUpdate: () -> Unit,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onLogout: () -> Unit,
) {
    var page by remember { mutableStateOf(DesktopPage.Home) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var suggested by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedCollection by remember { mutableStateOf<TrackCollection?>(null) }
    var albumSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var collectionSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var collectionLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var libraryLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val updater = remember { DesktopUpdater() }
    val scope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<DesktopUpdate?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }

    suspend fun refreshUpdates() {
        if (updateChecking) return
        updateChecking = true
        updateError = null
        runCatching { updater.check() }
            .onSuccess { availableUpdate = it }
            .onFailure { updateError = it.message ?: "Could not check for updates" }
        updateChecking = false
    }

    LaunchedEffect(updater) {
        while (true) {
            refreshUpdates()
            delay(UPDATE_CHECK_INTERVAL_MS)
        }
    }

    LaunchedEffect(client) {
        loading = true
        try {
            coroutineScope {
                val newAlbums = async { client.newestAlbums() }
                val sessionQueue = async { client.allSongs().shuffled() }
                albums = newAlbums.await()
                // Один порядок создаётся при входе и живёт до полного закрытия приложения.
                suggested = sessionQueue.await()
            }
        } catch (failure: Exception) {
            error = failure.message
        } finally {
            loading = false
        }
        libraryLoading = true
        try {
            coroutineScope {
                val libraryAlbums = async { client.albumsAlphabetical() }
                val serverPlaylists = async { client.playlists() }
                allAlbums = libraryAlbums.await()
                playlists = serverPlaylists.await()
            }
        } catch (failure: Exception) {
            error = failure.message
        } finally {
            libraryLoading = false
        }
    }

    LaunchedEffect(selectedAlbum) {
        selectedAlbum?.let { album ->
            albumSongs = emptyList()
            albumSongs = runCatching { client.albumSongs(album.id) }.getOrElse {
                error = it.message
                emptyList()
            }
        }
    }

    LaunchedEffect(selectedPlaylist) {
        selectedPlaylist?.let { playlist ->
            playlistSongs = emptyList()
            playlistSongs = runCatching { client.playlistSongs(playlist.id) }.getOrElse {
                error = it.message
                emptyList()
            }
        }
    }

    LaunchedEffect(selectedCollection, suggested) {
        val collection = selectedCollection ?: return@LaunchedEffect
        collectionLoading = true
        collectionSongs = emptyList()
        collectionSongs = runCatching {
            when (collection) {
                TrackCollection.AllTracks -> suggested
                TrackCollection.LooseTracks -> suggested.filter(Song::isLoose)
            }
        }.getOrElse {
            error = it.message
            emptyList()
        }
        collectionLoading = false
    }

    val shellBackground = if (appearance.ambientGlow) {
        Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.background,
            ),
            radius = 1200f,
        )
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    }

    Column(Modifier.fillMaxSize().background(shellBackground)) {
        availableUpdate?.let { update ->
            DesktopUpdateBanner(
                update = update,
                busy = updateBusy,
                error = updateError,
                onDismiss = { availableUpdate = null },
                onUpdate = {
                    if (!updateBusy) scope.launch {
                        updateBusy = true
                        updateError = null
                        runCatching { updater.downloadAndInstall(update) }
                            .onSuccess { onExitForUpdate() }
                            .onFailure { updateError = it.message ?: "Could not start the installer" }
                        updateBusy = false
                    }
                },
                onReleasePage = { updater.openRelease(update) },
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().width(96.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                header = {
                    Surface(Modifier.padding(vertical = 22.dp).size(50.dp), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null) }
                    }
                },
            ) {
                NavigationRailItem(
                    selected = page == DesktopPage.Home && selectedAlbum == null && selectedPlaylist == null && selectedCollection == null,
                    onClick = { selectedAlbum = null; selectedPlaylist = null; selectedCollection = null; page = DesktopPage.Home },
                    icon = { Icon(if (page == DesktopPage.Home) Icons.Default.Home else Icons.Outlined.Home, null) },
                    label = { Text("Home") },
                )
                NavigationRailItem(
                    selected = page == DesktopPage.Search,
                    onClick = { selectedAlbum = null; selectedPlaylist = null; selectedCollection = null; page = DesktopPage.Search },
                    icon = { Icon(if (page == DesktopPage.Search) Icons.Default.Search else Icons.Outlined.Search, null) },
                    label = { Text("Search") },
                )
                NavigationRailItem(
                    selected = page == DesktopPage.Library && selectedAlbum == null && selectedPlaylist == null && selectedCollection == null,
                    onClick = { selectedAlbum = null; selectedPlaylist = null; selectedCollection = null; page = DesktopPage.Library },
                    icon = {
                        Icon(
                            if (page == DesktopPage.Library) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                            null,
                        )
                    },
                    label = { Text("Library") },
                )
                Spacer(Modifier.weight(1f))
                NavigationRailItem(
                    selected = page == DesktopPage.Settings,
                    onClick = { selectedAlbum = null; selectedPlaylist = null; selectedCollection = null; page = DesktopPage.Settings },
                    icon = {
                        Icon(
                            if (page == DesktopPage.Settings) Icons.Default.Settings else Icons.Outlined.Settings,
                            null,
                        )
                    },
                    label = { Text("Style") },
                )
                IconButton(onClick = onLogout, modifier = Modifier.padding(bottom = 18.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Log out")
                }
            }

            AnimatedContent(
                targetState = ShellTarget(
                    page,
                    selectedAlbum,
                    selectedPlaylist,
                    selectedCollection,
                    loading || (page == DesktopPage.Library && libraryLoading) ||
                        (selectedCollection != null && collectionLoading),
                ),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
            ) { target ->
                when {
                    target.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    target.album != null -> AlbumView(target.album, albumSongs, client, player) { selectedAlbum = null }
                    target.playlist != null -> PlaylistView(target.playlist, playlistSongs, client, player) { selectedPlaylist = null }
                    target.collection != null -> TrackCollectionView(
                        collection = target.collection,
                        songs = collectionSongs,
                        client = client,
                        player = player,
                        onBack = { selectedCollection = null },
                    )
                    target.page == DesktopPage.Home -> HomeView(
                        username = credentials.username,
                        albums = albums,
                        suggested = suggested,
                        client = client,
                        player = player,
                        onAlbum = { selectedPlaylist = null; selectedCollection = null; selectedAlbum = it },
                    )
                    target.page == DesktopPage.Search -> SearchView(client, player, onAlbum = {
                        selectedPlaylist = null
                        selectedCollection = null
                        selectedAlbum = it
                    })
                    target.page == DesktopPage.Library -> LibraryView(
                        albums = allAlbums,
                        playlists = playlists,
                        client = client,
                        onAlbum = { selectedPlaylist = null; selectedCollection = null; selectedAlbum = it },
                        onPlaylist = { selectedAlbum = null; selectedCollection = null; selectedPlaylist = it },
                        onCollection = { selectedAlbum = null; selectedPlaylist = null; selectedCollection = it },
                    )
                    else -> AppearanceView(
                        settings = appearance,
                        onChange = onAppearanceChange,
                        currentVersion = updater.currentVersion,
                        availableUpdate = availableUpdate,
                        checkingUpdate = updateChecking,
                        installingUpdate = updateBusy,
                        updateError = updateError,
                        onCheckUpdate = { scope.launch { refreshUpdates() } },
                        onInstallUpdate = {
                            val update = availableUpdate
                            if (update != null && !updateBusy) scope.launch {
                                updateBusy = true
                                updateError = null
                                runCatching { updater.downloadAndInstall(update) }
                                    .onSuccess { onExitForUpdate() }
                                    .onFailure { updateError = it.message ?: "Could not start the installer" }
                                updateBusy = false
                            }
                        },
                    )
                }
            }
        }
        player.current?.let { song ->
            Column {
                if (showLyrics) {
                    DesktopLyricsPane(
                        client = client,
                        song = song,
                        player = player,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
                    )
                }
                PlayerBar(
                    client = client,
                    player = player,
                    showLyrics = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                )
            }
        }
        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun DesktopUpdateBanner(
    update: DesktopUpdate,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onReleasePage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("NaviPlayer ${update.version} is ready", fontWeight = FontWeight.Bold)
                Text(
                    error ?: "Download, install with system authentication, then restart NaviPlayer.",
                    color = if (error == null) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f)
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (error != null) Button(onClick = onReleasePage) { Text("Release page") }
            Button(onClick = onUpdate, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Update")
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss") }
        }
    }
}

@Composable
private fun AppearanceView(
    settings: AppearanceSettings,
    onChange: (AppearanceSettings) -> Unit,
    currentVersion: String,
    availableUpdate: DesktopUpdate?,
    checkingUpdate: Boolean,
    installingUpdate: Boolean,
    updateError: String?,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Text("Appearance", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Make NaviPlayer feel at home on your desktop.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            availableUpdate?.let { "NaviPlayer ${it.version} is available" }
                                ?: "NaviPlayer $currentVersion is up to date",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            updateError ?: if (availableUpdate == null) {
                                "Checks automatically while the app is open."
                            } else {
                                "Install it here and restart NaviPlayer automatically."
                            },
                            color = if (updateError == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = onCheckUpdate, enabled = !checkingUpdate && !installingUpdate) {
                        if (checkingUpdate) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Check now")
                    }
                    if (availableUpdate != null) {
                        Button(onClick = onInstallUpdate, enabled = !installingUpdate && !checkingUpdate) {
                            if (installingUpdate) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Update")
                        }
                    }
                }
            }
        }
        item {
            Text("Surface", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SurfaceChoice(
                    title = "Deep night",
                    subtitle = "Soft charcoal layers",
                    color = Color(0xFF17141C),
                    selected = settings.surfaceStyle == DesktopSurfaceStyle.Night,
                    onClick = { onChange(settings.copy(surfaceStyle = DesktopSurfaceStyle.Night)) },
                )
                SurfaceChoice(
                    title = "AMOLED",
                    subtitle = "True black background",
                    color = Color.Black,
                    selected = settings.surfaceStyle == DesktopSurfaceStyle.Amoled,
                    onClick = { onChange(settings.copy(surfaceStyle = DesktopSurfaceStyle.Amoled)) },
                )
            }
        }
        item {
            Text("Accent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesktopAccent.entries.forEach { accent ->
                    Surface(
                        modifier = Modifier
                            .size(width = 132.dp, height = 86.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onChange(settings.copy(accent = accent)) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (settings.accent == accent) accent.color.copy(alpha = .24f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(Modifier.size(28.dp), CircleShape, accent.color) {}
                            Text(accent.label, maxLines = 2, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ambient glow", fontWeight = FontWeight.SemiBold)
                        Text(
                            "A subtle accent glow behind the library",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.ambientGlow,
                        onCheckedChange = { onChange(settings.copy(ambientGlow = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceChoice(
    title: String,
    subtitle: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 250.dp, height = 120.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), RoundedCornerShape(18.dp), color) {}
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeView(
    username: String,
    albums: List<Album>,
    suggested: List<Song>,
    client: SubsonicClient,
    player: DesktopPlayer,
    onAlbum: (Album) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("your sound,", style = MaterialTheme.typography.titleLarge)
                        Text(username, fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
                        Text("ready whenever you are", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                    }
                    FilledIconButton(
                        enabled = suggested.isNotEmpty(),
                        onClick = { suggested.randomOrNull()?.let { player.play(it, suggested, client) } },
                        modifier = Modifier.size(64.dp),
                    ) { Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(30.dp)) }
                }
            }
        }
        item { SectionTitle("Recently added") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(albums, key = Album::id) { album -> AlbumCard(album, client, onAlbum) }
            }
        }
        item { SectionTitle("A different handful") }
        items(suggested.take(12), key = Song::id) { song ->
            SongRow(song, client, player.current?.id == song.id, player.playing) {
                player.play(song, suggested, client)
            }
        }
    }
}

@Composable
private fun SearchView(client: SubsonicClient, player: DesktopPlayer, onAlbum: (Album) -> Unit) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(SearchResult(emptyList(), emptyList())) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Search", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Songs and albums") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
            Button(
                enabled = query.isNotBlank() && !busy,
                modifier = Modifier.height(56.dp),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try { result = client.search(query.trim()) }
                        catch (failure: Exception) { error = failure.message }
                        finally { busy = false }
                    }
                },
            ) { Text("Search") }
        }
        error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result.albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(result.albums, key = Album::id) { AlbumCard(it, client, onAlbum) }
                    }
                }
                item { Spacer(Modifier.height(12.dp)); SectionTitle("Songs") }
            }
            items(result.songs, key = Song::id) { song ->
                SongRow(song, client, player.current?.id == song.id, player.playing) {
                    player.play(song, result.songs, client)
                }
            }
        }
    }
}

@Composable
private fun LibraryView(
    albums: List<Album>,
    playlists: List<Playlist>,
    client: SubsonicClient,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onCollection: (TrackCollection) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(
            "Library",
            modifier = Modifier.padding(horizontal = 32.dp),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollectionCard(
                collection = TrackCollection.AllTracks,
                icon = { Icon(Icons.Default.Shuffle, null, Modifier.size(30.dp)) },
                onClick = onCollection,
            )
            CollectionCard(
                collection = TrackCollection.LooseTracks,
                icon = { Icon(Icons.Default.MusicNote, null, Modifier.size(30.dp)) },
                onClick = onCollection,
            )
        }
        if (playlists.isNotEmpty()) {
            SectionTitle("Playlists", Modifier.padding(start = 32.dp, top = 24.dp, bottom = 12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(playlists, key = Playlist::id) { playlist ->
                    PlaylistCard(playlist, client, onPlaylist)
                }
            }
        }
        SectionTitle("Albums", Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(albums, key = Album::id) { album -> AlbumCard(album, client, onAlbum) }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: TrackCollection,
    icon: @Composable () -> Unit,
    onClick: (TrackCollection) -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(260.dp)
            .height(92.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick(collection) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(collection.title, fontWeight = FontWeight.Bold)
                Text(
                    collection.subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrackCollectionView(
    collection: TrackCollection,
    songs: List<Song>,
    client: SubsonicClient,
    player: DesktopPlayer,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            FilledIconButton(
                enabled = songs.isNotEmpty(),
                onClick = {
                    songs.randomOrNull()?.let { player.play(it, songs, client) }
                },
                modifier = Modifier.padding(start = 8.dp).size(72.dp),
            ) {
                Icon(Icons.Default.Shuffle, "Shuffle ${collection.title}", Modifier.size(34.dp))
            }
            Column(Modifier.padding(start = 18.dp)) {
                Text(collection.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "${songs.size} songs • ${collection.subtitle}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(songs, key = Song::id) { song ->
                SongRow(song, client, player.current?.id == song.id, player.playing) {
                    player.play(song, songs, client)
                }
            }
        }
    }
}

@Composable
private fun AlbumView(album: Album, songs: List<Song>, client: SubsonicClient, player: DesktopPlayer, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.Bottom) {
            Cover(client.coverUrl(album.coverArt, 700), Modifier.size(220.dp), 32.dp)
            Column(Modifier.padding(start = 28.dp, bottom = 8.dp)) {
                Text("ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(album.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(album.artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                album.year?.let { Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(songs, key = Song::id) { song ->
                SongRow(song, client, player.current?.id == song.id, player.playing) {
                    player.play(song, songs, client)
                }
            }
        }
    }
}

@Composable
private fun PlaylistView(
    playlist: Playlist,
    songs: List<Song>,
    client: SubsonicClient,
    player: DesktopPlayer,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.Bottom) {
            Cover(client.coverUrl(playlist.coverArt, 700), Modifier.size(220.dp), 32.dp)
            Column(Modifier.padding(start = 28.dp, bottom = 8.dp)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(playlist.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("${playlist.songCount} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(songs, key = Song::id) { song ->
                SongRow(song, client, player.current?.id == song.id, player.playing) {
                    player.play(song, songs, client)
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, client: SubsonicClient, onClick: (Album) -> Unit) {
    Column(Modifier.width(176.dp).clip(MaterialTheme.shapes.large).clickable { onClick(album) }.padding(10.dp)) {
        Cover(client.coverUrl(album.coverArt), Modifier.fillMaxWidth().aspectRatio(1f), 26.dp)
        Text(album.title, Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, client: SubsonicClient, onClick: (Playlist) -> Unit) {
    Row(
        Modifier.width(250.dp).clip(MaterialTheme.shapes.large).clickable { onClick(playlist) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(client.coverUrl(playlist.coverArt, 240), Modifier.size(76.dp), 20.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${playlist.songCount} songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SongRow(song: Song, client: SubsonicClient, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(client.coverUrl(song.coverArt, 160), Modifier.size(48.dp), 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" • "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(formatTime(song.durationSeconds.toDouble()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Icon(if (selected && playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
        }
    }
}

@Composable
private fun PlayerBar(
    client: SubsonicClient,
    player: DesktopPlayer,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
) {
    val song = player.current ?: return
    var seekPreview by remember(song.id) { mutableStateOf<Float?>(null) }
    var volumePreview by remember { mutableStateOf<Float?>(null) }
    val shownPosition = seekPreview?.toDouble() ?: player.positionSeconds
    val shownVolume = volumePreview?.toDouble() ?: player.volume
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 12.dp, shadowElevation = 16.dp) {
        Row(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(client.coverUrl(song.coverArt, 240), Modifier.size(72.dp), 18.dp)
            Column(Modifier.width(280.dp).padding(horizontal = 14.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                val problem = player.error
                if (problem != null) {
                    Text(problem, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = player::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }
            FilledIconButton(onClick = player::toggle, modifier = Modifier.size(54.dp)) {
                Icon(if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (player.playing) "Pause" else "Play")
            }
            IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Next") }
            Spacer(Modifier.width(20.dp))
            Text(formatTime(shownPosition), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = shownPosition.coerceIn(0.0, player.durationSeconds.coerceAtLeast(1.0)).toFloat(),
                onValueChange = { seekPreview = it },
                onValueChangeFinished = {
                    seekPreview?.let { player.seek(it.toDouble()) }
                    seekPreview = null
                },
                valueRange = 0f..player.durationSeconds.coerceAtLeast(1.0).toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(formatTime(player.durationSeconds), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onToggleLyrics) {
                Icon(
                    Icons.AutoMirrored.Filled.Subject,
                    "Lyrics",
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            IconButton(onClick = {
                volumePreview = null
                player.changeVolume(if (shownVolume > 0.0) 0.0 else 1.0)
            }) {
                Icon(if (shownVolume == 0.0) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Volume")
            }
            Slider(
                value = shownVolume.toFloat(),
                onValueChange = {
                    volumePreview = it
                    player.previewVolume(it.toDouble())
                },
                onValueChangeFinished = {
                    volumePreview?.let { player.changeVolume(it.toDouble()) }
                    volumePreview = null
                },
                valueRange = 0f..1f,
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

@Composable
private fun DesktopLyricsPane(
    client: SubsonicClient,
    song: Song,
    player: DesktopPlayer,
    modifier: Modifier = Modifier,
) {
    var lyrics by remember(song.id) { mutableStateOf<Lyrics?>(null) }
    var loaded by remember(song.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(song.id) {
        loaded = false
        lyrics = client.lyrics(song.id)
        loaded = true
    }

    val available = lyrics
    val positionMs = (player.positionSeconds * 1_000.0).toLong() + 250L
    val activeLine = if (available?.synced == true) {
        available.lines.indexOfLast { it.startMs <= positionMs }
    } else {
        -1
    }
    LaunchedEffect(song.id, activeLine) {
        if (activeLine >= 0) listState.animateScrollToItem(activeLine, -80)
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 8.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Subject, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Lyrics",
                    Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (available?.synced == true) {
                    Text("synced", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                available == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lyrics for this track", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(available.lines) { index, line ->
                        val color by animateColorAsState(
                            if (!available.synced || index == activeLine) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .48f),
                            label = "lyricColor",
                        )
                        Text(
                            line.text.ifBlank { "♪" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = available.synced) { player.seek(line.startMs / 1_000.0) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            color = color,
                            style = if (index == activeLine) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == activeLine) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String?, modifier: Modifier, radius: androidx.compose.ui.unit.Dp) {
    Surface(modifier, RoundedCornerShape(radius), MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (url == null) Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, Modifier.size(38.dp)) }
        else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SectionTitle(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

private fun formatTime(seconds: Double): String {
    val whole = seconds.takeIf(Double::isFinite)?.roundToInt()?.coerceAtLeast(0) ?: 0
    return "%d:%02d".format(whole / 60, whole % 60)
}
