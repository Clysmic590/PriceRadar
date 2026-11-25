package ro.priceradar

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject

data class Listing(val name: String, val price: Long, val reference: Long?, val coupons: List<String>)

/** Public HTML only: no login, CAPTCHA bypass or private retailer API. */
class RetailerClient {
    fun fetch(url: String): Listing {
        val retailer = Retailer.fromUrl(url)
        if (retailer == Retailer.ALTEX) return fetchAltex(url)
        if (retailer == Retailer.VEXIO) return fetchVexioLegacy(url)
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36")
            .header("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.7")
            .timeout(20000).maxBodySize(3_000_000).followRedirects(true).execute()
        check(response.statusCode() == 200) { "Retailer returned HTTP ${response.statusCode()}" }
        check(Retailer.fromUrl(response.url().toString()) == retailer) { "Retailer redirected to an unsupported site" }
        return parse(response.parse(), response.url().toString())
    }

    /** Restored from the earlier Vexio integration; eMAG and Altex do not use this path. */
    private fun fetchVexioLegacy(url: String): Listing {
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36 PriceRadar/1.8")
            .header("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.7")
            .timeout(30000).maxBodySize(6_000_000).followRedirects(true).execute()
        check(response.statusCode() == 200) { "Vexio returned HTTP ${response.statusCode()}" }
        check(Retailer.fromUrl(response.url().toString()) == Retailer.VEXIO) { "Vexio redirected outside its store" }
        return parse(response.parse(), response.url().toString())
    }

    private fun fetchAltex(url: String): Listing {
        val sku = Regex("/cpd/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(java.net.URI(url).path.orEmpty())?.groupValues?.get(1)
            ?: error("Invalid Altex product link")
        val catalogAttempt = runCatching {
            val response = Jsoup.connect("https://fenrir.altex.ro/catalog/product/$sku")
                .userAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.7")
                .header("X-Client-Type", "web")
                .referrer(url)
                .ignoreContentType(true).timeout(16000).maxBodySize(1_000_000).execute()
            check(response.statusCode() == 200) { "catalog HTTP ${response.statusCode()}" }
            parseAltexCatalog(response.body())
        }
        catalogAttempt.getOrNull()?.let { return it }

        // The catalog host occasionally rejects background clients. The public product page
        // still exposes structured product data, so use it as a safe fallback.
        return runCatching {
            val response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.7")
                .timeout(20000).maxBodySize(4_000_000).followRedirects(true).execute()
            check(response.statusCode() == 200) { "page HTTP ${response.statusCode()}" }
            check(Retailer.fromUrl(response.url().toString()) == Retailer.ALTEX) { "redirected away from Altex" }
            parse(response.parse(), response.url().toString())
        }.getOrElse { pageFailure ->
            val catalogMessage = catalogAttempt.exceptionOrNull()?.message ?: "unknown catalog error"
            error("Altex check failed ($catalogMessage; ${pageFailure.message ?: "product page unavailable"})")
        }
    }

    internal fun parseAltexCatalog(body: String): Listing {
        val root = JSONObject(body)
        val product = root.getJSONObject("product")
        val offers = product.optJSONArray("offers") ?: error("Altex did not return an offer")
        val offer = (0 until offers.length()).mapNotNull(offers::optJSONObject)
            .firstOrNull { it.optJSONObject("stock")?.optInt("status") in 1..2 }
            ?: offers.optJSONObject(0) ?: error("Altex did not return an offer")
        val priceData = offer.getJSONObject("price")
        val price = parseMoney(priceData.opt("selling_price")?.toString().orEmpty())
            ?: parseMoney(priceData.opt("base_price")?.toString().orEmpty())
            ?: error("Altex did not return a price")
        val reference = parseMoney(priceData.opt("msrp_price")?.toString().orEmpty())
            ?.takeIf { it > price }
        val name = product.optString("name").takeIf { it.isNotBlank() } ?: "Altex product"
        return Listing(name, price, reference, couponStrings(root))
    }

    internal fun parseAltexWebResult(encodedResult: String): Listing? = runCatching {
        val payload = org.json.JSONTokener(encodedResult).nextValue() as? String ?: return@runCatching null
        val json = JSONObject(payload)
        check(json.optString("currency").let { it.isBlank() || it == "RON" })
        val price = parseMoney(json.opt("price")?.toString().orEmpty()) ?: return@runCatching null
        val reference = parseMoney(json.opt("reference")?.toString().orEmpty())?.takeIf { it > price }
        val coupons = cleanCouponTexts(json.optJSONArray("coupons")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty())
        Listing(json.optString("name").ifBlank { "Altex product" }, price, reference, coupons)
    }.getOrNull()

    private fun couponStrings(root: Any?): List<String> {
        val found = linkedSetOf<String>()
        fun visit(value: Any?, key: String = "") {
            when (value) {
                is JSONObject -> value.keys().forEach { child -> visit(value.opt(child), child) }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index), key)
                is String -> if (key.contains(Regex("(?i)coupon|voucher|cupon|promo|campaign|discount")) || value.contains(Regex("(?i)cod|cupon|voucher|extra reducere"))) {
                    value.replace(Regex("\\s+"), " ").trim().takeIf { it.length in 5..300 }?.let(found::add)
                }
            }
        }
        visit(root)
        return cleanCouponTexts(found)
    }
    fun parse(doc: Document, url: String): Listing {
        Retailer.fromUrl(url)
        val products = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
                is JSONObject -> {
                    val types = value.opt("@type")?.toString().orEmpty()
                    if (types.contains("Product")) products.add(value)
                    value.keys().forEach { key -> if (key == "@graph" || key == "mainEntity") visit(value.opt(key)) }
                }
            }
        }
        doc.select("script[type=application/ld+json]").forEach { runCatching { visit(org.json.JSONTokener(it.data()).nextValue()) } }
        val product = products.firstOrNull()
        val offers = product?.opt("offers")
        val offer = when (offers) {
            is JSONObject -> offers
            is JSONArray -> offers.optJSONObject(0)
            else -> null
        }
        val currency = offer?.optString("priceCurrency")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=product:price:currency]")?.attr("content")
        check(currency == null || currency == "RON") { "Unsupported currency: $currency" }
        val raw = offer?.priceValue()
            ?: doc.selectFirst("meta[property=product:price:amount], meta[itemprop=price]")?.attr("content")
            ?: doc.selectFirst("[itemprop=price]")?.let { it.attr("content").ifBlank { it.text() } }
            ?: doc.selectFirst("[data-price], [data-product-price]")?.let { it.attr("data-price").ifBlank { it.attr("data-product-price") } }
            ?: when (Retailer.fromUrl(url)) {
                Retailer.EMAG -> doc.selectFirst(".product-new-price, [class*=product-new-price], .product-page-pricing")?.text()
                Retailer.VEXIO -> doc.selectFirst(".price, .product-price, [class*=price-new]")?.text()
                Retailer.ALTEX -> doc.selectFirst(".Price-current, .price-current, [class*=Price-current], [class*=price__current]")?.text()
            }
        val price = raw?.let(::parseMoney) ?: error("No reliable product price found. Page may require JavaScript or block automated access.")
        val name = product?.optString("name")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: "Tracked product"
        // Reference prices are only hints, never interpreted as historical observations.
        val reference = doc.selectFirst(".product-old-price, .old-price, .pret-vechi, del[itemprop=price]")?.text()?.let(::parseMoney)
        val nodes = doc.select("[class*=coupon], [class*=voucher], [class*=cupon], [id*=coupon], [id*=voucher], [data-testid*=coupon], [data-testid*=voucher]").map { it.text() }
        val pattern = Regex("(?i)(?:cupon|voucher|cod\\s+(?:promo|promotional|de\\s+reducere)|extra\\s+reducere|reducere\\s+cu\\s+cod)[^.!?\\n]{0,140}")
        val couponText = nodes + pattern.findAll(doc.body()?.text().orEmpty()).map { it.value }.toList()
        val coupons = cleanCouponTexts(couponText)
        return Listing(name, price, reference, coupons)
    }

    private fun JSONObject.priceValue(): String? {
        listOf("price", "lowPrice", "highPrice").forEach { key ->
            opt(key)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return optJSONObject("priceSpecification")?.opt("price")?.toString()?.takeIf { it.isNotBlank() }
    }
}

internal fun cleanCouponTexts(candidates: Iterable<String>): List<String> {
    val promoIntent = Regex("(?i)\\b(cupon|voucher|cod\\s+(?:promo|promotional|de\\s+reducere)|extra\\s+reducere|reducere\\s+cu\\s+cod)\\b")
    val codeOnly = Regex("^[A-Z0-9][A-Z0-9_-]{3,19}$")
    val contextualCode = Regex("(?i)\\b(use|folosește|foloseste|aplică|aplica|checkout)\\b.*\\b[A-Z0-9][A-Z0-9_-]{3,19}\\b")
    val legalOrCheckout = Regex("(?i)cod\\s+(produs|fiscal|de\\s+confirmare)|registrul|capital\\s+social|protectia\\s+consumator|termeni|cardului|cvv|parol|adresa|telefon")
    return candidates.asSequence()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length in 4..180 }
        .filterNot { legalOrCheckout.containsMatchIn(it) }
        .filter { promoIntent.containsMatchIn(it) || codeOnly.matches(it) || contextualCode.containsMatchIn(it) }
        .distinctBy { it.lowercase() }
        .take(8)
        .toList()
}

