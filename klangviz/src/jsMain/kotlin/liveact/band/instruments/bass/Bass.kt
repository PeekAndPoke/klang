/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.bass

import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Ornaments
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.QuakeTrace
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Tuning

/**
 * The basses, by model: each its own shape (the body's outline, the head, the pickups, four strings and their
 * tuners) and its gold, all with the bass's longer scale and tuning, its right hand plucking (more from the arm,
 * narrower), and hanging lower. Played by a string player (see `StringPlayer`); the cast says who plays which (the
 * maintainer, 2026-11-21: "for now letters are fine: Bass A, Bass B").
 */
object Bass {
    /** Bass A's body, Beben's: long, heavy and rounded like a river stone, its two horns short and soft. */
    val RIVER_STONE: BodyOutline = BodyOutline(
        listOf(
            0.15 to -0.05, 0.175 to -0.08, 0.19 to -0.11, 0.17 to -0.138, 0.12 to -0.152, 0.06 to -0.153, 0.0 to -0.148,
            -0.08 to -0.16, -0.17 to -0.172, -0.25 to -0.148, -0.29 to -0.085, -0.302 to 0.0, -0.29 to 0.08,
            -0.245 to 0.138, -0.165 to 0.163, -0.075 to 0.15, 0.0 to 0.134, 0.085 to 0.138, 0.175 to 0.14,
            0.245 to 0.13, 0.27 to 0.105, 0.245 to 0.08, 0.20 to 0.066, 0.17 to 0.056, 0.15 to 0.05,
        ),
    )

    /** Long horns: Bass B's body. */
    val LONG_HORN: BodyOutline = BodyOutline(
        listOf(
            0.15 to -0.05, 0.18 to -0.09, 0.22 to -0.12, 0.16 to -0.15, 0.05 to -0.135, -0.03 to -0.115, -0.16 to -0.18,
            -0.255 to -0.11, -0.275 to 0.0, -0.255 to 0.11, -0.15 to 0.172, -0.02 to 0.125, 0.08 to 0.14, 0.20 to 0.137,
            0.295 to 0.10, 0.225 to 0.075, 0.15 to 0.05,
        ),
    )

    /** The basses' head. */
    val HEAD: BodyOutline = BodyOutline(
        listOf(0.0 to -0.025, 0.04 to -0.04, 0.19 to -0.048, 0.23 to -0.015, 0.20 to 0.034, 0.05 to 0.036, 0.0 to 0.025),
    )

    /**
     * Bass A, Beben's (KLANG-00478; the maintainer, 2026-11-21: "I also like the bass idea A ... i would change the
     * lines on the instrument to represent the 'quake' that Beben stands for"): the river stone, with a quake's trace
     * in gold across its lower half (a straight line that breaks into a few big peaks below where he plucks and
     * lies straight again), gold dots on the fretboard and gold hardware.
     */
    val A: StringDesign = bass(
        name = "Bass A",
        outline = RIVER_STONE,
        knobs = listOf(-0.20 to -0.05, -0.235 to -0.025),
        ornaments = Ornaments(
            markers = true,
            goldHardware = true,
            quake = QuakeTrace(
                from = -0.27, to = 0.17, y = -0.098, bow = 0.015, at = -0.03, rise = 0.035, fall = 0.06,
                height = 0.034, period = 0.04, calm = 0.0,
            ),
        ),
    )

    /** Bass B: long horns, no gold (the band's bass before the models had names). */
    val B: StringDesign = bass(name = "Bass B", outline = LONG_HORN)

    /** A bass of the model [name]: what the string player reads of it. */
    private fun bass(
        name: String,
        outline: BodyOutline,
        knobs: List<Pair<Double, Double>> = listOf(-0.15 to -0.085, -0.19 to -0.05),
        ornaments: Ornaments = Ornaments.NONE,
    ): StringDesign = StringDesign(
        name = name, scale = 0.84, bodyHalfLength = 0.25, bodyHalfWidth = 0.17, strings = 4, neck = 0.05, tilt = 0.62,
        swing = 0.040, tau = 0.02, hang = -0.13, arm = 0.35, outline = outline,
        pickups = listOf(0.0, 0.07),
        knobs = knobs,
        head = HEAD,
        tuning = Tuning.BASS,
        ornaments = ornaments,
    )
}
