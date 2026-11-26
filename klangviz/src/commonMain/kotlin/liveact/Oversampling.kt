/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import kotlin.math.floor

/**
 * The live act's oversampling steps: the samples drawn for each pixel shown, [STEPS]. 2 and 4 draw more pixels than
 * the screen shows and scale them down (1.41 and 2 times as many each way), which smooths edges and the glitter; 0.5
 * draws half the pixels of 1 (0.71 as many each way) and scales them up, softer and faster, for a slow machine.
 *
 * 0.5 is a choice by hand only (the key o, an app's control, `?oversample=0.5`): nothing chooses it by itself, and
 * the step down by itself goes no lower than 1 ([below]). At 1 and below the picture follows the frame rate
 * ([follows]): 0.5 is half the pixels of what 1 would draw at that moment, never sharper.
 */
object Oversampling {
    /** The steps, fewest samples first. */
    val STEPS: List<Double> = listOf(0.5, 1.0, 2.0, 4.0)

    /** The plain picture: one sample for each pixel. */
    const val PLAIN = 1.0

    /** The step `?oversample=` asks for ([text]): one of [STEPS], else [PLAIN]. */
    fun parse(text: String?): Double = text?.toDoubleOrNull()?.takeIf { it in STEPS } ?: PLAIN

    /**
     * The step the address asks for: `?oversample=` where it is there ([parse]), else the quality's (`?quality=`,
     * [PicturePreset]), else [PLAIN].
     */
    fun of(switches: Switches): Double =
        if (switches.has(Switch.Oversample)) parse(switches[Switch.Oversample]) else PicturePreset.of(switches)?.samples ?: PLAIN

    /** The step after [samples], as the key o cycles through them all: after the last, the first again. */
    fun next(samples: Double): Double = STEPS[(STEPS.indexOf(samples) + 1).mod(STEPS.size)]

    /** The step below [samples] for the step down by itself: never below [PLAIN]; null where there is none. */
    fun below(samples: Double): Double? = STEPS.lastOrNull { it < samples && it >= PLAIN }

    /** Whether the picture follows the frame rate at [samples] (as the plain one does): at [PLAIN] and below. */
    fun follows(samples: Double): Boolean = samples <= PLAIN

    /** The step as a label: `0.5x`, `1x`, `2x`, `4x`. */
    fun label(samples: Double): String =
        if (samples == floor(samples)) "${samples.toInt()}x" else "${samples}x"
}
