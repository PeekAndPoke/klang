package io.peekandpoke.klang.feel

import kotlinx.css.Color

/**
 * Standard gauge color scheme used across all gauges
 */
object KlangStudioColors {
    val excellent = Color.lightSkyBlue
    val good = Color.yellowGreen
    val moderate = Color.gold
    val warning = Color.darkGoldenrod
    val critical = Color.crimson

    val colors = listOf(excellent, good, moderate, warning, critical)

    fun rangedMixer(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = colors
    )

    fun rangedMixerReversed(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = colors.asReversed(),
    )
}
