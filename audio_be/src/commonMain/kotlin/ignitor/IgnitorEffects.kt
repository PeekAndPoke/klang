/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.WetDryMix
import io.peekandpoke.klang.audio_be.ShapingFuncs
import io.peekandpoke.klang.audio_be.DistortionShape
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.applyDistortionShape
import io.peekandpoke.klang.audio_be.effects.PhaserCore
import io.peekandpoke.klang.audio_be.filters.DEFAULT_DC_BLOCK_COEFF
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_be.nanGuard
import io.peekandpoke.klang.audio_be.parseDistortionShape
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// Distortion
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Distortion / waveshaping combinator. Processes per-sample.
 *
 * Drives the signal into a nonlinear transfer function, generating new harmonics.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 *
 * **DC blocker is always applied** when the effect is engaged — not just for
 * asymmetric shapes (`diode`, `rectify`) that need it for correctness, but also
 * for symmetric shapes at extreme drive where any input asymmetry causes the
 * output to rail-lock toward `±1` and produce a DC bias that can damage speakers.
 *
 * **Output is bounded to ±1 by a C¹-piecewise soft cap** ([ShapingFuncs.softCap]).
 * Below the linear-region threshold the cap is identity (clean signals
 * untouched); above, the rail-edge transients from the DC blocker's 2× HF gain
 * are smoothly compressed toward ±1 with continuous value + slope at the
 * threshold (no cliff click). The cap is per-stage so chained ignitor
 * combinators (downstream filters, mix points) see a well-behaved bounded
 * input — without it, a heavy-distort branch can dominate a mix.
 *
 * @param amount Drive intensity. 0.0 = bypass, 0.3 = warm saturation, 1.0 = heavy distortion,
 *   2.0+ = extreme. Internally: gain = 10^(amount × 1.2). Default: 0.0 (bypass).
 * @param shape Waveshaper function. Default: "soft" (tanh). Options:
 *   - **Symmetric soft:** "soft" (tanh), "gentle" (soft clip, 2× gain), "softsat" (algebraic, gentler still), "cubic", "exp" (transistor), "sineshaper" (peak-at-unity fold).
 *   - **Symmetric hard / harsh:** "hard" (clip), "zerosquare" (high-gain tanh → square), "chebyshev" (3rd-harmonic), "fold" (sin wavefold), "linearfold" (triangle wavefold).
 *   - **Asymmetric (even harmonics, DC):** "diode", "tube" (shifted-tanh), "asym" (poly), "stompbox" (diode pedal), "rectify" (full-wave).
 */
fun Ignitor.distort(amount: Ignitor, shape: String = "soft", oversampleStages: Int = 0): Ignitor =
    drive(amount).shape(shape, oversampleStages)
// NOTE for this Ignitor-amount door: only the DRIVE half bypasses at amount <= 0 — the shaper
// keeps shaping at unity gain (tanh(1.0) = 0.76, a -2.4 dB peak squash with odd harmonics for
// full-scale input; identity only well below |x| ~ 0.3). A CONSTANT 0 through the Double
// overload below still short-circuits to a true bypass; the DSL door has always behaved like
// this chain. (Ledger W5.)
// ^ The fused legacy DistortIgnitor is DELETED (ledger W5, maintainer decision): every live
// authoring door already built this exact Drive+Shape chain, the fused node was the file's
// third bypass policy (stale DC blocker + oversampler across its gate, plus a group-delay pop
// at every gate flip), and wire trees are never persisted, so nothing can miss it. The chain
// is the documented equivalence ("Equivalent to this.drive(amount).shape(shape, oversample)")
// — same gain, same shaper, same DC blocker and softCap, applied by ShapeIgnitor, which has
// NO bypass and therefore no policy to disagree about. One audible nuance vs the fused node:
// an amount at or crossing 0 (modulated, or a CONSTANT 0 in a legacy wire tree — the Double
// convenience below still short-circuits) now bypasses only the DRIVE; the shaper keeps
// shaping at unity gain. For the default soft shape that is a real squash at full scale
// (tanh(1.0) = 0.76, -2.4 dB + odd harmonics; identity only below |x| ~ 0.3) — the DSL door
// has ALWAYS behaved this way, and the shaper state staying contiguous is the point.

/**
 * Distortion / waveshaping combinator (convenience overload with fixed amount).
 *
 * @param amount Drive intensity. 0.0 = bypass, 0.3 = warm, 1.0 = heavy, 2.0+ = extreme. Default: 0.0.
 * @param shape Waveshaper function. Default: "soft". See [distort] for all options.
 * @param oversampleStages Number of 2x oversampling stages. 0 = off, 1 = 2x, 2 = 4x, etc.
 */
fun Ignitor.distort(amount: Double, shape: String = "soft", oversampleStages: Int = 0): Ignitor {
    if (amount <= 0.0) return this
    return distort(ParamIgnitor("amount", amount), shape, oversampleStages)
}

/**
 * Pre-amplification stage. Boosts signal level without waveshaping.
 *
 * Use before a shape() or distort() to control how hard the signal hits the shaper.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 *
 * @param amount Gain boost intensity. 0.0 = bypass, 0.5 = moderate boost, 1.0 = loud,
 *   2.0+ = extreme. Internally: gain = 10^(amount × 1.2). Default: 0.0 (bypass).
 * @param type Drive type. Default: "linear". Future: "tube", "fet", "tape".
 */
fun Ignitor.drive(amount: Ignitor, type: String = "linear"): Ignitor =
    DriveIgnitor(this, amount, type)

private class DriveIgnitor(
    private val upstream: Ignitor,
    private val amount: Ignitor,
    // Reserved for the future tube/fet/tape dispatch; "linear" is the only implemented type,
    // so nothing stores or reads it today (ledger W12 hoisted the old per-block lowercase()
    // out of generate; storing a lowercased copy per note-on would just move the allocation).
    @Suppress("UNUSED_PARAMETER") type: String,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { work ->
            upstream.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (amt <= 0.0) {
                for (i in ctx.offset until end) {
                    buffer[i] = work[i]
                }
                return@use
            }

            val driveGain = 10.0.pow(amt * 1.2)

            for (i in ctx.offset until end) {
                buffer[i] = (work[i] * driveGain)
            }
        }
    }
}

/**
 * Pre-amplification stage (convenience overload with fixed amount).
 *
 * @param amount Gain boost intensity. 0.0 = bypass, 1.0 = loud. Default: 0.0.
 * @param type Drive type. Default: "linear".
 */
fun Ignitor.drive(amount: Double, type: String = "linear"): Ignitor {
    if (amount <= 0.0) return this
    return drive(ParamIgnitor("amount", amount), type)
}

/**
 * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
 *
 * Unlike [distort], this does not boost the signal before shaping — it only clips
 * whatever amplitude is already there. Use [drive] before shape() for a two-stage chain.
 *
 * **DC blocker is always applied** to guard against rail-lock when the input is
 * already heavily saturated (e.g. after `drive`). See [distort] for the rationale.
 * Output is bounded to ±1 by a C¹-piecewise soft cap ([ShapingFuncs.softCap])
 * — identity in the linear region, smooth saturation above. See [distort].
 *
 * @param shape Waveshaper function. Default: "soft" (tanh). See [distort] for the full
 *   list of options ("soft", "hard", "gentle", "softsat", "cubic", "exp", "sineshaper",
 *   "zerosquare", "chebyshev", "fold", "linearfold", "diode", "tube", "asym", "stompbox",
 *   "rectify").
 */
fun Ignitor.shape(shape: String = "soft", oversampleStages: Int = 0): Ignitor =
    ShapeIgnitor(this, shape, oversampleStages)

private class ShapeIgnitor(
    private val upstream: Ignitor,
    shape: String,
    oversampleStages: Int,
) : Ignitor {
    private val shape: DistortionShape = parseDistortionShape(shape)
    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    // DC blocker pre-softCap. See `Ignitor.distort` for the rationale.
    private val dcBlocker = LowPassHighPassFilters.DcBlocker()

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { work ->
            upstream.generate(work, freqHz, ctx)

            val end = ctx.offset + ctx.length
            val s = shape
            val os = oversampler

            if (os != null) {
                // NaN-guard fused into the per-sample loop — see Oversampler.process KDoc.
                os.process(work, ctx.offset, ctx.length, ctx.scratchBuffers) { w, count ->
                    for (i in 0 until count) {
                        w[i] = applyDistortionShape(s, w[i]).nanGuard()
                    }
                }
            } else {
                for (i in ctx.offset until end) {
                    work[i] = applyDistortionShape(s, work[i]).nanGuard()
                }
            }

            dcBlocker.process(work, ctx.offset, ctx.length)

            for (i in ctx.offset until end) {
                buffer[i] = ShapingFuncs.softCap(work[i])
            }
        }
    }
}

// ResolvedShape and resolveDistortionShape() moved to audio_be/DistortionShape.kt

// ═══════════════════════════════════════════════════════════════════════════════
// BitCrush
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bit-depth reduction (bitcrush) for lo-fi digital sound. Processes per-sample.
 *
 * Symmetric midtread quantizer: `round(x * halfLevels) / halfLevels`, output
 * clamped to `[-1, 1]`. No DC bias, no amplitude inflation. The clamp catches
 * the non-integer `halfLevels` case where a unit input would otherwise map to
 * a grid point outside the input range (e.g. `amount = 1.5` → `hl ≈ 1.414` →
 * raw output `2/1.414 ≈ 1.414`, clamped to `1.0`).
 *
 * Amount is read once per block (control rate). **Bypasses when amount < 1.0** —
 * fewer than 2 levels means the grid step exceeds the input range entirely.
 *
 * @param amount Bit depth. Below 1.0 = bypass. 1.0 = 2 levels (extreme lo-fi),
 *   4.0 = 16 levels, 8.0 = 256 levels, 16.0 = 65536 levels (subtle).
 *   Internally: `levels = 2^amount`. Typical range: 2.0–8.0.
 */
fun Ignitor.crush(amount: Ignitor): Ignitor = CrushIgnitor(this, amount)

private class CrushIgnitor(
    private val upstream: Ignitor,
    private val amount: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { work ->
            upstream.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            val levels = 2.0.pow(amt)
            if (levels < 2.0) {
                for (i in ctx.offset until end) {
                    buffer[i] = work[i]
                }
                return@use
            }

            val halfLevels = levels / 2.0
            for (i in ctx.offset until end) {
                // Midtread symmetric quantizer (round, not floor) — no DC bias.
                // Clamp output to [-1, 1] to catch non-integer halfLevels inflation.
                val q = round(work[i] * halfLevels) / halfLevels
                buffer[i] = q.coerceIn(-1.0, 1.0)
            }
        }
    }
}

/**
 * Bit-depth reduction (convenience overload with fixed amount).
 *
 * @param amount Bit depth. Below 1.0 = bypass. 4.0 = 16 levels (lo-fi),
 *   8.0 = 256 levels. Default: 0.0.
 */
fun Ignitor.crush(amount: Double): Ignitor {
    if (amount < 1.0) return this
    return crush(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Coarse (Sample Rate Reducer)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sample-rate reducer (coarse). Holds a sample value for multiple frames. Processes per-sample.
 *
 * Creates aliased, metallic artifacts by reducing the effective sample rate.
 * Amount is read once per block (control rate). Amounts in (0, 1] are audibly inactive but the
 * hold clock keeps running (an exact take-every-sample copy — ledger W3: contiguity through a
 * modulated crossing); only `amount <= 0` and non-finite values take the true bypass arm, and
 * they HEAL when the amount returns. The first hold is `amount` samples (give or take one for
 * non-dyadic amounts — 1/amount accumulates in floats), like every later hold.
 *
 * @param amount Sample-hold factor. Values <= 1.0 are audibly inactive (see above).
 *   2.0 = every 2nd sample held, 4.0 = every 4th (strong aliasing), 10.0+ = extreme lo-fi.
 *   Typical range: 2.0–8.0. Default: 0.0 (inactive).
 */
private class CoarseIgnitor(
    private val upstream: Ignitor,
    private val amount: Ignitor,
) : Ignitor {
    private var lastValue: Double = 0.0

    // Bootstrapped at 1.0 — "take a sample NOW", the oversampled strip path's shape (ledger
    // W1): the old 0.0 start + `idx == 0` block latch re-armed at note-relative sample
    // `amount` for every power-of-two amount, so a block boundary landing there displaced the
    // hold grid for the REST of the note (live in ATruthWorthLyingFor's coarse(2)); it also
    // made the first hold 2x long. Both die with this bootstrap, and every coarse path in the
    // engine now anchors its grid the same way.
    private var counter: Double = 1.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { work ->
            upstream.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            // Ledger W3: the guard is load-bearing only for amt <= 0 (a negative increment
            // would walk the counter down and hold forever) and for non-finite amounts (a NaN
            // would poison the counter and latch DC for the note's life, an Inf would hold
            // forever — both now read as bypass and HEAL when the amount returns). For amt in
            // (0, 1] the engaged loop below already degenerates to an exact copy, so the S&H
            // clock stays contiguous through the whole authorable range and a modulated amount
            // crossing 1.0 no longer freezes the grid or replays a stale held sample.
            // NaN-guard: the !(x > 0) form is what catches NaN.
            if (!(amt > 0.0) || amt.isInfinite()) {
                for (i in ctx.offset until end) {
                    buffer[i] = work[i]
                }
                return@use
            }

            // coerceAtLeast(1.0): amounts in (0, 1] mean "take every sample" — without the
            // floor the counter would grow unboundedly at increments > 1.
            val invAmt = 1.0 / amt.coerceAtLeast(1.0)

            for (i in ctx.offset until end) {
                if (counter >= 1.0) {
                    // nanGuard mirrors the strip door: a NaN input must not latch into the
                    // held value for `amount` frames.
                    lastValue = work[i].nanGuard()
                    counter -= 1.0
                }
                buffer[i] = lastValue
                counter += invAmt
            }
        }
    }
}

fun Ignitor.coarse(amount: Ignitor): Ignitor = CoarseIgnitor(this, amount)

/**
 * Sample-rate reducer (convenience overload with fixed amount).
 *
 * @param amount Sample-hold factor. Values <= 1.0 = inactive. 4.0 = strong aliasing. Default: 0.0.
 */
fun Ignitor.coarse(amount: Double): Ignitor {
    if (amount <= 1.0) return this
    return coarse(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Phaser
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-stage all-pass cascade phaser with sine LFO modulation. Processes per-sample.
 *
 * Sweeps a series of notch filters through the spectrum, creating the classic
 * "whooshing" or "jet" effect. All params read once per block (control rate).
 * Bypasses when wet <= 0 — the LFO clock keeps running through a bypass, so a wet gap never
 * displaces the sweep. The LFO itself is evaluated at block boundaries (see PhaserCore), so its
 * effective Nyquist is `sampleRate / (2 * blockFrames)` (~187 Hz at 48 kHz / 128); above that the
 * sweep aliases at the block rate. `rate` is deliberately unclamped (raw engine).
 *
 * @param rate LFO speed in Hz. 0.0 = static, 0.5 = slow sweep, 2.0 = moderate,
 *   5.0+ = fast. Typical range: 0.1–5.0. Default: no default (required).
 * @param wet Wet/dry balance under the shared C4 law (correlated branch, p = 2):
 *   0.0 = bit-exact bypass, 0.5 = equal mix, 1.0 = phased only. Typical range: 0.3–1.0.
 * @param center Center frequency of the notch sweep in Hz. Default: 1000.0.
 *   Clamped to [100, 18000]. Typical range: 500–4000.
 * @param sweep Modulation width in Hz — how far the notch sweeps from center.
 *   Default: 1000.0. Clamped to [100, 18000]. Typical range: 500–3000.
 * @param dryFloor Minimum dry coefficient in [0, 1]. Default 0.0 (true crossfade);
 *   1.0 makes the phaser purely additive like the orbit-side phaser.
 */
fun Ignitor.phaser(
    rate: Ignitor,
    wet: Ignitor,
    center: Ignitor = ParamIgnitor("center", 1000.0),
    sweep: Ignitor = ParamIgnitor("sweep", 1000.0),
    dryFloor: Ignitor = ParamIgnitor("dryFloor", 0.0),
): Ignitor = PhaserIgnitor(this, rate, wet, center, sweep, dryFloor)

private class PhaserIgnitor(
    private val upstream: Ignitor,
    private val rate: Ignitor,
    private val wet: Ignitor,
    private val center: Ignitor,
    private val sweep: Ignitor,
    private val dryFloor: Ignitor,
) : Ignitor {
    // Lazy-init: PhaserCore needs sampleRate at construction, but we only see
    // ctx.sampleRate on the first generate() call.
    private var core: PhaserCore? = null

    // True while the cascade holds post-bypass state — cleared on bypass entry (ledger D5).
    private var stateDirty = false

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val phaser = core ?: PhaserCore(PhaserCore.DEFAULT_STAGES, ctx.sampleRate).also { core = it }

        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            // Read every param and advance the LFO clock UNCONDITIONALLY (ledger D1): the sweep
            // is a function of note-relative time, not of how many blocks happened to observe
            // wet > 0, and a stateful param ignitor ticks through its per-block read. The read
            // ORDER (wet, rate, center, sweep, dryFloor) is the pre-D1 order on purpose: a
            // hand-built Kotlin graph may share one stateful instance across two slots, and the
            // slot assignment of its per-block draws is observable (review round 3). The
            // ctx.length guard on the kernel WRITES is defensive, currently unreachable: today a
            // zero-length 0.0 could never reach alphaAt anyway (prepareBlock early-returns at
            // frames <= 0, and the next real block rewrites all three first) — the guard removes
            // the reliance on those two cross-file facts, it does not fix a live bug. A param
            // cannot tick on a window with no samples, so skipping the reads there loses nothing.
            val wetVal = Ignitors.readParam(wet, freqHz, ctx).coerceIn(0.0, 1.0)

            if (ctx.length > 0) {
                phaser.rate = Ignitors.readParam(rate, freqHz, ctx)
                phaser.center = Ignitors.readParam(center, freqHz, ctx)
                phaser.sweep = Ignitors.readParam(sweep, freqHz, ctx)
            }

            phaser.prepareBlock(ctx.length)

            val floorVal = Ignitors.readParam(dryFloor, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (wetVal <= 0.0) {
                // Allpass state is CLEARED on bypass entry (the shimmer's C4.1 policy, ledger D5):
                // resuming on a stale cascade + feedback sample would click at an alignment-
                // dependent boundary. The LFO phase survives — PhaserCore.reset() keeps it. The
                // ctx.length guard is ledger D7: a zero-length window reads a modulated wet as 0.0
                // (deterministic E5 value) and must not get to DECIDE a bypass.
                if (stateDirty && ctx.length > 0) {
                    phaser.reset()
                    stateDirty = false
                }

                for (i in ctx.offset until end) {
                    buffer[i] = input[i]
                }
                return@use
            }

            if (ctx.length > 0) {
                stateDirty = true
            }

            // C4 (filter unification): the shared wet/dry law, correlated branch (p = 2) —
            // an allpass cascade is unit-magnitude and fully correlated, so amplitudes add.
            // floor passed RAW: WetDryMix owns the domain coercion (incl. non-finite -> 0.0),
            // so every door resolves a bad floor the SAME way (bus/strip store raw too)
            val dryC = WetDryMix.dryCoeff(wetVal, floor = floorVal, p = 2)
            val wetC = WetDryMix.wetCoeff(wetVal, p = 2)
            for (i in ctx.offset until end) {
                val dry = input[i]
                val phased = phaser.step(dry) // local name: `wet` is the Ignitor property
                buffer[i] = dry * dryC + phased * wetC
            }
        }
    }
}

/**
 * 4-stage all-pass cascade phaser (convenience overload with fixed values).
 *
 * @param rate LFO speed in Hz. Typical range: 0.1–5.0.
 * @param wet Wet/dry balance (shared C4 law, p = 2): 0.0 = bypass, 1.0 = phased only. Default: 0.5.
 * @param center Center frequency in Hz. Default: 1000.0. Clamped to [100, 18000].
 * @param sweep Modulation width in Hz. Default: 1000.0. Clamped to [100, 18000].
 * @param dryFloor Minimum dry coefficient. Default: 0.0 (true crossfade).
 */
fun Ignitor.phaser(
    rate: Double,
    wet: Double = 0.5,
    center: Double = 1000.0,
    sweep: Double = 1000.0,
    dryFloor: Double = 0.0,
): Ignitor {
    if (wet <= 0.0) return this
    return phaser(
        ParamIgnitor("rate", rate),
        ParamIgnitor("wet", wet),
        ParamIgnitor("center", center),
        ParamIgnitor("sweep", sweep),
        ParamIgnitor("dryFloor", dryFloor),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tremolo
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Amplitude modulation (tremolo) via sine LFO. Processes per-sample.
 *
 * Modulates the volume up and down rhythmically, creating a pulsing effect.
 * Rate and depth are read once per block (control rate). Bypasses when depth <= 0.
 *
 * @param rate LFO speed in Hz. 0.0 = static, 2.0 = gentle pulse, 5.0 = moderate,
 *   10.0+ = fast chopping. Typical range: 1.0–8.0. Default: no default (required).
 * @param depth Modulation intensity. 0.0 = bypass (no tremolo), 0.5 = subtle,
 *   1.0 = full depth (volume drops to zero). Typical range: 0.2–0.8.
 *   Default: no default (required).
 */
fun Ignitor.tremolo(
    rate: Ignitor,
    depth: Ignitor,
): Ignitor = TremoloIgnitor(this, rate, depth)

private class TremoloIgnitor(
    private val upstream: Ignitor,
    private val rate: Ignitor,
    private val depth: Ignitor,
) : Ignitor {
    private var phase: Double = 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val rateVal = Ignitors.readParam(rate, freqHz, ctx)
            val depthVal = Ignitors.readParam(depth, freqHz, ctx)
            val end = ctx.offset + ctx.length
            val phaseInc = (TWO_PI * rateVal) / ctx.sampleRate

            if (depthVal <= 0.0) {
                // The LFO is a clock (ledger W2, the D1/D2 shape one file over): it advances
                // through a depth gap by the whole window, so a modulated depth dipping to 0
                // resumes exactly where an ungated LFO would be — not at a block-quantised
                // stale phase. A zero-length window advances nothing by construction.
                phase = (phase + phaseInc * ctx.length).wrapPhase(TWO_PI)

                for (i in ctx.offset until end) {
                    buffer[i] = input[i]
                }
                return@use
            }

            for (i in ctx.offset until end) {
                // wrapPhase over the bare subtract (ledger W2): identical in range, and a
                // non-finite or negative rate can no longer kill the phase for the note's life.
                // A non-finite rate reads as phase 0 every sample = a steady 1 - depth/2 gain
                // (a level change, not silence), healing the moment the rate returns.
                phase = (phase + phaseInc).wrapPhase(TWO_PI)

                val lfoNorm = (sin(phase) + 1.0) * 0.5
                val gain = 1.0 - (depthVal * (1.0 - lfoNorm))
                buffer[i] = (input[i] * gain)
            }
        }
    }
}

/**
 * Amplitude modulation / tremolo (convenience overload with fixed values).
 *
 * @param rate LFO speed in Hz. Typical range: 1.0–8.0.
 * @param depth Modulation intensity. 0.0 = bypass, 1.0 = full depth.
 */
fun Ignitor.tremolo(
    rate: Double,
    depth: Double,
): Ignitor {
    if (depth <= 0.0) return this
    return tremolo(ParamIgnitor("rate", rate), ParamIgnitor("depth", depth))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shimmer — granular pitch-shift cloud with feedback (Aetherizer-style)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Granular shimmer effect — short overlapping grains read back from a ring buffer at
 * pitched rates, with a feedback loop through a tone lowpass.
 *
 * `wet`, `feedback`, `tone` and `dryFloor` are read once per block (control rate) with no
 * smoothing: a fast-modulated wet steps the mix gain at the block rate, and a modulated tone
 * switches the feedback-LPF coefficient at block boundaries — raw engine, no hidden ramps. The
 * grain machinery itself is sample-anchored (the first grain fires on the note's first sample).
 *
 * @param wet Wet/dry balance. 0.0 = bit-exact bypass regardless of feedback, 1.0 = cloud only.
 * Mix: the shared wet/dry law, equal-POWER branch (p = 1) — the pitch-shifted tail is
 * decorrelated from the dry, so powers add and the level holds across the knob.
 * @param dryFloor Minimum dry coefficient in [0, 1]. Default 0.0 (true crossfade).
 * @param feedback Wet → grain-buffer feedback. 0.0 = single pass, 0.9 = long cascading tails.
 *   Hard-clamped to 0.95 for stability.
 * @param tone One-pole LPF cutoff (Hz) in the feedback path. Lower = darker. Clamped to [200, 16000].
 * @param pitches Semitone transpositions for grains. Each grain is assigned a pitch from this
 *   list in round-robin order. Default: `[0, 7, 12]` (root + fifth + octave).
 */
fun Ignitor.shimmer(
    wet: Ignitor,
    feedback: Ignitor,
    tone: Ignitor,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
    dryFloor: Ignitor = ParamIgnitor("dryFloor", 0.0),
): Ignitor = ShimmerIgnitor(this, wet, feedback, tone, pitches, dryFloor)

// NOTE: shimmer is WIP — internal grain bookkeeping may still change. Keep the
// per-block logic readable; revisit perf rules (audio/ref/performance.md) once
// the grain scheduler is finalised.
private class ShimmerIgnitor(
    private val upstream: Ignitor,
    private val wet: Ignitor,
    private val feedback: Ignitor,
    private val tone: Ignitor,
    pitches: List<Double>,
    private val dryFloor: Ignitor,
) : Ignitor {
    /** True once the grain engine has produced state that a bypass must clear. */
    private var stateDirty = false

    private val ringSize = 96_000
    private val ring = AudioBuffer(ringSize)
    private var writePos: Int = 0

    private val maxGrains = 8
    private val grainActive = BooleanArray(maxGrains)
    private val grainReadPos = DoubleArray(maxGrains)
    private val grainRate = DoubleArray(maxGrains)
    private val grainElapsed = IntArray(maxGrains)
    private val grainTotal = IntArray(maxGrains)

    private val intervalRates = DoubleArray(pitches.size) { 2.0.pow(pitches[it] / 12.0) }
    private var nextIntervalIdx: Int = 0

    private val grainsPerSecond = 12.0
    private val grainSizeSec = 0.150
    private var samplesUntilNextGrain: Int = 0

    private var feedbackTap: Double = 0.0
    private var lpfState: Double = 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val wetVal = Ignitors.readParam(wet, freqHz, ctx).coerceIn(0.0, 1.0)
            val fbVal = Ignitors.readParam(feedback, freqHz, ctx).coerceIn(0.0, 0.95)
            val toneVal = Ignitors.readParam(tone, freqHz, ctx).coerceIn(200.0, 16000.0)
            // Read (tick) dryFloor unconditionally too — the D1 rule, all four slots.
            val floorVal = Ignitors.readParam(dryFloor, freqHz, ctx)
            val end = ctx.offset + ctx.length

            // C4: wet == 0 IS bypass, regardless of feedback — bit-identical passthrough is
            // the wet(0) contract. State is CLEARED on bypass entry (decided in the C4.1
            // review): a modulated wet dipping to 0 must not freeze a stale tail and
            // resurrect it later, detached from wall time.
            if (wetVal <= 0.0) {
                // The ctx.length guard is ledger D7: a zero-length window reads a modulated wet
                // as 0.0 (the deterministic E5 value) and must not get to DECIDE a bypass.
                if (stateDirty && ctx.length > 0) {
                    ring.fill(0.0)
                    for (g in 0 until maxGrains) grainActive[g] = false
                    // Ledger D6: the grain SCHEDULER resets with the grain state — a resume must
                    // not keep a framing-dependent countdown remainder or round-robin position,
                    // which would shift every later grain onset (and possibly the pitch order)
                    // by where the bypass happened to land in a block. writePos resets too: a
                    // grain's lookback WRAPS around the ring seam, and after the wrap the read
                    // index is absolute — a surviving write head (itself block-quantised at
                    // bypass entry) would misalign the resumed cloud's warmup against the seam.
                    samplesUntilNextGrain = 0
                    nextIntervalIdx = 0
                    writePos = 0
                    feedbackTap = 0.0
                    lpfState = 0.0
                    stateDirty = false
                }
                for (i in ctx.offset until end) {
                    buffer[i] = input[i]
                }
                return@use
            }

            if (ctx.length > 0) {
                stateDirty = true
            }

            val sampleRate = ctx.sampleRate
            val grainPeriodSamples = (sampleRate / grainsPerSecond).toInt().coerceAtLeast(1)
            val grainTotalSamples = (sampleRate * grainSizeSec).toInt().coerceAtLeast(1)
            val invGrainTotal = 1.0 / grainTotalSamples

            val lpfA = exp(-TWO_PI * toneVal / sampleRate)
            val lpfOneMinusA = 1.0 - lpfA

            // C4 (filter unification): shared wet/dry law, DEcorrelated branch (p = 1) — the
            // pitch-shifted grain tail carries no phase relation to the dry, powers add.
            // floor passed RAW: WetDryMix owns the domain coercion (see the phaser above)
            val dryC = WetDryMix.dryCoeff(wetVal, floor = floorVal, p = 1)
            val wetC = WetDryMix.wetCoeff(wetVal, p = 1)

            for (i in ctx.offset until end) {
                val dry = input[i]

                var write = dry + feedbackTap * fbVal
                if (write > 2.0) write = 2.0 else if (write < -2.0) write = -2.0
                ring[writePos] = write
                writePos++
                if (writePos >= ringSize) writePos = 0

                if (samplesUntilNextGrain <= 0) {
                    samplesUntilNextGrain = grainPeriodSamples
                    val rate = intervalRates[nextIntervalIdx]
                    nextIntervalIdx = (nextIntervalIdx + 1) % intervalRates.size

                    var slot = -1
                    for (g in 0 until maxGrains) {
                        if (!grainActive[g]) {
                            slot = g; break
                        }
                    }
                    if (slot >= 0) {
                        val lookback = rate * grainTotalSamples
                        var start = writePos - lookback
                        while (start < 0.0) start += ringSize
                        grainReadPos[slot] = start
                        grainRate[slot] = rate
                        grainElapsed[slot] = 0
                        grainTotal[slot] = grainTotalSamples
                        grainActive[slot] = true
                    }
                }
                samplesUntilNextGrain--

                var wetSample = 0.0 // local name: `wet` is the Ignitor property
                for (g in 0 until maxGrains) {
                    if (!grainActive[g]) continue

                    val pos = grainReadPos[g]
                    val idx1 = pos.toInt()
                    val frac = pos - idx1
                    val idx2 = if (idx1 + 1 >= ringSize) 0 else idx1 + 1
                    val sample = ring[idx1] + frac * (ring[idx2] - ring[idx1])

                    val phase = grainElapsed[g] * invGrainTotal
                    val win = 0.5 - 0.5 * cos(TWO_PI * phase)
                    wetSample += sample * win

                    var nextPos = pos + grainRate[g]
                    while (nextPos >= ringSize) nextPos -= ringSize
                    grainReadPos[g] = nextPos
                    grainElapsed[g]++
                    if (grainElapsed[g] >= grainTotal[g]) grainActive[g] = false
                }

                lpfState = (lpfOneMinusA * wetSample + lpfA * lpfState).flushDenormal()
                feedbackTap = lpfState

                buffer[i] = (dry * dryC + wetSample * wetC)
            }
        }
    }
}

/**
 * Granular shimmer (convenience overload with fixed values).
 *
 * @param wet Wet/dry balance: 0.0 = bypass (regardless of feedback — the C4 contract),
 *   1.0 = cloud only. Default: 0.5.
 * @param feedback Cascade feedback. 0.0 = single pass, 0.9 = long tails. Default: 0.5.
 * @param tone Feedback-path LPF cutoff in Hz. Default: 4000.0.
 * @param pitches Semitone transpositions for grains. Default: `[0, 7, 12]`.
 * @param dryFloor Minimum dry coefficient. Default: 0.0 (true crossfade).
 */
fun Ignitor.shimmer(
    wet: Double = 0.5,
    feedback: Double = 0.5,
    tone: Double = 4000.0,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
    dryFloor: Double = 0.0,
): Ignitor {
    if (wet <= 0.0) return this
    return shimmer(
        ParamIgnitor("wet", wet),
        ParamIgnitor("feedback", feedback),
        ParamIgnitor("tone", tone),
        pitches,
        ParamIgnitor("dryFloor", dryFloor),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DC Blocker
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One-pole highpass DC blocker — degenerate first-order HPF with raw pole.
 *
 * Removes DC offset accumulation from waveshaping, feedback, and asymmetric clipping.
 * Essential after distortion shapes that produce asymmetric output (diode, rectify).
 *
 * Cutoff approximation: `fc ≈ (1 − a)·fs / (2π)`. Reference points:
 *   - `coefficient = 0.995` → ~35 Hz @ 44.1k, ~38 Hz @ 48k (good for feedback paths)
 *   - `coefficient = 0.999` → ~7 Hz  @ 44.1k, ~7.6 Hz @ 48k (good for master output)
 *
 * Higher coefficient = lower cutoff = less low-frequency content removed.
 *
 * Implementation delegates to [LowPassHighPassFilters.DcBlocker]; see that class for
 * the dedup history (this used to be one of 9 inline copies before 2026-04-29).
 *
 * @param coefficient Raw IIR pole. NaN/Inf or out-of-range values fall back to 0.995. Default: 0.995.
 */
fun Ignitor.dcBlock(coefficient: Double = DEFAULT_DC_BLOCK_COEFF): Ignitor =
    DcBlockIgnitor(this, coefficient)

private class DcBlockIgnitor(
    private val upstream: Ignitor,
    coefficient: Double,
) : Ignitor {
    private val dcBlocker = LowPassHighPassFilters.DcBlocker(coefficient)

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)
            dcBlocker.process(input, buffer, ctx.offset, ctx.length)
        }
    }
}
