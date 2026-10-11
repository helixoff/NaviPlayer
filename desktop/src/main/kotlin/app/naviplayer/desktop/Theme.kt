package app.naviplayer.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class DesktopSurfaceStyle { Night, Amoled }

enum class DesktopAccent(val label: String, val color: Color) {
    Coral("Coral", Color(0xFFFF8E78)),
    Violet("Violet", Color(0xFFC5A5FF)),
    Blue("Electric blue", Color(0xFF78A9FF)),
    Mint("Mint", Color(0xFF62DDBD)),
}

data class AppearanceSettings(
    val surfaceStyle: DesktopSurfaceStyle = DesktopSurfaceStyle.Night,
    val accent: DesktopAccent = DesktopAccent.Violet,
    val ambientGlow: Boolean = true,
)

private fun naviColors(settings: AppearanceSettings) = darkColorScheme(
    primary = settings.accent.color,
    onPrimary = Color(0xFF17131C),
    primaryContainer = settings.accent.color.copy(alpha = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) .22f else .28f),
    onPrimaryContainer = Color(0xFFF8EDFF),
    secondary = settings.accent.color.copy(alpha = .82f),
    secondaryContainer = settings.accent.color.copy(alpha = .20f),
    onSecondaryContainer = Color(0xFFF8EDFF),
    background = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color.Black else Color(0xFF0E0D12),
    onBackground = Color(0xFFF1EAF2),
    surface = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color.Black else Color(0xFF0E0D12),
    onSurface = Color(0xFFF1EAF2),
    surfaceVariant = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color(0xFF19171D) else Color(0xFF29262E),
    onSurfaceVariant = Color(0xFFCFC5D2),
    surfaceContainer = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color(0xFF0B0A0D) else Color(0xFF19171E),
    surfaceContainerHigh = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color(0xFF121015) else Color(0xFF222027),
    surfaceContainerHighest = if (settings.surfaceStyle == DesktopSurfaceStyle.Amoled) Color(0xFF1B181F) else Color(0xFF302C35),
    outline = Color(0xFF958B99),
)

private val NaviShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

@Composable
fun NaviDesktopTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = naviColors(settings), shapes = NaviShapes, content = content)
}
