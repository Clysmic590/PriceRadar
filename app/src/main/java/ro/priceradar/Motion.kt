package ro.priceradar

import androidx.compose.runtime.staticCompositionLocalOf

// Defaults to reduced motion on Android low-RAM devices; user can override it.
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Bound chart geometry while keeping first/last points and each bucket's extrema. */
fun chartSamples(history: List<Observation>, maxPoints: Int = 240): List<Observation> {
    require(maxPoints >= 4)
    if (history.size <= maxPoints) return history
    val buckets = (maxPoints - 2) / 2
    val interior = history.size - 2
    return buildList {
        add(history.first())
        repeat(buckets) { bucket ->
            val start = 1 + bucket.toLong() * interior / buckets
            val end = 1 + (bucket + 1L) * interior / buckets
            var low = start.toInt(); var high = low
            for (i in start.toInt() until end.toInt()) {
                if (history[i].price < history[low].price) low = i
                if (history[i].price > history[high].price) high = i
            }
            add(history[minOf(low, high)])
            if (low != high) add(history[maxOf(low, high)])
        }
        add(history.last())
    }
}
