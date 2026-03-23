package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.exciter.Exciters.dust
import io.peekandpoke.klang.audio_be.exciter.Exciters.sawtooth
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Factory functions for Exciter oscillator primitives.
 *
 * Each factory returns a fresh Exciter with its own phase state.
 */
object Exciters {

    fun sine(gain: Double = 1.0, analog: Double = 0.0): Exciter {
        var phase = 0.0
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * sin(phase)).toFloat()
                        phase += phaseInc * drift.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * sin(phase)).toFloat()
                        phase += phaseInc * phaseMod[i] * drift.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                }
            } else {
                // Clean digital path (unchanged)
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
    }

    fun sawtooth(gain: Double = 0.6, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * drift.nextMultiplier()
                        var out = 2.0 * phase - 1.0
                        out -= polyBlep(phase, dt)
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * drift.nextMultiplier()
                        var out = 2.0 * phase - 1.0
                        if (dt > BLEP_MIN_DT) out -= polyBlep(phase, dt)
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
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
    }

    fun ramp(gain: Double = 0.6, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * drift.nextMultiplier()
                        // Reverse saw: 1 - (2*phase) = 1..−1, plus inverted PolyBLEP
                        var out = 1.0 - 2.0 * phase
                        out += polyBlep(phase, dt)
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * drift.nextMultiplier()
                        var out = 1.0 - 2.0 * phase
                        if (dt > BLEP_MIN_DT) out += polyBlep(phase, dt)
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        // Reverse saw: 1 - (2*phase) = 1..−1, plus inverted PolyBLEP
                        var out = 1.0 - 2.0 * phase
                        out += polyBlep(phase, inc)
                        buffer[i] = (gain * out).toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i]
                        var out = 1.0 - 2.0 * phase
                        if (dt > BLEP_MIN_DT) out += polyBlep(phase, dt)
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    fun square(gain: Double = 0.5, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * drift.nextMultiplier()
                        // PolyBLEP square: two sawtooths subtracted, shifted by half period
                        var out = if (phase < 0.5) 1.0 else -1.0
                        out += polyBlep(phase, dt)                  // transition at 0
                        out -= polyBlep((phase + 0.5) % 1.0, dt)   // transition at 0.5
                        buffer[i] = (gain * out).toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * drift.nextMultiplier()
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
            } else {
                // Clean digital path (unchanged)
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
    }

    fun triangle(gain: Double = 0.7, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        // Piecewise linear: rising from -1 to +1 in first half, falling in second half
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = (gain * out).toFloat()
                        phase += inc * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = (gain * out).toFloat()
                        phase += inc * phaseMod[i] * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
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
    }

    fun whiteNoise(rng: Random, gain: Double = 1.0): Exciter {
        return Exciter { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = (gain * (rng.nextDouble() * 2.0 - 1.0)).toFloat()
            }
        }
    }

    /** Naive sawtooth without anti-aliasing. Brighter/harsher than [sawtooth] (PolyBLEP). */
    fun zawtooth(gain: Double = 1.0, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * (2.0 * phase - 1.0)).toFloat()
                        phase += inc * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * (2.0 * phase - 1.0)).toFloat()
                        phase += inc * phaseMod[i] * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
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
    }

    /** Impulse: outputs [gain] once per cycle (at phase wrap), 0.0 otherwise. */
    fun impulse(gain: Double = 1.0): Exciter {
        var phase = 0.0
        var lastPhase = Double.POSITIVE_INFINITY

        return Exciter { buffer, freqHz, ctx ->
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

    /** Pulse wave with variable duty cycle. [duty] 0.0..1.0 controls the ratio of high to low. */
    fun pulze(duty: Double = 0.5, gain: Double = 1.0, analog: Double = 0.0): Exciter {
        var phase = 0.0 // Normalized 0..1
        val d = duty.coerceIn(0.0, 1.0)
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (drift.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * if (phase < d) 1.0 else -1.0).toFloat()
                        phase += inc * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * if (phase < d) 1.0 else -1.0).toFloat()
                        phase += inc * phaseMod[i] * drift.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * if (phase < d) 1.0 else -1.0).toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (gain * if (phase < d) 1.0 else -1.0).toFloat()
                        phase += inc * phaseMod[i]
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    /** Brown noise (random walk with leaky integrator). Deeper, rumbly character. */
    fun brownNoise(rng: Random, gain: Double = 1.0): Exciter {
        var out = 0.0

        return Exciter { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val white = rng.nextDouble() * 2.0 - 1.0
                out = (out + 0.02 * white) / 1.02
                buffer[i] = (gain * out).toFloat()
            }
        }
    }

    /** Pink noise (1/f spectrum via Paul Kellet's IIR cascades). */
    fun pinkNoise(rng: Random, gain: Double = 1.0): Exciter {
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0

        return Exciter { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val white = rng.nextDouble() * 2.0 - 1.0
                b0 = 0.99886 * b0 + white * 0.0555179
                b1 = 0.99332 * b1 + white * 0.0750759
                b2 = 0.96900 * b2 + white * 0.1538520
                b3 = 0.86650 * b3 + white * 0.3104856
                b4 = 0.55000 * b4 + white * 0.5329522
                b5 = -0.7616 * b5 - white * 0.0168980
                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                b6 = white * 0.115926
                buffer[i] = (gain * pink * 0.11).toFloat()
            }
        }
    }

    /** Dust: sparse random impulses. [density] 0.0..1.0 controls impulse rate. [maxRateHz] caps the rate. */
    fun dust(rng: Random, density: Double = 0.2, maxRateHz: Double = 200.0, gain: Double = 1.0): Exciter {
        return Exciter { buffer, _, ctx ->
            val d = density.coerceIn(0.0, 1.0)
            val rateHz = d * maxRateHz
            val p = (rateHz / ctx.sampleRateD).coerceIn(0.0, 1.0)
            val end = ctx.offset + ctx.length

            for (i in ctx.offset until end) {
                buffer[i] = if (rng.nextDouble() < p) (gain * rng.nextDouble()).toFloat() else 0.0f
            }
        }
    }

    /** Crackle: sparse random impulses with higher max rate than [dust]. */
    fun crackle(rng: Random, density: Double = 0.2, maxRateHz: Double = 800.0, gain: Double = 1.0): Exciter {
        return dust(rng, density, maxRateHz, gain)
    }

    /**
     * Supersaw: multiple detuned sawtooth oscillators summed together (mono).
     *
     * Ported from legacy [Oscillators.supersawFn]. Key difference: receives freqHz at render time
     * rather than fixed at construction, uses normalized 0..1 phase with PolyBLEP.
     */
    fun superSaw(voices: Int = 5, freqSpread: Double = 0.2, gain: Double = 0.6, analog: Double = 0.0, rng: Random = Random): Exciter {
        val v = voices.coerceIn(1, 32)
        val phases = DoubleArray(v) { rng.nextDouble() }
        val voiceGain = gain / v.toDouble()
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                // Non-modulated: compute detune increments once per block
                val detunes = DoubleArray(v) { n ->
                    val det = getUnisonDetune(v, freqSpread, n)
                    applySemitoneDetuneToFrequency(freqHz, det) / sr
                }

                if (drift.active) {
                    // Analog path: per-sample per-voice jitter
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val baseDt = detunes[0]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            buffer[i] = ((2.0 * p - 1.0 - if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[0] = p
                    }
                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val baseDt = detunes[n]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            buffer[i] = (buffer[i] + (2.0 * p - 1.0 - if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[n] = p
                    }
                } else {
                    // Clean digital path (unchanged)
                    // Voice 0: write (overwrite buffer)
                    run {
                        var p = phases[0]
                        val dt = detunes[0]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = ((2.0 * p - 1.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                buffer[i] = ((2.0 * p - 1.0 - polyBlep(p, dt)) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[0] = p
                    }

                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val dt = detunes[n]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = (buffer[i] + (2.0 * p - 1.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                buffer[i] = (buffer[i] + (2.0 * p - 1.0 - polyBlep(p, dt)) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[n] = p
                    }
                }
            } else {
                // Modulated path: per-sample (jitter on top of modulation)
                for (i in ctx.offset until end) {
                    val mod = phaseMod[i]
                    var sum = 0.0
                    for (n in 0 until v) {
                        var p = phases[n]
                        val det = getUnisonDetune(v, freqSpread, n)
                        var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                        if (drift.active) dt *= drift.nextMultiplier()
                        sum += if (dt <= BLEP_MIN_DT) {
                            2.0 * p - 1.0
                        } else {
                            2.0 * p - 1.0 - polyBlep(p, dt)
                        }
                        p += dt; p = wrapPhase(p, 1.0)
                        phases[n] = p
                    }
                    buffer[i] = (sum * voiceGain).toFloat()
                }
            }
        }
    }

    /**
     * Supersine: multiple detuned sine oscillators summed together (mono).
     *
     * Uses TWO_PI phase with sin() — no anti-aliasing needed (sine is inherently band-limited).
     */
    fun superSine(voices: Int = 5, freqSpread: Double = 0.2, gain: Double = 1.0, analog: Double = 0.0, rng: Random = Random): Exciter {
        val v = voices.coerceIn(1, 32)
        val phases = DoubleArray(v) { rng.nextDouble() * TWO_PI }
        val voiceGain = gain / v.toDouble()
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                val detunes = DoubleArray(v) { n ->
                    val det = getUnisonDetune(v, freqSpread, n)
                    TWO_PI * applySemitoneDetuneToFrequency(freqHz, det) / sr
                }

                if (drift.active) {
                    // Analog path: per-sample per-voice jitter
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val baseInc = detunes[0]
                        for (i in ctx.offset until end) {
                            buffer[i] = (sin(p) * voiceGain).toFloat()
                            p += baseInc * drift.nextMultiplier()
                            p = wrapPhase(p, TWO_PI)
                        }
                        phases[0] = p
                    }
                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val baseInc = detunes[n]
                        for (i in ctx.offset until end) {
                            buffer[i] = (buffer[i] + sin(p) * voiceGain).toFloat()
                            p += baseInc * drift.nextMultiplier()
                            p = wrapPhase(p, TWO_PI)
                        }
                        phases[n] = p
                    }
                } else {
                    // Clean digital path (unchanged)
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val inc = detunes[0]
                        for (i in ctx.offset until end) {
                            buffer[i] = (sin(p) * voiceGain).toFloat()
                            p += inc; p = wrapPhase(p, TWO_PI)
                        }
                        phases[0] = p
                    }

                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val inc = detunes[n]
                        for (i in ctx.offset until end) {
                            buffer[i] = (buffer[i] + sin(p) * voiceGain).toFloat()
                            p += inc; p = wrapPhase(p, TWO_PI)
                        }
                        phases[n] = p
                    }
                }
            } else {
                // Modulated path: per-sample (jitter on top of modulation)
                for (i in ctx.offset until end) {
                    val mod = phaseMod[i]
                    var sum = 0.0
                    for (n in 0 until v) {
                        var p = phases[n]
                        val det = getUnisonDetune(v, freqSpread, n)
                        var inc = TWO_PI * applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                        if (drift.active) inc *= drift.nextMultiplier()
                        sum += sin(p)
                        p += inc; p = wrapPhase(p, TWO_PI)
                        phases[n] = p
                    }
                    buffer[i] = (sum * voiceGain).toFloat()
                }
            }
        }
    }

    /**
     * Supersquare: multiple detuned PolyBLEP square oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with dual PolyBLEP transitions (at 0 and 0.5).
     */
    fun superSquare(voices: Int = 5, freqSpread: Double = 0.2, gain: Double = 0.5, analog: Double = 0.0, rng: Random = Random): Exciter {
        val v = voices.coerceIn(1, 32)
        val phases = DoubleArray(v) { rng.nextDouble() }
        val voiceGain = gain / v.toDouble()
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                val detunes = DoubleArray(v) { n ->
                    val det = getUnisonDetune(v, freqSpread, n)
                    applySemitoneDetuneToFrequency(freqHz, det) / sr
                }

                if (drift.active) {
                    // Analog path: per-sample per-voice jitter
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val baseDt = detunes[0]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            var out = if (p < 0.5) 1.0 else -1.0
                            if (dt > BLEP_MIN_DT) {
                                out += polyBlep(p, dt)
                                out -= polyBlep((p + 0.5) % 1.0, dt)
                            }
                            buffer[i] = (out * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[0] = p
                    }
                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val baseDt = detunes[n]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            var out = if (p < 0.5) 1.0 else -1.0
                            if (dt > BLEP_MIN_DT) {
                                out += polyBlep(p, dt)
                                out -= polyBlep((p + 0.5) % 1.0, dt)
                            }
                            buffer[i] = (buffer[i] + out * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[n] = p
                    }
                } else {
                    // Clean digital path (unchanged)
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val dt = detunes[0]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = ((if (p < 0.5) 1.0 else -1.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                var out = if (p < 0.5) 1.0 else -1.0
                                out += polyBlep(p, dt)
                                out -= polyBlep((p + 0.5) % 1.0, dt)
                                buffer[i] = (out * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[0] = p
                    }

                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val dt = detunes[n]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = (buffer[i] + (if (p < 0.5) 1.0 else -1.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                var out = if (p < 0.5) 1.0 else -1.0
                                out += polyBlep(p, dt)
                                out -= polyBlep((p + 0.5) % 1.0, dt)
                                buffer[i] = (buffer[i] + out * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[n] = p
                    }
                }
            } else {
                // Modulated path: per-sample (jitter on top of modulation)
                for (i in ctx.offset until end) {
                    val mod = phaseMod[i]
                    var sum = 0.0
                    for (n in 0 until v) {
                        var p = phases[n]
                        val det = getUnisonDetune(v, freqSpread, n)
                        var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                        if (drift.active) dt *= drift.nextMultiplier()
                        sum += if (dt <= BLEP_MIN_DT) {
                            if (p < 0.5) 1.0 else -1.0
                        } else {
                            var out = if (p < 0.5) 1.0 else -1.0
                            out += polyBlep(p, dt)
                            out -= polyBlep((p + 0.5) % 1.0, dt)
                            out
                        }
                        p += dt; p = wrapPhase(p, 1.0)
                        phases[n] = p
                    }
                    buffer[i] = (sum * voiceGain).toFloat()
                }
            }
        }
    }

    /**
     * Supertri: multiple detuned triangle oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with piecewise linear triangle — inherently band-limited,
     * no anti-aliasing needed.
     */
    fun superTri(voices: Int = 5, freqSpread: Double = 0.2, gain: Double = 0.7, analog: Double = 0.0, rng: Random = Random): Exciter {
        val v = voices.coerceIn(1, 32)
        val phases = DoubleArray(v) { rng.nextDouble() }
        val voiceGain = gain / v.toDouble()
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                val detunes = DoubleArray(v) { n ->
                    val det = getUnisonDetune(v, freqSpread, n)
                    applySemitoneDetuneToFrequency(freqHz, det) / sr
                }

                if (drift.active) {
                    // Analog path: per-sample per-voice jitter
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val baseDt = detunes[0]
                        for (i in ctx.offset until end) {
                            val out = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            buffer[i] = (out * voiceGain).toFloat()
                            p += baseDt * drift.nextMultiplier()
                            p = wrapPhase(p, 1.0)
                        }
                        phases[0] = p
                    }
                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val baseDt = detunes[n]
                        for (i in ctx.offset until end) {
                            val out = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            buffer[i] = (buffer[i] + out * voiceGain).toFloat()
                            p += baseDt * drift.nextMultiplier()
                            p = wrapPhase(p, 1.0)
                        }
                        phases[n] = p
                    }
                } else {
                    // Clean digital path (unchanged)
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val dt = detunes[0]
                        for (i in ctx.offset until end) {
                            val out = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            buffer[i] = (out * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[0] = p
                    }

                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val dt = detunes[n]
                        for (i in ctx.offset until end) {
                            val out = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            buffer[i] = (buffer[i] + out * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[n] = p
                    }
                }
            } else {
                // Modulated path: per-sample (jitter on top of modulation)
                for (i in ctx.offset until end) {
                    val mod = phaseMod[i]
                    var sum = 0.0
                    for (n in 0 until v) {
                        var p = phases[n]
                        val det = getUnisonDetune(v, freqSpread, n)
                        var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                        if (drift.active) dt *= drift.nextMultiplier()
                        sum += if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                        p += dt; p = wrapPhase(p, 1.0)
                        phases[n] = p
                    }
                    buffer[i] = (sum * voiceGain).toFloat()
                }
            }
        }
    }

    /**
     * Superramp: multiple detuned reverse-sawtooth oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with PolyBLEP anti-aliasing (inverted from supersaw).
     */
    fun superRamp(voices: Int = 5, freqSpread: Double = 0.2, gain: Double = 0.6, analog: Double = 0.0, rng: Random = Random): Exciter {
        val v = voices.coerceIn(1, 32)
        val phases = DoubleArray(v) { rng.nextDouble() }
        val voiceGain = gain / v.toDouble()
        val drift = AnalogDrift(analog)

        return Exciter { buffer, freqHz, ctx ->
            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (phaseMod == null) {
                val detunes = DoubleArray(v) { n ->
                    val det = getUnisonDetune(v, freqSpread, n)
                    applySemitoneDetuneToFrequency(freqHz, det) / sr
                }

                if (drift.active) {
                    // Analog path: per-sample per-voice jitter
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val baseDt = detunes[0]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            buffer[i] = ((1.0 - 2.0 * p + if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[0] = p
                    }
                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val baseDt = detunes[n]
                        for (i in ctx.offset until end) {
                            val dt = baseDt * drift.nextMultiplier()
                            buffer[i] = (buffer[i] + (1.0 - 2.0 * p + if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                            p += dt; p = wrapPhase(p, 1.0)
                        }
                        phases[n] = p
                    }
                } else {
                    // Clean digital path (unchanged)
                    // Voice 0: write
                    run {
                        var p = phases[0]
                        val dt = detunes[0]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = ((1.0 - 2.0 * p) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                buffer[i] = ((1.0 - 2.0 * p + polyBlep(p, dt)) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[0] = p
                    }

                    // Voices 1..v: accumulate
                    for (n in 1 until v) {
                        var p = phases[n]
                        val dt = detunes[n]
                        if (dt <= BLEP_MIN_DT) {
                            for (i in ctx.offset until end) {
                                buffer[i] = (buffer[i] + (1.0 - 2.0 * p) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        } else {
                            for (i in ctx.offset until end) {
                                buffer[i] = (buffer[i] + (1.0 - 2.0 * p + polyBlep(p, dt)) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                        }
                        phases[n] = p
                    }
                }
            } else {
                // Modulated path: per-sample (jitter on top of modulation)
                for (i in ctx.offset until end) {
                    val mod = phaseMod[i]
                    var sum = 0.0
                    for (n in 0 until v) {
                        var p = phases[n]
                        val det = getUnisonDetune(v, freqSpread, n)
                        var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                        if (drift.active) dt *= drift.nextMultiplier()
                        sum += if (dt <= BLEP_MIN_DT) {
                            1.0 - 2.0 * p
                        } else {
                            1.0 - 2.0 * p + polyBlep(p, dt)
                        }
                        p += dt; p = wrapPhase(p, 1.0)
                        phases[n] = p
                    }
                    buffer[i] = (sum * voiceGain).toFloat()
                }
            }
        }
    }

    /** Silence: fills buffer with zeros. */
    fun silence(): Exciter = Exciter { buffer, _, ctx ->
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

    // ═════════════════════════════════════════════════════════════════════════════
    // Unison / supersaw helpers
    // ═════════════════════════════════════════════════════════════════════════════

    internal fun getUnisonDetune(unison: Int, detune: Double, voiceIndex: Int): Double {
        if (unison < 2) return 0.0
        val a = -detune * 0.5
        val b = detune * 0.5
        val n = voiceIndex.toDouble() / (unison - 1).toDouble()
        return n * (b - a) + a
    }

    private fun applySemitoneDetuneToFrequency(frequency: Double, detuneSemitones: Double): Double =
        frequency * 2.0.pow(detuneSemitones / 12.0)

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
