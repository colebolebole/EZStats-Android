package com.ezstats.android.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ezstats.android.ui.common.Pull
import com.ezstats.android.ui.common.isShortScreen
import com.ezstats.android.ui.common.springyPull
import com.ezstats.android.ui.theme.Motion
import kotlinx.coroutines.launch

/** One tab of [FloatingTabBar]. */
class TabItem<T>(val value: T, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The app's tab bar: a floating pill above the bottom edge. The selected tab shows its icon and name in a pill of
 * its own, which slides to the tab you pick on a spring, while the tabs make room for it. On the weather's sky
 * ([onSky]) it turns to frosted glass.
 */
@Composable
fun <T> FloatingTabBar(
    items: List<TabItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSky: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val short = isShortScreen()
    val haptic = LocalHapticFeedback.current
    val container by animateColorAsState(if (onSky) Color.White.copy(alpha = 0.16f) else scheme.surfaceContainerHigh, Motion.color, label = "bar")
    val border by animateColorAsState(if (onSky) Color.White.copy(alpha = 0.22f) else scheme.outlineVariant.copy(alpha = 0.4f), Motion.color, label = "bar border")
    val indicator by animateColorAsState(if (onSky) Color.White else scheme.primary, Motion.color, label = "indicator")
    val onIndicator by animateColorAsState(if (onSky) Color(0xFF1B2A3A) else scheme.onPrimary, Motion.color, label = "on indicator")
    val idle by animateColorAsState(if (onSky) Color.White.copy(alpha = 0.85f) else scheme.onSurfaceVariant, Motion.color, label = "idle")

    // Where each tab is inside the bar, so the pill can slide to the selected one.
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val index = items.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val target = bounds[index]
    LaunchedEffect(index, target) {
        val (x, w) = target ?: return@LaunchedEffect
        if (width.value == 0f) {
            left.snapTo(x)
            width.snapTo(w)
        } else {
            val spec = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
            scope.launch { left.animateTo(x, spec) }
            width.animateTo(w, spec)
        }
    }

    // Off the sky, the page fades out behind the bar so the cards scrolling under it don't crowd it.
    val page = scheme.background
    val fade by animateColorAsState(if (onSky) Color.Transparent else page, Motion.color, label = "bar fade")
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.Transparent, fade.copy(alpha = fade.alpha * 0.92f), fade))) }
            .navigationBarsPadding()
            .padding(top = if (short) 12.dp else 20.dp, bottom = if (short) 6.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = container,
            border = BorderStroke(1.dp, border),
            shadowElevation = if (onSky) 0.dp else 6.dp,
            modifier = Modifier.springyPull(Pull.BOTH, limit = 10.dp),
        ) {
            Row(
                Modifier
                    .height(if (short) 54.dp else 64.dp)
                    .padding(6.dp)
                    .selectableGroup()
                    .drawBehind {
                        if (width.value > 0f) {
                            drawRoundRect(
                                indicator,
                                topLeft = Offset(left.value, 0f),
                                size = Size(width.value, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { i, item ->
                    val isSelected = i == index
                    val content by animateColorAsState(if (isSelected) onIndicator else idle, Motion.color, label = "tab content")
                    Row(
                        Modifier
                            .height(if (short) 42.dp else 52.dp)
                            .clip(CircleShape)
                            .onPlaced { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(color = content),
                                onClick = {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelect(item.value)
                                    }
                                },
                            )
                            .padding(horizontal = if (isSelected) 20.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (isSelected) item.selectedIcon else item.icon, item.label, Modifier.size(24.dp), tint = content)
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(Motion.fadeIn(60)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)),
                            exit = fadeOut(Motion.fadeOut()) + shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 520f)),
                        ) {
                            Row {
                                Spacer(Modifier.width(10.dp))
                                Text(item.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                            }
                        }
                    }
                    if (i < items.lastIndex) Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
