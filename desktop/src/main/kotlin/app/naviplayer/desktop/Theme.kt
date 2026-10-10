package app.naviplayer.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NaviColors = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF571F12),
    primaryContainer = Color(0xFF733626),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFC2C5FF),
    secondaryContainer = Color(0xFF404378),
    onSecondaryContainer = Color(0xFFE1E0FF),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE8E1E5),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFE8E1E5),
    surfaceVariant = Color(0xFF2B292F),
    onSurfaceVariant = Color(0xFFD6C2C0),
    surfaceContainer = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF28252B),
    surfaceContainerHighest = Color(0xFF343038),
    outline = Color(0xFFA5908D),
)

private val NaviShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

@Composable
fun NaviDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NaviColors, shapes = NaviShapes, content = content)
}
