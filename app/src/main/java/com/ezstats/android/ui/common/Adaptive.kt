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

/** How many 164 dp tiles fit across [contentDp] (with 12 dp between): what the grid on a phone has always done. */
fun phoneGridColumns(contentDp: Int): Int = max(1, (contentDp + 12) / (164 + 12))

/** What an item in the devices grid is, for working out how many columns it takes. */
enum class Cell { FULL, WIDE, SMALL }

/**
 * How many columns each of [cells] takes in a grid of [columns]: a FULL item (a heading or a strip) the whole row, a
 * WIDE tile (a thermostat) two columns, or the whole row when there are three or fewer, and a SMALL tile one. Where the
 * next item doesn't fit in what is left of a row, the item before it stretches to fill the row, so the order you
 * arranged them in never leaves a hole.
 */
fun gridSpans(cells: List<Cell>, columns: Int): IntArray {
    val cols = max(1, columns)
    val spans = IntArray(cells.size) { i ->
        when (cells[i]) {
            Cell.FULL -> cols
            Cell.WIDE -> if (cols <= 3) cols else 2
            Cell.SMALL -> 1
        }
    }
    var used = 0
    var last = -1
    for (i in cells.indices) {
        if (used > 0 && used + spans[i] > cols) {
            spans[last] += cols - used
            used = 0
        }
        used += spans[i]
        last = i
        if (used >= cols) used = 0
    }
    return spans
}

/** Columns of the weather station list: one on a phone, two on a small tablet, three on a big one. */
fun stationListColumns(widthDp: Int): Int = when (widthClassOf(widthDp)) {
    WidthClass.COMPACT -> 1
    WidthClass.MEDIUM -> 2
    WidthClass.EXPANDED -> 3
}
