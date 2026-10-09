/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.finiteOrZero
import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_be.utils.safeOut
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.coercePasses
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

// ─────────────────────────────────────────────────────────────────────────────────────
// IIR filters and shared coefficient helpers.
//
// First-order:
//   • the one-pole coefficients [bilinearK] and [onePoleLpfCoeff], shared with the one-pole Ignitor nodes
//     (`OnePoleLowpassIgnitor` behind `onepole(freq)`, `OnePoleHighpassIgnitor` in `ignitor/IgnitorFilters.kt`),
//     which carry the one-pole lowpass and highpass. Their class twins `OnePoleLPF` / `OnePoleHPF` served only the
//     benchmark and their own spec since 2026-08-24 and retired 2026-09-27 (test consolidation).
//   • DcBlocker — degenerate raw-pole HPF (parameterized by raw IIR pole; cheaper)
//
// Second-order (TPT/Vadim Zavalishin SVF, "The Art of VA Filter Design", canonical Cytomic form):
//   • the bandpass of the orbit's resonators, run inside `ResonatorBank` since engine tidy-up step 12 (a); its
//     class (`SvfBPF` on `BaseSvf`) and the strip's lowpass, highpass and notch subclasses are gone (the strip's in
//     phase 3 step 9); the tree's SVF is `Ignitor.svf` in `ignitor/IgnitorFilters.kt`.
//
// **One-pole history (do not re-litigate):** written for the classes, it holds for the Ignitor nodes, which
// run the same coefficients and topology.
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
// (no input-scaling `b0`). Cheaper than the one-pole HPF by 1 mul/sample, parameterized
// by the raw IIR pole `a` instead of cutoffHz (kept this way for back-compat with
// the public `Ignitor.dcBlock(coefficient)` API). Replaced 9 open-coded inline copies
// of the same recurrence in `IgnitorEffects.distort()`, `Ignitor.shape()`, and
// the voice strip's distort with a single source of truth. Since engine tidy-up step 11
// the shaper's one `DcBlocker` lives in `DistortionCore`, shared by the `Shape` node and
// the fused `Distort` node.
//
// **2× edge transient**: rail-to-rail input produces a ~2× peak transient through
// the raw-pole topology (railed input − railed previous + nearly-railed feedback).
// The `Shape` node (`Ignitor.distort()` and `Ignitor.shape()`) follows `DistortionCore`
// with `ShapingFuncs.softCap()` to bound output to ±1; the fused `Distort` node does not. The master-out DcBlocker in `KlangAudioRenderer`
// runs on post-limiter samples (already ±1-bounded), so no softCap needed there.
//
// **NaN/Inf guard**: `Double.coerceIn` returns NaN if input is NaN, which would give the
// filter a NaN COEFFICIENT — guarded explicitly in both `bilinearK` and the `DcBlocker`
// constructor, because a poisoned coefficient re-poisons the state every sample and so is
// not something a state guard can heal. (A poisoned STATE is a different problem and is
// handled: `flushState` rejects non-finite carries as well as sub-denormal ones since the
// master round, so no IIR here can latch on a hostile SAMPLE.)
//
// **Block-based API**: all filters use `process(buffer, offset, length)` so JIT keeps
// state in registers across the loop. State load/store happens at function entry/exit,
// not per sample.
//
// **SVF history (review notes added 2026-04-29):**
//
// The TPT-SVF (the `ResonatorBank` bands, plus the `Ignitor.svf` combinator in
// `ignitor/IgnitorFilters.kt`) implements the canonical Vadim Zavalishin / Cytomic
// trapezoidal-integrator SVF. Math verified against "The Art of VA Filter Design"
// eq. 5.18 — trapezoidal integrators are unconditionally stable for any finite g, k.
//
// Coefficient setup is shared via [computeSvfCoeffs] / [SvfCoeffs] — same NaN/Inf
// guard as `bilinearK`, single source of truth across class and Ignitor sides.
//
// **Coefficient zipper (Ignitor side, env-modulated)**: when `FilterEnvDef.depth ≠ 0`,
// the cutoff sweeps over the block. Per-block coefficient recompute used to leave a
// stair-step at the block rate (~187 Hz buzz @ 256 frames/48k on aggressive sweeps).
// Now: compute coefs at block start AND end (2 `tan` calls), then linearly interpolate
// `a1, a2, a3, k` per sample using a Bresenham-style accumulator (1 add per coef per
// sample, no multiplies in the inner loop). Same idiom as `Oversampler.upsample`.
// Per-sample tan would have been ~30 ns × 48k × N voices — far too expensive.
//
// **What was reviewed / decided (do not re-litigate):**
// - Kept the specialized SVF subclasses rather than collapsing: JIT specialization
//   wins over the 5-line dup, and an abstract `tap()` lambda would defeat inlining. (The
//   last one, `SvfBPF`, folded into `ResonatorBank`'s band loop in engine tidy-up step 12 (a);
//   the decision stands for any tap added back.)
// - Kept the per-sample `when(mode)` in `Ignitor.svf` — hoisting it to 4 inner loops
//   would re-duplicate the state-update math we just deduped via `computeSvfCoeffs`.
// - A resonator band's q is fixed while its bank sounds (`ResonatorBank`); `Ignitor.svf` supports audio-rate
//   `q: Ignitor`. Different surfaces, intentional. The retired strip pipeline only modulated
//   cutoff, never Q.
// - BPF tap is UNITY-peak since C2 of the filter unification (`k·v1`): q is a pure
//   width control. `bandpass(q=10)` gets narrower, not louder — see `IgnitorFilters.kt`.
//
// **Round 4 (2026-04-29) — Q clamp widened from [0.1, 50] to [0.1, 200]:**
//
// The vowel tables in `SprudelVoiceData` use Q=60-130 per band. The old
// clamp at 50 was silently flattening every vowel — every formant peak was lower and
// fatter than the data prescribed. Trapezoidal SVF is unconditionally stable for any
// finite Q (`The Art of VA Filter Design` ch. 5.3), so widening the clamp is safe.
// The pole gets very close to the unit circle at extreme Q (e.g. Q=130, fc=600,
// fs=48k → |p|≈0.99996, settling time ~Q/(π·fc) ≈ 70 ms) but never on/outside it.
// `flushState` threshold (1e-15) won't false-trigger because legitimate state
// stays well above that for many seconds at musical fc/Q ranges.
// ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The SVF's cutoff guard: inside the audible band and below Nyquist, a non-finite value
 * substituted by 1 kHz. **The one home of these numbers** (Katalyst 5c-10): [bilinearK] applies
 * it, and `ResonatorBank` applies it BEFORE taking a logarithm of the frequency, which is only
 * safe while the two agree. A caller that has already clamped changes nothing by clamping again.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun clampSvfCutoff(cutoffHz: Double, sampleRate: Double): Double =
    if (cutoffHz.isFinite()) cutoffHz.coerceIn(5.0, 0.5 * sampleRate - 1.0) else 1000.0

/** The q a non-finite q falls back to: Butterworth, `1/sqrt(2)`. */
internal const val SVF_Q_FALLBACK: Double = 0.7071067811865475

/**
 * The SVF's q guard, the twin of [clampSvfCutoff] and its one home. [computeSvfCoeffs] applies
 * it, `LowPassHighPassFilters.vowelGain` folds the SAME clamped q back into the vowel's gain
 * (which is exact only while both use this function), and `ResonatorBank` applies it before the
 * logarithm.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun clampSvfQ(q: Double): Double =
    if (q.isFinite()) q.coerceIn(0.1, 200.0) else SVF_Q_FALLBACK

/** Bilinear-prewarped angle factor `K = tan(π·fc/fs)` with NaN/Inf-safe cutoff clamp. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun bilinearK(cutoffHz: Double, sampleRate: Double): Double {
    return tan(PI * clampSvfCutoff(cutoffHz = cutoffHz, sampleRate = sampleRate) / sampleRate)
}

/** First-order LPF coefficient `α = K/(1+K)` for `y[ n ] = α·x + (1−α)·y[n-1]`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun onePoleLpfCoeff(cutoffHz: Double, sampleRate: Double): Double {
    val k = bilinearK(cutoffHz = cutoffHz, sampleRate = sampleRate)
    return k / (1.0 + k)
}

/**
 * Polynomial approximation of a diode-pair's I-V resistance characteristic — a curve
 * fit (Horner form) used as a cheap stand-in for the transcendental diode equation.
 *
 * Output is ≥ ~1.0 for all real `x` and grows monotonically with `|x|`. Used by the
 * nonlinear (saturated) SVF branches to make the resonance damping coefficient a
 * **function of the BP integrator state** — as state grows, damping grows,
 * compressing the resonance peak. The signal stays linear, only the *damping gain*
 * becomes state-dependent. Stability is preserved because increasing signal →
 * more damping → bounded resonance (the inverse of what hard-capping `tanh` in
 * the feedback signal would do).
 *
 * Per-sample cost: 4 muls + 4 adds (Horner form).
 *
 * The idea of steering resonance damping from a diode-pair model is inspired by the
 * analog-filter saturation in 2DaT's Obxd (github.com/2DaT/Obxd, by Vadim Filatov);
 * the SVF topology here and the way the term is folded into the coefficients
 * (see the saturated branches of `Ignitor.svf` in `ignitor/IgnitorFilters.kt`) are our own.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun diodePairResistanceApprox(x: Double): Double {
    return ((((0.0103592 * x + 0.00920833) * x + 0.185) * x + 0.05) * x + 1.0)
}

/**
 * Scales the BP integrator state (`ic1eq`) before evaluating [diodePairResistanceApprox] —
 * how hard the resonance "leans into" the diode curve. Larger ⇒ more state-dependent
 * damping. The value follows the analog-saturation lineage of 2DaT's Obxd; see
 * [diodePairResistanceApprox].
 */
internal const val SAT_STATE_SCALE: Double = 0.0876

/**
 * Default raw IIR pole for [LowPassHighPassFilters.DcBlocker]. `≈ 35 Hz @ 44.1k, 38 Hz @ 48k`.
 * Used by `DistortionCore` (under `Ignitor.distort()`, `Ignitor.shape()` and the fused `Distort` node) to
 * suppress DC accumulation from asymmetric waveshapers. Matches the historic `0.995` literal that lived inline.
 */
internal const val DEFAULT_DC_BLOCK_COEFF: Double = 0.995

// NOTE: `BODY_FLOOR` and `VOWEL_FLOOR` moved to `audio_bridge/constants/BusEffectDefaults.kt`
// (Katalyst DSL step 1, 2026-09-17): the Katalyst `body`/`vowel` stage knobs need the same
// numbers, and a second declaration here would be the drift the parity rule forbids. `bodyWet`
// (wire spelling: `bodyMix`) is clamped to [0, 1] since C4 (the shared wet/dry law lives on that
// domain; the old raw extension above 1 is a deleted capability - plan: Helper domain).

/**
 * Overall level tame for the formant bank before the dry/wet blend. The vowel tables are tuned
 * with high Q (80–140), so the raw BPF peaks reach ~+38 dB; this scales them down so blending is
 * sensible — but the formants must still clearly DOMINATE the [VOWEL_FLOOR] (that is what makes a
 * vowel), so this is far less aggressive than it first was. Preserves each vowel's relative
 * balance (single multiplier). Tunable by ear.
 */
internal const val VOWEL_TAME: Double = 0.05

/**
 * Bundled TPT-SVF coefficient set (`a1, a2, a3, k`, plus the exposed angle [g] and the bell
 * mix [m1]). Mutable holder, allocated once per filter instance (not per call) so the
 * compute helpers can write the whole bundle without returning a tuple. Consumers:
 * [SvfCoeffSweep] (for `Ignitor.svf`), `Ignitor.svf`, `ResonatorBank` and `EqCore`.
 */
internal class SvfCoeffs {
    var a1: Double = 0.0
    var a2: Double = 0.0
    var a3: Double = 0.0
    var k: Double = 0.0

    /**
     * Bilinear-prewarped angle `g = tan(π·fc/fs)`. Same value used internally
     * by `computeSvfCoeffs` to derive a1/a2/a3 — exposed here for the
     * analog-style nonlinear (saturated) SVF branches in
     * `Ignitor.svf` (its lowpass and highpass taps), which
     * use `kEff + g` for the per-sample closed-form solve.
     */
    var g: Double = 0.0

    /**
     * BELL mix coefficient — non-zero ONLY when written by [computeSvfBellCoeffs]; plain
     * [computeSvfCoeffs] writes 0.0 so a SHARED per-instance holder can never carry one
     * section's bell gain into the next configured section (harmless for today's readers —
     * only the bell tap reads it — but a trap for future shelf sections without the guard).
     */
    var m1: Double = 0.0
}

/**
 * Computes the TPT-SVF coefficient bundle from `cutoffHz` and `q`. NaN/Inf-safe via
 * [bilinearK]; `q` is clamped to `[0.1, 200.0]` and falls back to `1/√2` (Butterworth)
 * if non-finite. Single source of truth for the SVF coefficient math — used by
 * `Ignitor.svf` (per-block recompute), [SvfCoeffSweep] (the swept path of `Ignitor.svf`, computed
 * at block start and end), `ResonatorBank` (at install) and `EqCore`.
 *
 * **Q clamp note (2026-04-29)**: widened from `[0.1, 50.0]` to `[0.1, 200.0]` for
 * formant synthesis. Vowel tables in `SprudelVoiceData` use Q=60-130 per band and
 * the old clamp was silently flattening every vowel. Trapezoidal SVF is
 * unconditionally stable for any finite Q. See file header.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun computeSvfCoeffs(cutoffHz: Double, q: Double, sampleRate: Double, out: SvfCoeffs) {
    val g = bilinearK(cutoffHz = cutoffHz, sampleRate = sampleRate)
    val safeQ = clampSvfQ(q)
    out.k = 1.0 / safeQ
    out.a1 = 1.0 / (1.0 + g * (g + out.k))
    out.a2 = g * out.a1
    out.a3 = g * out.a2
    out.g = g
    out.m1 = 0.0 // stale-holder guard: only computeSvfBellCoeffs sets a bell mix (see SvfCoeffs)
}

/**
 * Computes the Simper-SVF BELL coefficient bundle: `db` decibels of peaking gain at
 * [cutoffHz], bandwidth set by [q]. Reuses [computeSvfCoeffs] at `q·A` (with
 * `A = 10^(db/40)`) and derives the bell mix `m1 = safeOut(k · (A² − 1))` from the CLAMPED
 * `k` that [computeSvfCoeffs] actually wrote — NEVER from a recomputed `1/(q·A)`: with the
 * q·A clamp active the two differ by tens of dB (q=10, db=+120 → the clamped k gives exactly
 * +120 dB peak; a recomputed m1 lands ~34 dB off). The bell TAP is `v0 + m1·v1` on the same
 * recurrence every other section type runs.
 *
 * Peak gain at fc is exactly `A² = 10^(db/20)` — for ANY effective k, so the q·A clamp
 * NEVER moves the peak, only the bandwidth (unclamped, q·A holds the half-gain width at
 * exactly 1/q for ANY gain — that constancy is the point of the parameterization; once q·A
 * hits the 0.1 floor — at q=0.707, below ≈ −34 dB — the width becomes 10·A and the cut
 * narrows as it deepens). Cut and boost are reciprocal at EVERY
 * frequency while both q·A and q/A are unclamped. What DOES cap the peak is the safeOut on
 * m1: once m1 saturates at SAFE_MAX the peak stops rising — db stops doing anything above
 * ≈ 346 (at safeQ = 200; from ≈ 280 at the q-floor 0.1), saturating near
 * `1 + SAFE_MAX·safeQ`. Far beyond that (db ≈ 12330) `A` itself overflows and q·A inherits
 * the Butterworth fallback, DROPPING the peak — the composite db → gain map is monotone
 * only below the cap.
 *
 * Non-finite [db] falls back to 0.0 (transparent bell). // NaN-guard
 * The safeOut on m1 is the house output-clamp contract, closing the finite-but-astronomical
 * window (db ∈ [346, 6165) yields finite m1 up to ~9e305 — unbounded, that ducks the master
 * limiter for seconds or lands NaN → full-scale DC downstream) and the non-finite case in
 * ONE guard. It is applied to the COEFFICIENT at configure time — the per-sample path stays
 * untouched — and it does NOT shorten the limiter-duck symptom at extreme db (raw Motor:
 * no musical clamp on db).
 *
 * [db] is COEFFICIENT-bearing: it moves bandwidth through `q·A`, so an LFO on db zippers
 * exactly like an LFO on cutoff — the same per-block snap class as the rest of the SVF
 * surface. [q] is the PRE-GAIN bandwidth parameter.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun computeSvfBellCoeffs(
    cutoffHz: Double,
    q: Double,
    db: Double,
    sampleRate: Double,
    out: SvfCoeffs,
) {
    val safeDb = db.finiteOrZero() // NaN-guard
    val a = 10.0.pow(safeDb / 40.0)
    computeSvfCoeffs(cutoffHz = cutoffHz, q = q * a, sampleRate = sampleRate, out = out)
    out.m1 = safeOut(out.k * (a * a - 1.0))
}

private val SQRT2 = sqrt(2.0)

/**
 * Per-stage q values for a [passes]-deep cascade of 12 dB/oct SVF stages (C5).
 *
 * The ladder is the classic Butterworth pole arrangement for a 2·[passes]-order filter —
 * `q_k = 1/(2·cos((2k+1)π/(4N)))` — SCALED by `userQ/0.7071`, so:
 *  - at the default q the cascade is exactly Butterworth: flat passband, −3 dB AT fc —
 *    `lpf(800, passes = 2)` still means 800 (staggering was the maintainer's C5 decision;
 *    a plain q-per-stage cascade is −6 dB at fc with the knee drifting to ~0.64·fc);
 *  - a resonant userQ keeps its character but the peak COMPOUNDS across stages (raw
 *    engine, documented, no clamp). So does `analog`: every stage gets the full drive,
 *    so `passes = 3, analog = 3` is three helpings of state-dependent damping, not one
 *    steeper filter with the same character.
 *
 * `passes = 1` returns `[userQ]` verbatim — the single-stage path stays bit-identical.
 *
 * ASSOCIATION IS LOAD-BEARING: `userQ · (√2 / 2cosθ)` — the RELATIVE factor first, exactly as
 * the bridge's `passesLadderRel` computes it. Written as `(userQ · √2) / 2cosθ` the two differ
 * by 1 ULP and the optimizer's fused sections stop being bit-identical to the authored tree;
 * `PassesLadderParitySpec` asserts raw bits.
 */
internal fun butterworthQLadder(passes: Int, userQ: Double): DoubleArray {
    val n = coercePasses(passes)
    if (n == 1) {
        return doubleArrayOf(userQ)
    }
    return DoubleArray(n) { k ->
        userQ * (SQRT2 / (2.0 * cos((2.0 * k + 1.0) * PI / (4.0 * n))))
    }
}

object LowPassHighPassFilters {

    /**
     * The whole vowel stage on one channel, in one call: a [ResonatorBank] installed from the bands. A one-shot
     * builder for the specs and `audio_benchmark`; the orbit's stage (`KatalystResonatorEffect`) installs the
     * engine's shared tables into banks of its own instead. A null [floor] is [VOWEL_FLOOR].
     */
    fun createFormant(
        bands: List<VowelBands.Band>,
        mix: Double,
        sampleRate: Double,
        floor: Double? = null,
    ): AudioFilter = installed(table = ResonatorTable.ofVowel(bands), mix = mix, sampleRate = sampleRate, floor = floor ?: VOWEL_FLOOR)

    /** The whole body stage on one channel, in one call; the twin of [createFormant]. A null [floor] is [BODY_FLOOR]. */
    fun createBody(
        bands: List<BodyMaterials.Mode>,
        mix: Double,
        sampleRate: Double,
        floor: Double? = null,
    ): AudioFilter = installed(table = ResonatorTable.ofBody(bands), mix = mix, sampleRate = sampleRate, floor = floor ?: BODY_FLOOR)

    private fun installed(table: ResonatorTable, mix: Double, sampleRate: Double, floor: Double): ResonatorBank =
        ResonatorBank(capacity = table.count, sampleRate = sampleRate).also { it.install(ResonatorConfig(table = table, mix = mix, floor = floor)) }

    /**
     * The body gain rule: a body mode's LINEAR band gain. The SVF bandpass is unity-peak at fc (C2 of the filter
     * unification), so the gain is the plain `10^(db/20)` and `mode.db` IS the peak emphasis in dB, independent of the
     * mode's q. `freq` and `q` go to the bank raw (it guards them). The one copy of the rule
     * ([ResonatorTable.ofBody] calls it).
     */
    internal fun bodyGain(mode: BodyMaterials.Mode): Double {
        // NaN-guard: a non-finite dB is 0 dB, unity gain.
        val safeDb = mode.db.finiteOrZero()

        return 10.0.pow(safeDb / 20.0)
    }

    /**
     * The vowel gain rule, with the **legacy Q-peak fold**: the vowel tables were tuned when the bandpass peaked at
     * `Q` and `band.db` was gain on top of that; the SVF is unity-peak since C2, so the gain folds the peak back in,
     * `10^(db/20) * clampedQ`. EXACT (the SVF scales by k = 1/clampedQ, and k * q = 1) as long as the fold uses the
     * SAME clamp as `computeSvfCoeffs`, so `freq = 730, q = 10, db = 0` still peaks at +20 dB before [VOWEL_TAME]. The
     * bank gets the raw q. Flipping the tables to the absolute-peak convention is a deferred follow-up; do not "clean
     * up" the fold without rewriting every table.
     *
     * [VOWEL_TAME] then scales every band alike, so each vowel keeps its tuned balance. The operand order is the
     * arithmetic the tables were heard with: `(dB factor * q) * tame`. The one copy of the rule
     * ([ResonatorTable.ofVowel] calls it).
     */
    internal fun vowelGain(band: VowelBands.Band): Double {
        // NaN-guards: a non-finite dB is 0 dB; a non-finite q folds the SVF's own fallback, and
        // it must be THAT clamp, or the k * q cancellation the fold rests on is not exact.
        val safeDb = band.db.finiteOrZero()
        val safeQ = clampSvfQ(band.q)

        return 10.0.pow(safeDb / 20.0) * safeQ * VOWEL_TAME
    }

    // --- Implementations ---

    /**
     * Lightweight DC blocker — degenerate first-order HPF with raw pole and
     * no input scaling: `y[ n ] = x[ n ] − x[n-1] + a·y[n-1]`. One mul/sample cheaper than
     * the one-pole HPF (`OnePoleHighpassIgnitor`). Produces a ~2× edge transient on rail-to-rail input; call sites
     * post-distort/post-clip pair this with `ShapingFuncs.softCap()` to bound output to ±1.
     *
     * Coefficient is the raw IIR pole: `a ≈ 1 − 2π·fc/fs`. At `a = 0.995, fs = 44.1k`
     * the −3 dB knee is ~35 Hz; at `a = 0.999`, ~7 Hz. A finite coefficient is clamped to
     * `[0, 0.99999]`; a non-finite one falls back to [DEFAULT_DC_BLOCK_COEFF] (0.995).
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
                yc = out.flushState()
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
                yc = out.flushState()
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
}
