package ro.priceradar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFFB6F36A)
private val Ink = Color(0xFF10140F)
private val Panel = Color(0xFF1B211A)

class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shared = sharedUrl(intent)
        Alerts.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink, surface = Panel, secondary = Color(0xFFA2C3EE))) {
                PriceRadar(shared) { shared = "" }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); shared = sharedUrl(intent) }
    private fun sharedUrl(intent: Intent) = if (intent.action == Intent.ACTION_SEND) Regex("https://[^\\s]+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value.orEmpty() else ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceRadar(shared: String, consumed: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val products by repo.products.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(shared) { if (shared.isNotBlank()) { url = shared; adding = true; consumed() } }
    fun refresh() { scope.launch { busy = true; try { repo.refresh(context) } finally { busy = false } } }
    val product = products.find { it.id == selected }
    BackHandler(enabled = selected != null) { selected = null }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (product == null) "PriceRadar" else "Product radar", fontWeight = FontWeight.Bold) },
                navigationIcon = { if (product != null) IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { refresh() }, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh prices") } })
        },
        bottomBar = { if (product == null) NavigationBar {
            listOf("Watchlist", "Coupons", "Settings").forEachIndexed { i, title ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(title) }, icon = { Icon(when(i) { 0 -> Icons.Default.Radar; 1 -> Icons.Default.LocalOffer; else -> Icons.Default.Settings }, title) })
            }
        } },
        floatingActionButton = { if (product == null && tab == 0) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Track product") }, containerColor = Mint) },
        containerColor = Ink
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (product != null) {
                ProductDetail(product,
                    save = { target, drops, targets -> scope.launch { repo.settings(product.id, target, drops, targets) } },
                    remove = { scope.launch { repo.remove(product.id); selected = null } })
            } else when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Column(Modifier.padding(vertical = 12.dp)) {
                        Text("BUY AT THE RIGHT MOMENT", color = Mint, style = MaterialTheme.typography.labelMedium)
                        Text("Good prices.\nNo guesswork.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Your personal radar for Romanian retailers", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("${products.size}", "Tracking", Modifier.weight(1f))
                        Stat("${products.count { p -> p.target != null }}", "Price targets", Modifier.weight(1f))
                    } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("All") + Retailer.entries.map { it.label }).forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
                    } }
                    item { Text("Your watchlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (products.isEmpty()) item { InfoCard("Your next great deal starts here", "Paste a product link from eMAG, Vexio or Altex. Price history starts with the first successful check.") }
                    items(products.filter { filter == "All" || it.retailer.label == filter }, key = { it.id }) { p -> ProductCard(p) { selected = p.id } }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Coupon radar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                    item { InfoCard("Public offers, in one place", "Coupon text found on tracked product pages appears here. Availability and checkout eligibility must be verified with the retailer.") }
                    val found = products.filter { it.coupons.isNotEmpty() }
                    if (found.isEmpty()) item { Text("No coupon text detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(found, key = { it.id }) { p -> Card(onClick = { selected = p.id }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(p.retailer.label, color = Mint); Text(p.name, fontWeight = FontWeight.Bold); p.coupons.forEach { Text(it) }; if (p.error != null) Text("Last check failed; offers may be stale", color = MaterialTheme.colorScheme.error) } } }
                }
                else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Your radar, your rules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item { InfoCard("Background checks", "Prices are checked approximately every 6 hours with an internet connection. Android battery management can delay checks. Open the app and tap refresh for an immediate check.") }
                    item { Button(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else Alerts.show(context, "test", "PriceRadar", "Notifications are ready") }) { Text("Enable notifications") } }
                    item { OutlinedButton(onClick = { Alerts.show(context, "test", "PriceRadar", "Your price radar is ready") }) { Text("Send test notification") } }
                    item { InfoCard("Integration status · needs live testing", "eMAG, Vexio and Altex use public HTML extraction. Dynamic pages, anti-bot protections, marketplace sellers and coupon conditions may prevent accurate results. Failed checks never create price points.") }
                    item { InfoCard("Private by design", "Your watchlist and observations stay on this device. Uninstalling clears them. Product URLs are sent directly to the selected retailer; no account or tracking backend is used.") }
                    item { InfoCard("Fake-discount detector", "Compares the advertised reference price with the lowest price observed in the previous 30 days. At least 7 distinct observed days are required. This is a limited-data signal, not a legal determination.") }
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false; error = null }, title = { Text("Track a product") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paste an HTTPS product link from eMAG, Vexio or Altex.")
        OutlinedTextField(url, onValueChange = { url = it; error = null }, label = { Text("Product URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { scope.launch { try { val id = repo.add(url); adding = false; url = ""; selected = id; refresh() } catch (e: Exception) { error = e.message } } }) { Text("Start tracking") } }, dismissButton = { TextButton(onClick = { adding = false; error = null }) { Text("Cancel") } })
}

@Composable private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(20.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, color = Mint, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelLarge) } }
}
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
}
@Composable private fun ProductCard(p: Product, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.retailer.label.uppercase(), color = Mint, style = MaterialTheme.typography.labelMedium); if (p.coupons.isNotEmpty()) Text("Coupon detected", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium) }
        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Text(p.price?.let(::money) ?: "Waiting for first check", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        p.target?.let { Text("Target ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (p.error != null) Text("Check failed · tap for details", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        else Text(if (p.checked == 0L) "Not checked yet" else "Checked ${stamp(p.checked)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
private fun stamp(time: Long) = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))

@Composable private fun ProductDetail(p: Product, save: (Long?, Boolean, Boolean) -> Unit, remove: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var targetText by rememberSaveable(p.id, p.target) { mutableStateOf(p.target?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var drops by rememberSaveable(p.id, p.dropAlerts) { mutableStateOf(p.dropAlerts) }
    var targets by rememberSaveable(p.id, p.targetAlerts) { mutableStateOf(p.targetAlerts) }
    var saved by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val points = p.history.filter { it.time >= System.currentTimeMillis() - period.duration.toMillis() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(p.retailer.label, color = Mint); Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp)) }
        p.error?.let { item { InfoCard("Last check failed", "$it\nLast successful price is preserved.") } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Period.entries.forEach { option -> FilterChip(selected = period == option, onClick = { period = option }, label = { Text(option.label) }) } } }
        item { Card { Column(Modifier.padding(20.dp)) {
            Text("Price history", style = MaterialTheme.typography.titleMedium)
            if (points.size < 2) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Text(if (points.isEmpty()) "No observations in this period" else "First observation collected · check again later", style = MaterialTheme.typography.bodyMedium) }
            else PriceChart(points)
            if (points.isNotEmpty()) { Text("Low ${money(points.minOf { it.price })}  ·  High ${money(points.maxOf { it.price })}", color = Mint, style = MaterialTheme.typography.labelLarge); Text("${stamp(points.first().time)} — ${stamp(points.last().time)}", style = MaterialTheme.typography.labelSmall) }
            Text("Observed prices only; gaps are not reconstructed.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        } } }
        item { InfoCard("Discount check", discountAssessment(p)) }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(targetText, onValueChange = { targetText = it; saved = false }, label = { Text("Target price in RON (optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = targetText.isNotBlank() && parseMoney(targetText) == null)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notify on any price drop", Modifier.weight(1f)); Switch(drops, { drops = it; saved = false }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notify when target is reached", Modifier.weight(1f)); Switch(targets, { targets = it; saved = false }) }
            Button(onClick = { save(targetText.takeIf { it.isNotBlank() }?.let(::parseMoney), drops, targets); saved = true }, enabled = targetText.isBlank() || parseMoney(targetText) != null, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Alerts saved" else "Save alerts") }
        } }
        item { InfoCard("Coupon detection", if (p.coupons.isEmpty()) "No public coupon text found. Some offers appear only in the retailer app, account or checkout." else p.coupons.joinToString("\n\n")) }
        item { OutlinedButton(onClick = { uri.openUri(p.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open at ${p.retailer.label}") } }
        item { TextButton(onClick = { deleting = true }) { Text("Stop tracking", color = MaterialTheme.colorScheme.error) } }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Stop tracking?") }, text = { Text("This removes the product and its local price history.") }, confirmButton = { TextButton(onClick = remove) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}
@Composable private fun PriceChart(points: List<Observation>) {
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 20.dp)) {
        val low = points.minOf { it.price }.toDouble(); val high = points.maxOf { it.price }.toDouble()
        val span = (high - low).coerceAtLeast(1.0)
        val timeSpan = (points.last().time - points.first().time).coerceAtLeast(1)
        repeat(4) { n -> val y = size.height * n / 3; drawLine(Color(0xFF374131), Offset(0f, y), Offset(size.width, y), 1f) }
        val path = Path()
        points.forEachIndexed { i, point ->
            val x = size.width * ((point.time - points.first().time).toDouble() / timeSpan).toFloat()
            val y = size.height * (1f - ((point.price - low) / span).toFloat())
            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path, Mint, style = Stroke(width = 5f))
    }
}
