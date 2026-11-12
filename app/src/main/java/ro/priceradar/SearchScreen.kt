package ro.priceradar

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchDialog(initialQuery: String = "", initialStore: Retailer? = null, onClose: () -> Unit, onPasteLink: () -> Unit, onTrack: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf(initialQuery.take(120)) }
    var selectedStore by rememberSaveable { mutableStateOf(initialStore ?: Retailer.EMAG) }
    var browser by remember { mutableStateOf<Pair<Retailer, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun search() {
        if (query.trim().length < 2) { error = "Enter at least two characters"; return }
        keyboard?.hide()
        error = null
        browser = selectedStore to storeSearchUrl(selectedStore, query)
    }
    Dialog(onDismissRequest = { if (browser != null) browser = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val browsing = browser
            if (browsing != null) {
                StoreBrowser(browsing.first, browsing.second, onClose = { browser = null }, onTrack = onTrack)
            } else Scaffold(topBar = {
                TopAppBar(title = { Text("Search store websites") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close search") } },
                    actions = { TextButton(onClick = onPasteLink) { Text("Paste link") } })
            }) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { PageHeading("Find your next favourite", "Search directly on the store’s website.") }
                    item { Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(query, { query = it.take(120); error = null }, Modifier.fillMaxWidth(), label = { Text("Product name or model") }, singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                            Text("Choose a store", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                Retailer.entries.forEachIndexed { index, store -> SegmentedButton(selected = selectedStore == store, onClick = { selectedStore = store }, shape = SegmentedButtonDefaults.itemShape(index, Retailer.entries.size), label = { Text(store.label) }) }
                            }
                            Button(onClick = { search() }, enabled = query.trim().length >= 2, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search ${selectedStore.label}")
                            }
                        }
                    } }
                    error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    item { Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Open a result, then tap Track product. Results remain the retailer’s own website.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
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
    Scaffold(topBar = { TopAppBar(title = { Text(store.label) }, navigationIcon = { IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { TextButton(onClick = onClose) { Text("Search") } }) },
        bottomBar = { Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { StoreBadge(store); Text(if (isProductUrl(currentUrl, store)) "Product ready to track" else "Open a product result", fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTrack(currentUrl) }, enabled = isProductUrl(currentUrl, store), modifier = Modifier.weight(1f)) { Text("Track product") }
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                        .onFailure { error = "No browser is available" }
                }, modifier = Modifier.weight(1f)) { Text("Open in browser") }
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
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = false
                    isHorizontalScrollBarEnabled = false
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

