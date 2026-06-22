package com.sednium.localspaces.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The original app actually pins `isDark = false` and forces the light
 * "sedYellow / sedRed" palette everywhere (see App.tsx:359), keeping the
 * `.dark` CSS rules only as a latent secondary theme. We mirror that
 * intent: SedniumTheme defaults to Light, but a fully working Dark
 * ColorScheme is provided for parity / future use.
 */

private val SedniumLightColors = lightColorScheme(
    primary = SedniumColors.Orange,
    onPrimary = SedniumColors.Milk,
    background = SedniumColors.Milk,
    onBackground = SedniumColors.Orange,
    surface = SedniumColors.Milk,
    onSurface = SedniumColors.Orange,
    surfaceVariant = OrangeAlpha.a05,
    onSurfaceVariant = SedniumColors.Orange,
    outline = OrangeAlpha.a30,
    outlineVariant = OrangeAlpha.a20,
    error = SedniumColors.Red600,
    onError = SedniumColors.White,
    errorContainer = SedniumColors.Red100,
    onErrorContainer = SedniumColors.Red800
)

private val SedniumDarkColors = darkColorScheme(
    primary = SedniumColors.Orange,
    onPrimary = SedniumColors.Milk,
    background = SedniumColors.DarkBackground,
    onBackground = SedniumColors.Gray100,
    surface = SedniumColors.DarkSurfaceAlt,
    onSurface = SedniumColors.Gray100,
    surfaceVariant = SedniumColors.Gray800,
    onSurfaceVariant = SedniumColors.Gray300,
    outline = SedniumColors.Gray700,
    outlineVariant = SedniumColors.Gray800,
    error = SedniumColors.Red500,
    onError = SedniumColors.White,
    errorContainer = SedniumColors.Red900,
    onErrorContainer = SedniumColors.Red100
)

/** Exposes a simple boolean so deep components can branch like the TSX did with `isDark`. */
val LocalSedniumIsDark = staticCompositionLocalOf { false }

@Composable
fun SedniumTheme(
    darkTheme: Boolean = false, // app forces light by default, matching App.tsx
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) SedniumDarkColors else SedniumLightColors

    androidx.compose.runtime.CompositionLocalProvider(LocalSedniumIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = SedniumTypography,
            shapes = SedniumShapes,
            content = content
        )
    }
}
