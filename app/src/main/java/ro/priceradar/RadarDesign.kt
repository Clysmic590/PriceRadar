package ro.priceradar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PageHeading(title: String, subtitle: String) {
    val oneUi = LocalOneUiMode.current
    Column(Modifier.fillMaxWidth().padding(top = if (oneUi) 42.dp else 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!oneUi) Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Text("LIVE DEAL RADAR", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text(title, style = if (oneUi) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WatchOverview(total: Int, active: Int, reached: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(color = Color.Transparent, shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF203C66), Color(0xFF17273F), Color(0xFF17212F)))).border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(32.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (active > 0) "●  RADAR ACTIVE" else "RADAR STANDBY", style = MaterialTheme.typography.labelMedium, color = accent)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("$total", fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(if (total == 1) "product tracked" else "products tracked", Modifier.padding(bottom = 8.dp), color = Color(0xFFCAD8CC))
                    }
                }
                AnimatedRadarOrb(active)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric("$active", "Watching", Modifier.weight(1f))
                OverviewMetric("$reached", "Targets met", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnimatedRadarOrb(active: Int) {
    val reduced = LocalReducedMotion.current
    val motion = rememberInfiniteTransition(label = "dashboard radar")
    val angle by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "radar rotation")
    val pulse by motion.animateFloat(.9f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "radar pulse")
    val accent = MaterialTheme.colorScheme.primary
    Surface(shape = CircleShape, color = accent.copy(alpha = if (active > 0) .16f else .08f), modifier = Modifier
        .graphicsLayer { scaleX = if (reduced || active == 0) 1f else pulse; scaleY = scaleX }
        .border(1.dp, accent.copy(alpha=.38f), CircleShape)) {
        Icon(Icons.Default.Radar, null, Modifier.padding(18.dp).size(38.dp).graphicsLayer { rotationZ = if (reduced || active == 0) 0f else angle }, tint = accent)
    }
}

@Composable
private fun OverviewMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8C7BB))
    }
}

@Composable
fun DiscoveryTile(onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val motion = rememberInfiniteTransition(label = "discovery cue")
    val nudge by motion.animateFloat(0f, 7f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "arrow nudge")
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=.18f), RoundedCornerShape(24.dp))) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Search)
            Column(Modifier.weight(1f)) {
                Text("Hunt for a better deal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Scan eMAG, Vexio & Altex", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, null, Modifier.graphicsLayer { translationX = if (reduced) 0f else nudge }, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun IconTile(icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
fun HelpDisclosure(title: String, body: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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

