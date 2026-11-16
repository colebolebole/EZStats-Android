package com.ezstats.android.ui.weather

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Station
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * What the Weather tab remembers while you are on the other tab: which station it shows, how far down each
 * station's page you scrolled, and whether the station list is open.
 */
@Stable
class WeatherUiState {
    var pageCount by mutableIntStateOf(0)
    val pager: PagerState = PagerState(currentPage = 0) { pageCount }
    var showPicker by mutableStateOf(false)
    internal var currentKey: String? = null
    private val scrolls = HashMap<String, ScrollState>()
    fun scrollFor(key: String) = scrolls.getOrPut(key) { ScrollState(0) }
}

@Composable
fun rememberWeatherUiState() = remember { WeatherUiState() }

/** Which of your thermostats each station is nearest to. */
internal fun nearestNames(state: StateData?): Map<String, List<String>> {
    val map = HashMap<String, MutableList<String>>()
    state?.devices?.forEach { d -> d.weather?.let { map.getOrPut(it.station) { mutableListOf() } += deviceName(d) } }
    return map
}

/**
 * The order stations are paged through: the ones near your thermostats first (like a weather app's "my
 * location"), then the rest of the forecasting stations by world and name, then the ones not forecasting.
 */
internal fun orderStations(all: List<Station>, nearest: Map<String, List<String>>): List<Station> {
    val (working, idle) = all.partition { it.working }
    val (mine, others) = working.partition { nearest[it.key].orEmpty().isNotEmpty() }
    return mine + others.sortedWith(compareBy({ it.world ?: "" }, { it.label.lowercase() })) + idle.sortedBy { it.label.lowercase() }
}

/**
 * The Weather tab, made like a weather app: one station per page under a live sky, swiped between, with a list of
 * every station a tap away.
 */
@Composable
fun WeatherTab(vm: EzViewModel, padding: PaddingValues, ui: WeatherUiState) {
    val state = vm.data
    val scope = rememberCoroutineScope()
    // The stations near your thermostats come with the dashboard, so there is something to show while the full
    // list (every station on the server) is still on its way.
    val loaded = vm.weather
    val all = loaded?.stations ?: state?.stations?.values?.toList().orEmpty()
    val nearest = remember(state?.devices) { nearestNames(state) }
    val ordered = remember(all, nearest) { orderStations(all, nearest) }
    val skies = remember(ordered) { ordered.map { skyOf(it) } }
    val pager = ui.pager
    SideEffect { ui.pageCount = ordered.size }

    // Stay on the same station when the list changes under us (a station appears, or one stops forecasting).
    val latest by rememberUpdatedState(ordered)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { ui.currentKey = latest.getOrNull(it)?.key }
    }
    LaunchedEffect(ordered) {
        val i = ordered.indexOfFirst { it.key == ui.currentKey }
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    // A thermostat asked to show its station.
    LaunchedEffect(vm.weatherStation, ordered) {
        val key = vm.weatherStation ?: return@LaunchedEffect
        val i = ordered.indexOfFirst { it.key == key }
        if (i >= 0) {
            pager.scrollToPage(i)
            ui.showPicker = false
            vm.weatherStation = null
        }
    }

    BackHandler(enabled = ui.showPicker) { ui.showPicker = false }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
    val bottom = padding.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        SkyBackdrop(skies, position = { pager.currentPage + pager.currentPageOffsetFraction }, animated = vm.settings.animatedWeather)

        if (ordered.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loaded == null) {
                    LoadingIndicator(Modifier.size(72.dp), color = OnSky)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading the weather…", color = OnSky, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("No weather stations on this server yet.", color = OnSky, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
        } else {
            HorizontalPager(
                state = pager,
                key = { ordered.getOrNull(it)?.key ?: it },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val station = ordered[page]
                Box(
                    Modifier.fillMaxSize().fadeEdges(top, bottom).graphicsLayer {
                        // Pages lean back a little as they leave, and come forward as they arrive.
                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        alpha = lerp(1f, 0.35f, off)
                        val s = lerp(1f, 0.92f, off)
                        scaleX = s
                        scaleY = s
                    },
                ) {
                    StationPage(station, nearest[station.key].orEmpty(), ui.scrollFor(station.key), top, bottom)
                }
            }
        }

        // The bar along the top: the station list, and a dot per station.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { ui.showPicker = true }, enabled = ordered.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Rounded.List, "Locations: all weather stations", tint = OnSky)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (ordered.size > 1) PageDots(ordered.size, pager)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (loaded == null && ordered.isNotEmpty()) LoadingIndicator(Modifier.size(28.dp), color = OnSky)
            }
        }

        AnimatedVisibility(
            visible = ui.showPicker,
            enter = fadeIn(Motion.fadeIn()) + scaleIn(Motion.open, initialScale = 0.92f) +
                slideInVertically(Motion.slide) { it / 10 },
            exit = fadeOut(Motion.fadeOut()) + scaleOut(Motion.close, targetScale = 0.95f) +
                slideOutVertically(Motion.slide) { it / 14 },
        ) {
            StationPicker(
                stations = ordered,
                nearest = nearest,
                current = ordered.getOrNull(pager.currentPage)?.key,
                bottom = bottom,
                loading = loaded == null,
                onPick = { key ->
                    val i = ordered.indexOfFirst { it.key == key }
                    if (i >= 0) scope.launch { pager.scrollToPage(i) }
                    ui.showPicker = false
                },
                onClose = { ui.showPicker = false },
            )
        }
    }
}

/**
 * Fades the page out where it runs under the bar at the top and the navigation bar at the bottom, so text
 * never sits behind them.
 */
private fun Modifier.fadeEdges(top: Dp, bottom: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val t = top.toPx()
        val b = bottom.toPx()
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                ((t - 12.dp.toPx()) / h).coerceIn(0f, 1f) to Color.Transparent,
                ((t + 28.dp.toPx()) / h).coerceIn(0f, 1f) to Color.Black,
                ((h - b - 36.dp.toPx()) / h).coerceIn(0f, 1f) to Color.Black,
                ((h - b + 4.dp.toPx()) / h).coerceIn(0f, 1f) to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * A dot per station; the current one stretches into a pill, and the stretch follows the swipe. With many
 * stations the dots shrink so they still fit.
 */
@Composable
private fun PageDots(count: Int, pager: PagerState) {
    val small = count > 12
    Row(horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(24)) { i ->
            val selected = pager.currentPage == i
            val w by animateDpAsState(if (selected) (if (small) 12.dp else 18.dp) else (if (small) 5.dp else 7.dp), label = "dot")
            val a by animateFloatAsState(if (selected) 1f else 0.45f, Motion.quick, label = "dot alpha")
            Box(
                Modifier
                    .height(if (small) 5.dp else 7.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = a)),
            )
        }
    }
}
