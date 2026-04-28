package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.TWO_PI
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
fun deviationToRatioIgnitor(userMod: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    userMod.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) buffer[i] = buffer[i] + 1.0f
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
 * @param depth modulation depth in semitones
 */
fun vibratoModIgnitor(rate: Ignitor, depth: Ignitor): Ignitor {
    var lfoPhase = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        val rateVal = Ignitors.readParam(rate, freqHz, ctx)
        val depthSemitones = Ignitors.readParam(depth, freqHz, ctx)

        if (depthSemitones <= 0.0) {
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
        }

        val lfoInc = TWO_PI * rateVal / ctx.sampleRateD
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(2.0.pow(sin(lfoPhase) * depthSemitones / 12.0).toFloat())
            lfoPhase += lfoInc
            if (lfoPhase >= TWO_PI) lfoPhase -= TWO_PI
        }
    }
}

fun vibratoModIgnitor(rate: Double, depth: Double): Ignitor =
    vibratoModIgnitor(ParamIgnitor("rate", rate), ParamIgnitor("depth", depth))

/**
 * Accelerate — exponential pitch ramp in ratio space.
 *
 * Produces `2^(amount * progress)` per sample, where progress ramps 0→1 over voice duration.
 * At progress=0: output = 1.0. At progress=1: output = `2^amount`.
 *
 * Output is passed through [safeOut] — large `amount` values can grow `ratio`
 * past `Float.MAX_VALUE` (overflowing to `+Inf`); the safety clamp keeps the
 * oscillator phase accumulator finite. See `audio/ref/numerical-safety.md`.
 *
 * @param amount pitch change exponent over full voice duration. Positive = pitch rises.
 */
fun accelerateModIgnitor(amount: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        val amountVal = Ignitors.readParam(amount, freqHz, ctx)

        val end = ctx.offset + ctx.length

        if (amountVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
        }

        val totalFrames = ctx.voiceDurationFrames.toDouble()
        if (totalFrames <= 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
        }

        val startProgress = ctx.voiceElapsedFrames.toDouble() / totalFrames
        val step = 2.0.pow(amountVal / totalFrames)
        var ratio = 2.0.pow(amountVal * startProgress)

        for (i in ctx.offset until end) {
            buffer[i] = safeOut(ratio.toFloat())
            ratio *= step
        }
    }
}

fun accelerateModIgnitor(amount: Double): Ignitor =
    accelerateModIgnitor(ParamIgnitor("amount", amount))

/**
 * Pitch envelope — ADSR-shaped pitch ratio.
 *
 * Produces `2^(amount * envLevel / 12)` per sample.
 *
 * Output is passed through [safeOut] — extreme `amount` values cannot produce
 * `+Inf` ratios that would poison the oscillator phase accumulator.
 * See `audio/ref/numerical-safety.md`.
 *
 * @param amount semitones of pitch shift at peak
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
    amount: Ignitor,
    curve: Ignitor = ParamIgnitor("curve", 0.0),
    anchor: Ignitor = ParamIgnitor("anchor", 0.0),
): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        val amountVal = Ignitors.readParam(amount, freqHz, ctx)
        val end = ctx.offset + ctx.length

        if (amountVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
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

            buffer[i] = safeOut(2.0.pow((amountVal * envLevel) / 12.0).toFloat())
        }
    }
}

/**
 * FM — frequency modulation in ratio space.
 *
 * Generates the [modulator] at `freqHz * ratio`, scales by `depth / freqHz`, applies
 * an optional ADSR envelope to the depth. Output: `1.0 + modOutput * effectiveDepth / freqHz`.
 *
 * The `freqHz` divisor goes through [safeDiv] to handle sub-Hz pitches (e.g. heavy
 * detune toward zero) without producing huge phase-mod ratios. Final output goes
 * through [safeOut]. See `audio/ref/numerical-safety.md`.
 *
 * @param modulator the modulation signal source (any Ignitor subtree)
 * @param ratio frequency ratio between modulator and carrier
 * @param depth modulation depth in Hz
 */
fun fmModIgnitor(
    modulator: Ignitor,
    ratio: Ignitor,
    depth: Ignitor,
    envAttackSec: Ignitor = ParamIgnitor("envAttackSec", 0.0),
    envDecaySec: Ignitor = ParamIgnitor("envDecaySec", 0.0),
    envSustainLevel: Ignitor = ParamIgnitor("envSustainLevel", 1.0),
    envReleaseSec: Ignitor = ParamIgnitor("envReleaseSec", 0.0),
): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        val end = ctx.offset + ctx.length

        if (freqHz <= 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
        }

        val ratioVal = Ignitors.readParam(ratio, freqHz, ctx)
        val depthVal = Ignitors.readParam(depth, freqHz, ctx)

        if (depthVal == 0.0) {
            for (i in ctx.offset until end) buffer[i] = 1.0f
            return@Ignitor
        }

        val envAttackSecVal = Ignitors.readParam(envAttackSec, freqHz, ctx)
        val envDecaySecVal = Ignitors.readParam(envDecaySec, freqHz, ctx)
        val envSustainLevelVal = Ignitors.readParam(envSustainLevel, freqHz, ctx)
        val envReleaseSecVal = Ignitors.readParam(envReleaseSec, freqHz, ctx)

        val envLevel = if (envAttackSecVal > 0.0 || envDecaySecVal > 0.0 || envSustainLevelVal < 1.0) {
            computeFilterEnvelope(ctx, envAttackSecVal, envDecaySecVal, envSustainLevelVal, envReleaseSecVal)
        } else {
            1.0
        }
        val effectiveDepth = depthVal * envLevel

        ctx.scratchBuffers.use { modBuf ->
            val modFreq = freqHz * ratioVal
            modulator.generate(modBuf, modFreq, ctx)

            // Sub-Hz freqHz (from heavy detune) would otherwise blow up `effectiveDepth / freqHz`.
            val safeFreq = safeDiv(freqHz.toFloat()).toDouble()
            for (i in ctx.offset until end) {
                buffer[i] = safeOut((1.0 + modBuf[i].toDouble() * effectiveDepth / safeFreq).toFloat())
            }
        }
    }
}
