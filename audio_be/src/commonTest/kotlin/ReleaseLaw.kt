/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * The decided release law, written here from the [TailRelease] constants and nothing else, for the
 * oracles of the capped drain (`CapLaw`) and of a stopped engine's release (`EngineStopReleaseSpec`):
 * the weight `k` frames into the release is `exp(-k / tau)`, 60 dB per [TailRelease.RT60_SECONDS],
 * exactly 0 from the first frame under [TailRelease.FLOOR_DB]; 1 before the release (k < 0).
 *
 * Computed as the engine computes it, the recurrence `g(k + 1) = g(k) * exp(-1 / tau)` from exactly 1
 * (KLANG-00290 (engine follow ups) item 25), so the oracles compare bit for bit; [closedForm] is the
 * law itself, and `TailReleaseLawSpec` pins the recurrence to it.
 */
internal class ReleaseLaw(sampleRate: Int) {
    private val tau = TailRelease.RT60_SECONDS * sampleRate / ln(1000.0)
    private val floor = 10.0.pow(TailRelease.FLOOR_DB / 20.0)

    /** The recurrence's gains from frame 0 to the first under the floor (exclusive). */
    private val gains: DoubleArray = run {
        val ratio = exp(-1.0 / tau)
        val out = ArrayList<Double>()
        var g = 1.0

        while (g >= floor) {
            out.add(g)
            g *= ratio
        }

        out.toDoubleArray()
    }

    /** The first release frame whose gain is under the floor. */
    val floorFrame: Int = gains.size

    /** The weight [k] frames into the release. */
    fun gain(k: Int): Double {
        if (k < 0) {
            return 1.0
        }

        return if (k < gains.size) gains[k] else 0.0
    }

    /** The law in closed form, `exp(-k / tau)`, with no floor: what the recurrence stands for. */
    fun closedForm(k: Int): Double = exp(-k / tau)

    /** The first frame whose closed-form gain is under the floor. */
    val closedFormFloorFrame: Int = run {
        var k = 0
        while (exp(-k / tau) >= floor) {
            k++
        }
        k
    }
}
