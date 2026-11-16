package com.ezstats.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalView
import com.ezstats.android.ui.common.CrispHaptics
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.ezstats.android.BuildConfig
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.AppSettings
import com.ezstats.android.ui.theme.Motion
import kotlin.coroutines.cancellation.CancellationException

private const val REPO = "https://github.com/colebolebole/EZStats-Android"

/** The app's own settings: how it looks and behaves on this phone, the account, and what version it is. */
@Composable
fun SettingsScreen(vm: EzViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val gesture = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { gesture.snapTo(it.progress) }
            vm.showSettings = false
        } catch (e: CancellationException) {
            gesture.animateTo(0f, Motion.settle)
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val set: ((AppSettings) -> AppSettings) -> Unit = { vm.updateSettings(it) }

    Scaffold(
        modifier = Modifier
            .graphicsLayer {
                val g = gesture.value
                scaleX = 1f - 0.08f * g
                scaleY = 1f - 0.08f * g
                translationX = g * 40.dp.toPx()
                shape = RoundedCornerShape((32 * g).dp)
                clip = g > 0f
            }
            .nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Group("Appearance") {
                    ChoiceRow(Icons.Rounded.Contrast, "Theme", AppSettings.THEMES, s.theme, { cap(it) }) { v -> set { it.copy(theme = v) } }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(Icons.Rounded.Palette, "Wallpaper colours", "Match the colours of your wallpaper", s.dynamicColor) { v ->
                            set { it.copy(dynamicColor = v) }
                        }
                    }
                    SwitchRow(Icons.Rounded.Animation, "Animated weather", "Rain, snow, sunshine and lightning behind the forecast", s.animatedWeather) { v ->
                        set { it.copy(animatedWeather = v) }
                    }
                }
            }
            item {
                Group("Units and updates") {
                    ChoiceRow(
                        Icons.Rounded.Thermostat, "Temperature", AppSettings.UNITS, s.units,
                        { when (it) { "f" -> "°F"; "c" -> "°C"; else -> "Server" } },
                    ) { v -> set { it.copy(units = v) } }
                    ChoiceRow(
                        Icons.Rounded.Sync, "Refresh every", AppSettings.REFRESH.map { it.toString() }, s.refreshSeconds.toString(),
                        { "${it}s" },
                    ) { v -> set { it.copy(refreshSeconds = v.toInt()) } }
                    SwitchRow(Icons.Rounded.Vibration, "Haptic feedback", "Crisp ticks as you turn the dial and flip switches", s.haptics) { v ->
                        set { it.copy(haptics = v) }
                    }
                    AnimatedVisibility(
                        visible = s.haptics,
                        enter = fadeIn() + expandVertically(Motion.size),
                        exit = fadeOut() + shrinkVertically(Motion.size),
                    ) {
                        val view = LocalView.current
                        ChoiceRow(Icons.Rounded.GraphicEq, "Strength", AppSettings.HAPTIC_STRENGTHS, s.hapticStrength, { cap(it) }) { v ->
                            set { it.copy(hapticStrength = v) }
                            // A sample at the new strength, so you can feel the difference.
                            CrispHaptics(view, context, CrispHaptics.strengthOf(v)).performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }
                    }
                }
            }
            item {
                Group("Account") {
                    LinkRow(Icons.Rounded.Dns, "Servers", vm.active?.let { "${it.name} · ${it.url}" } ?: "None") { vm.showServers = true }
                    if (vm.data != null) {
                        LinkRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", vm.data?.player?.let { "Signed in as $it" }) {
                            vm.showSettings = false
                            vm.signOut()
                        }
                    }
                }
            }
            item {
                Group("About") {
                    LinkRow(Icons.Rounded.Info, "EZStats for Android", "Version ${BuildConfig.VERSION_NAME}") {}
                    LinkRow(Icons.AutoMirrored.Rounded.OpenInNew, "Source code", "github.com/colebolebole/EZStats-Android") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO)))
                    }
                }
            }
        }
    }
}

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** A titled group of rows on one rounded surface. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val toggle = { v: Boolean ->
        haptic.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onChange(v)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 6.dp).clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, role = Role.Switch, onValueChange = toggle),
    )
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1) } },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 6.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    )
}

/** A setting with a few choices, as a connected button group under its name. */
@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    options: List<String>,
    current: String,
    label: (String) -> String,
    onPick: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { i, o ->
                ToggleButton(
                    checked = o == current,
                    onCheckedChange = {
                        if (o != current) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onPick(o)
                        }
                    },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(label(o), maxLines = 1) }
            }
        }
    }
}
