package ro.priceradar

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private data class RadarScreen(val productId: String?, val tab: Int)

class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf("")
    private var openedProduct by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shared = sharedUrl(intent)
        openedProduct = intent.getStringExtra("product_id")
        Alerts.schedule(this)
        setContent {
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            val preferences = remember { getSharedPreferences("performance", MODE_PRIVATE) }
            var reducedMotion by remember { mutableStateOf(preferences.getBoolean("reduced_motion", getSystemService(ActivityManager::class.java).isLowRamDevice)) }
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                RadarTheme {
                    PriceRadar(shared, openedProduct, { openedProduct = null }, { value ->
                        reducedMotion = value
                        preferences.edit().putBoolean("reduced_motion", value).apply()
                    }) { shared = "" }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); shared = sharedUrl(intent); openedProduct = intent.getStringExtra("product_id") }
    private fun sharedUrl(intent: Intent) = if (intent.action == Intent.ACTION_SEND) Regex("https://[^\\s]+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value.orEmpty() else ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceRadar(shared: String, openedProduct: String?, opened: () -> Unit, changeMotion: (Boolean) -> Unit, consumed: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val products by repo.products.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val startupError by repo.startupError.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var discovering by rememberSaveable { mutableStateOf(false) }
    var discoveryQuery by rememberSaveable { mutableStateOf("") }
    var discoveryStore by rememberSaveable { mutableStateOf<Retailer?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(WatchSort.RECENT) }
    var status by rememberSaveable { mutableStateOf(WatchStatus.ALL) }
    val visible by produceState<List<Product>>(emptyList(), products, query, filter, sort, status) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { watchlist(products, query, filter, sort, status) }
    }
    val couponProducts = remember(products) { products.filter { it.coupons.isNotEmpty() } }
    val targetCount = remember(products) { products.count { it.target != null } }
    val watchScroll = rememberLazyListState()
    val couponScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    var notice by remember { mutableStateOf<String?>(null) }
    val accessibility = LocalAccessibilityManager.current
    val noticeTimeout = accessibility?.calculateRecommendedTimeoutMillis(6000, containsText = true, containsControls = true) ?: 6000L
    LaunchedEffect(notice, noticeTimeout) {
        if (notice != null) {
            delay(noticeTimeout)
            notice = null
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { destination ->
        if (destination != null) scope.launch {
            notice = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(historyCsv(repo.products.value)) }
                        ?: error("Could not open destination")
                }
                "History exported"
            } catch (e: Exception) { "Export failed: ${e.message}" }
        }
    }
    var notificationsEnabled by remember { mutableStateOf(Alerts.enabled(context)) }
    fun openNotificationSettings() {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = Alerts.enabled(context)
        if (!granted) openNotificationSettings()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = Alerts.enabled(context)
    }
    LaunchedEffect(shared) { if (shared.isNotBlank()) { url = shared; adding = true; consumed() } }
    LaunchedEffect(openedProduct, loading) { if (openedProduct != null && !loading) { selected = openedProduct.takeIf { id -> products.any { it.id == id } }; opened() } }
    fun refresh(id: String? = selected) {
        if (busy || repo.loading.value) return
        val currentProducts = repo.products.value
        if (currentProducts.none { !it.paused && (id == null || it.id == id) }) {
            notice = if (currentProducts.isEmpty()) "Add a product to start checking prices" else "No active products to check · resume tracking first"
            return
        }
        notice = null
        busy = true
        scope.launch {
            try {
                val ok = repo.refresh(context, id)
                notice = if (ok) "Check complete" else "Some checks failed · see product details"
            } finally { busy = false }
        }
    }
    val product = products.find { it.id == selected }
    BackHandler(enabled = selected != null) { selected = null }
    if (startupError != null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(startupError!!) }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (product == null) "PriceRadar" else "Product radar", fontWeight = FontWeight.Bold) },
                navigationIcon = { if (product != null) IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { refresh() }, enabled = !busy && !loading) { RefreshIcon(busy) } })
        },
        bottomBar = { if (product == null) NavigationBar {
            listOf("Watchlist", "Coupons", "Settings").forEachIndexed { i, title ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(title) }, icon = { val pop = animateFloatAsState(if (tab == i && !reducedMotion) 1.10f else 1f, if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy), label = "tab focus"); Icon(when(i) { 0 -> Icons.Default.Radar; 1 -> Icons.Default.LocalOffer; else -> Icons.Default.Settings }, title, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) })
            }
        } },
        floatingActionButton = { if (product == null && tab == 0) ExtendedFloatingActionButton(onClick = { discoveryQuery = ""; discoveryStore = null; discovering = true }, icon = { Icon(Icons.Default.Search, null) }, text = { Text("Find product") }, containerColor = MaterialTheme.colorScheme.primary) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimatedVisibility(notice != null, enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(160)), exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(120))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(notice.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { notice = null }) { Text("Dismiss") } }
            }
            AnimatedContent(targetState = RadarScreen(selected, tab), modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val direction = when {
                            targetState.productId != null -> 1
                            initialState.productId != null -> -1
                            targetState.tab > initialState.tab -> 1
                            else -> -1
                        }
                        ((fadeIn(tween(180)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 12 * direction }) togetherWith
                            fadeOut(tween(100))).using(null)
                    }
                }, label = "screen transition") { screen ->
                val displayProduct = products.find { it.id == screen.productId }
                if (displayProduct != null) {
                    ProductDetail(displayProduct,
                        save = { target, drops, targets, minimum -> scope.launch { repo.settings(displayProduct.id, target, drops, targets, minimum) } },
                        pause = { value -> scope.launch { repo.pause(displayProduct.id, value) } },
                        remove = { scope.launch { repo.remove(displayProduct.id); selected = null } })
                } else when (screen.tab) {
                0 -> PullToRefreshBox(isRefreshing = busy, onRefresh = { refresh(null) }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = watchScroll, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Column(Modifier.padding(vertical = 12.dp)) {
                        Text("BUY AT THE RIGHT MOMENT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text("Good prices.\nNo guesswork.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Your personal radar for Romanian retailers", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("${products.size}", "Tracking", Modifier.weight(1f))
                        Stat("${targetCount}", "Price targets", Modifier.weight(1f))
                    } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("All") + Retailer.entries.map { it.label }).forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
                    } }
                    item { Text("Your watchlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search products") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { WatchSort.entries.forEach { option -> FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) }) } } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { WatchStatus.entries.forEach { option -> FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option.label) }) } } }
                    if (products.isEmpty() && !loading) item { RadarWelcome() }
                    if (products.isNotEmpty()) item { Text("${visible.size} of ${products.size} products · pull down to refresh", style = MaterialTheme.typography.labelMedium) }
                    if (products.isNotEmpty() && visible.isEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No matching products. Try another search or filter.")
                            TextButton(onClick = { query = ""; filter = "All"; status = WatchStatus.ALL }) { Text("Reset filters") }
                        }
                    }
                    items(visible, key = { it.id }, contentType = { "tracked-product" }) { p ->
                        ProductCard(p, if (reducedMotion) Modifier else Modifier.animateItem(),
                            chooseStore = { store -> discoveryQuery = p.name; discoveryStore = store; discovering = true }) { selected = p.id }
                    }
                }
                }
                1 -> LazyColumn(state = couponScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Coupon radar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                    item { InfoCard("Public offers, in one place", "Coupon text found on tracked product pages appears here. Availability and checkout eligibility must be verified with the retailer.") }
                    val found = couponProducts
                    if (found.isEmpty()) item { Text("No coupon text detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(found, key = { it.id }) { p -> Card(onClick = { selected = p.id }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(p.retailer.label, color = MaterialTheme.colorScheme.primary); Text(p.name, fontWeight = FontWeight.Bold); p.coupons.forEach { Text(it) }; if (p.error != null) Text("Last check failed; offers may be stale", color = MaterialTheme.colorScheme.error) } } }
                }
                else -> LazyColumn(state = settingsScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Your radar, your rules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item { Card { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Reduce motion", fontWeight = FontWeight.Bold); Text("Lighter transitions for slower phones. Enabled automatically on low-memory devices.", style = MaterialTheme.typography.bodySmall) }
                        Switch(reducedMotion, changeMotion)
                    } } }
                    item { OutlinedButton(onClick = { exporter.launch("PriceRadar-history.csv") }, enabled = products.any { it.history.isNotEmpty() }) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Export price history (CSV)") } }
                    item { Text("Export includes product links, UTC timestamps, observed prices and targets. Choose where to save it.", style = MaterialTheme.typography.bodySmall) }
                    item { InfoCard("Background checks", "Prices are checked approximately every 6 hours with an internet connection. Android battery management can delay checks. Open the app and tap refresh for an immediate check.") }
                    item { Text(if (notificationsEnabled) "Notifications enabled" else "Notifications disabled · allow notifications and the Price alerts category in Android settings") }
                    item { Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openNotificationSettings()
                    }) { Text(if (notificationsEnabled) "Notification settings" else "Enable notifications") } }
                    item { OutlinedButton(onClick = {
                        notificationsEnabled = Alerts.enabled(context)
                        if (notificationsEnabled) {
                            Alerts.show(context, "test", "PriceRadar", "Your price radar is ready")
                            notice = "Test notification sent"
                        } else {
                            notice = "Notifications are disabled in Android settings"
                            openNotificationSettings()
                        }
                    }) { Text("Send test notification") } }
                    item { InfoCard("Integration status · needs live testing", "eMAG, Vexio and Altex use public HTML extraction. Dynamic pages, anti-bot protections, marketplace sellers and coupon conditions may prevent accurate results. Failed checks never create price points.") }
                    item { InfoCard("Private by design", "Your watchlist and observations stay on this device. Uninstalling clears them. Product URLs are sent directly to the selected retailer; no account or tracking backend is used.") }
                    item { InfoCard("Fake-discount detector", "Compares the advertised reference price with the lowest price observed in the previous 30 days. At least 7 distinct observed days are required. This is a limited-data signal, not a legal determination.") }
                }
            } }
        }
    }
    if (discovering) ProductSearchDialog(initialQuery = discoveryQuery, initialStore = discoveryStore,
        onClose = { discovering = false }, onPasteLink = { discovering = false; adding = true }, onTrack = { link ->
            discovering = false
            scope.launch {
                try {
                    val existing = repo.products.value.find { it.url == link.substringBefore('#') }
                    val id = existing?.id ?: repo.add(link)
                    discovering = false; selected = id
                    if (existing == null) refresh(id)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    notice = "Could not track product: ${e.message}"
                }
            }
        })
    if (adding) AlertDialog(onDismissRequest = { adding = false; error = null }, title = { Text("Track a product") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paste an HTTPS product link from eMAG, Vexio or Altex.")
        OutlinedTextField(url, onValueChange = { url = it; error = null }, label = { Text("Product URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { scope.launch { try { val id = repo.add(url); adding = false; url = ""; selected = id; refresh(id) } catch (e: Exception) { error = e.message } } }) { Text("Start tracking") } }, dismissButton = { TextButton(onClick = { adding = false; error = null }) { Text("Cancel") } })
}

@Composable private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(20.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelLarge) } }
}
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
}
@Composable private fun ProductCard(p: Product, modifier: Modifier = Modifier, chooseStore: (Retailer) -> Unit, open: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val pressScale = animateFloatAsState(if (pressed && !reducedMotion) 0.985f else 1f,
        if (reducedMotion) snap() else tween(130, easing = FastOutSlowInEasing), label = "product press")
    Card(onClick = open, interactionSource = interaction, modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.retailer.label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); if (p.coupons.isNotEmpty()) Text("Coupon detected", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium) }
        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        if (p.paused) Text("Tracking paused", color = MaterialTheme.colorScheme.secondary)
        Text(p.price?.let(::money) ?: "Waiting for first check", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        p.target?.let { Text("Target ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("Find this product name at", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Retailer.entries.forEach { store -> AssistChip(onClick = { chooseStore(store) }, label = { Text(store.label) }) }
        }
        if (p.error != null) Text("Check failed · tap for details", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        else Text(if (p.checked == 0L) "Not checked yet" else "Checked ${stamp(p.checked)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
private fun stamp(time: Long) = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))

@Composable private fun ProductDetail(p: Product, save: (Long?, Boolean, Boolean, Int) -> Unit, pause: (Boolean) -> Unit, remove: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var targetText by rememberSaveable(p.id, p.target) { mutableStateOf(p.target?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var drops by rememberSaveable(p.id, p.dropAlerts) { mutableStateOf(p.dropAlerts) }
    var targets by rememberSaveable(p.id, p.targetAlerts) { mutableStateOf(p.targetAlerts) }
    var minimum by rememberSaveable(p.id, p.minimumDropPercent) { mutableStateOf(p.minimumDropPercent.toFloat()) }
    var saved by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val points = remember(p.history, period) { p.history.filter { it.time >= System.currentTimeMillis() - period.duration.toMillis() } }
    val lowPrice = remember(points) { points.minOfOrNull { it.price } }
    val highPrice = remember(points) { points.maxOfOrNull { it.price } }
    val assessment = remember(p.history, p.reference) { discountAssessment(p) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(p.retailer.label, color = MaterialTheme.colorScheme.primary); Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp)) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (p.paused) "Tracking paused" else "Tracking active", fontWeight = FontWeight.Bold); Text("Pause keeps your history and skips checks and alerts", style = MaterialTheme.typography.bodySmall) }; Switch(!p.paused, { pause(!it) }) } }
        p.price?.let { price -> p.target?.let { target -> item { InfoCard("Target progress", if (price <= target) "Your target is met · ${money(target - price)} below target" else "${money(price - target)} to go · target ${money(target)}") } } }
        p.error?.let { item { InfoCard("Last check failed", "$it\nLast successful price is preserved.") } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Period.entries.forEach { option -> FilterChip(selected = period == option, onClick = { period = option }, label = { Text(option.label) }) } } }
        item { Card { Column(Modifier.padding(20.dp)) {
            Text("Price history", style = MaterialTheme.typography.titleMedium)
            if (points.size < 2) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Text(if (points.isEmpty()) "No observations in this period" else "First observation collected · check again later", style = MaterialTheme.typography.bodyMedium) }
            else PriceChart(points)
            if (points.isNotEmpty()) { Text("Low ${money(lowPrice!!)}  ·  High ${money(highPrice!!)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Text("${stamp(points.first().time)} — ${stamp(points.last().time)}", style = MaterialTheme.typography.labelSmall) }
            Text("Observed prices only; gaps are not reconstructed.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        } } }
        item { InfoCard("Discount check", assessment) }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(targetText, onValueChange = { targetText = it; saved = false }, label = { Text("Target price in RON (optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = targetText.isNotBlank() && parseMoney(targetText) == null)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Price-drop notifications", Modifier.weight(1f)); Switch(drops, { drops = it; saved = false }) }
            Text(if (minimum.toInt() == 0) "Alert for any drop" else "Minimum drop: ${minimum.toInt()}% since the previous check")
            Slider(value = minimum, onValueChange = { minimum = it; saved = false }, valueRange = 0f..50f, steps = 49, enabled = drops)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notify when target is reached", Modifier.weight(1f)); Switch(targets, { targets = it; saved = false }) }
            Button(onClick = { save(targetText.takeIf { it.isNotBlank() }?.let(::parseMoney), drops, targets, minimum.toInt()); saved = true }, enabled = targetText.isBlank() || parseMoney(targetText) != null, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Alerts saved" else "Save alerts") }
        } }
        item { InfoCard("Coupon detection", if (p.coupons.isEmpty()) "No public coupon text found. Some offers appear only in the retailer app, account or checkout." else p.coupons.joinToString("\n\n")) }
        item { OutlinedButton(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${p.name}\n${p.url}")
            }, "Share product"))
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share product") } }
        item { OutlinedButton(onClick = { uri.openUri(p.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open at ${p.retailer.label}") } }
        item { TextButton(onClick = { deleting = true }) { Text("Stop tracking", color = MaterialTheme.colorScheme.error) } }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Stop tracking?") }, text = { Text("This removes the product and its local price history.") }, confirmButton = { TextButton(onClick = remove) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}
@Composable private fun PriceChart(points: List<Observation>) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val reducedMotion = LocalReducedMotion.current
    val sampled = remember(points) { chartSamples(points) }
    val reveal = remember(points) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(points, reducedMotion) {
        if (reducedMotion) reveal.snapTo(1f) else reveal.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 20.dp).drawWithCache {
        val low = sampled.minOf { it.price }.toDouble()
        val span = (sampled.maxOf { it.price }.toDouble() - low).coerceAtLeast(1.0)
        val timeSpan = (sampled.last().time - sampled.first().time).coerceAtLeast(1)
        val path = Path()
        sampled.forEachIndexed { i, point ->
            val x = size.width * ((point.time - sampled.first().time).toDouble() / timeSpan).toFloat()
            val y = size.height * (1f - ((point.price - low) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        onDrawBehind {
            repeat(4) { n -> val y = size.height * n / 3; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
            clipRect(right = size.width * reveal.value) { drawPath(path, accent, style = Stroke(width = 5f)) }
        }
    })
}

@Composable private fun RefreshIcon(busy: Boolean) {
    if (busy && !LocalReducedMotion.current) {
        val motion = rememberInfiniteTransition(label = "radar sweep")
        val angle = motion.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "refresh spin")
        Icon(Icons.Default.Refresh, "Checking prices", Modifier.graphicsLayer { rotationZ = angle.value })
    } else Icon(Icons.Default.Refresh, "Refresh prices")
}

@Composable private fun RadarWelcome() {
    val reducedMotion = LocalReducedMotion.current
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val pop = animateFloatAsState(if (arrived || reducedMotion) 1f else 0.8f,
        if (reducedMotion) snap() else tween(220, easing = FastOutSlowInEasing), label = "radar hello")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                Icon(Icons.Default.Radar, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Text("Your next great deal starts here", fontWeight = FontWeight.SemiBold)
            Text("Tap Find product to search eMAG, Vexio or Altex by name. Price history starts with the first successful check.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
