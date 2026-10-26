package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R

object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b00001

    /** YouTube-styled phone layout variant (labeled bottom navigation + restyled screens).
     *  A YouTube layout is still a phone layout, so PHONE is always set alongside it. */
    const val YOUTUBE: Int = 0b10000

    /** Values stored in the app_layout_key preference. */
    private const val LAYOUT_PHONE = 0
    private const val LAYOUT_YOUTUBE = 1

    // Phone is the safe default until the real preference has been loaded.
    private var layoutId: Int = PHONE or YOUTUBE

    private fun Context.getLayoutInt(): Int {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getInt(
            getString(R.string.app_layout_key),
            LAYOUT_YOUTUBE
        )
    }

    /** Reloads the active layout from settings. Must be called before inflating layouts. */
    fun Context.updateLayout() {
        layoutId = when (getLayoutInt()) {
            LAYOUT_PHONE -> PHONE
            else -> PHONE or YOUTUBE
        }
    }

    /** Returns true if the current orientation is landscape. */
    fun isLandscape(): Boolean =
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Returns true if the layout is any of the flags.
     *
     * Valid flags are: PHONE, YOUTUBE
     * */
    fun isLayout(flags: Int): Boolean {
        return (layoutId and flags) != 0
    }
}
