package com.ezstats.android.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Crisp haptics: short, sharp vibration primitives (the kind a good watch crown or camera dial has) instead of
 * the phone's generic buzz. Every haptic in the app goes through here, picked by the [HapticFeedbackType] the
 * caller asks for:
 *  - [HapticFeedbackType.SegmentFrequentTick]: a detent of the thermostat dial, light enough to fire many times a second
 *  - [HapticFeedbackType.SegmentTick] and [HapticFeedbackType.TextHandleMove]: picking an option, a step of − / +
 *  - [HapticFeedbackType.GestureThresholdActivate]: hitting the end of a range, a firmer bump
 *  - [HapticFeedbackType.ToggleOn] / [HapticFeedbackType.ToggleOff]: a switch or power button, a click
 *  - [HapticFeedbackType.LongPress] and [HapticFeedbackType.Confirm]: a heavy click
 *
 * [strength] (0 to 1) scales them all. Phones without the primitives fall back to predefined effects, and very old
 * ones to the view's own haptics.
 */
class CrispHaptics(private val view: View, context: Context, private val strength: Float) : HapticFeedback {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val primitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null && vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        )

    private enum class Kind { DETENT, TICK, EDGE, CLICK, HEAVY }

    private fun kindOf(type: HapticFeedbackType): Kind = when (type) {
        HapticFeedbackType.SegmentFrequentTick -> Kind.DETENT
        HapticFeedbackType.SegmentTick, HapticFeedbackType.TextHandleMove, HapticFeedbackType.KeyboardTap,
        HapticFeedbackType.VirtualKey, HapticFeedbackType.ContextClick -> Kind.TICK
        HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.GestureEnd, HapticFeedbackType.Reject -> Kind.EDGE
        HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff -> Kind.CLICK
        else -> Kind.HEAVY // long press, confirm
    }

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (strength <= 0f) return
        val kind = kindOf(hapticFeedbackType)
        val v = vibrator
        if (v == null || !v.hasVibrator()) return fallback(kind)
        when {
            primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val c = VibrationEffect.startComposition()
                when (kind) {
                    Kind.DETENT -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (0.9f * strength).coerceIn(0f, 1f))
                    Kind.TICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.7f * strength).coerceIn(0f, 1f))
                    Kind.EDGE -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, strength.coerceIn(0f, 1f))
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.5f * strength).coerceIn(0f, 1f), 40)
                    Kind.CLICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (0.8f * strength).coerceIn(0f, 1f))
                    Kind.HEAVY -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, strength.coerceIn(0f, 1f))
                }
                v.vibrate(c.compose())
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> v.vibrate(
                VibrationEffect.createPredefined(
                    when (kind) {
                        Kind.DETENT, Kind.TICK -> VibrationEffect.EFFECT_TICK
                        Kind.EDGE, Kind.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                        Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                    },
                ),
            )
            else -> fallback(kind)
        }
    }

    private fun fallback(kind: Kind) {
        view.performHapticFeedback(
            when (kind) {
                Kind.DETENT, Kind.TICK -> HapticFeedbackConstants.CLOCK_TICK
                Kind.CLICK -> HapticFeedbackConstants.VIRTUAL_KEY
                Kind.EDGE, Kind.HEAVY -> HapticFeedbackConstants.LONG_PRESS
            },
        )
    }

    companion object {
        /** The strength for each choice in Settings. */
        fun strengthOf(level: String) = when (level) {
            "light" -> 0.45f
            "strong" -> 1f
            else -> 0.75f
        }
    }
}

/** A feedback that does nothing: haptics switched off in Settings. */
object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
