/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.filters.WetDryMix
import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * Stereo phaser — two independent [PhaserCore] instances (one per channel) sharing
 * the same parameters but maintaining independent state.
 *
 * **Output**: additive — `output = dry + wet · sin²(depth·π/2)` (C4: the shared wet/dry
 * law with floor 1.0, correlated branch p = 2; the dry is a shared per-cylinder mix and is
 * never attenuated). At `depth = 0.5` you get the dry
 * plus half-amplitude wet (≈ +3 dB louder than dry; allpass cascade has unit
 * magnitude so the wet sample magnitude tracks the dry, modulated by the swept
 * notches). At `depth = 1.0` you get dry + full wet. This matches the strudel /
 * cylinder-bus convention where `phaserDepth` adds an effect on top of the source.
 *
 * The Ignitor-DSL phaser ([io.peekandpoke.klang.audio_be.ignitor.PhaserIgnitor]) uses
 * the *crossfade* convention instead, with a `blend` parameter — different surface,
 * different contract. They share [PhaserCore] for the per-sample math; only the
 * output mixing differs.
 *
 * **Stereo image**: both channels share the same LFO phase (centred-mono image).
 * For wider stereo, use a future `stereoSpread` knob to offset the right LFO
 * (out of scope for this dedup pass).
 */
class Phaser(sampleRate: Int) {
    private val coreL = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate)
    private val coreR = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate)

    /** LFO frequency in Hz. */
    var rate: Double
        get() = coreL.rate
        set(value) {
            coreL.rate = value
            coreR.rate = value
        }

    /** Wet/dry crossfade amount, clamped to `[0, 1]`. NaN/Inf silently ignored. */
    var depth: Double = 0.0
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceIn(0.0, 1.0)
        }

    /** Center breakpoint frequency in Hz. */
    var center: Double
        get() = coreL.center
        set(value) {
            coreL.center = value
            coreR.center = value
        }

    /** LFO sweep width in Hz. */
    var sweep: Double
        get() = coreL.sweep
        set(value) {
            coreL.sweep = value
            coreR.sweep = value
        }

    /** Feedback amount, clamped to `[0, PhaserCore.MAX_FEEDBACK]`. */
    var feedback: Double
        get() = coreL.feedback
        set(value) {
            coreL.feedback = value
            coreR.feedback = value
        }

    fun process(buffer: StereoBuffer, frames: Int) {
        if (depth <= 0.0) return

        val left = buffer.left
        val right = buffer.right
        // C4 (filter unification): shared wet/dry law with floor = 1.0 — the orbit phaser is
        // ADDITIVE (the dry is a shared per-cylinder mix and is never attenuated), correlated
        // branch (p = 2). The dry coefficient is pinned at 1 by the floor; the wet follows
        // sin^2 instead of the old linear depth (identical at 0, 0.5 and 1).
        val wetC = WetDryMix.wetCoeff(depth, p = 2)

        // Control-rate: compute α at block boundaries once per channel.
        coreL.prepareBlock(frames)
        coreR.prepareBlock(frames)

        for (i in 0 until frames) {
            val dryL = left[i]
            val wetL = coreL.step(dryL)
            left[i] = dryL + wetL * wetC

            val dryR = right[i]
            val wetR = coreR.step(dryR)
            right[i] = dryR + wetR * wetC
        }
    }
}
