/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.SAT_STATE_SCALE
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.bilinearK
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.filters.diodePairResistanceApprox
import io.peekandpoke.klang.audio_be.filters.onePoleLpfCoeff
import io.peekandpoke.klang.audio_be.flushState
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIVE_PER_ANALOG
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tan

// ═══════════════════════════════════════════════════════════════════════════════
// SVF Mode enum — determines which output tap is used
// ═══════════════════════════════════════════════════════════════════════════════

/** SVF filter mode — selects the output tap from the state variable filter topology. */
enum class SvfMode {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
    NOTCH,
}

// ═══════════════════════════════════════════════════════════════════════════════
// Filter envelope parameters (optional, used by all filter combinators)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Optional ADSR-style envelope that modulates filter cutoff at control rate (once per block).
 *
 * When applied, the effective cutoff becomes: `baseCutoff * 2^(depth/12 * envValue)` —
 * depth is SEMITONES (C3 of the filter unification; +12 doubles the cutoff at full
 * envelope, negative sweeps down, no dead zone) and `envValue` is 0.0..1.0 from the
 * envelope shape. `depth = 0.0` stays the exact no-envelope identity.
 */
data class FilterEnvDef(
    // The per-field defaults below are NOT the surface defaults, and deliberately so: this is the
    // RESOLVED struct the runtime reads, not a door. Every production construction names all five
    // (`IgnitorDslRuntime.filterEnvDef`, which fills from
    // `audio_bridge/constants/FilterEnvelopeDefaults.kt`), so these values are reachable only
    // through [NONE] and through tests that want a partial shape. `depth = 0.0` IS [NONE]: it is
    // the off switch this class is read through, so it must not be moved to the surface's 7
    // semitones. The other four keep the neutral values they were written with (three zeros and
    // a unity sustain) for the same reason: with `depth = 0` nothing reads them, and giving them
    // the surface constants would suggest they were the source of truth, which
    // `FilterEnvelopeDefaults.kt` is.
    val depth: Double = 0.0,
    val attackSec: Double = 0.0,
    val decaySec: Double = 0.0,
    val sustainLevel: Double = 1.0,
    val releaseSec: Double = 0.0,
) {
    companion object {
        val NONE = FilterEnvDef()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SVF Filter — unified implementation for all modes
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * State Variable Filter (SVF) combinator.
 *
 * The TPT/Zavalishin canonical Cytomic form computes lowpass, highpass, bandpass, and
 * notch simultaneously; [mode] selects which output is used. Each instance creates
 * per-voice filter state in its closure. Optional [env] modulates cutoff over the
 * voice's lifetime — when active, coefficients are computed at block start AND end
 * and Bresenham-style linearly interpolated per sample to avoid the ~187 Hz block-rate
 * stair-stepping that per-block-only recompute would produce.
 *
 * **How this envelope differs from the voice strip's, on TWO counts, both open under decision D3
 * of `docs/tasks/builtin-instruments.md`.**
 *
 *  - **The law.** [computeFilterEnvelope] has no curve term at all, so every segment here is a
 *    straight LINE. The strip's filter envelope runs through `EnvelopeCalc` with
 *    `AdsrCurve.Default`, which is Exponential with K = 3, because `VoiceFactory` builds its
 *    `Voice.Envelope` without the three curve arguments. Measured at `env = 24`, the two shapes
 *    are up to 806 cents apart at the same instant (RMS 256 cents on a pluck, 512 on a pad).
 *    This is the BIGGER of the two differences by 4x to 100x.
 *  - **The sampling.** The strip computes its envelope once per block and lets
 *    `BaseSvf.setCutoff` ramp the coefficients over `FILTER_SMOOTH_SAMPLES` (32) samples and then
 *    HOLD; this node computes the envelope at block start and at block end and interpolates the
 *    coefficients across the WHOLE block.
 *
 * The two agree on the ENDPOINTS and on the stage times. Which law and which sampling phase 3
 * keeps is D3's to answer and nothing here anticipates it: a node with `env.depth == 0.0` never
 * enters this path at all, which is why step 3a can be bit-identical while D3 is open.
 *
 * Optional [humanize] is the per-voice analog character the voice strip gets from
 * `VoiceFactory`: a fixed cutoff tolerance and a slow drift lane, both drawn once per voice.
 * `null` is no humanization and renders bit-for-bit what this filter rendered without the
 * feature. See [FilterHumanization].
 *
 * Coefficient math is shared with `BaseSvf` via `computeSvfCoeffs`. NaN/Inf-safe
 * cutoff (via `bilinearK`); Q is clamped to `[0.1, 200.0]` with `isFinite` fallback.
 *
 * Mode dispatch is hoisted out of the per-sample loop into one specialized loop
 * body per tap. See `audio/ref/performance.md` for why class form (vs SAM lambda
 * + closure capture) matters in Kotlin/JS.
 *
 * @param mode Filter type: [SvfMode.LOWPASS], [SvfMode.HIGHPASS], [SvfMode.BANDPASS], or [SvfMode.NOTCH].
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Typical: 200–8000 for LP, 100–2000 for HP, 300–5000 for BP/Notch.
 * @param q Resonance / Q factor. 0.707 = flat (Butterworth), higher = sharper peak.
 *   Clamped to [0.1, 200.0]. Default: 0.707. Typical range: 0.5–10.0.
 * BPF tap is unity-peak at fc since C2 (`k·v1`) — q is a pure width control;
 * `bandpass(q=10)` gets narrower, not louder.
 * @param env Optional ADSR envelope to modulate cutoff over time. Default: none.
 */
fun Ignitor.svf(
    mode: SvfMode,
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = SvfIgnitor(this, mode, cutoffHz, q, env, analog, humanize)

private class SvfIgnitor(
    private val upstream: Ignitor,
    private val mode: SvfMode,
    private val cutoffHz: Ignitor,
    private val q: Ignitor,
    private val env: FilterEnvDef,
    private val analog: Ignitor,
    private val humanize: FilterHumanization?,
) : Ignitor {
    // Integrator state.
    private var ic1eq: Double = 0.0
    private var ic2eq: Double = 0.0

    // Coefficient buffers (start-of-block, plus end-of-block when env is active).
    private val coefs = SvfCoeffs()
    private val coefsEnd = SvfCoeffs()
    private var initialized: Boolean = false
    private val hasEnv: Boolean = env.depth != 0.0
    private val hasDrift: Boolean = humanize?.hasDrift == true

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val baseCutoff = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val qVal = Ignitors.readParam(q, freqHz, ctx)
            val analogVal = Ignitors.readParam(analog, freqHz, ctx).coerceAtLeast(0.0)
            // Per-voice analog humanization, both 1.0 when the node has none, and `x * 1.0` is
            // exactly `x` for every double, so a node without it renders bit-for-bit what it
            // rendered before these two lines existed. The ORDER of the two multiplies is the
            // voice strip's: `FilterModRenderer` multiplies the envelope's cutoff by the block's
            // drift and `BaseSvf.setCutoff` then multiplies by the fixed tolerance.
            val driftMul = humanize?.blockDriftMultiplier(ctx) ?: 1.0
            val offsetMul = humanize?.cutoffOffsetMul ?: 1.0
            val saturate = analogVal > 0.0 && (mode == SvfMode.LOWPASS || mode == SvfMode.HIGHPASS)
            val driveScale = analogVal * FILTER_DRIVE_PER_ANALOG
            val sr = ctx.sampleRate.toDouble()
            val length = ctx.length

            // Per-sample coefficient deltas (Bresenham accumulator). When env is off they're
            // zero and the inner loop just adds zero each sample — no measurable cost.
            // `g` is only needed by the saturated path (analog-style state-dependent damping); the
            // linear branches just `g += 0.0` per sample.
            var a1: Double
            var a2: Double
            var a3: Double
            var k: Double
            var g: Double
            var a1Step = 0.0
            var a2Step = 0.0
            var a3Step = 0.0
            var kStep = 0.0
            var gStep = 0.0

            if (hasEnv) {
                val envStart = computeFilterEnvelope(
                    ctx, env.attackSec, env.decaySec, env.sustainLevel, env.releaseSec,
                    sampleOffsetWithinBlock = 0,
                )
                val envEnd = computeFilterEnvelope(
                    ctx, env.attackSec, env.decaySec, env.sustainLevel, env.releaseSec,
                    sampleOffsetWithinBlock = length,
                )
                // C3 (filter unification): envelope depth is SEMITONES — the sweep is
                // pitch-linear (cutoff = base * 2^(depth/12 * env)), negative depth sweeps
                // down symmetrically, and there is no dead zone anywhere.
                val cutoffStart = baseCutoff * 2.0.pow(env.depth / 12.0 * envStart) * driftMul * offsetMul
                val cutoffEnd = baseCutoff * 2.0.pow(env.depth / 12.0 * envEnd) * driftMul * offsetMul

                computeSvfCoeffs(cutoffStart, qVal, sr, coefs)
                computeSvfCoeffs(cutoffEnd, qVal, sr, coefsEnd)

                a1 = coefs.a1; a2 = coefs.a2; a3 = coefs.a3; k = coefs.k; g = coefs.g

                if (length > 0) {
                    val invLen = 1.0 / length
                    a1Step = (coefsEnd.a1 - coefs.a1) * invLen
                    a2Step = (coefsEnd.a2 - coefs.a2) * invLen
                    a3Step = (coefsEnd.a3 - coefs.a3) * invLen
                    kStep = (coefsEnd.k - coefs.k) * invLen
                    gStep = (coefsEnd.g - coefs.g) * invLen
                }
                initialized = true
            } else if (!initialized || hasDrift || cutoffHz !is ParamIgnitor || q !is ParamIgnitor) {
                // `hasDrift` is false without a lane, so the cheap latch below is untouched for
                // every node that does not humanize; with one, the cutoff moves every block and
                // there is nothing to latch.
                computeSvfCoeffs(baseCutoff * driftMul * offsetMul, qVal, sr, coefs)
                a1 = coefs.a1; a2 = coefs.a2; a3 = coefs.a3; k = coefs.k; g = coefs.g
                initialized = true
            } else {
                a1 = coefs.a1; a2 = coefs.a2; a3 = coefs.a3; k = coefs.k; g = coefs.g
            }

            val end = ctx.offset + length
            // Mode is fixed for the lifetime of this Ignitor — branch once per block,
            // not per sample. LOWPASS/HIGHPASS additionally branch on `saturate` (analog>0)
            // to switch between the linear closed-form math and the analog-style state-dependent
            // damping path (per-sample diode-pair polynomial + explicit-feedback solve).
            when (mode) {
                SvfMode.LOWPASS -> {
                    if (saturate) {
                        for (i in ctx.offset until end) {
                            val v0 = input[i]
                            val tCfb = diodePairResistanceApprox(ic1eq * SAT_STATE_SCALE) - 1.0
                            val kEff = k + 2.0 * driveScale * tCfb
                            val kPlusG = kEff + g
                            val vHp = (v0 - kPlusG * ic1eq - ic2eq) / (1.0 + g * kPlusG)
                            val vBp = g * vHp + ic1eq
                            val vLp = g * vBp + ic2eq
                            ic1eq = (2.0 * vBp - ic1eq).flushState()
                            ic2eq = (2.0 * vLp - ic2eq).flushState()
                            buffer[i] = vLp
                            a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                        }
                    } else {
                        for (i in ctx.offset until end) {
                            val v0 = input[i]
                            val v3 = v0 - ic2eq
                            val v1 = a1 * ic1eq + a2 * v3
                            val v2 = ic2eq + a2 * ic1eq + a3 * v3
                            ic1eq = (2.0 * v1 - ic1eq).flushState()
                            ic2eq = (2.0 * v2 - ic2eq).flushState()
                            buffer[i] = v2
                            a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                        }
                    }
                }

                SvfMode.HIGHPASS -> {
                    if (saturate) {
                        for (i in ctx.offset until end) {
                            val v0 = input[i]
                            val tCfb = diodePairResistanceApprox(ic1eq * SAT_STATE_SCALE) - 1.0
                            val kEff = k + 2.0 * driveScale * tCfb
                            val kPlusG = kEff + g
                            val vHp = (v0 - kPlusG * ic1eq - ic2eq) / (1.0 + g * kPlusG)
                            val vBp = g * vHp + ic1eq
                            val vLp = g * vBp + ic2eq
                            ic1eq = (2.0 * vBp - ic1eq).flushState()
                            ic2eq = (2.0 * vLp - ic2eq).flushState()
                            buffer[i] = vHp
                            a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                        }
                    } else {
                        for (i in ctx.offset until end) {
                            val v0 = input[i]
                            val v3 = v0 - ic2eq
                            val v1 = a1 * ic1eq + a2 * v3
                            val v2 = ic2eq + a2 * ic1eq + a3 * v3
                            ic1eq = (2.0 * v1 - ic1eq).flushState()
                            ic2eq = (2.0 * v2 - ic2eq).flushState()
                            buffer[i] = v0 - k * v1 - v2
                            a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                        }
                    }
                }

                SvfMode.BANDPASS -> {
                    // C2 (filter unification): k * v1 = unity peak at fc (k = 1/clampedQ) —
                    // q is a pure width control, matching SvfBPF and the fused EqCore
                    // BANDPASS arm bit-for-bit. Both env-path coefficient sets share one
                    // per-block q, so kStep is structurally 0 — no mid-ramp mismatch.
                    for (i in ctx.offset until end) {
                        val v0 = input[i]
                        val v3 = v0 - ic2eq
                        val v1 = a1 * ic1eq + a2 * v3
                        val v2 = ic2eq + a2 * ic1eq + a3 * v3
                        ic1eq = (2.0 * v1 - ic1eq).flushState()
                        ic2eq = (2.0 * v2 - ic2eq).flushState()
                        buffer[i] = k * v1
                        a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                    }
                }

                SvfMode.NOTCH -> {
                    for (i in ctx.offset until end) {
                        val v0 = input[i]
                        val v3 = v0 - ic2eq
                        val v1 = a1 * ic1eq + a2 * v3
                        val v2 = ic2eq + a2 * ic1eq + a3 * v3
                        ic1eq = (2.0 * v1 - ic1eq).flushState()
                        ic2eq = (2.0 * v2 - ic2eq).flushState()
                        buffer[i] = v0 - k * v1
                        a1 += a1Step; a2 += a2Step; a3 += a3Step; k += kStep; g += gStep
                    }
                }
            }
        }
    }
}

/**
 * SVF filter with constant cutoff and Q (convenience overload).
 *
 * @param mode Filter type: LOWPASS, HIGHPASS, BANDPASS, or NOTCH.
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.svf(
    mode: SvfMode,
    cutoffHz: Double,
    q: Double = 0.707,
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Double = 0.0,
): Ignitor = svf(
    mode, ParamIgnitor("cutoffHz", cutoffHz), ParamIgnitor("q", q), env,
    ParamIgnitor("analog", analog),
)

// ═══════════════════════════════════════════════════════════════════════════════
// Convenience wrappers — delegates to svf() with the appropriate mode
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Staggers the per-stage q of a `passes` cascade (C5): stage k reads `userQ · ladderRel[k]`.
 *
 * The two arms mirror `IgnitorDslOptimizer.expandPasses` EXACTLY, which is what keeps the
 * fused and the chained door bit-identical: a literal folds into the value on both sides
 * (no `safeOut` on either), and anything else goes through [times] on both sides (so both
 * get the same block-constant fold, the same `safeOut` scrubbing and the same
 * `controlRateValueOrNull` contract). Folding a [ParamIgnitor]'s value here instead would skip
 * the `safeOut` the fused door applies, so a non-finite q would land on the SVF's Butterworth
 * 0.7071 fallback on one door and on the scrubbed value's clamp on the other: the 0.1 floor for
 * NaN and -Inf, the 200 ceiling for +Inf (safeOut clamps an infinity to a finite SAFE_MAX). Same
 * program, two filters.
 *
 * Where a non-finite q still comes from, since 2026-09-19: NOT from `oscParams`, whose values the
 * `IgnitorDsl.Param` leaf reads as unset when they are non-finite (`IgnitorDslRuntime`). Two live
 * routes remain, which is why this is not dead code (both verified by a caller search and a
 * render on 2026-09-19):
 *
 *  - an authored `IgnitorDsl.Param` whose DEFAULT is non-finite. A default is the instrument's own
 *    declaration and is deliberately not scrubbed; `IgnitorDslOptimizerRenderSpec`'s C5 rows drive
 *    exactly this.
 *  - ARITHMETIC in a q expression. `Plus` and `Minus` are clamp-free by contract (see
 *    `PlusIgnitor`), so two finite operands can overflow: `Osc.param("a", 1e308).plus(...)` as a q
 *    renders sample for sample what a `+Infinity` q renders.
 *
 * A [ParamIgnitor] that engine code constructs directly (the `Double` overloads of [svf] and its
 * wrappers) would be a third, but no production caller does that today: `scaledBy` has exactly two
 * callers, both in `IgnitorDslRuntime`'s passes cascade and both fed `q.noMod()`, and a voice's
 * `FilterDef` q never comes near here at all (it becomes an `AudioFilter` through
 * `LowPassHighPassFilters.createLPF`/`createHPF`; the ignitor package does not reference
 * `FilterDef`). An earlier version of this KDoc claimed that `FilterDef` route and was wrong.
 */
internal fun Ignitor.scaledBy(factor: Double): Ignitor = when {
    factor == 1.0 -> this
    this is ConstantIgnitor -> ConstantIgnitor(value * factor)
    else -> this * ConstantIgnitor(factor)
}

/**
 * Lowpass filter — lets low frequencies through, dulls the highs.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 200–8000.
 * @param q Resonance. 0.707 = flat (Butterworth), higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.lowpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(SvfMode.LOWPASS, cutoffHz, q, env, analog, humanize)

/**
 * Lowpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 200–8000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Analog character amount. Default: 0 (clean linear). >0 engages OB-X-style state-dependent damping.
 */
fun Ignitor.lowpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(SvfMode.LOWPASS, cutoffHz, q, env, analog)

/**
 * Highpass filter — lets high frequencies through, removes the bottom.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 80–2000.
 * @param q Resonance. 0.707 = flat, higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.highpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(SvfMode.HIGHPASS, cutoffHz, q, env, analog, humanize)

/**
 * Highpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 80–2000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Analog character amount. Default: 0 (clean linear). >0 engages OB-X-style state-dependent damping.
 */
fun Ignitor.highpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(SvfMode.HIGHPASS, cutoffHz, q, env, analog)

/**
 * Bandpass filter — keeps only a frequency band, removes everything above and below.
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the pass band (peak at fc is unity since C2). Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.bandpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(SvfMode.BANDPASS, cutoffHz, q, env, analog, humanize)

/**
 * Bandpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the pass band (peak at fc is unity since C2). Default: 0.707. Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Reserved — currently a no-op (BP saturation not implemented).
 */
fun Ignitor.bandpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(SvfMode.BANDPASS, cutoffHz, q, env, analog)

/**
 * Notch (band-reject) filter — removes one frequency band, keeps everything else.
 *
 * @param cutoffHz Center frequency of the notch in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the notch. Higher = narrower cut. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.notch(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(SvfMode.NOTCH, cutoffHz, q, env, analog, humanize)

/**
 * Notch (band-reject) filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the notch. Higher = narrower cut. Default: 0.707. Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Reserved — currently a no-op.
 */
fun Ignitor.notch(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(SvfMode.NOTCH, cutoffHz, q, env, analog)

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Lowpass (for warmth / simple smoothing)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple one-pole lowpass filter for warmth or smoothing. Processes per-sample.
 *
 * Gentler slope than the SVF (6 dB/oct vs 12 dB/oct). Good for subtle tone shaping.
 * Cutoff is read once per block (control rate).
 *
 * Same matched-Z one-pole leaky integrator as `LowPassHighPassFilters.OnePoleLPF` —
 * coefficient math is shared via `onePoleLpfCoeff`. See that file for review notes.
 *
 * Class form (not SAM lambda) so the integrator state lives in a class field rather
 * than a closure-captured `var` (Kotlin/JS ObjectRef). Hot loop snapshots state into
 * a local for register-fast access.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Lower = darker/warmer, higher = more transparent. Typical: 1000–8000.
 */
private class OnePoleLowpassIgnitor(
    private val upstream: Ignitor,
    private val cutoffHz: Ignitor,
) : Ignitor {
    private var y: Double = 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val fc = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val a = onePoleLpfCoeff(fc, ctx.sampleRate.toDouble())

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                y += a * (input[i] - y)
                y = y.flushState()
                buffer[i] = y
            }
        }
    }
}

fun Ignitor.onePoleLowpass(cutoffHz: Ignitor): Ignitor = OnePoleLowpassIgnitor(this, cutoffHz)

/**
 * One-pole lowpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 1000–8000.
 */
fun Ignitor.onePoleLowpass(cutoffHz: Double): Ignitor = onePoleLowpass(ParamIgnitor("cutoffHz", cutoffHz))

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Highpass
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple one-pole highpass filter. Processes per-sample.
 *
 * Gentler slope than the SVF (6 dB/oct). Good for removing low-end rumble.
 * Cutoff is read once per block (control rate).
 *
 * Same canonical bilinear topology as `LowPassHighPassFilters.OnePoleHPF` — see that
 * file's header for the review history and topology rationale. Class form (not SAM
 * lambda) so state stays in class fields, snapshotted into locals for the hot loop.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Frequencies below this are attenuated. Typical: 30–500.
 */
private class OnePoleHighpassIgnitor(
    private val upstream: Ignitor,
    private val cutoffHz: Ignitor,
) : Ignitor {
    private var y: Double = 0.0
    private var xPrev: Double = 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val fc = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val k = bilinearK(fc, ctx.sampleRate.toDouble())
            val invOnePlusK = 1.0 / (1.0 + k)
            val b0 = invOnePlusK
            val a1 = (1.0 - k) * invOnePlusK

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                val x = input[i]
                y = b0 * (x - xPrev) + a1 * y
                y = y.flushState()
                xPrev = x
                buffer[i] = y
            }
        }
    }
}

fun Ignitor.onePoleHighpass(cutoffHz: Ignitor): Ignitor = OnePoleHighpassIgnitor(this, cutoffHz)

/**
 * One-pole highpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 30–500.
 */
fun Ignitor.onePoleHighpass(cutoffHz: Double): Ignitor = onePoleHighpass(ParamIgnitor("cutoffHz", cutoffHz))

// ═══════════════════════════════════════════════════════════════════════════════
// Formant Filter (parallel bandpass bank)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Formant filter — parallel bandpass filter bank for vowel synthesis.
 *
 * Sums multiple SVF bandpass filters, each at a different frequency with its own Q and gain.
 * Use to create vowel sounds ("ah", "ee", "oo") or instrument body resonances.
 *
 * @param bands List of [FormantBand] specifications, each with freq (Hz), q, and db (gain).
 *   Typical vowel: 3–5 bands between 300–3500 Hz with Q of 5–15.
 */
fun Ignitor.formant(bands: List<FormantBand>): Ignitor = FormantIgnitor(this, bands)

private class FormantIgnitor(
    private val upstream: Ignitor,
    bands: List<FormantBand>,
) : Ignitor {
    private class BandState(val freq: Double, val q: Double, val linearGain: Double) {
        var ic1eq = 0.0
        var ic2eq = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var a3 = 0.0
        var initialized = false
    }

    private val bandStates = bands.map { band ->
        BandState(band.freq, band.q, 10.0.pow(band.db / 20.0))
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = 0.0
            }

            for (band in bandStates) {
                if (!band.initialized) {
                    val nyquist = 0.5 * ctx.sampleRate
                    val fc = band.freq.coerceIn(5.0, nyquist - 1.0)
                    val Q = band.q.coerceIn(0.1, 50.0)
                    val g = tan(PI * fc / ctx.sampleRate)
                    band.a1 = 1.0 / (1.0 + g * (g + 1.0 / Q))
                    band.a2 = g * band.a1
                    band.a3 = g * band.a2
                    band.initialized = true
                }

                for (i in ctx.offset until end) {
                    val v0 = input[i]
                    val v3 = v0 - band.ic2eq
                    val v1 = band.a1 * band.ic1eq + band.a2 * v3
                    val v2 = band.ic2eq + band.a2 * band.ic1eq + band.a3 * v3
                    band.ic1eq = (2.0 * v1 - band.ic1eq).flushState()
                    band.ic2eq = (2.0 * v2 - band.ic2eq).flushState()
                    buffer[i] = (buffer[i] + v1 * band.linearGain)
                }
            }
        }
    }
}

/**
 * A single formant band specification.
 *
 * @property freq Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–3500.
 * @property q Bandwidth (resonance). Higher = narrower peak. Typical: 5–15.
 * @property db Gain in decibels. 0 = unity, negative = attenuated, positive = boosted.
 */
data class FormantBand(
    val freq: Double,
    val q: Double,
    val db: Double,
)

// ═══════════════════════════════════════════════════════════════════════════════
// Internal: filter/FM envelope computation — the ONE shared envelope law
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Computes the ADSR envelope level at a given position (attack/decay/sustain — no release).
 *
 * Used to determine the actual level at any point during the gate-on phase,
 * including at gate-end for correct release-start calculation.
 */
private fun envelopeLevelAtPosition(
    absPos: Int,
    attackFrames: Int,
    decayFrames: Int,
    sustainLevel: Double,
): Double = when {
    absPos < attackFrames -> {
        val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
        absPos * attRate
    }

    absPos < attackFrames + decayFrames -> {
        val decPos = absPos - attackFrames
        val decRate = if (decayFrames > 0) (1.0 - sustainLevel) / decayFrames else 0.0
        1.0 - (decPos * decRate)
    }

    else -> sustainLevel
}

/**
 * Computes a simple ADSR envelope value at the current block position.
 * Sample-addressable via [sampleOffsetWithinBlock]. Two calling patterns exist and BOTH are
 * load-bearing: `SvfIgnitor` evaluates it at a block's endpoints (its control-rate coefficient
 * chord), and `FmModIgnitor` calls it PER SAMPLE (block-framing ledger E1). Do NOT memoize the
 * result per block or hoist a call out of a per-sample loop — that reintroduces the zero-FM-head
 * defect, and `BlockFramingInvarianceSpec`'s "fm with envelope" case goes red. If per-sample cost
 * ever shows in a profile, the agreed shape is a per-block precompute (frames, rates,
 * levelAtGateEnd) plus a thin `at(absPos)` body — ONE law with two entry points, never a second
 * copy of this math.
 *
 * Release phase decays from the **actual level at gate-end**, not from sustainLevel.
 * This prevents discontinuous jumps (clicks) when gate-off occurs during attack or decay.
 */
internal fun computeFilterEnvelope(
    ctx: IgniteContext,
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
    sampleOffsetWithinBlock: Int = 0,
): Double {
    val attackFrames = (attackSec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val decayFrames = (decaySec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val releaseFrames = (releaseSec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val clampedSustain = sustainLevel.coerceIn(0.0, 1.0)

    val absPos = ctx.voiceElapsedFrames + sampleOffsetWithinBlock
    val gateEndPos = ctx.gateEndFrame

    val envValue = if (absPos >= gateEndPos) {
        val levelAtGateEnd = envelopeLevelAtPosition(gateEndPos, attackFrames, decayFrames, clampedSustain)
        val relPos = absPos - gateEndPos
        // Divides by N, not N-1 like the CURVE evaluators (releaseProgressDenom), so this ramp
        // ends at levelAtGateEnd/N rather than 0 on the last rendered frame. Deliberate: this is a
        // straight LINEAR ramp with no curve endpoint to land on, and it drives a filter cutoff, so
        // the residual ends a sweep a hair above its floor rather than leaving a gain step.
        // Changing it would move filter-sweep sound in existing songs for no click benefit.
        val relRate = if (releaseFrames > 0) levelAtGateEnd / releaseFrames else 1.0
        levelAtGateEnd - (relPos * relRate)
    } else {
        envelopeLevelAtPosition(absPos, attackFrames, decayFrames, clampedSustain)
    }

    return envValue.coerceIn(0.0, 1.0)
}
