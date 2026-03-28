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

    fun sine(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase).toFloat()
                        phase += phaseInc * d.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase).toFloat()
                        phase += phaseInc * phaseMod[i] * d.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                }
            } else {
                // Clean digital path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase).toFloat()
                        phase += phaseInc
                        phase = wrapPhase(phase, TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase).toFloat()
                        phase += phaseInc * phaseMod[i]
                        phase = wrapPhase(phase, TWO_PI)
                    }
                }
            }
        }
    }

    fun sawtooth(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * d.nextMultiplier()
                        var out = 2.0 * phase - 1.0
                        out -= polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * d.nextMultiplier()
                        var out = 2.0 * phase - 1.0
                        if (dt > BLEP_MIN_DT) out -= polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        var out = 2.0 * phase - 1.0
                        out -= polyBlep(phase, inc)
                        buffer[i] = out.toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i]
                        var out = 2.0 * phase - 1.0
                        if (dt > BLEP_MIN_DT) out -= polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    fun ramp(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * d.nextMultiplier()
                        var out = 1.0 - 2.0 * phase
                        out += polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * d.nextMultiplier()
                        var out = 1.0 - 2.0 * phase
                        if (dt > BLEP_MIN_DT) out += polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        var out = 1.0 - 2.0 * phase
                        out += polyBlep(phase, inc)
                        buffer[i] = out.toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i]
                        var out = 1.0 - 2.0 * phase
                        if (dt > BLEP_MIN_DT) out += polyBlep(phase, dt)
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    fun square(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        val dt = inc * d.nextMultiplier()
                        // PolyBLEP square: two sawtooths subtracted, shifted by half period
                        var out = if (phase < 0.5) 1.0 else -1.0
                        out += polyBlep(phase, dt)                  // transition at 0
                        out -= polyBlep((phase + 0.5) % 1.0, dt)   // transition at 0.5
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val dt = inc * phaseMod[i] * d.nextMultiplier()
                        var out = if (phase < 0.5) 1.0 else -1.0
                        if (dt > BLEP_MIN_DT) {
                            out += polyBlep(phase, dt)
                            out -= polyBlep((phase + 0.5) % 1.0, dt)
                        }
                        buffer[i] = out.toFloat()
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
                        buffer[i] = out.toFloat()
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
                        buffer[i] = out.toFloat()
                        phase += dt
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    fun triangle(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        // Piecewise linear: rising from -1 to +1 in first half, falling in second half
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = out.toFloat()
                        phase += inc * d.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = out.toFloat()
                        phase += inc * phaseMod[i] * d.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        // Piecewise linear: rising from -1 to +1 in first half, falling in second half
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = out.toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        val out = if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                        buffer[i] = out.toFloat()
                        phase += inc * phaseMod[i]
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    fun whiteNoise(rng: Random): Exciter {
        return Exciter { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = (rng.nextDouble() * 2.0 - 1.0).toFloat()
            }
        }
    }

    /** Naive sawtooth without anti-aliasing. Brighter/harsher than [sawtooth] (PolyBLEP). */
    fun zawtooth(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val inc = freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (2.0 * phase - 1.0).toFloat()
                        phase += inc * d.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (2.0 * phase - 1.0).toFloat()
                        phase += inc * phaseMod[i] * d.nextMultiplier()
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            } else {
                // Clean digital path (unchanged)
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = (2.0 * phase - 1.0).toFloat()
                        phase += inc
                        phase = wrapPhase(phase, 1.0)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = (2.0 * phase - 1.0).toFloat()
                        phase += inc * phaseMod[i]
                        phase = wrapPhase(phase, 1.0)
                    }
                }
            }
        }
    }

    /** Impulse: outputs 1.0 once per cycle (at phase wrap), 0.0 otherwise. */
    fun impulse(analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0
        var lastPhase = Double.POSITIVE_INFINITY
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            val phaseInc = TWO_PI * freqHz / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                // Analog drift path: humanized timing irregularity
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0f else 0.0f
                        lastPhase = phase
                        phase += phaseInc * d.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0f else 0.0f
                        lastPhase = phase
                        phase += phaseInc * phaseMod[i] * d.nextMultiplier()
                        phase = wrapPhase(phase, TWO_PI)
                    }
                }
            } else {
                // Clean digital path (unchanged)
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0f else 0.0f
                        lastPhase = phase
                        phase += phaseInc
                        phase = wrapPhase(phase, TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0f else 0.0f
                        lastPhase = phase
                        phase += phaseInc * phaseMod[i]
                        phase = wrapPhase(phase, TWO_PI)
                    }
                }
            }
        }
    }

    /**
     * Pulse wave with variable duty cycle and PolyBLEP anti-aliasing.
     * [duty] 0.0..1.0 controls the ratio of high to low.
     * Applies PolyBLEP at both transitions (0 and duty) to reduce aliasing.
     */
    fun pulze(duty: Exciter = ParamExciter("duty", 0.5), analog: Exciter = ParamExciter("analog", 0.0)): Exciter {
        var phase = 0.0 // Normalized 0..1
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val dr = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { dutyBuf ->
                duty.generate(dutyBuf, freqHz, ctx)

                val inc = freqHz / ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (dr.active) {
                    if (phaseMod == null) {
                        for (i in ctx.offset until end) {
                            val d = dutyBuf[i].toDouble().coerceIn(0.01, 0.99)
                            val dt = inc * dr.nextMultiplier()
                            var out = if (phase < d) 1.0 else -1.0
                            out += polyBlep(phase, dt)
                            out -= polyBlep((phase + (1.0 - d)) % 1.0, dt)
                            buffer[i] = out.toFloat()
                            phase += dt
                            phase = wrapPhase(phase, 1.0)
                        }
                    } else {
                        for (i in ctx.offset until end) {
                            val d = dutyBuf[i].toDouble().coerceIn(0.01, 0.99)
                            val dt = inc * phaseMod[i] * dr.nextMultiplier()
                            var out = if (phase < d) 1.0 else -1.0
                            if (dt > BLEP_MIN_DT) {
                                out += polyBlep(phase, dt)
                                out -= polyBlep((phase + (1.0 - d)) % 1.0, dt)
                            }
                            buffer[i] = out.toFloat()
                            phase += dt
                            phase = wrapPhase(phase, 1.0)
                        }
                    }
                } else {
                    if (phaseMod == null) {
                        for (i in ctx.offset until end) {
                            val d = dutyBuf[i].toDouble().coerceIn(0.01, 0.99)
                            var out = if (phase < d) 1.0 else -1.0
                            out += polyBlep(phase, inc)
                            out -= polyBlep((phase + (1.0 - d)) % 1.0, inc)
                            buffer[i] = out.toFloat()
                            phase += inc
                            phase = wrapPhase(phase, 1.0)
                        }
                    } else {
                        for (i in ctx.offset until end) {
                            val d = dutyBuf[i].toDouble().coerceIn(0.01, 0.99)
                            val dt = inc * phaseMod[i]
                            var out = if (phase < d) 1.0 else -1.0
                            if (dt > BLEP_MIN_DT) {
                                out += polyBlep(phase, dt)
                                out -= polyBlep((phase + (1.0 - d)) % 1.0, dt)
                            }
                            buffer[i] = out.toFloat()
                            phase += dt
                            phase = wrapPhase(phase, 1.0)
                        }
                    }
                }
            }
        }
    }

    /** Brown noise (random walk with leaky integrator). Deeper, rumbly character. */
    fun brownNoise(rng: Random): Exciter {
        var out = 0.0

        return Exciter { buffer, _, ctx ->
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val white = rng.nextDouble() * 2.0 - 1.0
                out = (out + 0.02 * white) / 1.02
                buffer[i] = out.toFloat()
            }
        }
    }

    /** Pink noise (1/f spectrum via Paul Kellet's IIR cascades). */
    fun pinkNoise(rng: Random): Exciter {
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
                buffer[i] = (pink * 0.11).toFloat()
            }
        }
    }

    /** Dust: sparse random impulses. [density] 0.0..1.0 controls impulse rate. [maxRateHz] caps the rate. */
    fun dust(rng: Random, density: Exciter = ParamExciter("density", 0.2), maxRateHz: Double = 200.0): Exciter {
        return Exciter { buffer, _, ctx ->
            ctx.scratchBuffers.use { densityBuf ->
                density.generate(densityBuf, 0.0, ctx)
                val d = densityBuf[ctx.offset].toDouble().coerceIn(0.0, 1.0)
                val rateHz = d * maxRateHz
                val p = (rateHz / ctx.sampleRateD).coerceIn(0.0, 1.0)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = if (rng.nextDouble() < p) rng.nextDouble().toFloat() else 0.0f
                }
            }
        }
    }

    /** Crackle: sparse random impulses with higher max rate than [dust]. */
    fun crackle(rng: Random, density: Exciter = ParamExciter("density", 0.2), maxRateHz: Double = 800.0): Exciter {
        return dust(rng, density, maxRateHz)
    }

    /**
     * Supersaw: multiple detuned sawtooth oscillators summed together (mono).
     *
     * Ported from legacy [Oscillators.supersawFn]. Key difference: receives freqHz at render time
     * rather than fixed at construction, uses normalized 0..1 phase with PolyBLEP.
     */
    fun superSaw(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        analog: Exciter = ParamExciter("analog", 0.0),
        rng: Random = Random
    ): Exciter {
        var v = 0
        var phases = DoubleArray(0)
        var voiceGain = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = phases
                    phases = DoubleArray(v) { i -> if (i < old.size) old[i] else rng.nextDouble() }
                }

            ctx.scratchBuffers.use { spreadBuf ->
                freqSpread.generate(spreadBuf, freqHz, ctx)
                val spread = spreadBuf[ctx.offset].toDouble()

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (phaseMod == null) {
                    // Non-modulated: compute detune increments once per block
                    val detunes = DoubleArray(v) { n ->
                        val det = getUnisonDetune(v, spread, n)
                        applySemitoneDetuneToFrequency(freqHz, det) / sr
                    }

                    if (d.active) {
                        // Analog path: per-sample per-voice jitter
                        // Voice 0: write
                        run {
                            var p = phases[0]
                            val baseDt = detunes[0]
                            for (i in ctx.offset until end) {
                                val dt = baseDt * d.nextMultiplier()
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
                                val dt = baseDt * d.nextMultiplier()
                                buffer[i] =
                                    (buffer[i] + (2.0 * p - 1.0 - if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                            phases[n] = p
                        }
                    } else {
                        // Clean digital path
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
                            val det = getUnisonDetune(v, spread, n)
                            var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                            if (d.active) dt *= d.nextMultiplier()
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
        }
    }

    /**
     * Supersine: multiple detuned sine oscillators summed together (mono).
     *
     * Uses TWO_PI phase with sin() — no anti-aliasing needed (sine is inherently band-limited).
     */
    fun superSine(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        analog: Exciter = ParamExciter("analog", 0.0),
        rng: Random = Random
    ): Exciter {
        var v = 0
        var phases = DoubleArray(0)
        var voiceGain = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = phases
                    phases = DoubleArray(v) { i -> if (i < old.size) old[i] else rng.nextDouble() * TWO_PI }
                }
                if (v <= 0) {
                    buffer.fill(0f, ctx.offset, ctx.offset + ctx.length); return@use
                }

            ctx.scratchBuffers.use { spreadBuf ->
                freqSpread.generate(spreadBuf, freqHz, ctx)
                val spread = spreadBuf[ctx.offset].toDouble()

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (phaseMod == null) {
                    val detunes = DoubleArray(v) { n ->
                        val det = getUnisonDetune(v, spread, n)
                        TWO_PI * applySemitoneDetuneToFrequency(freqHz, det) / sr
                    }

                    if (d.active) {
                        // Analog path: per-sample per-voice jitter
                        // Voice 0: write
                        run {
                            var p = phases[0]
                            val baseInc = detunes[0]
                            for (i in ctx.offset until end) {
                                buffer[i] = (sin(p) * voiceGain).toFloat()
                                p += baseInc * d.nextMultiplier()
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
                                p += baseInc * d.nextMultiplier()
                                p = wrapPhase(p, TWO_PI)
                            }
                            phases[n] = p
                        }
                    } else {
                        // Clean digital path
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
                            val det = getUnisonDetune(v, spread, n)
                            var inc = TWO_PI * applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                            if (d.active) inc *= d.nextMultiplier()
                            sum += sin(p)
                            p += inc; p = wrapPhase(p, TWO_PI)
                            phases[n] = p
                        }
                        buffer[i] = (sum * voiceGain).toFloat()
                    }
                }
            }
            }
        }
    }

    /**
     * Supersquare: multiple detuned PolyBLEP square oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with dual PolyBLEP transitions (at 0 and 0.5).
     */
    fun superSquare(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        analog: Exciter = ParamExciter("analog", 0.0),
        rng: Random = Random
    ): Exciter {
        var v = 0
        var phases = DoubleArray(0)
        var voiceGain = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = phases
                    phases = DoubleArray(v) { i -> if (i < old.size) old[i] else rng.nextDouble() }
                }
                if (v <= 0) {
                    buffer.fill(0f, ctx.offset, ctx.offset + ctx.length); return@use
                }

            ctx.scratchBuffers.use { spreadBuf ->
                freqSpread.generate(spreadBuf, freqHz, ctx)
                val spread = spreadBuf[ctx.offset].toDouble()

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (phaseMod == null) {
                    val detunes = DoubleArray(v) { n ->
                        val det = getUnisonDetune(v, spread, n)
                        applySemitoneDetuneToFrequency(freqHz, det) / sr
                    }

                    if (d.active) {
                        // Analog path: per-sample per-voice jitter
                        // Voice 0: write
                        run {
                            var p = phases[0]
                            val baseDt = detunes[0]
                            for (i in ctx.offset until end) {
                                val dt = baseDt * d.nextMultiplier()
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
                                val dt = baseDt * d.nextMultiplier()
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
                        // Clean digital path
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
                            val det = getUnisonDetune(v, spread, n)
                            var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                            if (d.active) dt *= d.nextMultiplier()
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
        }
    }

    /**
     * Supertri: multiple detuned triangle oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with piecewise linear triangle — inherently band-limited,
     * no anti-aliasing needed.
     */
    fun superTri(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        analog: Exciter = ParamExciter("analog", 0.0),
        rng: Random = Random
    ): Exciter {
        var v = 0
        var phases = DoubleArray(0)
        var voiceGain = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = phases
                    phases = DoubleArray(v) { i -> if (i < old.size) old[i] else rng.nextDouble() }
                }
                if (v <= 0) {
                    buffer.fill(0f, ctx.offset, ctx.offset + ctx.length); return@use
                }

            ctx.scratchBuffers.use { spreadBuf ->
                freqSpread.generate(spreadBuf, freqHz, ctx)
                val spread = spreadBuf[ctx.offset].toDouble()

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (phaseMod == null) {
                    val detunes = DoubleArray(v) { n ->
                        val det = getUnisonDetune(v, spread, n)
                        applySemitoneDetuneToFrequency(freqHz, det) / sr
                    }

                    if (d.active) {
                        // Analog path: per-sample per-voice jitter
                        // Voice 0: write
                        run {
                            var p = phases[0]
                            val baseDt = detunes[0]
                            for (i in ctx.offset until end) {
                                val out = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                                buffer[i] = (out * voiceGain).toFloat()
                                p += baseDt * d.nextMultiplier()
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
                                p += baseDt * d.nextMultiplier()
                                p = wrapPhase(p, 1.0)
                            }
                            phases[n] = p
                        }
                    } else {
                        // Clean digital path
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
                            val det = getUnisonDetune(v, spread, n)
                            var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                            if (d.active) dt *= d.nextMultiplier()
                            sum += if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            p += dt; p = wrapPhase(p, 1.0)
                            phases[n] = p
                        }
                        buffer[i] = (sum * voiceGain).toFloat()
                    }
                }
            }
            }
        }
    }

    /**
     * Superramp: multiple detuned reverse-sawtooth oscillators summed together (mono).
     *
     * Uses normalized 0..1 phase with PolyBLEP anti-aliasing (inverted from supersaw).
     */
    fun superRamp(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        analog: Exciter = ParamExciter("analog", 0.0),
        rng: Random = Random
    ): Exciter {
        var v = 0
        var phases = DoubleArray(0)
        var voiceGain = 0.0
        var drift: AnalogDrift? = null

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = phases
                    phases = DoubleArray(v) { i -> if (i < old.size) old[i] else rng.nextDouble() }
                }
                if (v <= 0) {
                    buffer.fill(0f, ctx.offset, ctx.offset + ctx.length); return@use
                }

            ctx.scratchBuffers.use { spreadBuf ->
                freqSpread.generate(spreadBuf, freqHz, ctx)
                val spread = spreadBuf[ctx.offset].toDouble()

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (phaseMod == null) {
                    val detunes = DoubleArray(v) { n ->
                        val det = getUnisonDetune(v, spread, n)
                        applySemitoneDetuneToFrequency(freqHz, det) / sr
                    }

                    if (d.active) {
                        // Analog path: per-sample per-voice jitter
                        // Voice 0: write
                        run {
                            var p = phases[0]
                            val baseDt = detunes[0]
                            for (i in ctx.offset until end) {
                                val dt = baseDt * d.nextMultiplier()
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
                                val dt = baseDt * d.nextMultiplier()
                                buffer[i] =
                                    (buffer[i] + (1.0 - 2.0 * p + if (dt > BLEP_MIN_DT) polyBlep(p, dt) else 0.0) * voiceGain).toFloat()
                                p += dt; p = wrapPhase(p, 1.0)
                            }
                            phases[n] = p
                        }
                    } else {
                        // Clean digital path
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
                            val det = getUnisonDetune(v, spread, n)
                            var dt = applySemitoneDetuneToFrequency(freqHz, det) / sr * mod
                            if (d.active) dt *= d.nextMultiplier()
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
        }
    }

    /**
     * Karplus-Strong plucked string synthesis.
     *
     * A short noise burst excites a delay line with filtered feedback, producing
     * realistic plucked string sounds. Extended KS adds pick position modeling
     * and allpass stiffness filtering.
     *
     * @param decay Feedback amount (0.9–0.999). Higher = longer ring.
     * @param brightness Lowpass cutoff in feedback (0.0 = dark, 1.0 = bright).
     * @param pickPosition Where the pluck occurs (0.0–1.0). Affects harmonic content.
     * @param stiffness Higher harmonics decay faster (0.0 = nylon, 1.0 = piano wire). Bypassed when 0.
     * @param analog Perlin noise pitch drift amount.
     */
    fun karplusStrong(
        decay: Exciter = ParamExciter("decay", 0.996),
        brightness: Exciter = ParamExciter("brightness", 0.5),
        pickPosition: Exciter = ParamExciter("pickPosition", 0.5),
        stiffness: Exciter = ParamExciter("stiffness", 0.0),
        analog: Exciter = ParamExciter("analog", 0.0),
    ): Exciter {
        // Max delay line: supports down to ~20 Hz at 48kHz (2400 samples)
        val maxDelay = 2500
        val delayLine = FloatArray(maxDelay)
        var writePos = 0
        var excited = false
        var drift: AnalogDrift? = null

        // One-pole lowpass state for brightness filtering
        var lpState = 0.0

        // Allpass state for stiffness
        var apPrevIn = 0.0
        var apPrevOut = 0.0

        val rng = kotlin.random.Random

        return Exciter { buffer, freqHz, ctx ->
            val d = drift ?: initAnalogDrift(analog, freqHz, ctx).also { drift = it }

            // Read control-rate params once per block
            val decayVal = readParam(decay, freqHz, ctx)
            val brightnessVal = readParam(brightness, freqHz, ctx)
            val stiffnessVal = readParam(stiffness, freqHz, ctx)

            val lpAlpha = brightnessVal.coerceIn(0.01, 1.0)
            val hasStiffness = stiffnessVal > 0.0
            val apCoeff = stiffnessVal.coerceIn(0.0, 0.99) * 0.5

            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            // Delay length in fractional samples (determines pitch)
            val baseDelay = (sr / freqHz).coerceIn(2.0, (maxDelay - 1).toDouble())

            // Excite on first call: fill delay line with noise
            if (!excited) {
                excited = true
                val pickPosVal = readParam(pickPosition, freqHz, ctx)
                val delayLen = baseDelay.toInt()

                // Pick position affects which part of the buffer gets excited
                // pickPosition 0.0 = narrow burst at start (bridge-like, thin)
                // pickPosition 0.5 = full buffer (middle, rich harmonics)
                // pickPosition 1.0 = narrow burst at end (neck-like, warm)
                val pp = pickPosVal.coerceIn(0.0, 1.0)
                val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
                val burstStart = ((delayLen - burstLen) * pp).toInt()

                for (j in 0 until delayLen) {
                    delayLine[j] = if (j >= burstStart && j < burstStart + burstLen) {
                        (rng.nextDouble() * 2.0 - 1.0).toFloat()
                    } else {
                        0.0f
                    }
                }
                writePos = delayLen % maxDelay
            }

            for (i in ctx.offset until end) {
                // Calculate effective delay (with pitch modulation and analog drift)
                var dl = baseDelay
                if (phaseMod != null) dl /= phaseMod[i]
                if (d.active) dl /= d.nextMultiplier()
                dl = dl.coerceIn(2.0, (maxDelay - 1).toDouble())

                // Read with linear interpolation
                val readPosF = writePos - dl
                val readPosWrapped = if (readPosF < 0) readPosF + maxDelay else readPosF
                val readIdx = readPosWrapped.toInt() % maxDelay
                val frac = readPosWrapped - readPosWrapped.toInt()
                val nextIdx = (readIdx + 1) % maxDelay
                val sample = delayLine[readIdx] + (delayLine[nextIdx] - delayLine[readIdx]) * frac.toFloat()

                // One-pole lowpass (brightness)
                lpState = lpState + lpAlpha * (sample.toDouble() - lpState)
                var filtered = lpState

                // Allpass stiffness filter (bypassed when stiffness = 0)
                if (hasStiffness) {
                    val apOut = apCoeff * (filtered - apPrevOut) + apPrevIn
                    apPrevIn = filtered
                    apPrevOut = apOut
                    filtered = apOut
                }

                // Write back with decay
                delayLine[writePos] = (filtered * decayVal).toFloat()

                // Output
                buffer[i] = sample

                // Advance write position
                writePos = (writePos + 1) % maxDelay
            }
        }
    }

    /**
     * Super Karplus-Strong: multiple detuned plucked strings summed together.
     *
     * Like a 12-string guitar or a chorus of harps — each string has independent
     * noise excitation and analog drift, creating rich evolving shimmer that
     * naturally narrows as harmonics decay.
     *
     * @param voices Number of strings (1–16).
     * @param freqSpread Detune spread in semitones.
     * @param decay Feedback amount (0.9–0.999).
     * @param brightness Lowpass cutoff in feedback (0.0–1.0).
     * @param pickPosition Pluck position (0.0–1.0).
     * @param stiffness Allpass stiffness (0.0–1.0).
     * @param analog Perlin noise pitch drift amount.
     */
    fun superKarplusStrong(
        voices: Exciter = ParamExciter("voices", 8.0),
        freqSpread: Exciter = ParamExciter("freqSpread", 0.2),
        decay: Exciter = ParamExciter("decay", 0.996),
        brightness: Exciter = ParamExciter("brightness", 0.5),
        pickPosition: Exciter = ParamExciter("pickPosition", 0.5),
        stiffness: Exciter = ParamExciter("stiffness", 0.0),
        analog: Exciter = ParamExciter("analog", 0.0),
    ): Exciter {
        val maxDelay = 2500

        // Per-voice state
        data class StringState(
            val delayLine: FloatArray = FloatArray(maxDelay),
            var writePos: Int = 0,
            var excited: Boolean = false,
            var lpState: Double = 0.0,
            var apPrevIn: Double = 0.0,
            var apPrevOut: Double = 0.0,
            var drift: AnalogDrift? = null,
        )

        var v = 0
        var voiceGain = 0.0
        var strings = Array(0) { StringState() }
        val rng = kotlin.random.Random

        return Exciter { buffer, freqHz, ctx ->

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, freqHz, ctx)
                val newV = voicesBuf[ctx.offset].toInt()
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = strings
                    strings = Array(v) { i -> if (i < old.size) old[i] else StringState() }
                }
                if (v <= 0) {
                    buffer.fill(0f, ctx.offset, ctx.offset + ctx.length); return@use
                }

            // Read control-rate params once per block
            val spread = readParam(freqSpread, freqHz, ctx)
            val decayVal = readParam(decay, freqHz, ctx)
            val brightnessVal = readParam(brightness, freqHz, ctx)
            val stiffnessVal = readParam(stiffness, freqHz, ctx)

            val lpAlpha = brightnessVal.coerceIn(0.01, 1.0)
            val hasStiffness = stiffnessVal > 0.0
            val apCoeff = stiffnessVal.coerceIn(0.0, 0.99) * 0.5

            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            for (n in 0 until v) {
                val s = strings[n]
                val sd = s.drift ?: initAnalogDrift(analog, freqHz, ctx).also { s.drift = it }
                val detuneSemitones = getUnisonDetune(v, spread, n)
                val detunedFreq = applySemitoneDetuneToFrequency(freqHz, detuneSemitones)
                val baseDelay = (sr / detunedFreq).coerceIn(2.0, (maxDelay - 1).toDouble())

                // Excite each string independently
                if (!s.excited) {
                    s.excited = true
                    val pickPosVal = readParam(pickPosition, freqHz, ctx)
                    val delayLen = baseDelay.toInt()
                    val pp = pickPosVal.coerceIn(0.0, 1.0)
                    val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
                    val burstStart = ((delayLen - burstLen) * pp).toInt()

                    for (j in 0 until delayLen) {
                        s.delayLine[j] = if (j >= burstStart && j < burstStart + burstLen) {
                            (rng.nextDouble() * 2.0 - 1.0).toFloat()
                        } else {
                            0.0f
                        }
                    }
                    s.writePos = delayLen % maxDelay
                }

                val isFirst = n == 0

                for (i in ctx.offset until end) {
                    // Effective delay with detune, phaseMod, and per-voice drift
                    var dl = baseDelay
                    if (phaseMod != null) dl /= phaseMod[i]
                    if (sd.active) dl /= sd.nextMultiplier()
                    dl = dl.coerceIn(2.0, (maxDelay - 1).toDouble())

                    // Read with linear interpolation
                    val readPosF = s.writePos - dl
                    val readPosWrapped = if (readPosF < 0) readPosF + maxDelay else readPosF
                    val readIdx = readPosWrapped.toInt() % maxDelay
                    val frac = readPosWrapped - readPosWrapped.toInt()
                    val nextIdx = (readIdx + 1) % maxDelay
                    val sample = s.delayLine[readIdx] + (s.delayLine[nextIdx] - s.delayLine[readIdx]) * frac.toFloat()

                    // One-pole lowpass (brightness)
                    s.lpState = s.lpState + lpAlpha * (sample.toDouble() - s.lpState)
                    var filtered = s.lpState

                    // Allpass stiffness
                    if (hasStiffness) {
                        val apOut = apCoeff * (filtered - s.apPrevOut) + s.apPrevIn
                        s.apPrevIn = filtered
                        s.apPrevOut = apOut
                        filtered = apOut
                    }

                    // Write back with decay
                    s.delayLine[s.writePos] = (filtered * decayVal).toFloat()

                    // Sum to output
                    val out = (sample * voiceGain).toFloat()
                    if (isFirst) {
                        buffer[i] = out
                    } else {
                        buffer[i] = buffer[i] + out
                    }

                    s.writePos = (s.writePos + 1) % maxDelay
                }
            }
            }
        }
    }

    /** Silence: fills buffer with zeros. */
    fun silence(): Exciter = Exciter { buffer, _, ctx ->
        buffer.fill(0.0f, ctx.offset, ctx.offset + ctx.length)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Analog drift helper
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Initialize AnalogDrift lazily from an Exciter param on first block.
     * Reads the analog amount once from the param buffer (control rate).
     */
    internal fun initAnalogDrift(analog: Exciter, freqHz: Double, ctx: ExciteContext): AnalogDrift {
        if (analog is ParamExciter) return AnalogDrift(analog.default)
        return ctx.scratchBuffers.use { tmp ->
            analog.generate(tmp, freqHz, ctx)
            AnalogDrift(tmp[ctx.offset].toDouble())
        }
    }

    /** Read a control-rate parameter once per block. Optimized for constant ParamExciter. */
    internal fun readParam(param: Exciter, freqHz: Double, ctx: ExciteContext): Double {
        if (param is ParamExciter) return param.default
        return ctx.scratchBuffers.use { tmp -> param.generate(tmp, freqHz, ctx); tmp[ctx.offset].toDouble() }
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
