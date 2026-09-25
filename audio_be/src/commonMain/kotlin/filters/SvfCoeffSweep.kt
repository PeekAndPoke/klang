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
 * engine's modulated SVFs (decision D3, the sampling), shared by both hosts: the Ignitor filter node's
 * cutoff envelope (`SvfIgnitor`) and the voice strip's filter (`BaseSvf.sweepCutoff`, driven by
 * `FilterModRenderer`). A host copies [start] into its running coefficients, uses them for the block's
 * first sample and adds each step AFTER every sample, so the coefficients arrive at the end values when
 * the next block begins.
 *
 * Both ends share one [prepare] q, so `k` is the same at both ends and [kStep] is exactly 0: the
 * interpolation moves the cutoff only. Equal cutoffs at both ends (a sustain, a static or drift-only
 * filter) compute one coefficient set and zero steps, which is what computing both would give
 * (`x - x == 0.0`), without the second `tan`.
 *
 * Allocates nothing after construction.
 */
internal class SvfCoeffSweep {
    /** The coefficients at the block's first frame. Written by [prepare]. */
    val start = SvfCoeffs()
    private val end = SvfCoeffs()

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
     * Resolves one block: [start] at [cutoffStartHz], the steps toward [cutoffEndHz] over [frames]
     * samples. [frames] at or below 0 steps nothing.
     */
    fun prepare(cutoffStartHz: Double, cutoffEndHz: Double, q: Double, sampleRate: Double, frames: Int) {
        computeSvfCoeffs(cutoffStartHz, q, sampleRate, start)

        if (frames <= 0 || cutoffEndHz == cutoffStartHz) {
            a1Step = 0.0
            a2Step = 0.0
            a3Step = 0.0
            kStep = 0.0
            gStep = 0.0

            return
        }

        computeSvfCoeffs(cutoffEndHz, q, sampleRate, end)

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
