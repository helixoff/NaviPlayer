package app.naviplayer

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class AppAccent(val label: String, val light: Color, val dark: Color) {
    Dynamic("Dynamic", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    Coral("Coral", Color(0xFF9A452F), Color(0xFFFFB59F)),
    Violet("Violet", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    Blue("Blue", Color(0xFF315DA8), Color(0xFFA9C7FF)),
    Mint("Mint", Color(0xFF006B59), Color(0xFF71DBC2)),
}

data class AndroidAppearanceSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val accent: AppAccent = AppAccent.Dynamic,
    val amoled: Boolean = false,
)

/**
 * Shared application palette. Values come from [MaterialTheme.colorScheme], so
 * persisted theme, accent, dynamic-colour, and AMOLED choices apply everywhere.
 */
object Pal {
    val Bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
    val Card: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val CardLow: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
    val NavBar: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
    val NavIndicator: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer
    val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val AccentDark: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary
    val Avatar: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
    val OnAvatar: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer
    val Subtle: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Disc: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Text: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
}

// ───────── запасная схема (Android < 12 или если динамика недоступна) ─────────

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF571F12),
    primaryContainer = Color(0xFF733626),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFC2C5FF),
    onSecondary = Color(0xFF292C60),
    secondaryContainer = Color(0xFF404378),
    onSecondaryContainer = Color(0xFFE1E0FF),
    tertiary = Color(0xFFAEC6FF),
    onTertiary = Color(0xFF17315F),
    tertiaryContainer = Color(0xFF304878),
    onTertiaryContainer = Color(0xFFD8E2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131315),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF131315),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF4D4542),
    onSurfaceVariant = Color(0xFFD3C3BE),
    outline = Color(0xFF9D8D88),
    outlineVariant = Color(0xFF51443F),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF1B1B1E),
    surfaceContainer = Color(0xFF202023),
    surfaceContainerHigh = Color(0xFF2A292D),
    surfaceContainerHighest = Color(0xFF353438),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6750A4),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8E4A38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3A0B02),
    secondary = Color(0xFF585B8E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0FF),
    onSecondaryContainer = Color(0xFF151849),
    tertiary = Color(0xFF48618F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E2FF),
    onTertiaryContainer = Color(0xFF001A42),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EC),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF0DED8),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Color(0xFFD0BCFF),
)

// ───────── типографика ─────────
// Google Sans bundled with the app, so Samsung and other devices render the same typography.

private val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold),
)

private fun googleSansStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = GoogleSans,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontWeight = weight,
)

private val NaviTypography = Typography(
    displayMedium = googleSansStyle(46, 48, FontWeight.Bold, -1.2f),
    displaySmall = googleSansStyle(38, 40, FontWeight.Bold, -0.8f),
    headlineLarge = googleSansStyle(32, 36, FontWeight.Bold, -0.4f),
    headlineMedium = googleSansStyle(28, 32, FontWeight.Bold, -0.25f),
    headlineSmall = googleSansStyle(24, 30, FontWeight.Normal),
    titleLarge = googleSansStyle(21, 26, FontWeight.Bold),
    titleMedium = googleSansStyle(17, 22, FontWeight.Medium, 0.05f),
    titleSmall = googleSansStyle(15, 20, FontWeight.Medium),
    bodyLarge = googleSansStyle(17, 24, FontWeight.Normal),
    bodyMedium = googleSansStyle(15, 20, FontWeight.Normal),
    bodySmall = googleSansStyle(13, 18, FontWeight.Normal),
    labelLarge = googleSansStyle(15, 20, FontWeight.Medium),
    labelMedium = googleSansStyle(13, 16, FontWeight.Medium),
    labelSmall = googleSansStyle(11, 16, FontWeight.Medium),
)

// ───────── формы ─────────

private val NaviShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun NaviTheme(settings: AndroidAppearanceSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val ctx = LocalContext.current
    val baseScheme = when {
        settings.accent == AppAccent.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    val accentedScheme = if (settings.accent == AppAccent.Dynamic) baseScheme else {
        val accent = if (dark) settings.accent.dark else settings.accent.light
        baseScheme.copy(
            primary = accent,
            secondary = accent,
            inversePrimary = if (dark) settings.accent.light else settings.accent.dark,
        )
    }
    val scheme = if (dark && settings.amoled) {
        accentedScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1B1B1B),
        )
    } else accentedScheme
    MaterialTheme(colorScheme = scheme, typography = NaviTypography, shapes = NaviShapes, content = content)
}
