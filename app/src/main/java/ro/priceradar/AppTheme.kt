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
        primary = Color(0xFFA8F56A), onPrimary = Color(0xFF163800),
        primaryContainer = Color(0xFF244318), onPrimaryContainer = Color(0xFFD3FFB1),
        background = Color(0xFF080B09), surface = Color(0xFF111713),
        surfaceVariant = Color(0xFF1B251E), onSurfaceVariant = Color(0xFFB8C7BB),
        secondary = Color(0xFFFFB95D), secondaryContainer = Color(0xFF493314),
        onSecondaryContainer = Color(0xFFFFDDB1),
        tertiary = Color(0xFF79D7FF), tertiaryContainer = Color(0xFF123E50),
        surfaceContainer = Color(0xFF111713), surfaceContainerHigh = Color(0xFF18201A),
        surfaceContainerHighest = Color(0xFF202A22), outlineVariant = Color(0xFF344238)
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

