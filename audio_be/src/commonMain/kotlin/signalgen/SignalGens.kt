package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin
import kotlin.random.Random

/**
 * Factory functions for SignalGen oscillator primitives.
 *
 * Each factory returns a fresh SignalGen with its own phase state.
 * These are native reimplementations (not wrappers around OscFn) to avoid double-bridging.
 *
 * TEMPORARY: SignalGen POC bridge — these will become the primary oscillator implementations
 * when SignalGen replaces OscFn.
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
                    buffer[i] = gain * sin(phase)
                    phase += phaseInc
                    if (phase >= TWO_PI) phase -= TWO_PI
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = gain * sin(phase)
                    phase += phaseInc * phaseMod[i]
                    if (phase >= TWO_PI) phase -= TWO_PI
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
                    buffer[i] = gain * out
                    phase += inc
                    if (phase >= 1.0) phase -= 1.0
                }
            } else {
                for (i in ctx.offset until end) {
                    val dt = inc * phaseMod[i]
                    var out = 2.0 * phase - 1.0
                    out -= polyBlep(phase, dt)
                    buffer[i] = gain * out
                    phase += dt
                    if (phase >= 1.0) phase -= 1.0
                }
            }
        }
    }

    fun square(gain: Double = 0.5): SignalGen {
        var phase = 0.0

        return SignalGen { buffer, freqHz, ctx ->
            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    buffer[i] = gain * if (sin(phase) >= 0.0) 1.0 else -1.0
                    phase += phaseInc
                    if (phase >= TWO_PI) phase -= TWO_PI
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = gain * if (sin(phase) >= 0.0) 1.0 else -1.0
                    phase += phaseInc * phaseMod[i]
                    if (phase >= TWO_PI) phase -= TWO_PI
                }
            }
        }
    }

    fun triangle(gain: Double = 0.7): SignalGen {
        var phase = 0.0
        val norm = 2.0 / PI

        return SignalGen { buffer, freqHz, ctx ->
            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                for (i in ctx.offset until end) {
                    buffer[i] = gain * norm * asin(sin(phase))
                    phase += phaseInc
                    if (phase >= TWO_PI) phase -= TWO_PI
                }
            } else {
                for (i in ctx.offset until end) {
                    buffer[i] = gain * norm * asin(sin(phase))
                    phase += phaseInc * phaseMod[i]
                    if (phase >= TWO_PI) phase -= TWO_PI
                }
            }
        }
    }

    fun whiteNoise(rng: Random, gain: Double = 1.0): SignalGen {
        return SignalGen { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = gain * (rng.nextDouble() * 2.0 - 1.0)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // PolyBLEP (copy from Oscillators — kept private to avoid coupling)
    // ═════════════════════════════════════════════════════════════════════════════

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
