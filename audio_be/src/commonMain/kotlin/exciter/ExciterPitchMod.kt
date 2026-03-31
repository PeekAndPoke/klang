package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pitch modulation combinators.
 *
 * These modify the frequency reaching inner Exciters by writing
 * per-sample multipliers into ctx.phaseMod.
 *
 * Each combinator owns its own DoubleArray buffer (not from ScratchBuffers)
 * because the buffer must stay alive while the inner generate() runs.
 *
 * Ported from: fillPitchModulation() in voices/common.kt
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Vibrato
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sinusoidal pitch modulation (vibrato).
 *
 * @param rate LFO frequency in Hz
 * @param depth modulation depth (e.g. 0.02 = ±2% frequency deviation)
 */
fun Exciter.vibrato(rate: Exciter, depth: Exciter): Exciter {
    var lfoPhase = 0.0
    var modBuf: DoubleArray? = null

    return Exciter { buffer, freqHz, ctx ->
        val rateVal = Exciters.readParam(rate, freqHz, ctx)
        val depthVal = Exciters.readParam(depth, freqHz, ctx)

        if (depthVal <= 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Exciter
        }

        val bufSize = ctx.offset + ctx.length
        val existing0 = modBuf
        if (existing0 == null || existing0.size < bufSize) {
            modBuf = DoubleArray(bufSize)
        }
        val mb = modBuf ?: error("unreachable")

        val lfoInc = TWO_PI * rateVal / ctx.sampleRateD
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            mb[i] = 1.0 + sin(lfoPhase) * depthVal
            lfoPhase += lfoInc
            if (lfoPhase >= TWO_PI) lfoPhase -= TWO_PI
        }

        // Chain with existing phaseMod
        val existing = ctx.phaseMod
        if (existing != null) {
            for (i in ctx.offset until end) {
                mb[i] *= existing[i]
            }
        }

        val savedMod = ctx.phaseMod
        ctx.phaseMod = mb
        this.generate(buffer, freqHz, ctx)
        ctx.phaseMod = savedMod
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.vibrato(rate: Double, depth: Double): Exciter {
    if (depth <= 0.0) return this
    return vibrato(ParamExciter("rate", rate), ParamExciter("depth", depth))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Accelerate (Exponential Pitch Ramp)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Exponential pitch change over the voice duration.
 *
 * @param amount pitch change exponent over the full voice duration.
 *   `2.0.pow(amount * progress)` gives the frequency multiplier at each point.
 *   E.g., amount=1.0 means frequency doubles over voice duration.
 *   Positive = pitch rises, negative = pitch falls.
 */
fun Exciter.accelerate(amount: Exciter): Exciter {
    var modBuf: DoubleArray? = null

    return Exciter { buffer, freqHz, ctx ->
        val amountVal = Exciters.readParam(amount, freqHz, ctx)

        if (amountVal == 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Exciter
        }

        val bufSize = ctx.offset + ctx.length
        val existing0 = modBuf
        if (existing0 == null || existing0.size < bufSize) {
            modBuf = DoubleArray(bufSize)
        }
        val mb = modBuf ?: error("unreachable")

        val totalFrames = ctx.voiceDurationFrames.toDouble()
        if (totalFrames <= 0.0) {
            this.generate(buffer, freqHz, ctx); return@Exciter
        }
        // Multiplicative stepping: one pow() per block instead of per sample
        val startProgress = ctx.voiceElapsedFrames.toDouble() / totalFrames
        val step = 2.0.pow(amountVal / totalFrames)
        var ratio = 2.0.pow(amountVal * startProgress)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            mb[i] = ratio
            ratio *= step
        }

        // Chain with existing phaseMod
        val existing = ctx.phaseMod
        if (existing != null) {
            for (i in ctx.offset until end) {
                mb[i] *= existing[i]
            }
        }

        val savedMod = ctx.phaseMod
        ctx.phaseMod = mb
        this.generate(buffer, freqHz, ctx)
        ctx.phaseMod = savedMod
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.accelerate(amount: Double): Exciter {
    if (amount == 0.0) return this
    return accelerate(ParamExciter("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Pitch Envelope
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One-shot pitch envelope: anchor → peak → sustain → release.
 *
 * Ported from: PitchEnvelope handling in fillPitchModulation() in voices/common.kt
 *
 * @param attackSec attack time in seconds
 * @param decaySec decay time in seconds
 * @param releaseSec release time in seconds (currently unused in pitch env, kept for parity)
 * @param amount semitones of pitch shift at peak
 * @param curve envelope curve shape (not yet implemented — linear for now)
 * @param anchor starting envelope level: 0.0 = start shifted, 1.0 = start normal
 */
fun Exciter.pitchEnvelope(
    attackSec: Exciter,
    decaySec: Exciter,
    releaseSec: Exciter = ParamExciter("releaseSec", 0.0),
    amount: Exciter,
    curve: Exciter = ParamExciter("curve", 0.0),
    anchor: Exciter = ParamExciter("anchor", 0.0),
): Exciter {
    var modBuf: DoubleArray? = null

    return Exciter { buffer, freqHz, ctx ->
        val amountVal = Exciters.readParam(amount, freqHz, ctx)

        if (amountVal == 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Exciter
        }

        val attackSecVal = Exciters.readParam(attackSec, freqHz, ctx)
        val decaySecVal = Exciters.readParam(decaySec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val releaseSecVal = Exciters.readParam(releaseSec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val curveVal = Exciters.readParam(curve, freqHz, ctx)
        val anchorVal = Exciters.readParam(anchor, freqHz, ctx)

        val bufSize = ctx.offset + ctx.length
        val existing0 = modBuf
        if (existing0 == null || existing0.size < bufSize) {
            modBuf = DoubleArray(bufSize)
        }
        val mb = modBuf ?: error("unreachable")

        val attackFrames = attackSecVal * ctx.sampleRate
        val decayFrames = decaySecVal * ctx.sampleRate

        val end = ctx.offset + ctx.length
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

            // Convert semitones to frequency ratio
            mb[i] = 2.0.pow((amountVal * envLevel) / 12.0)
        }

        // Chain with existing phaseMod
        val existing = ctx.phaseMod
        if (existing != null) {
            for (i in ctx.offset until end) {
                mb[i] *= existing[i]
            }
        }

        val savedMod = ctx.phaseMod
        ctx.phaseMod = mb
        this.generate(buffer, freqHz, ctx)
        ctx.phaseMod = savedMod
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.pitchEnvelope(
    attackSec: Double,
    decaySec: Double,
    releaseSec: Double = 0.0,
    amount: Double,
    curve: Double = 0.0,
    anchor: Double = 0.0,
): Exciter {
    if (amount == 0.0) return this
    return pitchEnvelope(
        attackSec = ParamExciter("attackSec", attackSec),
        decaySec = ParamExciter("decaySec", decaySec),
        releaseSec = ParamExciter("releaseSec", releaseSec),
        amount = ParamExciter("amount", amount),
        curve = ParamExciter("curve", curve),
        anchor = ParamExciter("anchor", anchor),
    )
}
