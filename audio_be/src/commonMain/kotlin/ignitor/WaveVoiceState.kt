/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.waveTrapezoid

/**
 * One oscillator voice's state for the unified [waveTrapezoid] — the single piecewise-linear shape
 * behind saw / ramp / square / pulse / triangle and their raw (aliased) variants.
 *
 * Holds the phase, base increment, gain, analog drift, and the precomputed 4-segment shape (rise →
 * high plateau → fall → low plateau). [setSawShape] / [setPulseShape] bake the shape from a config;
 * [sampleAt] / the hot loop evaluate it via the shared multiply-only [waveTrapezoid].
 *
 * A `final` class with non-nullable `Double` fields: no boxing on Kotlin/JS, monomorphic calls.
 */
internal class WaveVoiceState {
    /** Normalised phase, `0..1`. */
    var phase: Double = 0.0

    /** Base normalised phase increment per sample (pre-phaseMod, pre-drift). */
    var dt: Double = 0.0

    /** Output gain (used by the unison super stack; mono ignitors leave it unused). */
    var gain: Double = 0.0

    /** Per-voice analog drift, or `null`/inactive for none. */
    var drift: AnalogDrift? = null

    // Precomputed 4-segment shape — readable so the hot loop can hoist; only the setters mutate.
    var riseEnd: Double = 0.5; private set    // end of the rise ramp
    var highEnd: Double = 0.5; private set    // end of the high plateau
    var fallEnd: Double = 1.0; private set    // end of the fall ramp (low plateau runs to 1)
    var riseSlope: Double = 2.0; private set  // 2 / rise-ramp length
    var fallSlope: Double = 0.0; private set  // 2 / fall-ramp length

    /**
     * Saw / ramp config: a linear rise over `[0, 1−rf]` then a finite flyback over `[1−rf, 1]` (no
     * plateaus). [rf] = flyback fraction (`0` = instant / raw). Reproduces the former analog saw exactly.
     */
    fun setSawShape(rf: Double) {
        val re = 1.0 - rf
        riseEnd = re
        highEnd = re                     // empty high plateau
        fallEnd = 1.0                    // fall fills the rest; empty low plateau
        riseSlope = if (re > 0.0) 2.0 / re else 0.0
        fallSlope = if (rf > 0.0) 2.0 / rf else 0.0
    }

    /**
     * Pulse / square / triangle config: a min-flank rise into a high plateau (high portion = [duty]),
     * then a min-flank fall into a low plateau. [floor] = minimum flank length in phase (`0` = raw /
     * instant edges). [riseFlank]/[fallFlank] (`0..1`) open each edge from the floor up to a full ramp;
     * both `1` at duty 0.5 ⇒ a triangle.
     */
    fun setPulseShape(duty: Double, riseFlank: Double, fallFlank: Double, floor: Double) {
        val d = duty.coerceIn(0.01, 0.99)
        val r = (riseFlank * d).coerceAtLeast(floor).coerceAtMost(d)                  // rise ramp length
        val f = (fallFlank * (1.0 - d)).coerceAtLeast(floor).coerceAtMost(1.0 - d)    // fall ramp length
        riseEnd = r
        highEnd = d
        fallEnd = d + f
        riseSlope = if (r > 0.0) 2.0 / r else 0.0
        fallSlope = if (f > 0.0) 2.0 / f else 0.0
    }

    /** Bipolar value at normalised phase [p] (the shared [waveTrapezoid]). */
    fun sampleAt(p: Double): Double = waveTrapezoid(p, riseEnd, highEnd, fallEnd, riseSlope, fallSlope)
}
