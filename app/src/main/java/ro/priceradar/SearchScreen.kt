package ro.priceradar

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchDialog(initialQuery: String = "", initialStore: Retailer? = null, onClose: () -> Unit, onPasteLink: () -> Unit, onTrack: (String) -> Unit) {
    val client = remember { ProductSearchClient() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery.take(120)) }
    var submitted by rememberSaveable { mutableStateOf("") }
    var selectedStore by rememberSaveable { mutableStateOf(initialStore) }
    var storeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<StoreSearch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<Pair<Retailer, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun search() {
        if (searching) return
        if (query.trim().length < 2) { error = "Enter at least two characters"; return }
        keyboard?.hide()
        error = null
        results = emptyList()
        if (selectedStore == Retailer.VEXIO) {
            submitted = query.trim().take(120)
            browser = Retailer.VEXIO to storeSearchUrl(Retailer.VEXIO, submitted)
            return
        }
        submitted = query.trim().take(120)
        results = emptyList(); error = null; searching = true
        val term = submitted
        scope.launch {
            try {
                kotlinx.coroutines.coroutineScope {
                    Retailer.entries.forEach { store -> launch {
                        val result = client.search(store, term)
                        results = results + result
                    } }
                }
            } finally { searching = false }
        }
    }
    LaunchedEffect(Unit) { if (initialQuery.isNotBlank() && submitted.isBlank()) search() }
    Dialog(onDismissRequest = { if (browser != null) browser = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val browsing = browser
            if (browsing != null) {
                StoreBrowser(browsing.first, browsing.second, onClose = { browser = null }, onTrack = onTrack)
            } else Scaffold(topBar = { TopAppBar(title = { Text("Explore stores") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close search") } }, actions = { TextButton(onClick = onPasteLink) { Text("Paste link") } }) }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 20.dp)) { PageHeading("Find your next favourite", "One search. Three stores to explore.") }
                    OutlinedTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Product name or model") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        TextButton(onClick = { storeMenuOpen = true }) { Text("Search in: ${selectedStore?.label ?: "All stores"} ▾") }
                        DropdownMenu(expanded = storeMenuOpen, onDismissRequest = { storeMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("All stores") }, onClick = { selectedStore = null; storeMenuOpen = false })
                            Retailer.entries.forEach { store ->
                                DropdownMenuItem(text = { Text(store.label) }, onClick = { selectedStore = store; storeMenuOpen = false })
                            }
                        }
                    }
                    Button(onClick = { search() }, enabled = !searching && query.trim().length >= 2, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 52.dp)) { Text(if (searching) "Searching stores…" else "Search products") }
                    error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
                    if (submitted.isNotBlank()) Text("Results for $submitted", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium)
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val stores = Retailer.entries.filter { selectedStore == null || it == selectedStore }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (submitted.isNotBlank()) stores.forEach { store ->
                            item(key = "store-${store.name}") {
                                OutlinedButton(onClick = {
                                    val term = submitted.ifBlank { query.trim() }
                                    if (term.length >= 2) browser = store to storeSearchUrl(store, term) else error = "Enter a product name first"
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (store == Retailer.VEXIO) "Open Vexio results" else "Browse ${store.label} results") }
                            }
                            val result = results.find { it.retailer == store }
                            if (result?.error != null) item(key = "error-${store.name}") { Text("${store.label}: ${result.error}", style = MaterialTheme.typography.bodySmall) }
                            items(result?.products.orEmpty(), key = { it.url }, contentType = { "search-product" }) { product ->
                                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StoreBadge(product.retailer)
                                    Text(product.name, maxLines = 3, style = MaterialTheme.typography.titleMedium)
                                    Text(product.price?.let { money(it) } ?: "See current price at store", style = MaterialTheme.typography.headlineSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onTrack(product.url) }) { Text("Track") }
                                        OutlinedButton(onClick = { browser = store to product.url }) { Text("View / buy") }
                                    }
                                } }
                            }
                        }
                        if (submitted.isBlank()) item { Text("Search by name or model. No link needed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (results.isNotEmpty()) item { Text("Check the model and seller before buying. Search previews aren’t saved as price history.", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreBrowser(store: Retailer, startUrl: String, onClose: () -> Unit, onTrack: (String) -> Unit) {
    var currentUrl by remember(startUrl) { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView?.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView?.onResume() }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    Scaffold(topBar = { TopAppBar(title = { Text(store.label) }, navigationIcon = { IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { TextButton(onClick = onClose) { Text("Results") } }) },
        bottomBar = { Surface { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isProductUrl(currentUrl, store)) "Product selected at ${store.label}" else "Open a product in ${store.label} to track it", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTrack(currentUrl) }, enabled = isProductUrl(currentUrl, store)) { Text("Track this product") }
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                        .onFailure { error = "No browser is available" }
                }) { Text("Open store / buy") }
            }
        } } }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && runCatching { Retailer.fromUrl(request.url.toString()) != store }.getOrDefault(true)
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            loading = true; error = null
                            if (runCatching { Retailer.fromUrl(url) == store }.getOrDefault(false)) currentUrl = url
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            val finishedUrl = view.url ?: url
                            if (runCatching { Retailer.fromUrl(finishedUrl) == store }.getOrDefault(false)) currentUrl = finishedUrl
                        }
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            if (runCatching { Retailer.fromUrl(url) == store }.getOrDefault(false)) currentUrl = url
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, problem: android.net.http.SslError) {
                            handler.cancel(); loading = false; error = "Store certificate could not be verified"
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
                            if (request.isForMainFrame) { loading = false; error = "Store page could not load. Try Open store / buy." }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) { loading = false; error = "Store returned HTTP ${response.statusCode}. Try Open store / buy." }
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            view.destroy(); webView = null; loading = false; error = "Store browser closed to free memory. Return to results and try again."; return true
                        }
                    }
                    loadUrl(startUrl)
                }
            }, onRelease = { it.stopLoading(); it.destroy(); webView = null })
        }
    }
}
