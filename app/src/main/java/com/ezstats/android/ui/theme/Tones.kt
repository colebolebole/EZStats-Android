package com.ezstats.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Heating is always orange and cooling always blue, whatever the wallpaper: they mean something. */
val HeatColor = Color(0xFFF2711C)
val CoolColor = Color(0xFF3B82F6)
val SunColor = Color(0xFFF5B800)
val StormColor = Color(0xFF7C3AED)
val SnowColor = Color(0xFF7DD3FC)

enum class Tone { NEUTRAL, HEAT, COOL, GOOD, WARN, BAD }

@Immutable
data class ToneColors(val container: Color, val content: Color, val accent: Color)

/** Container, content and accent colours for a status. */
@Composable
fun toneColors(tone: Tone): ToneColors {
    val dark = LocalIsDark.current
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        Tone.HEAT -> if (dark) ToneColors(Color(0xFF5C2A10), Color(0xFFFFDBC9), Color(0xFFFF9A5C))
        else ToneColors(Color(0xFFFFDBC9), Color(0xFF7A2E00), HeatColor)
        Tone.COOL -> if (dark) ToneColors(Color(0xFF0F3B6B), Color(0xFFD3E4FF), Color(0xFF7FB2FF))
        else ToneColors(Color(0xFFD3E4FF), Color(0xFF00315C), CoolColor)
        Tone.GOOD -> if (dark) ToneColors(Color(0xFF1C4B2A), Color(0xFFC8F0D0), Color(0xFF6FD98A))
        else ToneColors(Color(0xFFC8F0D0), Color(0xFF00391A), Color(0xFF1F9D4C))
        Tone.WARN -> if (dark) ToneColors(Color(0xFF574500), Color(0xFFFFE8A3), Color(0xFFFFCF40))
        else ToneColors(Color(0xFFFFE8A3), Color(0xFF4A3800), Color(0xFFB98900))
        Tone.BAD -> ToneColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        Tone.NEUTRAL -> ToneColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.outline)
    }
}
