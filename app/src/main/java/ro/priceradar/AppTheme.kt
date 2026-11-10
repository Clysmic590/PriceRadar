package ro.priceradar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppTheme(val label: String, val description: String) {
    RADAR("Original", "Dark forest tones with a lime glow"),
    ONE_UI("One UI", "Inspired by rounded panels and calm blue accents"),
    COLOR_OS("ColorOS", "Inspired by airy surfaces and fresh green accents")
}

@Composable
fun RadarTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val target = when (theme) {
        AppTheme.RADAR -> darkColorScheme(primary = Color(0xFFB6F36A), onPrimary = Color(0xFF10140F), background = Color(0xFF10140F), surface = Color(0xFF1B211A), secondary = Color(0xFFA2C3EE))
        AppTheme.ONE_UI -> darkColorScheme(primary = Color(0xFF9DBDFF), onPrimary = Color(0xFF002E69), background = Color(0xFF08090C), surface = Color(0xFF17191F), surfaceVariant = Color(0xFF282C36), onSurfaceVariant = Color(0xFFC3C6D0), secondary = Color(0xFFBAC8ED), secondaryContainer = Color(0xFF283E65), onSecondaryContainer = Color(0xFFDCE5FF))
        AppTheme.COLOR_OS -> lightColorScheme(primary = Color(0xFF006B54), onPrimary = Color.White, background = Color(0xFFF2F7F3), surface = Color(0xFFFCFFFC), surfaceVariant = Color(0xFFDEEAE2), onSurfaceVariant = Color(0xFF3F4C44), secondary = Color(0xFF426653), secondaryContainer = Color(0xFFCEEEDB), onSecondaryContainer = Color(0xFF102F20))
    }
    val primary by animateColorAsState(target.primary, tween(300), label = "theme accent")
    val background by animateColorAsState(target.background, tween(300), label = "theme background")
    val surface by animateColorAsState(target.surface, tween(300), label = "theme surface")
    val radius = when (theme) { AppTheme.RADAR -> 12.dp; AppTheme.ONE_UI -> 28.dp; AppTheme.COLOR_OS -> 22.dp }
    val typography = Typography().let {
        if (theme == AppTheme.ONE_UI) it.copy(headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold), headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold)) else it
    }
    MaterialTheme(
        colorScheme = target.copy(primary = primary, background = background, surface = surface,
            surfaceContainer = if (theme == AppTheme.RADAR) target.surfaceContainer else target.surface,
            surfaceContainerHigh = if (theme == AppTheme.RADAR) target.surfaceContainerHigh else target.surface,
            surfaceContainerHighest = if (theme == AppTheme.RADAR) target.surfaceContainerHighest else target.surfaceVariant),
        shapes = if (theme == AppTheme.RADAR) Shapes() else Shapes(medium = RoundedCornerShape(radius), large = RoundedCornerShape(radius), extraLarge = RoundedCornerShape(32.dp)),
        typography = typography,
        content = content
    )
}
