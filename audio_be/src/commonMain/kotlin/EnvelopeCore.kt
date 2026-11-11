/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import kotlin.math.ceil
import kotlin.math.floor

/**
 * THE envelope law of the engine, one copy for every host (phase 3, decision D3, 2026-09-25). Every
 * ADSR-shaped envelope is a thin host of it: the Ignitor chain `adsr` (`AdsrIgnitor`), the Ignitor filter
 * cutoff envelope (`SvfIgnitor`), the Ignitor FM index envelope (`FmModIgnitor`), the Ignitor pitch
 * envelope (`PitchEnvelopeModIgnitor`, which sprudel's `penv` fills through `classic()` since pitch pipeline
 * step 1; sprudel's `fm` fills the FM node's since step 4). The voice strip's VCA and filter envelope were hosts too
 * until the strip retired (phase 3 step 9), its pitch envelope until pitch pipeline step 1, and the voice's own
 * control-rate FM envelope (one value per block, release always 0) until step 4. A host adapts only its block contract (per sample, or
 * at the filter's knots, `nextStageFrame`) and maps the level onto its destination; the level itself is computed here.
 *
 * **The shape.** Its entry points are [prepare], once per block, and [at], per sample: the "per-block
 * precompute plus a thin `at(absPos)` body, one law with two entry points" that block-framing ledger E1
 * agreed on. [prepare] allocates nothing and neither does [at].
 *
 * **The law, rule by rule.**
 *  - **Frames.** The attack and the decay count FRACTIONAL frames (`seconds * sampleRate` as a Double,
 *    decision D3.5): a 0.005 s attack at 44.1 kHz is 220.5 frames, so frame 220 is still attack. The
 *    RELEASE counts `floor(N)` frames, and that is the precise reading of D3.5, not an exception to it:
 *    a voice renders whole frames, so a release of N frames renders relPos 0 .. floor(N) - 1, and dividing
 *    by `floor(N) - 1` lands p = 1 on the last rendered frame, where every curve's endpoint is an exact
 *    0.0 (`releaseProgressDenom`). A fractional denominator would leave a residual on that frame.
 *  - **A non-positive or NaN time is a zero-length stage** (a coerced input), and so is one too small to
 *    have a finite reciprocal. An infinite one never ends.
 *  - **The curves** are [adsrCurveShape], the one `when` of the engine.
 *  - **Composition.** Attack `g(p)` from 0 to 1; decay `s + (1 - s) * g(1 - p)`; sustain `s`; release
 *    `L * g(1 - p)`, where L is [levelAtGate].
 *  - **Progress.** Attack and decay multiply by a hoisted reciprocal (they have no endpoint that depends
 *    on the last bit: the frame after a stage belongs to the next one); the release DIVIDES, because its
 *    endpoint does (see `releaseProgressDenom`).
 *  - **The release starts from the attack-decay-sustain law evaluated AT the gate frame** ([levelAtGate]),
 *    stateless: the envelope is continuous at the gate instant, and no host keeps a history that a late
 *    first block or a second stage sharing the state could get wrong (block-framing ledger E4). A
 *    release of fewer than two frames reaches 0 on the gate frame itself (`releaseProgressOffset`,
 *    "0 means 0"). A gate at or before the onset (frame 0 or earlier, e.g. a negative `legato`) releases
 *    from 0: the envelope is 0 on every frame (an amplitude envelope silences the voice, as before this
 *    law; the modulation envelopes used to release from the attack curve evaluated at the gate).
 *  - **The sustain is RAW** (the Motor stays raw): no clamp here. The chain `adsr`, the
 *    FM node and the pitch node (sprudel's `penv` and `fm` included) substitute their own default for
 *    a non-finite sustain before [prepare] (the filter node's knobs are finite by construction). Each host maps the level onto its destination: an
 *    amplitude floors at 0, a filter or FM depth is clamped to [0, 1], a pitch passes raw.
 */
internal class EnvelopeCore {
    internal var attackFrames: Double = 0.0
    internal var attDecFrames: Double = 0.0
    internal var attRate: Double = 1.0
    internal var decRate: Double = 1.0
    internal var sustain: Double = 0.0
    internal var relDenom: Double = 1.0
    internal var relOffset: Double = 1.0
    internal var gateEndPos: Int = 0
    internal var attackCurve: AdsrCurve = AdsrCurve.Default
    internal var decayCurve: AdsrCurve = AdsrCurve.Default
    internal var releaseCurve: AdsrCurve = AdsrCurve.Default
    internal var k: Double = ADSR_EXP_K
    internal var norm: Double = ADSR_EXP_NORM

    /** The level the release starts from: the attack-decay-sustain law AT the gate frame. Set by [prepare]. */
    var levelAtGate: Double = 0.0
        private set

    /**
     * Resolves one block's envelope. Frame counts are voice-relative and may be fractional; [gateEndPos]
     * is the gate's voice-relative frame. [k] and [norm] are the Exponential curvature and its
     * [adsrExpNorm]; every host passes the defaults.
     *
     * **Inline, and the doubles travel through the core's own fields, ON PURPOSE** (engine follow-up 31, 2026-10-15;
     * the rule in `audio/ref/performance.md`, "a non-integral double handed to a function that is not inlined is a
     * heap number per call"). The block's work, [prepareBlock], is past V8's bytecode limit for inlining (460 bytes)
     * and is never inlined, so a double argument crossed it as a heap number per host per block whenever it was not
     * a small integer: a sustain of 0.3 or 0.7, a stage time that is not a whole number of frames. Here the host's
     * own compiled code stores them, and the call carries only the gate frame and the curves. [sustain], [k] and
     * [norm] are stored as they are (their final values); the three stage lengths are STAGED in the fields that
     * [prepareBlock] reads first and then overwrites with their derived values ([attackFrames], [attDecFrames],
     * [relDenom]). Staged in existing fields and not in a holder of its own because a holder is an allocation per
     * voice build (a `DoubleArray(6)` read about 224 B per note on V8 and 64 on the JVM, over `E-C-n1`'s lines). No
     * test can pin it (the sound is the same either way): this KDoc is the guard. Do not hand [prepareBlock] the
     * doubles as arguments again.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun prepare(
        attackFrames: Double,
        decayFrames: Double,
        sustainLevel: Double,
        releaseFrames: Double,
        gateEndPos: Int,
        attackCurve: AdsrCurve,
        decayCurve: AdsrCurve,
        releaseCurve: AdsrCurve,
        k: Double = ADSR_EXP_K,
        norm: Double = ADSR_EXP_NORM,
    ) {
        // Staged: prepareBlock reads these three as the stage lengths, then overwrites them.
        this.attackFrames = attackFrames
        this.attDecFrames = decayFrames
        this.relDenom = releaseFrames
        // Final values.
        this.sustain = sustainLevel
        this.k = k
        this.norm = norm

        prepareBlock(gateEndPos = gateEndPos, attackCurve = attackCurve, decayCurve = decayCurve, releaseCurve = releaseCurve)
    }

    /**
     * Call only through `prepare`. [prepare]'s work. Reads the three stage lengths [prepare] staged in
     * [attackFrames], [attDecFrames] and [relDenom], and overwrites those fields with what the law needs; [sustain],
     * [k] and [norm] are already set.
     */
    internal fun prepareBlock(gateEndPos: Int, attackCurve: AdsrCurve, decayCurve: AdsrCurve, releaseCurve: AdsrCurve) {
        val a = stageFrames(attackFrames)
        val d = stageFrames(attDecFrames)
        val r = floor(stageFrames(relDenom))

        this.attackFrames = a
        this.attDecFrames = a + d
        this.attRate = if (a > 0.0) 1.0 / a else 1.0
        this.decRate = if (d > 0.0) 1.0 / d else 1.0
        this.relDenom = releaseProgressDenom(r)
        this.relOffset = releaseProgressOffset(r)
        this.gateEndPos = gateEndPos
        this.attackCurve = attackCurve
        this.decayCurve = decayCurve
        this.releaseCurve = releaseCurve

        // A gate at or before the onset releases from 0: the voice was never open, so there is no
        // attack-decay level to release from. Evaluating the law there would extrapolate the attack
        // curve (a Square or SCurve squares a negative progress: a gate at -N frames with a zero-frame
        // attack released from N^2), and a zero attack would release from its full decay top.
        levelAtGate = if (gateEndPos <= 0) 0.0 else adsAt(gateEndPos)
    }

    /** The envelope level at the voice-relative frame [pos]: the whole law. Inline: it runs per sample. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun at(pos: Int): Double =
        if (pos >= gateEndPos) releaseAt(pos - gateEndPos, levelAtGate) else adsAt(pos)

    /** The attack-decay-sustain level at [pos], as if the gate were still open. */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun adsAt(pos: Int): Double = when {
        pos < attackFrames -> adsrCurveShape(curve = attackCurve, x = pos * attRate, k = k, norm = norm)
        pos < attDecFrames -> sustain + (1.0 - sustain) * adsrCurveShape(curve = decayCurve, x = 1.0 - (pos - attackFrames) * decRate, k = k, norm = norm)
        else -> sustain
    }

    /** The release level [relPos] frames after the gate, falling from [startLevel]. */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun releaseAt(relPos: Int, startLevel: Double): Double {
        val p = ((relPos + relOffset) / relDenom).coerceAtMost(1.0)

        return startLevel * adsrCurveShape(curve = releaseCurve, x = 1.0 - p, k = k, norm = norm)
    }

    /** The frame where the attack and decay end and the sustain begins. Set by [prepare]. */
    val sustainFrom: Double get() = attDecFrames

    /**
     * The first frame after [pos] where the law changes its stage: the first decay frame, the first sustain frame, the
     * gate, the frame from which a finished release holds its end level; [Int.MAX_VALUE] when none follows. Between two
     * of these frames the level is one stage's curve, monotone, so the level at the two ends bounds the level between
     * them. A stage boundary that the gate has made unreachable is still returned (one frame too many, never one too
     * few). Prepared by [prepare]; frame counts past [Int.MAX_VALUE] saturate (`toInt`). The filter envelope's knots
     * (`SvfIgnitor`) read it once per knot.
     */
    fun nextStageFrame(pos: Int): Int {
        var next = Int.MAX_VALUE
        val attackEnd = ceil(attackFrames).toInt()
        val decayEnd = ceil(attDecFrames).toInt()
        val releaseEnd = (gateEndPos.toDouble() + ceil(relDenom - relOffset)).toInt()

        if (attackEnd > pos) {
            next = attackEnd
        }

        if (decayEnd > pos && decayEnd < next) {
            next = decayEnd
        }

        if (gateEndPos > pos && gateEndPos < next) {
            next = gateEndPos
        }

        if (releaseEnd > pos && releaseEnd < next) {
            next = releaseEnd
        }

        return next
    }

    /** The level a finished release holds: [levelAtGate] times the release curve at its end. */
    fun releaseEndLevel(): Double = levelAtGate * adsrCurveShape(curve = releaseCurve, x = 0.0, k = k, norm = norm)

    /** True when the release has fully arrived at its end by [relPos] frames after the gate. */
    fun releaseDone(relPos: Int): Boolean = (relPos + relOffset) / relDenom >= 1.0

    /**
     * A stage's frame count: a non-positive or NaN count, and a count so small that its reciprocal is not
     * finite (below about 5.6e-309 frames), is a zero-length stage. The second rule keeps `pos * (1 / a)`
     * from being `0 * Inf`, a NaN, on the stage's first frame. An infinite count stays: the stage never
     * ends (its reciprocal is 0).
     */
    private fun stageFrames(frames: Double): Double = if (frames > 0.0 && (1.0 / frames).isFinite()) frames else 0.0
}

/**
 * How long the de-click smoother is given past the release's end, in its own time constants, before the voice ends:
 * the voice's lifetime is the release plus [declickSettleSeconds] (the `Adsr` arm of the build adds it to the tail).
 *
 * Why (audit L2-01): the release lands its raw level on exact 0 on its last frame, but a one-pole that follows a
 * falling ramp stands above it by about the slope times its time constant, so a voice that ended there was cut with
 * gain left on its last frame (-49.6 dB at the default 50 ms release, -23.4 dB at 5 ms, worse with a linear curve).
 * Past the release the raw level is 0, the smoother falls by a factor e per time constant, and [EnvelopeDeclick]
 * flushes it to exact 0 below `DENORMAL_THRESHOLD` (1e-15): 34.5 time constants from a residual of 1. 36 leave room
 * for a residual up to about 4 (a sustain above 1, raw); a larger one ends below 1e-15 times itself, under -300 dB.
 */
internal const val ENV_DECLICK_SETTLE_TIME_CONSTANTS: Double = 36.0

/**
 * The settle time a de-click of [declickSeconds] adds to an envelope's tail at [sampleRate]: [ENV_DECLICK_SETTLE_TIME_CONSTANTS]
 * of it, plus one frame, because the voice renders whole frames and the extension's fraction is floored away. Only
 * for a finite de-click above 0 (the caller's test); 0 is no de-click and needs no settle.
 */
internal fun declickSettleSeconds(declickSeconds: Double, sampleRate: Int): Double =
    ENV_DECLICK_SETTLE_TIME_CONSTANTS * declickSeconds + 1.0 / sampleRate

/**
 * The one-pole de-click smoother on an AMPLITUDE envelope's gain, primed to the first level it sees so a
 * voice that starts mid-note or at full level is not faded in. `classic()`'s envelope runs it always (its
 * `declick` is the constant `ENV_DECLICK_SECONDS`), any other Ignitor `adsr` when its `declick` knob is above 0. The modulation envelopes
 * have none. Its coefficient is `timeConstantCoeff` (`utils/time_constant.kt`) of the `declick` time.
 *
 * Its state is flushed ([flushState]), the engine rule for IIR state: below 1e-15 it is exact 0. Without it a sustain
 * of 0 left the gain falling for ever into subnormals, which reached the cylinder (audit L4-03), and a voice could
 * not end on an exact 0 (audit L2-01, [ENV_DECLICK_SETTLE_TIME_CONSTANTS]).
 */
internal class EnvelopeDeclick {
    private var smoothed: Double = 0.0
    private var primed: Boolean = false

    /** The smoothed gain for this sample's [level] under a per-sample [coeff]. */
    fun next(level: Double, coeff: Double): Double {
        if (!primed) {
            smoothed = level
            primed = true
        } else {
            smoothed = (smoothed + coeff * (level - smoothed)).flushState()
        }

        return smoothed
    }
}
