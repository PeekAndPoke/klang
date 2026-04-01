package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.pow

/**
 * Core signal generator interface.
 *
 * An Ignitor is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
 * Ignitors are composed via extension functions (filters, envelopes, arithmetic, pitch modulation).
 */
fun interface Ignitor {
    /**
     * @param buffer where to write output samples
     * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
     * @param ctx per-voice rendering context (timing, block params, scratch buffers)
     */
    fun generate(buffer: FloatArray, freqHz: Double, ctx: IgniteContext)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════

/** Mix two signals additively per-sample. Uses a scratch buffer for the second signal. */
operator fun Ignitor.plus(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
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
operator fun Ignitor.times(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
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
fun Ignitor.mul(factor: Ignitor): Ignitor = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Ignitor.mul(factor: Double): Ignitor {
    if (factor == 1.0) return this
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)
        val f = factor.toFloat()
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * f
        }
    }
}

/** Divide signal amplitude per-sample by an audio-rate [divisor]. Guards against division by zero. */
fun Ignitor.div(divisor: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        divisor.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val d = tmp[i]
            buffer[i] = if (d != 0.0f) buffer[i] / d else 0.0f
        }
    }
}

/** Divide signal amplitude by a constant [divisor]. Delegates to [mul] with the reciprocal. */
fun Ignitor.div(divisor: Double): Ignitor = mul(1.0 / divisor)

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Ignitor.detune(semitones: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        ctx.scratchBuffers.use { semiBuf ->
            semitones.generate(semiBuf, freqHz, ctx)
            val s = semiBuf[ctx.offset].toDouble()
            val ratio = 2.0.pow(s / 12.0)
            this.generate(buffer, freqHz * ratio, ctx)
        }
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Ignitor.detune(semitones: Double): Ignitor {
    if (semitones == 0.0) return this
    val ratio = 2.0.pow(semitones / 12.0)
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz * ratio, ctx)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Gain from Ignitor (param slot)
// ═══════════════════════════════════════════════════════════════════════════════

/** Apply audio-rate gain from another exciter. Short-circuits when [gain] is a [ParamIgnitor] with default 1.0. */
fun Ignitor.withGain(gain: Ignitor): Ignitor {
    if (gain is ParamIgnitor && gain.default == 1.0) return this
    return this * gain
}

/** Shift frequency up one octave. */
fun Ignitor.octaveUp(): Ignitor = detune(12.0)

/** Shift frequency down one octave. */
fun Ignitor.octaveDown(): Ignitor = detune(-12.0)
