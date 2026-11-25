package ro.priceradar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
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

data class SearchHistoryItem(val query: String, val retailer: Retailer, val time: Long)

object SearchHistory {
    private const val KEY = "recent_store_searches"
    fun load(context: Context): List<SearchHistoryItem> = runCatching {
        val raw = context.getSharedPreferences("search_history", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = org.json.JSONArray(raw)
        List(array.length()) { index -> array.getJSONObject(index).let { item ->
            SearchHistoryItem(item.getString("query"), Retailer.valueOf(item.getString("retailer")), item.optLong("time"))
        } }
    }.getOrDefault(emptyList())

    fun add(context: Context, query: String, retailer: Retailer): List<SearchHistoryItem> {
        val clean = query.trim().replace(Regex("\\s+"), " ").take(120)
        return (listOf(SearchHistoryItem(clean, retailer, System.currentTimeMillis())) + load(context)
            .filterNot { it.query.equals(clean, true) && it.retailer == retailer }).take(12).also { save(context, it) }
    }
    fun remove(context: Context, item: SearchHistoryItem): List<SearchHistoryItem> = load(context)
        .filterNot { it == item }.also { save(context, it) }
    fun clear(context: Context) = save(context, emptyList())
    private fun save(context: Context, items: List<SearchHistoryItem>) {
        val array = org.json.JSONArray()
        items.forEach { array.put(org.json.JSONObject().put("query", it.query).put("retailer", it.retailer.name).put("time", it.time)) }
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchDialog(initialQuery: String = "", initialStore: Retailer? = null, onClose: () -> Unit, onPasteLink: () -> Unit, onTrack: (String, Listing?) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery.take(120)) }
    var selectedStore by rememberSaveable { mutableStateOf(initialStore ?: Retailer.EMAG) }
    var browser by remember { mutableStateOf<Pair<Retailer, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(SearchHistory.load(context)) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun search() {
        if (query.trim().length < 2) { error = "Enter at least two characters"; return }
        keyboard?.hide()
        error = null
        recent = SearchHistory.add(context, query, selectedStore)
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
                    if (recent.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { SearchHistory.clear(context); recent = emptyList() }) { Text("Clear") }
                            }
                        }
                        items(recent, key = { "${it.retailer.name}:${it.query}:${it.time}" }) { item ->
                            Surface(onClick = {
                                query = item.query
                                selectedStore = item.retailer
                                error = null
                            }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StoreBadge(item.retailer)
                                    Text(item.query, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2)
                                    IconButton(onClick = { recent = SearchHistory.remove(context, item) }) { Icon(Icons.Default.Close, "Remove recent search") }
                                }
                            }
                        }
                    }
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
private fun StoreBrowser(store: Retailer, startUrl: String, onClose: () -> Unit, onTrack: (String, Listing?) -> Unit) {
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
                Button(onClick = {
                    val page = webView
                    if (store == Retailer.ALTEX && page != null) {
                        page.evaluateJavascript(ALTEX_LISTING_SCRIPT) { result ->
                            onTrack(currentUrl, RetailerClient().parseAltexWebResult(result))
                        }
                    } else onTrack(currentUrl, null)
                }, enabled = isProductUrl(currentUrl, store), modifier = Modifier.weight(1f)) { Text("Track product") }
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

internal val ALTEX_LISTING_SCRIPT = """
(() => {
  try {
    const products = [];
    const visit = value => {
      if (Array.isArray(value)) value.forEach(visit);
      else if (value && typeof value === 'object') {
        const type = value['@type'];
        if (type === 'Product' || (Array.isArray(type) && type.includes('Product'))) products.push(value);
        Object.values(value).forEach(visit);
      }
    };
    document.querySelectorAll('script[type="application/ld+json"]').forEach(node => {
      try { visit(JSON.parse(node.textContent)); } catch (_) {}
    });
    const product = products[0];
    if (!product) return '';
    const offers = Array.isArray(product.offers) ? product.offers : [product.offers];
    const offer = offers.find(Boolean) || {};
    const couponNodes = [...document.querySelectorAll('[class*="coupon" i],[class*="voucher" i],[class*="cupon" i],[id*="coupon" i],[id*="voucher" i]')];
    const promo = /\b(cupon|voucher|cod\s+(promo|promotional|de\s+reducere)|extra\s+reducere|reducere\s+cu\s+cod)\b/i;
    const blocked = /cod\s+(produs|fiscal|de\s+confirmare)|registrul|capital\s+social|protectia\s+consumator|termeni|cardului|cvv|parol|adresa|telefon/i;
    const coupons = [...new Set(couponNodes.map(node => (node.innerText || '').replace(/\s+/g, ' ').trim())
      .filter(text => text.length >= 4 && text.length <= 180 && !blocked.test(text) && (promo.test(text) || /^[A-Z0-9][A-Z0-9_-]{3,19}$/.test(text))))].slice(0, 8);
    return JSON.stringify({name: product.name || '', price: offer.price || offer.lowPrice || '', currency: offer.priceCurrency || 'RON', reference: '', coupons});
  } catch (_) { return ''; }
})()
""".trimIndent()


