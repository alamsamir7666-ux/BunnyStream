package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import java.util.concurrent.ConcurrentHashMap

/**
 * Session cache of details-page information for YouTube layout home cards.
 *
 * Home rows and expanded lists are [SearchResponse]s which only carry the
 * portrait poster url, while the movie details page additionally shows the
 * horizontal backdrop, the year and the plot. Cards that want that
 * information load the item details once per session (APIRepository.load has
 * its own 10 minute cache) and this object remembers the result. [NONE]
 * means "the provider has no details / the request failed" and stops
 * repeated re-fetching.
 */
object HomeCardCache {
    /** Details-page information shown on YouTube layout cards. */
    data class CardDetails(
        val backdropUrl: String?,
        val plot: String?,
        val year: Int?,
    )

    private val NONE = CardDetails(null, null, null)
    private val cache = ConcurrentHashMap<String, CardDetails>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private fun key(item: SearchResponse): String = "${item.apiName}|${item.url}"

    /** Returns the cached details for the item, or null when not loaded yet. */
    fun peek(item: SearchResponse): CardDetails? = cache[key(item)]

    /**
     * Returns the details for the item, calling [onReady] on the main thread.
     * Cache hits call back synchronously; a miss starts (or joins) a
     * background details load exactly once per item per session.
     */
    fun fetch(item: SearchResponse, onReady: (CardDetails) -> Unit) {
        val k = key(item)
        cache[k]?.let { cached ->
            onReady(cached)
            return
        }
        // A request for this item is already running; the next rebind reads
        // the cache once it lands, so no callback is needed here.
        if (!inFlight.add(k)) return

        ioSafe {
            val details = try {
                val api = APIHolder.getApiFromNameNull(item.apiName)
                    ?: APIHolder.getApiFromUrlNull(item.url)
                if (api == null) {
                    NONE
                } else {
                    val response: LoadResponse? =
                        (APIRepository(api).load(item.url) as? Resource.Success)?.value
                    CardDetails(
                        backdropUrl = response?.backgroundPosterUrl,
                        plot = response?.plot,
                        year = response?.year,
                    )
                }
            } catch (e: Exception) {
                NONE
            }
            cache[k] = details
            runOnMainThread {
                onReady(details)
            }
        }
    }
}
