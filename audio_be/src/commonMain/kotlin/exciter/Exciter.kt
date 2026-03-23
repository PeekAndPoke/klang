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

/** Scale signal amplitude by a constant factor. */
fun Exciter.mul(factor: Double): Exciter = Exciter { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = (buffer[i] * factor).toFloat()
    }
}

/** Divide signal amplitude by a constant. */
fun Exciter.div(divisor: Double): Exciter = mul(1.0 / divisor)

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] (positive = up, negative = down). */
fun Exciter.detune(semitones: Double): Exciter {
    val ratio = 2.0.pow(semitones / 12.0)
    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz * ratio, ctx)
    }
}

/** Shift frequency up one octave. */
fun Exciter.octaveUp(): Exciter = detune(12.0)

/** Shift frequency down one octave. */
fun Exciter.octaveDown(): Exciter = detune(-12.0)
