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
}
