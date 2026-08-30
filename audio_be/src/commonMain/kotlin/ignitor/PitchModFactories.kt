/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.safeDiv
import io.peekandpoke.klang.audio_be.safeOut

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.pow
import kotlin.math.sin

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
        val end = ctx.offset + ctx.length
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
        val rateVal = Ignitors.readParam(rate, freqHz, ctx)
        val depthSemitones = Ignitors.readParam(semitones, freqHz, ctx)
        val end = ctx.offset + ctx.length
        val lfoInc = TWO_PI * rateVal / ctx.sampleRateD

        if (depthSemitones <= 0.0) {
            // The LFO still ADVANCES: state moves once per rendered sample, whatever the output
            // (block-framing contract). The old early return froze the phase for the whole block,
            // so a modulated depth passing through zero resumed the LFO from a phase stale by a
            // block-size-dependent amount (block-framing ledger E2).
            for (i in ctx.offset until end) {
                buffer[i] = 1.0
                lfoPhase += lfoInc
                if (lfoPhase >= TWO_PI) {
                    lfoPhase -= TWO_PI
                }
            }
            return
        }
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(2.0.pow(sin(lfoPhase) * depthSemitones / 12.0))
            lfoPhase += lfoInc
            if (lfoPhase >= TWO_PI) lfoPhase -= TWO_PI
        }
    }
}

fun vibratoModIgnitor(rate: Ignitor, semitones: Ignitor): Ignitor = VibratoModIgnitor(rate, semitones)

fun vibratoModIgnitor(rate: Double, semitones: Double): Ignitor =
    vibratoModIgnitor(ParamIgnitor("rate", rate), ParamIgnitor("semitones", semitones))

/**
 * Accelerate — exponential pitch ramp in ratio space.
 *
 * Produces `2^((semitones/12) * progress)` per sample, where progress ramps 0→1 over the
 * voice duration. At progress=0: output = 1.0. At progress=1: output = `2^(semitones/12)` —
 * `accelerate(12)` ends exactly one octave up. (Unit changed from octaves to SEMITONES in
 * the pitch-param unification, 2026-08-24; in-repo values migrated ×12.)
 *
 * Output is passed through [safeOut] — large values can grow `ratio`
 * past `Float.MAX_VALUE` (overflowing to `+Inf`); the safety clamp keeps the
 * oscillator phase accumulator finite. See `audio/ref/numerical-safety.md`.
 *
 * @param semitones pitch change in SEMITONES over the full voice duration. Positive = rises.
 */
fun accelerateModIgnitor(semitones: Ignitor): Ignitor = AccelerateModIgnitor(semitones)

private class AccelerateModIgnitor(private val semitones: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val amountVal = Ignitors.readParam(semitones, freqHz, ctx) / 12.0 // semitones -> octaves
        val end = ctx.offset + ctx.length

        if (amountVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0
            return
        }

        val totalFrames = ctx.voiceDurationFramesD
        if (totalFrames <= 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0
            return
        }

        val startProgress = ctx.voiceElapsedFrames.toDouble() / totalFrames
        val step = 2.0.pow(amountVal / totalFrames)
        var ratio = 2.0.pow(amountVal * startProgress)

        for (i in ctx.offset until end) {
            buffer[i] = safeOut(ratio)
            ratio *= step
        }
    }
}

fun accelerateModIgnitor(semitones: Double): Ignitor =
    accelerateModIgnitor(ParamIgnitor("semitones", semitones))

/**
 * Pitch envelope — ADSR-shaped pitch ratio.
 *
 * Produces `2^(amount * envLevel / 12)` per sample.
 *
 * Output is passed through [safeOut] — extreme `amount` values cannot produce
 * `+Inf` ratios that would poison the oscillator phase accumulator.
 * See `audio/ref/numerical-safety.md`.
 *
 * @param semitones semitones of pitch shift at peak
 * @param attackSec attack time
 * @param decaySec decay time
 * @param releaseSec release time
 * @param curve envelope curve (not yet implemented — linear)
 * @param anchor starting envelope level: 0.0 = start shifted, 1.0 = start normal
 */
fun pitchEnvelopeModIgnitor(
    attackSec: Ignitor,
    decaySec: Ignitor,
    releaseSec: Ignitor = ParamIgnitor("releaseSec", 0.0),
    semitones: Ignitor,
    curve: Ignitor = ParamIgnitor("curve", 0.0),
    anchor: Ignitor = ParamIgnitor("anchor", 0.0),
): Ignitor = PitchEnvelopeModIgnitor(attackSec, decaySec, releaseSec, semitones, curve, anchor)

private class PitchEnvelopeModIgnitor(
    private val attackSec: Ignitor,
    private val decaySec: Ignitor,
    private val releaseSec: Ignitor,
    private val semitones: Ignitor,
    private val curve: Ignitor,
    private val anchor: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val amountVal = Ignitors.readParam(semitones, freqHz, ctx)
        val end = ctx.offset + ctx.length

        if (amountVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0
            return
        }

        val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx)
        val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val curveVal = Ignitors.readParam(curve, freqHz, ctx)
        val anchorVal = Ignitors.readParam(anchor, freqHz, ctx)

        val attackFrames = attackSecVal * ctx.sampleRate
        val decayFrames = decaySecVal * ctx.sampleRate

        for (i in ctx.offset until end) {
            val sampleOffset = i - ctx.offset
            val relPos = (ctx.voiceElapsedFrames + sampleOffset).toDouble()

            var envLevel = anchorVal
            if (relPos < attackFrames) {
                val progress = if (attackFrames > 0) relPos / attackFrames else 1.0
                envLevel = anchorVal + (1.0 - anchorVal) * progress
            } else if (relPos < (attackFrames + decayFrames)) {
                val decayProgress = if (decayFrames > 0) (relPos - attackFrames) / decayFrames else 1.0
                envLevel = 1.0 - (1.0 - anchorVal) * decayProgress
            }

            buffer[i] = safeOut(2.0.pow((amountVal * envLevel) / 12.0))
        }
    }
}

/**
 * FM — frequency modulation in ratio space.
 *
 * Generates the [modulator] at `fmFreq * ratio`, scales by `depth / fmFreq`, applies
 * an optional ADSR envelope to the depth. Output: `1.0 + modOutput * effectiveDepth / fmFreq`,
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
 */
fun fmModIgnitor(
    modulator: Ignitor,
    ratio: Ignitor,
    depth: Ignitor,
    envAttackSec: Ignitor = ParamIgnitor("envAttackSec", 0.0),
    envDecaySec: Ignitor = ParamIgnitor("envDecaySec", 0.0),
    envSustainLevel: Ignitor = ParamIgnitor("envSustainLevel", 1.0),
    envReleaseSec: Ignitor = ParamIgnitor("envReleaseSec", 0.0),
    freq: Ignitor = FreqIgnitor,
): Ignitor = FmModIgnitor(modulator, ratio, depth, envAttackSec, envDecaySec, envSustainLevel, envReleaseSec, freq)

private class FmModIgnitor(
    private val modulator: Ignitor,
    private val ratio: Ignitor,
    private val depth: Ignitor,
    private val envAttackSec: Ignitor,
    private val envDecaySec: Ignitor,
    private val envSustainLevel: Ignitor,
    private val envReleaseSec: Ignitor,
    private val freq: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val end = ctx.offset + ctx.length

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
        val ratioVal = Ignitors.readParam(ratio, fmFreqVal, ctx)
        val depthVal = Ignitors.readParam(depth, fmFreqVal, ctx)
        // Read — and thereby advance — the env subtrees BEFORE the depth gate below: state moves
        // once per rendered block whatever the output, or a depth passing through zero would
        // freeze a modulated envelope time. The same E2 shape, one level down.
        val envAttackSecVal = Ignitors.readParam(envAttackSec, fmFreqVal, ctx)
        val envDecaySecVal = Ignitors.readParam(envDecaySec, fmFreqVal, ctx)
        val envSustainLevelVal = Ignitors.readParam(envSustainLevel, fmFreqVal, ctx)
        val envReleaseSecVal = Ignitors.readParam(envReleaseSec, fmFreqVal, ctx)

        ctx.scratchBuffers.use { modBuf ->
            // The modulator advances FIRST, unconditionally: its state moves once per rendered
            // sample, whatever the output (block-framing contract). The old early return for
            // `depth == 0` skipped it, freezing its phase for whole blocks — a modulated depth
            // passing through zero resumed the modulator from a phase stale by a block-size-
            // dependent amount (block-framing ledger E2).
            modulator.generate(modBuf, fmFreqVal * ratioVal, ctx)

            if (depthVal == 0.0) {
                for (i in ctx.offset until end) {
                    buffer[i] = 1.0
                }
                return
            }

            // envReleaseSec COUNTS: a release-ONLY envelope (attack 0, decay 0, sustain 1) is
            // exactly the E10 remedy shape, and a gate that ignores it drops the release silently
            // — the depth then holds full through the tail and collapses at teardown instead.
            val hasEnv = envAttackSecVal > 0.0 || envDecaySecVal > 0.0 ||
                envSustainLevelVal < 1.0 || envReleaseSecVal > 0.0

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
            // unless the attack happened to be a multiple of the block length. The envelope is
            // analytic and sample-addressable via `sampleOffsetWithinBlock`, so the hold bought
            // nothing but the bug. Cost: computeFilterEnvelope per sample, on FM-with-envelope
            // voices only.
            for (i in ctx.offset until end) {
                val envLevel = computeFilterEnvelope(
                    ctx, envAttackSecVal, envDecaySecVal, envSustainLevelVal, envReleaseSecVal,
                    sampleOffsetWithinBlock = i - ctx.offset,
                )
                val effectiveDepth = depthVal * envLevel
                buffer[i] = safeOut((1.0 + modBuf[i] * effectiveDepth / safeFreq))
            }
        }
    }
}
