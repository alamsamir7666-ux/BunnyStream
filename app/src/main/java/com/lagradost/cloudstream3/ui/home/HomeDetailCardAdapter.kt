package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.HomeResultDetailBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.getImageFromDrawable

/**
 * YouTube layout expanded home list: one full-width details-style card per
 * row (16:9 backdrop, title, meta row, plot) like the movie details page
 * header. The backdrop, year and plot come from [HomeCardCache]; until the
 * details arrive the card shows the poster, type, score and provider.
 */
class HomeDetailCardAdapter(
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(
    itemSame = { a, b -> a.url == b.url && a.apiName == b.apiName },
    contentSame = { a, b -> a == b }
)) {
    companion object {
        /** Horizontal margin on each side of a card in the expanded sheet. */
        private const val CARD_MARGIN_DP = 12

        private fun typeSingular(type: TvType?): Int? = when (type) {
            TvType.Movie, TvType.AnimeMovie -> R.string.movies_singular
            TvType.TvSeries -> R.string.tv_series_singular
            TvType.Anime -> R.string.anime_singular
            TvType.Cartoon -> R.string.cartoons_singular
            TvType.Documentary -> R.string.documentaries_singular
            TvType.Live -> R.string.live_singular
            TvType.OVA -> R.string.ova_singular
            TvType.AsianDrama -> R.string.asian_drama_singular
            else -> null
        }
    }

    var hasNext: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val binding = HomeResultDetailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        // The sheet spans the full screen width and the card is full width
        // minus its 12dp margins, so the 16:9 image height can be derived
        // up front. This also keeps every card the same size so recycled
        // views never mix differently sized cards.
        val cardWidth = parent.context.resources.displayMetrics.widthPixels -
            (CARD_MARGIN_DP * 2).toPx
        binding.detailImageContainer.updateLayoutParams {
            height = (cardWidth * 9f / 16f).toInt()
        }
        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        (holder.view as? HomeResultDetailBinding)?.let {
            clearImage(it.detailPoster)
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SearchResponse,
        position: Int
    ) {
        val binding = holder.view as? HomeResultDetailBinding ?: return
        val context = binding.root.context

        binding.detailCard.tag = item.url
        binding.detailTitle.text = item.name
        if (!item.posterUrl.isNullOrEmpty()) {
            binding.detailPoster.loadImage(item.posterUrl, item.posterHeaders) {
                error { getImageFromDrawable(context, R.drawable.default_cover) }
            }
        } else binding.detailPoster.loadImage(R.drawable.default_cover)

        fun applyDetails(details: HomeCardCache.CardDetails?) {
            // The view may have been recycled to another item while the
            // details were loading.
            if (binding.detailCard.tag != item.url) return
            details?.backdropUrl?.let { backdrop ->
                binding.detailPoster.loadImage(backdrop, item.posterHeaders)
            }
            binding.detailPlot.isVisible = details?.plot != null
            details?.plot?.let { binding.detailPlot.text = it }
            binding.detailMeta.text = buildMeta(context, item, details)
        }

        // Instantly show whatever is already known, then upgrade to the full
        // details-page information (backdrop, year, plot) when it arrives.
        applyDetails(HomeCardCache.peek(item))
        HomeCardCache.fetch(item) { details ->
            applyDetails(details)
        }

        binding.root.setOnClickListener { view ->
            clickCallback(SearchClickCallback(SEARCH_ACTION_LOAD, view, position, item))
        }
        binding.root.setOnLongClickListener { view ->
            clickCallback(SearchClickCallback(SEARCH_ACTION_SHOW_METADATA, view, position, item))
            true
        }
    }

    private fun buildMeta(
        context: Context,
        item: SearchResponse,
        details: HomeCardCache.CardDetails?
    ): String {
        val parts = mutableListOf<String>()
        typeSingular(item.type)?.let { parts.add(context.getString(it)) }
        item.score?.toStringNull(0.1, 10, 1)?.let { parts.add("★ $it/10") }
        details?.year?.let { parts.add(it.toString()) }
        item.apiName?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }
}
