package io.peekandpoke.klang.audio_be.exciter

import kotlin.math.pow

/**
 * Core signal generator interface.
 *
 * A Exciter is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
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

/** Mix two signals additively. Uses a scratch buffer for the second signal. */
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

/** Ring-modulate two signals (multiply sample-by-sample). Uses a scratch buffer. */
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

/** Scale signal amplitude by a factor (Exciter param — audio-rate modulatable). */
fun Exciter.mul(factor: Exciter): Exciter = this * factor

/** Scale signal amplitude by a constant factor. Convenience for [mul] with Exciter. */
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

/** Divide signal amplitude by a divisor (Exciter param). */
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

/** Divide signal amplitude by a constant. Convenience for [div] with Exciter. */
fun Exciter.div(divisor: Double): Exciter = mul(1.0 / divisor)

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] (Exciter param — audio-rate modulatable). */
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

/** Shift frequency by [semitones] (constant). Convenience for [detune] with Exciter. */
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

/** Apply audio-rate gain from another exciter. Skips if gain is constant 1.0. */
fun Exciter.withGain(gain: Exciter): Exciter {
    if (gain is ParamExciter && gain.default == 1.0) return this
    return this * gain
}

/** Shift frequency up one octave. */
fun Exciter.octaveUp(): Exciter = detune(12.0)

/** Shift frequency down one octave. */
fun Exciter.octaveDown(): Exciter = detune(-12.0)
