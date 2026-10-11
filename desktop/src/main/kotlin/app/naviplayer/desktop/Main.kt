package app.naviplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlin.system.exitProcess

fun main() {
    System.setProperty("awt.appClassName", "NaviPlayer")
    application {
        val store = SessionStore()
        val discord = DiscordPresence()
        val player = DesktopPlayer(discord)
        val mpris = MprisService(player)

        Window(
            onCloseRequest = {
                player.close()
                discord.close()
                mpris.close()
                exitApplication()
                exitProcess(0)
            },
            title = "NaviPlayer",
            icon = painterResource("naviplayer.png"),
            state = WindowState(width = 1280.dp, height = 820.dp),
        ) {
            var appearance by remember { mutableStateOf(store.loadAppearance()) }
            NaviDesktopTheme(appearance) {
                DesktopApp(
                    store = store,
                    player = player,
                    appearance = appearance,
                    onAppearanceChange = {
                        appearance = it
                        store.saveAppearance(it)
                    },
                )
            }
        }
    }
}
