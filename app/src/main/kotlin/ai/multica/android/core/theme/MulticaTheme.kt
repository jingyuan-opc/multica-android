package ai.multica.android.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

/**
 * Theme entry point. Pass [darkTheme] explicitly to override the
 * system setting (e.g. from Settings → Theme picker).
 *
 * For v1 we use the static brand palette. Material You dynamic color
 * (Android 12+) is exposed via [dynamicColor] but disabled by default
 * — the multica brand is a deliberate product choice, not a "use
 * whatever wallpaper color" experience.
 */
@Composable
fun MulticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MulticaTypography,
        shapes = MulticaShapes,
        content = content,
    )
}

private val LightColors = lightColorScheme(
    primary = Color(BrandColors.primaryLight),
    onPrimary = Color(BrandColors.primaryForegroundLight),
    primaryContainer = Color(BrandColors.brandLight),
    onPrimaryContainer = Color(BrandColors.brandLightForeground),
    secondary = Color(BrandColors.secondaryLight),
    onSecondary = Color(BrandColors.secondaryForegroundLight),
    secondaryContainer = Color(BrandColors.secondaryLight),
    onSecondaryContainer = Color(BrandColors.secondaryForegroundLight),
    tertiary = Color(BrandColors.info),
    onTertiary = Color(BrandColors.brandLightForeground),
    background = Color(BrandColors.backgroundLight),
    onBackground = Color(BrandColors.foregroundLight),
    surface = Color(BrandColors.cardLight),
    onSurface = Color(BrandColors.cardForegroundLight),
    surfaceVariant = Color(BrandColors.mutedLight),
    onSurfaceVariant = Color(BrandColors.mutedForegroundLight),
    error = Color(BrandColors.destructiveLight),
    onError = Color(BrandColors.destructiveForegroundLight),
    outline = Color(BrandColors.borderLight),
    outlineVariant = Color(BrandColors.borderLight),
)

private val DarkColors = darkColorScheme(
    primary = Color(BrandColors.primaryDark),
    onPrimary = Color(BrandColors.primaryForegroundDark),
    primaryContainer = Color(BrandColors.brandDark),
    onPrimaryContainer = Color(BrandColors.brandDarkForeground),
    secondary = Color(BrandColors.secondaryDark),
    onSecondary = Color(BrandColors.secondaryForegroundDark),
    secondaryContainer = Color(BrandColors.secondaryDark),
    onSecondaryContainer = Color(BrandColors.secondaryForegroundDark),
    tertiary = Color(BrandColors.info),
    onTertiary = Color(BrandColors.brandDarkForeground),
    background = Color(BrandColors.backgroundDark),
    onBackground = Color(BrandColors.foregroundDark),
    surface = Color(BrandColors.cardDark),
    onSurface = Color(BrandColors.cardForegroundDark),
    surfaceVariant = Color(BrandColors.mutedDark),
    onSurfaceVariant = Color(BrandColors.mutedForegroundDark),
    error = Color(BrandColors.destructiveDark),
    onError = Color(BrandColors.destructiveForegroundDark),
    outline = Color(BrandColors.borderDark),
    outlineVariant = Color(BrandColors.borderDark),
)
