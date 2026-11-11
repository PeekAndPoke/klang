/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.filters.SAT_STATE_SCALE
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.bilinearK
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.filters.diodePairResistanceApprox
import io.peekandpoke.klang.audio_be.filters.clampSvfRange
import io.peekandpoke.klang.audio_be.filters.filterEnvCutoffAtLevel
import io.peekandpoke.klang.audio_be.filters.onePoleLpfCoeff
import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIVE_PER_ANALOG
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE

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
 * Optional ADSR-style envelope that modulates filter cutoff, exact at knots that belong to the note and linear
 * between them (`SvfIgnitor.renderEnvelope`, [FILTER_ENV_KNOT_SEMITONES]).
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
    val attack: Double = 0.0,
    val decay: Double = 0.0,
    val sustain: Double = 1.0,
    val release: Double = 0.0,
    // The three stage curves, RESOLVED (`IgnitorDslRuntime.filterEnvDef` names them from the node's
    // knobs). Unlike the values above these default to the surface's own constant, `MOD_ENV_CURVE`:
    // a curve has no neutral value, and a partial shape built to mean "the node's envelope" must run
    // the curve the node runs.
    val attackCurve: AdsrCurve = MOD_ENV_CURVE,
    val decayCurve: AdsrCurve = MOD_ENV_CURVE,
    val releaseCurve: AdsrCurve = MOD_ENV_CURVE,
) {
    companion object {
        val NONE = FilterEnvDef()
    }
}

/**
 * The most the filter envelope's cutoff moves between two knots of `SvfIgnitor`, in semitones: half a semitone. Between
 * two knots the coefficients step linearly, so the cutoff there departs from the envelope's; at this step it stayed
 * within 2.2 cents of the exact per-sample cutoff over nine measured shapes (attacks 0 to 10 ms, depths -36 to 60
 * semitones, q 0.7 to 8), under the pitch JND of a pure tone (about 5 cents), which a cutoff is heard less precisely
 * than. One semitone measured up to 12.5 cents (KLANG-00432, "Decisions pass C").
 */
internal const val FILTER_ENV_KNOT_SEMITONES: Double = 0.5

/**
 * The longest stretch between two knots of the filter envelope, in frames of the rate the node runs at: one block at
 * the base rate. It bounds how late a knob read per block (the cutoff, q, an `analog` drift) enters an envelope path
 * that is not moving (two knots), and a held sustain computes no coefficients at these knots (the cutoff is unchanged).
 */
internal const val FILTER_ENV_KNOT_MAX_FRAMES: Int = 128

// ═══════════════════════════════════════════════════════════════════════════════
// SVF Filter — unified implementation for all modes
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * State Variable Filter (SVF) combinator.
 *
 * The TPT/Zavalishin canonical Cytomic form computes lowpass, highpass, bandpass, and
 * notch simultaneously; [mode] selects which output is used. Each instance creates
 * per-voice filter state in its closure. Optional [env] modulates cutoff over the
 * voice's lifetime: when active, the envelope ([EnvelopeCore], the engine's one envelope law) gives the
 * cutoff EXACTLY at knots that belong to the note (`filterEnvCutoff`), and the coefficients step linearly
 * per sample between two knots. The knots follow the envelope: never more than [FILTER_ENV_KNOT_MAX_FRAMES]
 * apart, one at every stage boundary, and close enough that the cutoff moves at most
 * [FILTER_ENV_KNOT_SEMITONES] between two (KLANG-00432, L4-02, decisions pass C; until then the envelope was
 * read at each block's two ends, which straightened a short attack and depended on where the block borders fell).
 * The level is clamped to [0, 1] before it scales the depth.
 *
 * **The default curve** was the voice strip's too (decision D3 of KLANG-00237 (builtin instruments)):
 * an unshaped stage takes `MOD_ENV_CURVE`, exponential; `curves` can shape each stage, and sprudel's
 * `lpfCurves` (and its three siblings) reach it through `classic()`'s curve slots.
 *
 * A node with `env.depth == 0.0` never enters this path at all.
 *
 * Optional [humanize] is the per-voice analog character the voice strip got from
 * `VoiceFactory`: a fixed cutoff tolerance and a slow drift lane, both drawn once per voice.
 * `null` is no humanization and renders bit-for-bit what this filter rendered without the
 * feature. See [FilterHumanization].
 *
 * Coefficient math is shared with `ResonatorBank` and `EqCore` via `computeSvfCoeffs`. NaN/Inf-safe
 * cutoff (via `bilinearK`); Q is clamped to `[0.1, 200.0]` with `isFinite` fallback.
 *
 * Mode dispatch is hoisted out of the per-sample loop into one specialized loop
 * body per tap. See `audio/ref/performance.md` for why class form (vs SAM lambda
 * + closure capture) matters in Kotlin/JS.
 *
 * @param mode Filter type: [SvfMode.LOWPASS], [SvfMode.HIGHPASS], [SvfMode.BANDPASS], or [SvfMode.NOTCH].
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs].
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
): Ignitor = SvfIgnitor(upstream = this, mode = mode, cutoffHz = cutoffHz, q = q, env = env, analog = analog, humanize = humanize)

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

    // The static path's coefficients.
    private val coefs = SvfCoeffs()
    private var initialized: Boolean = false
    private val hasEnv: Boolean = env.depth != 0.0

    // The cutoff envelope's evaluator, one per node (prepared per block).
    private val envCore = EnvelopeCore()
    private val hasDrift: Boolean = humanize?.hasDrift == true

    // Per-voice analog humanization, both 1.0 when the node has none, and `x * 1.0` is exactly `x` for
    // every double, so a node without it renders bit-for-bit what it rendered without the feature. The
    // ORDER of the two multiplies is the retired voice strip's: it multiplied the envelope's cutoff by the
    // block's drift and then both ends by the fixed tolerance.
    private val offsetMul: Double = humanize?.cutoffOffsetMul ?: 1.0

    // The block's cutoff, q, drift and analog drive, read in [generate] and held here for the coefficient setups and
    // the sample loops: a holder, not arguments (see [aimFrom]).
    private var blockCutoff: Double = 0.0
    private var blockQ: Double = 0.0
    private var blockDriftMul: Double = 1.0
    private var blockSaturate: Boolean = false
    private var blockDriveScale: Double = 0.0

    // The coefficients the sample loops run with, and what each of them adds after every sample: the static path's
    // latched set with no steps, or the envelope path's segment between two knots ([runSegment] reads and writes them).
    private var runA1: Double = 0.0
    private var runA2: Double = 0.0
    private var runA3: Double = 0.0
    private var runK: Double = 0.0
    private var runG: Double = 0.0
    private var stepA1: Double = 0.0
    private var stepA2: Double = 0.0
    private var stepA3: Double = 0.0
    private var stepK: Double = 0.0
    private var stepG: Double = 0.0

    // The envelope path's knots (KLANG-00432, L4-02; see [renderEnvelope]): the next knot's frame, its exact
    // coefficients, cutoff, q and envelope level; the cutoff and q the running coefficients last arrived at; the
    // gate the knot was aimed with; the length and the cutoff travel of the last segment (the next one's first guess);
    // and the voice frame the next block must start at, anything else starts over at an exact knot.
    private val target = SvfCoeffs()
    private var knotFrame: Int = 0
    private var knotCutoffHz: Double = 0.0
    private var knotQ: Double = 0.0
    private var knotLevel: Double = 0.0
    private var runCutoffHz: Double = 0.0
    private var runQ: Double = 0.0
    private var aimedGate: Int = 0
    private var lastLength: Int = FILTER_ENV_KNOT_MAX_FRAMES
    private var lastTravel: Double = 0.0
    private var expectedFrame: Int = -1

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            blockCutoff = Ignitors.readParam(cutoffHz, freqHz, ctx)
            blockQ = Ignitors.readParam(q, freqHz, ctx)

            val analogVal = Ignitors.readParam(analog, freqHz, ctx).coerceAtLeast(0.0)

            // A statement, not `humanize?.driftMul ?: 1.0`: the elvis merges a double with null, and V8 holds the
            // merge tagged, a heap number per block (`audio/ref/performance.md`, the `x ?: field` rule).
            val hum = humanize

            if (hum == null) {
                blockDriftMul = 1.0
            } else {
                hum.stepDrift(ctx)
                blockDriftMul = hum.driftMul
            }

            blockSaturate = analogVal > 0.0 && (mode == SvfMode.LOWPASS || mode == SvfMode.HIGHPASS)
            blockDriveScale = analogVal * FILTER_DRIVE_PER_ANALOG

            if (hasEnv) {
                renderEnvelope(input = input, buffer = buffer, ctx = ctx)

                return@use
            }

            if (!initialized || hasDrift || cutoffHz !is ParamIgnitor || q !is ParamIgnitor) {
                // `hasDrift` is false without a lane, so the cheap latch below is untouched for
                // every node that does not humanize; with one, the cutoff moves every block and
                // there is nothing to latch.
                computeStaticCoeffs(ctx)
                initialized = true
            }

            runA1 = coefs.a1; runA2 = coefs.a2; runA3 = coefs.a3; runK = coefs.k; runG = coefs.g
            stepA1 = 0.0; stepA2 = 0.0; stepA3 = 0.0; stepK = 0.0; stepG = 0.0

            runSegment(input = input, buffer = buffer, from = ctx.offset, to = ctx.offset + ctx.length)
        }
    }

    /**
     * The envelope path (KLANG-00432, L4-02, decisions pass C, 2026-11-11): the cutoff is EXACT at knots that belong to
     * the NOTE, and the coefficients step linearly between two knots, an add per coefficient per sample as before.
     *
     * **Where the knots fall.** From a knot at frame `p` the next is at most [FILTER_ENV_KNOT_MAX_FRAMES] frames on, never
     * past the envelope's next stage frame (`EnvelopeCore.nextStageFrame`: the attack's end, the decay's end, the gate, the
     * release's end), and close enough that the cutoff moves at most [FILTER_ENV_KNOT_SEMITONES] between the two: the
     * level's change across the segment times the depth. Within one stage the level is monotone, so its two ends bound
     * it. So a fast deep sweep gets a knot every frame or two (a 1 ms attack over 48 semitones), a held sustain one per
     * [FILTER_ENV_KNOT_MAX_FRAMES] whose coefficients are not recomputed (the cutoff is unchanged). Measured against the
     * exact per-sample cutoff over nine shapes (attacks 0 to 10 ms, depths -36 to 60 semitones, q 0.7 to 8): the cutoff
     * stays within 2.2 cents of it, with 118 to 336 coefficient sets per note; the block law this replaces was 230 to
     * 2870 cents off in the same cases with 114 to 211 sets (KLANG-00432, "Decisions pass C").
     *
     * **Note-anchored.** Every knot is a function of the note's frames and the envelope, not of the block grid: the
     * running coefficients carry across a block border, so the same note sounds the same, bit for bit, wherever its
     * onset falls in a block, as long as nothing it reads per block moves (the cutoff and q knobs, an `analog` drift,
     * a gate learned late). Those enter at the next knot's aim, at most two knots (256 frames) late, gliding there; a
     * gate learned late re-aims at once from where the coefficients stand (it moves the envelope's stages).
     *
     * A block that does not start where the last one ended (the first block, a skipped one) starts over at an exact knot.
     */
    private fun renderEnvelope(input: AudioBuffer, buffer: AudioBuffer, ctx: IgniteContext) {
        envCore.prepareModEnvelope(
            ctx = ctx, attack = env.attack, decay = env.decay, sustain = env.sustain, release = env.release,
            attackCurve = env.attackCurve, decayCurve = env.decayCurve, releaseCurve = env.releaseCurve,
        )

        val start = ctx.voiceElapsedFrames
        val from = ctx.offset
        val end = ctx.offset + ctx.length

        if (start != expectedFrame) {
            snapTo(ctx = ctx, frame = start)
        } else if (ctx.gateEndFrame != aimedGate) {
            // Between two knots the running coefficients are at no knot's cutoff: a NaN keeps [aimFrom] from reading
            // the new knot as flat (NaN equals nothing) and freezing them where they stand.
            runCutoffHz = Double.NaN
            knotLevel = levelAt(start)
            aimFrom(ctx = ctx, frame = start)
        }

        expectedFrame = start + ctx.length

        var i = from

        while (i < end) {
            val frame = start + (i - from)

            if (frame == knotFrame) {
                arrive(ctx = ctx, frame = frame)
            }

            val toKnot = knotFrame - frame
            val segmentEnd = if (toKnot < end - i) i + toKnot else end

            runSegment(input = input, buffer = buffer, from = i, to = segmentEnd)

            i = segmentEnd
        }
    }

    /** The envelope's level at [frame], clamped to [0, 1] as the cutoff law reads it (`filterEnvCutoff`). */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun levelAt(frame: Int): Double = clampSvfRange(x = envCore.at(frame), lo = 0.0, hi = 1.0)

    /** The cutoff at the envelope [level] with the block's base, drift and tolerance ([filterEnvCutoff]'s law). */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun cutoffAtLevel(level: Double): Double =
        filterEnvCutoffAtLevel(level = level, baseCutoff = blockCutoff, depthSemitones = env.depth) * blockDriftMul * offsetMul

    /** A knot at [frame] itself, exact: the loop in [renderEnvelope] arrives at it before its first sample. */
    private fun snapTo(ctx: IgniteContext, frame: Int) {
        val level = levelAt(frame)
        val cutoff = cutoffAtLevel(level)

        computeSvfCoeffs(cutoffHz = cutoff, q = blockQ, sampleRate = ctx.sampleRate.toDouble(), out = target)

        knotFrame = frame
        knotLevel = level
        knotCutoffHz = cutoff
        knotQ = blockQ
        lastLength = FILTER_ENV_KNOT_MAX_FRAMES
        lastTravel = 0.0
    }

    /** At a knot: the running coefficients take its exact set, and the next segment is aimed from there. */
    private fun arrive(ctx: IgniteContext, frame: Int) {
        runA1 = target.a1; runA2 = target.a2; runA3 = target.a3; runK = target.k; runG = target.g
        runCutoffHz = knotCutoffHz
        runQ = knotQ

        aimFrom(ctx = ctx, frame = frame)
    }

    /**
     * Aims the running coefficients from [frame] (where the envelope's level is [knotLevel]) at the next knot: finds it
     * (see [renderEnvelope]), computes its exact coefficients into [target] and the steps that reach them. A knot whose
     * cutoff and q equal the running ones steps nothing and computes no coefficients (a sustain, a finished release).
     *
     * Its doubles travel through the node's fields, not as arguments (`audio/ref/performance.md`, "a non-integral double
     * handed to a function that is not inlined is a heap number per call"): the frame is an Int, the rest is read here.
     */
    private fun aimFrom(ctx: IgniteContext, frame: Int) {
        val fromLevel = knotLevel
        val depth = if (env.depth < 0.0) -env.depth else env.depth
        var length = FILTER_ENV_KNOT_MAX_FRAMES

        // The first guess, after a segment that moved: its length, twice it when it moved at most half the allowed
        // step. Whole numbers only: a `Double.toInt()` here is a call on Kotlin/JS that V8 left out of this method's
        // budget, so its double crossed as a heap number (measured: about 1.2 KB per note of a 10 ms attack).
        if (lastTravel > 0.0) {
            length = if (lastTravel <= FILTER_ENV_KNOT_SEMITONES * 0.5) lastLength * 2 else lastLength

            if (length > FILTER_ENV_KNOT_MAX_FRAMES) {
                length = FILTER_ENV_KNOT_MAX_FRAMES
            }
        }

        val toStage = envCore.nextStageFrame(frame) - frame

        if (toStage < length) {
            length = toStage
        }

        var level: Double
        var travel: Double

        // Halved while the cutoff moves too far (a NaN travel ends it). One loop with one level read, so the envelope's
        // law is inlined here once.
        while (true) {
            level = levelAt(frame + length)
            travel = depth * (if (level > fromLevel) level - fromLevel else fromLevel - level)

            if (!(travel > FILTER_ENV_KNOT_SEMITONES) || length <= 1) {
                break
            }

            length /= 2
        }

        lastLength = length
        lastTravel = travel
        aimedGate = ctx.gateEndFrame
        knotFrame = frame + length
        knotLevel = level

        val cutoff = cutoffAtLevel(level)

        knotCutoffHz = cutoff
        knotQ = blockQ

        // Flat: the running coefficients ARE [target]'s (an arrival just copied them, and a re-aim sets [runCutoffHz]
        // to NaN, which equals nothing), so the segment only steps nothing.
        if (cutoff == runCutoffHz && blockQ == runQ) {
            stepA1 = 0.0; stepA2 = 0.0; stepA3 = 0.0; stepK = 0.0; stepG = 0.0

            return
        }

        computeSvfCoeffs(cutoffHz = cutoff, q = blockQ, sampleRate = ctx.sampleRate.toDouble(), out = target)

        val inv = 1.0 / length

        stepA1 = (target.a1 - runA1) * inv
        stepA2 = (target.a2 - runA2) * inv
        stepA3 = (target.a3 - runA3) * inv
        stepK = (target.k - runK) * inv
        stepG = (target.g - runG) * inv
    }

    /**
     * The sample loops over `[from, to)`: the running coefficients from the node's fields, each stepped after every
     * sample, written back at the end. Mode is fixed for the lifetime of this Ignitor, so it branches once per call, not
     * per sample; LOWPASS/HIGHPASS additionally branch on the block's analog drive, to switch between the linear
     * closed-form math and the analog-style state-dependent damping path (per-sample diode-pair polynomial plus an
     * explicit-feedback solve).
     *
     * A method of its own, with no double argument (see [aimFrom]): the envelope path calls it once per segment, the
     * static path once per block.
     *
     * The saturated branch, in its own words (moved here from the retired strip `SvfLPF`, phase 3 step 9):
     *  - Convention conversion, why the factor of 2: the OB-X-style filter parameterises
     *    resonance with `R`, where `2R` corresponds to our `k` (damping vs. inverse quality
     *    factor). A feedback term `2 * (R + tCfb)` therefore maps onto our `k + 2 * tCfb`,
     *    hence `kEff = k + 2 * driveScale * tCfb`.
     *  - Why it works where tanh failed: the signal stays linear and only the DAMPING grows
     *    with the state, so the resonance compresses and the filter stays bounded. A tanh
     *    capping the feedback signal does the opposite (less damping, runaway). The failed
     *    attempts are recorded in `audio/ref/memory-history.md`, "Filter Saturation Dead-End".
     */
    private fun runSegment(input: AudioBuffer, buffer: AudioBuffer, from: Int, to: Int) {
        var a1 = runA1
        var a2 = runA2
        var a3 = runA3
        var k = runK
        var g = runG
        val a1Step = stepA1
        val a2Step = stepA2
        val a3Step = stepA3
        val kStep = stepK
        val gStep = stepG
        val saturate = blockSaturate
        val driveScale = blockDriveScale

        when (mode) {
            SvfMode.LOWPASS -> {
                if (saturate) {
                    for (i in from until to) {
                        val v0 = input[i]
                        val tCfb = diodePairResistanceApprox(ic1eq * SAT_STATE_SCALE) - 1.0
                        val kRaw = k + 2.0 * driveScale * tCfb
                        // Never NEGATIVE damping (audit N1): the polynomial dips under 1 for a state between about
                        // -3.1 and 0, which at q times analog over about 590 made the loop oscillate for good near
                        // fs/3. Floored at 0, bit-identical wherever the damping is positive (all ordinary settings).
                        // NaN-guard: a NaN reads as 0 here, the state's flush catches it below.
                        val kEff = if (kRaw > 0.0) kRaw else 0.0
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
                    for (i in from until to) {
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
                    for (i in from until to) {
                        val v0 = input[i]
                        val tCfb = diodePairResistanceApprox(ic1eq * SAT_STATE_SCALE) - 1.0
                        val kRaw = k + 2.0 * driveScale * tCfb
                        // Never NEGATIVE damping (audit N1): the polynomial dips under 1 for a state between about
                        // -3.1 and 0, which at q times analog over about 590 made the loop oscillate for good near
                        // fs/3. Floored at 0, bit-identical wherever the damping is positive (all ordinary settings).
                        // NaN-guard: a NaN reads as 0 here, the state's flush catches it below.
                        val kEff = if (kRaw > 0.0) kRaw else 0.0
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
                    for (i in from until to) {
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
                // C2 (filter unification): k * v1 = unity peak at fc (k = 1/clampedQ):
                // q is a pure width control, matching the `ResonatorBank` bands and the fused EqCore
                // BANDPASS arm bit-for-bit. Between two knots of one q, kStep is exactly 0.
                for (i in from until to) {
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
                for (i in from until to) {
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

        runA1 = a1
        runA2 = a2
        runA3 = a3
        runK = k
        runG = g
    }

    /**
     * The static path's coefficients, at [blockCutoff] times the drift and the tolerance, into [coefs]. A method of
     * its own, reading the holder, for the reason [aimFrom] gives (engine follow-up 29): in a voice
     * with a second SVF, V8 keeps it a call, and it spends its own budget. In a voice with one SVF, V8 inlines it
     * into [generate] (238 bytes of bytecode on the production bundle), and its callees then compete for
     * [generate]'s budget. That is why the `coerceIn` half of the clamps is written out as comparisons
     * (`clampSvfRange`): with Kotlin's `coerceIn` (a stdlib call, 67 bytes) V8 left the clamps out there, about 2
     * heap numbers per block for a lowpass with `analog`. The `isFinite` guard was a stdlib call too, and V8 did
     * leave it out (a lowpass or highpass alone with `analog`): since engine follow-up 31 it is `isFiniteInline`, an
     * abs and a compare (`clampSvfCutoff`).
     */
    private fun computeStaticCoeffs(ctx: IgniteContext) {
        computeSvfCoeffs(cutoffHz = blockCutoff * blockDriftMul * offsetMul, q = blockQ, sampleRate = ctx.sampleRate.toDouble(), out = coefs)
    }
}

/**
 * SVF filter with constant cutoff and Q (convenience overload).
 *
 * @param mode Filter type: LOWPASS, HIGHPASS, BANDPASS, or NOTCH.
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs].
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
    mode = mode, cutoffHz = ParamIgnitor("cutoffHz", cutoffHz), q = ParamIgnitor("q", q), env = env,
    analog = ParamIgnitor("analog", analog),
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
 * `controlRateValue` contract). Folding a [ParamIgnitor]'s value here instead would skip
 * the `safeOut` the fused door applies, so a non-finite q would land on the SVF's Butterworth
 * 0.7071 fallback on one door and on the scrubbed value's clamp on the other: the 0.1 floor for
 * NaN and -Inf, the 200 ceiling for +Inf (safeOut clamps an infinity to a finite SAFE_MAX). Same
 * program, two filters.
 *
 * Where a non-finite q still comes from, since 2026-09-19: NOT from `ignitorParams`, whose values the
 * `IgnitorDsl.Param` leaf reads as unset when they are non-finite (`IgnitorDslRuntime`). Two live
 * routes remain, which is why this is not dead code (both verified by a caller search and a
 * render on 2026-09-19):
 *
 *  - an authored `IgnitorDsl.Param` whose DEFAULT is non-finite. A default is the instrument's own
 *    declaration and is deliberately not scrubbed; `IgnitorDslOptimizerRenderSpec`'s C5 rows drive
 *    exactly this.
 *  - ARITHMETIC in a q expression. `Plus` and `Minus` are clamp-free by contract (see
 *    `PlusIgnitor`), so two finite operands can overflow: `Ignitor.param("a", 1e308).plus(...)` as a q
 *    renders sample for sample what a `+Infinity` q renders.
 *
 * A [ParamIgnitor] that engine code constructs directly (the `Double` overloads of [svf] and its
 * wrappers) would be a third, but no production caller does that today: `scaledBy` has exactly two
 * callers, both in `IgnitorDslRuntime`'s passes cascade and both fed `q.noMod()`. A voice's filter q
 * reaches the tree only as a slot (`classic()`).
 */
internal fun Ignitor.scaledBy(factor: Double): Ignitor = when {
    factor == 1.0 -> this
    this is ConstantIgnitor -> ConstantIgnitor(value * factor)
    else -> this * ConstantIgnitor(factor)
}

/**
 * Lowpass filter — lets low frequencies through, dulls the highs.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 200 to 8000.
 * @param q Resonance. 0.707 = flat (Butterworth), higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.lowpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(mode = SvfMode.LOWPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog, humanize = humanize)

/**
 * Lowpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 200 to 8000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Analog character amount. Default: 0 (clean linear). >0 engages OB-X-style state-dependent damping.
 */
fun Ignitor.lowpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(mode = SvfMode.LOWPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog)

/**
 * Highpass filter — lets high frequencies through, removes the bottom.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 80 to 2000.
 * @param q Resonance. 0.707 = flat, higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.highpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(mode = SvfMode.HIGHPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog, humanize = humanize)

/**
 * Highpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 80 to 2000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Analog character amount. Default: 0 (clean linear). >0 engages OB-X-style state-dependent damping.
 */
fun Ignitor.highpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(mode = SvfMode.HIGHPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog)

/**
 * Bandpass filter — keeps only a frequency band, removes everything above and below.
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 300 to 5000.
 * @param q Width of the pass band (peak at fc is unity since C2). Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.bandpass(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(mode = SvfMode.BANDPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog, humanize = humanize)

/**
 * Bandpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 300 to 5000.
 * @param q Width of the pass band (peak at fc is unity since C2). Default: 0.707. Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Reserved — currently a no-op (BP saturation not implemented).
 */
fun Ignitor.bandpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(mode = SvfMode.BANDPASS, cutoffHz = cutoffHz, q = q, env = env, analog = analog)

/**
 * Notch (band-reject) filter — removes one frequency band, keeps everything else.
 *
 * @param cutoffHz Center frequency of the notch in Hz. Clamped to [5, 0.49 * fs]. Typical: 300 to 5000.
 * @param q Width of the notch. Higher = narrower cut. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.notch(
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvDef = FilterEnvDef.NONE,
    analog: Ignitor = ParamIgnitor("analog", 0.0),
    humanize: FilterHumanization? = null,
): Ignitor = svf(mode = SvfMode.NOTCH, cutoffHz = cutoffHz, q = q, env = env, analog = analog, humanize = humanize)

/**
 * Notch (band-reject) filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 300 to 5000.
 * @param q Width of the notch. Higher = narrower cut. Default: 0.707. Clamped to [0.1, 200.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 * @param analog Reserved — currently a no-op.
 */
fun Ignitor.notch(cutoffHz: Double, q: Double = 0.707, env: FilterEnvDef = FilterEnvDef.NONE, analog: Double = 0.0): Ignitor =
    svf(mode = SvfMode.NOTCH, cutoffHz = cutoffHz, q = q, env = env, analog = analog)

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Lowpass (for warmth / simple smoothing)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple one-pole lowpass filter for warmth or smoothing. Processes per-sample.
 *
 * Gentler slope than the SVF (6 dB/oct vs 12 dB/oct). Good for subtle tone shaping.
 * Cutoff is read once per block (control rate).
 *
 * The bilinear-prewarped one-pole leaky integrator, `y += a·(x − y)` with `a = K/(1+K)`: the coefficient
 * math is `onePoleLpfCoeff` in `filters/LowPassHighPassFilters.kt`, whose header holds the review notes.
 *
 * Class form (not SAM lambda) so the integrator state lives in a class field rather
 * than a closure-captured `var` (Kotlin/JS ObjectRef). The hot loop reads and writes the field directly.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs].
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
            val a = onePoleLpfCoeff(cutoffHz = fc, sampleRate = ctx.sampleRate.toDouble())

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                y += a * (input[i] - y)
                y = y.flushState()
                buffer[i] = y
            }
        }
    }
}

fun Ignitor.onePoleLowpass(cutoffHz: Ignitor): Ignitor = OnePoleLowpassIgnitor(upstream = this, cutoffHz = cutoffHz)

/**
 * One-pole lowpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 1000 to 8000.
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
 * The canonical bilinear topology `y = b0·(x − xPrev) + a1·y`: see the header of
 * `filters/LowPassHighPassFilters.kt` for the review history and topology rationale. Class form (not SAM
 * lambda) so state stays in class fields; the hot loop reads and writes them directly.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs].
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
            val k = bilinearK(cutoffHz = fc, sampleRate = ctx.sampleRate.toDouble())
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

fun Ignitor.onePoleHighpass(cutoffHz: Ignitor): Ignitor = OnePoleHighpassIgnitor(upstream = this, cutoffHz = cutoffHz)

/**
 * One-pole highpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, 0.49 * fs]. Typical: 30 to 500.
 */
fun Ignitor.onePoleHighpass(cutoffHz: Double): Ignitor = onePoleHighpass(ParamIgnitor("cutoffHz", cutoffHz))

// ═══════════════════════════════════════════════════════════════════════════════
// Internal: the filter and FM envelopes' block setup, one place for both node hosts
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Prepares this [EnvelopeCore] for one block of a MODULATION envelope on the Ignitor side: the filter
 * cutoff envelope (`SvfIgnitor`, which reads [EnvelopeCore.at] at its knots) and the FM index
 * envelope (`FmModIgnitor`, which reads it per sample). The level is the core's; each host clamps it to
 * [0, 1], the depth range of both destinations.
 *
 * Kotlin `inline` ON PURPOSE (engine follow-up 10d, 2026-10-19; `audio/ref/performance.md`, "a non-integral double
 * handed to a function that is not inlined is a heap number per call"). As a plain function it took the four stage
 * knobs as double arguments, and inside `FmModIgnitor.generate` V8 left it out of the budget under a mixed profile,
 * so each non-integral knob (a sustain of 0.3, an attack of 0.001 s) crossed as a heap number per block. Inline, the
 * host's own compiled code stores them into the core's fields ([EnvelopeCore.prepare] is inline too) and only the gate
 * frame and the curves cross into `prepareBlock`. No test can pin it (the sound is the same either way): this KDoc
 * is the guard. Do not turn it back into a plain function.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun EnvelopeCore.prepareModEnvelope(
    ctx: IgniteContext,
    attack: Double,
    decay: Double,
    sustain: Double,
    release: Double,
    attackCurve: AdsrCurve,
    decayCurve: AdsrCurve,
    releaseCurve: AdsrCurve,
) {
    prepare(
        attackFrames = attack * ctx.sampleRate,
        decayFrames = decay * ctx.sampleRate,
        sustainLevel = sustain,
        releaseFrames = release * ctx.sampleRate,
        gateEndPos = ctx.gateEndFrame,
        attackCurve = attackCurve,
        decayCurve = decayCurve,
        releaseCurve = releaseCurve,
    )
}
