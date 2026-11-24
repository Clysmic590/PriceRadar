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

/** Removes retailer-specific specs and bundles so another store can match the brand/model. */
fun crossStoreSearchQuery(productName: String): String {
    val normalized = productName.replace(Regex("\\s+"), " ").trim()
    var core = normalized
        .substringBefore(" + ")
        .substringBefore(" | ")
        .substringBefore(',')
        .substringBefore(';')
        .replace(Regex("(?i)^(telefon(?:\\s+mobil)?|smartphone|telefon|laptop|televizor|monitor|tableta|tablet|casti|headphones)\\s+"), "")
        .trim()
    Regex("(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:GB|TB|MB)\\b").find(core)?.let { core = core.take(it.range.first).trim() }
    val colours = "titanium grey|titanium gray|space grey|space gray|midnight black|phantom black|negru|alb|albastru|gri|verde|rosu|auriu|argintiu|black|white|blue|grey|gray|green|red|gold|silver"
    core = core.replace(Regex("(?i)(?:\\s+(?:$colours))+$"), "").trim()
    return core.take(80).trim().ifBlank { normalized.take(80) }
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

