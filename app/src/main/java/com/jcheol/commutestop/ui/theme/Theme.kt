package com.jcheol.commutestop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = WarmWhite,
    secondary = Mint,
    onSecondary = DeepNavy,
    background = WarmWhite,
    onBackground = Ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    surfaceVariant = SoftMint,
    onSurfaceVariant = MutedInk,
)

private val DarkColors = darkColorScheme(
    primary = ColorTokens.LightBlue,
    onPrimary = DeepNavy,
    secondary = Mint,
    onSecondary = DeepNavy,
    background = DeepNavy,
    onBackground = WarmWhite,
    surface = DarkSurface,
    onSurface = WarmWhite,
    surfaceVariant = Navy,
    onSurfaceVariant = ColorTokens.LightMuted,
)

private object ColorTokens {
    val LightBlue = androidx.compose.ui.graphics.Color(0xFF8FC0FF)
    val LightMuted = androidx.compose.ui.graphics.Color(0xFFC3CDD4)
}

@Composable
fun CommuteStopTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
