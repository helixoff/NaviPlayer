package app.naviplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class ChipData(val title: String, val cover: String?, val icon: ImageVector?, val onClick: () -> Unit)

// ───────────────────────── home ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api: SubsonicApi, user: String, avatarUri: String?, data: HomeData?,
    loading: Boolean, error: String?,
    onOpen: (Dest) -> Unit, onRefresh: () -> Unit, onSearch: () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading) refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                val heroShape = RoundedCornerShape(38.dp)
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                        .clip(heroShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                        )
                ) {
                    Box(
                        Modifier.size(170.dp).align(Alignment.TopEnd).offset(x = 48.dp, y = (-54).dp)
                            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    )
                    Box(
                        Modifier.size(110.dp).align(Alignment.BottomEnd).offset(x = 30.dp, y = 42.dp)
                            .clip(expressiveShape(48.dp, 18.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                    )
                    Column(Modifier.padding(24.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Waves, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("NaviPlayer", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(50))
                                    .clickable { onOpen(Dest.Profile) },
                            ) {
                                UserAvatar(user, avatarUri, Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        Text(timeLabel(), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), style = MaterialTheme.typography.titleLarge)
                        Text(user, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.displayMedium)
                    }
                }
            }

            item { SearchPill(onSearch) }

            if (loading) {
                item { LoadingCards() }
            } else if (error != null) {
                item { MessageCard(Icons.Default.CloudOff, "music is out of reach", "$error\nPull down to try again.") }
            } else if (data == null) {
                item { MessageCard(Icons.Default.LibraryMusic, "nothing here yet", "Pull down to load your library.") }
            } else {
                val chips = buildList {
                    add(ChipData("all tracks", null, Icons.Default.Shuffle) { onOpen(Dest.AllTracks) })
                    add(ChipData("loose tracks", null, Icons.Default.MusicNote) { onOpen(Dest.Loose) })
                    data.recent.take(4).forEach { a -> add(ChipData(a.name, a.coverArt, null) { onOpen(a.dest()) }) }
                }

                item { SectionHeader("jump back in") }
                chips.chunked(2).forEach { pair ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Dim.Page, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { JumpChip(api, it, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (data.artists.isNotEmpty()) {
                    item { SectionHeader("artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Dim.Page), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(data.artists) { a -> ArtistBubble(a, api) { onOpen(Dest.ArtistD(a.id, a.name)) } }
                        }
                    }
                }

                if (data.mixes.isNotEmpty()) {
                    item { SectionHeader("made for you") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Dim.Page), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(data.mixes) { m -> MixCard(api, m) { onOpen(Dest.MixD(m)) } }
                        }
                    }
                }

                if (data.albumsToday.isNotEmpty()) {
                    item { SectionHeader("albums for today") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Dim.Page), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(data.albumsToday) { a ->
                                Column(Modifier.width(220.dp).clickable { onOpen(a.dest()) }) {
                                    Cover(api, a.coverArt, Modifier.size(220.dp), Dim.HeroRadius)
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text, style = MaterialTheme.typography.titleMedium)
                                    Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpChip(api: SubsonicApi, d: ChipData, modifier: Modifier) {
    Row(
        modifier.height(82.dp).clip(RoundedCornerShape(24.dp)).background(Pal.Card).clickable(onClick = d.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (d.icon != null) IconBox(d.icon, 82.dp, 24.dp)
        else Cover(api, d.cover, Modifier.size(82.dp), 24.dp)
        Text(
            d.title, Modifier.padding(horizontal = 12.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = Pal.Text, style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun MixCard(api: SubsonicApi, mix: Mix, onClick: () -> Unit) {
    Column(Modifier.width(190.dp).clickable(onClick = onClick)) {
        Mosaic(api, mix.songs.mapNotNull { it.coverArt }.distinct().take(4), Modifier.size(190.dp), Dim.HeroRadius)
        Spacer(Modifier.height(8.dp))
        Text(mix.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text, style = MaterialTheme.typography.titleMedium)
        Text(mix.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
    }
}

// ───────────────────────── search ─────────────────────────

@Composable
fun SearchScreen(
    api: SubsonicApi, store: SessionStore, holder: PlayerHolder,
    query: String, onQuery: (String) -> Unit, onOpen: (Dest) -> Unit,
) {
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var history by remember { mutableStateOf(store.history()) }
    var loading by remember { mutableStateOf(false) }
    var searchErr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        searchErr = null
        if (query.trim().length < 2) { result = null; loading = false; return@LaunchedEffect }
        result = null
        loading = true
        delay(400) // ждём, пока допечатаешь
        try {
            result = api.search(query.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchErr = e.message ?: "search failed"
        }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("search") }
        item {
            TextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dim.Page),
                singleLine = true, shape = RoundedCornerShape(28.dp),
                placeholder = { Text("songs, albums, artists") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "clear") }
                }) else null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Pal.Card, unfocusedContainerColor = Pal.Card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    store.addHistory(query.trim()); history = store.history()
                }),
            )
        }

        if (loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp)) }
        }
        searchErr?.let { msg ->
            item { Text("search failed: $msg", Modifier.padding(Dim.Page), color = MaterialTheme.colorScheme.error) }
        }

        val r = result
        if (r == null) {
            if (history.isNotEmpty()) {
                item { SectionHeader("recent") }
                items(history) { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onQuery(q) }.padding(horizontal = Dim.Page, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, tint = Pal.Subtle)
                        Spacer(Modifier.width(14.dp))
                        Text(q, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            if (r.songs.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty()) {
                item { EmptyHint("nothing found") }
            }
            if (r.songs.isNotEmpty()) {
                item { SectionHeader("songs") }
                itemsIndexed(r.songs) { i, s ->
                    SongRow(api, s, holder.current?.mediaId == s.id) {
                        store.addHistory(query.trim())
                        holder.play(r.songs, i, api, "search")
                    }
                }
            }
            if (r.albums.isNotEmpty()) {
                item { SectionHeader("albums") }
                items(r.albums) { a ->
                    ListRow(api, a.coverArt, a.name, a.artist) { store.addHistory(query.trim()); onOpen(a.dest()) }
                }
            }
            if (r.artists.isNotEmpty()) {
                item { SectionHeader("artists") }
                items(r.artists) { a ->
                    ListRow(api, a.coverArt, a.name, "artist") { store.addHistory(query.trim()); onOpen(Dest.ArtistD(a.id, a.name)) }
                }
            }
        }
    }
}

// ───────────────────────── library ─────────────────────────

@Composable
fun LibraryScreen(api: SubsonicApi, holder: PlayerHolder, onOpen: (Dest) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reload) {
        loading = true
        loadError = null
        try {
            if (tab == 0) {
                playlists = api.playlists()
            } else if (tab == 1) {
                if (albums.isEmpty()) {
                    val all = ArrayList<Album>()
                    var offset = 0
                    while (offset < 3000) { // страницами по 500
                        val page = api.albumList("alphabeticalByName", 500, offset)
                        all.addAll(page)
                        if (page.size < 500) break
                        offset += 500
                    }
                    albums = all
                }
            } else if (tab == 2) {
                if (artists.isEmpty()) artists = api.artists()
            } else if (songs.isEmpty()) {
                val all = ArrayList<Song>()
                var offset = 0
                while (offset < 5000) {
                    val page = api.allSongs(offset)
                    if (page.isEmpty()) break
                    all.addAll(page)
                    offset += page.size
                    if (page.size < 500) break
                }
                songs = all.sortedBy { it.title.lowercase() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: "couldn't load this part of your library"
        } finally {
            loading = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("library") }
        item { QuickRow(Icons.Default.Shuffle, "all tracks", "every song, shuffled") { onOpen(Dest.AllTracks) } }
        item { QuickRow(Icons.Default.MusicNote, "loose tracks", "songs without proper tags") { onOpen(Dest.Loose) } }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dim.Page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("playlists", "albums", "artists", "songs").forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i, onClick = { tab = i }, label = { Text(label) },
                        leadingIcon = if (tab == i) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    )
                }
            }
        }
        if (loading) {
            item { LoadingCards() }
        } else if (loadError != null) {
            item { MessageCard(Icons.Default.CloudOff, "library didn't load", loadError!!, "try again") { reload++ } }
        } else if (tab == 0) {
            if (playlists.isEmpty()) item { MessageCard(Icons.AutoMirrored.Filled.QueueMusic, "no playlists yet", "Create one in Navidrome and it will appear here.") }
            items(playlists) { p ->
                ListRow(api, p.coverArt, p.name, "${p.songCount} songs") { onOpen(Dest.PlaylistD(p.id, p.name)) }
            }
        } else if (tab == 1) {
            if (albums.isEmpty()) item { EmptyHint("no albums yet") }
            items(albums) { a -> ListRow(api, a.coverArt, a.name, a.artist) { onOpen(a.dest()) } }
        } else if (tab == 2) {
            if (artists.isEmpty()) item { EmptyHint("no artists yet") }
            items(artists) { a -> ListRow(api, a.coverArt, a.name, "artist") { onOpen(Dest.ArtistD(a.id, a.name)) } }
        } else {
            if (songs.isEmpty()) item { EmptyHint("no songs yet") }
            itemsIndexed(songs) { i, s ->
                SongRow(api, s, holder.current?.mediaId == s.id) {
                    // в очередь кладём окно из 300 песен, чтобы не гонять тысячи элементов
                    holder.play(songs.drop(i).take(300), 0, api, "all songs")
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dim.Page, vertical = 8.dp).clip(RoundedCornerShape(28.dp))
            .background(Pal.Card).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Pal.Subtle)
        Spacer(Modifier.width(12.dp))
        Text("search songs, albums, artists", color = Pal.Subtle, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ArtistBubble(a: Artist, api: SubsonicApi, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (a.coverArt != null) Cover(api, a.coverArt, Modifier.size(84.dp), 42.dp)
        else LetterAvatar(a.name, 84.dp)
        Spacer(Modifier.height(6.dp))
        Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun QuickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) =
    BaseRow(title, subtitle, onClick, leading = { IconBox(icon, Dim.RowCover, Dim.RowRadius) })
