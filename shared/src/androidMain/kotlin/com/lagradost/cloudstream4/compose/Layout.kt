package com.lagradost.cloudstream4.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.cloudstream4.compose.DeviceLayout.Companion.LocalLayout

@JvmInline
@Immutable
value class DeviceLayout(private val value: Int) {
    infix fun or(other: DeviceLayout) =
        DeviceLayout(value or other.value)

    internal infix fun and(other: DeviceLayout) = (value and other.value) != 0

    companion object {
        val LocalLayout: ProvidableCompositionLocal<DeviceLayout> = staticCompositionLocalOf { PHONE }
    }
}

val PHONE = DeviceLayout(0b00001)

@Composable
fun isLayout(flags: DeviceLayout) : Boolean = LocalLayout.current and flags
