package com.drygonkiller.wordsearch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val light = lightColorScheme(
    primary = Color(0xFF265D48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EDE1),
    onPrimaryContainer = Color(0xFF184733),
    secondaryContainer = Color(0xFFE4ECDC),
    onSecondaryContainer = Color(0xFF22503B),
    tertiary = Color(0xFF70511A),
    tertiaryContainer = Color(0xFFF6E7C6),
    onTertiaryContainer = Color(0xFF584318),
    background = Color(0xFFF5F7F1),
    surface = Color(0xFFFCFDF8),
    onSurface = Color(0xFF192B22),
    onSurfaceVariant = Color(0xFF4D6255),
    onBackground = Color(0xFF192B22),
    surfaceContainer = Color(0xFFEDF2E8),
    surfaceContainerHigh = Color(0xFFE4EDE2),
    outline = Color(0xFF748778),
    outlineVariant = Color(0xFFD9E4D8),
)

private val dark = darkColorScheme(
    primary = Color(0xFFA6DBC0),
    onPrimary = Color(0xFF153E2C),
    primaryContainer = Color(0xFF244D3D),
    onPrimaryContainer = Color(0xFFCBEAD8),
    secondaryContainer = Color(0xFF314C3E),
    onSecondaryContainer = Color(0xFFCFEDDA),
    tertiary = Color(0xFFF0D697),
    tertiaryContainer = Color(0xFF4E3D20),
    onTertiaryContainer = Color(0xFFFCE8B8),
    background = Color(0xFF111B16),
    surface = Color(0xFF1B2821),
    onSurface = Color(0xFFE8F0E8),
    onSurfaceVariant = Color(0xFFB8C8BB),
    onBackground = Color(0xFFE8F0E8),
    surfaceContainer = Color(0xFF23322A),
    surfaceContainerHigh = Color(0xFF2D4034),
    outline = Color(0xFF8BA297),
    outlineVariant = Color(0xFF3A4F42),
)

private val defaultTypography = Typography()
private val type = defaultTypography.copy(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
        fontSize = 40.sp, lineHeight = 43.sp, letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
        fontSize = 25.sp, lineHeight = 29.sp,
    ),
    titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaultTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun WordSearchTheme(isDark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark) dark else light, typography = type, content = content)
}

@Composable
fun Glyph(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    dimension: Dp = 24.dp,
) {
    Canvas(modifier.size(dimension)) {
        val unit = size.minDimension / 24f
        val origin = Offset((size.width - 24 * unit) / 2, (size.height - 24 * unit) / 2)
        val stroke = 1.65f * unit
        fun p(x: Int, y: Int) = origin + Offset(x * unit, y * unit)
        fun line(vararg xy: Int) {
            for (i in 0 until xy.size - 2 step 2) {
                drawLine(color, p(xy[i], xy[i + 1]), p(xy[i + 2], xy[i + 3]), stroke, StrokeCap.Round)
            }
        }
        fun circle(x: Int, y: Int, radius: Int) {
            drawCircle(color, radius * unit, p(x, y), style = Stroke(stroke))
        }
        fun oval(x: Int, y: Int, width: Int, height: Int) {
            drawOval(color, p(x, y), Size(width * unit, height * unit), style = Stroke(stroke))
        }
        when (name) {
            "home" -> { line(3, 11, 12, 4, 21, 11); line(5, 10, 5, 20, 10, 20, 10, 15, 14, 15, 14, 20, 19, 20, 19, 10) }
            "grid" -> { line(4, 4, 20, 4, 20, 20, 4, 20, 4, 4); line(12, 4, 12, 20); line(4, 12, 20, 12) }
            "stats" -> { line(4, 20, 21, 20); line(7, 17, 7, 11); line(12, 17, 12, 6); line(17, 17, 17, 9) }
            "settings" -> {
                line(4, 6, 7, 6); line(11, 6, 20, 6); circle(9, 6, 2)
                line(4, 12, 13, 12); line(17, 12, 20, 12); circle(15, 12, 2)
                line(4, 18, 7, 18); line(11, 18, 20, 18); circle(9, 18, 2)
            }
            "coin" -> { circle(12, 12, 8); line(14, 9, 11, 9, 10, 12, 14, 12, 13, 15, 10, 15) }
            "calendar" -> { line(4, 6, 20, 6, 20, 20, 4, 20, 4, 6); line(4, 10, 20, 10); line(8, 4, 8, 8); line(16, 4, 16, 8); line(9, 15, 11, 17, 15, 13) }
            "check" -> line(5, 12, 10, 17, 19, 7)
            "back" -> { line(14, 5, 7, 12, 14, 19); line(7, 12, 21, 12) }
            "next" -> { line(10, 5, 17, 12, 10, 19); line(3, 12, 17, 12) }
            "pause" -> { line(9, 6, 9, 18); line(15, 6, 15, 18) }
            "clock" -> { circle(12, 12, 8); line(12, 7, 12, 12, 16, 14) }
            "hint" -> { line(9, 18, 15, 18); line(10, 21, 14, 21); line(9, 15, 6, 11, 7, 6, 12, 3, 17, 6, 18, 11, 15, 15) }
            "paw" -> { circle(5, 11, 2); circle(9, 6, 2); circle(15, 6, 2); circle(19, 11, 2); oval(7, 13, 10, 7) }
            "food" -> { line(6, 4, 6, 10, 11, 10, 11, 4); line(8, 4, 8, 20); line(17, 20, 17, 4, 14, 8, 14, 13, 17, 13) }
            "travel" -> { line(3, 10, 21, 4, 15, 21, 11, 14, 3, 10); line(11, 14, 21, 4) }
            "sports" -> { circle(12, 12, 8); line(4, 12, 20, 12); line(12, 4, 12, 20); line(6, 7, 18, 17); line(18, 7, 6, 17) }
            "leaf" -> { line(6, 18, 6, 12, 10, 7, 20, 4, 19, 13, 14, 18, 6, 18, 4, 20); line(6, 18, 15, 10) }
            "globe" -> { circle(12, 12, 8); oval(8, 4, 8, 16); line(4, 12, 20, 12) }
            "jobs" -> { line(3, 8, 21, 8, 21, 19, 3, 19, 3, 8); line(8, 8, 8, 5, 16, 5, 16, 8); line(3, 12, 12, 15, 21, 12) }
            "book" -> { line(12, 7, 8, 5, 3, 5, 3, 18, 8, 18, 12, 20, 16, 18, 21, 18, 21, 5, 16, 5, 12, 7, 12, 20) }
            "film" -> { line(3, 10, 21, 10, 21, 20, 3, 20, 3, 10, 3, 7, 20, 3, 21, 7, 4, 11); line(8, 6, 10, 9); line(14, 5, 16, 8) }
            "science" -> { line(9, 4, 15, 4); line(10, 4, 10, 11, 5, 19, 19, 19, 14, 11, 14, 4); line(8, 15, 16, 15) }
            "star" -> line(12, 3, 15, 9, 21, 10, 16, 14, 18, 21, 12, 17, 6, 21, 8, 14, 3, 10, 9, 9, 12, 3)
            else -> line(8, 5, 18, 12, 8, 19, 8, 5)
        }
    }
}
