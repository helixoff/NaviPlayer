package app.naviplayer.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    val store = SessionStore()
    val discord = DiscordPresence()
    val player = DesktopPlayer(discord)

    Window(
        onCloseRequest = {
            player.close()
            discord.close()
            exitApplication()
        },
        title = "NaviPlayer",
        state = WindowState(width = 1280.dp, height = 820.dp),
    ) {
        NaviDesktopTheme {
            DesktopApp(store, player)
        }
    }
}
