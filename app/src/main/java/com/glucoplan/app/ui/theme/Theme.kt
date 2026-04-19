package com.glucoplan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Primary = Color(0xFF1a6fc4)
val InRange  = Color(0xFF43A047)
val Low      = Color(0xFFFF9800)  // чуть ниже нормы
val High     = Color(0xFFE65100)  // выше нормы
val Hypo     = Color(0xFFE53935)  // опасно низкий
val Hyper    = Color(0xFFB71C1C)  // опасно высокий

@Composable
fun GlucoseColor(glucose: Double, min: Double, max: Double): Color = when {
    glucose < 3.9  -> Hypo
    glucose < min  -> Low
    glucose <= max -> InRange
    glucose <= 10  -> High
    else           -> Hyper
}

// ─── Typography ───────────────────────────────────────────────────────────────

private val AppTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 11.sp, lineHeight = 16.sp),
)

// ─── Shapes ───────────────────────────────────────────────────────────────────

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ─── Color schemes ────────────────────────────────────────────────────────────

private val LightScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    secondary        = Color(0xFF535F70),
    tertiary         = Color(0xFF6B5778)
)

private val DarkScheme = darkColorScheme(
    primary          = Color(0xFF9ECAFF),
    onPrimary        = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    secondary        = Color(0xFFBBC8DB),
    tertiary         = Color(0xFFD6BEE4)
)

@Composable
fun GlucoPlanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    val uiMode = androidx.compose.ui.platform.LocalContext.current.resources.configuration.uiMode
    return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
}
