/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.DB20_OVER_LN10
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.ENV_COEFF_BLEND_DB
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.LN10_OVER_20
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Dynamic range compressor with configurable threshold, ratio, knee, attack, and release.
 *
 * This compressor reduces the dynamic range of audio by attenuating signals above the threshold.
 * The amount of attenuation is controlled by the ratio parameter.
 *
 * **Topology**: dB-domain one-pole envelope follower (peak link via `max(|L|, |R|)`) feeding a
 * soft-knee parabolic gain curve. Standard musical-compressor design. Used in two places:
 *   1. Per-cylinder musical compressor (user-facing).
 *   2. Master brickwall limiter via `KlangAudioRenderer.kt:20-27`.
 *
 * **Round 5 review (2026-04-30)** kept the topology and applied a code-quality cleanup pass.
 * Deferred-but-known items live in the user's memory at
 * `~/.claude/projects/-opt-dev-peekandpoke-klang/memory/project_filter_review.md`:
 *   - Linear-domain rewrite (would eliminate 2 `exp`/`ln` per sample on top of the constant
 *     caching done here; deferred to the future dedicated `MasterLimiter` work).
 *   - Peak-detector RMS smoothing (secondary britzeling fix; deferred).
 *   - Lookahead support (master-limiter-only feature; deferred to dedicated `MasterLimiter`).
 *
 * @param sampleRate Sample rate in Hz
 * @param thresholdDb Threshold in dB (default: -20.0). Signals above this are compressed.
 * @param ratio Compression ratio (default: 4.0). For example, 4:1 means 4 dB input = 1 dB output above threshold.
 * @param kneeDb Soft knee width in dB (default: 6.0). A wider knee makes the compression more gradual.
 *   `kneeDb = 0` produces a hard-knee gain curve with a C¹ slope kink at the threshold corner;
 *   audible as crackle/sizzle on transient-rich material. Use ≥ 1 dB on brickwall settings.
 * @param attackSeconds Attack time in seconds (default: 0.003). How quickly compression is applied.
 * @param releaseSeconds Release time in seconds (default: 0.1). How quickly compression is released.
 */
class Compressor(
    private val sampleRate: Int,
    thresholdDb: Double = -20.0,
    ratio: Double = 4.0,
    kneeDb: Double = 6.0,
    attackSeconds: Double = 0.003,
    releaseSeconds: Double = 0.1,
    /**
     * Lookahead in seconds. `0.0` (the default) keeps the classic feed-forward one-pole path with
     * no delay line and no added latency — which is what every per-orbit compressor uses, and must,
     * since latency on one orbit would shift it against the rest of the mix.
     *
     * Above [MIN_LOOKAHEAD_FRAMES] worth of samples this switches to the anticipating path, which
     * delays the signal by this much and is therefore only appropriate where the delay is uniform
     * for everything downstream — i.e. the final master bus.
     *
     * A constructor `val`, deliberately, unlike every other parameter here: the rings are sized once
     * from it and must never be reallocated on the audio thread.
     */
    val lookaheadSeconds: Double = 0.0,
) {
    // Compressor parameters (all mutable for real-time changes; setters silently ignore non-finite values).
    var thresholdDb: Double = guardOr(thresholdDb, -20.0)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
        }

    var ratio: Double = guardOr(ratio, 4.0).coerceAtLeast(1.0)
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceAtLeast(1.0)
            updateCoefficients()
        }

    var kneeDb: Double = guardOr(kneeDb, 6.0).coerceAtLeast(0.0)
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceAtLeast(0.0)
            updateCoefficients()
        }

    /**
     * How fast the gain closes. One concept, two mechanisms: a one-pole time constant when
     * [lookaheadSeconds] is 0, the smoothing length when it is not.
     *
     * On the lookahead path this re-splits the box taps but never reallocates past the ceiling fixed
     * at construction, so writing it from a live settings change stays audio-thread safe.
     */
    var attackSeconds: Double = guardOr(attackSeconds, 0.003)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
            if (delayFrames > 0) resizeSmoothing()
        }

    var releaseSeconds: Double = guardOr(releaseSeconds, 0.1)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
        }

    /**
     * Make-up gain in decibels to compensate for volume loss after compression.
     */
    var makeupGainDb: Double = 0.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    // Envelope follower state.
    private var envelopeDb: Double = SILENCE_DB

    // Computed coefficients (refreshed on any parameter setter).
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0

    // ── Lookahead state ──────────────────────────────────────────────────────
    // Sized once, here. Nothing below is ever reallocated: `lookaheadSeconds` is a
    // constructor val precisely so the ring cannot be resized on the audio thread.

    /** Signal delay D in frames. 0 disables the whole lookahead path. */
    private val delayFrames: Int = run {
        val frames = (lookaheadSeconds * sampleRate).toInt()
        if (frames < MIN_LOOKAHEAD_FRAMES) 0 else frames
    }

    /** Signal delay rings (one per channel, one shared write index). */
    private val delayL = DoubleArray(if (delayFrames > 0) delayFrames else 0)
    private val delayR = DoubleArray(if (delayFrames > 0) delayFrames else 0)
    private var delayPos = 0

    // Monotonic-increasing deque for the running MINIMUM of the required gain over a window of
    // `delayFrames + 1`. Primitive-backed: ArrayDeque<Double> boxes on Kotlin/JS.
    private val minVals = DoubleArray(if (delayFrames > 0) delayFrames + 2 else 0)
    private val minIdx = IntArray(if (delayFrames > 0) delayFrames + 2 else 0)
    private var minHead = 0
    private var minTail = 0
    private var sampleCounter = 0

    // Two cascaded box filters: combined support b1 + b2 - 1 taps, coverage condition
    // b1 + b2 - 2 <= delayFrames.
    private var boxA = DoubleArray(0)
    private var boxB = DoubleArray(0)
    private var boxAPos = 0
    private var boxBPos = 0
    private var boxASum = 0.0
    private var boxBSum = 0.0

    /** Release-stage state: instant down, one-pole up. Runs BEFORE the smoother. */
    private var releaseGain = 1.0

    init {
        updateCoefficients()
        if (delayFrames > 0) {
            resizeSmoothing()
            resetLookaheadState()
        }
    }

    /**
     * Rebuild the two box filters from [attackSeconds], honouring `b1 + b2 - 2 <= delayFrames`.
     * Called from the setter too, so a live attack change re-splits the taps — but never
     * reallocates beyond the ceiling fixed at construction.
     */
    private fun resizeSmoothing() {
        val requested = (attackSeconds * sampleRate).toInt()
        val total = requested.coerceIn(2, delayFrames + 2)
        val half = (total / 2).coerceAtLeast(1)
        if (boxA.size != half) boxA = DoubleArray(half)
        if (boxB.size != half) boxB = DoubleArray(half)
    }

    /**
     * Process a stereo buffer in-place.
     */
    fun process(left: AudioBuffer, right: AudioBuffer, blockSize: Int) {
        val makeupLinear = computeMakeupLinear()

        // Two loop bodies, branched OUTSIDE the per-sample loop: `envelopeStep` is inlined into each
        // so the zero-lookahead path stays exactly as specialized as it was.
        if (delayFrames > 0) {
            for (i in 0 until blockSize) {
                val l = left[i]
                val r = right[i]
                val gain = lookaheadStep(max(abs(l), abs(r))) * makeupLinear
                // Emit the DELAYED sample, then park the current one. One shared write index.
                val outL = delayL[delayPos]
                val outR = delayR[delayPos]
                delayL[delayPos] = l
                delayR[delayPos] = r
                delayPos = if (delayPos + 1 == delayFrames) 0 else delayPos + 1
                left[i] = outL * gain
                right[i] = outR * gain
            }
            return
        }

        for (i in 0 until blockSize) {
            val totalGain = envelopeStep(max(abs(left[i]), abs(right[i]))) * makeupLinear
            left[i] = left[i] * totalGain
            right[i] = right[i] * totalGain
        }
    }

    /**
     * Process a mono buffer in-place.
     */
    fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        val makeupLinear = computeMakeupLinear()
        for (i in 0 until length) {
            val idx = offset + i
            val totalGain = envelopeStep(abs(buffer[idx])) * makeupLinear
            buffer[idx] = buffer[idx] * totalGain
        }
    }

    /**
     * Per-sample envelope-follower + gain-curve step. Inlined into both `process()` overloads
     * so each loop body stays specialized to its input shape (stereo pair vs mono indexed).
     * Uses precomputed [DB20_OVER_LN10] / [LN10_OVER_20] to skip per-sample `ln(10.0)` calls.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun envelopeStep(inputLevel: Double): Double {
        // Convert to dB (with silence floor to avoid log(0)).
        val inputDb = if (inputLevel > SILENCE_LIN) {
            DB20_OVER_LN10 * ln(inputLevel)
        } else {
            SILENCE_DB
        }

        // Envelope follower: one-pole IIR `y += k * (x - y)`. Instead of hard-switching the
        // coefficient on direction (a slope kink + chatter at every attack<->release crossing),
        // blend attack->release smoothly across a +/- ENV_COEFF_BLEND_DB window around error=0.
        // C1, branchless: error >= +W keeps full attackCoeff (peaks still caught at full speed →
        // limiter-safe), error <= -W is full releaseCoeff; |error| < W blends with no kink.
        val error = inputDb - envelopeDb
        val blend = smoothstep01((error + ENV_COEFF_BLEND_DB) / (2.0 * ENV_COEFF_BLEND_DB))
        val coeff = releaseCoeff + (attackCoeff - releaseCoeff) * blend
        envelopeDb += coeff * error

        // Gain curve → linear multiplier. Skip `exp` only below an inaudible reduction floor
        // (GAIN_SKIP_THRESHOLD_DB); the old -0.01 dB cutoff snapped the gain 1.0<->0.99885.
        val gainReductionDb = calculateGainReduction(envelopeDb)
        return if (gainReductionDb < GAIN_SKIP_THRESHOLD_DB) {
            exp(gainReductionDb * LN10_OVER_20)
        } else {
            1.0
        }
    }

    /**
     * Per-sample lookahead gain: **min-hold → release → two cascaded boxes**.
     *
     * The ordering is load-bearing. Taking `min(smoothed, onePoleRelease)` *after* the smoother is
     * C0 at the crossover — the box path leaves its minimum with slope 0, accelerates, then meets
     * the release path and the gain slope drops ~40x in one sample. That is the same corner
     * [ENV_COEFF_BLEND_DB] exists to remove. Running release *before* the boxes keeps the whole
     * trajectory C1, and safety is preserved because `release <= held` pointwise, so
     * `box(release) <= box(held) <= required`.
     *
     * Why the ceiling is actually met: the min-hold window is `delayFrames + 1`, so the sample
     * emerging from the delay ring lies inside the hold window of **every** tap the smoother is
     * averaging. An average is >= its minimum, so the smoothed gain is <= what that sample requires.
     * Derivation in `docs/tasks/master-limiter-lookahead.md` §2.3.
     */
    private fun lookaheadStep(inputLevel: Double): Double {
        // NaN-guard — max(NaN, x) propagates NaN, which would poison the deque ordering (every
        // comparison false) and then sit in the ring for delayFrames samples. MasterStage maps NaN
        // to Short.MIN_VALUE, i.e. a full-scale click.
        val level = if (inputLevel != inputLevel) 0.0 else inputLevel

        val inputDb = if (level > SILENCE_LIN) DB20_OVER_LN10 * ln(level) else SILENCE_DB
        val reductionDb = calculateGainReduction(inputDb)
        val required = if (reductionDb < GAIN_SKIP_THRESHOLD_DB) exp(reductionDb * LN10_OVER_20) else 1.0

        // ── Running MINIMUM over the next (delayFrames + 1) samples ──
        while (minTail != minHead) {
            val back = if (minTail == 0) minVals.size - 1 else minTail - 1
            if (minVals[back] >= required) minTail = back else break
        }
        minVals[minTail] = required
        minIdx[minTail] = sampleCounter
        minTail = if (minTail + 1 == minVals.size) 0 else minTail + 1
        while (minIdx[minHead] <= sampleCounter - (delayFrames + 1)) {
            minHead = if (minHead + 1 == minVals.size) 0 else minHead + 1
        }
        val held = minVals[minHead]
        sampleCounter++

        // ── Release stage: instant down, one-pole up ──
        val recovered = releaseGain + releaseCoeff * (1.0 - releaseGain)
        releaseGain = if (held < recovered) held else recovered

        // ── Two cascaded box filters (C1 step response) ──
        val a = boxA
        boxASum += releaseGain - a[boxAPos]
        a[boxAPos] = releaseGain
        boxAPos = if (boxAPos + 1 == a.size) 0 else boxAPos + 1
        val stage1 = boxASum / a.size

        val b = boxB
        boxBSum += stage1 - b[boxBPos]
        b[boxBPos] = stage1
        boxBPos = if (boxBPos + 1 == b.size) 0 else boxBPos + 1

        return boxBSum / b.size
    }

    /** Prime the lookahead state to "wide open, silent rings". */
    private fun resetLookaheadState() {
        delayL.fill(0.0)
        delayR.fill(0.0)
        delayPos = 0
        minHead = 0
        minTail = 0
        sampleCounter = 0
        releaseGain = 1.0
        boxA.fill(1.0)
        boxB.fill(1.0)
        boxAPos = 0
        boxBPos = 0
        boxASum = boxA.size.toDouble()
        boxBSum = boxB.size.toDouble()
        // Seed the deque with a unity entry so the first sample has a valid front.
        minVals[0] = 1.0
        minIdx[0] = 0
        minTail = 1
    }

    /** Block-rate makeup-gain linear multiplier; precomputed once per `process()` call. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun computeMakeupLinear(): Double {
        return if (abs(makeupGainDb) > 0.01) exp(makeupGainDb * LN10_OVER_20) else 1.0
    }

    /** Clamped cubic smoothstep: 0 below 0, 1 above 1, C1 at both ends. Polynomial (no `exp`). */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun smoothstep01(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3.0 - 2.0 * c)
    }

    /**
     * Calculate gain reduction in dB for a given input level in dB.
     * Implements soft-knee compression — parabolic blend on `[-halfKnee, +halfKnee]`,
     * verified C¹ continuous at both boundaries when `kneeDb > 0`.
     */
    private fun calculateGainReduction(inputDb: Double): Double {
        val overshootDb = inputDb - thresholdDb
        val halfKnee = kneeDb / 2.0

        return when {
            // Below threshold - no compression
            overshootDb < -halfKnee -> 0.0

            // In the knee - soft transition
            overshootDb < halfKnee -> {
                (1.0 / ratio - 1.0) * (overshootDb + halfKnee) * (overshootDb + halfKnee) / (2.0 * kneeDb)
            }

            // Above threshold - full compression
            else -> (1.0 / ratio - 1.0) * overshootDb
        }
    }

    /**
     * Update time constants for attack and release.
     */
    private fun updateCoefficients() {
        val attackTime = max(0.0001, attackSeconds)
        val releaseTime = max(0.0001, releaseSeconds)

        attackCoeff = 1.0 - exp(-1.0 / (attackTime * sampleRate))
        releaseCoeff = 1.0 - exp(-1.0 / (releaseTime * sampleRate))
    }

    /**
     * Reset the compressor state.
     */
    fun reset() {
        envelopeDb = SILENCE_DB
        if (delayFrames > 0) resetLookaheadState()
    }

    companion object {
        // Compile-time constants for the dB↔linear math — saves one `ln(10.0)` per sample
        // in the hot loop. Full elimination of `exp`/`ln` would require a linear-domain
        // rewrite (deferred — see file KDoc).
        private const val LN10: Double = 2.302585092994046
        private const val DB20_OVER_LN10: Double = 8.685889638065035   // 20 / ln(10)
        private const val LN10_OVER_20: Double = 0.11512925464970229   // ln(10) / 20

        /**
         * Below this many frames the smoother degenerates (two boxes need >= 1 tap each) and the
         * gain would step rather than ramp — a click. Sub-minimum lookahead takes the classic path.
         */
        const val MIN_LOOKAHEAD_FRAMES: Int = 8

        // Envelope follower silence floor.
        private const val SILENCE_DB: Double = -100.0
        private const val SILENCE_LIN: Double = 1e-10

        // Attack<->release coefficient blend half-width in dB. The follower coefficient
        // smoothstep-blends from releaseCoeff to attackCoeff across [-W, +W] around error=0,
        // removing the hard direction-switch kink. Larger = smoother but softer near-threshold
        // attack; tune by ear.
        private const val ENV_COEFF_BLEND_DB: Double = 2.0

        // Gain-curve exp-skip cutoff in dB. Below this reduction we return 1.0 to skip the
        // per-sample `exp`; small enough that exp(cutoff) ≈ 1 within ~-100 dB so the residual
        // step is inaudible (the old -0.01 dB cutoff snapped the gain by ~0.12%).
        private const val GAIN_SKIP_THRESHOLD_DB: Double = -1e-4

        /** Returns [value] when finite; otherwise [fallback]. Used to keep param setters safe against NaN/Inf. */
        private fun guardOr(value: Double, fallback: Double): Double =
            if (value.isFinite()) value else fallback

        /**
         * Parse compressor settings from a string.
         * Format: "threshold:ratio:knee:attack:release"
         * Example: "-20:4:6:0.003:0.1"
         *
         * @return CompressorSettings or null if parsing fails
         */
        fun parseSettings(input: String): CompressorSettings? {
            val parts = input.split(":").mapNotNull { it.toDoubleOrNull() }

            return when (parts.size) {
                5 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = parts[2],
                    attackSeconds = parts[3],
                    releaseSeconds = parts[4]
                )

                2 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = 6.0,
                    attackSeconds = 0.003,
                    releaseSeconds = 0.1
                )

                else -> null
            }
        }
    }

    data class CompressorSettings(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    )
}
