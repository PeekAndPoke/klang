/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The geometry of a dial (KLANG-00338): the gauges' scale and the knobs' ring, in a 100 by 100 SVG view box.
 *
 * A dial sweeps 270 degrees clockwise, from bottom left (a fraction of 0) over the top (0.5) to bottom right (1), the
 * sweep the Motor's gauges and the volume knob have always had.
 */
internal object Dial {

    /** Where the sweep starts, in degrees clockwise from the positive x axis (SVG's y points down). */
    const val START_DEGREES = 135.0

    /** How far the sweep goes. */
    const val SWEEP_DEGREES = 270.0

    /** [fraction] coerced into 0..1; a NaN counts as 0. */
    fun clamp(fraction: Double): Double {
        if (fraction != fraction) { // NaN-guard
            return 0.0
        }

        return fraction.coerceIn(0.0, 1.0)
    }

    /** The point at [fraction] of the sweep, [radius] from the centre (50, 50). */
    fun point(fraction: Double, radius: Double): Pair<Double, Double> {
        val radians = (START_DEGREES + clamp(fraction) * SWEEP_DEGREES) * PI / 180.0

        return (50.0 + radius * cos(radians)) to (50.0 + radius * sin(radians))
    }

    /**
     * The arc from the sweep's start to [fraction], [radius] from the centre, as SVG path data; empty for a fraction
     * of 0, where there is nothing to draw.
     */
    fun arc(fraction: Double, radius: Double): String {
        val f = clamp(fraction)

        if (f == 0.0) {
            return ""
        }

        val (x0, y0) = point(fraction = 0.0, radius = radius)
        val (x1, y1) = point(fraction = f, radius = radius)
        val largeArc = if (f * SWEEP_DEGREES > 180.0) 1 else 0

        return "M ${x0.fmt()} ${y0.fmt()} A $radius $radius 0 $largeArc 1 ${x1.fmt()} ${y1.fmt()}"
    }

    /** The turn of a knob's cap at [fraction], in degrees clockwise from straight up: -135 to 135. */
    fun capDegrees(fraction: Double): Double = START_DEGREES - 270.0 + clamp(fraction) * SWEEP_DEGREES

    private fun Double.fmt(): String = asDynamic().toFixed(2) as String
}
