package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.databinding.HomeScrollViewBinding
import com.lagradost.cloudstream3.databinding.HomeScrollViewYoutubeBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.settings.Globals
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class HomeScrollAdapter(
    val callback: ((View, Int, LoadResponse) -> Unit),
    /** Play button click for the YouTube layout hero card. */
    val playCallback: ((View, Int, LoadResponse) -> Unit)? = null,
) : NoStateAdapter<LoadResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.uniqueUrl == b.uniqueUrl && a.name == b.name
})) {
    var hasMoreItems: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(Globals.YOUTUBE)) {
            HomeScrollViewYoutubeBinding.inflate(inflater, parent, false)
        } else {
            HomeScrollViewBinding.inflate(inflater, parent, false)
        }

        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is HomeScrollViewBinding -> {
                clearImage(binding.homeScrollPreview)
            }

            is HomeScrollViewYoutubeBinding -> {
                clearImage(binding.homeScrollPreview)
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: LoadResponse,
        position: Int,
    ) {
        val binding = holder.view

        val posterUrl = item.backgroundPosterUrl ?: item.posterUrl

        when (binding) {
            is HomeScrollViewBinding -> {
                binding.homeScrollPreview.loadImage(posterUrl, item.posterHeaders)
                binding.homeScrollPreviewTags.apply {
                    text = item.tags?.joinToString(" • ") ?: ""
                    isGone = item.tags.isNullOrEmpty()
                    maxLines = 2
                }
                binding.homeScrollPreviewTitle.text = item.name.html()

                bindLogo(
                    url = item.logoUrl,
                    headers = item.posterHeaders,
                    titleView = binding.homeScrollPreviewTitle,
                    logoView = binding.homePreviewLogo
                )
            }

            is HomeScrollViewYoutubeBinding -> {
                binding.homeScrollPreview.loadImage(posterUrl, item.posterHeaders)
                binding.homeScrollPreviewTitle.text = item.name.html()
                binding.homeScrollPreviewTags.apply {
                    text = HomeFeedFilter.buildHeroMeta(binding.root.context, item)
                    isGone = text.isNullOrBlank()
                    maxLines = 1
                }

                binding.homeYtHeroPlay.setOnClickListener { view ->
                    playCallback?.invoke(view, position, item)
                }
                binding.homeYtHeroInfo.setOnClickListener { view ->
                    callback.invoke(view, position, item)
                }
            }
        }
    }
}
