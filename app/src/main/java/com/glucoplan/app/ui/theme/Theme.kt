package com.glucoplan.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1a6fc4)
val InRange = Color(0xFF43A047)
val Low = Color(0xFFFF9800)
val High = Color(0xFFFF9800)
val Hypo = Color(0xFFE53935)
val Hyper = Color(0xFFE53935)

@Composable
fun GlucoseColor(glucose: Double, min: Double, max: Double): Color = when {
    glucose < 3.9  -> Hypo
    glucose < min  -> Low
    glucose <= max -> InRange
    glucose <= 10  -> High
    else           -> Hyper
}

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    secondary = Color(0xFFBBC8DB),
    tertiary = Color(0xFFD6BEE4)
)

@Composable
fun GlucoPlanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    val uiMode = androidx.compose.ui.platform.LocalContext.current.resources.configuration.uiMode
    return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
}
