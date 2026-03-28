package io.peekandpoke.klang.audio_be.exciter

import kotlin.math.pow

/**
 * Core signal generator interface.
 *
 * An Exciter is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
 * Exciters are composed via extension functions (filters, envelopes, arithmetic, pitch modulation).
 */
fun interface Exciter {
    /**
     * @param buffer where to write output samples
     * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
     * @param ctx per-voice rendering context (timing, block params, scratch buffers)
     */
    fun generate(buffer: FloatArray, freqHz: Double, ctx: ExciteContext)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════

/** Mix two signals additively per-sample. Uses a scratch buffer for the second signal. */
operator fun Exciter.plus(other: Exciter): Exciter = Exciter { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] + tmp[i]
        }
    }
}

/** Ring-modulate two signals by per-sample multiplication. Uses a scratch buffer for the second signal. */
operator fun Exciter.times(other: Exciter): Exciter = Exciter { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * tmp[i]
        }
    }
}

/** Scale signal amplitude per-sample by an audio-rate [factor]. Delegates to [times]. */
fun Exciter.mul(factor: Exciter): Exciter = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Exciter.mul(factor: Double): Exciter {
    if (factor == 1.0) return this
    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)
        val f = factor.toFloat()
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * f
        }
    }
}

/** Divide signal amplitude per-sample by an audio-rate [divisor]. Uses a scratch buffer. */
fun Exciter.div(divisor: Exciter): Exciter = Exciter { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        divisor.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] / tmp[i]
        }
    }
}

/** Divide signal amplitude by a constant [divisor]. Delegates to [mul] with the reciprocal. */
fun Exciter.div(divisor: Double): Exciter = mul(1.0 / divisor)

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Exciter.detune(semitones: Exciter): Exciter {
    return Exciter { buffer, freqHz, ctx ->
        ctx.scratchBuffers.use { semiBuf ->
            semitones.generate(semiBuf, freqHz, ctx)
            val s = semiBuf[ctx.offset].toDouble()
            val ratio = 2.0.pow(s / 12.0)
            this.generate(buffer, freqHz * ratio, ctx)
        }
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Exciter.detune(semitones: Double): Exciter {
    if (semitones == 0.0) return this
    val ratio = 2.0.pow(semitones / 12.0)
    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz * ratio, ctx)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Gain from Exciter (param slot)
// ═══════════════════════════════════════════════════════════════════════════════

/** Apply audio-rate gain from another exciter. Short-circuits when [gain] is a [ParamExciter] with default 1.0. */
fun Exciter.withGain(gain: Exciter): Exciter {
    if (gain is ParamExciter && gain.default == 1.0) return this
    return this * gain
}

/** Shift frequency up one octave. */
fun Exciter.octaveUp(): Exciter = detune(12.0)

/** Shift frequency down one octave. */
fun Exciter.octaveDown(): Exciter = detune(-12.0)
