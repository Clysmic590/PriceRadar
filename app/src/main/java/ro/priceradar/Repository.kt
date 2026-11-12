package ro.priceradar

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val refreshMutex = Mutex()
    private val state = MutableStateFlow<List<Product>>(emptyList())
    val products = state.asStateFlow()
    private val startupErrorState = MutableStateFlow<String?>(null)
    val startupError = startupErrorState.asStateFlow()
    private val loadingState = MutableStateFlow(true)
    val loading = loadingState.asStateFlow()
    private val initialization = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
        try { mutex.withLock { state.value = read() } }
        catch (e: Exception) { startupErrorState.value = "Could not load your watchlist. Restart the app to retry."; throw e }
        finally { loadingState.value = false }
    }
    private fun read(): List<Product> = db.readableDatabase.rawQuery("SELECT payload FROM products ORDER BY rowid DESC", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(decode(JSONObject(cursor.getString(0)))) }
    }
    private fun save(p: Product) {
        val json = JSONObject().put("id", p.id).put("url", p.url).put("name", p.name)
            .put("target", p.target ?: JSONObject.NULL).put("reference", p.reference ?: JSONObject.NULL)
            .put("checked", p.checked).put("error", p.error ?: JSONObject.NULL)
            .put("dropAlerts", p.dropAlerts).put("targetAlerts", p.targetAlerts)
            .put("paused", p.paused).put("minimumDropPercent", p.minimumDropPercent)
            .put("coupons", JSONArray(p.coupons)).put("history", JSONArray().also { a -> p.history.forEach { a.put(JSONObject().put("time", it.time).put("price", it.price)) } })
        val values = android.content.ContentValues().apply { put("id", p.id); put("payload", json.toString()) }
        db.writableDatabase.insertWithOnConflict("products", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        val current = state.value
        state.value = if (current.any { it.id == p.id }) current.map { if (it.id == p.id) p else it } else listOf(p) + current
    }
    suspend fun add(url: String): String = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        val clean = url.trim().substringBefore('#')
        val retailer = Retailer.fromUrl(clean)
        require(state.value.none { it.url == clean }) { "Already tracking this link" }
        val p = Product(UUID.randomUUID().toString(), clean, retailer, "New ${retailer.label} product")
        save(p); p.id
    } }
    suspend fun settings(id: String, target: Long?, drops: Boolean, targets: Boolean, minimumDrop: Int) = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        state.value.find { it.id == id }?.let { save(it.copy(target = target, dropAlerts = drops, targetAlerts = targets, minimumDropPercent = minimumDrop.coerceIn(0, 100))) }
    } }
    suspend fun pause(id: String, paused: Boolean) = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        state.value.find { it.id == id }?.let { save(it.copy(paused = paused)) }
    } }
    suspend fun pauseAll(paused: Boolean) = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        state.value.toList().forEach { product ->
            if (product.paused != paused) save(product.copy(paused = paused))
        }
    } }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        db.writableDatabase.delete("products", "id=?", arrayOf(id)); state.value = state.value.filterNot { it.id == id }
    } }
    suspend fun restore(product: Product) = withContext(Dispatchers.IO) { initialization.await(); mutex.withLock {
        if (state.value.none { it.id == product.id }) save(product)
    } }
    suspend fun refresh(context: Context, onlyId: String? = null): Boolean = withContext(Dispatchers.IO) {
        initialization.await()
        refreshMutex.withLock {
            val candidates = mutex.withLock { state.value.filter { !it.paused && (onlyId == null || it.id == onlyId) } }
            var success = true
            candidates.forEach { original ->
                try {
                    val listing = RetailerClient().fetch(original.url)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    mutex.withLock {
                        val latest = state.value.find { it.id == original.id && !it.paused }
                        if (latest != null) {
                            val now = System.currentTimeMillis()
                            val current = latest.copy(name = listing.name, history = (latest.history + Observation(now, listing.price)).filter { it.time >= now - 366L * 86_400_000 }, reference = listing.reference, coupons = listing.coupons, checked = now, error = null)
                            save(current)
                            alertReason(latest, listing.price)?.let { message -> runCatching { Alerts.show(context, latest.id, listing.name, message) } }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    success = false
                    mutex.withLock {
                        state.value.find { it.id == original.id && !it.paused }?.let { latest ->
                            save(latest.copy(error = e.message ?: "Refresh failed", checked = System.currentTimeMillis()))
                        }
                    }
                }
            }
            success
        }
    }
    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
        private fun decode(j: JSONObject): Product {
            val h = j.getJSONArray("history"); val c = j.getJSONArray("coupons")
            return Product(j.getString("id"), j.getString("url"), Retailer.fromUrl(j.getString("url")), j.getString("name"),
                List(h.length()) { h.getJSONObject(it).let { o -> Observation(o.getLong("time"), o.getLong("price")) } },
                if (j.isNull("target")) null else j.getLong("target"), if (j.isNull("reference")) null else j.getLong("reference"),
                List(c.length()) { c.getString(it) }, if (j.isNull("error")) null else j.getString("error"), j.optLong("checked"), j.optBoolean("dropAlerts", true), j.optBoolean("targetAlerts", true), j.optBoolean("paused", false), j.optInt("minimumDropPercent", 0).coerceIn(0,100))
        }
    }
}

