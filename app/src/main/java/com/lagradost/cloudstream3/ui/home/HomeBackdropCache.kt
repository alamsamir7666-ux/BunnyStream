package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import java.util.concurrent.ConcurrentHashMap

/**
 * Session cache of horizontal backdrop art for YouTube layout home cards.
 *
 * Home rows are [SearchResponse]s which only carry the portrait poster url,
 * while the movie details page shows the horizontal poster
 * (LoadResponse.backgroundPosterUrl). The wide 16:9 home cards want that
 * horizontal art, so on first bind each item's details are loaded once
 * (APIRepository.load has its own 10 minute cache) and the backdrop url is
 * remembered for the rest of the session. An empty string means "provider has
 * no backdrop / the request failed" and stops repeated re-fetching.
 */
object HomeBackdropCache {
    private val cache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private fun key(item: SearchResponse): String = "${item.apiName}|${item.url}"

    /** Returns the cached backdrop url for the item, or null when unknown. */
    fun get(item: SearchResponse): String? = cache[key(item)]?.takeIf { it.isNotEmpty() }

    /**
     * Returns the backdrop url for the item, calling [onReady] on the main
     * thread. Cache hits call back synchronously; a miss starts (or joins) a
     * background details load exactly once per item per session. When the
     * provider has no horizontal art, [onReady] receives null and the caller
     * simply keeps the poster already displayed by SearchResultBuilder.
     */
    fun fetch(item: SearchResponse, onReady: (String?) -> Unit) {
        val k = key(item)
        cache[k]?.let { cached ->
            onReady(cached.takeIf { it.isNotEmpty() })
            return
        }
        // A request for this item is already running; the next rebind reads
        // the cache once it lands, so no callback is needed here.
        if (!inFlight.add(k)) return

        ioSafe {
            val backdrop = try {
                val api = APIHolder.getApiFromNameNull(item.apiName)
                    ?: APIHolder.getApiFromUrlNull(item.url)
                if (api == null) null else {
                    val response = APIRepository(api).load(item.url)
                    (response as? Resource.Success)?.value?.backgroundPosterUrl
                }
            } catch (e: Exception) {
                null
            }
            cache[k] = backdrop ?: ""
            runOnMainThread {
                onReady(backdrop)
            }
        }
    }
}
