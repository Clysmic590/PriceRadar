package ro.priceradar

import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject

// Search prices are previews, never inserted into observed history.
data class SearchProduct(val name: String, val url: String, val retailer: Retailer, val price: Long?)
data class StoreSearch(val retailer: Retailer, val products: List<SearchProduct>, val error: String? = null)

fun storeSearchUrl(retailer: Retailer, query: String): String {
    val encoded = URLEncoder.encode(query.trim().take(120), "UTF-8")
    return when (retailer) {
        Retailer.EMAG -> "https://www.emag.ro/search/${encoded.replace("+", "%20")}" 
        Retailer.VEXIO -> "https://www.vexio.ro/search/?q=$encoded"
        Retailer.ALTEX -> "https://altex.ro/cauta/?q=$encoded"
    }
}

fun isProductUrl(url: String, retailer: Retailer): Boolean = runCatching {
    if (Retailer.fromUrl(url) != retailer) return@runCatching false
    val path = URI(url).path.orEmpty()
    when (retailer) {
        Retailer.EMAG -> Regex("/pd/[^/]+", RegexOption.IGNORE_CASE).containsMatchIn(path)
        Retailer.ALTEX -> Regex("/cpd/[^/]+", RegexOption.IGNORE_CASE).containsMatchIn(path)
        Retailer.VEXIO -> Regex("^/[^/]+/\\d+-[^/]+/?$").matches(path)
    }
}.getOrDefault(false)

class ProductSearchClient {
    companion object { private val requestSlots = Semaphore(3) }
    private data class Cached(val time: Long, val products: List<SearchProduct>)
    private val cache = LinkedHashMap<Pair<Retailer, String>, Cached>()

    suspend fun search(retailer: Retailer, query: String): StoreSearch = requestSlots.withPermit { withContext(Dispatchers.IO) {
        val clean = query.trim().take(120)
        require(clean.length >= 2) { "Enter at least two characters" }
        val key = retailer to clean.lowercase(java.util.Locale.ROOT)
        val cached = synchronized(cache) { cache[key]?.takeIf { System.currentTimeMillis() - it.time < 300_000 } }
        if (cached != null) return@withContext StoreSearch(retailer, cached.products)
        try {
            ensureActive()
            val response = Jsoup.connect(storeSearchUrl(retailer, clean))
                .userAgent("PriceRadar/1.2 (personal price tracker)")
                .timeout(12_000).maxBodySize(2_000_000).followRedirects(false).ignoreHttpErrors(true).execute()
            check(response.statusCode() == 200) { "Store search unavailable (HTTP ${response.statusCode()})" }
            val products = parse(response.parse(), retailer)
            ensureActive()
            if (products.isNotEmpty()) synchronized(cache) {
                if (cache.size >= 12) cache.remove(cache.keys.first())
                cache[key] = Cached(System.currentTimeMillis(), products)
            }
            StoreSearch(retailer, products, if (products.isEmpty()) "No readable results. Try searching directly in this store." else null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            StoreSearch(retailer, emptyList(), e.message ?: "Store search unavailable")
        }
    } }

    fun parse(doc: Document, retailer: Retailer): List<SearchProduct> {
        val found = linkedMapOf<String, SearchProduct>()
        fun add(name: String, rawUrl: String, price: Long?) {
            val resolved = runCatching { URI("https://www.${retailer.host}").resolve(rawUrl).toString().substringBefore('#') }.getOrNull() ?: return
            if (name.isBlank() || !isProductUrl(resolved, retailer) || found.size >= 24) return
            found.putIfAbsent(resolved, SearchProduct(name.take(240), resolved, retailer, price))
        }
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 12 || found.size >= 24) return
            when (value) {
                is JSONArray -> for (i in 0 until minOf(value.length(), 100)) visit(value.opt(i), depth + 1)
                is JSONObject -> {
                    val type = value.opt("@type")?.toString().orEmpty()
                    if (type.contains("Product")) {
                        val offer = when (val o = value.opt("offers")) { is JSONObject -> o; is JSONArray -> o.optJSONObject(0); else -> null }
                        val currency = offer?.optString("priceCurrency").orEmpty()
                        val price = if (currency == "RON") offer?.optString("price")?.let(::parseMoney) else null
                        add(value.optString("name"), value.optString("url").ifBlank { offer?.optString("url").orEmpty() }, price)
                    }
                    listOf("@graph", "itemListElement", "item", "mainEntity").forEach { visit(value.opt(it), depth + 1) }
                }
            }
        }
        doc.select("script[type=application/ld+json]").take(20).forEach {
            runCatching { visit(org.json.JSONTokener(it.data()).nextValue()) }
        }
        val cards = doc.select(".card-item, .product-box, .product-item, .product-layout, .Products-item, [data-testid=product-card], [itemtype*=schema.org/Product]")
        cards.take(100).forEach { card ->
            val link = card.select("a[href]").firstOrNull { a ->
                val absolute = runCatching { URI("https://www.${retailer.host}").resolve(a.attr("href")).toString() }.getOrDefault("")
                isProductUrl(absolute, retailer) && (a.text().length >= 3 || a.attr("title").length >= 3)
            } ?: return@forEach
            val name = link.attr("title").ifBlank { link.text() }
            val priceNode = card.selectFirst("meta[itemprop=price], [itemprop=price], .product-new-price, .product-price, .price-current, [data-testid=product-price]")
            val currency = card.selectFirst("[itemprop=priceCurrency]")?.let { it.attr("content").ifBlank { it.text() } }
            val rawPrice = priceNode?.let { it.attr("content").ifBlank { it.text() } }
            // Unmarked currencies and combined promotional text are not reliable previews.
            val price = if (currency == "RON" || rawPrice?.contains(Regex("(?i)\\b(lei|RON)\\b")) == true) rawPrice?.let(::parseMoney) else null
            add(name, link.attr("href"), price)
        }
        return found.values.toList()
    }
}
