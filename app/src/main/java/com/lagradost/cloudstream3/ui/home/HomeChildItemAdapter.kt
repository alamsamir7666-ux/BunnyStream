package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.HomeRemoveGridBinding
import com.lagradost.cloudstream3.databinding.HomeRemoveGridExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeResultWideBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class HomeScrollViewHolderState(view: ViewBinding) : ViewHolderState<Boolean>(view) {
    // very shitty that we cant store the state when the view clears,
    // but this is because the focus clears before the view is removed
    // so we have to manually store it
    var wasFocused: Boolean = false
    override fun save(): Boolean = wasFocused
    override fun restore(state: Boolean) {
        if (state) {
            wasFocused = false
        }
    }
}

class ResumeItemAdapter(
    nextFocusUp: Int? = null,
    nextFocusDown: Int? = null,
    clickCallback: (SearchClickCallback) -> Unit,
    private val removeCallback: (View) -> Unit,
) : HomeChildItemAdapter(
    id = "resumeAdapter".hashCode(),
    nextFocusUp = nextFocusUp,
    nextFocusDown = nextFocusDown,
    clickCallback = clickCallback
) {
    override val footers = 0

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Boolean> {
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeRemoveGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeRemoveGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Boolean>) {
        // Clear the image, idk if this saves ram or not, but I guess?
        clearImage(holder.view.root.findViewById(R.id.imageView))
    }

    override fun onBindFooter(holder: ViewHolderState<Boolean>) {
        this.applyBinding(holder, false)
        when (val binding = holder.view) {
            is HomeRemoveGridBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }

            is HomeRemoveGridExpandedBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }
        }
        holder.itemView.apply {
            nextFocusUp?.let {
                nextFocusUpId = it
            }
            nextFocusDown?.let {
                nextFocusDownId = it
            }

            setOnClickListener { v ->
                removeCallback.invoke(v ?: return@setOnClickListener)
            }
        }
    }
}

/** Remember to set `updatePosterSize` to cache the poster size,
 * otherwise the width and height is unset */
open class HomeChildItemAdapter(
    id: Int,
    var nextFocusUp: Int? = null,
    var nextFocusDown: Int? = null,
    var clickCallback: (SearchClickCallback) -> Unit,
) :
    BaseAdapter<SearchResponse, Boolean>(
        id, diffCallback = BaseDiffCallback(
            itemSame = { a, b ->
                a.url == b.url && a.name == b.name
            },
            contentSame = { a, b ->
                a == b
            })
    ) {
    var hasNext: Boolean = false
    var isHorizontal: Boolean = false
        set(value) {
            field = value
            updateCachedPosterSize()
        }

    private fun updateCachedPosterSize() {
        setWidth = if (!isHorizontal) {
            minPosterSize
        } else {
            maxPosterSize
        }
        setHeight = if (!isHorizontal) {
            maxPosterSize
        } else {
            minPosterSize
        }
    }

    init {
        updateCachedPosterSize()
    }

    protected var setWidth = 0
    protected var setHeight = 0

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Boolean> {
        if (isLayout(Globals.YOUTUBE)) {
            val binding = HomeResultWideBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            sizeWideCard(binding.root, binding.wideImageContainer, parent)
            return HomeScrollViewHolderState(binding)
        }
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    companion object {
        // The vast majority of the lag comes from creating the view
        // This simply shares the views between all HomeChildItemAdapter
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 20) }

        var minPosterSize: Int = 0
        var maxPosterSize: Int = 0

        fun updatePosterSize(context: Context, value: Int? = null) {
            val scale = value ?: PreferenceManager.getDefaultSharedPreferences(context)
                ?.getInt(context.getString(R.string.poster_size_key), 0) ?: 0
            // Scale by +10% per step
            val mul = 1.0f + scale * 0.1f
            minPosterSize = (114.toPx.toFloat() * mul).toInt()
            maxPosterSize = (180.toPx.toFloat() * mul).toInt()
        }

        fun updateLayoutParms(layout: FrameLayout, width: Int, height: Int) {
            val params = layout.layoutParams
            if (params.height == height && params.width == width) return

            params.width = width
            params.height = height

            layout.layoutParams = params
        }

        /** YouTube layout: how much of the row width a single wide card occupies.
         *  The next card peeks behind it so the row is clearly swipeable. */
        private const val WIDE_CARD_WIDTH_RATIO = 0.70f

        /** YouTube layout wide card: sizes the card to ~70% of the row width
         *  and its image area to 16:9, the aspect of the details page
         *  horizontal poster (backdrop) shown in the card. */
        fun sizeWideCard(root: View, imageContainer: View?, parent: ViewGroup) {
            val screenWidth = parent.context.resources.displayMetrics.widthPixels
            // Full-width rows give the exact size; narrow (wrap_content, few
            // items) rows must not shrink the cards, so fall back to the
            // screen width. This keeps every card the same size so the shared
            // RecycledViewPool never mixes differently sized wide cards.
            val rowWidth =
                if (parent.width >= screenWidth * 0.9f) parent.width else screenWidth
            val cardWidth = (rowWidth * WIDE_CARD_WIDTH_RATIO).toInt()

            root.updateLayoutParams {
                width = cardWidth
            }
            imageContainer?.updateLayoutParams {
                height = (cardWidth * 9f / 16f).toInt()
            }
        }
    }

    protected fun applyBinding(holder: ViewHolderState<Boolean>, isFirstItem: Boolean) {
        when (val binding = holder.view) {
            is HomeResultGridBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }

            is HomeResultGridExpandedBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)

                if (isFirstItem) { // to fix tv
                    binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                }
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Boolean>,
        item: SearchResponse,
        position: Int
    ) {
        applyBinding(holder, position == 0)

        SearchResultBuilder.bind(
            clickCallback = { click ->
                // ok, so here we hijack the callback to fix the focus
                when (click.action) {
                    SEARCH_ACTION_LOAD -> (holder as? HomeScrollViewHolderState)?.wasFocused = true
                }
                clickCallback(click)
            },
            item,
            position,
            holder.itemView,
            nextFocusUp,
            nextFocusDown
        )

        (holder.view as? HomeResultWideBinding)?.let { wide ->
            // The wide YouTube card always shows the title under the image,
            // regardless of the poster title preference.
            wide.imageText.isVisible = true
            // Render the rating as plain "star 7.9" text instead of the
            // poster corner pill that SearchResultBuilder applies.
            wide.textRating.setBackgroundResource(0)

            // Show the same horizontal poster as the details page header
            // (backgroundPosterUrl) instead of the portrait poster. It is
            // not part of search results, so load the item details once per
            // session and cache; until it arrives the portrait poster
            // bound by SearchResultBuilder stays as a natural fallback.
            // The tag guards against the view being recycled to another
            // item before the backdrop is ready.
            wide.imageView.tag = item.url
            HomeBackdropCache.fetch(item) { backdrop ->
                if (backdrop != null && wide.imageView.tag == item.url) {
                    wide.imageView.loadImage(backdrop, item.posterHeaders)
                }
            }

            val typeRes = when (item.type) {
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
            wide.wideTypeChip.isVisible = typeRes != null
            typeRes?.let { wide.wideTypeChip.setText(it) }
        }

        holder.itemView.tag = position
    }
}
