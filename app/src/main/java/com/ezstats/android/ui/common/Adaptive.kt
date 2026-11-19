package com.ezstats.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

// How the screens adapt to a wider window: a tablet, a foldable opened up, a phone on its side, or a resized window.
// The width is the window's own (not the device's), so split-screen and freeform windows adapt too.

/** Material's window width classes: a phone upright, a small tablet, and anything bigger or on its side. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

fun widthClassOf(widthDp: Int): WidthClass = when {
    widthDp >= 840 -> WidthClass.EXPANDED
    widthDp >= 600 -> WidthClass.MEDIUM
    else -> WidthClass.COMPACT
}

@Composable
fun widthClass(): WidthClass = widthClassOf(LocalConfiguration.current.screenWidthDp)

/**
 * The space to leave at each side so content of at most [maxContentDp] sits in the middle of a [screenDp] wide screen,
 * and never less than [minDp]. On a phone this is just the usual margin.
 */
fun sidePaddingDp(screenDp: Int, maxContentDp: Int, minDp: Int = 16): Int = max(minDp, (screenDp - maxContentDp) / 2)

/** [sidePaddingDp] for the current window, as the content padding of a list or grid that scrolls the full width. */
@Composable
fun rememberSidePadding(maxContent: Dp, min: Dp = 16.dp): Dp =
    sidePaddingDp(LocalConfiguration.current.screenWidthDp, maxContent.value.toInt(), min.value.toInt()).dp

/**
 * How many columns the devices grid has when the content is [contentDp] wide, or null on a phone (which fits as many
 * 164 dp tiles as there is room for). A thermostat's tile takes two columns and a compact tile one, so the count is
 * always even: every row of wide tiles fills the width, with no hole at its end. Each wide tile gets at least 340 dp.
 */
fun deviceGridColumns(contentDp: Int): Int? {
    if (contentDp < 600) return null
    val wideTiles = ((contentDp + 12) / (340 + 12)).coerceIn(2, 4)
    return wideTiles * 2
}

/** Columns of the weather station list: one on a phone, two on a small tablet, three on a big one. */
fun stationListColumns(widthDp: Int): Int = when (widthClassOf(widthDp)) {
    WidthClass.COMPACT -> 1
    WidthClass.MEDIUM -> 2
    WidthClass.EXPANDED -> 3
}
