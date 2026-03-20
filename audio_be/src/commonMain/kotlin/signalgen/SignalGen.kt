package io.peekandpoke.klang.audio_be.signalgen

import kotlin.math.pow

/**
 * Core signal generator interface.
 *
 * A SignalGen is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
 *
 * @param buffer where to write output samples
 * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
 * @param ctx per-voice rendering context (timing, block params, scratch buffers)
 */
fun interface SignalGen {
    fun generate(buffer: DoubleArray, freqHz: Double, ctx: SignalContext)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════

/** Mix two signals additively. Uses a scratch buffer for the second signal. */
operator fun SignalGen.plus(other: SignalGen): SignalGen = SignalGen { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] += tmp[i]
        }
    }
}

/** Ring-modulate two signals (multiply sample-by-sample). Uses a scratch buffer. */
operator fun SignalGen.times(other: SignalGen): SignalGen = SignalGen { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] *= tmp[i]
        }
    }
}

/** Scale signal amplitude by a constant factor. */
fun SignalGen.mul(factor: Double): SignalGen = SignalGen { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] *= factor
    }
}

/** Divide signal amplitude by a constant. */
fun SignalGen.div(divisor: Double): SignalGen = mul(1.0 / divisor)

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] (positive = up, negative = down). */
fun SignalGen.detune(semitones: Double): SignalGen {
    val ratio = 2.0.pow(semitones / 12.0)
    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz * ratio, ctx)
    }
}

/** Shift frequency up one octave. */
fun SignalGen.octaveUp(): SignalGen = detune(12.0)

/** Shift frequency down one octave. */
fun SignalGen.octaveDown(): SignalGen = detune(-12.0)
