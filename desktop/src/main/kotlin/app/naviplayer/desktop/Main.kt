package app.naviplayer.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

fun main() {
    System.setProperty("awt.appClassName", "NaviPlayer")
    application {
        // Keep native resources stable across Compose recompositions. Creating these
        // objects in the composition body leaves old media players and RPC clients
        // alive whenever the root is recomposed.
        val runtime = remember { DesktopRuntime() }
        DisposableEffect(runtime) {
            onDispose(runtime::close)
        }
        val shutdown = {
            runtime.close()
            exitApplication()
            exitProcess(0)
        }

        Window(
            onCloseRequest = shutdown,
            title = "NaviPlayer",
            icon = painterResource("naviplayer.png"),
            state = WindowState(width = 1280.dp, height = 820.dp),
        ) {
            var appearance by remember { mutableStateOf(runtime.store.loadAppearance()) }
            NaviDesktopTheme(appearance) {
                DesktopApp(
                    store = runtime.store,
                    player = runtime.player,
                    appearance = appearance,
                    onExitForUpdate = shutdown,
                    onAppearanceChange = {
                        appearance = it
                        runtime.store.saveAppearance(it)
                    },
                )
            }
        }
    }
}

private class DesktopRuntime : AutoCloseable {
    val store = SessionStore()
    private val discord = DiscordPresence()
    private val playbackWebhook = PlaybackWebhookReporter()
    val player = DesktopPlayer(discord, playbackWebhook)
    private val mpris = MprisService(player)
    private val windowsMediaKeys = WindowsMediaKeyService(player)
    private val closed = AtomicBoolean(false)
    private val shutdownHook = Thread(::close, "naviplayer-shutdown")

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        windowsMediaKeys.close()
        mpris.close()
        player.close()
        playbackWebhook.close()
        discord.close()
        if (Thread.currentThread() !== shutdownHook) {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        }
    }
}
