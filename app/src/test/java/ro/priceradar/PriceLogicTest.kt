package ro.priceradar

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class PriceLogicTest {
    @Test fun observedEmagStructuredOffer() {
        val html = javaClass.getResource("/emag-observed.html")!!.readText()
        val result = RetailerClient().parse(Jsoup.parse(html), "https://www.emag.ro/pd/DFHWK8MBM/")
        assertEquals(119999L, result.price)
        assertEquals("Sony WH-1000XM5L", result.name)
    }
    @Test fun romanianAndStructuredPrices() {
        assertEquals(123456L, parseMoney("1.234,56 lei"))
        assertEquals(123456L, parseMoney("1234.56"))
        assertEquals(123400L, parseMoney("1.234"))
        assertNull(parseMoney("unavailable"))
        assertNull(parseMoney("0"))
        assertNull(parseMoney("-100"))
    }
    @Test fun rejectsUntrustedLinks() {
        listOf("http://emag.ro/p", "https://emag.ro.evil.com/p", "https://user@emag.ro/p", "https://altex.ro:8080/p").forEach {
            assertTrue(runCatching { Retailer.fromUrl(it) }.isFailure)
        }
        assertEquals(Retailer.VEXIO, Retailer.fromUrl("https://www.vexio.ro/product"))
    }
    @Test fun structuredOfferAndCouponForEveryRetailer() {
        Retailer.entries.forEach { retailer ->
            val html = """<h1>Headphones</h1><script type="application/ld+json">{"@graph":[{"@type":"Product","name":"Headphones","offers":{"@type":"Offer","price":"299.99","priceCurrency":"RON"}}]}</script><del class="old-price">399,99 lei</del><div class="coupon">Use AUDIO10 at checkout</div>"""
            val result = RetailerClient().parse(Jsoup.parse(html), "https://${retailer.host}/product")
            assertEquals(29999L, result.price)
            assertEquals(39999L, result.reference)
            assertEquals(listOf("Use AUDIO10 at checkout"), result.coupons)
        }
    }
    @Test fun blockedPageDoesNotInventPrice() {
        assertTrue(runCatching { RetailerClient().parse(Jsoup.parse("<h1>Verify you are human</h1>"), "https://emag.ro/product") }.isFailure)
    }
    @Test fun foreignCurrencyRejected() {
        val html = """<script type="application/ld+json">{"@type":"Product","offers":{"price":"50","priceCurrency":"EUR"}}</script>"""
        assertTrue(runCatching { RetailerClient().parse(Jsoup.parse(html), "https://altex.ro/p") }.isFailure)
    }
    @Test fun discountNeedsDistinctObservedDays() {
        val now = 40L * 86_400_000
        val p = Product("id", "https://emag.ro/p", Retailer.EMAG, "Test", List(20) { Observation(now - it * 1000, 10000) }, reference = 20000)
        assertTrue(discountAssessment(p, now).contains("Collecting evidence"))
    }
    @Test fun inflatedReferenceIsFlagged() {
        val now = 40L * 86_400_000
        val history = (8 downTo 1).map { Observation(now - it * 86_400_000, 10000) } + Observation(now, 9500)
        val p = Product("id", "https://emag.ro/p", Retailer.EMAG, "Test", history, reference = 20000)
        assertTrue(discountAssessment(p, now).startsWith("Suspicious"))
    }
}
