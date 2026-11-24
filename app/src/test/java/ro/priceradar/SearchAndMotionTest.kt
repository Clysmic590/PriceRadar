package ro.priceradar

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SearchAndMotionTest {
    @Test fun blackFridayForegroundIntervalsAreValidated() {
        assertEquals(listOf(10, 30), BLACK_FRIDAY_INTERVALS)
        assertEquals(10_000L, blackFridayRefreshMillis(10))
        assertEquals(30_000L, blackFridayRefreshMillis(30))
        assertEquals(10_000L, blackFridayRefreshMillis(7))
    }
    @Test fun blackFridayScanningStopsOutsideTheForeground() {
        assertTrue(shouldRunBlackFridayScan(mode = true, foreground = true, busy = false, loading = false, hasActiveProducts = true))
        assertFalse(shouldRunBlackFridayScan(mode = true, foreground = false, busy = false, loading = false, hasActiveProducts = true))
        assertFalse(shouldRunBlackFridayScan(mode = true, foreground = true, busy = true, loading = false, hasActiveProducts = true))
    }
    @Test fun blackFridayModeOnlyRunsNovemberFirstThroughFifteenth() {
        assertTrue(isBlackFridayWindow(LocalDate.of(2026, 11, 1)))
        assertTrue(isBlackFridayWindow(LocalDate.of(2026, 11, 15)))
        assertFalse(isBlackFridayWindow(LocalDate.of(2026, 10, 31)))
        assertFalse(isBlackFridayWindow(LocalDate.of(2026, 11, 16)))
        assertFalse(isBlackFridayWindow(LocalDate.of(2026, 12, 1)))
    }
    @Test fun offSeasonBlackFridayPasswordIsExactAndTrimmed() {
        assertTrue(blackFridayPasswordValid("BLACKFRIDAY"))
        assertTrue(blackFridayPasswordValid(" BLACKFRIDAY "))
        assertFalse(blackFridayPasswordValid("blackfriday"))
        assertFalse(blackFridayPasswordValid("BLACKFRIDAY1"))
    }
    @Test fun crossStoreSearchUsesBrandAndModelInsteadOfFullRetailerTitle() {
        assertEquals(
            "OPPO Find X10 Pro Max",
            crossStoreSearchQuery("Telefon mobil OPPO Find X10 Pro Max Titanium Grey 12GB+512GB + Incarcator OPPO 80W SuperVOOC Dual Ports GaN Charger, Alb")
        )
        assertEquals("SAMSUNG Galaxy S25 Ultra 5G", crossStoreSearchQuery("Smartphone SAMSUNG Galaxy S25 Ultra 5G 256GB, Black"))
        assertEquals("ASUS Vivobook 15 X1504VA", crossStoreSearchQuery("Laptop ASUS Vivobook 15 X1504VA, Intel Core i5, 16GB RAM"))
    }
    @Test fun landscapeIsAvailableOnlyOnLargeScreens() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, orientationForScreen(599))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, orientationForScreen(600))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, orientationForScreen(800))
    }
    @Test fun currencyRatesExpireAfterTwentyFourHours() {
        val day = 24L * 60 * 60 * 1_000
        assertTrue(shouldRefreshCurrencyRates(0L, day))
        assertFalse(shouldRefreshCurrencyRates(1_000L, 1_000L + day - 1))
        assertTrue(shouldRefreshCurrencyRates(1_000L, 1_000L + day))
    }
    @Test fun radarStopsReportingHealthyWhenAnyCheckFailed() {
        assertEquals("●  PRICERADAR WORKING PARTIALLY", radarStatus(active = 2, failed = 1))
        assertEquals("●  RADAR ACTIVE", radarStatus(active = 2, failed = 0))
        assertEquals("RADAR STANDBY", radarStatus(active = 0, failed = 0))
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

    @Test fun bottomNavigationPillFollowsDragAndSettlesOnNearestTab() {
        assertEquals(0.5f, bottomTabDragPosition(0f, 50f, 100f), 0.0001f)
        assertEquals(1.75f, bottomTabDragPosition(1f, 75f, 100f), 0.0001f)
        assertEquals(0f, bottomTabDragPosition(0.2f, -100f, 100f), 0.0001f)
        assertEquals(2f, bottomTabDragPosition(1.8f, 100f, 100f), 0.0001f)
        assertEquals(0, bottomTabForPosition(0.49f))
        assertEquals(1, bottomTabForPosition(0.5f))
        assertEquals(2, bottomTabForPosition(1.5f))
    }
}
