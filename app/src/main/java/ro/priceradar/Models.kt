package ro.priceradar

import java.net.URI
import java.time.Duration

enum class Retailer(val host: String, val label: String) {
    EMAG("emag.ro", "eMAG"), VEXIO("vexio.ro", "Vexio"), ALTEX("altex.ro", "Altex");
    companion object {
        fun fromUrl(url: String): Retailer {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) { "Use an HTTPS product link" }
            return entries.firstOrNull { uri.host == it.host || uri.host == "www.${it.host}" }
                ?: throw IllegalArgumentException("Choose an eMAG, Vexio or Altex product link")
        }
    }
}
data class Observation(val time: Long, val price: Long)
data class Product(
    val id: String, val url: String, val retailer: Retailer, val name: String,
    val history: List<Observation> = emptyList(), val target: Long? = null,
    val reference: Long? = null, val coupons: List<String> = emptyList(),
    val error: String? = null, val checked: Long = 0,
    val dropAlerts: Boolean = true, val targetAlerts: Boolean = true,
    val paused: Boolean = false, val minimumDropPercent: Int = 0
) { val price: Long? get() = history.lastOrNull()?.price }
enum class WatchSort(val label: String) { RECENT("Recently checked"), PRICE("Lowest price"), NAME("Name"), TARGET("Near target") }
enum class WatchStatus(val label: String) { ALL("All states"), ACTIVE("Active"), PAUSED("Paused"), TARGET_MET("Target reached"), FAILED("Check failed") }
fun watchlist(products: List<Product>, query: String, retailer: String, sort: WatchSort, status: WatchStatus = WatchStatus.ALL): List<Product> {
    val filtered = products.filter { (retailer == "All" || it.retailer.label == retailer) && (it.name.contains(query.trim(), true) || it.retailer.label.contains(query.trim(), true)) }
    val matching = filtered.filter { p -> when (status) {
        WatchStatus.ALL -> true
        WatchStatus.ACTIVE -> !p.paused
        WatchStatus.PAUSED -> p.paused
        WatchStatus.TARGET_MET -> p.price?.let { price -> p.target?.let { price <= it } } == true
        WatchStatus.FAILED -> p.error != null
    } }
    return when (sort) {
        WatchSort.RECENT -> matching.sortedByDescending { it.checked }
        WatchSort.PRICE -> matching.sortedBy { it.price ?: Long.MAX_VALUE }
        WatchSort.NAME -> matching.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        WatchSort.TARGET -> matching.sortedBy { p -> if (p.price != null && p.target != null) p.price!!.toDouble() / p.target else Double.MAX_VALUE }
    }
}
fun alertReason(p: Product, next: Long): String? {
    if (p.paused) return null
    val old = p.price
    if (p.targetAlerts && p.target != null && next <= p.target && (old == null || old > p.target)) return "Target reached: ${money(next)}"
    if (p.dropAlerts && old != null && next < old && (old - next).toDouble() / old * 100 + 0.000001 >= p.minimumDropPercent) return "Price dropped to ${money(next)}"
    return null
}
fun historyCsv(products: List<Product>): String {
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" + value else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    return buildString {
        append("product,retailer,url,observed_at_utc,price_ron,target_ron\r\n")
        products.forEach { p -> p.history.forEach { o ->
            append(listOf(p.name, p.retailer.label, p.url, java.time.Instant.ofEpochMilli(o.time).toString(), java.math.BigDecimal.valueOf(o.price, 2).toPlainString(), p.target?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()).joinToString(",") { cell(it) })
            append("\r\n")
        } }
    }
}
enum class Period(val label: String, val duration: Duration) {
    DAY("1 day", Duration.ofDays(1)), WEEK("7 days", Duration.ofDays(7)),
    MONTH("1 month", Duration.ofDays(30)), YEAR("1 year", Duration.ofDays(365))
}
fun money(bani: Long) = "%,.2f RON".format(java.util.Locale.US, bani / 100.0)
fun parseMoney(value: String): Long? {
    if (value.contains('-')) return null
    var s = value.replace(Regex("[^0-9,.]"), "")
    if (s.isEmpty()) return null
    if (s.contains(',') && s.contains('.')) {
        s = if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
    } else if (s.contains(',')) s = if (s.substringAfterLast(',').length == 3) s.replace(",", "") else s.replace(',', '.')
    else if (s.count { it == '.' } > 1 || (s.contains('.') && s.substringAfterLast('.').length == 3)) s = s.replace(".", "")
    return runCatching { java.math.BigDecimal(s).movePointRight(2).longValueExact().takeIf { it > 0 } }.getOrNull()
}
fun discountAssessment(product: Product, now: Long = System.currentTimeMillis()): String {
    val price = product.price ?: return "Waiting for a price"
    val previous = product.history.dropLast(1).filter { it.time >= now - Duration.ofDays(30).toMillis() }
    val dates = previous.map { it.time / 86_400_000 }.distinct()
    if (dates.size < 7) return "Collecting evidence · need 7 observed days"
    val low = previous.minOf { it.price }
    val reference = product.reference ?: return "No advertised reference price detected"
    if (reference > low * 1.05 && reference > price) return "Suspicious reference price · above observed 30-day low ${money(low)}"
    return if (price < low) "New observed 30-day low" else "Not below observed 30-day low ${money(low)}"
}
