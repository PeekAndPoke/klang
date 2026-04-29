package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.flushDenormal
import kotlin.math.PI
import kotlin.math.tan

// ─────────────────────────────────────────────────────────────────────────────────────
// First-order IIR filters and shared coefficient helpers.
//
// Three first-order filters live here:
//   • OnePoleLPF — bilinear-prewarped lowpass (parameterized by cutoffHz)
//   • OnePoleHPF — bilinear-prewarped highpass (parameterized by cutoffHz, true −3 dB at fc)
//   • DcBlocker — degenerate raw-pole HPF (parameterized by raw IIR pole; cheaper)
//
// **OnePoleLPF / OnePoleHPF history (do not re-litigate):**
//
// Before 2026-04-29 the coefficients used the matched-Z mapping `α = 1 − exp(−2π·fc/fs)`
// (LPF) and `a = exp(−2π·fc/fs)` (HPF). The HPF additionally used the topology
// `y = a·(y + x − xPrev)` which has Nyquist gain `2a/(1+a)` — fine at low cutoffs but
// the gain droops at high cutoffs (`a → 0`), letting through less HF than expected.
// Combined with the matched-Z bias, the actual −3 dB knee sat well off `cutoffHz`.
//
// Current (post-2026-04-29):
// - **Bilinear pre-warp**: `K = tan(π·fc/fs)` for both filters. Standard TPT mapping,
//   accurate to ~fs/4 (beyond which all bilinear designs warp).
// - **LPF**: same topology `y[n] = α·x[n] + (1 − α)·y[n-1]` with `α = K/(1+K)`.
//   Per-sample cost identical to the old version.
// - **HPF**: switched to canonical bilinear form `y[n] = b0·(x[n] − x[n-1]) + a1·y[n-1]`
//   with `b0 = 1/(1+K)`, `a1 = (1−K)/(1+K)`. +1 mul/sample. True −3 dB at `fc`, no
//   Nyquist droop.
//
// **DcBlocker history (added 2026-04-29):**
//
// `DcBlocker` is a degenerate first-order HPF: `y[n] = x[n] − x[n-1] + a·y[n-1]`
// (no input-scaling `b0`). Cheaper than `OnePoleHPF` by 1 mul/sample, parameterized
// by the raw IIR pole `a` instead of cutoffHz (kept this way for back-compat with
// the public `Ignitor.dcBlock(coefficient)` API). Replaced 9 open-coded inline copies
// of the same recurrence in `IgnitorEffects.distort()`, `Ignitor.clip()`, and
// `voices/strip/filter/DistortionRenderer` with a single source of truth.
//
// **2× edge transient**: rail-to-rail input produces a ~2× peak transient through
// the raw-pole topology (railed input − railed previous + nearly-railed feedback).
// `Ignitor.distort()` and `Ignitor.clip()` pair `DcBlocker` with `ClippingFuncs.softCap()`
// downstream to bound output to ±1. The master-out DcBlocker in `KlangAudioRenderer`
// runs on post-limiter samples (already ±1-bounded), so no softCap needed there.
//
// **NaN/Inf guard**: `Double.coerceIn` returns NaN if input is NaN, which would corrupt
// IIR state forever (`flushDenormal` only catches sub-denormal magnitudes). Guarded
// explicitly in both `bilinearK` and `DcBlocker` constructor.
//
// **Block-based API**: all filters use `process(buffer, offset, length)` so JIT keeps
// state in registers across the loop. State load/store happens at function entry/exit,
// not per sample.
// ─────────────────────────────────────────────────────────────────────────────────────

/** Bilinear-prewarped angle factor `K = tan(π·fc/fs)` with NaN/Inf-safe cutoff clamp. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun bilinearK(cutoffHz: Double, sampleRate: Double): Double {
    val fc = if (cutoffHz.isFinite()) cutoffHz.coerceIn(5.0, 0.5 * sampleRate - 1.0) else 1000.0
    return tan(PI * fc / sampleRate)
}

/** First-order LPF coefficient `α = K/(1+K)` for `y[n] = α·x + (1−α)·y[n-1]`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun onePoleLpfCoeff(cutoffHz: Double, sampleRate: Double): Double {
    val k = bilinearK(cutoffHz, sampleRate)
    return k / (1.0 + k)
}

/**
 * Default raw IIR pole for [LowPassHighPassFilters.DcBlocker]. `≈ 35 Hz @ 44.1k, 38 Hz @ 48k`.
 * Used by `Ignitor.distort()` and `Ignitor.clip()` to suppress DC accumulation from
 * asymmetric waveshapers. Matches the historic `0.995` literal that lived inline.
 */
internal const val DEFAULT_DC_BLOCK_COEFF: Double = 0.995

object LowPassHighPassFilters {

    fun createLPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        when (q) {
            null -> OnePoleLPF(cutoffHz, sampleRate)
            else -> SvfLPF(cutoffHz, q, sampleRate)
        }

    fun createHPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        when (q) {
            null -> OnePoleHPF(cutoffHz, sampleRate)
            else -> SvfHPF(cutoffHz, q, sampleRate)
        }

    fun createBPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        SvfBPF(cutoffHz, q ?: 1.0, sampleRate)

    fun createNotch(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        SvfNotch(cutoffHz, q ?: 1.0, sampleRate)

    // --- Implementations ---

    /**
     * First-order bilinear-prewarped LPF: `K = tan(π·fc/fs); α = K/(1+K)`,
     * `y[n] = α·x[n] + (1−α)·y[n-1]`. DC gain = 1, monotonic, stable.
     * Cutoff is accurate (−3 dB at `fc`) up to ~fs/4 — beyond that all bilinear
     * designs warp. See file header for review history.
     */
    class OnePoleLPF(cutoffHz: Double, private val sampleRate: Double) : AudioFilter, AudioFilter.Tunable {
        private var y = 0.0
        private var a: Double = 0.0

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            a = onePoleLpfCoeff(cutoffHz, sampleRate)
        }

        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i]
                y += a * (x - y)
                y = flushDenormal(y)
                buffer[i] = y
            }
        }
    }

    /**
     * First-order canonical bilinear HPF: `K = tan(π·fc/fs); b0 = 1/(1+K); a1 = (1−K)/(1+K)`,
     * `y[n] = b0·(x[n] − x[n-1]) + a1·y[n-1]`. `H(z) = b0·(1 − z⁻¹)/(1 − a1·z⁻¹)`.
     * DC gain = 0, Nyquist gain = 1, true −3 dB at `fc`, stable. See file header for
     * the review history (replaced the old `y = a·(y + x − xPrev)` topology in 2026-04
     * because that one had Nyquist droop at high cutoffs).
     */
    class OnePoleHPF(cutoffHz: Double, private val sampleRate: Double) : AudioFilter, AudioFilter.Tunable {
        private var y = 0.0
        private var xPrev = 0.0
        private var b0: Double = 0.0
        private var a1: Double = 0.0

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            val k = bilinearK(cutoffHz, sampleRate)
            val invOnePlusK = 1.0 / (1.0 + k)
            b0 = invOnePlusK
            a1 = (1.0 - k) * invOnePlusK
        }

        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i]
                y = b0 * (x - xPrev) + a1 * y
                y = flushDenormal(y)
                xPrev = x
                buffer[i] = y
            }
        }
    }

    /**
     * Lightweight DC blocker — degenerate first-order HPF with raw pole and
     * no input scaling: `y[n] = x[n] − x[n-1] + a·y[n-1]`. One mul/sample cheaper than
     * [OnePoleHPF]. Produces a ~2× edge transient on rail-to-rail input — call sites
     * post-distort/post-clip pair this with `ClippingFuncs.softCap()` to bound output to ±1.
     *
     * Coefficient is the raw IIR pole: `a ≈ 1 − 2π·fc/fs`. At `a = 0.995, fs = 44.1k`
     * the −3 dB knee is ~35 Hz; at `a = 0.999`, ~7 Hz. NaN/Inf and out-of-range values
     * are guarded — non-finite or `a ∉ [0, 1)` falls back to [DEFAULT_DC_BLOCK_COEFF].
     *
     * Block-based API matches the [AudioFilter] convention so JIT keeps state in
     * registers across the loop. See file header for the dedup history.
     */
    class DcBlocker(coefficient: Double = DEFAULT_DC_BLOCK_COEFF) {
        private val coeff: Double = if (coefficient.isFinite()) {
            coefficient.coerceIn(0.0, 0.99999)
        } else {
            DEFAULT_DC_BLOCK_COEFF
        }
        private var xPrev = 0.0
        private var y = 0.0

        /** In-place: applies DC blocking to `buffer[offset..offset+length)`. */
        fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            var xp = xPrev
            var yc = y
            val a = coeff
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i]
                val out = x - xp + a * yc
                xp = x
                yc = flushDenormal(out)
                buffer[i] = out
            }
            xPrev = xp
            y = yc
        }

        /** Reads [input], writes DC-blocked result to [output]. Both buffers must cover [offset..offset+length). */
        fun process(input: AudioBuffer, output: AudioBuffer, offset: Int, length: Int) {
            var xp = xPrev
            var yc = y
            val a = coeff
            val end = offset + length
            for (i in offset until end) {
                val x = input[i]
                val out = x - xp + a * yc
                xp = x
                yc = flushDenormal(out)
                output[i] = out
            }
            xPrev = xp
            y = yc
        }

        fun reset() {
            xPrev = 0.0
            y = 0.0
        }
    }

    // State Variable Filter (Shared logic)
    abstract class BaseSvf(cutoffHz: Double, q: Double, private val sampleRate: Double) : AudioFilter,
        AudioFilter.Tunable {
        protected var ic1eq = 0.0
        protected var ic2eq = 0.0
        protected var a1: Double = 0.0
        protected var a2: Double = 0.0
        protected var a3: Double = 0.0
        protected var k: Double = 0.0
        private val q: Double = q

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            val nyquist = 0.5 * sampleRate
            val fc = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            val Q = q.coerceIn(0.1, 50.0)

            val g = tan(PI * fc / sampleRate)
            k = 1.0 / Q
            a1 = 1.0 / (1.0 + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        }
    }

    class SvfLPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = v2
            }
        }
    }

    class SvfHPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = (v0 - k * v1 - v2)
            }
        }
    }

    class SvfBPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = v1
            }
        }
    }

    class SvfNotch(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = (v0 - k * v1)
            }
        }
    }
}
