package app.naviplayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class AndroidUpdate(val version: String, val apkUrl: String)

class AndroidUpdater(private val context: Context) {
    private val appContext = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val currentVersion: String = appContext.packageManager
        .getPackageInfo(appContext.packageName, 0).versionName ?: "0.0.0"

    suspend fun check(): AndroidUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NaviPlayer/$currentVersion")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val releases = JSONArray(response.body?.string().orEmpty())
            var latest: AndroidUpdate? = null
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                val tag = release.optString("tag_name")
                if (release.optBoolean("draft") || !tag.startsWith("v") || tag.startsWith("vdesktop")) continue
                val version = tag.removePrefix("v")
                if (compareVersions(version, currentVersion) <= 0) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.getJSONObject(assetIndex)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        val candidate = AndroidUpdate(version, asset.getString("browser_download_url"))
                        if (latest == null || compareVersions(candidate.version, latest.version) > 0) {
                            latest = candidate
                        }
                        break
                    }
                }
            }
            latest
        }
    }

    fun downloadAndInstall(update: AndroidUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            error("Allow installs from NaviPlayer, return to the app and tap Update again")
        }

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "NaviPlayer-${update.version}.apk"
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("NaviPlayer ${update.version}")
            .setDescription("Downloading update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                runCatching { appContext.unregisterReceiver(this) }
                val uri = manager.getUriForDownloadedFile(downloadId) ?: return
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, APK_MIME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val RELEASES_API = "https://api.github.com/repos/helixoff/NaviPlayer/releases?per_page=30"
    }
}

@Composable
fun AndroidUpdateBanner() {
    val context = LocalContext.current
    val updater = remember { AndroidUpdater(context) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AndroidUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(updater) {
        update = runCatching { updater.check() }.getOrNull()
    }

    val available = update ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("NaviPlayer ${available.version} available", fontWeight = FontWeight.Bold)
                Text(
                    message ?: "Download and install without hunting through releases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f),
                )
            }
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        runCatching { updater.downloadAndInstall(available) }
                            .onSuccess { message = "Downloading… the installer will open automatically" }
                            .onFailure { message = it.message }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Update")
            }
            IconButton(onClick = { update = null }) { Icon(Icons.Default.Close, "Dismiss") }
        }
    }
}
