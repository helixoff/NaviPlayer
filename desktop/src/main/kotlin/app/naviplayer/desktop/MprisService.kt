package app.naviplayer.desktop

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * Publishes the desktop player through MPRIS on Linux.
 *
 * Desktop environments route global media keys through this D-Bus interface,
 * so the integration also makes NaviPlayer visible to playerctl and media widgets.
 */
class MprisService(private val player: DesktopPlayer) : AutoCloseable {
    private var connection: DBusConnection? = null
    private var listener: AutoCloseable? = null

    init {
        if (System.getProperty("os.name").contains("linux", ignoreCase = true)) {
            runCatching {
                DBusConnectionBuilder.forSessionBus().build().also { bus ->
                    val exported = MprisObject(player)
                    bus.requestBusName(BUS_NAME)
                    bus.exportObject(OBJECT_PATH, exported)
                    connection = bus
                    listener = player.addStateListener { publishPlayerState() }
                }
            }.onFailure { error ->
                System.err.println("NaviPlayer MPRIS unavailable: ${error.message}")
            }
        }
    }

    override fun close() {
        runCatching { listener?.close() }
        listener = null
        runCatching { connection?.close() }
        connection = null
    }

    private fun publishPlayerState() {
        val bus = connection ?: return
        val changed = linkedMapOf<String, Variant<*>>(
            "PlaybackStatus" to Variant(playbackStatus(player)),
            "Metadata" to Variant(metadata(player.current), "a{sv}"),
            "CanGoNext" to Variant(player.current != null),
            "CanGoPrevious" to Variant(player.current != null),
            "CanPlay" to Variant(player.current != null),
        )
        runCatching {
            bus.sendMessage(Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, changed, emptyList()))
        }
    }

    companion object {
        private const val BUS_NAME = "org.mpris.MediaPlayer2.naviplayer"
        private const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        private const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        private const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"

        private fun playbackStatus(player: DesktopPlayer): String = when {
            player.current == null -> "Stopped"
            player.playing -> "Playing"
            else -> "Paused"
        }

        private fun metadata(song: Song?): Map<String, Variant<*>> {
            if (song == null) return emptyMap()
            val safeId = song.id.replace(Regex("[^A-Za-z0-9_]"), "_")
            return linkedMapOf(
                "mpris:trackid" to Variant(DBusPath("/app/naviplayer/track/$safeId")),
                "mpris:length" to Variant(song.durationSeconds.toLong() * 1_000_000L),
                "xesam:title" to Variant(song.title),
                "xesam:artist" to Variant(listOf(song.artist.ifBlank { "Unknown artist" }), "as"),
                "xesam:album" to Variant(song.album),
            )
        }
    }

    @Suppress("FunctionName")
    private class MprisObject(private val player: DesktopPlayer) : MprisRoot, MprisPlayer, Properties {
        override fun getObjectPath() = OBJECT_PATH

        override fun Raise() = Unit
        override fun Quit() = Unit
        override fun Next() = player.next()
        override fun Previous() = player.previous()
        override fun Pause() = player.pause()
        override fun PlayPause() = player.toggle()
        override fun Stop() = player.stop()
        override fun Play() = player.play()
        override fun Seek(offset: Long) = player.seek(player.positionSeconds + offset / 1_000_000.0)

        override fun SetPosition(trackId: DBusPath, position: Long) {
            val currentPath = metadata(player.current)["mpris:trackid"]?.value
            if (trackId == currentPath) player.seek(position / 1_000_000.0)
        }

        override fun OpenUri(uri: String) = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            property(interfaceName, propertyName).value as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
            throw PropertyReadOnly("$interfaceName.$propertyName is read-only")
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
            ROOT_INTERFACE -> rootProperties()
            PLAYER_INTERFACE -> playerProperties()
            else -> emptyMap()
        }

        private fun property(interfaceName: String, propertyName: String): Variant<*> =
            GetAll(interfaceName)[propertyName]
                ?: throw UnknownProperty("Unknown property $interfaceName.$propertyName")

        private fun rootProperties() = linkedMapOf<String, Variant<*>>(
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(false),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("NaviPlayer"),
            "DesktopEntry" to Variant("naviplayer"),
            "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
            "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
        )

        private fun playerProperties() = linkedMapOf<String, Variant<*>>(
            "PlaybackStatus" to Variant(playbackStatus(player)),
            "LoopStatus" to Variant("None"),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(false),
            "Metadata" to Variant(metadata(player.current), "a{sv}"),
            "Volume" to Variant(1.0),
            "Position" to Variant((player.positionSeconds * 1_000_000L).toLong()),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(player.current != null),
            "CanGoPrevious" to Variant(player.current != null),
            "CanPlay" to Variant(player.current != null),
            "CanPause" to Variant(player.current != null),
            "CanSeek" to Variant(player.current != null),
            "CanControl" to Variant(true),
        )
    }
}

@DBusInterfaceName("org.mpris.MediaPlayer2")
private interface MprisRoot : DBusInterface {
    @Suppress("FunctionName")
    fun Raise()

    @Suppress("FunctionName")
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
private interface MprisPlayer : DBusInterface {
    @Suppress("FunctionName") fun Next()
    @Suppress("FunctionName") fun Previous()
    @Suppress("FunctionName") fun Pause()
    @Suppress("FunctionName") fun PlayPause()
    @Suppress("FunctionName") fun Stop()
    @Suppress("FunctionName") fun Play()
    @Suppress("FunctionName") fun Seek(offset: Long)
    @Suppress("FunctionName") fun SetPosition(trackId: DBusPath, position: Long)
    @Suppress("FunctionName") fun OpenUri(uri: String)
}
