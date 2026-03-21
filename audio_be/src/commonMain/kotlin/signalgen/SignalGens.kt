package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.signalgen.SignalGens.sawtooth
import kotlin.math.sin
import kotlin.random.Random

/**
 * Factory functions for SignalGen oscillator primitives.
 *
 * Each factory returns a fresh SignalGen with its own phase state.
 */
object SignalGens {

    fun sine(gain: Double = 1.0): SignalGen {
        var phase = 0.0

        return SignalGen { buffer, freqHz, ctx ->
            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    buffer[i] = (gain * sin(phase)).toFloat()
                    phase += phaseInc
                    phase = wrapPhase(phase, TWO_PI)
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = (gain * sin(phase)).toFloat()
                    phase += phaseInc * phaseMod[i]
                    phase = wrapPhase(phase, TWO_PI)
                }
            }
        }
    }

    fun sawtooth(gain: Double = 0.6): SignalGen {
        var phase = 0.0 // Normalized 0..1

        return SignalGen { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    var out = 2.0 * phase - 1.0
                    out -= polyBlep(phase, inc)
                    buffer[i] = (gain * out).toFloat()
                    phase += inc
                    phase = wrapPhase(phase, 1.0)
                }
            } else {
                for (i in ctx.offset until end) {
                    val dt = inc * phaseMod[i]
                    var out = 2.0 * phase - 1.0
                    if (dt > BLEP_MIN_DT) out -= polyBlep(phase, dt)
                    buffer[i] = (gain * out).toFloat()
                    phase += dt
                    phase = wrapPhase(phase, 1.0)
                }
            }
        }
    }

    fun square(gain: Double = 0.5): SignalGen {
        var phase = 0.0 // Normalized 0..1

        return SignalGen { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    // PolyBLEP square: two sawtooths subtracted, shifted by half period
                    var out = if (phase < 0.5) 1.0 else -1.0
                    out += polyBlep(phase, inc)                  // transition at 0
                    out -= polyBlep((phase + 0.5) % 1.0, inc)   // transition at 0.5
                    buffer[i] = (gain * out).toFloat()
                    phase += inc
                    phase = wrapPhase(phase, 1.0)
                }
            } else {
                for (i in ctx.offset until end) {
                    val dt = inc * phaseMod[i]
                    var out = if (phase < 0.5) 1.0 else -1.0
                    if (dt > BLEP_MIN_DT) {
                        out += polyBlep(phase, dt)
                        out -= polyBlep((phase + 0.5) % 1.0, dt)
                    }
                    buffer[i] = (gain * out).toFloat()
                    phase += dt
                    phase = wrapPhase(phase, 1.0)
                }
            }
        }
    }

    fun triangle(gain: Double = 0.7): SignalGen {
        var phase = 0.0 // Normalized 0..1

        return SignalGen { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    // Piecewise linear: rising from -1 to +1 in first half, falling in second half
                    val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                    buffer[i] = (gain * out).toFloat()
                    phase += inc
                    phase = wrapPhase(phase, 1.0)
                }
            } else {
                for (i in ctx.offset until end) {
                    val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                    buffer[i] = (gain * out).toFloat()
                    phase += inc * phaseMod[i]
                    phase = wrapPhase(phase, 1.0)
                }
            }
        }
    }

    fun whiteNoise(rng: Random, gain: Double = 1.0): SignalGen {
        return SignalGen { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = (gain * (rng.nextDouble() * 2.0 - 1.0)).toFloat()
            }
        }
    }

    /** Naive sawtooth without anti-aliasing. Brighter/harsher than [sawtooth] (PolyBLEP). */
    fun zawtooth(gain: Double = 1.0): SignalGen {
        var phase = 0.0 // Normalized 0..1

        return SignalGen { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    buffer[i] = (gain * (2.0 * phase - 1.0)).toFloat()
                    phase += inc
                    phase = wrapPhase(phase, 1.0)
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = (gain * (2.0 * phase - 1.0)).toFloat()
                    phase += inc * phaseMod[i]
                    phase = wrapPhase(phase, 1.0)
                }
            }
        }
    }

    /** Impulse: outputs [gain] once per cycle (at phase wrap), 0.0 otherwise. */
    fun impulse(gain: Double = 1.0): SignalGen {
        var phase = 0.0
        var lastPhase = Double.POSITIVE_INFINITY

        return SignalGen { buffer, freqHz, ctx ->
            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length
            val gainF = gain.toFloat()

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    buffer[i] = if (phase < lastPhase) gainF else 0.0f
                    lastPhase = phase
                    phase += phaseInc
                    phase = wrapPhase(phase, TWO_PI)
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = if (phase < lastPhase) gainF else 0.0f
                    lastPhase = phase
                    phase += phaseInc * phaseMod[i]
                    phase = wrapPhase(phase, TWO_PI)
                }
            }
        }
    }

    /** Silence: fills buffer with zeros. */
    fun silence(): SignalGen = SignalGen { buffer, _, ctx ->
        buffer.fill(0.0f, ctx.offset, ctx.offset + ctx.length)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════════════

    /** Minimum dt for PolyBLEP — avoids division by zero at freqHz=0 or negative phaseMod. */
    private const val BLEP_MIN_DT = 1e-5

    /** Wraps phase into [0, period). Handles both positive overflow and negative values. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun wrapPhase(phase: Double, period: Double): Double {
        var p = phase
        while (p >= period) p -= period
        while (p < 0.0) p += period
        return p
    }

    /**
     * First-order PolyBLEP residual for anti-aliased discontinuities.
     * [t] is the normalized phase (0..1), [dt] is the normalized phase increment per sample.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun polyBlep(t: Double, dt: Double): Double {
        var correction = 0.0

        if (t < dt) {
            val r = t / dt
            correction += r + r - r * r - 1.0
        }

        if (t > 1.0 - dt) {
            val r = (t - 1.0) / dt
            correction += r * r + r + r + 1.0
        }

        return correction
    }
}
