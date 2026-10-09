/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.utils.safeDiv
import io.peekandpoke.klang.audio_be.utils.safeOut

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.fastExp2
import io.peekandpoke.klang.audio_be.utils.fastSin
import io.peekandpoke.klang.audio_be.utils.wrapPhaseFastOrSafe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.FM_RATIO
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_SEMITONES
import kotlin.math.pow
import kotlin.math.abs

/**
 * Mod-factory functions for the build-time pitch-mod approach.
 *
 * Each factory returns an [Ignitor] that outputs per-sample values in **ratio space**:
 * 1.0 = no pitch change, >1.0 = higher pitch, <1.0 = lower pitch.
 *
 * Ratio space is the internal convention — multiple mods combine via simple multiplication
 * (using the existing [Ignitor.times] operator). [ModApplyingIgnitor] uses the ratio directly
 * as a phase-increment multiplier.
 *
 * The user-facing `pitchMod()` DSL extension accepts **deviation space** (0 = no change) and
 * converts to ratio internally by adding 1.0. This is handled at the DSL/buildIgnitor boundary,
 * not in these factories.
 */

/**
 * Converts a deviation-space mod Ignitor (0.0 = no change) to ratio space (1.0 = no change)
 * by adding 1.0 to every sample. Used by the [IgnitorDsl.PitchMod] handler.
 */
fun deviationToRatioIgnitor(userMod: Ignitor): Ignitor = DeviationToRatioIgnitor(userMod)

private class DeviationToRatioIgnitor(private val userMod: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        userMod.generate(buffer, freqHz, ctx)
        val end = ctx.windowEnd
        for (i in ctx.offset until end) buffer[i] = buffer[i] + 1.0
    }
}

/**
 * Vibrato — sinusoidal pitch LFO in ratio space.
 *
 * Produces `2^(sin(lfoPhase) * depthSemitones / 12)` per sample.
 * At depth=0: output = 1.0 (no change). At depth=1, ±1 semitone wobble.
 *
 * Output is passed through [safeOut] — extreme `depthSemitones` values cannot
 * produce `+Inf` ratios that would poison the oscillator phase accumulator.
 * See `audio/ref/numerical-safety.md`.
 *
 * @param rate LFO frequency in Hz
 * @param semitones modulation depth in SEMITONES
 */
private class VibratoModIgnitor(
    private val rate: Ignitor,
    private val semitones: Ignitor,
) : Ignitor {
    private var lfoPhase: Double = 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // NaN-guard: a non-finite rate or depth reads as UNSET and takes the node's default, the chain
        // `adsr`'s rule (`finiteOr`). Raw, a NaN depth skips the `<= 0.0` bypass and `safeOut` turns every
        // ratio into 0 (the oscillator holds still), and a NaN rate pins the LFO at phase 0 (no vibrato).
        // No clamp: every finite value passes raw.
        val rateVal = finiteOr(value = Ignitors.readParam(rate, freqHz, ctx), fallback = VIBRATO_RATE_HZ)
        val depthSemitones = finiteOr(value = Ignitors.readParam(semitones, freqHz, ctx), fallback = VIBRATO_SEMITONES)
        val end = ctx.windowEnd
        val lfoInc = TWO_PI * rateVal / ctx.sampleRateD
        val depthOctaves = depthSemitones / 12.0
        // The one-subtract wrap holds while |inc| < 2π; a rate past the sample rate (raw-Motor,
        // either sign) takes the full wrap. NaN and infinite inc take it too.
        val safeWrap = !(abs(lfoInc) < TWO_PI)

        if (depthSemitones <= 0.0) {
            // The LFO still ADVANCES: state moves once per rendered sample, whatever the output
            // (block-framing contract). The old early return froze the phase for the whole block,
            // so a modulated depth passing through zero resumed the LFO from a phase stale by a
            // block-size-dependent amount (block-framing ledger E2).
            for (i in ctx.offset until end) {
                buffer[i] = 1.0
                lfoPhase += lfoInc
                lfoPhase = lfoPhase.wrapPhaseFastOrSafe(period = TWO_PI, safe = safeWrap)
            }
            return
        }
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(fastExp2(fastSin(lfoPhase) * depthOctaves))
            lfoPhase += lfoInc
            lfoPhase = lfoPhase.wrapPhaseFastOrSafe(period = TWO_PI, safe = safeWrap)
        }
    }
}

fun vibratoModIgnitor(rate: Ignitor, semitones: Ignitor): Ignitor = VibratoModIgnitor(rate = rate, semitones = semitones)

fun vibratoModIgnitor(rate: Double, semitones: Double): Ignitor =
    vibratoModIgnitor(rate = ParamIgnitor("rate", rate), semitones = ParamIgnitor("semitones", semitones))

/**
 * Accelerate: an exponential pitch glide over the GATE, then held.
 *
 * Produces `2^((semitones / 12) * progress)` per sample, where progress runs from 0 at the onset to 1 at the gate
 * close (`IgniteContext.voiceDurationFrames`, the gate length). From the gate frame on, through the whole release, it
 * HOLDS the target `2^(semitones / 12)`: `accelerate(12)` arrives one octave up when the note ends and stays there
 * (decision D2 of the pitch pipeline, "gliding to the gate close is the correct behaviour", maintainer 2026-10-08,
 * and its hold, 2026-10-09). Sprudel's `accelerate` is this node too: it fills the accelerate stage `classic()`
 * places, through the flat `accelerate` slot (pitch pipeline step 3). The voice strip's `AccelerateRenderer`, which
 * glided over the scheduled end (the release tail included), retired with it. (Unit changed from octaves to
 * SEMITONES in the pitch-param unification, 2026-08-24; in-repo values migrated x12.)
 *
 * Until the hold (2026-10-09) the node kept rising past the gate at the same rate, so a voice with a release tail
 * ended at `semitones * (gate + tail) / gate`. The hold changed the Ignitor door's sound only there: an authored
 * `accelerate` under a release tail, after the gate. The frames before the gate kept their bits.
 *
 * The law per block: one `pow` seeds the block's first frame, `2^(octaves * (elapsed / gate))`, then one multiply by
 * `2^(octaves / gate)` per sample up to the gate (the per-block seed reassociates the product: block-framing P4,
 * bounded at 1e-11 across framings); the block's frames at or past the gate take the target. The loop splits at the
 * gate, so no frame tests it. A held realtime voice keeps its far scheduled gate as the base (a note-off never moves
 * `voiceDurationFrames`), so its glide stays inert and never reaches the hold. A gate of zero frames (sprudel's
 * `legato(0)`) has arrived at once: the voice holds the target from its first frame (decided by default, maintainer
 * questions Q27, 2026-10-09); the ramp, which holds the only division by the gate, never runs then.
 *
 * Output is passed through [safeOut], the ramp and the hold alike: from about 598 semitones (`12 * log2(1e15)`) the
 * ratio passes `SAFE_MAX` and is clamped there, and past about 12,288 semitones `2^x` would overflow to `+Inf`; the
 * clamp keeps the oscillator phase accumulator finite. See `audio/ref/numerical-safety.md`.
 *
 * @param semitones pitch change in SEMITONES from the onset to the gate close. Positive = rises.
 */
fun accelerateModIgnitor(semitones: Ignitor): Ignitor = AccelerateModIgnitor(semitones)

private class AccelerateModIgnitor(private val semitones: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // NaN-guard: a non-finite amount reads as UNSET, 0 (no glide), the chain `adsr`'s rule (`finiteOr`).
        // Raw, a NaN skips the `== 0.0` bypass and `safeOut` turns every ratio into 0: the oscillator holds
        // still. No clamp.
        val amountVal = finiteOr(value = Ignitors.readParam(semitones, freqHz, ctx), fallback = 0.0) / 12.0 // semitones -> octaves
        val start = ctx.offset
        val end = ctx.windowEnd

        if (amountVal == 0.0) {
            for (i in start until end) {
                buffer[i] = 1.0
            }

            return
        }

        // The window's frames before the gate glide, the rest hold. A gate of 0 frames (or less) holds from the first
        // frame: `framesToGate <= 0`. Int arithmetic with no overflow: the gate is at most the held-voice horizon
        // (`Int.MAX_VALUE / 2`), the elapsed count is non-negative, and the window is at most one block.
        val framesToGate = ctx.voiceDurationFrames - ctx.voiceElapsedFrames
        val rampEnd = when {
            framesToGate <= 0 -> start
            framesToGate >= end - start -> end
            else -> start + framesToGate
        }

        if (rampEnd > start) {
            // Here the gate is longer than the elapsed count, so `totalFrames` is positive.
            val totalFrames = ctx.voiceDurationFramesD
            val startProgress = ctx.voiceElapsedFrames.toDouble() / totalFrames
            val step = 2.0.pow(amountVal / totalFrames)
            var ratio = 2.0.pow(amountVal * startProgress)

            for (i in start until rampEnd) {
                buffer[i] = safeOut(ratio)
                ratio *= step
            }
        }

        if (rampEnd < end) {
            // The hold: the target from the gate frame on, through the release.
            val target = safeOut(2.0.pow(amountVal))

            for (i in rampEnd until end) {
                buffer[i] = target
            }
        }
    }
}

fun accelerateModIgnitor(semitones: Double): Ignitor =
    accelerateModIgnitor(ParamIgnitor("semitones", semitones))

/**
 * Pitch envelope: an ADSR on the pitch ratio, the chain `adsr`'s pattern.
 *
 * Produces `2^(semitones * envLevel / 12)` per sample. The level rises from 0 to 1 over the
 * attack, falls to the sustain over the decay, holds, and from the gate's end falls from the
 * level it had reached back to 0 over the release. The release does NOT extend the voice's life
 * (nothing reports it as a tail), and release `0` returns to the note at the gate frame.
 *
 * **The law** is [EnvelopeCore], the engine's one envelope law (fractional attack and decay frames,
 * the release on `floor(N)` frames, the release starting from the level AT the gate frame, the
 * sustain raw). The level becomes a ratio in [renderPitchEnvelopeRatios]. Sprudel's `penv` is this node too:
 * it fills the pitch envelope stage `classic()` places (pitch pipeline step 1; the voice strip's
 * `PitchEnvelopeRenderer`, which shared the mapping, retired with it).
 *
 * Output is passed through [safeOut] — extreme `amount` values cannot produce
 * `+Inf` ratios that would poison the oscillator phase accumulator.
 * See `audio/ref/numerical-safety.md`.
 *
 * @param semitones semitones of pitch shift at peak
 * @param attack attack time
 * @param decay decay time
 * @param release release time, from the gate's end
 * @param sustain held level, a share of [semitones]; not clamped (the Motor stays raw), and a
 *   non-finite one reads as unset, [PITCH_ENV_SUSTAIN_LEVEL] (`finiteOr`, the chain `adsr`'s rule)
 * @param attackCurve attack shape, [MOD_ENV_CURVE] when the node leaves it unset
 * @param decayCurve decay shape
 * @param releaseCurve release shape
 */
fun pitchEnvelopeModIgnitor(
    attack: Ignitor,
    decay: Ignitor,
    release: Ignitor = ParamIgnitor("release", PITCH_ENV_RELEASE_SEC),
    semitones: Ignitor,
    sustain: Ignitor = ParamIgnitor("sustain", PITCH_ENV_SUSTAIN_LEVEL),
    attackCurve: AdsrCurve = MOD_ENV_CURVE,
    decayCurve: AdsrCurve = MOD_ENV_CURVE,
    releaseCurve: AdsrCurve = MOD_ENV_CURVE,
): Ignitor = PitchEnvelopeModIgnitor(
    attack = attack, decay = decay, release = release, semitones = semitones, sustain = sustain, attackCurve = attackCurve, decayCurve = decayCurve, releaseCurve = releaseCurve,
)

private class PitchEnvelopeModIgnitor(
    private val attack: Ignitor,
    private val decay: Ignitor,
    private val release: Ignitor,
    private val semitones: Ignitor,
    private val sustain: Ignitor,
    private val attackCurve: AdsrCurve,
    private val decayCurve: AdsrCurve,
    private val releaseCurve: AdsrCurve,
) : Ignitor {
    private val core = EnvelopeCore()

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // NaN-guard: a non-finite amount reads as UNSET, 0 (no envelope), the chain `adsr`'s rule
        // (`finiteOr`). Raw, a NaN skips the `== 0.0` bypass and `safeOut` turns every ratio into 0: the
        // oscillator holds still. No clamp.
        val amountVal = finiteOr(value = Ignitors.readParam(semitones, freqHz, ctx), fallback = 0.0)
        val end = ctx.windowEnd

        if (amountVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0
            return
        }

        // Read order is the pre-ADSR order (attack, decay, release, then the sustain in the slot the
        // anchor had): a stateful param subtree advances when it is read.
        val attackVal = Ignitors.readParam(attack, freqHz, ctx)
        val decayVal = Ignitors.readParam(decay, freqHz, ctx)
        val releaseVal = Ignitors.readParam(release, freqHz, ctx)
        // NaN-guard: a non-finite sustain reads as UNSET and takes the shared default, the chain
        // `adsr`'s rule (`finiteOr`). No clamp: every finite sustain passes raw (the Motor stays raw).
        val sustainVal = finiteOr(value = Ignitors.readParam(sustain, freqHz, ctx), fallback = PITCH_ENV_SUSTAIN_LEVEL)

        core.prepare(
            attackFrames = attackVal * ctx.sampleRate,
            decayFrames = decayVal * ctx.sampleRate,
            sustainLevel = sustainVal,
            releaseFrames = releaseVal * ctx.sampleRate,
            gateEndPos = ctx.gateEndFrame,
            attackCurve = attackCurve,
            decayCurve = decayCurve,
            releaseCurve = releaseCurve,
        )

        renderPitchEnvelopeRatios(core = core, amount = amountVal, buffer = buffer, from = ctx.offset, to = end, firstPos = ctx.voiceElapsedFrames)
    }
}

/**
 * THE pitch envelope's level-to-ratio mapping (phase 3 step 5b (c1)): it served two hosts, the Ignitor node
 * ([pitchEnvelopeModIgnitor]) and the voice strip's own pitch envelope, which multiplied into a buffer an earlier
 * pitch stage wrote; the strip's host retired in pitch pipeline step 1, so one host is left and it always writes.
 *
 * Fills `buffer[from until to]` with `safeOut(2^(amount * level / 12))`, where `level` is [core]'s
 * law at the voice-relative frame `firstPos + (i - from)`. [core] must be prepared for this block.
 *
 * Two shortcuts write ONE ratio for the whole block, and each is bit for bit what the per-sample loop
 * would write:
 *  - the block sits wholly in the sustain, before the gate;
 *  - the block is wholly released: the release is complete on its first sample, or it starts from
 *    level 0 (sustain 0, gate after the sweep). In the second case every sample would compute
 *    `0 * shape(x)` for an x in 0..1, a signed zero whatever the curve, and `fastExp2` returns exactly
 *    1.0 for both +0.0 and -0.0.
 *
 * No allocation.
 */
internal fun renderPitchEnvelopeRatios(
    core: EnvelopeCore,
    amount: Double,
    buffer: DoubleArray,
    from: Int,
    to: Int,
    firstPos: Int,
) {
    val gateEndPos = core.gateEndPos
    val lastPos = firstPos + (to - 1 - from)

    val inSustain = firstPos >= core.sustainFrom && lastPos < gateEndPos
    val released = firstPos >= gateEndPos && (core.levelAtGate == 0.0 || core.releaseDone(firstPos - gateEndPos))

    if (inSustain || released) {
        val level = if (inSustain) core.sustain else core.releaseEndLevel()
        val settled = safeOut(fastExp2(amount * level / 12.0))

        for (i in from until to) {
            buffer[i] = settled
        }

        return
    }

    for (i in from until to) {
        buffer[i] = safeOut(fastExp2(amount * core.at(firstPos + (i - from)) / 12.0))
    }
}

/**
 * FM — frequency modulation in ratio space.
 *
 * Generates the [modulator] at `fmFreq * ratio`, scales by `depth / fmFreq`, applies
 * an optional ADSR envelope to the depth (every stage on `MOD_ENV_CURVE`, exponential).
 * Output: `1.0 + modOutput * effectiveDepth / fmFreq`,
 * where `fmFreq` is the [freq] param — DEFAULTING to the note ([FreqIgnitor] answers the freq
 * argument), so default-authored FM behaves exactly as before, while an absolute [freq] makes
 * the patch immune to `detune` like any absolute oscillator (the D13 anchor; the runtime no
 * longer consumes the freq ARGUMENT except to anchor the [freq] read — house convention, see
 * `IgnitorDslWalk`).
 *
 * The `fmFreq` divisor goes through [safeDiv] to handle sub-Hz pitches (e.g. heavy
 * detune toward zero) without producing huge phase-mod ratios. Final output goes
 * through [safeOut]. See `audio/ref/numerical-safety.md`.
 *
 * @param modulator the modulation signal source (any Ignitor subtree)
 * @param ratio frequency ratio between modulator and carrier
 * @param depth modulation depth in Hz
 * @param freq the FM machinery's frequency; default = the note
 *
 * The DSL runtime builds [FmModIgnitor] directly, with the pitch mod above the fm as its modulator reads it
 * ([CarrierFreqMod], pitch pipeline step 3b); this factory builds an fm with no pitch node above it.
 */
fun fmModIgnitor(
    modulator: Ignitor,
    ratio: Ignitor,
    depth: Ignitor,
    attack: Ignitor = ParamIgnitor("attack", 0.0),
    decay: Ignitor = ParamIgnitor("decay", 0.0),
    sustain: Ignitor = ParamIgnitor("sustain", 1.0),
    release: Ignitor = ParamIgnitor("release", 0.0),
    freq: Ignitor = FreqIgnitor,
): Ignitor = FmModIgnitor(modulator = modulator, ratio = ratio, depth = depth, attack = attack, decay = decay, sustain = sustain, release = release, freq = freq, modulatorPitch = null)

/**
 * The fm's mod (see [fmModIgnitor]). [modulatorPitch] is the pitch mod above this fm as its modulator reads it (pitch
 * pipeline step 3b, decision D1), or null when no pitch node sits above the fm: the modulator's sources apply it, so
 * the operator moves as one, and this node pins the carrier's frequency on it every block before it renders the
 * modulator ([CarrierFreqMod]). Internal, as the wrapper is: only the DSL runtime builds one with a [modulatorPitch].
 */
internal class FmModIgnitor(
    private val modulator: Ignitor,
    private val ratio: Ignitor,
    private val depth: Ignitor,
    private val attack: Ignitor,
    private val decay: Ignitor,
    private val sustain: Ignitor,
    private val release: Ignitor,
    private val freq: Ignitor,
    private val modulatorPitch: CarrierFreqMod?,
) : Ignitor {
    private val envCore = EnvelopeCore()

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val end = ctx.windowEnd

        // The freq param is anchored on the incoming argument (FreqIgnitor answers it), read
        // FIRST because it is the bypass condition below.
        val fmFreqVal = Ignitors.readParam(freq, freqHz, ctx)

        // fmFreq's SIGN is stable for the voice's life in the constant-valued forms — the
        // default (FreqIgnitor inherits the argument's per-voice stability: a per-voice val in
        // IgniteRenderer, detune rescales by 2^(s/12), strictly positive), a Constant, and a
        // Param (build-time-folded) — and that is what licenses this bypass: a note-less voice
        // (fmFreq <= 0, e.g. an fm sound triggered via s() without a note) stays note-less
        // forever, no later block where modulator state could matter, the E2 always-advance
        // rationale does not apply, and rendering the graph here would both waste a full
        // modulator render per block and shift the voice's per-voice rng draw order against
        // pre-change content. Bypass entirely, as the old code did. Note the ONE subtree that
        // does advance on a note-less voice is the freq expression itself (read above — it IS
        // the gate input), while modulator/ratio/depth/envs stay frozen: an E2-shaped asymmetry
        // inside this node, accepted with the residuals below.
        // Known accepted residuals (the E2 row's frozen-for-those-blocks shape): a NESTED fm
        // under an outer ratio modulated to <= 0, and a general MODULATED freq expression
        // crossing <= 0 — sign stability does NOT hold for arbitrary expressions.
        // NaN-guard: `!(x > 0.0)` (not `x <= 0.0`) so a NaN freq reads as note-less silence
        // instead of engaging the machinery with a poisoned divisor.
        if (!(fmFreqVal > 0.0)) {
            for (i in ctx.offset until end) {
                buffer[i] = 1.0
            }
            return
        }

        // Every other param reads at the RESOLVED fm frequency — the same actualFreq convention
        // the wave/super oscillators use for their own params.
        // NaN-guard: a non-finite ratio or depth reads as UNSET and takes the node's default (ratio 1,
        // depth 0 = no fm), the chain `adsr`'s rule (`finiteOr`). Raw, a NaN depth skips the `== 0.0`
        // bypass and `safeOut` turns every ratio into 0 (the carrier holds still), and a NaN ratio drives
        // the modulator at a NaN frequency. No clamp.
        val ratioVal = finiteOr(value = Ignitors.readParam(ratio, fmFreqVal, ctx), fallback = FM_RATIO)
        val depthVal = finiteOr(value = Ignitors.readParam(depth, fmFreqVal, ctx), fallback = 0.0)
        // Read — and thereby advance — the env subtrees BEFORE the depth gate below: state moves
        // once per rendered block whatever the output, or a depth passing through zero would
        // freeze a modulated envelope time. The same E2 shape, one level down.
        val attackVal = Ignitors.readParam(attack, fmFreqVal, ctx)
        val decayVal = Ignitors.readParam(decay, fmFreqVal, ctx)
        // NaN-guard: a non-finite sustain reads as UNSET and takes the node's default 1.0, the chain
        // `adsr`'s rule (`finiteOr`); a NaN would otherwise make the depth NaN and `safeOut` would
        // freeze the carrier at ratio 0. The sustain passes raw into the envelope law; the LEVEL is
        // clamped to [0, 1] below, the depth range, as the filter envelope clamps it. The three stage
        // times need no guard: the envelope law reads a NaN or negative time as a zero-length stage,
        // and an infinite one as a stage that never ends, never a NaN.
        val sustainVal = finiteOr(value = Ignitors.readParam(sustain, fmFreqVal, ctx), fallback = 1.0)
        val releaseVal = Ignitors.readParam(release, fmFreqVal, ctx)

        ctx.scratchBuffers.use { modBuf ->
            // The modulator advances FIRST, unconditionally: its state moves once per rendered
            // sample, whatever the output (block-framing contract). The old early return for
            // `depth == 0` skipped it, freezing its phase for whole blocks — a modulated depth
            // passing through zero resumed the modulator from a phase stale by a block-size-
            // dependent amount (block-framing ledger E2).
            // The modulator reads the pitch mod above this fm at the CARRIER's frequency, which is this call's
            // `freqHz` (the carrier's sources render this node): pinned BEFORE the modulator renders (step 3b).
            modulatorPitch?.pin(carrierHz = freqHz)
            modulator.generate(modBuf, fmFreqVal * ratioVal, ctx)

            if (depthVal == 0.0) {
                for (i in ctx.offset until end) {
                    buffer[i] = 1.0
                }
                return
            }

            // release COUNTS: a release-ONLY envelope (attack 0, decay 0, sustain 1) is
            // exactly the E10 remedy shape, and a gate that ignores it drops the release silently
            // — the depth then holds full through the tail and collapses at teardown instead.
            val hasEnv = attackVal > 0.0 || decayVal > 0.0 ||
                sustainVal < 1.0 || releaseVal > 0.0

            // Sub-Hz fmFreq (from heavy detune) would otherwise blow up `effectiveDepth / fmFreq`.
            val safeFreq = safeDiv(fmFreqVal)

            if (!hasEnv) {
                // Op order kept bit-identical to the old code (`mod * depth / freq`).
                for (i in ctx.offset until end) {
                    buffer[i] = safeOut((1.0 + modBuf[i] * depthVal / safeFreq))
                }
                return
            }

            // The depth envelope is evaluated PER SAMPLE (block-framing ledger E1). It used to be
            // evaluated once at the block's first sample and held flat, which snapped every
            // envelope breakpoint to a block boundary: with sgbell's 1 ms attack the first block
            // of every note had ZERO FM, and the peak depth was never produced at any block size
            // unless the attack happened to be a multiple of the block length. The envelope core is
            // prepared once per block and read per sample.
            //
            // `MOD_ENV_CURVE` on every stage, the default of every modulation envelope (decision D3):
            // the FM index envelope has no curve surface yet (`future/envelope-shape-followups.md` §4), so the
            // default is all it gets.
            envCore.prepareModEnvelope(
                ctx = ctx, attack = attackVal, decay = decayVal, sustain = sustainVal, release = releaseVal,
                attackCurve = MOD_ENV_CURVE, decayCurve = MOD_ENV_CURVE, releaseCurve = MOD_ENV_CURVE,
            )

            for (i in ctx.offset until end) {
                val envLevel = envCore.at(ctx.voiceElapsedFrames + (i - ctx.offset)).coerceIn(0.0, 1.0)
                val effectiveDepth = depthVal * envLevel
                buffer[i] = safeOut((1.0 + modBuf[i] * effectiveDepth / safeFreq))
            }
        }
    }
}
