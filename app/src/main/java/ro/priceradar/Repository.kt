package ro.priceradar

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class Database(context: Context): SQLiteOpenHelper(context, "prices.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE products (id TEXT PRIMARY KEY, payload TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
class Repository private constructor(context: Context) {
    private val db = Database(context.applicationContext)
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())
    val products = state.asStateFlow()
    private fun read(): List<Product> = db.readableDatabase.rawQuery("SELECT payload FROM products ORDER BY rowid DESC", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(decode(JSONObject(cursor.getString(0)))) }
    }
    private fun save(p: Product) {
        val json = JSONObject().put("id", p.id).put("url", p.url).put("name", p.name)
            .put("target", p.target ?: JSONObject.NULL).put("reference", p.reference ?: JSONObject.NULL)
            .put("checked", p.checked).put("error", p.error ?: JSONObject.NULL)
            .put("dropAlerts", p.dropAlerts).put("targetAlerts", p.targetAlerts)
            .put("coupons", JSONArray(p.coupons)).put("history", JSONArray().also { a -> p.history.forEach { a.put(JSONObject().put("time", it.time).put("price", it.price)) } })
        val values = android.content.ContentValues().apply { put("id", p.id); put("payload", json.toString()) }
        db.writableDatabase.insertWithOnConflict("products", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        state.value = read()
    }
    suspend fun add(url: String): String = withContext(Dispatchers.IO) { mutex.withLock {
        val clean = url.trim().substringBefore('#')
        val retailer = Retailer.fromUrl(clean)
        require(state.value.none { it.url == clean }) { "Already tracking this link" }
        val p = Product(UUID.randomUUID().toString(), clean, retailer, "New ${retailer.label} product")
        save(p); p.id
    } }
    suspend fun settings(id: String, target: Long?, drops: Boolean, targets: Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        state.value.find { it.id == id }?.let { save(it.copy(target = target, dropAlerts = drops, targetAlerts = targets)) }
    } }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        db.writableDatabase.delete("products", "id=?", arrayOf(id)); state.value = read()
    } }
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        var success = true
        state.value.toList().forEach { p ->
            try {
                val listing = RetailerClient().fetch(p.url)
                val now = System.currentTimeMillis()
                val current = p.copy(name = listing.name, history = (p.history + Observation(now, listing.price)).filter { it.time >= now - 366L * 86_400_000 }, reference = listing.reference, coupons = listing.coupons, checked = now, error = null)
                save(current)
                val old = p.price
                val drop = p.dropAlerts && old != null && listing.price < old
                val target = p.target
                val crossed = p.targetAlerts && target != null && listing.price <= target && (old == null || old > target)
                if (drop || crossed) runCatching {
                    Alerts.show(context, p.id, listing.name, if (crossed) "Target reached: ${money(listing.price)}" else "Price dropped to ${money(listing.price)}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                success = false; save(p.copy(error = e.message ?: "Refresh failed", checked = System.currentTimeMillis()))
            }
        }; success
    } }
    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
        private fun decode(j: JSONObject): Product {
            val h = j.getJSONArray("history"); val c = j.getJSONArray("coupons")
            return Product(j.getString("id"), j.getString("url"), Retailer.fromUrl(j.getString("url")), j.getString("name"),
                List(h.length()) { h.getJSONObject(it).let { o -> Observation(o.getLong("time"), o.getLong("price")) } },
                if (j.isNull("target")) null else j.getLong("target"), if (j.isNull("reference")) null else j.getLong("reference"),
                List(c.length()) { c.getString(it) }, if (j.isNull("error")) null else j.getString("error"), j.optLong("checked"), j.optBoolean("dropAlerts", true), j.optBoolean("targetAlerts", true))
        }
    }
}
