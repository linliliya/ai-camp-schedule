package cn.aurange.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF182230)
val Navy = Color(0xFF344054)
val Muted = Color(0xFF475467)
val Canvas = Color(0xFFF5F6F8)
val Line = Color(0xFFD7DEE8)
val Blue = Color(0xFF3659B8)
val Lavender = Color(0xFFEAF0FC)
val Amber = Color(0xFF9A4B0A)
val Sage = Color(0xFF18794E)
val Danger = Color(0xFFB42318)

val EditorialFont = FontFamily(Font(R.font.lora_regular, FontWeight.Normal))

private val AppFont = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_medium, FontWeight.SemiBold),
    Font(R.font.misans_medium, FontWeight.Bold)
)

fun phaseColor(type: String): Color = when (type) {
    "prep" -> Color(0xFF1D6B64)
    "open" -> Blue
    "regular" -> Sage
    "exam" -> Amber
    "close" -> Color(0xFF6B4897)
    else -> Muted
}
fun phaseBackground(type: String): Color = when (type) {
    "prep" -> Color(0xFFE8F3F0)
    "open" -> Lavender
    "regular" -> Color(0xFFE9F4ED)
    "exam" -> Color(0xFFFFF1DE)
    "close" -> Color(0xFFF1ECF8)
    else -> Color(0xFFF0F2F5)
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
).let { t ->
    fun TextStyle.chinese() = copy(fontFamily = AppFont, letterSpacing = 0.sp)
    t.copy(displayLarge = t.displayLarge.chinese(), displayMedium = t.displayMedium.chinese(), displaySmall = t.displaySmall.chinese(),
        headlineLarge = t.headlineLarge.chinese(), headlineMedium = t.headlineMedium.chinese(), headlineSmall = t.headlineSmall.chinese(),
        titleLarge = t.titleLarge.chinese(), titleMedium = t.titleMedium.chinese(), titleSmall = t.titleSmall.chinese(),
        bodyLarge = t.bodyLarge.chinese(), bodyMedium = t.bodyMedium.chinese(), bodySmall = t.bodySmall.chinese(),
        labelLarge = t.labelLarge.chinese(), labelMedium = t.labelMedium.chinese(), labelSmall = t.labelSmall.chinese())
}

@Composable fun ScheduleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White,
        primaryContainer = Lavender, onPrimaryContainer = Blue, secondary = Sage,
        secondaryContainer = Lavender, onSecondaryContainer = Blue, background = Canvas, surface = Color.White,
        surfaceContainer = Canvas, surfaceContainerHigh = Color.White, surfaceContainerHighest = Lavender,
        onSurface = Ink, onBackground = Ink, outline = Color(0xFF7C8BA1), outlineVariant = Line,
        surfaceVariant = Canvas, onSurfaceVariant = Muted, error = Danger),
        typography = AppTypography, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)), content = content)
}
