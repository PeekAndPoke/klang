package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.flushDenormal

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
 *
 * **Strudel parameter mapping**:
 * - `room`     → wet/dry send amount (caller-side; not a parameter here).
 * - `size`     → [roomSize] (decay tail length via comb feedback).
 * - `roomLp`   → [roomLp] (HF damping cutoff; overrides [damp]).
 * - `roomFade` → [roomFade] (overrides [roomSize]).
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

    /** Decay tail length (comb feedback). 0 = short tail, 1 = long tail. NaN/Inf silently ignored. */
    var roomSize: Double = 0.5
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** High-frequency damping (air absorption). 0 = bright, 1 = dark. NaN/Inf silently ignored. */
    var damp: Double = 0.5
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Strudel `roomFade` — when set, overrides [roomSize]. NaN/Inf silently ignored. */
    var roomFade: Double? = null
        set(value) {
            if (value != null && !value.isFinite()) return
            field = value
        }

    /** Strudel `roomLp` — when set, overrides [damp] (lower cutoff = more damping). NaN/Inf silently ignored. */
    var roomLp: Double? = null
        set(value) {
            if (value != null && !value.isFinite()) return
            field = value
        }

    // TODO(klang): future hooks — see docs/agent-tasks/ignitor-dsl-open-items.md.
    //   roomDim   — dimensional / modulated-allpass reverb variant.
    //   iResponse — IR-convolution reverb (FIR / partitioned-FFT path).
    var roomDim: Double? = null
    var iResponse: String? = null

    /**
     * Returns true if the internal reverb buffers still contain audio above
     * [threshold]. Used by cylinder cleanup to detect a tail that should keep
     * the cylinder alive.
     *
     * **Cost**: O(numCombs × maxCombSize × 2 channels) — at 48 kHz ≈ 28k samples.
     * Not intended for per-block use; called from cleanup polling.
     *
     * **Conservative under stable feedback (≤ 0.98)**: if every stored sample
     * is below threshold, no future iteration brings the output back above
     * threshold (steady-state bound is `threshold / (1 − fb) ≈ 50 · threshold`,
     * still well below audibility for typical thresholds).
     */
    fun hasTail(threshold: Double = 0.00001): Boolean {
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

        // Comb feedback ← roomSize (or roomFade override)
        val effectiveSize = roomFade ?: roomSize
        val feedback = effectiveSize * FEEDBACK_SCALE + FEEDBACK_OFFSET

        // Damping ← damp (or roomLp override). roomLp = nyquist → no damping;
        // roomLp = 0 → max damping. Then scale to the comb LPF range.
        val effectiveDamp = if (roomLp != null) {
            val nyquist = sampleRate / 2.0
            val normalised = (roomLp!! / nyquist).coerceIn(0.0, 1.0)
            1.0 - normalised
        } else {
            damp
        }
        val damping = effectiveDamp * DAMP_SCALE
        val invDamping = 1.0 - damping
        val outputGain = FIXED_GAIN

        // --- 2. Audio-rate processing ---

        for (i in 0 until length) {
            val inpL = inL[i]
            val inpR = inR[i]

            var sumL = 0.0
            var sumR = 0.0

            // Parallel comb filters (each with one-pole LPF damping in the
            // feedback path). Inlined to keep state in registers.
            for (c in 0 until numCombs) {
                // Left
                val bufL = combBufsL[c]
                val sizeL = bufL.size
                var posL = combPosL[c]

                val outSampleL = bufL[posL]
                combStoreL[c] = flushDenormal((outSampleL * invDamping) + (combStoreL[c] * damping))
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
                combStoreR[c] = flushDenormal((outSampleR * invDamping) + (combStoreR[c] * damping))
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
                bufL[posL] = flushDenormal(sumL + (bufOutL * ALL_PASS_FEEDBACK))
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
                bufR[posR] = flushDenormal(sumR + (bufOutR * ALL_PASS_FEEDBACK))
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
        /** Reference sample rate the canonical Freeverb tunings were tuned for. */
        private const val REFERENCE_SAMPLE_RATE: Int = 44100

        /**
         * Comb-feedback mapping: `feedback = roomSize · FEEDBACK_SCALE + FEEDBACK_OFFSET`.
         * For `roomSize ∈ [0, 1]`, feedback ∈ [0.70, 0.98] — the canonical Jezar range.
         */
        private const val FEEDBACK_SCALE: Double = 0.28
        private const val FEEDBACK_OFFSET: Double = 0.7

        /**
         * Comb-damping mapping: `damping = damp · DAMP_SCALE` (so `damp = 1` → 0.4).
         * Limits HF roll-off in the comb LPF feedback path to a musical maximum.
         */
        private const val DAMP_SCALE: Double = 0.4

        /** Output normalisation — sum of 8 resonant combs has high peak amplitude. */
        private const val FIXED_GAIN: Double = 0.015

        /** Allpass coefficient — Jezar Freeverb fixed value. */
        private const val ALL_PASS_FEEDBACK: Double = 0.5

        /** Right-channel decorrelation: every delay line is +N samples vs left. Scaled by sample rate. */
        private const val STEREO_SPREAD_44K1: Int = 23
    }
}
