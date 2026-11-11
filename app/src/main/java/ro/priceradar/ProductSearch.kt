package ro.priceradar

import java.net.URI
import java.net.URLEncoder

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
        Retailer.VEXIO -> Regex("^/[^/]+/(?:[^/]+/)?\\d+-[^/]+/?$").matches(path)
    }
}.getOrDefault(false)

