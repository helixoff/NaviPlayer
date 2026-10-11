package app.naviplayer.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.TimeUnit

data class DesktopUpdate(
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String,
)

class DesktopUpdater {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val currentVersion: String = javaClass.getResourceAsStream("/naviplayer.properties")?.use { input ->
        Properties().also { it.load(input) }.getProperty("version")
    } ?: "0.0.0"

    suspend fun check(): DesktopUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NaviPlayer/$currentVersion")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val releases = JSONArray(response.body?.string().orEmpty())
            val extension = if (isWindows()) ".msi" else ".deb"
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!tag.startsWith("desktop-v")) continue
                val version = tag.removePrefix("desktop-v")
                if (compareVersions(version, currentVersion) <= 0) return@withContext null
                val assets = release.optJSONArray("assets") ?: continue
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.getJSONObject(assetIndex)
                    if (asset.optString("name").endsWith(extension, ignoreCase = true)) {
                        return@withContext DesktopUpdate(
                            version = version,
                            downloadUrl = asset.getString("browser_download_url"),
                            releaseUrl = release.getString("html_url"),
                            notes = release.optString("body"),
                        )
                    }
                }
            }
            null
        }
    }

    suspend fun downloadAndInstall(update: DesktopUpdate) = withContext(Dispatchers.IO) {
        val extension = if (isWindows()) ".msi" else ".deb"
        val downloads = Path.of(System.getProperty("user.home"), "Downloads")
            .takeIf { Files.isDirectory(it) }
            ?: Path.of(System.getProperty("java.io.tmpdir"))
        val destination = downloads.resolve("NaviPlayer-${update.version}$extension")
        val temporary = destination.resolveSibling("${destination.fileName}.part")
        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "NaviPlayer/$currentVersion")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("GitHub returned an empty package")
            body.byteStream().use { input ->
                Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
            }
        }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
        if (isWindows()) {
            ProcessBuilder("msiexec.exe", "/i", destination.toString()).start()
        } else {
            runCatching { ProcessBuilder("xdg-open", destination.toString()).start() }
                .getOrElse { Desktop.getDesktop().open(destination.toFile()) }
        }
    }

    fun openRelease(update: DesktopUpdate) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(java.net.URI(update.releaseUrl))
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun isWindows() = System.getProperty("os.name").contains("windows", ignoreCase = true)

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/helixoff/NaviPlayer/releases?per_page=30"
    }
}
