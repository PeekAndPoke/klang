/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.DB20_OVER_LN10
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.ENV_COEFF_BLEND_DB
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.FAST_RELEASE_DIVISOR
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
 * **Two topologies, selected by [lookaheadSeconds]:**
 *   - `0` (default) — dB-domain one-pole envelope follower (peak link via `max(|L|, |R|)`) feeding a
 *     soft-knee parabolic gain curve. Standard musical-compressor design; no added latency.
 *   - `> 0` — anticipating: running MINIMUM of the required gain over the lookahead window, a
 *     release stage, then two cascaded box filters, applied to the signal delayed by that window.
 *     Holds the ceiling on transients instead of chasing them. Costs exactly that much latency, so
 *     it belongs only where the delay is uniform downstream (the summed master mix).
 *
 * Used in two places:
 *   1. Per-cylinder musical compressor (user-facing).
 *   2. Master brickwall limiter via `KlangAudioRenderer.kt:20-27`.
 *
 * **Round 5 review (2026-04-30)** kept the topology and applied a code-quality cleanup pass.
 * Deferred-but-known items live in the user's memory at
 * `~/.claude/projects/-opt-dev-peekandpoke-klang/memory/project_filter_review.md`:
 *   - Linear-domain rewrite (would eliminate 2 `exp`/`ln` per sample on top of the constant
 *     caching done here; deferred to the future dedicated `MasterLimiter` work).
 *   - Peak-detector RMS smoothing (secondary britzeling fix; deferred).
 *   - ~~Lookahead support~~ — DONE 2026-08-06, see [lookaheadSeconds] and `lookaheadStep`.
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
     * On the lookahead path this re-splits the box taps. It allocates nothing — the rings are sized
     * once at construction — but it DOES re-prime them to the *current* gain (not to unity; see
     * [primeBoxes]), so a live write causes a small gain step bounded by the smoother's own lag.
     * Fine at a settings change, not something to call per block.
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
    private var fastReleaseCoeff: Double = 0.0

    // ── Lookahead state ──────────────────────────────────────────────────────
    // Sized once, here. Nothing below is ever reallocated: `lookaheadSeconds` is a
    // constructor val precisely so the ring cannot be resized on the audio thread.

    /** Signal delay D in frames. 0 disables the whole lookahead path. */
    private val delayFrames: Int = run {
        // guardOr like every sibling param: Infinity would saturate .toInt() to Int.MAX_VALUE and
        // ask for a 2-billion-element array. MasterChain already guards, but this class is public
        // with three other call sites.
        val seconds = guardOr(lookaheadSeconds, 0.0)
        val frames = (seconds * sampleRate).toInt()
        if (frames < MIN_LOOKAHEAD_FRAMES) 0 else frames
    }

    /** Signal delay rings (one per channel, one shared write index). */
    private val delayL = DoubleArray(if (delayFrames > 0) delayFrames else 0)
    private val delayR = DoubleArray(if (delayFrames > 0) delayFrames else 0)

    /** Frames of signal delay this instance actually adds — 0 on the classic path. */
    val latencyFrames: Int get() = delayFrames

    /** [latencyFrames] in milliseconds. */
    val latencyMs: Double get() = delayFrames * 1000.0 / sampleRate

    private var delayPos = 0

    // Monotonic-increasing deque for the running MINIMUM of the required gain over a window of
    // `delayFrames + 1`. Primitive-backed: ArrayDeque<Double> boxes on Kotlin/JS.
    // Sized delayFrames + 3, and the +3 is load-bearing. The window holds up to `delayFrames + 1`
    // entries, but eviction runs AFTER the push, so occupancy transiently reaches `delayFrames + 2`
    // — and a head/tail ring represents at most `size - 1`. At `size = delayFrames + 2` that push
    // makes tail meet head, the deque reads as EMPTY, the emptiness-guarded eviction then does
    // nothing, and the next push overwrites the head: the whole hold window is discarded and the
    // ceiling guarantee with it. Reproduced on monotonically-falling loud sub-bass (a saturated
    // 20 Hz tone at +18 dB over threshold leaked 362 samples past 1.0).
    //
    // ⚠️ The `+ 3` and the `minHead != minTail` guard in the eviction loop are ONE fix, not two.
    // Without the guard, `size = delayFrames + 2` is exactly correct — the ambiguous full-ring state
    // self-corrects by evicting one stale entry. The guard (added for Int-wrap safety) is what makes
    // the extra slot load-bearing. Do not "simplify" either half on the grounds that the other
    // handles it.
    private val minVals = DoubleArray(if (delayFrames > 0) delayFrames + 3 else 0)
    private val minIdx = IntArray(if (delayFrames > 0) delayFrames + 3 else 0)
    private var minHead = 0
    private var minTail = 0
    private var sampleCounter = 0

    // Two cascaded box filters: combined support b1 + b2 - 1 taps, coverage condition
    // b1 + b2 - 2 <= delayFrames.
    // Allocated ONCE at the ceiling; `boxLen` is the portion actually in use, so re-splitting the
    // taps from the attackSeconds setter can never allocate or resize on the audio thread.
    private val boxA = DoubleArray(if (delayFrames > 0) (delayFrames + 2) / 2 else 0)
    private val boxB = DoubleArray(if (delayFrames > 0) (delayFrames + 2) / 2 else 0)
    private var boxLen = 1
    private var boxAPos = 0
    private var boxBPos = 0
    private var boxASum = 0.0
    private var boxBSum = 0.0

    /**
     * Release-stage state — a **dual, program-dependent release**. Runs BEFORE the smoother, and
     * only on the lookahead path (the classic path keeps its own single release in [envelopeStep]).
     *
     * [slowGain] tracks the required gain with a plain one-pole in both directions: it absorbs the
     * sustained level and barely moves. [fastGain] handles only what is left over (`held / slow`),
     * instant down and one-pole up, so it deals with each individual hit and is finished before the
     * next one lands. Applied gain is their product, which is `<= held` by construction — so the
     * ceiling argument in [lookaheadStep]'s KDoc is unaffected.
     *
     * The fast branch is `releaseSeconds` / [FAST_RELEASE_DIVISOR]. Deliberately derived rather than
     * exposed: `releaseSeconds` keeps meaning "the release", and no new DSL knob was added for
     * something nobody had asked to control.
     *
     * ── How we got here (2026-08-06/07), because the reasoning is not obvious from the code ──
     *
     * Adding lookahead fixed the transient "knock" but created a new problem: the limiter went from
     * doing ~nothing during a transient (the hard clip did the work) to applying its full reduction
     * to the **entire summed mix** on every hit. With a single 100 ms release the gain is still
     * climbing back when the next kick lands at a musical tempo, so a sustained bed audibly swells
     * between hits — a pump. Measured on a kick-plus-bed at 120 BPM: the bed sat 0.67 dB down
     * between kicks and the mix ran 1.59 dB below its ceiling.
     *
     * Two other fixes were measured and REJECTED, so nobody re-tries them:
     *  - **A dB-domain release.** Its gap is also exponential, so it crawls in the last dB just as
     *    badly: 205 ms to recover within 0.5 dB, versus 187 ms for the linear one. No better.
     *  - **Simply shortening the release.** It does reduce the pump, but it gives back most of what
     *    the 5 ms lookahead window bought: 55 Hz non-fundamental energy goes −41.1 dB → −37.4 dB at
     *    a 50 ms release, −35.3 dB at 30 ms.
     *
     * ⚠️ **The by-ear verdict on the pump itself was "really subtle"** — and that was on material
     * built to be maximally unkind (a sustained saw pad under a hard four-on-the-floor at
     * `MasterFx.gain(2.2)`, level-matched A/B). On real songs it is likely inaudible. **So this is
     * NOT kept for the pumping.**
     *
     * It is kept because of the second, non-subtle consequence: at an identical peak (−0.34 dBFS in
     * both renders) the mean level goes **−1.59 dB → −0.55 dB**. Same ceiling, ~1 dB louder, because
     * the mix spends less time ducked. That is roughly one gain stage's worth of loudness for one
     * divide per sample, on a single instance — and loudness at a fixed ceiling is exactly what a
     * master chain exists to produce.
     *
     * If this ever needs to be reverted: the pump alone does not justify it, and dropping back to a
     * single release costs ~1 dB of loudness, not audible quality.
     */
    private var slowGain = 1.0
    private var fastGain = 1.0

    init {
        updateCoefficients()
        if (delayFrames > 0) {
            resizeSmoothing()
            resetLookaheadState()
        }
    }

    /**
     * Re-split the box taps from [attackSeconds], honouring `b1 + b2 - 2 <= delayFrames`.
     *
     * **Allocates nothing.** The arrays are sized once at construction to the ceiling; this only
     * moves `boxLen`. It also re-primes the ring, because changing the used length invalidates the
     * running sums and the positions — without that, shrinking would index past the live region and
     * growing would divide a stale sum by a larger length, both of which produce a gain above 1.
     */
    private fun resizeSmoothing() {
        val requested = (attackSeconds * sampleRate).toInt()
        val total = requested.coerceIn(2, delayFrames + 2)
        // Floor the per-box tap count. At `half == 1` the two boxes are an identity — no smoothing
        // at all — and the gain steps by the full reduction in a single sample (~595 dB/ms), which
        // is exactly the click MIN_LOOKAHEAD_FRAMES exists to prevent. Reachable from the DSL as
        // `.lookahead(0.005).attack(0.0)`, i.e. from "make it snappy". The zero-lookahead path has
        // the equivalent floor in updateCoefficients() (`max(0.0001, attackSeconds)`); this is its
        // twin, and a bound against a defect rather than a clamp on taste.
        // NB `boxA.size` is (delayFrames + 2) / 2, and delayFrames >= MIN_LOOKAHEAD_FRAMES, so the
        // range cannot invert. That coupling is MIN_LOOKAHEAD_FRAMES >= 2 * MIN_BOX_TAPS - 2 — break
        // it and coerceIn throws IllegalArgumentException on the audio thread.
        val half = (total / 2).coerceIn(MIN_BOX_TAPS, boxA.size)
        if (half == boxLen) return

        boxLen = half
        primeBoxes()
    }

    /**
     * Refill both box rings with the CURRENT gain and rebuild their running sums for [boxLen].
     *
     * Priming with `1.0` would be a full-scale event, not a neutral one: mid-stream at −12 dB of
     * reduction it snaps the gain to unity in a single sample (measured: Δgain 0.76 linear,
     * +7.42 dBFS out, 65 samples past the ceiling) and holds it open for ~2·boxLen samples while the
     * rings refill — the exact knock this whole feature removes. Priming with [releaseGain] is
     * continuous AND still safe: the applied gain `<= held = min(required[n−D…n])`, and a primed tap can
     * only affect samples within `boxLen − 1 <= D` of now, all inside that window.
     */
    private fun primeBoxes() {
        val seed = slowGain * fastGain
        for (i in 0 until boxLen) {
            boxA[i] = seed
            boxB[i] = seed
        }
        boxAPos = 0
        boxBPos = 0
        boxASum = seed * boxLen
        boxBSum = seed * boxLen
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
                // NaN-guard on the ring write too, not just the detector: MasterStage maps NaN to
                // Short.MIN_VALUE (full-scale negative), so an unguarded NaN would surface as a
                // click delayFrames samples later, far from whatever produced it.
                // NaN-guard, and non-finite generally: an Infinity survives `l != l`, is stored,
                // and later emerges as `Inf * 0.0` = NaN — which MasterStage maps to
                // Short.MIN_VALUE, the exact full-scale click this guard exists to prevent.
                delayL[delayPos] = if (l.isFinite()) l else 0.0
                delayR[delayPos] = if (r.isFinite()) r else 0.0
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
     *
     * ⚠️ **This overload always takes the classic one-pole path, even when [lookaheadSeconds] is
     * set** — it has no delay ring of its own, and sharing the stereo one would desynchronise it.
     * A lookahead-configured `Compressor` therefore limits differently through this door (measured:
     * a +12 dB spike exits at −0.35 dBFS through the stereo overload and +12.04 dBFS through this
     * one). No production caller does this today; do not add one.
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
        // NaN-guard, and non-finite generally. NaN would poison the deque ordering (every
        // comparison false); +Infinity is worse — it drives `required` to 0, pins the gain there for
        // the whole window, and the master then fades the entire mix back in over ~450 ms. Reachable
        // in a raw engine via runaway feedback, and the DC blocker ahead of the limiter passes the
        // first Inf through unchanged. Same guard as the ring write, deliberately.
        val level = if (inputLevel.isFinite()) inputLevel else 0.0

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
        // Wrap-safe form. The naive `minIdx[minHead] <= sampleCounter - (delayFrames + 1)` breaks
        // when sampleCounter overflows Int — ~12.4 h at 48 kHz, and it counts whenever the worklet
        // is alive, not only while playing. Past the wrap that predicate is true for nearly every
        // stored index, so the front walks past the tail and the loop can spin forever inside the
        // render callback. Subtracting first keeps the difference small and correct across the wrap.
        while (minHead != minTail && sampleCounter - minIdx[minHead] > delayFrames) {
            minHead = if (minHead + 1 == minVals.size) 0 else minHead + 1
        }
        val held = minVals[minHead]
        sampleCounter++

        // ── Dual release: slow branch absorbs the level, fast branch clears each hit ──
        // Slow: plain one-pole toward `held`, both directions. Never rises above 1 because `held`
        // never does.
        slowGain += releaseCoeff * (held - slowGain)

        // Fast: only the residual the slow branch has not taken. Instant down, one-pole up.
        // Fallback is `held`, not 1.0, so `slow * fast <= held` is unconditional: with slow <= 1,
        // `slow * min(held, fastRecovered) <= held` holds in the degenerate branch too. (1.0 is the
        // correct limit for the ordinary held >> slow case and gives the same answer there — but it
        // breaks the invariant under ~200 dB of sustained reduction, and an "always" that has an
        // exception is worse than the extra token.)
        val residual = if (slowGain > SILENCE_LIN) held / slowGain else held
        val fastRecovered = fastGain + fastReleaseCoeff * (1.0 - fastGain)
        fastGain = if (residual < fastRecovered) residual else fastRecovered

        // Product is <= held by construction, so the ceiling argument in the KDoc is unaffected.
        val releaseGain = slowGain * fastGain

        // ── Two cascaded box filters (C1 step response) ──
        val len = boxLen
        val a = boxA
        boxASum += releaseGain - a[boxAPos]
        a[boxAPos] = releaseGain
        boxAPos = if (boxAPos + 1 == len) 0 else boxAPos + 1
        val stage1 = boxASum / len

        val b = boxB
        boxBSum += stage1 - b[boxBPos]
        b[boxBPos] = stage1
        boxBPos = if (boxBPos + 1 == len) 0 else boxBPos + 1

        return boxBSum / len
    }

    /** Prime the lookahead state to "wide open, silent rings". */
    private fun resetLookaheadState() {
        delayL.fill(0.0)
        delayR.fill(0.0)
        delayPos = 0
        minHead = 0
        minTail = 0
        sampleCounter = 0
        slowGain = 1.0
        fastGain = 1.0
        primeBoxes()
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
        // Fast branch of the dual release (lookahead path only). A tenth of the configured release,
        // so `releaseSeconds` keeps meaning "the release" and no new knob is needed.
        fastReleaseCoeff = 1.0 - exp(-1.0 / ((releaseTime / FAST_RELEASE_DIVISOR) * sampleRate))
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

        /** How much faster the dual release's fast branch is than the configured release. */
        private const val FAST_RELEASE_DIVISOR: Double = 10.0

        /** Fewest taps per box filter. Two 1-tap boxes are an identity — see `resizeSmoothing`. */
        private const val MIN_BOX_TAPS: Int = 4

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
