package ro.priceradar

import org.junit.Assert.*
import org.junit.Test

class SearchAndMotionTest {
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
    @Test fun vexioRecognizesBrandProductPathsButRejectsBrowsingAndForeignPages() {
        listOf(
            "https://www.vexio.ro/huse-telefoane/samsung/123456-capac-protectie-s25-ultra/",
            "https://www.vexio.ro/accesorii-audio-hi-fi/ugreen/1785176-av170-2x-rca-cable-cinch-jack-3-5mm-2m-black/",
            "https://vexio.ro/casti/123-headphones/?utm_source=test#details"
        ).forEach { assertTrue(it, isProductUrl(it, Retailer.VEXIO)) }
        listOf(
            "https://www.vexio.ro/search/?q=samsung",
            "https://www.vexio.ro/huse-telefoane/samsung/",
            "https://www.vexio.ro/shopping/add-to-cart/",
            "https://vexio.ro.evil.com/huse/samsung/123-case/",
            "https://user@vexio.ro/huse/samsung/123-case/",
            "https://www.vexio.ro:444/huse/samsung/123-case/"
        ).forEach { assertFalse(it, isProductUrl(it, Retailer.VEXIO)) }
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
