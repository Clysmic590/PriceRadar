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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
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
    var refreshHours by remember { mutableIntStateOf(Alerts.interval(context)) }
    var showRefreshSchedule by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
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
    val activeCount = remember(products) { products.count { !it.paused } }
    val reachedCount = remember(products) { products.count { p -> p.price?.let { price -> p.target?.let { price <= it } } == true } }
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
        bottomBar = { if (product == null) Surface(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Watchlist", "Coupons", "Settings").forEachIndexed { i, title ->
                    Surface(onClick = { tab = i }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                        color = if (tab == i) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(when(i) { 0 -> Icons.Default.Radar; 1 -> Icons.Default.LocalOffer; else -> Icons.Default.Settings }, null,
                                tint = if (tab == i) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(title, style = MaterialTheme.typography.labelMedium,
                                color = if (tab == i) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } },
        snackbarHost = {
            notice?.let { message -> Snackbar(
                modifier = Modifier.padding(16.dp),
                action = { TextButton(onClick = { notice = null }) { Text("Dismiss") } }
            ) { Text(message) } }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 840.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            // Animate a single screen layer: avoid composing and measuring two lists at once.
            val screen = RadarScreen(selected, tab)
            val screenAlpha = remember { Animatable(1f) }
            LaunchedEffect(screen, reducedMotion) {
                if (reducedMotion) screenAlpha.snapTo(1f)
                else { screenAlpha.snapTo(0.65f); screenAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = screenAlpha.value }) {
                val displayProduct = products.find { it.id == screen.productId }
                if (displayProduct != null) {
                    ProductDetail(displayProduct,
                        save = { target, drops, targets, minimum -> scope.launch { repo.settings(displayProduct.id, target, drops, targets, minimum) } },
                        pause = { value -> scope.launch { repo.pause(displayProduct.id, value) } },
                        remove = { scope.launch { repo.remove(displayProduct.id); selected = null } })
                } else when (screen.tab) {
                0 -> PullToRefreshBox(isRefreshing = busy, onRefresh = { refresh(null) }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = watchScroll, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { PageHeading("Your watchlist", "A little patience. A better price.") }
                    item { WatchOverview(products.size, activeCount, reachedCount) }
                    item { DiscoveryTile { discoveryQuery = ""; discoveryStore = null; discovering = true } }
                    if (products.isNotEmpty()) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Saved products", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showFilters = true }) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(6.dp)); Text("Filter & sort") }
                        } }
                        item { TextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search your watchlist") }, shape = RoundedCornerShape(24.dp), singleLine = true,
                            colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                            leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
                        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (listOf("All") + Retailer.entries.map { it.label }).forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
                        } }
                    }
                    if (products.isEmpty() && !loading) item { RadarWelcome() }
                    if (products.isNotEmpty()) item { Text("${visible.size} products · ${sort.label} · ${status.label}", style = MaterialTheme.typography.labelMedium) }
                    if (products.isNotEmpty() && visible.isEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No matching products. Try another search or filter.")
                            TextButton(onClick = { query = ""; filter = "All"; status = WatchStatus.ALL }) { Text("Reset filters") }
                        }
                    }
                    items(visible, key = { it.id }, contentType = { "tracked-product" }) { p ->
                        ProductCard(p, Modifier,
                            chooseStore = { store -> discoveryQuery = p.name; discoveryStore = store; discovering = true }) { selected = p.id }
                    }
                }
                }
                1 -> LazyColumn(state = couponScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { PageHeading("Offers for you", "Coupons from the products you follow.") }
                    item { HelpDisclosure("How offers work", "Coupon text found on tracked product pages appears here. Check availability and eligibility with the retailer at checkout.") }
                    val found = couponProducts
                    if (found.isEmpty()) item { Text("No coupon text detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(found, key = { it.id }) { p -> Card(onClick = { selected = p.id }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(p.retailer.label, color = MaterialTheme.colorScheme.primary); Text(p.name, fontWeight = FontWeight.Bold); p.coupons.forEach { Text(it) }; if (p.error != null) Text("Last check failed; offers may be stale", color = MaterialTheme.colorScheme.error) } } }
                }
                else -> LazyColumn(state = settingsScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { PageHeading("Settings", "Your radar. Your rhythm.") }
                    item { SettingsGroup("Price monitoring") {
                        PreferenceRow(Icons.Default.Schedule, "Automatic refresh", "Every $refreshHours ${if (refreshHours == 1) "hour" else "hours"}", { showRefreshSchedule = true })
                        GroupDivider()
                        PreferenceRow(Icons.Default.Notifications, "Notifications", if (notificationsEnabled) "Enabled · price alerts are on" else "Disabled · tap to enable", {
                            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else openNotificationSettings()
                        })
                        GroupDivider()
                        PreferenceRow(Icons.Default.NotificationsActive, "Send test notification", "Check how an alert looks", {
                            notificationsEnabled = Alerts.enabled(context)
                            if (notificationsEnabled) { Alerts.show(context, "test", "PriceRadar", "Your price radar is ready"); notice = "Test notification sent" }
                            else { notice = "Notifications are disabled in Android settings"; openNotificationSettings() }
                        })
                    } }
                    item { Text("Android may delay background checks to save battery. Pull down on your watchlist to check now.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { SettingsGroup("Comfort & data") {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Animation)
                            Column(Modifier.weight(1f)) { Text("Reduce motion", style = MaterialTheme.typography.titleMedium); Text("Fewer effects, lighter navigation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(reducedMotion, changeMotion)
                        }
                        GroupDivider()
                        PreferenceRow(Icons.Default.Download, "Export price history", "Save observations as a CSV file", { exporter.launch("PriceRadar-history.csv") }, enabled = products.any { it.history.isNotEmpty() })
                    } }
                    item { HelpDisclosure("Stores & price accuracy", "eMAG, Vexio and Altex use public HTML extraction. Dynamic pages, anti-bot protections, sellers and coupons can prevent accurate results. Failed checks never create price points.") }
                    item { HelpDisclosure("Your privacy", "Your watchlist and history stay on this device. Uninstalling clears them. Product URLs go directly to retailers. CSV exports contain product links, UTC timestamps, observed prices and targets.") }
                    item { HelpDisclosure("Understanding discounts", "The discount check compares the advertised reference price with the lowest price observed in the previous 30 days. At least 7 observed days are needed. It is a limited-data signal, not a legal determination.") }
                    item { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Radar, null, tint = MaterialTheme.colorScheme.primary)
                        Text("PriceRadar", style = MaterialTheme.typography.titleMedium)
                        Text("UI remake · 1.4.0 preview 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }

                }
            } }
        }
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }) {
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Make it easy to find", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { Text("SORT BY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            items(WatchSort.entries) { option ->
                Surface(onClick = { sort = option }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sort == option, onClick = null); Text(option.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
            item { HorizontalDivider(); Text("SHOW", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            items(WatchStatus.entries) { option ->
                Surface(onClick = { status = option }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(status == option, onClick = null); Text(option.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
            item { Button(onClick = { showFilters = false }, modifier = Modifier.fillMaxWidth()) { Text("Show products") } }
        }
    }
    if (showRefreshSchedule) {
        var choice by rememberSaveable { mutableIntStateOf(refreshHours) }
        AlertDialog(onDismissRequest = { showRefreshSchedule = false },
            title = { Text("Refresh prices") },
            text = { Column {
                Text("Choose how often to check in the background.", modifier = Modifier.padding(bottom = 12.dp))
                Alerts.intervals.forEach { hours ->
                    Surface(onClick = { choice = hours }, shape = MaterialTheme.shapes.medium,
                        color = if (choice == hours) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == hours, onClick = null)
                            Text("Every $hours ${if (hours == 1) "hour" else "hours"}", Modifier.padding(12.dp))
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                Alerts.changeInterval(context, choice)
                refreshHours = choice
                showRefreshSchedule = false
                notice = "Refresh schedule updated"
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRefreshSchedule = false }) { Text("Cancel") } })
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
    val targetMet = p.price?.let { price -> p.target?.let { price <= it } } == true
    val previous = p.history.dropLast(1).lastOrNull()?.price
    val movement = if (previous != null && p.price != null && previous > 0) (p.price!! - previous) * 100.0 / previous else null
    val accent = when { targetMet -> androidx.compose.ui.graphics.Color(0xFFA8F56A); p.error != null -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
    Card(onClick = open, modifier = modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = .18f), RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StoreBadge(p.retailer)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(99.dp), color = accent.copy(alpha = .12f)) {
                    Text(when { p.paused -> "PAUSED"; targetMet -> "● TARGET HIT"; p.error != null -> "CHECK FAILED"; else -> "● WATCHING" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CURRENT PRICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (targetMet) accent else MaterialTheme.colorScheme.onSurface)
                Text(p.target?.let { "Your target  ${money(it)}" } ?: "Set a target to catch the right price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            movement?.let { change -> Surface(shape = RoundedCornerShape(12.dp), color = (if (change <= 0) androidx.compose.ui.graphics.Color(0xFFA8F56A) else MaterialTheme.colorScheme.error).copy(alpha=.12f)) {
                Text("${if (change <= 0) "↓" else "↑"} ${"%.1f".format(kotlin.math.abs(change))}%", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, color = if (change <= 0) androidx.compose.ui.graphics.Color(0xFFA8F56A) else MaterialTheme.colorScheme.error)
            } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text("COMPARE STORES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Retailer.entries.forEach { store -> OutlinedButton(onClick = { chooseStore(store) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text(store.label, style = MaterialTheme.typography.labelMedium) } }
            }
            Text(if (p.checked == 0L) "Waiting for the first check" else "Last checked ${stamp(p.checked)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        item { StoreBadge(p.retailer) }
        item { Text(p.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LATEST OBSERVED PRICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(p.price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(p.target?.let { "Your target: ${money(it)}" } ?: "Add a target below to get notified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        } }

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
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primary.copy(alpha=.12f), modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=.25f), RoundedCornerShape(99.dp))) {
            Icon(Icons.Default.Radar, null, Modifier.padding(22.dp).size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("Your radar is quiet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Add a product and we’ll start building its price story, spotting drops and watching your target.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

