package app.naviplayer

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

// ───────────────────────── альбом / плейлист / микс ─────────────────────────

@Composable
fun TrackListScreen(
    api: SubsonicApi,
    holder: PlayerHolder,
    title: String,
    subtitle: String,
    cover: String?,
    from: String,
    preload: List<Song>?,
    loader: suspend () -> List<Song>,
    onBack: () -> Unit,
) {
    var songs by remember { mutableStateOf(preload ?: emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(preload == null) }

    LaunchedEffect(Unit) {
        if (preload == null) {
            try { songs = loader() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { err = e.message }
            loading = false
        }
    }

    val totalSec = songs.sumOf { it.durationSec }
    val meta = buildString {
        if (songs.isNotEmpty()) append("${songs.size} songs")
        if (totalSec > 0) append(" · ${totalSec / 60} min")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ScreenTopBar(onBack)
                val covers = songs.mapNotNull { it.coverArt }.distinct()
                val box = Modifier.fillMaxWidth(0.68f).aspectRatio(1f)
                if (cover != null) {
                    Cover(api, cover, box, Dim.HeroRadius)
                } else if (covers.isNotEmpty()) {
                    Mosaic(api, covers.take(4), box, Dim.HeroRadius)
                } else {
                    Box(box.clip(RoundedCornerShape(Dim.HeroRadius)).background(Pal.Disc), Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = Pal.Accent, modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.padding(horizontal = Dim.Page), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, color = Pal.Subtle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(meta, color = Pal.Subtle, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.padding(horizontal = Dim.Page), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (songs.isNotEmpty()) holder.play(songs, 0, api, from) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = songs.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("play")
                    }
                    FilledTonalButton(
                        onClick = { if (songs.isNotEmpty()) holder.play(songs, songs.indices.random(), api, from, true) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = songs.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("shuffle")
                    }
                }
                if (loading) {
                    Spacer(Modifier.height(32.dp)); CircularProgressIndicator()
                }
                err?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(12.dp))
            }
        }
        itemsIndexed(songs) { i, s ->
            SongRow(api, s, holder.current?.mediaId == s.id) { holder.play(songs, i, api, from) }
        }
    }
}

// ───────────────────────── артист ─────────────────────────

@Composable
fun ArtistScreen(api: SubsonicApi, name: String, id: String, onBack: () -> Unit, onOpen: (Dest) -> Unit) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(id) {
        try { albums = api.artistAlbums(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dim.Page, end = Dim.Page, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenTopBar(onBack, Modifier.padding(vertical = 6.dp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LetterAvatar(name, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            if (albums.isEmpty()) "artist" else "${albums.size} albums",
                            color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(albums) { a -> AlbumCard(api, a) { onOpen(a.dest()) } }
    }
}

// ───────────────────────── профиль ─────────────────────────

@Composable
fun ProfileScreen(
    user: String, server: String, store: SessionStore,
    avatarUri: String?, onAvatarChanged: (String?) -> Unit,
    appearance: AndroidAppearanceSettings,
    onAppearanceChange: (AndroidAppearanceSettings) -> Unit,
    onBack: () -> Unit, onLogout: () -> Unit,
) {
    var searches by remember { mutableIntStateOf(store.history().size) }
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val value = uri.toString()
        store.setAvatarUri(value)
        onAvatarChanged(value)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTopBar(onBack)
        val avatarShape = ScallopShape()
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(
                user = user,
                avatarUri = avatarUri,
                modifier = Modifier.size(150.dp).border(3.dp, Pal.Accent.copy(alpha = 0.7f), avatarShape),
                shape = avatarShape,
                large = true,
            )
            SmallFloatingActionButton(
                onClick = { avatarPicker.launch(arrayOf("image/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Edit, "change profile picture") }
        }
        Spacer(Modifier.height(16.dp))
        Text(user, style = MaterialTheme.typography.displaySmall)
        Text("your account", color = Pal.Subtle, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        Column(Modifier.padding(horizontal = Dim.Page)) {
            StatCard(searches.toString(), "searches", Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text("appearance", Modifier.padding(start = 4.dp, bottom = 10.dp), color = Pal.Accent, style = MaterialTheme.typography.titleMedium)
            TonalCard {
                AppearanceChoices(
                    title = "theme",
                    choices = AppThemeMode.entries,
                    selected = appearance.themeMode,
                    label = AppThemeMode::label,
                    onSelect = { onAppearanceChange(appearance.copy(themeMode = it)) },
                )
                SettingsDivider()
                AppearanceChoices(
                    title = "accent",
                    choices = AppAccent.entries,
                    selected = appearance.accent,
                    label = AppAccent::label,
                    onSelect = { onAppearanceChange(appearance.copy(accent = it)) },
                )
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AMOLED black", style = MaterialTheme.typography.titleMedium)
                        Text("Use true black surfaces in dark mode", color = Pal.Subtle, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = appearance.amoled,
                        onCheckedChange = { onAppearanceChange(appearance.copy(amoled = it)) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("this account", Modifier.padding(start = 4.dp, bottom = 10.dp), color = Pal.Accent, style = MaterialTheme.typography.titleMedium)

            // Группа строк в одной карточке с разделителями — как в настройках Android.
            TonalCard {
                InfoRow(Icons.Default.Image, "profile picture", "choose an image from this phone") {
                    avatarPicker.launch(arrayOf("image/*"))
                }
                if (avatarUri != null) {
                    SettingsDivider()
                    InfoRow(Icons.Default.DeleteOutline, "remove profile picture", "use the letter avatar") {
                        store.setAvatarUri(null)
                        onAvatarChanged(null)
                    }
                }
                SettingsDivider()
                InfoRow(Icons.Default.PhoneAndroid, Build.MODEL, "this device") { }
                SettingsDivider()
                InfoRow(Icons.Default.Dns, "server", server) { }
                SettingsDivider()
                InfoRow(Icons.Default.Info, "version", version) { }
                SettingsDivider()
                InfoRow(Icons.Default.History, "clear search history", "$searches searches on this phone") {
                    store.clearHistory(); searches = 0
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("log out")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun <T> AppearanceChoices(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    label = { Text(label(choice)) },
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    TonalCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Pal.Accent)
            Text(label, color = Pal.Subtle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsDivider() =
    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    BaseRow(
        title, subtitle, onClick,
        leading = { Icon(icon, null, tint = Pal.Accent, modifier = Modifier.size(26.dp)) },
    )
}
