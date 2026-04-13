package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pitch modulation combinators.
 *
 * These modify the frequency reaching inner Ignitors by writing
 * per-sample multipliers into ctx.phaseMod.
 *
 * Buffers are borrowed from [ScratchBuffers.useDouble] — the buffer stays alive
 * within the scope while the inner generate() runs.
 *
 * Ported from: fillPitchModulation() in voices/common.kt
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Vibrato
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sinusoidal pitch modulation (vibrato).
 *
 * @param rate LFO frequency in Hz (e.g. 5.0 = 5 Hz oscillation)
 * @param depth modulation depth in semitones (e.g. 0.5 = ±0.5 semitone pitch deviation).
 *   Internally converted to a frequency ratio via `depth / 12.0` so that both the Ignitor DSL
 *   and the Sprudel DSL use the same unit.
 */
fun Ignitor.vibrato(rate: Ignitor, depth: Ignitor): Ignitor {
    var lfoPhase = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        val rateVal = Ignitors.readParam(rate, freqHz, ctx)
        val depthSemitones = Ignitors.readParam(depth, freqHz, ctx)

        if (depthSemitones <= 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Ignitor
        }

        ctx.scratchBuffers.useDouble { mb ->
            val lfoInc = TWO_PI * rateVal / ctx.sampleRateD
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                mb[i] = 2.0.pow(sin(lfoPhase) * depthSemitones / 12.0)
                lfoPhase += lfoInc
                if (lfoPhase >= TWO_PI) lfoPhase -= TWO_PI
            }

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
}

/** Double convenience overload — keeps early return optimization. */
fun Ignitor.vibrato(rate: Double, depth: Double): Ignitor {
    if (depth <= 0.0) return this
    return vibrato(ParamIgnitor("rate", rate), ParamIgnitor("depth", depth))
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
fun Ignitor.accelerate(amount: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        val amountVal = Ignitors.readParam(amount, freqHz, ctx)

        if (amountVal == 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Ignitor
        }

        val totalFrames = ctx.voiceDurationFrames.toDouble()
        if (totalFrames <= 0.0) {
            this.generate(buffer, freqHz, ctx); return@Ignitor
        }

        ctx.scratchBuffers.useDouble { mb ->
            val startProgress = ctx.voiceElapsedFrames.toDouble() / totalFrames
            val step = 2.0.pow(amountVal / totalFrames)
            var ratio = 2.0.pow(amountVal * startProgress)

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                mb[i] = ratio
                ratio *= step
            }

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
}

/** Double convenience overload — keeps early return optimization. */
fun Ignitor.accelerate(amount: Double): Ignitor {
    if (amount == 0.0) return this
    return accelerate(ParamIgnitor("amount", amount))
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
fun Ignitor.pitchEnvelope(
    attackSec: Ignitor,
    decaySec: Ignitor,
    releaseSec: Ignitor = ParamIgnitor("releaseSec", 0.0),
    amount: Ignitor,
    curve: Ignitor = ParamIgnitor("curve", 0.0),
    anchor: Ignitor = ParamIgnitor("anchor", 0.0),
): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        val amountVal = Ignitors.readParam(amount, freqHz, ctx)

        if (amountVal == 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Ignitor
        }

        val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx)
        val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx)

        @Suppress("UNUSED_VARIABLE")
        val curveVal = Ignitors.readParam(curve, freqHz, ctx)
        val anchorVal = Ignitors.readParam(anchor, freqHz, ctx)

        ctx.scratchBuffers.useDouble { mb ->
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

                mb[i] = 2.0.pow((amountVal * envLevel) / 12.0)
            }

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
}

/** Double convenience overload — keeps early return optimization. */
fun Ignitor.pitchEnvelope(
    attackSec: Double,
    decaySec: Double,
    releaseSec: Double = 0.0,
    amount: Double,
    curve: Double = 0.0,
    anchor: Double = 0.0,
): Ignitor {
    if (amount == 0.0) return this
    return pitchEnvelope(
        attackSec = ParamIgnitor("attackSec", attackSec),
        decaySec = ParamIgnitor("decaySec", decaySec),
        releaseSec = ParamIgnitor("releaseSec", releaseSec),
        amount = ParamIgnitor("amount", amount),
        curve = ParamIgnitor("curve", curve),
        anchor = ParamIgnitor("anchor", anchor),
    )
}
