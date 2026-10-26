package com.lagradost.cloudstream3.ui.home

import android.content.Context
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.isMovieType

/**
 * Shared state for the YouTube layout home category chips (All / Anime / Movies /
 * TV Shows / Action / Comedy).
 *
 * The selected category filters both the hero banner (preview) and the home feed
 * lists. Filtering is done client-side on the already loaded data; when a filter
 * produces nothing the original unfiltered content is shown as a fallback so the
 * home screen never appears broken.
 */
object HomeFeedFilter {

    enum class Category {
        ALL, ANIME, MOVIES, TV_SHOWS, ACTION, COMEDY
    }

    /** Currently selected category chip. */
    var current: Category = Category.ALL

    /** Invoked whenever [current] changes so the feed can be re-submitted. */
    var onChanged: (() -> Unit)? = null

    private val ANIME_TYPES = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    private val TV_SHOW_TYPES = setOf(TvType.TvSeries, TvType.AsianDrama)

    private fun TvType?.matchesType(category: Category): Boolean = when (category) {
        Category.ANIME -> this in ANIME_TYPES
        Category.MOVIES -> this != null && this.isMovieType()
        Category.TV_SHOWS -> this in TV_SHOW_TYPES
        else -> true
    }

    /** Matches a category against free text (list names / genre tags). */
    fun matchesText(category: Category, text: String?): Boolean {
        if (text == null) return false
        return when (category) {
            Category.ALL -> true
            Category.ANIME -> text.contains("anime", ignoreCase = true)
            Category.MOVIES -> text.contains("movie", ignoreCase = true)
            Category.TV_SHOWS -> text.contains("tv", ignoreCase = true) ||
                    text.contains("series", ignoreCase = true) ||
                    text.contains("show", ignoreCase = true)
            Category.ACTION -> text.contains("action", ignoreCase = true)
            Category.COMEDY -> text.contains("comedy", ignoreCase = true)
        }
    }

    /** True if the genre words (Action / Comedy) match the item tags. */
    private fun LoadResponse.matchesGenre(category: Category): Boolean =
        tags?.any { matchesText(category, it) } == true

    /** Filters the hero banner items, falling back to the full list when empty. */
    fun filterPreview(list: List<LoadResponse>): List<LoadResponse> {
        if (current == Category.ALL) return list
        val filtered = list.filter { item ->
            when (current) {
                Category.ACTION, Category.COMEDY -> item.matchesGenre(current)
                else -> item.type.matchesType(current) || item.matchesGenre(current)
            }
        }
        return filtered.ifEmpty { list }
    }

    /** Filters the home feed lists, dropping empty lists and falling back when all are empty. */
    fun filterPage(list: List<HomeViewModel.ExpandableHomepageList>): List<HomeViewModel.ExpandableHomepageList> {
        if (current == Category.ALL) return list
        val filtered = list.mapNotNull { expandable ->
            val homeList = expandable.list
            // A list whose own name matches keeps all of its items (the whole
            // list is inherently that category), otherwise filter by item type.
            if (matchesText(current, homeList.name)) {
                expandable
            } else {
                val items = homeList.list.filter { it.type.matchesType(current) }
                if (items.isEmpty()) {
                    null
                } else {
                    expandable.copy(list = homeList.copy(list = items))
                }
            }
        }
        return filtered.ifEmpty { list }
    }

    /** Builds the hero meta row: "Anime • Comedy • Fantasy • 1 Season". */
    fun buildHeroMeta(context: Context, item: LoadResponse): String {
        val parts = mutableListOf<String>()
        when (item.type) {
            TvType.Anime, TvType.AnimeMovie, TvType.OVA -> parts.add("Anime")
            TvType.Movie -> parts.add("Movie")
            TvType.TvSeries -> parts.add("TV Series")
            TvType.AsianDrama -> parts.add("Drama")
            TvType.Documentary -> parts.add("Documentary")
            else -> {}
        }
        item.tags?.take(3)?.let { tags -> parts.addAll(tags) }
        val seasons = countSeasons(item)
        if (seasons > 0) {
            parts.add(
                "$seasons " + context.getString(
                    if (seasons == 1) R.string.home_yt_season else R.string.home_yt_seasons
                )
            )
        }
        return parts.joinToString(" • ")
    }

    private fun countSeasons(item: LoadResponse): Int {
        val namedSeasons = when (item) {
            is AnimeLoadResponse -> item.seasonNames?.size
            is TvSeriesLoadResponse -> item.seasonNames?.size
            else -> null
        }
        if (namedSeasons != null && namedSeasons > 0) return namedSeasons
        return when (item) {
            is AnimeLoadResponse ->
                item.episodes.values.flatten().mapNotNull { it.season }.distinct().size
            is TvSeriesLoadResponse ->
                item.episodes.mapNotNull { it.season }.distinct().size
            else -> 0
        }
    }
}
