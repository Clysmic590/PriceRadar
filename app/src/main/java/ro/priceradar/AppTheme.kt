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
        primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF002E69),
        background = Color(0xFF0B1019), surface = Color(0xFF151E2C),
        surfaceVariant = Color(0xFF243146), onSurfaceVariant = Color(0xFFC3C6D0),
        secondary = Color(0xFFBAC8ED), secondaryContainer = Color(0xFF283E65),
        onSecondaryContainer = Color(0xFFDCE5FF),
        surfaceContainer = Color(0xFF151E2C), surfaceContainerHigh = Color(0xFF151E2C),
        surfaceContainerHighest = Color(0xFF243146)
    )
    val typography = Typography().let {
        it.copy(headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold))
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(32.dp)),
        typography = typography,
        content = content
    )
}
