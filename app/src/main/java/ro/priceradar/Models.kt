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
    val dropAlerts: Boolean = true, val targetAlerts: Boolean = true
) { val price: Long? get() = history.lastOrNull()?.price }
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
