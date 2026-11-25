package ro.priceradar

import org.junit.Assert.*
import org.junit.Test

class UpgradeTest {
    private val base = Product("id", "https://emag.ro/p", Retailer.EMAG, "Headphones", listOf(Observation(1000, 10000)))
    @Test fun minimumDropAndTargetCrossing() {
        val p = base.copy(minimumDropPercent = 10, target = 9500)
        assertNull(alertReason(p.copy(targetAlerts = false), 9500))
        assertTrue(alertReason(p.copy(targetAlerts = false), 9000)!!.startsWith("Price dropped"))
        assertTrue(alertReason(p, 9500)!!.startsWith("Target reached"))
        assertNull(alertReason(p.copy(history = listOf(Observation(2000, 9400))), 9400))
        assertNull(alertReason(p.copy(paused = true), 5000))
        assertNull(alertReason(base, 10000))
    }
    @Test fun searchSortAndUnknownPrices() {
        val unknown = base.copy(id = "other", name = "Speaker", history = emptyList())
        assertEquals(listOf(base), watchlist(listOf(unknown, base), "HEAD", "All", WatchSort.NAME))
        assertEquals(listOf(base, unknown), watchlist(listOf(unknown, base), "", "All", WatchSort.PRICE))
        assertTrue(watchlist(listOf(base), "", "Altex", WatchSort.RECENT).isEmpty())
    }
    @Test fun csvEscapesNamesAndUsesExactMoney() {
        val csv = historyCsv(listOf(base.copy(name = "=SUM(1,2)\"", target = 9999)))
        assertTrue(csv.contains("\"'=SUM(1,2)\"\"\""))
        assertTrue(csv.contains("1970-01-01T00:00:01Z"))
        assertTrue(csv.contains("\"100.00\",\"99.99\""))
        assertEquals(1, historyCsv(emptyList()).lines().filter { it.isNotBlank() }.size)
    }
    @Test fun statusFiltersCombineWithSearchAndRetailer() {
        val reached = base.copy(id = "reached", target = 10000)
        val paused = base.copy(id = "paused", paused = true)
        val failed = base.copy(id = "failed", error = "Offline")
        val unknown = base.copy(id = "unknown", history = emptyList(), target = 10000)
        val products = listOf(reached, paused, failed, unknown)
        assertEquals(listOf(paused), watchlist(products, "", "All", WatchSort.NAME, WatchStatus.PAUSED))
        assertEquals(listOf(reached), watchlist(products, "HEAD", "eMAG", WatchSort.NAME, WatchStatus.TARGET_MET))
        assertEquals(listOf(failed), watchlist(products, "", "All", WatchSort.NAME, WatchStatus.FAILED))
        assertFalse(watchlist(products, "", "All", WatchSort.NAME, WatchStatus.ACTIVE).contains(paused))
        assertTrue(watchlist(products, "", "Altex", WatchSort.NAME, WatchStatus.TARGET_MET).isEmpty())
        assertTrue(watchlist(products, "Speaker", "All", WatchSort.NAME, WatchStatus.FAILED).isEmpty())
    }
    @Test fun compareStoresDisappearWhenSameProductIsAlreadyTracked() {
        val emag = base.copy(name = "Samsung Galaxy S25 Ultra 256GB Titanium")
        val altex = base.copy(id = "altex", url = "https://altex.ro/cpd/ABC", retailer = Retailer.ALTEX, name = "Telefon Samsung Galaxy S25 Ultra 256 GB Titanium")
        assertEquals(listOf(Retailer.VEXIO), availableCompareStores(emag, listOf(emag, altex)))
        val vexio = base.copy(id = "vexio", url = "https://vexio.ro/telefon/p", retailer = Retailer.VEXIO, name = "Samsung Galaxy S25 Ultra Titanium 256GB")
        assertTrue(availableCompareStores(emag, listOf(emag, altex, vexio)).isEmpty())
    }
    @Test fun couponCleanerRejectsProductAndLegalPageText() {
        val result = cleanCouponTexts(listOf(
            "Cod produs: MJR4QZD/A Telefon mobil Apple iPhone",
            "Cod Fiscal: RO 31962960 Sediu social Strada Oxigenului nr 11 Telefon 0374 477 111",
            "cod de confirmare din aplicatia Mail atunci cand suni",
            "Foloseste cod de reducere SAVE10 pentru 10% extra reducere",
            "BLACKFRIDAY20"
        ))
        assertEquals(listOf("Foloseste cod de reducere SAVE10 pentru 10% extra reducere", "BLACKFRIDAY20"), result)
    }
}

