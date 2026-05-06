package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.tan

// ─────────────────────────────────────────────────────────────────────────────────────
// IIR filters and shared coefficient helpers.
//
// First-order:
//   • OnePoleLPF — bilinear-prewarped lowpass (parameterized by cutoffHz)
//   • OnePoleHPF — bilinear-prewarped highpass (parameterized by cutoffHz, true −3 dB at fc)
//   • DcBlocker — degenerate raw-pole HPF (parameterized by raw IIR pole; cheaper)
//
// Second-order (TPT/Vadim Zavalishin SVF, "The Art of VA Filter Design", canonical Cytomic form):
//   • BaseSvf + SvfLPF / SvfHPF / SvfBPF / SvfNotch — 4 specialized subclasses sharing
//     the state-update math; differ only in the output tap selection.
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
//
// **SVF history (review notes added 2026-04-29):**
//
// The TPT-SVF (`BaseSvf` + 4 subclasses, plus the `Ignitor.svf` combinator in
// `ignitor/IgnitorFilters.kt`) implements the canonical Vadim Zavalishin / Cytomic
// trapezoidal-integrator SVF. Math verified against "The Art of VA Filter Design"
// eq. 5.18 — trapezoidal integrators are unconditionally stable for any finite g, k.
//
// Coefficient setup is shared via [computeSvfCoeffs] / [SvfCoeffs] — same NaN/Inf
// guard as `bilinearK`, single source of truth across class and Ignitor sides.
//
// **Coefficient zipper (Ignitor side, env-modulated)**: when `FilterEnvelope.depth ≠ 0`,
// the cutoff sweeps over the block. Per-block coefficient recompute used to leave a
// stair-step at the block rate (~187 Hz buzz @ 256 frames/48k on aggressive sweeps).
// Now: compute coefs at block start AND end (2 `tan` calls), then linearly interpolate
// `a1, a2, a3, k` per sample using a Bresenham-style accumulator (1 add per coef per
// sample, no multiplies in the inner loop). Same idiom as `Oversampler.upsample`.
// Per-sample tan would have been ~30 ns × 48k × N voices — far too expensive.
//
// **What was reviewed / decided (do not re-litigate):**
// - Kept the 4 specialized SVF subclasses rather than collapsing — JIT specialization
//   wins over the 5-line dup, and an abstract `tap()` lambda would defeat inlining.
// - Kept the per-sample `when(mode)` in `Ignitor.svf` — hoisting it to 4 inner loops
//   would re-duplicate the state-update math we just deduped via `computeSvfCoeffs`.
// - `BaseSvf.q` stays construction-time immutable; `Ignitor.svf` supports audio-rate
//   `q: Ignitor`. Different surfaces, intentional. The strip pipeline only modulates
//   cutoff, never Q.
// - BPF tap is the constant-skirt form (`v1` direct), peak gain = Q at fc. Standard
//   SVF convention. `bandpass(q=10)` ⇒ +20 dB at fc — documented in `IgnitorFilters.kt`.
//
// **Round 4 (2026-04-29) — Q clamp widened from [0.1, 50] to [0.1, 200]:**
//
// `FormantFilter`'s vowel tables in `SprudelVoiceData` use Q=60-130 per band. The old
// clamp at 50 was silently flattening every vowel — every formant peak was lower and
// fatter than the data prescribed. Trapezoidal SVF is unconditionally stable for any
// finite Q (`The Art of VA Filter Design` ch. 5.3), so widening the clamp is safe.
// The pole gets very close to the unit circle at extreme Q (e.g. Q=130, fc=600,
// fs=48k → |p|≈0.99996, settling time ~Q/(π·fc) ≈ 70 ms) but never on/outside it.
// `flushDenormal` threshold (1e-15) won't false-trigger because legitimate state
// stays well above that for many seconds at musical fc/Q ranges.
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

/**
 * Bundled TPT-SVF coefficient set: `a1, a2, a3, k`. Mutable holder, allocated once per
 * filter instance (not per call) so [computeSvfCoeffs] can write all four without
 * returning a tuple. Used by both `BaseSvf` and `Ignitor.svf`.
 */
internal class SvfCoeffs {
    var a1: Double = 0.0
    var a2: Double = 0.0
    var a3: Double = 0.0
    var k: Double = 0.0
}

/**
 * Computes the TPT-SVF coefficient bundle from `cutoffHz` and `q`. NaN/Inf-safe via
 * [bilinearK]; `q` is clamped to `[0.1, 200.0]` and falls back to `1/√2` (Butterworth)
 * if non-finite. Single source of truth for the SVF coefficient math — used by
 * `BaseSvf.setCutoff`, `Ignitor.svf` (per-block recompute), and the env-modulated
 * coefficient-lerp path (computed at block start and end).
 *
 * **Q clamp note (2026-04-29)**: widened from `[0.1, 50.0]` to `[0.1, 200.0]` for
 * formant synthesis. Vowel tables in `SprudelVoiceData` use Q=60-130 per band and
 * the old clamp was silently flattening every vowel. Trapezoidal SVF is
 * unconditionally stable for any finite Q. See file header.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun computeSvfCoeffs(cutoffHz: Double, q: Double, sampleRate: Double, out: SvfCoeffs) {
    val g = bilinearK(cutoffHz, sampleRate)
    val safeQ = if (q.isFinite()) q.coerceIn(0.1, 200.0) else 0.7071067811865475
    out.k = 1.0 / safeQ
    out.a1 = 1.0 / (1.0 + g * (g + out.k))
    out.a2 = g * out.a1
    out.a3 = g * out.a2
}

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

    fun createFormant(bands: List<FilterDef.Formant.Band>, sampleRate: Double): AudioFilter =
        FormantFilter(bands, sampleRate)

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

    /**
     * State Variable Filter base — TPT/Zavalishin canonical Cytomic form. Subclasses
     * specialize `process()` to select the output tap (LP/HP/BP/Notch). The state-update
     * math is identical across all 4 subclasses; only the per-sample tap differs.
     *
     * `q` is fixed at construction. The voice-strip pipeline only modulates cutoff
     * (`AudioFilter.Tunable.setCutoff`); audio-rate Q lives on `Ignitor.svf`'s side.
     *
     * Coefficient math is shared via [computeSvfCoeffs] (NaN/Inf-safe via [bilinearK]).
     * The helper writes into a private scratch holder; we then mirror to direct fields
     * so subclasses' inner loops touch fields, not getters (JIT specialization safety).
     */
    abstract class BaseSvf(cutoffHz: Double, q: Double, private val sampleRate: Double) : AudioFilter,
        AudioFilter.Tunable {
        protected var ic1eq = 0.0
        protected var ic2eq = 0.0
        protected var a1: Double = 0.0
        protected var a2: Double = 0.0
        protected var a3: Double = 0.0
        protected var k: Double = 0.0
        private val coefs = SvfCoeffs()
        private val q: Double = q

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            computeSvfCoeffs(cutoffHz, q, sampleRate, coefs)
            a1 = coefs.a1
            a2 = coefs.a2
            a3 = coefs.a3
            k = coefs.k
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
