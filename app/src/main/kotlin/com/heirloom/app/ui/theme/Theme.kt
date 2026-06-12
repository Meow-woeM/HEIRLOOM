package com.heirloom.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Weathered parchment & ink, with one warm accent (ember orange). No stock Material look.
val Parchment = Color(0xFFF3EAD8)
val ParchmentRaised = Color(0xFFFAF4E6)
val ParchmentShade = Color(0xFFE6D7BB)
val Ink = Color(0xFF2A2118)
val InkFaded = Color(0xFF5C4F3F)
val Umber = Color(0xFF6B4F35)
val Sepia = Color(0xFF9A7B5A)
val Ember = Color(0xFFC75B1E)
val EmberDeep = Color(0xFF9C4514)

// Lantern-lit dark variant.
val Nightwood = Color(0xFF211912)
val NightwoodRaised = Color(0xFF2E2418)
val NightwoodShade = Color(0xFF191209)
val Candlelight = Color(0xFFEADFC4)
val CandlelightFaded = Color(0xFFB3A689)
val EmberNight = Color(0xFFE07B3C)

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = ParchmentRaised,
    primaryContainer = ParchmentShade,
    onPrimaryContainer = EmberDeep,
    secondary = Umber,
    onSecondary = Parchment,
    secondaryContainer = ParchmentShade,
    onSecondaryContainer = Ink,
    background = Parchment,
    onBackground = Ink,
    surface = ParchmentRaised,
    onSurface = Ink,
    surfaceVariant = ParchmentShade,
    onSurfaceVariant = InkFaded,
    outline = Sepia,
    error = Color(0xFF8E2F1C),
    onError = Parchment,
)

private val DarkColors = darkColorScheme(
    primary = EmberNight,
    onPrimary = NightwoodShade,
    primaryContainer = NightwoodRaised,
    onPrimaryContainer = EmberNight,
    secondary = CandlelightFaded,
    onSecondary = Nightwood,
    secondaryContainer = NightwoodRaised,
    onSecondaryContainer = Candlelight,
    background = Nightwood,
    onBackground = Candlelight,
    surface = NightwoodRaised,
    onSurface = Candlelight,
    surfaceVariant = NightwoodShade,
    onSurfaceVariant = CandlelightFaded,
    outline = Color(0xFF6F5F45),
    error = Color(0xFFD9745C),
    onError = NightwoodShade,
)

// Serif display type for headers; default sans for body readability.
private val FrontierTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun HeirloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FrontierTypography,
        content = content,
    )
}
