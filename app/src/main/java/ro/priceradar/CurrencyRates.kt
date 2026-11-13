package ro.priceradar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

data class ExchangeRates(val rates: Map<String, Double>, val date: String, val fromCache: Boolean)

object CurrencyRates {
    private val supported = listOf("EUR", "USD", "GBP")

    suspend fun load(context: Context, forceRefresh: Boolean = false): ExchangeRates? = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences("currency_rates", Context.MODE_PRIVATE)
        fun cached(): ExchangeRates? {
            val values = supported.mapNotNull { code ->
                preferences.getString("rate_$code", null)?.toDoubleOrNull()?.let { code to it }
            }.toMap()
            if (values.size != supported.size) return null
            return ExchangeRates(values, preferences.getString("date", "").orEmpty(), true)
        }

        val savedAt = preferences.getLong("saved_at", 0L)
        if (!forceRefresh && !shouldRefreshCurrencyRates(savedAt, System.currentTimeMillis())) cached()?.let { return@withContext it }

        runCatching {
            val body = Jsoup.connect("https://api.frankfurter.dev/v1/latest?base=RON&symbols=EUR,USD,GBP")
                .ignoreContentType(true)
                .timeout(12_000)
                .execute()
                .body()
            val json = JSONObject(body)
            val ratesJson = json.getJSONObject("rates")
            val values = supported.associateWith { ratesJson.getDouble(it) }
            preferences.edit().apply {
                values.forEach { (code, rate) -> putString("rate_$code", rate.toString()) }
                putString("date", json.optString("date"))
                putLong("saved_at", System.currentTimeMillis())
            }.apply()
            ExchangeRates(values, json.optString("date"), false)
        }.getOrNull() ?: cached()
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CurrencyRateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "currency-rates",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

internal fun shouldRefreshCurrencyRates(savedAt: Long, now: Long): Boolean =
    savedAt <= 0L || now - savedAt >= 24L * 60 * 60 * 1_000

class CurrencyRateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (CurrencyRates.load(applicationContext, forceRefresh = true) != null) Result.success()
        else Result.retry()
}

fun convertedMoney(priceInBani: Long, currency: String, exchange: ExchangeRates?): String {
    if (currency == "RON") return money(priceInBani)
    val rate = exchange?.rates?.get(currency) ?: return money(priceInBani)
    val value = priceInBani / 100.0 * rate
    val symbol = when (currency) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> "$currency " }
    return "$symbol${"%,.2f".format(Locale.US, value)}"
}

