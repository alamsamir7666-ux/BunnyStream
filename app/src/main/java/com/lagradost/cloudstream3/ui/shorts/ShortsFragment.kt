package com.lagradost.cloudstream3.ui.shorts

import android.view.View
import com.lagradost.cloudstream3.databinding.FragmentShortsBinding
import com.lagradost.cloudstream3.ui.BaseFragment

/**
 * Placeholder for the YouTube-style Shorts tab.
 * The actual short-form feed arrives in a later part of the YouTube layout.
 */
class ShortsFragment : BaseFragment<FragmentShortsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentShortsBinding::inflate)
) {
    override fun fixLayout(view: View) = Unit
}
