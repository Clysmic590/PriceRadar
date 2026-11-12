package ro.priceradar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val LocalOneUiMode = staticCompositionLocalOf { false }

@Composable
fun RadarTheme(oneUiMode: Boolean = true, accent: Color = Color(0xFF4C8DFF), content: @Composable () -> Unit) {
    val radarColors = darkColorScheme(
        primary = Color(0xFFA8C7FF), onPrimary = Color(0xFF092650),
        primaryContainer = Color(0xFFB6D0FF), onPrimaryContainer = Color(0xFF0B2449),
        background = Color(0xFF090B10), surface = Color(0xFF191D25),
        surfaceVariant = Color(0xFF243146), onSurfaceVariant = Color(0xFFC3C6D0),
        secondary = Color(0xFFBAC8ED), secondaryContainer = Color(0xFF283E65),
        onSecondaryContainer = Color(0xFFDCE5FF),
        tertiary = Color(0xFF79D7FF), tertiaryContainer = Color(0xFF123E50),
        surfaceContainer = Color(0xFF191D25), surfaceContainerHigh = Color(0xFF222832),
        surfaceContainerHighest = Color(0xFF243146), outlineVariant = Color(0xFF354154)
    )
    val oneUiColors = darkColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = .30f), onPrimaryContainer = Color.White,
        background = Color(0xFF000000), surface = Color(0xFF171717),
        surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFC7C7C7),
        secondary = Color(0xFF9DBEFF), secondaryContainer = Color(0xFF263D63),
        onSecondaryContainer = Color(0xFFE0EAFF),
        tertiary = Color(0xFF72D6FF), tertiaryContainer = Color(0xFF143F50),
        surfaceContainer = Color(0xFF171717), surfaceContainerHigh = Color(0xFF202020),
        surfaceContainerHighest = Color(0xFF292929), outlineVariant = Color(0xFF3B3B3B)
    )
    val typography = Typography().let {
        it.copy(headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalOneUiMode provides oneUiMode) {
        MaterialTheme(
            colorScheme = if (oneUiMode) oneUiColors else radarColors,
            shapes = if (oneUiMode) Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp)
            ) else Shapes(
                small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
            ),
            typography = typography,
            content = content
        )
    }
}

