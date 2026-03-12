package io.peekandpoke.klang.feel

import kotlinx.css.Color

/**
 * Thin alias kept for backward compatibility — delegates to [KlangColors].
 */
object KlangStudioColors {
    val excellent: Color get() = KlangColors.excellent
    val good: Color get() = KlangColors.good
    val moderate: Color get() = KlangColors.moderate
    val warning: Color get() = KlangColors.warning
    val critical: Color get() = KlangColors.critical

    val colors: List<Color> get() = KlangColors.statusColors

    fun rangedMixer(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = colors,
    )

    fun rangedMixerReversed(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = colors.asReversed(),
    )
}
