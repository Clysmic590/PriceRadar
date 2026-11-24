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
            .userAgent("PriceRadar/1.0 (personal price tracker)")
            .timeout(20000).maxBodySize(3_000_000).followRedirects(false).execute()
        check(response.statusCode() == 200) { "Vexio returned HTTP ${response.statusCode()}" }
        return parse(response.parse(), url)
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
        val product = JSONObject(body).getJSONObject("product")
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
        return Listing(name, price, reference, emptyList())
    }

    internal fun parseAltexWebResult(encodedResult: String): Listing? = runCatching {
        val payload = org.json.JSONTokener(encodedResult).nextValue() as? String ?: return@runCatching null
        val json = JSONObject(payload)
        check(json.optString("currency").let { it.isBlank() || it == "RON" })
        val price = parseMoney(json.opt("price")?.toString().orEmpty()) ?: return@runCatching null
        val reference = parseMoney(json.opt("reference")?.toString().orEmpty())?.takeIf { it > price }
        Listing(json.optString("name").ifBlank { "Altex product" }, price, reference, emptyList())
    }.getOrNull()
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
        val price = raw?.let(::parseMoney) ?: error("No reliable product price found. Page may require JavaScript or block automated access.")
        val name = product?.optString("name")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: "Tracked product"
        // Reference prices are only hints, never interpreted as historical observations.
        val reference = doc.selectFirst(".product-old-price, .old-price, .pret-vechi, del[itemprop=price]")?.text()?.let(::parseMoney)
        val couponText = doc.select("[class*=coupon], [class*=voucher], [class*=cupon]").map { it.text() }
        val coupons = couponText.filter { it.length in 5..300 }.distinct().take(5)
        return Listing(name, price, reference, coupons)
    }

    private fun JSONObject.priceValue(): String? {
        listOf("price", "lowPrice", "highPrice").forEach { key ->
            opt(key)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return optJSONObject("priceSpecification")?.opt("price")?.toString()?.takeIf { it.isNotBlank() }
    }
}
