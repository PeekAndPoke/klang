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
 */
internal class ReleaseLaw(sampleRate: Int) {
    private val tau = TailRelease.RT60_SECONDS * sampleRate / ln(1000.0)
    private val floor = 10.0.pow(TailRelease.FLOOR_DB / 20.0)

    /** The first release frame whose gain is under the floor. */
    val floorFrame: Int = run {
        var k = 0
        while (exp(-k / tau) >= floor) {
            k++
        }
        k
    }

    /** The weight [k] frames into the release. */
    fun gain(k: Int): Double {
        if (k < 0) {
            return 1.0
        }

        val g = exp(-k / tau)

        return if (g < floor) 0.0 else g
    }
}
