package ro.priceradar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RadarTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
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
    val typography = Typography().let {
        it.copy(headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold))
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)),
        typography = typography,
        content = content
    )
}

