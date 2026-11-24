package ro.priceradar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PageHeading(title: String, subtitle: String) {
    val oneUi = LocalOneUiMode.current
    val reducedMotion = LocalReducedMotion.current
    var shown by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(260)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 4 }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val constrained = maxWidth < 390.dp || LocalDensity.current.fontScale > 1.15f
            Column(Modifier.fillMaxWidth().padding(top = if (oneUi) 42.dp else 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!oneUi) Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Text("LIVE DEAL RADAR", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text(title, style = if (constrained) MaterialTheme.typography.headlineLarge else if (oneUi) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text(subtitle, style = if (constrained) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WatchOverview(total: Int, active: Int, reached: Int, failed: Int) {
    val accent = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > .5f
    val gradient = if (lightTheme) listOf(
        lerp(MaterialTheme.colorScheme.surface, accent, .14f),
        lerp(MaterialTheme.colorScheme.surface, accent, .08f),
        MaterialTheme.colorScheme.surface
    ) else listOf(
        lerp(Color(0xFF111820), accent, .32f),
        lerp(Color(0xFF10151C), accent, .18f),
        lerp(Color(0xFF101419), accent, .09f)
    )
    BoxWithConstraints {
        val constrained = maxWidth < 370.dp || LocalDensity.current.fontScale > 1.15f
        Surface(color = Color.Transparent, shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.linearGradient(gradient))
                    .border(1.dp, accent.copy(alpha = .34f), RoundedCornerShape(28.dp))
                    .padding(if (constrained) 17.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(7.dp).background(accent, CircleShape))
                            Text(if (failed > 0) "WORKING PARTIALLY" else if (active > 0) "RADAR ACTIVE" else "RADAR STANDBY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("$total", fontSize = if (constrained) 40.sp else 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = if (lightTheme) MaterialTheme.colorScheme.onSurface else Color.White)
                            Text(if (total == 1) "product tracked" else "products tracked", Modifier.padding(bottom = 7.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (lightTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFCAD8CC), maxLines = 2)
                        }
                    }
                    AnimatedRadarOrb(active, failed, accent, compact = true)
                }
                HorizontalDivider(color = accent.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$active watching", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f), CircleShape))
                    Text("$reached targets met", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (failed > 0) {
                        Spacer(Modifier.weight(1f))
                        Text("$failed issue${if (failed == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedRadarOrb(active: Int, failed: Int, accent: Color, compact: Boolean = false) {
    val reduced = LocalReducedMotion.current
    val animate = !reduced && active > 0 && failed == 0
    val angle: Float
    val pulse: Float
    if (animate) {
        val motion = rememberInfiniteTransition(label = "dashboard radar")
        angle = motion.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "radar rotation").value
        pulse = motion.animateFloat(.9f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "radar pulse").value
    } else {
        angle = 0f
        pulse = 1f
    }
    Surface(shape = CircleShape, color = accent.copy(alpha = if (active > 0) .16f else .08f), modifier = Modifier
        .graphicsLayer { scaleX = pulse; scaleY = pulse }
        .border(1.dp, accent.copy(alpha=.38f), CircleShape)) {
        Icon(Icons.Default.Radar, null, Modifier.padding(if (compact) 12.dp else 18.dp).size(if (compact) 28.dp else 38.dp).graphicsLayer { rotationZ = angle }, tint = accent)
    }
}

internal fun radarStatus(active: Int, failed: Int): String = when {
    failed > 0 -> "●  PRICERADAR WORKING PARTIALLY"
    active > 0 -> "●  RADAR ACTIVE"
    else -> "RADAR STANDBY"
}

@Composable
private fun OverviewMetric(value: String, label: String, modifier: Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > .5f
    Column(modifier) {
        AnimatedContent(targetState = value, transitionSpec = {
            if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
            else (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .88f)) togetherWith (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.08f))
        }, label = "overview metric") { animatedValue ->
            Text(animatedValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (lightTheme) MaterialTheme.colorScheme.onSurface else Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (lightTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFB8C7BB))
    }
}

@Composable
fun DiscoveryTile(onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val motion = rememberInfiniteTransition(label = "discovery cue")
    val nudge by motion.animateFloat(0f, 7f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "arrow nudge")
    BoxWithConstraints {
      val constrained = maxWidth < 370.dp || LocalDensity.current.fontScale > 1.15f
      Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=.18f), RoundedCornerShape(24.dp))) {
        Row(Modifier.fillMaxWidth().padding(if (constrained) 14.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(if (constrained) 10.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Search)
            Column(Modifier.weight(1f)) {
                Text("Hunt for a better deal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Scan eMAG, Vexio & Altex", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, null, Modifier.graphicsLayer { translationX = if (reduced) 0f else nudge }, tint = MaterialTheme.colorScheme.primary)
        }
      }
    }
}

@Composable
fun IconTile(icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .28f), shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun PreferenceRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupDivider() { HorizontalDivider(Modifier.padding(start = 78.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }

@Composable
fun OneUiSettingsHeader() {
    Column(
        Modifier.fillMaxWidth().padding(top = 54.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
        Text("PriceRadar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OneUiSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            title,
            Modifier.padding(start = 18.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun OneUiSettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary) {
                Icon(icon, null, Modifier.padding(10.dp).size(23.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
                Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing() else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OneUiDivider() {
    HorizontalDivider(Modifier.padding(start = 77.dp), color = Color(0xFF303030), thickness = 0.7.dp)
}

@Composable
fun OneUiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFF9A9AA2),
            uncheckedTrackColor = Color(0xFF35353B),
            uncheckedBorderColor = Color(0xFF74747D)
        )
    )
}

@Composable
fun OneUiAccentPicker(selected: Color, onSelected: (Color) -> Unit) {
    val colors = OneUiAccentPalette
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Colour palette", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF5F5F5))
        Text("Choose the accent used for icons, switches and selected controls.", fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFAAAAAA))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            colors.forEach { color ->
                Surface(
                    onClick = { onSelected(color) },
                    shape = CircleShape,
                    color = color,
                    border = if (selected.toArgb() == color.toArgb()) androidx.compose.foundation.BorderStroke(3.dp, Color.White) else null,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (selected.toArgb() == color.toArgb()) Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, Modifier.size(19.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun HelpDisclosure(title: String, body: String, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand")
            }
            if (expanded) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StoreBadge(store: Retailer) {
    val color = when (store) {
        Retailer.EMAG -> Color(0xFF8DBBFF)
        Retailer.VEXIO -> Color(0xFFD2B5FF)
        Retailer.ALTEX -> Color(0xFFFFB6AE)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
        Text(store.label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}


