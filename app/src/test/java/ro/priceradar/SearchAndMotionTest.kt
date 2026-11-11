package ro.priceradar

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class SearchAndMotionTest {
    @Test fun structuredResultsKeepStoreIdentityAndRonPrices() {
        Retailer.entries.forEach { store ->
            val path = when (store) {
                Retailer.EMAG -> "/headphones/pd/ABC123/"
                Retailer.ALTEX -> "/headphones/cpd/ABC123/"
                Retailer.VEXIO -> "/casti/123-headphones/"
            }
            val html = """<script type="application/ld+json">{"@type":"ItemList","itemListElement":[{"@type":"ListItem","item":{"@type":"Product","name":"Headphones","url":"$path","offers":{"price":"299.99","priceCurrency":"RON"}}}]}</script>"""
            val results = ProductSearchClient().parse(Jsoup.parse(html), store)
            assertEquals(1, results.size)
            assertEquals(store, results.single().retailer)
            assertEquals(29999L, results.single().price)
            assertTrue(isProductUrl(results.single().url, store))
        }
    }
    @Test fun refusesForeignLinksAndDoesNotInventPriceOrResults() {
        val html = """<script type="application/ld+json">[
            {"@type":"Product","name":"Wrong store","url":"https://altex.ro/item/cpd/ABC/"},
            {"@type":"Product","name":"Foreign currency","url":"https://emag.ro/item/pd/ABC/","offers":{"price":"12","priceCurrency":"EUR"}},
            {"@type":"Product","name":"Malicious","url":"https://emag.ro.evil.com/pd/ABC/"}
            ]</script>"""
        val results = ProductSearchClient().parse(Jsoup.parse(html), Retailer.EMAG)
        assertEquals(listOf("Foreign currency"), results.map { it.name })
        assertNull(results.single().price)
        assertTrue(ProductSearchClient().parse(Jsoup.parse("<h1>Verify you are human</h1>"), Retailer.EMAG).isEmpty())
        assertFalse(isProductUrl("https://user@emag.ro/pd/ABC/", Retailer.EMAG))
        assertFalse(isProductUrl("http://emag.ro/pd/ABC/", Retailer.EMAG))
        assertFalse(isProductUrl("https://vexio.ro/search/?q=123", Retailer.VEXIO))
    }
    @Test fun cardResultsDeduplicateAndAreBounded() {
        val html = (1..50).joinToString("") { """<div class="card-item"><a href="/product/pd/ID$it/">Product $it</a><span class="product-new-price">123,45 lei</span></div>""" }
        val results = ProductSearchClient().parse(Jsoup.parse(html + html), Retailer.EMAG)
        assertEquals(24, results.size)
        assertEquals(results.size, results.map { it.url }.distinct().size)
        assertTrue(results.all { it.price == 12345L })
    }
    @Test fun searchTermsAreEncodedForEveryStore() {
        Retailer.entries.forEach { store ->
            val url = storeSearchUrl(store, "Sony & Samsung / +")
            assertEquals(store, Retailer.fromUrl(url))
            assertFalse(url.contains(" "))
            assertTrue(url.contains("%26"))
            assertTrue(url.contains("%2F"))
            assertTrue(url.contains("%2B"))
        }
    }
    @Test fun chartGeometryIsBoundedAndPreservesExtremesAndEndpoints() {
        val history = (0 until 100_000).map { i -> Observation(i.toLong(), when (i) { 54321 -> 100L; 54322 -> 999999L; else -> 10000L }) }
        val sampled = chartSamples(history)
        assertTrue(sampled.size <= 240)
        assertEquals(history.first(), sampled.first())
        assertEquals(history.last(), sampled.last())
        assertEquals(100L, sampled.minOf { it.price })
        assertEquals(999999L, sampled.maxOf { it.price })
        assertEquals(sampled.sortedBy { it.time }, sampled)
        assertEquals(history.take(3), chartSamples(history.take(3)))
        assertTrue(chartSamples(emptyList()).isEmpty())
    }
}
