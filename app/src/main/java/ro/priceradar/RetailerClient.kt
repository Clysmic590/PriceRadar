package ro.priceradar

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject

data class Listing(val name: String, val price: Long, val reference: Long?, val coupons: List<String>)

/** Public HTML only: no login, CAPTCHA bypass or private retailer API. */
class RetailerClient {
    fun fetch(url: String): Listing {
        Retailer.fromUrl(url)
        val response = Jsoup.connect(url).userAgent("PriceRadar/1.0 (personal price tracker)")
            .timeout(20000).maxBodySize(3_000_000).followRedirects(false).execute()
        check(response.statusCode() == 200) { "Retailer returned HTTP ${response.statusCode()}" }
        return parse(response.parse(), url)
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
        val raw = offer?.optString("price")?.takeIf { it.isNotBlank() }
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
}
