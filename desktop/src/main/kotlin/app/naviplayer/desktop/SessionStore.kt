package app.naviplayer.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

class SessionStore(
    private val directory: Path = defaultConfigDirectory(),
) {
    private val sessionFile = directory.resolve("session.properties")

    fun load(): Credentials? {
        if (!Files.isRegularFile(sessionFile)) return null
        return runCatching {
            val values = Properties().also { properties ->
                Files.newInputStream(sessionFile).use(properties::load)
            }
            Credentials(
                server = values.getProperty("server").orEmpty(),
                username = values.getProperty("username").orEmpty(),
                password = values.getProperty("password").orEmpty(),
            ).takeIf { it.server.isNotBlank() && it.username.isNotBlank() }
        }.getOrNull()
    }

    fun save(credentials: Credentials) {
        Files.createDirectories(directory)
        val temporary = sessionFile.resolveSibling("${sessionFile.fileName}.tmp")
        val values = Properties().apply {
            setProperty("server", credentials.server)
            setProperty("username", credentials.username)
            setProperty("password", credentials.password)
        }
        Files.newOutputStream(temporary).use { values.store(it, "NaviPlayer desktop session") }
        runCatching {
            Files.setPosixFilePermissions(temporary, setOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            ))
        }
        Files.move(temporary, sessionFile, StandardCopyOption.REPLACE_EXISTING)
    }

    fun clear() {
        Files.deleteIfExists(sessionFile)
    }

    fun salt(): String {
        Files.createDirectories(directory)
        val saltFile = directory.resolve("salt")
        if (Files.isRegularFile(saltFile)) return Files.readString(saltFile).trim()
        return SubsonicClient.newSalt().also { Files.writeString(saltFile, it) }
    }

    companion object {
        private fun defaultConfigDirectory(): Path {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("windows")) {
                System.getenv("APPDATA")?.takeIf(String::isNotBlank)?.let {
                    return Path.of(it, "NaviPlayer")
                }
            }
            System.getenv("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank)?.let {
                return Path.of(it, "naviplayer")
            }
            return Path.of(System.getProperty("user.home"), ".config", "naviplayer")
        }
    }
}
