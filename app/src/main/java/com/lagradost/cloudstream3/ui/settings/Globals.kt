package com.lagradost.cloudstream3.ui.settings

import android.content.res.Configuration
import android.content.res.Resources

object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b00001

    /** Returns true if the current orientation is landscape. */
    fun isLandscape(): Boolean =
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Returns true if the layout is any of the flags.
     *
     * Valid flag is: PHONE
     * */
    fun isLayout(flags: Int): Boolean {
        return (PHONE and flags) != 0
    }
}
