package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import coil3.asImage
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationBarItemView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadBinding
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadYoutubeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountEditDialog
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.selectHomepage
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.Globals
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.isCastApiAvailable
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarMargin
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import androidx.core.graphics.toColorInt
import com.lagradost.cloudstream3.ui.setRecycledViewPool

class HomeParentItemAdapterPreview(
    private val viewModel: HomeViewModel,
    private val accountViewModel: AccountViewModel
) : ParentItemAdapter(
    id = "HomeParentItemAdapterPreview".hashCode(),
    clickCallback = {
        viewModel.click(it)
    }, moreInfoClickCallback = {
        viewModel.popup(it)
    }, expandCallback = {
        viewModel.expand(it)
    }) {
    override val headers = 1

    /** Currently attached header holder, used to push provider changes to the YouTube top bar. */
    private var headerHolder: HeaderViewHolder? = null

    /** Updates the YouTube top bar brand (provider logo + name) when the home provider changes. */
    fun onApiChanged(apiName: String?) {
        headerHolder?.updateProviderBranding(apiName)
    }

    override fun onCreateHeader(parent: ViewGroup): ViewHolderState<Bundle> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(Globals.YOUTUBE)) {
            FragmentHomeHeadYoutubeBinding.inflate(inflater, parent, false)
        } else {
            FragmentHomeHeadBinding.inflate(inflater, parent, false)
        }

        return HeaderViewHolder(binding, viewModel, accountViewModel).also {
            headerHolder = it
        }
    }

    override fun onBindHeader(holder: ViewHolderState<Bundle>) {
        (holder as? HeaderViewHolder)?.bind()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewDetachedFromWindow()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewAttachedToWindow()
            }
        }
    }

    private class HeaderViewHolder(
        val binding: ViewBinding,
        val viewModel: HomeViewModel,
        accountViewModel: AccountViewModel,
    ) :
        ViewHolderState<Bundle>(binding) {

        override fun save(): Bundle =
            Bundle().apply {
                putParcelable(
                    "resumeRecyclerView",
                    resumeRecyclerView.layoutManager?.onSaveInstanceState()
                )
                putParcelable(
                    "bookmarkRecyclerView",
                    bookmarkRecyclerView.layoutManager?.onSaveInstanceState()
                )
                //putInt("previewViewpager", previewViewpager.currentItem)
            }

        override fun restore(state: Bundle) {
            state.getSafeParcelable<Parcelable>("resumeRecyclerView")?.let { recycle ->
                resumeRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
            state.getSafeParcelable<Parcelable>("bookmarkRecyclerView")?.let { recycle ->
                bookmarkRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
        }

        val previewAdapter = HomeScrollAdapter(
            callback = { view, position, item ->
                viewModel.click(
                    LoadClickCallback(0, view, position, item)
                )
            },
            playCallback = { view, position, item ->
                viewModel.click(
                    LoadClickCallback(START_ACTION_RESUME_LATEST, view, position, item)
                )
            }
        )

        private val resumeAdapter = ResumeItemAdapter(
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId,
            removeCallback = { v ->
                try {
                    val context = v.context ?: return@ResumeItemAdapter
                    val builder: AlertDialog.Builder =
                        AlertDialog.Builder(context)
                    // Copy pasted from https://github.com/recloudstream/cloudstream/pull/1658/files
                    builder.apply {
                        setTitle(R.string.clear_history)
                        setMessage(
                            context.getString(R.string.delete_message).format(
                                context.getString(
                                    R.string.continue_watching
                                )
                            )
                        )
                        setNegativeButton(R.string.cancel) { _, _ -> /*NO-OP*/ }
                        setPositiveButton(R.string.delete) { _, _ ->
                            DataStoreHelper.deleteAllResumeStateIds()
                            viewModel.reloadStored()
                        }
                        show()
                    }
                } catch (t: Throwable) {
                    // This may throw a formatting error
                    logError(t)
                }
            },
            clickCallback = { callback ->
                if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                    viewModel.click(callback)
                    return@ResumeItemAdapter
                }
                callback.view.context?.getActivity()?.showOptionSelectStringRes(
                    callback.view,
                    callback.card.posterUrl,
                    listOf(
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    ),
                    listOf(
                        R.string.action_open_play,
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    )
                ) { (isTv, actionId) ->
                    when (actionId + if (isTv) 0 else 1) {
                        // play
                        0 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    START_ACTION_RESUME_LATEST,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        //info
                        1 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    SEARCH_ACTION_LOAD,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        // remove
                        2 -> {
                            val card = callback.card
                            if (card is DataStoreHelper.ResumeWatchingResult) {
                                DataStoreHelper.removeLastWatched(card.parentId)
                                viewModel.reloadStored()
                            }
                        }
                    }
                }
            })
        private val bookmarkAdapter = HomeChildItemAdapter(
            id = "bookmarkAdapter".hashCode(),
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId
        ) { callback ->
            if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                viewModel.click(callback)
                return@HomeChildItemAdapter
            }

            (callback.view.context?.getActivity() as? MainActivity)?.loadPopup(
                callback.card,
                load = false
            )
            /*
            callback.view.context?.getActivity()?.showOptionSelectStringRes(
                callback.view,
                callback.card.posterUrl,
                listOf(
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks,
                ),
                listOf(
                    R.string.action_open_play,
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks
                )
            ) { (isTv, actionId) ->
                when (actionId + if (isTv) 0 else 1) { // play
                    0 -> {
                        viewModel.click(
                            SearchClickCallback(
                                START_ACTION_RESUME_LATEST,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    1 -> { // info
                        viewModel.click(
                            SearchClickCallback(
                                SEARCH_ACTION_LOAD,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    2 -> { // remove
                        DataStoreHelper.setResultWatchState(
                            callback.card.id,
                            WatchType.NONE.internalId
                        )
                        viewModel.reloadStored()
                    }
                }
            }
            */
        }

        private val previewViewpager: ViewPager2 =
            itemView.findViewById(R.id.home_preview_viewpager)

        private val homePreviewShimmer: com.facebook.shimmer.ShimmerFrameLayout? =
            itemView.findViewById(R.id.home_preview_shimmer)

        private val previewViewpagerText: ViewGroup =
            itemView.findViewById(R.id.home_preview_viewpager_text)

        // private val previewHeader: FrameLayout = itemView.findViewById(R.id.home_preview)
        private val resumeHolder: View = itemView.findViewById(R.id.home_watch_holder)
        private val resumeRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_watch_child_recyclerview)
        private val bookmarkHolder: View = itemView.findViewById(R.id.home_bookmarked_holder)
        private val bookmarkRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_bookmarked_child_recyclerview)

        private val headProfilePic: ImageView? = itemView.findViewById(R.id.home_head_profile_pic)
        private val headProfilePicCard: View? =
            itemView.findViewById(R.id.home_head_profile_padding)

        private val alternateHeadProfilePic: ImageView? =
            itemView.findViewById(R.id.alternate_home_head_profile_pic)
        private val alternateHeadProfilePicCard: View? =
            itemView.findViewById(R.id.alternate_home_head_profile_padding)

        private val topPadding: View? = itemView.findViewById(R.id.home_padding)

        private val alternativeAccountPadding: View? =
            itemView.findViewById(R.id.alternative_account_padding)

        private val homeNonePadding: View = itemView.findViewById(R.id.home_none_padding)

        private val watchSectionTitle: TextView? =
            itemView.findViewById(R.id.home_watch_parent_item_title)
        private val bookmarkSectionTitle: View? =
            itemView.findViewById(R.id.home_bookmark_parent_item_title)

        private var lastPreviewItems: List<LoadResponse> = emptyList()

        fun onSelect(item: LoadResponse, position: Int) {
            (binding as? FragmentHomeHeadBinding)?.apply {
                //homePreviewImage.setImage(item.posterUrl, item.posterHeaders)

                homePreviewPlay.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                homePreviewInfo.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

                // very ugly code, but I don't care
                val id = item.getId()
                val watchType =
                    DataStoreHelper.getResultWatchState(id)
                homePreviewBookmark.setText(watchType.stringRes)
                homePreviewBookmark.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(
                        homePreviewBookmark.context,
                        watchType.iconRes
                    ),
                    null,
                    null
                )

                homePreviewBookmark.setOnClickListener { fab ->
                    fab.context.getActivity()?.showBottomDialog(
                        WatchType.entries
                            .map { fab.context.getString(it.stringRes) }
                            .toList(),
                        DataStoreHelper.getResultWatchState(id).ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        val newValue = WatchType.entries[it]

                        ResultViewModel2().updateWatchStatus(
                            newValue,
                            fab.context,
                            item
                        ) { statusChanged: Boolean ->
                            if (!statusChanged) return@updateWatchStatus

                            homePreviewBookmark.setCompoundDrawablesWithIntrinsicBounds(
                                null,
                                ContextCompat.getDrawable(
                                    homePreviewBookmark.context,
                                    newValue.iconRes
                                ),
                                null,
                                null
                            )
                            homePreviewBookmark.setText(newValue.stringRes)
                        }
                    }
                }
            }
        }

        private val previewCallback: ViewPager2.OnPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    previewAdapter.apply {
                        if (position >= itemCount - 1 && hasMoreItems) {
                            hasMoreItems = false // don't make two requests
                            viewModel.loadMoreHomeScrollResponses()
                        }
                    }
                    val item = previewAdapter.getItemOrNull(position) ?: return
                    onSelect(item, position)
                }
            }

        fun onViewDetachedFromWindow() {
            previewViewpager.unregisterOnPageChangeCallback(previewCallback)
        }

        private val toggleList = listOf<Pair<Chip, WatchType>>(
            Pair(itemView.findViewById(R.id.home_type_watching_btt), WatchType.WATCHING),
            Pair(itemView.findViewById(R.id.home_type_completed_btt), WatchType.COMPLETED),
            Pair(itemView.findViewById(R.id.home_type_dropped_btt), WatchType.DROPPED),
            Pair(itemView.findViewById(R.id.home_type_on_hold_btt), WatchType.ONHOLD),
            Pair(itemView.findViewById(R.id.home_plan_to_watch_btt), WatchType.PLANTOWATCH),
        )

        private val toggleListHolder: ChipGroup? = itemView.findViewById(R.id.home_type_holder)

        /** Name of the provider currently shown in the YouTube top bar brand. */
        var currentApiName: String? = null
            private set

        fun bind() = Unit

        init {
            if (binding is FragmentHomeHeadYoutubeBinding) {
                fixPaddingStatusbarMargin(binding.homeTopBarYt)

                binding.homeSearchIconYt.setOnClickListener {
                    viewModel.queryTextSubmit("")
                }

                setUpCastButton(binding)

                // Tap the logo + provider name to switch home provider
                binding.homeYtBrand.setOnClickListener {
                    it.context.selectHomepage(currentApiName) { api ->
                        viewModel.loadAndCancel(api, forceReload = true, fromUI = true)
                    }
                }
                // Show the current provider brand immediately (kept up to date by
                // HomeParentItemAdapterPreview.onApiChanged)
                updateProviderBranding(viewModel.apiName.value)

                binding.homeYtChips.setOnCheckedStateChangeListener { _, checkedIds ->
                    val category = when (checkedIds.firstOrNull()) {
                        R.id.home_yt_chip_anime -> HomeFeedFilter.Category.ANIME
                        R.id.home_yt_chip_movies -> HomeFeedFilter.Category.MOVIES
                        R.id.home_yt_chip_tv_shows -> HomeFeedFilter.Category.TV_SHOWS
                        R.id.home_yt_chip_action -> HomeFeedFilter.Category.ACTION
                        R.id.home_yt_chip_comedy -> HomeFeedFilter.Category.COMEDY
                        else -> HomeFeedFilter.Category.ALL
                    }
                    HomeFeedFilter.current = category
                    applyPreviewFilter()
                    HomeFeedFilter.onChanged?.invoke()
                }
            } else {
                previewViewpager.setPageTransformer(HomeScrollTransformer())
            }

            previewViewpager.adapter = previewAdapter
            resumeRecyclerView.adapter = resumeAdapter
            bookmarkRecyclerView.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
            bookmarkRecyclerView.adapter = bookmarkAdapter

            resumeRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            bookmarkRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            // YouTube layout: inset the wide card rows from the screen edges
            // like the YouTube app, while still letting cards scroll behind
            // the padding (clipToPadding is false).
            if (isLayout(Globals.YOUTUBE)) {
                val pad = 12.toPx
                resumeRecyclerView.setPadding(pad, 0, pad, 0)
                bookmarkRecyclerView.setPadding(pad, 0, pad, 0)
            }

            fixPaddingStatusbarMargin(topPadding)

            for ((chip, watch) in toggleList) {
                chip.isChecked = false
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.loadStoredData(setOf(watch))
                    }
                    // Else if all are unchecked -> Do not load data
                    else if (toggleList.all { !it.first.isChecked }) {
                        viewModel.loadStoredData(emptySet())
                    }
                }
            }

            headProfilePicCard?.isGone = false
            alternateHeadProfilePicCard?.isGone = false

            (headProfilePic ?: alternateHeadProfilePic)?.observe(viewModel.currentAccount) { currentAccount ->
                headProfilePic?.loadImage(currentAccount?.image)
                alternateHeadProfilePic?.loadImage(currentAccount?.image)
            }

            headProfilePicCard?.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            fun showAccountEditBox(context: Context): Boolean {
                val currentAccount = DataStoreHelper.getCurrentAccount()
                return if (currentAccount != null) {
                    showAccountEditDialog(
                        context = context,
                        account = currentAccount,
                        isNewAccount = false,
                        accountEditCallback = { accountViewModel.handleAccountUpdate(it, context) },
                        accountDeleteCallback = {
                            accountViewModel.handleAccountDelete(
                                it,
                                context
                            )
                        }
                    )
                    true
                } else false
            }

            alternateHeadProfilePicCard?.setOnLongClickListener {
                showAccountEditBox(it.context)
            }
            headProfilePicCard?.setOnLongClickListener {
                showAccountEditBox(it.context)
            }

            alternateHeadProfilePicCard?.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            (binding as? FragmentHomeHeadBinding)?.apply {
                homeSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        viewModel.queryTextSubmit(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        viewModel.queryTextChange(newText)
                        return true
                    }
                })
            }
        }

        /** Re-filters the hero banner items with the current category chip. */
        private fun applyPreviewFilter() {
            previewAdapter.submitList(HomeFeedFilter.filterPreview(lastPreviewItems))
        }

        private fun setUpCastButton(binding: FragmentHomeHeadYoutubeBinding) {
            val act = itemView.context.getActivity() ?: return
            try {
                if (!act.isCastApiAvailable()) return
                CastButtonFactory.setUpMediaRouteButton(act, binding.homeMediaRouteButton)
                CastContext.getSharedInstance(act.applicationContext) {
                    it.run()
                }.addOnCompleteListener {
                    binding.homeMediaRouteButton.isGone = !it.isSuccessful
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        /**
         * Shows the current home provider name + logo in the YouTube top bar.
         * - Real provider: provider name + repo icon (circular), falling back to a
         *   colored letter avatar while the icon loads or if the repo has none.
         * - No provider / Random: falls back to the app brand (BunnyStream + play logo).
         */
        fun updateProviderBranding(apiName: String?) {
            val ytBinding = binding as? FragmentHomeHeadYoutubeBinding ?: return
            currentApiName = apiName
            when (apiName) {
                null, noneApi.name -> {
                    ytBinding.homeYtTitle.setText(R.string.home_yt_app_title)
                    ytBinding.homeYtLogo.setImageResource(R.drawable.ic_yt_logo)
                }
                randomApi.name -> {
                    ytBinding.homeYtTitle.setText(R.string.home_random)
                    ytBinding.homeYtLogo.setImageResource(R.drawable.ic_yt_logo)
                }
                else -> {
                    ytBinding.homeYtTitle.text = apiName
                    ytBinding.homeYtLogo.setImageDrawable(createLetterAvatar(apiName))
                    lookupProviderIcon(apiName)
                }
            }
        }

        /** Loads the provider icon url, if any, and applies it over the letter avatar. */
        private fun applyProviderIcon(url: String, apiName: String) {
            val ytBinding = binding as? FragmentHomeHeadYoutubeBinding ?: return
            if (currentApiName != apiName) return
            ytBinding.homeYtLogo.loadImage(url) {
                transformations(CircleCropTransformation())
                error(createLetterAvatar(apiName).asImage())
            }
        }

        /** Resolves the provider icon from the extension repositories (cached per process). */
        private fun lookupProviderIcon(apiName: String) {
            val cached = providerIconCache[apiName]
            if (cached != null) {
                if (cached.isNotEmpty()) {
                    applyProviderIcon(cached, apiName)
                }
                return
            }

            ioSafe {
                val repositories =
                    (getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()) +
                            PREBUILT_REPOSITORIES

                var foundUrl: String? = null
                outer@ for (repoData in repositories) {
                    val plugins = RepositoryManager.getRepoPlugins(repoData) ?: continue
                    for (wrapper in plugins) {
                        if (wrapper.plugin.internalName.equals(apiName, ignoreCase = true) ||
                            wrapper.plugin.internalName.replace("provider", "", ignoreCase = true)
                                .equals(apiName, ignoreCase = true)
                        ) {
                            foundUrl = wrapper.plugin.iconUrl
                            break@outer
                        }
                    }
                }

                val normalizedUrl = foundUrl
                    ?.replace("%size%", "256")
                    ?.replace("%exact_size%", "256")

                providerIconCache[apiName] = normalizedUrl ?: ""

                if (normalizedUrl != null) {
                    runOnMainThread {
                        if (currentApiName == apiName) {
                            applyProviderIcon(normalizedUrl, apiName)
                        }
                    }
                }
            }
        }

        /** Colored circle with the provider initial, used until the real icon loads. */
        private fun createLetterAvatar(name: String): BitmapDrawable {
            val density = itemView.resources.displayMetrics.density
            val size = (AVATAR_LOGO_SIZE_DP * density).toInt().coerceAtLeast(AVATAR_LOGO_SIZE_DP)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AVATAR_COLORS[name.hashCode().mod(AVATAR_COLORS.size)]
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = size * 0.5f
                isFakeBoldText = true
            }
            val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(
                name.firstOrNull()?.uppercase() ?: "?",
                size / 2f,
                baseline,
                textPaint
            )
            return BitmapDrawable(itemView.resources, bitmap)
        }

        private fun updatePreview(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
            if (preview is Resource.Success || binding is FragmentHomeHeadYoutubeBinding) {
                homeNonePadding.apply {
                    val params = layoutParams
                    params.height = 0
                    layoutParams = params
                }
            } else fixPaddingStatusbarView(homeNonePadding)

            when (preview) {
                is Resource.Success -> {
                    homePreviewShimmer?.stopShimmer()
                    homePreviewShimmer?.isGone = true
                    lastPreviewItems = preview.value.second
                    previewAdapter.submitList(HomeFeedFilter.filterPreview(lastPreviewItems))
                    previewAdapter.hasMoreItems = preview.value.first
                    /*if (!.setItems(
                            preview.value.second,
                            preview.value.first
                        )
                    ) {
                        // this might seam weird and useless, however this prevents a very weird andrid bug were the viewpager is not rendered properly
                        // I have no idea why that happens, but this is my ducktape solution
                        previewViewpager.setCurrentItem(0, false)
                        previewViewpager.beginFakeDrag()
                        previewViewpager.fakeDragBy(1f)
                        previewViewpager.endFakeDrag()
                        previewCallback.onPageSelected(0)
                        //previewHeader.isVisible = true
                    }*/

                    previewViewpager.isVisible = true
                    previewViewpagerText.isVisible = true
                    alternativeAccountPadding?.isVisible = false
                    // Explicitly bind the current item to ensure instant loading
                    val currentPos = previewViewpager.currentItem
                    val item = preview.value.second.getOrNull(currentPos)
                    if (item != null) {
                        onSelect(item, currentPos)
                    }
                }

                is Resource.Loading -> {
                    previewViewpager.isGone = true
                    previewViewpagerText.isGone = true
                    homePreviewShimmer?.isVisible = true
                    homePreviewShimmer?.startShimmer()
                }

                else -> {
                    homePreviewShimmer?.stopShimmer()
                    homePreviewShimmer?.isGone = true
                    previewAdapter.submitList(listOf())
                    previewViewpager.setCurrentItem(0, false)
                    previewViewpager.isVisible = false
                    previewViewpagerText.isVisible = false
                    alternativeAccountPadding?.isVisible = true
                    //previewHeader.isVisible = false
                }
            }
        }

        private fun updateResume(resumeWatching: List<SearchResponse>) {
            resumeHolder.isVisible = resumeWatching.isNotEmpty()
            resumeAdapter.submitList(resumeWatching)

            watchSectionTitle?.let { title ->
                title.setOnClickListener {
                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                title.text.toString(),
                                resumeWatching,
                                false
                            ), 1, false
                        ),
                        deleteCallback = {
                            viewModel.deleteResumeWatching()
                        }
                    )
                }
            }
        }

        private fun updateBookmarks(data: Pair<Boolean, List<SearchResponse>>) {
            val (visible, list) = data
            bookmarkHolder.isVisible = visible
            bookmarkAdapter.submitList(list)

            bookmarkSectionTitle?.setOnClickListener {
                val items = toggleList.map { it.first }.filter { it.isChecked }
                if (items.isEmpty()) return@setOnClickListener // we don't want to show an empty dialog
                val textSum = items
                    .mapNotNull { it.text }.joinToString()

                viewModel.popup(
                    HomeViewModel.ExpandableHomepageList(
                        HomePageList(
                            textSum,
                            list,
                            false
                        ), 1, false
                    ), deleteCallback = {
                        viewModel.deleteBookmarks(list)
                    }
                )
            }
        }

        fun onViewAttachedToWindow() {
            previewViewpager.registerOnPageChangeCallback(previewCallback)

            previewViewpager.apply {
                observe(viewModel.preview) {
                    updatePreview(it)
                }
                observe(viewModel.resumeWatching) {
                    updateResume(it)
                }
                observe(viewModel.bookmarks) {
                    updateBookmarks(it)
                }
                observe(viewModel.availableWatchStatusTypes) { (checked, visible) ->
                    for ((chip, watch) in toggleList) {
                        chip.apply {
                            isVisible = visible.contains(watch)
                            isChecked = checked.contains(watch)
                        }
                    }
                    toggleListHolder?.isGone = visible.isEmpty()
                }
            }
        }

        companion object {
            /** Matches the 26dp top bar logo size in fragment_home_head_youtube.xml. */
            private const val AVATAR_LOGO_SIZE_DP = 26

            /** Provider name -> resolved icon url. Empty string = looked up, none found. */
            private val providerIconCache = HashMap<String, String>()

            /** Material-style palette used for the letter avatar background. */
            private val AVATAR_COLORS = intArrayOf(
                0xFFE53935.toInt(), // red
                0xFFD81B60.toInt(), // pink
                0xFF8E24AA.toInt(), // purple
                0xFF5E35B1.toInt(), // deep purple
                0xFF3949AB.toInt(), // indigo
                0xFF1E88E5.toInt(), // blue
                0xFF00897B.toInt(), // teal
                0xFF43A047.toInt(), // green
                0xFFFB8C00.toInt(), // orange
                0xFFF4511E.toInt(), // deep orange
            )
        }
    }
}
