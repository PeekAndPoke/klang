/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb.Companion.FEEDBACK_OFFSET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln

/**
 * High-performance stereo reverb based on the Freeverb algorithm
 * (Schroeder/Moorer architecture, Jezar-at-Dreampoint tunings).
 *
 * **How it works:**
 * Simulates the complex reflections of an acoustic space using a network of
 * delay-based filters. The signal flows through 8 parallel comb filters
 * (creating resonance / echo density) and then through 4 series allpass
 * filters (diffusing the sound, smearing transients).
 *
 * **Output (caller contract):** [process] writes the **wet (reverberated)
 * signal additively** into `output` (wet-only; caller owns the dry mix).
 * Same convention as [DelayLine].
 *
 * **Sample rate**: tunings are scaled from the canonical 44.1 kHz reference,
 * but the diffusion network is designed for sample rates ≥ 22050 Hz. Below
 * that, several comb tunings collapse via `coerceAtLeast(1)` and lose the
 * prime-ish ratios that produce a dense response. Avoid sub-22 kHz playback.
 *
 * **Implementation details:**
 * - **Structure-of-Arrays** state layout (`combBufsL`, `combPosL`, …) — no
 *   per-filter object overhead, no per-sample virtual dispatch, better cache
 *   locality. The cost is more verbose code, but the inner loop is hot.
 * - **Inlined kernels**: the comb + allpass per-sample math is unrolled
 *   directly into [process] so the JIT can vectorise. ~24 buffer reads/
 *   writes per output sample — the heaviest single DSP unit in the engine.
 * - **Stereo decorrelation**: right channel uses the same tunings + a fixed
 *   23-sample spread on every delay line, producing a wide centred image
 *   even from a mono input.
 * - **Gain staging**: `FIXED_GAIN = 0.015` normalises the sum of 8 resonant
 *   combs to keep internal levels bounded.
 * - **Denormal protection**: each comb LPF state and allpass buffer write
 *   adds a tiny `ANTI_DENORMAL = 1e-18` bias, preventing the IIR state from
 *   decaying into subnormal range during silence (which would cause FPU
 *   stalls at ~50–100 cycles each). The bias is well below audibility and
 *   matches the canonical Freeverb approach. With 24 IIR stores per sample,
 *   the per-sample `+ 1e-18` is dramatically cheaper than `flushState()`
 *   (24× ABS + compare + branch) — the rest of the engine uses
 *   `flushState` because those components have only 1–2 IIR stages.
 *
 * **Parameter mapping.** Everything a user authors goes through [normalizeSize] or lands here raw;
 * both buses (per-orbit and master) MUST agree:
 * - `reverb(wet)` → send amount (caller-side; not a parameter here), 0..1.
 * - `reverb(size)` → [size]. Authored on the **~0..10** scale ([AUTHORED_SIZE_SCALE]), normalized
 *   to 0..1 here. Tail length, via comb feedback `feedback = size · FEEDBACK_SCALE + FEEDBACK_OFFSET`:
 *   authored 3 ≈ 1.0 s, 5 ≈ 1.4 s, 10 ≈ 12.5 s. The shortest reachable tail is ~0.7 s
 *   ([FEEDBACK_OFFSET]).
 * - `reverb(lowpass)` → [lowpass] (HF damping cutoff in Hz; unset = [DEFAULT_DAMP]).
 *
 * Authored sizes above 10 are bounded by [normalizeSize] (see there for why the bound sits at 10).
 */
class Reverb(
    val sampleRate: Int,
) {
    // --- Tuning (Freeverb canonical, scaled to actual sample rate) ---
    private val srScale = sampleRate / REFERENCE_SAMPLE_RATE.toDouble()
    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        .map { (it * srScale).toInt().coerceAtLeast(1) }.toIntArray()
    private val allPassTuning = intArrayOf(556, 441, 341, 225)
        .map { (it * srScale).toInt().coerceAtLeast(1) }.toIntArray()
    private val stereoSpread = (STEREO_SPREAD_44K1 * srScale).toInt().coerceAtLeast(1)

    /** Longest comb revolution in samples (the right channel of the longest comb) — the
     *  countdown period [drainSamplesUntilSilent] counts in. */
    private val longestCombSamples = (combTuning.max() + stereoSpread).toDouble()

    // --- State (flattened for performance) ---
    private val numCombs = combTuning.size
    private val numAllPass = allPassTuning.size

    // Left channel
    private val combBufsL = Array(numCombs) { AudioBuffer(combTuning[it]) }
    private val combPosL = IntArray(numCombs)
    private val combStoreL = DoubleArray(numCombs) // comb LPF history

    private val apBufsL = Array(numAllPass) { AudioBuffer(allPassTuning[it]) }
    private val apPosL = IntArray(numAllPass)

    // Right channel (decorrelated by stereoSpread on every delay line)
    private val combBufsR = Array(numCombs) { AudioBuffer(combTuning[it] + stereoSpread) }
    private val combPosR = IntArray(numCombs)
    private val combStoreR = DoubleArray(numCombs)

    private val apBufsR = Array(numAllPass) { AudioBuffer(allPassTuning[it] + stereoSpread) }
    private val apPosR = IntArray(numAllPass)

    // --- Parameters ---

    /** Decay tail length (comb feedback), normalized. 0 = short tail, 1 = long tail. NaN/Inf silently ignored. */
    var size: Double = normalizeSize(REVERB_SIZE)
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /**
     * High-frequency damping of the tail as a lowpass cutoff in Hz; lower = darker. Null = the fixed
     * [DEFAULT_DAMP]. NaN/Inf silently ignored.
     */
    var lowpass: Double? = null
        set(value) {
            if (value != null && !value.isFinite()) return
            field = value
        }

    /**
     * Returns true if the internal reverb buffers still contain audio above
     * [threshold]. Used by cylinder cleanup to detect a tail that should keep
     * the cylinder alive.
     *
     * **Cost**: O(numCombs × maxCombSize × 2 channels) — at 48 kHz ≈ 24k samples.
     * Not intended for per-block use; called from cleanup polling.
     *
     * **Conservative under stable feedback (≤ 0.98)**: if every stored sample
     * is below threshold, no future iteration brings the output back above
     * threshold (steady-state bound is `threshold / (1 − fb) ≈ 50 · threshold`,
     * still well below audibility for typical thresholds).
     */
    fun hasTail(threshold: Double = TAIL_THRESHOLD): Boolean {
        // Test/diagnostic only since the content-ceiling tail (`TailCeiling`): no production caller.
        for (c in 0 until numCombs) {
            for (sample in combBufsL[c]) {
                if (sample > threshold || sample < -threshold) {
                    return true
                }
            }
            for (sample in combBufsR[c]) {
                if (sample > threshold || sample < -threshold) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Highest absolute sample across ALL comb buffers, both channels — the drain-countdown
     * analog of `DelayLine.tapWindowPeakAbs`. For a comb the whole buffer IS the tap window:
     * the read position cycles through every cell and re-feeds it, so (unlike the delay ring)
     * nothing is overwritten before it is emitted. Allpass buffers are deliberately excluded
     * from the magnitude scan, matching [hasTail]'s own convention: the series chain holds
     * ~1.66k samples of comb-sum history (round 2 corrected round 1's single-stage figure) —
     * about one comb revolution, so once every comb cell is at the threshold the chain was fed
     * `<= numCombs x threshold / fb` for its whole memory, and the [FIXED_GAIN]-scaled wet a
     * cut leaves behind stays in the -100 dBFS class: the same inaudible cut the orbit teardown
     * has always made. The one thing the scan DOES report about a cell besides magnitude is
     * health: any non-finite cell returns [Double.POSITIVE_INFINITY] (a NaN spreads through the
     * LPF store and an Inf never decays, and `NaN > peak` is false, so a plain magnitude scan
     * would be BLIND to a NaN-poisoned network and let it drain garbage into the mix — review
     * round 2). Comb-only stays sufficient for that too: the allpass chain is fed from comb
     * reads, so allpass poison implies a poisoned comb cell first, and comb poison persists
     * until a reset.
     *
     * **Cost**: one O(all comb cells) scan (~22k samples at 44.1 kHz) — the same class as
     * [hasTail], intended for the off-transition (at most once per lease handoff), never
     * per block.
     */
    fun combPeakAbs(): Double {
        var peak = 0.0

        for (c in 0 until numCombs) {
            for (sample in combBufsL[c]) {
                if (!sample.isFinite()) {
                    return Double.POSITIVE_INFINITY
                }

                val mag = abs(sample)
                if (mag > peak) {
                    peak = mag
                }
            }
            for (sample in combBufsR[c]) {
                if (!sample.isFinite()) {
                    return Double.POSITIVE_INFINITY
                }

                val mag = abs(sample)
                if (mag > peak) {
                    peak = mag
                }
            }
        }

        return peak
    }

    /**
     * Closed-form upper bound on how many samples of silent-input processing it takes until
     * every comb cell is provably below [threshold] — the countdown the bus effect's draining
     * state runs on (the `DelayLine.drainSamplesUntilSilent` analog).
     *
     * One full revolution of a comb rewrites every cell as `feedback x lowpassed(history)`.
     * The damping LPF is a convex combination of already-read cells whose pre-revolution memory
     * weight is `damping^N` — indistinguishable from zero for every supported comb length
     * (the SHORTEST comb is N ~ 558 at the 22.05 kHz support floor, damping <= 0.4 via
     * DAMP_SCALE; the convexity additionally needs `damping < 1`, which holds structurally: the
     * effective damp is [DEFAULT_DAMP] or derived from [lowpass] bounded to [0, 1]) — so cell peaks
     * contract by at least `|feedback|` per revolution REGARDLESS of damping (round 2 settled
     * this after round 1's dominant-root stretch argument, which is real only for toy-sized
     * combs where `damping^N` still matters). What makes the `+ 1` spare revolution
     * LOAD-BEARING, not free margin: the LPF store carries ACROSS the off-transition and is
     * bounded by `peak/|fb|`, not `peak`, so the first revolution may hold instead of contract
     * — the spare revolution absorbs exactly that lag. Do not drop it.
     * Counting revolutions of the LONGEST comb bounds every shorter one, which revolves more
     * often: `n = ceil(ln(threshold/peak) / ln(|feedback|)) + 1` periods.
     *
     * The feedback is the same [effectiveFeedback] that [process] applies — structurally within
     * `[0.7, 0.98]` through the production path ([normalizeSize] bounds size, and the orbit
     * configure door bounds it again), so unlike the delay there is no self-oscillating regime
     * to sentinel. The `|feedback| >= 1` arm exists only so the formula can never claim a
     * (test-rigged, production-unreachable) growing network drains.
     *
     * [size] is the one in force by default. A caller whose size is still MOVING passes the
     * largest size the drain will run under instead (the orbit stage's knob glide, 2026-09-19):
     * the proof needs a bound on the feedback of every revolution, and a countdown taken from a
     * feedback that is still rising would end while the tail is audible.
     */
    fun drainSamplesUntilSilent(peak: Double, threshold: Double = TAIL_THRESHOLD, size: Double = this.size): Double {
        if (peak <= threshold) {
            return 0.0
        }

        val fbAbs = abs(feedbackFor(size))

        if (fbAbs >= 1.0) {
            return Double.POSITIVE_INFINITY
        }

        val periods = ceil(ln(threshold / peak) / ln(fbAbs)) + 1.0

        return periods * longestCombSamples
    }

    /**
     * Clear all reverb state — comb buffers, allpass buffers, LPF stores, and
     * read positions on both channels. Used by cylinder cleanup so a restart
     * doesn't carry the previous tail into a new playback. Parameter values
     * are preserved.
     */
    fun reset() {
        for (c in 0 until numCombs) {
            combBufsL[c].fill(0.0)
            combBufsR[c].fill(0.0)
            combStoreL[c] = 0.0
            combStoreR[c] = 0.0
            combPosL[c] = 0
            combPosR[c] = 0
        }
        for (a in 0 until numAllPass) {
            apBufsL[a].fill(0.0)
            apBufsR[a].fill(0.0)
            apPosL[a] = 0
            apPosR[a] = 0
        }
    }

    /**
     * Puts every parameter back to its constructor default — what a unit fresh from `Reverb(sampleRate)`
     * carries. With [reset] this makes a shelved unit indistinguishable from a new one
     * (`ReverbUnits.giveBack`). Keep in sync with the property initialisers above.
     */
    fun restoreDefaults() {
        size = normalizeSize(REVERB_SIZE)
        lowpass = null
    }

    /** The comb feedback [process] runs at: `size x FEEDBACK_SCALE + FEEDBACK_OFFSET` — one
     *  definition shared with [drainSamplesUntilSilent], so the drain math can never diverge from
     *  the DSP it predicts. */
    private fun effectiveFeedback(): Double = feedbackFor(size)

    /** The comb feedback a normalized [size] maps to: the one definition of the size-to-feedback mapping. */
    private fun feedbackFor(size: Double): Double = size * FEEDBACK_SCALE + FEEDBACK_OFFSET

    /** The comb feedback right now, for [TailCeiling] — one definition with [process] and the drain. */
    val tailFeedback: Double get() = effectiveFeedback()

    /** The window for [TailCeiling]: the longest comb's revolution plus one sample. */
    val tailWindowSamples: Double get() = longestCombSamples + 1.0

    /**
     * How many times a sample can pass the SHORTEST comb within [tailWindowSamples]: the comb
     * lengths span 1116..1617 (+ spread), under 2×, so two. Computed, not assumed, so a retuning
     * cannot silently break the ceiling's bound.
     */
    val tailLapsPerWindow: Int = ceil((longestCombSamples + 1.0) / combTuning.min().toDouble()).toInt()

    /**
     * Process one block. Reads dry stereo from [input], adds the wet
     * (reverberated) signal additively into [output]. Caller is responsible
     * for placing the dry mix into [output] beforehand if a wet+dry result is
     * desired (this class is typically used as a send/return effect).
     */
    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        val inL = input.left
        val inR = input.right
        val outL = output.left
        val outR = output.right

        // --- 1. Control-rate calculations (once per block) ---

        // Comb feedback ← size
        val feedback = effectiveFeedback()

        // Damping ← DEFAULT_DAMP, or lowpass when set. lowpass = nyquist → no damping;
        // lowpass = 0 → max damping. Then scale to the comb LPF range.
        val lp = lowpass
        val effectiveDamp = if (lp != null) {
            val nyquist = sampleRate / 2.0
            val normalised = (lp / nyquist).coerceIn(0.0, 1.0)
            1.0 - normalised
        } else {
            DEFAULT_DAMP
        }
        val damping = effectiveDamp * DAMP_SCALE
        val invDamping = 1.0 - damping
        val outputGain = FIXED_GAIN

        // --- 2. Audio-rate processing ---

        for (i in 0 until length) {
            // Non-finite guard on the two INPUT taps rather than the 24 state stores. The
            // stores keep `+ ANTI_DENORMAL` (converting them to `flushState` was measured at
            // ~+11%/sample and reverted 2026-05-19), so they have no per-store guard of their
            // own — but they do not need one: the comb/allpass network is a stable linear
            // system (|feedback| < 1 via normalizeSize, damping in [0,1], coefficients
            // guarded at configure), so a FINITE input can never drive the state non-finite.
            // Guarding the input is therefore equivalent and 12x cheaper. Without it, one Inf
            // sample latched every comb and allpass for the life of the orbit.
            // `abs(x) <= MAX_VALUE` rejects Inf AND NaN in one compare (NaN fails every
            // comparison) and leaves no branch on the data — see `flushState`.
            val rawL = inL[i]
            val rawR = inR[i]
            val inpL = if (abs(rawL) <= Double.MAX_VALUE) rawL else 0.0
            val inpR = if (abs(rawR) <= Double.MAX_VALUE) rawR else 0.0

            var sumL = 0.0
            var sumR = 0.0

            // Parallel comb filters (each with one-pole LPF damping in the
            // feedback path). Inlined to keep state in registers.
            //
            // Denormal protection via `+ ANTI_DENORMAL` (1e-18) on every state
            // store — the canonical Freeverb approach. A previous revision
            // (2026-05-08, Round 9) replaced this with `flushState()` for
            // engine-wide consistency, but that cost ~+11% per-sample because
            // Reverb has 24 IIR stores/sample (8 combs + 4 allpass × 2 ch),
            // vs 1–2 for other components. The ANTI_DENORMAL bias is well
            // below audibility (~250 dB below the noise floor); the engine's
            // `flushState` pattern remains canonical for low-state-count
            // components. Reverted 2026-05-19.
            for (c in 0 until numCombs) {
                // Left
                val bufL = combBufsL[c]
                val sizeL = bufL.size
                var posL = combPosL[c]

                val outSampleL = bufL[posL]
                combStoreL[c] = (outSampleL * invDamping) + (combStoreL[c] * damping) + ANTI_DENORMAL
                bufL[posL] = inpL + (combStoreL[c] * feedback)

                sumL += outSampleL

                if (++posL >= sizeL) {
                    posL = 0
                }
                combPosL[c] = posL

                // Right
                val bufR = combBufsR[c]
                val sizeR = bufR.size
                var posR = combPosR[c]

                val outSampleR = bufR[posR]
                combStoreR[c] = (outSampleR * invDamping) + (combStoreR[c] * damping) + ANTI_DENORMAL
                bufR[posR] = inpR + (combStoreR[c] * feedback)

                sumR += outSampleR

                if (++posR >= sizeR) {
                    posR = 0
                }
                combPosR[c] = posR
            }

            // Series allpass filters — diffuse the comb sum.
            for (a in 0 until numAllPass) {
                // Left
                val bufL = apBufsL[a]
                val sizeL = bufL.size
                var posL = apPosL[a]

                val bufOutL = bufL[posL]
                val newOutL = -sumL + bufOutL
                bufL[posL] = sumL + (bufOutL * ALL_PASS_FEEDBACK) + ANTI_DENORMAL
                sumL = newOutL

                if (++posL >= sizeL) {
                    posL = 0
                }
                apPosL[a] = posL

                // Right
                val bufR = apBufsR[a]
                val sizeR = bufR.size
                var posR = apPosR[a]

                val bufOutR = bufR[posR]
                val newOutR = -sumR + bufOutR
                bufR[posR] = sumR + (bufOutR * ALL_PASS_FEEDBACK) + ANTI_DENORMAL
                sumR = newOutR

                if (++posR >= sizeR) {
                    posR = 0
                }
                apPosR[a] = posR
            }

            // Wet output, additive.
            outL[i] = outL[i] + sumL * outputGain
            outR[i] = outR[i] + sumR * outputGain
        }
    }

    companion object {
        /**
         * The **authored** size scale — what `reverb(size = ...)` speaks on both doors (sprudel and
         * the master reverb builder): roughly 0..10.
         *
         * [size] itself is normalized 0..1. Keeping the conversion here means both buses go through
         * one definition instead of each inventing its own — the two silently disagreed before
         * (sprudel divided by 10, the master did not), so the same number meant a ~1 s tail on an
         * orbit and a ~12.5 s tail on the master.
         */
        const val AUTHORED_SIZE_SCALE: Double = 10.0

        /**
         * Authored size (the ~0..10 [AUTHORED_SIZE_SCALE]) → the normalized 0..1 that [size] consumes.
         *
         * **Bounded to 0..1, deliberately (maintainer, 2026-09-16).** Normalized 1.0 is a comb
         * feedback of 0.98 (about a 12.5 s tail), the top of canonical Freeverb's 0.70..0.98 range,
         * and the range the drain countdown ([drainSamplesUntilSilent]) and `TailCeiling` are proven
         * for. It is NOT the stability edge: unity feedback sits at normalized ~1.071 (authored
         * ~10.71), and the sliver between holds ever longer tails (authored ~10.36 ≈ 25 s, ~10.54 ≈
         * 50 s) approaching a freeze. Past unity the network has no steady state at all: the comb
         * buffers grow without bound until they reach Inf/NaN, and on the master, which feeds the
         * shared mix, every playback is railed until a reload. Record: `docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`.
         *
         * (An earlier attempt kept it unclamped and soft-capped the feedback instead, so extreme
         * values would self-oscillate. Measured, they do not: the saturator rails at exactly ±1.0,
         * so every comb sample latches and the output is pure DC with zero AC content. Reverted —
         * see docs/tasks/master-dsl.md.)
         *
         * The floor at 0 (a 0.7 s tail) is also inside the stable range; it stays because `size`
         * doubles as the orbit reverb's on switch.
         */
        fun normalizeSize(authored: Double): Double {
            // NaN-guard — coerceIn passes NaN straight through, and a NaN here would be dropped by
            // the size setter, silently leaving a pooled reverb on its previous owner's room.
            if (authored != authored) {
                return 0.0
            }

            return (authored / AUTHORED_SIZE_SCALE).coerceIn(0.0, 1.0)
        }

        /** The silence threshold [hasTail] and [drainSamplesUntilSilent] share (~-100 dBFS). */
        const val TAIL_THRESHOLD: Double = 0.00001

        /** Reference sample rate the canonical Freeverb tunings were tuned for. */
        private const val REFERENCE_SAMPLE_RATE: Int = 44100

        /**
         * Comb-feedback mapping: `feedback = size · FEEDBACK_SCALE + FEEDBACK_OFFSET`.
         * For `size ∈ [0, 1]`, feedback ∈ [0.70, 0.98] — the canonical Jezar range.
         */

        private const val FEEDBACK_SCALE: Double = 0.28
        private const val FEEDBACK_OFFSET: Double = 0.7

        /**
         * Comb-damping mapping: `damping = damp · DAMP_SCALE` (so `damp = 1` → 0.4), where damp is
         * [DEFAULT_DAMP] or derived from [lowpass]. Limits HF roll-off in the comb LPF feedback path
         * to a musical maximum.
         */
        private const val DAMP_SCALE: Double = 0.4

        /**
         * Damping (0 = bright .. 1 = dark) when no [lowpass] is set: the Freeverb default. Equivalent
         * to a lowpass at half the Nyquist frequency.
         */
        private const val DEFAULT_DAMP: Double = 0.5

        /** Output normalisation — sum of 8 resonant combs has high peak amplitude. */
        private const val FIXED_GAIN: Double = 0.015

        /** Allpass coefficient — Jezar Freeverb fixed value. */
        private const val ALL_PASS_FEEDBACK: Double = 0.5

        /** Right-channel decorrelation: every delay line is +N samples vs left. Scaled by sample rate. */
        private const val STEREO_SPREAD_44K1: Int = 23

        /**
         * Anti-denormal injection — a tiny DC bias added to every IIR state update.
         * Keeps the state magnitude above the FPU subnormal threshold (≈ 2.2e-308)
         * during silence, preventing ~50–100 cycle stalls on denormal arithmetic.
         * The resulting steady-state DC bias is `~1.7e-18 / (1 − damping) ≈ 2.8e-18`
         * for the comb LPF and `~2e-18` for the allpass — both ~250 dB below the
         * noise floor, inaudible. Canonical Freeverb approach.
         */
        private const val ANTI_DENORMAL: Double = 1e-18
    }
}
