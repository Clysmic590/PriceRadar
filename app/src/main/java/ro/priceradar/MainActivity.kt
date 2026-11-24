package ro.priceradar

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import kotlin.math.roundToInt


private val WatchlistTaglines = listOf(
    "Every product. Every drop. One radar.",
    "Good prices rarely announce themselves.",
    "Watching the numbers so you don't have to.",
    "Your next price drop starts here.",
    "Less guessing. Better buying.",
    "Deals move fast. Your radar moves faster.",
    "Patience pays. PriceRadar proves it.",
    "Keep watch. Catch the drop."
)

class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf("")
    private var openedProduct by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = orientationForScreen(resources.configuration.smallestScreenWidthDp)
        enableEdgeToEdge()
        shared = sharedUrl(intent)
        openedProduct = intent.getStringExtra("product_id")
        Alerts.schedule(this)
        CurrencyRates.schedule(this)
        setContent {
            val preferences = remember { getSharedPreferences("performance", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var followSystemTheme by remember { mutableStateOf(preferences.getBoolean("follow_system_theme", true)) }
            val useDarkTheme = !followSystemTheme || systemDark
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
            var reducedMotion by remember { mutableStateOf(preferences.getBoolean("reduced_motion", getSystemService(ActivityManager::class.java).isLowRamDevice)) }
            var accentArgb by remember {
                val saved = preferences.getInt("one_ui_accent", OneUiAccentPalette.first().toArgb())
                mutableIntStateOf(validatedAccent(saved).toArgb())
            }
            var blackFridayMode by remember { mutableStateOf(preferences.getBoolean("black_friday_mode", false)) }
            var blackFridayCalendarWindow by remember { mutableStateOf(isBlackFridayWindow(LocalDate.now())) }
            var blackFridayOverride by remember { mutableStateOf(preferences.getBoolean("black_friday_override", false)) }
            val blackFridayAvailable = blackFridayCalendarWindow || blackFridayOverride
            var blackFridaySeconds by remember { mutableIntStateOf(preferences.getInt("black_friday_seconds", 10).takeIf { it in BLACK_FRIDAY_INTERVALS } ?: 10) }
            var keepScreenOn by remember { mutableStateOf(preferences.getBoolean("keep_screen_on", false)) }
            val shouldKeepScreenOn = keepScreenOn && blackFridayMode && blackFridayAvailable
            DisposableEffect(shouldKeepScreenOn) {
                window.decorView.keepScreenOn = shouldKeepScreenOn
                if (shouldKeepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose {
                    window.decorView.keepScreenOn = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { blackFridayCalendarWindow = isBlackFridayWindow(LocalDate.now()) }
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                RadarTheme(true, if (blackFridayMode && blackFridayAvailable) Color(0xFFE53935) else Color(accentArgb), useDarkTheme) {
                    PriceRadar(
                        shared = shared,
                        openedProduct = openedProduct,
                        opened = { openedProduct = null },
                        changeMotion = { value -> reducedMotion = value; preferences.edit().putBoolean("reduced_motion", value).apply() },
                        followSystemTheme = followSystemTheme,
                        changeFollowSystemTheme = { value -> followSystemTheme = value; preferences.edit().putBoolean("follow_system_theme", value).apply() },
                        accentColor = validatedAccent(accentArgb),
                        changeAccent = { color -> accentArgb = validatedAccent(color.toArgb()).toArgb(); preferences.edit().putInt("one_ui_accent", accentArgb).apply() },
                        blackFridayMode = blackFridayMode,
                        blackFridayAvailable = blackFridayAvailable,
                        blackFridayCalendarWindow = blackFridayCalendarWindow,
                        unlockBlackFriday = { password ->
                            blackFridayPasswordValid(password).also { valid ->
                                if (valid) {
                                    blackFridayOverride = true
                                    preferences.edit().putBoolean("black_friday_override", true).apply()
                                }
                            }
                        },
                        changeBlackFridayMode = { value -> blackFridayMode = value; preferences.edit().putBoolean("black_friday_mode", value).apply() },
                        blackFridaySeconds = blackFridaySeconds,
                        changeBlackFridaySeconds = { value -> blackFridaySeconds = value; preferences.edit().putInt("black_friday_seconds", value).apply() },
                        keepScreenOn = keepScreenOn,
                        changeKeepScreenOn = { value -> keepScreenOn = value; preferences.edit().putBoolean("keep_screen_on", value).apply() },
                        consumed = { shared = "" }
                    )
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); shared = sharedUrl(intent); openedProduct = intent.getStringExtra("product_id") }
    private fun sharedUrl(intent: Intent) = if (intent.action == Intent.ACTION_SEND) Regex("https://[^\\s]+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value.orEmpty() else ""
}

internal fun orientationForScreen(smallestWidthDp: Int): Int =
    if (smallestWidthDp >= 600) ActivityInfo.SCREEN_ORIENTATION_USER
    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceRadar(shared: String, openedProduct: String?, opened: () -> Unit, changeMotion: (Boolean) -> Unit,
               followSystemTheme: Boolean, changeFollowSystemTheme: (Boolean) -> Unit,
               accentColor: Color, changeAccent: (Color) -> Unit, blackFridayMode: Boolean,
               blackFridayAvailable: Boolean, blackFridayCalendarWindow: Boolean,
               unlockBlackFriday: (String) -> Boolean,
               changeBlackFridayMode: (Boolean) -> Unit, blackFridaySeconds: Int,
               changeBlackFridaySeconds: (Int) -> Unit, keepScreenOn: Boolean,
               changeKeepScreenOn: (Boolean) -> Unit, consumed: () -> Unit) {
    val context = LocalContext.current
    val blackFridayActive = blackFridayMode && blackFridayAvailable
    val repo = remember { Repository.get(context) }
    val products by repo.products.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val startupError by repo.startupError.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val watchlistTagline = rememberSaveable { WatchlistTaglines.random() }
    var refreshHours by remember { mutableIntStateOf(Alerts.interval(context)) }
    var showRefreshSchedule by rememberSaveable { mutableStateOf(false) }
    var showBlackFridaySchedule by rememberSaveable { mutableStateOf(false) }
    var showQuickSettings by rememberSaveable { mutableStateOf(false) }
    val quickSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showBlackFridayUnlock by rememberSaveable { mutableStateOf(false) }
    var blackFridayPassword by rememberSaveable { mutableStateOf("") }
    var blackFridayPasswordError by remember { mutableStateOf(false) }
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
    val displayPreferences = remember { context.getSharedPreferences("performance", android.content.Context.MODE_PRIVATE) }
    var compactCards by remember { mutableStateOf(displayPreferences.getBoolean("compact_watchlist", false)) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val visible by produceState<List<Product>>(emptyList(), products, query, filter, sort, status) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { watchlist(products, query, filter, sort, status) }
    }
    val couponProducts = remember(products) { products.filter { it.coupons.isNotEmpty() } }
    val activeCount = remember(products) { products.count { !it.paused } }
    val reachedCount = remember(products) { products.count { p -> p.price?.let { price -> p.target?.let { price <= it } } == true } }
    val failedCount = remember(products) { products.count { it.error != null } }
    val watchScroll = rememberLazyListState()
    val couponScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    val pagerState = rememberPagerState(initialPage = tab, pageCount = { 3 })
    LaunchedEffect(pagerState.settledPage) { tab = pagerState.settledPage }
    var notice by remember { mutableStateOf<String?>(null) }
    var removedProduct by remember { mutableStateOf<Product?>(null) }
    val accessibility = LocalAccessibilityManager.current
    val noticeTimeout = accessibility?.calculateRecommendedTimeoutMillis(6000, containsText = true, containsControls = true) ?: 6000L
    LaunchedEffect(notice, noticeTimeout) {
        if (notice != null) {
            delay(noticeTimeout)
            notice = null
            removedProduct = null
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
    var appInForeground by remember { mutableStateOf(true) }
    fun openNotificationSettings() {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = Alerts.enabled(context)
        if (!granted) openNotificationSettings()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appInForeground = true
        notificationsEnabled = Alerts.enabled(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { appInForeground = false }
    LaunchedEffect(shared) { if (shared.isNotBlank()) { url = shared; adding = true; consumed() } }
    LaunchedEffect(openedProduct, loading) { if (openedProduct != null && !loading) { selected = openedProduct.takeIf { id -> products.any { it.id == id } }; opened() } }
    fun refresh(id: String? = selected, silent: Boolean = false) {
        if (busy || repo.loading.value) return
        val currentProducts = repo.products.value
        if (currentProducts.none { !it.paused && (id == null || it.id == id) }) {
            notice = if (currentProducts.isEmpty()) "Add a product to start checking prices" else "No active products to check · resume tracking first"
            return
        }
        if (!silent) notice = null
        busy = true
        scope.launch {
            try {
                val ok = repo.refresh(context, id)
                if (!silent) notice = if (ok) "Check complete" else "Some checks failed · see product details"
            } finally { busy = false }
        }
    }
    LaunchedEffect(blackFridayActive, blackFridaySeconds, appInForeground, busy, loading, products) {
        if (shouldRunBlackFridayScan(blackFridayActive, appInForeground, busy, loading, products.any { !it.paused })) {
            delay(blackFridayRefreshMillis(blackFridaySeconds))
            if (appInForeground) refresh(null, silent = true)
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
                actions = {
                    if (product == null && blackFridayActive) IconButton(onClick = { showQuickSettings = true }) { Icon(Icons.Default.Tune, "Black Friday quick-settings") }
                    IconButton(onClick = { refresh() }, enabled = !busy && !loading) { RefreshIcon(busy) }
                })
        },
        bottomBar = { if (product == null) RadarBottomBar(pagerState) { next ->
            if (pagerState.settledPage == next) scope.launch {
                when (next) {
                    0 -> watchScroll.animateScrollToItem(0)
                    1 -> couponScroll.animateScrollToItem(0)
                    else -> settingsScroll.animateScrollToItem(0)
                }
            } else scope.launch {
                if (reducedMotion) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
            }
        } },
        snackbarHost = {
            notice?.let { message -> Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    if (removedProduct != null) TextButton(onClick = {
                        removedProduct?.let { productToRestore ->
                            removedProduct = null
                            scope.launch {
                                repo.restore(productToRestore)
                                notice = "Product restored"
                            }
                        }
                    }) { Text("Undo") }
                    else TextButton(onClick = { notice = null }) { Text("Dismiss") }
                }
            ) { Text(message) } }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 840.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AnimatedContent(
                targetState = product?.id,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    if (reducedMotion) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                    else if (targetState != null) {
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)))
                    }
                },
                label = "product detail transition"
            ) { productId ->
                val detailProduct = products.find { it.id == productId }
                if (detailProduct != null) {
                    ProductDetail(detailProduct,
                        save = { target, drops, targets, minimum -> scope.launch { repo.settings(detailProduct.id, target, drops, targets, minimum) } },
                        pause = { value -> scope.launch { repo.pause(detailProduct.id, value) } },
                        remove = {
                            removedProduct = detailProduct
                            scope.launch {
                                repo.remove(detailProduct.id)
                                selected = null
                                notice = "Stopped tracking ${detailProduct.name}"
                            }
                        })
                } else HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page -> when (page) {
                0 -> PullToRefreshBox(isRefreshing = busy, onRefresh = { refresh(null) }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = watchScroll, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { PageHeading("Your watchlist", watchlistTagline) }
                    if (blackFridayActive) item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.error)
                                Column { Text("BLACK FRIDAY LIVE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error); Text("Scanning again ${blackFridayIntervalLabel(blackFridaySeconds)} after each completed check", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    item { WatchOverview(products.size, activeCount, reachedCount, failedCount) }
                    if (products.isNotEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = { discoveryQuery = ""; discoveryStore = null; discovering = true },
                                        color = androidx.compose.ui.graphics.Color.Transparent
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(18.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            IconTile(Icons.Default.Search)
                                            Column(Modifier.weight(1f)) {
                                                Text("Find a better deal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text("Search eMAG, Vexio & Altex", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("Saved products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                Text("${visible.size} of ${products.size} shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            CompactViewToggle(compactCards) { compact ->
                                                compactCards = compact
                                                displayPreferences.edit().putBoolean("compact_watchlist", compact).apply()
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            FilledIconButton(onClick = { showFilters = true }, modifier = Modifier.size(42.dp)) {
                                                Icon(Icons.Default.Tune, "Sort and filter", Modifier.size(19.dp))
                                            }
                                        }
                                        TextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("Search your watchlist") }, shape = RoundedCornerShape(22.dp), singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                            leadingIcon = { Icon(Icons.Default.Search, null) },
                                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (listOf("All") + Retailer.entries.map { it.label }).forEach { label ->
                                                AnimatedStoreFilterChip(label, filter == label) { filter = label }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (products.isEmpty() && !loading) item { RadarWelcome() }
                    if (products.isNotEmpty() && visible.isEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No matching products. Try another search or filter.")
                            TextButton(onClick = { query = ""; filter = "All"; status = WatchStatus.ALL }) { Text("Reset filters") }
                        }
                    }
                    itemsIndexed(visible, key = { _, p -> p.id }, contentType = { _, _ -> "tracked-product" }) { index, p ->
                        var itemShown by remember(p.id) { mutableStateOf(reducedMotion) }
                        LaunchedEffect(p.id) {
                            if (!reducedMotion) delay(index.coerceAtMost(5) * 40L)
                            itemShown = true
                        }
                        val itemProgress by animateFloatAsState(
                            if (itemShown) 1f else 0f,
                            if (reducedMotion) snap() else tween(300, easing = FastOutSlowInEasing),
                            label = "watchlist item entrance"
                        )
                        ProductCard(p, Modifier.graphicsLayer { alpha = itemProgress; translationY = (1f - itemProgress) * 28f },
                            chooseStore = { store -> discoveryQuery = crossStoreSearchQuery(p.name); discoveryStore = store; discovering = true },
                            refresh = { refresh(p.id) },
                            pause = { scope.launch { repo.pause(p.id, !p.paused) } },
                            actionsEnabled = !busy && !loading,
                            compact = compactCards
                        ) { selected = p.id }
                    }
                }
                }
                1 -> LazyColumn(state = couponScroll, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val found = couponProducts
                    item { PageHeading("Offers for you", "Coupons from the products you follow.") }
                    if (found.isEmpty()) item { HelpDisclosure("How offers work", "Coupon text found on tracked product pages appears here. Check availability and eligibility with the retailer at checkout.", initiallyExpanded = true) }
                    if (found.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.LocalOffer, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("No coupons found yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Run a fresh check or add another product and detected offers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { discoveryQuery = ""; discoveryStore = null; discovering = true }) { Text("Find a product") }
                        }
                    }
                    items(found, key = { it.id }) { p -> Card(onClick = { selected = p.id }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(p.retailer.label, color = MaterialTheme.colorScheme.primary); Text(p.name, fontWeight = FontWeight.Bold); p.coupons.forEach { Text(it) }; if (p.error != null) Text("Last check failed; offers may be stale", color = MaterialTheme.colorScheme.error) } } }
                }
                else -> LazyColumn(state = settingsScroll, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { OneUiSettingsHeader() }
                        item { OneUiSettingsGroup("Quick actions") {
                            OneUiSettingsRow(Icons.Default.Refresh, "Check all prices now", if (busy) "Price check in progress" else "Refresh every active product", { refresh(null) }, enabled = !busy && !loading)
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.PlayCircle, "Resume all products", "Turn monitoring back on for every saved product", {
                                scope.launch { repo.pauseAll(false); notice = "All products resumed" }
                            }, enabled = products.any { it.paused })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.PauseCircle, "Pause all products", "Keep history but stop checks and alerts", {
                                scope.launch { repo.pauseAll(true); notice = "All products paused" }
                            }, enabled = products.any { !it.paused })
                            if (failedCount > 0) {
                                OneUiDivider()
                                OneUiSettingsRow(Icons.Default.ErrorOutline, "Retry failed checks", "$failedCount ${if (failedCount == 1) "product needs" else "products need"} attention", {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val failedIds = repo.products.value.filter { it.error != null && !it.paused }.map { it.id }
                                                val succeeded = failedIds.count { repo.refresh(context, it) }
                                                notice = if (failedIds.isEmpty()) "Resume failed products before retrying" else "Retried $succeeded of ${failedIds.size} failed checks"
                                            } finally { busy = false }
                                        }
                                    }
                                }, enabled = !busy)
                            }
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.NotificationsActive, "Test notification", "Preview a PriceRadar alert", {
                                notificationsEnabled = Alerts.enabled(context)
                                if (notificationsEnabled) { Alerts.show(context, "test", "PriceRadar", "Your price radar is ready"); notice = "Test notification sent" }
                                else { notice = "Notifications are disabled in Android settings"; openNotificationSettings() }
                            })
                        } }
                        item { OneUiSettingsGroup("Price monitoring") {
                            OneUiSettingsRow(Icons.Default.Schedule, "Automatic refresh", "Every $refreshHours ${if (refreshHours == 1) "hour" else "hours"}", { showRefreshSchedule = true })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Bolt, "Black Friday mode", when {
                                !blackFridayAvailable -> "Available November 1–15"
                                blackFridayMode -> "On · every ${blackFridayIntervalLabel(blackFridaySeconds)} · tap to change speed"
                                else -> "Off · use the switch to enable"
                            }, { if (blackFridayAvailable) showBlackFridaySchedule = true else showBlackFridayUnlock = true }, trailing = {
                                OneUiSwitch(blackFridayMode && blackFridayAvailable, changeBlackFridayMode, enabled = blackFridayAvailable)
                            })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Notifications, "Notifications", if (notificationsEnabled) "On · price alerts are allowed" else "Off · tap to enable", {
                                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else openNotificationSettings()
                            })
                        } }
                        item { OneUiSettingsGroup("Display") {
                            OneUiAccentPicker(accentColor, changeAccent)
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Contrast, "Match device theme", "Automatically switch between light and dark", { changeFollowSystemTheme(!followSystemTheme) }, trailing = { OneUiSwitch(followSystemTheme, changeFollowSystemTheme) })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Animation, "Reduce motion", "Use simpler transitions and effects", { changeMotion(!reducedMotion) }, trailing = { OneUiSwitch(reducedMotion, changeMotion) })
                            if (blackFridayActive) {
                                OneUiDivider()
                                OneUiSettingsRow(Icons.Default.ScreenLockPortrait, "Keep screen on", "Prevent the display from sleeping while PriceRadar is open", { changeKeepScreenOn(!keepScreenOn) }, trailing = { OneUiSwitch(keepScreenOn, changeKeepScreenOn) })
                            }
                        } }
                        item { OneUiSettingsGroup("Data") {
                            OneUiSettingsRow(Icons.Default.Download, "Export price history", "Save all observations as a CSV file", { exporter.launch("PriceRadar-history.csv") }, enabled = products.any { it.history.isNotEmpty() })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Security, "Privacy", "Watchlist and history stay on this device", { notice = "PriceRadar stores your watchlist locally" })
                        } }
                        item { OneUiSettingsGroup("About PriceRadar") {
                            OneUiSettingsRow(Icons.Default.Info, "PriceRadar", "Version ${BuildConfig.VERSION_NAME} · Created by Clysmic590", { showAbout = true })
                            OneUiDivider()
                            OneUiSettingsRow(Icons.Default.Storefront, "Retailer support", "eMAG, Vexio and Altex · experimental", { notice = "Retailer integrations still require live device testing" })
                        } }
                        item { Text("Background checks may be delayed by Android battery controls. Pull down on the watchlist for an immediate check.", Modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF999999)) }
                        item { Spacer(Modifier.height(18.dp)) }
                } } }
            }
        }
    }
    if (showQuickSettings) ModalBottomSheet(
        onDismissRequest = { showQuickSettings = false },
        sheetState = quickSettingsSheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Black Friday quick-settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Black Friday mode", fontWeight = FontWeight.Bold)
                    Text(when {
                        !blackFridayAvailable -> "Available only November 1–15"
                        blackFridayMode -> "Live scanning is on"
                        else -> "Live scanning is off"
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(blackFridayMode && blackFridayAvailable, enabled = blackFridayAvailable, onCheckedChange = changeBlackFridayMode)
            }
            if (!blackFridayCalendarWindow) {
                Text("Off-season access unlocked on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (blackFridayActive) {
                Text("Scan frequency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BLACK_FRIDAY_INTERVALS.forEach { seconds ->
                        FilterChip(
                            selected = blackFridaySeconds == seconds,
                            onClick = { changeBlackFridaySeconds(seconds) },
                            label = { Text(blackFridayIntervalLabel(seconds)) }
                        )
                    }
                }
                Text("Scanning pauses whenever PriceRadar is not on screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", fontWeight = FontWeight.Bold)
                    Text("Prevent the display from sleeping", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(keepScreenOn, onCheckedChange = changeKeepScreenOn)
            }
            Button(onClick = { showQuickSettings = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
    if (showBlackFridayUnlock) AlertDialog(
        onDismissRequest = { showBlackFridayUnlock = false; blackFridayPasswordError = false },
        title = { Text("Unlock Black Friday mode") },
        text = { OutlinedTextField(
            value = blackFridayPassword,
            onValueChange = { blackFridayPassword = it.take(32); blackFridayPasswordError = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Off-season password") },
            singleLine = true,
            isError = blackFridayPasswordError,
            supportingText = { if (blackFridayPasswordError) Text("Incorrect password") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        ) },
        confirmButton = { TextButton(onClick = {
            if (unlockBlackFriday(blackFridayPassword)) {
                blackFridayPassword = ""
                blackFridayPasswordError = false
                showBlackFridayUnlock = false
                notice = "Black Friday mode unlocked on this device"
            } else blackFridayPasswordError = true
        }, enabled = blackFridayPassword.isNotBlank()) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = { showBlackFridayUnlock = false; blackFridayPasswordError = false }) { Text("Cancel") } }
    )
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
            text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Choose how often to check in the background.", modifier = Modifier.padding(bottom = 10.dp))
                Alerts.intervals.forEach { hours ->
                    Surface(onClick = { choice = hours }, modifier = Modifier.padding(horizontal = 4.dp), shape = RoundedCornerShape(18.dp),
                        color = if (choice == hours) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == hours, onClick = null)
                            Text("Every $hours ${if (hours == 1) "hour" else "hours"}", Modifier.padding(start = 10.dp, end = 8.dp), fontWeight = FontWeight.SemiBold)
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
    if (showBlackFridaySchedule) {
        var choice by rememberSaveable { mutableIntStateOf(blackFridaySeconds) }
        AlertDialog(
            onDismissRequest = { showBlackFridaySchedule = false },
            title = { Text("Black Friday scan speed") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choose how soon the next scan starts after the previous check finishes.", modifier = Modifier.padding(bottom = 8.dp))
                BLACK_FRIDAY_INTERVALS.forEach { seconds ->
                    Surface(onClick = { choice = seconds }, shape = MaterialTheme.shapes.medium,
                        color = if (choice == seconds) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == seconds, onClick = null)
                            Text("Every ${blackFridayIntervalLabel(seconds)}", Modifier.padding(12.dp))
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                changeBlackFridaySeconds(choice)
                showBlackFridaySchedule = false
                notice = "Black Friday scans set to every ${blackFridayIntervalLabel(choice)}"
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showBlackFridaySchedule = false }) { Text("Cancel") } }
        )
    }
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        icon = { Icon(Icons.Default.Radar, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("PriceRadar") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Created by Clysmic590", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("A private, on-device price tracker for eMAG, Vexio and Altex.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uriHandler.openUri("https://github.com/Clysmic590/PriceRadar") }) { Icon(Icons.Default.Code, null); Spacer(Modifier.width(8.dp)); Text("View on GitHub") }
        } },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Done") } }
    )
    if (discovering) ProductSearchDialog(initialQuery = discoveryQuery, initialStore = discoveryStore,
        onClose = { discovering = false }, onPasteLink = { discovering = false; adding = true }, onTrack = { link, initial ->
            discovering = false
            scope.launch {
                try {
                    val existing = repo.products.value.find { it.url == link.substringBefore('#') }
                    val id = existing?.id ?: repo.add(link, initial)
                    if (existing != null && initial != null) repo.seed(id, initial)
                    discovering = false; selected = id
                    if (initial == null && existing == null) refresh(id)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    notice = "Could not track product: ${e.message}"
                }
            }
        })
    if (adding) AlertDialog(onDismissRequest = { adding = false; error = null }, title = { Text("Track a product") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paste an HTTPS product link from eMAG, Vexio or Altex.")
        OutlinedTextField(url, onValueChange = { url = it; error = null }, label = { Text("Product URL") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null,
            trailingIcon = { IconButton(onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                val pasted = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (pasted.isNotBlank()) { url = pasted; error = null } else error = "Clipboard is empty"
            }) { Icon(Icons.Default.ContentPaste, "Paste link") } })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { scope.launch { try { val id = repo.add(url); adding = false; url = ""; selected = id; refresh(id) } catch (e: Exception) { error = e.message } } }) { Text("Start tracking") } }, dismissButton = { TextButton(onClick = { adding = false; error = null }) { Text("Cancel") } })
}

internal val BLACK_FRIDAY_INTERVALS = listOf(10, 30)
internal fun blackFridayRefreshMillis(seconds: Int): Long =
    (seconds.takeIf { it in BLACK_FRIDAY_INTERVALS } ?: 10) * 1_000L
internal fun blackFridayIntervalLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds == 60 -> "1 minute"
    else -> "${seconds / 60} minutes"
}
internal fun shouldRunBlackFridayScan(mode: Boolean, foreground: Boolean, busy: Boolean, loading: Boolean, hasActiveProducts: Boolean): Boolean =
    mode && foreground && !busy && !loading && hasActiveProducts
internal fun isBlackFridayWindow(date: LocalDate): Boolean =
    date.monthValue == 11 && date.dayOfMonth in 1..15
internal fun blackFridayPasswordValid(password: String): Boolean = password.trim() == "BLACKFRIDAY"

@Composable
private fun RadarBottomBar(pagerState: PagerState, onSelect: (Int) -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableFloatStateOf(0f) }
    var settlingTab by remember { mutableStateOf<Int?>(null) }
    var settleAnimationFinished by remember { mutableStateOf(true) }
    val settlePosition = remember { Animatable(0f) }
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, settlingTab, settleAnimationFinished) {
        val target = settlingTab
        if (target != null && settleAnimationFinished && !pagerState.isScrollInProgress && pagerState.settledPage == target) {
            settlingTab = null
        }
    }
    Surface(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 30.dp, vertical = 6.dp),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(66.dp).padding(5.dp)
        ) {
            val segmentWidthPx = constraints.maxWidth / 3f
            val pagerPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
            val pagePosition = when {
                dragging -> draggedPosition
                settlingTab != null -> settlePosition.value
                else -> pagerPosition
            }
            val pillOffset = pagePosition * segmentWidthPx
            val highlighted = pagePosition.roundToInt().coerceIn(0, 2)
            val pillScaleX by animateFloatAsState(
                if (dragging && !reducedMotion) 1.04f else 1f,
                tween(if (dragging) 150 else 280, easing = FastOutSlowInEasing),
                label = "bottom pill width"
            )
            val pillScaleY by animateFloatAsState(
                if (dragging && !reducedMotion) .95f else 1f,
                tween(if (dragging) 150 else 280, easing = FastOutSlowInEasing),
                label = "bottom pill height"
            )
            Surface(
                modifier = Modifier
                    .offset { IntOffset(pillOffset.roundToInt(), 0) }
                    .width(maxWidth / 3)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = pillScaleX
                        scaleY = pillScaleY
                    },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            Row(
                Modifier.fillMaxSize().pointerInput(pagerState, segmentWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scope.launch { settlePosition.stop() }
                            settlingTab = null
                            settleAnimationFinished = true
                            dragging = true
                            draggedPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, 2f)
                        },
                        onDragCancel = {
                            dragging = false
                            settlingTab = null
                        },
                        onDragEnd = {
                            val destination = bottomTabForPosition(draggedPosition)
                            val releasedAt = draggedPosition
                            scope.launch {
                                settlePosition.snapTo(releasedAt)
                                settleAnimationFinished = false
                                settlingTab = destination
                                dragging = false
                                onSelect(destination)
                                if (reducedMotion) {
                                    settlePosition.snapTo(destination.toFloat())
                                } else {
                                    settlePosition.animateTo(
                                        destination.toFloat(),
                                        tween(durationMillis = 380, easing = FastOutSlowInEasing)
                                    )
                                }
                                settleAnimationFinished = true
                            }
                        }
                    ) { change, amount ->
                        change.consume()
                        draggedPosition = bottomTabDragPosition(
                            current = draggedPosition,
                            horizontalDeltaPx = amount,
                            segmentWidthPx = segmentWidthPx
                        )
                    }
                }
            ) {
                listOf("Watchlist", "Coupons", "Settings").forEachIndexed { index, title ->
                    val interaction = remember(index) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                            selected = highlighted == index
                            contentDescription = "$title tab"
                        }.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {
                                if (index == highlighted && settlingTab == null) onSelect(index)
                                else scope.launch {
                                    settlePosition.snapTo(pagePosition)
                                    settleAnimationFinished = false
                                    settlingTab = index
                                    onSelect(index)
                                    if (reducedMotion) settlePosition.snapTo(index.toFloat())
                                    else settlePosition.animateTo(index.toFloat(), tween(380, easing = FastOutSlowInEasing))
                                    settleAnimationFinished = true
                                }
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val iconSize by animateDpAsState(
                                targetValue = if (highlighted == index) 23.dp else 21.dp,
                                animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                                label = "bottom icon size"
                            )
                            Icon(
                                when(index) { 0 -> Icons.Default.Radar; 1 -> Icons.Default.LocalOffer; else -> Icons.Default.Settings },
                                null,
                                Modifier.size(iconSize),
                                tint = if (highlighted == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(title, style = MaterialTheme.typography.labelSmall, color = if (highlighted == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

internal fun bottomTabDragPosition(current: Float, horizontalDeltaPx: Float, segmentWidthPx: Float): Float {
    if (segmentWidthPx <= 0f) return current.coerceIn(0f, 2f)
    return (current + horizontalDeltaPx / segmentWidthPx).coerceIn(0f, 2f)
}

internal fun bottomTabForPosition(position: Float): Int = position.roundToInt().coerceIn(0, 2)

@Composable
private fun AnimatedStoreFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reducedMotion) .92f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "store filter press"
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction
    )
}

@Composable
private fun CompactViewToggle(compact: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.width(88.dp).height(42.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(Modifier.padding(3.dp)) {
            listOf(false to Icons.Default.ViewAgenda, true to Icons.Default.ViewList).forEach { (value, icon) ->
                Surface(
                    onClick = { onChange(value) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(15.dp),
                    color = if (compact == value) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, if (value) "Compact view" else "Normal view", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidingDisplaySelector(compact: Boolean, onChange: (Boolean) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(if (compact) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun settle(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(dragPosition).animateTo(
                target,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            ) { dragPosition = value }
        }
    }
    LaunchedEffect(compact) { if (!dragging) settle(if (compact) 1f else 0f) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)).padding(3.dp)
    ) {
        Surface(
            modifier = Modifier.offset(x = (maxWidth / 2) * dragPosition).width(maxWidth / 2).fillMaxHeight(),
            shape = RoundedCornerShape(21.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}
        val selectorWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Row(Modifier.fillMaxSize().draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                dragPosition = (dragPosition + delta * 2f / selectorWidthPx).coerceIn(0f, 1f)
            },
            onDragStarted = {
                settleJob?.cancel()
                dragging = true
            },
            onDragStopped = {
                val target = dragPosition >= .5f
                dragging = false
                onChange(target)
                settle(if (target) 1f else 0f)
            }
        )) {
            listOf(false to "Normal", true to "Compact").forEach { (value, title) ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = interaction,
                        indication = null
                    ) { onChange(value); settle(if (value) 1f else 0f) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (value) Icons.Default.ViewList else Icons.Default.ViewAgenda, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(20.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelLarge) } }
}
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
}
@Composable private fun ProductCard(
    p: Product,
    modifier: Modifier = Modifier,
    chooseStore: (Retailer) -> Unit,
    refresh: () -> Unit,
    pause: () -> Unit,
    actionsEnabled: Boolean,
    compact: Boolean,
    open: () -> Unit
) {
    val targetMet = p.price?.let { price -> p.target?.let { price <= it } } == true
    val stale = p.checked > 0L && System.currentTimeMillis() - p.checked > 24L * 60 * 60 * 1_000
    val previous = p.history.dropLast(1).lastOrNull()?.price
    val movement = if (previous != null && p.price != null && previous > 0) {
        ((p.price!! - previous) * 100.0 / previous).takeIf { kotlin.math.abs(it) >= .05 }
    } else null
    val accent = when { targetMet -> androidx.compose.ui.graphics.Color(0xFFA8F56A); p.error != null -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        if (pressed && !reducedMotion) .985f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "product card press"
    )
    val cardMotion: FiniteAnimationSpec<IntSize> = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
    Card(onClick = open, interactionSource = interaction, modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = cardScale; scaleY = cardScale }.animateContentSize(cardMotion).border(if (p.error != null) 2.dp else 1.dp, accent.copy(alpha = if (p.error != null) .65f else .18f), RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (p.error != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceContainer)) {
        if (compact) CompactProductCard(p, targetMet, movement, accent, refresh, pause, actionsEnabled)
        else Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StoreBadge(p.retailer)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(99.dp), color = accent.copy(alpha = .12f)) {
                    Text(when { p.paused -> "PAUSED"; targetMet -> "● TARGET HIT"; p.error != null -> "CHECK FAILED"; else -> "● WATCHING" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(visible = p.error != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Text("Price check failed · tap refresh to retry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CURRENT PRICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(targetState = p.price, transitionSpec = {
                    if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .96f)) togetherWith (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.02f))
                }, label = "product price") { price ->
                    Text(price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (targetMet) accent else MaterialTheme.colorScheme.onSurface)
                }
                Text(p.target?.let { "Your target  ${money(it)}" } ?: "Set a target to catch the right price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            movement?.let { change -> Surface(shape = RoundedCornerShape(12.dp), color = (if (change <= 0) androidx.compose.ui.graphics.Color(0xFFA8F56A) else MaterialTheme.colorScheme.error).copy(alpha=.12f)) {
                Text("${if (change <= 0) "↓" else "↑"} ${"%.1f".format(kotlin.math.abs(change))}%", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, color = if (change <= 0) androidx.compose.ui.graphics.Color(0xFFA8F56A) else MaterialTheme.colorScheme.error)
            } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text("COMPARE WITH ANOTHER STORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Retailer.entries.filter { it != p.retailer }.forEach { store -> OutlinedButton(onClick = { chooseStore(store) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text(store.label, style = MaterialTheme.typography.labelMedium) } }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        p.checked == 0L -> "Waiting for the first check"
                        stale -> "Price may be stale · checked ${relativeStamp(p.checked)}"
                        else -> "Checked ${relativeStamp(p.checked)}"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = refresh, enabled = actionsEnabled && !p.paused) {
                    Icon(Icons.Default.Refresh, "Check price now", tint = if (p.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = pause, enabled = actionsEnabled) {
                    Icon(if (p.paused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        if (p.paused) "Resume tracking" else "Pause tracking")
                }
            }
        }
    }
}

@Composable
private fun CompactProductCard(
    p: Product,
    targetMet: Boolean,
    movement: Double?,
    accent: Color,
    refresh: () -> Unit,
    pause: () -> Unit,
    actionsEnabled: Boolean
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StoreBadge(p.retailer)
            Spacer(Modifier.width(10.dp))
            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.price?.let(::money) ?: "No price yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (targetMet) accent else MaterialTheme.colorScheme.onSurface)
                Text(
                    when {
                        p.paused -> "Paused"
                        p.error != null -> "Price check failed · tap refresh to retry"
                        p.checked == 0L -> "Waiting for first check"
                        else -> "Checked ${relativeStamp(p.checked)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            movement?.let { change ->
                Text("${if (change <= 0) "↓" else "↑"} ${"%.1f".format(kotlin.math.abs(change))}%", color = if (change <= 0) Color(0xFFA8F56A) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CompactActionButton(onClick = refresh, enabled = actionsEnabled && !p.paused) {
                    Icon(Icons.Default.Refresh, "Check price now", Modifier.size(19.dp), tint = if (p.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                CompactActionButton(onClick = pause, enabled = actionsEnabled) {
                    Icon(if (p.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (p.paused) "Resume tracking" else "Pause tracking", Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactActionButton(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
private fun stamp(time: Long) = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
private fun relativeStamp(time: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - time).coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 45 -> "just now"
        seconds < 3_600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3_600} hr ago"
        seconds < 172_800 -> "yesterday"
        seconds < 604_800 -> "${seconds / 86_400} days ago"
        else -> stamp(time)
    }
}

@Composable private fun ProductDetail(p: Product, save: (Long?, Boolean, Boolean, Int) -> Unit, pause: (Boolean) -> Unit, remove: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var targetText by rememberSaveable(p.id, p.target) { mutableStateOf(p.target?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var drops by rememberSaveable(p.id, p.dropAlerts) { mutableStateOf(p.dropAlerts) }
    var targets by rememberSaveable(p.id, p.targetAlerts) { mutableStateOf(p.targetAlerts) }
    var minimum by rememberSaveable(p.id, p.minimumDropPercent) { mutableStateOf(p.minimumDropPercent.toFloat()) }
    var saved by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var currency by rememberSaveable(p.id) { mutableStateOf("RON") }
    var currencyExpanded by rememberSaveable(p.id) { mutableStateOf(false) }
    var trackingEnabled by rememberSaveable(p.id) { mutableStateOf(!p.paused) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val exchange by produceState<ExchangeRates?>(null, context) { value = CurrencyRates.load(context) }
    val points = remember(p.history, period) { p.history.filter { it.time >= System.currentTimeMillis() - period.duration.toMillis() } }
    val lowPrice = remember(points) { points.minOfOrNull { it.price } }
    val highPrice = remember(points) { points.maxOfOrNull { it.price } }
    val assessment = remember(p.history, p.reference) { discountAssessment(p) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    LaunchedEffect(p.paused) { trackingEnabled = !p.paused }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { StoreBadge(p.retailer) }
        item { Text(p.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Surface(onClick = { currencyExpanded = !currencyExpanded }, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LATEST OBSERVED PRICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(p.price?.let { convertedMoney(it, currency, exchange) } ?: "No price yet", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(p.target?.let { "Your target: ${convertedMoney(it, currency, exchange)}" } ?: "Add a target below to get notified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (p.checked > 0L) Text("Updated ${relativeStamp(p.checked)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyExchange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(7.dp))
                    Text("$currency · tap for price conversion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (currencyExpanded) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("RON", "EUR", "USD", "GBP").forEach { code ->
                            FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }, enabled = code == "RON" || exchange != null)
                        }
                    }
                    Text(
                        if (exchange == null) "Loading estimated exchange rates…" else "Estimated daily rate${exchange?.date?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f)
                    )
                    if (currency != "RON" && p.price != null) TextButton(onClick = {
                        uri.openUri("https://www.google.com/search?q=${p.price!! / 100.0}+RON+to+$currency")
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Check on Google")
                    }
                }
            }
        } }

        p.price?.let { price -> p.target?.let { target -> item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target progress", fontWeight = FontWeight.SemiBold)
                    Text(if (price <= target) "Your target is met · ${money(target - price)} below target" else "${money(price - target)} to go · target ${money(target)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { (target.toFloat() / price.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        } } }
        p.error?.let { item { InfoCard("Last check failed", "$it\nLast successful price is preserved.") } }
        item { Card(onClick = {
            trackingEnabled = !trackingEnabled
            pause(!trackingEnabled)
        }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (!trackingEnabled) Icons.Default.PauseCircle else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(if (!trackingEnabled) "Tracking paused" else "Tracking active", fontWeight = FontWeight.Bold)
                    Text(if (trackingEnabled) "Checks and alerts are enabled" else "History kept · checks and alerts paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OneUiSwitch(trackingEnabled, { enabled -> trackingEnabled = enabled; pause(!enabled) })
            }
        } }
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
            p.price?.let { current ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Current price" to current, "5% below" to (current * 95 / 100), "10% below" to (current * 90 / 100)).forEach { (label, value) ->
                        AssistChip(onClick = {
                            targetText = "%.2f".format(Locale.US, value / 100.0)
                            saved = false
                        }, label = { Text(label) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Price-drop notifications", Modifier.weight(1f)); OneUiSwitch(drops, { drops = it; saved = false }) }
            Text(if (minimum.toInt() == 0) "Alert for any drop" else "Minimum drop: ${minimum.toInt()}% since the previous check")
            Slider(value = minimum, onValueChange = { minimum = it; saved = false }, valueRange = 0f..50f, steps = 49, enabled = drops)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notify when target is reached", Modifier.weight(1f)); OneUiSwitch(targets, { targets = it; saved = false }) }
            Button(onClick = { save(targetText.takeIf { it.isNotBlank() }?.let(::parseMoney), drops, targets, minimum.toInt()); saved = true }, enabled = targetText.isBlank() || parseMoney(targetText) != null, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Alerts saved" else "Save alerts") }
        } }
        item { InfoCard("Coupon detection", if (p.coupons.isEmpty()) "No public coupon text found. Some offers appear only in the retailer app, account or checkout." else p.coupons.joinToString("\n\n")) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Product actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductQuickAction(Icons.Default.OpenInNew, "Open", Modifier.weight(1f)) { uri.openUri(p.url) }
                    ProductQuickAction(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, if (copied) "Copied" else "Copy", Modifier.weight(1f)) {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("${p.retailer.label} product", p.url))
                        copied = true
                    }
                    ProductQuickAction(Icons.Default.Share, "Share", Modifier.weight(1f)) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${p.name}\n${p.url}")
                        }, "Share product"))
                    }
                }
            }
        }
        item { TextButton(onClick = { deleting = true }) { Text("Stop tracking", color = MaterialTheme.colorScheme.error) } }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Stop tracking?") }, text = { Text("This removes the product and its local price history.") }, confirmButton = { TextButton(onClick = remove) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}

@Composable
private fun ProductQuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .18f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha=.12f)) {
                Icon(Icons.Default.Radar, null, Modifier.padding(14.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your radar is quiet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Add a product to start building its price history and watching for drops.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}



