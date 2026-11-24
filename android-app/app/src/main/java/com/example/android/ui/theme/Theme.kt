package id.panenku.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PanenkuColors {
    val Green = Color(0xFF3EAB36)
    val Forest = Color(0xFF275823)
    val Ink = Color(0xFF1C1C1C)
    val Muted = Color(0xFF727272)
    val Background = Color(0xFFFAFAFA)
    val Border = Color(0xFFE6E6E6)
    val SoftGreen = Color(0xFFF3FBF2)
    val Orange = Color(0xFFAF6000)
    val SoftOrange = Color(0xFFFFF5E8)
    val Red = Color(0xFFB3261E)
}
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val page = 20.dp
    val maxWidth = 840.dp
}
private val Light = lightColorScheme(
    primary = PanenkuColors.Green, onPrimary = Color.White,
    primaryContainer = PanenkuColors.SoftGreen, onPrimaryContainer = PanenkuColors.Forest,
    secondary = PanenkuColors.Forest,
    secondaryContainer = PanenkuColors.SoftGreen, onSecondaryContainer = PanenkuColors.Forest,
    outline = PanenkuColors.Border, surfaceTint = PanenkuColors.Green,
    background = PanenkuColors.Background, onBackground = PanenkuColors.Ink,
    surface = Color.White, onSurface = PanenkuColors.Ink,
    surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = PanenkuColors.Muted,
    outlineVariant = PanenkuColors.Border, error = PanenkuColors.Red
)
private val Dark = darkColorScheme(primary = Color(0xFF83D87A), primaryContainer = Color(0xFF23471F))
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)
@Composable
fun PanenkuTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    // Default mengikuti desain terang. Aktifkan isSystemInDarkTheme() bila tema gelap diperlukan.
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = AppTypography, content = content)
}
