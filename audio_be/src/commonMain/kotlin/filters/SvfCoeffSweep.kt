/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.EnvelopeCore
import kotlin.math.pow

/**
 * One block of a swept TPT SVF: the coefficients at the block's first frame ([start]) and the per-sample
 * steps that carry each of them linearly to the coefficients at the block's end. THE interpolation of the
 * engine's modulated SVFs (decision D3, the sampling): the Ignitor filter node's cutoff envelope
 * (`SvfIgnitor`). Its other host, the fixed `BaseSvf`, snapped its coefficients through it at construction and
 * went with its last caller in engine tidy-up step 12 (a) (the voice strip's filter modulator that swept it per
 * block retired in phase 3 step 9). A host copies [start] into its running coefficients, uses them for the block's
 * first sample and adds each step AFTER every sample, so the coefficients arrive at the end values when
 * the next block begins.
 *
 * Both ends share one [q], so `k` is the same at both ends and [kStep] is exactly 0: the
 * interpolation moves the cutoff only. Equal cutoffs at both ends (a sustain, a static or drift-only
 * filter) compute one coefficient set and zero steps, which is what computing both would give
 * (`x - x == 0.0`), without the second `tan`.
 *
 * **The block's cutoffs and q are fields the host writes, not arguments of [prepare]** (engine follow-up 29,
 * 2026-10-11; `audio/ref/performance.md`, "a non-integral double handed to a function that is not inlined").
 * [prepare] is past V8's bytecode limit for inlining (460 bytes: two coefficient computations), so V8 never inlines
 * it, and a double argument crosses the call as a heap number: three per block on the filter envelope's path
 * (both ends and a q that is not a small integer). The host owns this sweep and fills it, the holder shape of the
 * delay's `DelayConfig`. No test can pin it (the sound is the same either way): this KDoc is the guard.
 *
 * Allocates nothing after construction.
 */
internal class SvfCoeffSweep {
    /** The coefficients at the block's first frame. Written by [prepare]. */
    val start = SvfCoeffs()
    private val end = SvfCoeffs()

    /** The cutoff at the block's first frame, in Hz. Written by the host before [prepare]. */
    var cutoffStartHz: Double = 0.0

    /** The cutoff the block's steps arrive at, in Hz. Written by the host before [prepare]. */
    var cutoffEndHz: Double = 0.0

    /** The q of both ends. Written by the host before [prepare]. */
    var q: Double = 0.0

    var a1Step: Double = 0.0
        private set
    var a2Step: Double = 0.0
        private set
    var a3Step: Double = 0.0
        private set
    var kStep: Double = 0.0
        private set
    var gStep: Double = 0.0
        private set

    /**
     * Resolves one block from [cutoffStartHz], [cutoffEndHz] and [q], which the host has written: [start] at
     * [cutoffStartHz], the steps toward [cutoffEndHz] over [frames] samples. [frames] at or below 0 steps
     * nothing. [sampleRate] is a whole number of Hz, a small integer on V8, so it crosses the call unboxed.
     */
    fun prepare(sampleRate: Double, frames: Int) {
        val cutoffStartHz = cutoffStartHz
        val cutoffEndHz = cutoffEndHz
        val q = q

        computeSvfCoeffs(cutoffHz = cutoffStartHz, q = q, sampleRate = sampleRate, out = start)

        if (frames <= 0 || cutoffEndHz == cutoffStartHz) {
            a1Step = 0.0
            a2Step = 0.0
            a3Step = 0.0
            kStep = 0.0
            gStep = 0.0

            return
        }

        computeSvfCoeffs(cutoffHz = cutoffEndHz, q = q, sampleRate = sampleRate, out = end)

        val invFrames = 1.0 / frames

        a1Step = (end.a1 - start.a1) * invFrames
        a2Step = (end.a2 - start.a2) * invFrames
        a3Step = (end.a3 - start.a3) * invFrames
        kStep = (end.k - start.k) * invFrames
        gStep = (end.g - start.g) * invFrames
    }
}

/**
 * The cutoff a filter envelope asks for at the voice-relative frame [pos]:
 * `baseCutoff * 2^(depthSemitones / 12 * level)`, with the envelope's level clamped to [0, 1] (depth is
 * SEMITONES, C3 of the filter unification). This [EnvelopeCore] must be prepared for the block. Both
 * filter hosts read it at the block's two ends; each then multiplies its own drift and tolerance.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun EnvelopeCore.filterEnvCutoff(pos: Int, baseCutoff: Double, depthSemitones: Double): Double =
    baseCutoff * 2.0.pow(depthSemitones / 12.0 * at(pos).coerceIn(0.0, 1.0))
