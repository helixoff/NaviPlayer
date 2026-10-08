package app.naviplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun Root(store: SessionStore, holder: PlayerHolder) {
    var creds by remember { mutableStateOf(store.load()) }
    val c = creds
    if (c == null) {
        LoginScreen { store.save(it); creds = it }
    } else {
        val api = remember(c) { SubsonicApi(c, store.salt()) }
        LaunchedEffect(api) {
            holder.api = api
        }
        AppScaffold(api, store, holder, c) { holder.api = null; store.clear(); creds = null }
    }
}

@Composable
fun AppScaffold(api: SubsonicApi, store: SessionStore, holder: PlayerHolder, creds: Credentials, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Dest>() }
    var showPlayer by remember { mutableStateOf(false) }
    var home by remember { mutableStateOf<HomeData?>(null) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeVersion by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var avatarUri by remember { mutableStateOf(store.avatarUri()) }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    val open: (Dest) -> Unit = { stack.add(it) }

    // следим, виден ли экран: в фоне прогресс опрашивать не нужно (экономим батарею)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) visible = true
            else if (event == Lifecycle.Event.ON_STOP) visible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(visible, holder.isPlaying) {
        if (visible) {
            holder.tick()
            while (holder.isPlaying) { delay(500); holder.tick() }
        }
    }
    LaunchedEffect(api, homeVersion) {
        homeLoading = true
        homeError = null
        try { home = HomeRepo.load(api, store) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { homeError = e.message ?: "couldn't reach your music server" }
        finally { homeLoading = false }
    }
    BackHandler(stack.isNotEmpty()) { pop() }
    BackHandler(stack.isEmpty() && tab != 0) { tab = 0 }

    NaviBackdrop(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (holder.current != null) MiniPlayer(holder) { showPlayer = true }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                        color = Pal.NavBar.copy(alpha = 0.96f),
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp,
                    ) {
                    NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                        data class Tab(val label: String, val filled: ImageVector, val outlined: ImageVector)
                        listOf(
                            Tab("home", Icons.Filled.Home, Icons.Outlined.Home),
                            Tab("search", Icons.Filled.Search, Icons.Outlined.Search),
                            Tab("library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
                        ).forEachIndexed { i, t ->
                            val selected = tab == i
                            NavigationBarItem(
                                selected = selected,
                                onClick = { stack.clear(); tab = i },
                                icon = { Icon(if (selected) t.filled else t.outlined, t.label) },
                                label = { Text(t.label) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Pal.NavIndicator),
                            )
                        }
                    }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val top = stack.lastOrNull()
                if (top != null) {
                    key(top) {
                        when (top) {
                            is Dest.AlbumD -> TrackListScreen(
                                api, holder, top.title, top.artist, top.cover, top.title, null,
                                { api.album(top.id).second }, ::pop,
                            )
                            is Dest.PlaylistD -> TrackListScreen(
                                api, holder, top.name, "playlist", null, top.name, null,
                                { api.playlist(top.id) }, ::pop,
                            )
                            is Dest.MixD -> TrackListScreen(
                                api, holder, top.mix.title, top.mix.subtitle, null, top.mix.title, top.mix.songs,
                                { top.mix.songs }, ::pop,
                            )
                            Dest.AllTracks -> TrackListScreen(
                                api, holder, "all tracks", "every song, shuffled", null, "all tracks", null,
                                { api.allSongs().dedupe().shuffled() }, ::pop,
                            )
                            Dest.Loose -> TrackListScreen(
                                api, holder, "loose tracks", "no proper tags, shuffled", null, "loose tracks", null,
                                { api.allSongs().filter { it.isLoose() }.dedupe().shuffled() }, ::pop,
                            )
                            is Dest.ArtistD -> ArtistScreen(api, top.name, top.id, ::pop, open)
                            Dest.Profile -> ProfileScreen(
                                creds.user, creds.url, store, avatarUri,
                                onAvatarChanged = { avatarUri = it },
                                onBack = ::pop,
                                onLogout = onLogout,
                            )
                        }
                    }
                } else when (tab) {
                    0 -> HomeScreen(
                        api, creds.user, avatarUri, holder, home, homeLoading, homeError, open,
                        onRefresh = { store.clearCache(); home = null; homeVersion++ },
                        onSearch = { stack.clear(); tab = 1 },
                    )
                    1 -> SearchScreen(api, store, holder, query, { query = it }, open)
                    else -> LibraryScreen(api, holder, open)
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BackHandler { showPlayer = false }
            NowPlayingScreen(api, holder) { showPlayer = false }
        }
    }
}
