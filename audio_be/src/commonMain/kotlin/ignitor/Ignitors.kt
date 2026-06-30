/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.applySemitoneDetuneToFrequency
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.dust
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.pulze
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.readParam
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.sawtooth
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.square
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.superSaw
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.superSawRaw
import io.peekandpoke.klang.audio_be.ignitor.Ignitors.zawtooth
import io.peekandpoke.klang.audio_be.smallNumFastMod
import io.peekandpoke.klang.audio_be.waveTrapezoid
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Factory functions for Ignitor oscillator primitives.
 *
 * Each factory returns a fresh Ignitor with its own phase state.
 */
object Ignitors {

    // Shared ConstantIgnitor singletons used as factory defaults. The normal DSL
    // path (IgnitorDslRuntime.toExciter) always supplies an explicit runtime
    // ignitor, so these defaults only fire when a Kotlin caller invokes a factory
    // without that argument. Sprudel's oscParam lookup runs at the DSL layer
    // (IgnitorDslRuntime.buildIgnitor), upstream of these factories.
    private val analogDefault = ConstantIgnitor(0.0)
    private val voicesDefault = ConstantIgnitor(7.0)
    private val detuneDefault = ConstantIgnitor(0.2)
    private val dutyDefault = ConstantIgnitor(0.5)
    private val densityDefault = ConstantIgnitor(0.2)
    private val rateDefault = ConstantIgnitor(1.0)
    private val decayDefault = ConstantIgnitor(0.996)
    private val brightnessDefault = ConstantIgnitor(0.5)
    private val pickPositionDefault = ConstantIgnitor(0.5)
    private val stiffnessDefault = ConstantIgnitor(0.0)

    /** Sine wave oscillator. Inherently band-limited, no anti-aliasing needed. */
    fun sine(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = SineIgnitor(freq, analog)

    private class SineIgnitor(
        private val freq: Ignitor,
        private val analog: Ignitor,
    ) : Ignitor {
        private var phase: Double = 0.0
        private var drift: AnalogDrift? = null

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)
            val d = drift ?: initAnalogDrift(analog, actualFreq, ctx).also { drift = it }

            val phaseInc = TWO_PI * actualFreq / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase)
                        phase += phaseInc * d.nextMultiplier()
                        phase = phase.wrapPhase(TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase)
                        phase += phaseInc * phaseMod[i] * d.nextMultiplier()
                        phase = phase.wrapPhase(TWO_PI)
                    }
                }
            } else {
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase)
                        phase += phaseInc
                        phase = phase.wrapPhase(TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = sin(phase)
                        phase += phaseInc * phaseMod[i]
                        phase = phase.wrapPhase(TWO_PI)
                    }
                }
            }
        }
    }

    // ── One waveform engine ──────────────────────────────────────────────────────
    // saw / ramp / square / pulse / triangle (+ raw zaw / zamp / pulze) all render through the SAME
    // shape ([waveTrapezoid] via [WaveVoiceState]) and the same hot loop — see [WaveIgnitor].

    private enum class WaveKind { SAW, PULSE }

    /**
     * The single mono oscillator behind saw / ramp / square / pulse / triangle (and the raw
     * zaw / zamp / pulze). A `±1` piecewise-linear [waveTrapezoid] in a [WaveVoiceState], finite-slope
     * edges (no PolyBLEP). [kind] picks the shape config (SAW = rise + finite flyback; PULSE = min-flank
     * rise/fall around a high plateau of width [duty]). [polarity] negates for ramp/zamp. [flankSamples]
     * is the edge length in samples (`0` = raw / instant / aliased). [duty] may be audio-rate (PWM).
     */
    private class WaveIgnitor(
        private val freq: Ignitor,
        private val analog: Ignitor,
        private val kind: WaveKind,
        private val polarity: Double,
        private val flankSamples: Double,
        private val duty: Ignitor = ConstantIgnitor(0.5),
        private val riseFlank: Double = 0.0,
        private val fallFlank: Double = 0.0,
        // SAW-only: caps the saw flyback fraction; read solely in the WaveKind.SAW branch. The PULSE kind
        // (square/triangle) ignores it — no per-shape WaveIgnitor split needed since the DSL types already
        // separate SAW (Sawtooth/Ramp expose shapeMax) from PULSE (Pulze/Triangle don't).
        private val shapeMax: Double = SAW_SHAPE_MAX,
    ) : Ignitor {
        private val voice = WaveVoiceState()
        private var driftInit = false
        private var lastDt: Double = Double.NaN
        private var lastDuty: Double = Double.NaN

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)
            if (!driftInit) {
                driftInit = true
                val amt = readParam(analog, actualFreq, ctx)
                voice.drift = if (amt > 0.0) AnalogDrift(amt, ctx.sampleRate) else null
            }
            val dt = actualFreq / ctx.sampleRateD
            val pm = ctx.phaseMod
            val off = ctx.offset
            val end = off + ctx.length

            if (kind == WaveKind.SAW) {
                if (dt != lastDt) {
                    lastDt = dt
                    voice.setSawShape((flankSamples * dt).coerceAtMost(shapeMax))
                }
                renderHoisted(buffer, off, end, dt, pm)
                return
            }

            // PULSE — constant duty: bake once + hoist; audio-rate duty (PWM): rebake per sample.
            val dutyConst = duty.controlRateValueOrNull(actualFreq, ctx)
            if (dutyConst != null) {
                val d = dutyConst
                if (d != lastDuty || dt != lastDt) {
                    lastDuty = d; lastDt = dt
                    voice.setPulseShape(d, riseFlank, fallFlank, flankSamples * dt)
                }
                renderHoisted(buffer, off, end, dt, pm)
            } else {
                if (dt != lastDt) {
                    lastDt = dt; lastDuty = Double.NaN
                }
                val floor = flankSamples * dt
                ctx.scratchBuffers.use { dutyBuf ->
                    duty.generate(dutyBuf, actualFreq, ctx)
                    var phase = voice.phase
                    val drift = voice.drift
                    val pol = polarity
                    for (i in off until end) {
                        val d = dutyBuf[i]
                        if (d != lastDuty) {
                            lastDuty = d; voice.setPulseShape(d, riseFlank, fallFlank, floor)
                        }
                        buffer[i] = pol * waveTrapezoid(
                            phase, voice.riseEnd, voice.highEnd, voice.fallEnd, voice.riseSlope, voice.fallSlope,
                        )
                        var inc = dt
                        if (pm != null) inc *= pm[i]
                        if (drift != null) inc *= drift.nextMultiplier()
                        phase += inc
                        phase = if (pm != null) phase.wrapPhase(1.0) else phase.smallNumFastMod(1.0)
                    }
                    voice.phase = phase
                }
            }
        }

        /** Tight loop with the shape hoisted into locals (constant within the block). */
        private fun renderHoisted(buffer: AudioBuffer, off: Int, end: Int, dt: Double, pm: DoubleArray?) {
            var phase = voice.phase
            val drift = voice.drift
            val pol = polarity
            val riseEnd = voice.riseEnd
            val highEnd = voice.highEnd
            val fallEnd = voice.fallEnd
            val riseSlope = voice.riseSlope
            val fallSlope = voice.fallSlope
            for (i in off until end) {
                buffer[i] = pol * waveTrapezoid(phase, riseEnd, highEnd, fallEnd, riseSlope, fallSlope)
                var inc = dt
                if (pm != null) inc *= pm[i]
                if (drift != null) inc *= drift.nextMultiplier()
                phase += inc
                phase = if (pm != null) phase.wrapPhase(1.0) else phase.smallNumFastMod(1.0)
            }
            voice.phase = phase
        }
    }

    /**
     * Sawtooth — rise then a finite flyback ([SAW_RESET_SAMPLES] samples; no PolyBLEP, softens with
     * pitch). Single-voice form of the shape shared with [superSaw]. Per-voice analog drift via [analog].
     */
    fun sawtooth(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
        resetSamples: Double = SAW_RESET_SAMPLES,
        shapeMax: Double = SAW_SHAPE_MAX,
    ): Ignitor = WaveIgnitor(
        freq, analog, WaveKind.SAW, polarity = 1.0, flankSamples = resetSamples, shapeMax = shapeMax,
    )

    /** Reverse sawtooth — the negated [sawtooth] (own `RAMP_RESET_SAMPLES` flyback knob). */
    fun ramp(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
        resetSamples: Double = RAMP_RESET_SAMPLES,
        shapeMax: Double = RAMP_SHAPE_MAX,
    ): Ignitor = WaveIgnitor(
        freq, analog, WaveKind.SAW, polarity = -1.0, flankSamples = resetSamples, shapeMax = shapeMax,
    )

    /** Square wave — a 50%-duty [pulze] (Kotlin convenience; the DSL drives `duty` via an osc-param). */
    fun square(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = pulze(freq, ConstantIgnitor(0.5), analog)

    /** Triangle wave — the pulse engine with both flanks fully open (duty 0.5, rise = fall = 1). */
    fun triangle(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = WaveIgnitor(
        freq, analog, WaveKind.PULSE, polarity = 1.0, flankSamples = PULSE_MIN_FLANK_SAMPLES,
        duty = ConstantIgnitor(0.5), riseFlank = 1.0, fallFlank = 1.0,
    )

    /** White noise generator. Flat spectrum with equal energy at all frequencies. */
    fun whiteNoise(rng: Random): Ignitor = WhiteNoiseIgnitor(rng)

    private class WhiteNoiseIgnitor(private val rng: Random) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = (rng.nextDouble() * 2.0 - 1.0)
            }
        }
    }

    /** Naive sawtooth ("zaw") — the raw [sawtooth] (`flankSamples = 0` → instant reset, aliased/harsh). */
    fun zawtooth(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = WaveIgnitor(freq, analog, WaveKind.SAW, polarity = 1.0, flankSamples = 0.0)

    /** Raw ramp ("zamp") — the negated [zawtooth] (naive reverse saw, no anti-aliasing). */
    fun zamp(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = WaveIgnitor(freq, analog, WaveKind.SAW, polarity = -1.0, flankSamples = 0.0)

    /** Raw pulse ("pulze") — naive aliased pulse with variable [duty] (the raw [square]). */
    fun rawPulze(
        freq: Ignitor = FreqIgnitor,
        duty: Ignitor = dutyDefault,
        analog: Ignitor = analogDefault,
    ): Ignitor = WaveIgnitor(
        freq, analog, WaveKind.PULSE, polarity = 1.0, flankSamples = 0.0,
        duty = duty, riseFlank = PULSE_RISE_FLANK, fallFlank = PULSE_FALL_FLANK,
    )

    /** Impulse: outputs 1.0 once per cycle (at phase wrap), 0.0 otherwise. */
    fun impulse(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = ImpulseIgnitor(freq, analog)

    private class ImpulseIgnitor(
        private val freq: Ignitor,
        private val analog: Ignitor,
    ) : Ignitor {
        private var phase: Double = 0.0
        private var lastPhase: Double = Double.POSITIVE_INFINITY
        private var drift: AnalogDrift? = null

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)
            val d = drift ?: initAnalogDrift(analog, actualFreq, ctx).also { drift = it }

            val phaseInc = TWO_PI * actualFreq / ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            if (d.active) {
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0 else 0.0
                        lastPhase = phase
                        phase += phaseInc * d.nextMultiplier()
                        phase = phase.wrapPhase(TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0 else 0.0
                        lastPhase = phase
                        phase += phaseInc * phaseMod[i] * d.nextMultiplier()
                        phase = phase.wrapPhase(TWO_PI)
                    }
                }
            } else {
                if (phaseMod == null) {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0 else 0.0
                        lastPhase = phase
                        phase += phaseInc
                        phase = phase.wrapPhase(TWO_PI)
                    }
                } else {
                    for (i in ctx.offset until end) {
                        buffer[i] = if (phase < lastPhase) 1.0 else 0.0
                        lastPhase = phase
                        phase += phaseInc * phaseMod[i]
                        phase = phase.wrapPhase(TWO_PI)
                    }
                }
            }
        }
    }

    /** Rounded pulse with variable [duty] cycle (square / pulse) — band-limited [WaveIgnitor], PWM-capable. */
    fun pulze(
        freq: Ignitor = FreqIgnitor,
        duty: Ignitor = dutyDefault,
        analog: Ignitor = analogDefault,
        flankSamples: Double = PULSE_MIN_FLANK_SAMPLES,
        riseFlank: Double = PULSE_RISE_FLANK,
        fallFlank: Double = PULSE_FALL_FLANK,
    ): Ignitor = WaveIgnitor(
        freq, analog, WaveKind.PULSE, polarity = 1.0, flankSamples = flankSamples,
        duty = duty, riseFlank = riseFlank, fallFlank = fallFlank,
    )

    /** Brown noise (random walk with leaky integrator). Deeper, rumbly character. */
    fun brownNoise(rng: Random): Ignitor = BrownNoiseIgnitor(rng)

    private class BrownNoiseIgnitor(private val rng: Random) : Ignitor {
        private var out: Double = 0.0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val white = rng.nextDouble() * 2.0 - 1.0
                out = (out + 0.02 * white) / 1.02
                buffer[i] = out
            }
        }
    }

    /** Pink noise (1/f spectrum via Paul Kellet's IIR cascades). */
    fun pinkNoise(rng: Random): Ignitor = PinkNoiseIgnitor(rng)

    private class PinkNoiseIgnitor(private val rng: Random) : Ignitor {
        private var b0: Double = 0.0
        private var b1: Double = 0.0
        private var b2: Double = 0.0
        private var b3: Double = 0.0
        private var b4: Double = 0.0
        private var b5: Double = 0.0
        private var b6: Double = 0.0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
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
                buffer[i] = (pink * 0.11)
            }
        }
    }

    /** Perlin noise: smooth organic noise using 1D Perlin noise. Output range -1..1. */
    fun perlinNoise(rng: Random, rate: Ignitor = rateDefault): Ignitor =
        PerlinNoiseIgnitor(rng, rate)

    private class PerlinNoiseIgnitor(rng: Random, private val rate: Ignitor) : Ignitor {
        private val noise = PerlinNoise(rng)
        private var pos: Double = rng.nextDouble() * 256.0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            ctx.scratchBuffers.use { rateBuf ->
                rate.generate(rateBuf, 0.0, ctx)
                val step = rateBuf[ctx.offset] * PERLIN_STEP
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = noise.noise(pos)
                    pos += step
                }
            }
        }
    }

    /** Berlin noise: piecewise-linear interpolated random noise, scaled to -1..1. */
    fun berlinNoise(rng: Random, rate: Ignitor = rateDefault): Ignitor =
        BerlinNoiseIgnitor(rng, rate)

    private class BerlinNoiseIgnitor(rng: Random, private val rate: Ignitor) : Ignitor {
        private val noise = BerlinNoise(rng)
        private var pos: Double = rng.nextDouble() * 256.0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            ctx.scratchBuffers.use { rateBuf ->
                rate.generate(rateBuf, 0.0, ctx)
                val step = rateBuf[ctx.offset] * PERLIN_STEP
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    // BerlinNoise outputs 0..1, scale to -1..1
                    buffer[i] = (noise.noise(pos) * 2.0 - 1.0)
                    pos += step
                }
            }
        }
    }

    /** Dust: sparse random impulses. [density] 0.0..1.0 controls impulse rate. [maxRateHz] caps the rate. */
    fun dust(rng: Random, density: Ignitor = densityDefault, maxRateHz: Double = 200.0): Ignitor =
        DustIgnitor(rng, density, maxRateHz)

    private class DustIgnitor(
        private val rng: Random,
        private val density: Ignitor,
        private val maxRateHz: Double,
    ) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            ctx.scratchBuffers.use { densityBuf ->
                density.generate(densityBuf, 0.0, ctx)
                val d = densityBuf[ctx.offset].coerceIn(0.0, 1.0)
                val rateHz = d * maxRateHz
                val p = (rateHz / ctx.sampleRateD).coerceIn(0.0, 1.0)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = if (rng.nextDouble() < p) rng.nextDouble() else 0.0
                }
            }
        }
    }

    /** Crackle: sparse random impulses with higher max rate than [dust]. */
    fun crackle(rng: Random, density: Ignitor = densityDefault, maxRateHz: Double = 800.0): Ignitor {
        return dust(rng, density, maxRateHz)
    }

    /**
     * Supersaw: a detuned stack of the analog-flyback saw shape (mono). Voice count is read lazily
     * from the [voices] Ignitor param on the first block. See [SawStackIgnitor].
     */
    fun superSawRaw(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        // Unison character — defaults are the SUPERSAW_* tuning constants; the DSL threads per-voice overrides.
        sideAtten: Double = SUPERSAW_SIDE_ATTEN,
        gainJitter: Double = SUPERSAW_GAIN_JITTER,
        spreadPower: Double = SUPERSAW_SPREAD_POWER,
        centerJitterScale: Double = SUPERSAW_CENTER_JITTER_SCALE,
    ): Ignitor = SawStackIgnitor(
        freq, voices, detune, analog, rng,
        polarity = 1.0,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower, centerJitterScale = centerJitterScale,
        resetSamples = SAW_RESET_SAMPLES, shapeMax = SAW_SHAPE_MAX,
    )

    /** Super Saw (mono). Alias of [superSawRaw]; the historic pitch-tracking HPF wrapper was removed. */
    fun superSaw(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        sideAtten: Double = SUPERSAW_SIDE_ATTEN,
        gainJitter: Double = SUPERSAW_GAIN_JITTER,
        spreadPower: Double = SUPERSAW_SPREAD_POWER,
        centerJitterScale: Double = SUPERSAW_CENTER_JITTER_SCALE,
    ): Ignitor = superSawRaw(
        freq, voices, detune, analog, rng,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower, centerJitterScale = centerJitterScale,
    )

    /**
     * Shared engine for every unison oscillator: a stack of detuned voices summed to mono with the
     * super-saw character — center-dominant [sideAtten] gains, per-voice amplitude [gainJitter],
     * independent per-voice [AnalogDrift], even [detune] spacing (shaped by [spreadPower]) with the
     * **gain-weighted mean detune removed** so the pitch centroid sits exactly on the note. [polarity]
     * flips the waveform and is baked into the voice gains (no per-sample sign flip).
     *
     * Subclasses supply only the per-voice shape — [configureShape] (control-rate, from the detuned
     * increment) and [renderVoice] (the per-sample inner loop). Voice count is read lazily from the
     * [voices] param; detune + shape are cached, recomputed only when freq / voice count / spread change.
     */
    private abstract class DetunedStackIgnitor(
        private val freq: Ignitor,
        private val voices: Ignitor,
        private val detune: Ignitor,
        private val analog: Ignitor,
        private val rng: Random,
        private val polarity: Double,
        private val sideAtten: Double,
        private val gainJitter: Double,
        private val spreadPower: Double,
        private val centerJitterScale: Double,
    ) : Ignitor {
        private var v: Int = 0
        private var voiceStates: Array<WaveVoiceState> = emptyArray()

        // Detune-recompute cache keys (NaN forces recompute).
        private var lastFreq: Double = Double.NaN
        private var lastSpread: Double = Double.NaN

        /** Set the per-voice shape from its detuned per-sample increment [dt] (control rate). */
        protected abstract fun configureShape(vs: WaveVoiceState, dt: Double)

        /** Render one voice's block: write the buffer when [first], else accumulate onto it. */
        protected abstract fun renderVoice(
            buffer: AudioBuffer, off: Int, end: Int, vs: WaveVoiceState, first: Boolean, pm: DoubleArray?,
        )

        final override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)

            val newV = maxOf(0, readParam(voices, actualFreq, ctx).toInt())
            if (newV != v) {
                v = newV
                val old = voiceStates
                // Reuse existing voice objects (preserve phase); random start phase for new ones —
                // lush (phase-0 is thin; even spacing makes voice-count-dependent overtones). Innocent
                // for tuning: a phase offset doesn't change frequency, and `p += dt` is unbiased.
                voiceStates = Array(v) { i ->
                    if (i < old.size) old[i] else WaveVoiceState().also { it.phase = rng.nextDouble() }
                }
                // Per-voice independent analog drift (amount read once, control rate). When off,
                // leave drift null so the hot loop skips it with a single null check (no allocation).
                val analogAmt = readParam(analog, actualFreq, ctx)
                for (n in 0 until v) {
                    voiceStates[n].drift = if (analogAmt > 0.0) AnalogDrift(analogAmt, ctx.sampleRate) else null
                }
                computeVoiceGains()
                lastFreq = Double.NaN         // force detune/shape recompute
                lastSpread = Double.NaN
            }
            if (v <= 0) {
                buffer.fill(0.0, ctx.offset, ctx.offset + ctx.length); return
            }

            val spread = readParam(detune, actualFreq, ctx)
            // Recompute per-voice detune increment + shape ONCE per (freq, spread).
            if (actualFreq != lastFreq || spread != lastSpread) {
                lastFreq = actualFreq
                lastSpread = spread
                computeDetunes(actualFreq, spread, ctx.sampleRateD)
            }

            // Voice-major: each voice runs its whole sample block in one call (register residency);
            // voice 0 writes the buffer, the rest accumulate. Voices are independent (each owns its
            // drift). `pm` = phaseMod (vibrato/FM/pitch-env/accelerate), null when unmodulated.
            val pm = ctx.phaseMod
            val off = ctx.offset
            val end = off + ctx.length
            for (n in 0 until v) {
                renderVoice(buffer, off, end, voiceStates[n], n == 0, pm)
            }
        }

        /**
         * Center-dominant base gains × per-voice amplitude jitter, renormalized to sum 1, with
         * [polarity] folded in (negated for the ramp) so the hot loop needs no per-sample sign flip.
         *
         * The CENTER voice (detune ≈ 0 → carries the perceived pitch / the "ring") gets a **scaled-down**
         * share of the jitter ([SUPERSAW_CENTER_JITTER_SCALE]): a big jitter draw on it would weaken the
         * on-pitch fundamental and make that note "won't ring", but fully exempting it (scale 0) flattens
         * the liveliness. `0.0` = stable center, `1.0` = jittered like the sides. Sides always get full
         * jitter; phases are untouched (random), so this trades only ring-stability vs liveliness, not timbre.
         */
        private fun computeVoiceGains() {
            val base = superSawVoiceGains(v, sideAtten)
            val center = (v - 1) / 2
            var s = 0.0
            for (n in 0 until v) {
                val scale = if (n == center) centerJitterScale else 1.0
                val jit = 1.0 + (rng.nextDouble() - 0.5) * 2.0 * gainJitter * scale
                val g = (base[n] * jit).coerceAtLeast(0.0)
                voiceStates[n].gain = g; s += g
            }
            if (s > 0.0) {
                val inv = polarity / s; for (n in 0 until v) voiceStates[n].gain *= inv
            }
        }

        /**
         * Per-voice detune increment + analog flyback shape, with the gain-weighted mean detune
         * removed so the perceived pitch centroid sits exactly on the note (anchors tuning despite
         * gain jitter, curve shaping, or voice-count parity). Works for either polarity (the sign
         * cancels in the weighted mean).
         */
        private fun computeDetunes(actualFreq: Double, spread: Double, sr: Double) {
            // Gain-weighted mean detune (two cheap passes — avoids a per-call array allocation).
            var wsum = 0.0
            var gsum = 0.0
            for (n in 0 until v) {
                val g = voiceStates[n].gain
                wsum += getUnisonDetune(v, spread, n, spreadPower) * g; gsum += g
            }
            val mean = if (gsum != 0.0) wsum / gsum else 0.0
            for (n in 0 until v) {
                val vs = voiceStates[n]
                vs.dt = actualFreq.applySemitoneDetuneToFrequency(getUnisonDetune(v, spread, n, spreadPower) - mean) / sr
                configureShape(vs, vs.dt)
            }
        }
    }

    /** A [DetunedStackIgnitor] whose voices render the piecewise-linear [waveTrapezoid] shape. */
    private abstract class TrapezoidStackIgnitor(
        freq: Ignitor, voices: Ignitor, detune: Ignitor, analog: Ignitor, rng: Random,
        polarity: Double, sideAtten: Double, gainJitter: Double, spreadPower: Double, centerJitterScale: Double,
    ) : DetunedStackIgnitor(
        freq, voices, detune, analog, rng, polarity, sideAtten, gainJitter, spreadPower, centerJitterScale,
    ) {
        final override fun renderVoice(
            buffer: AudioBuffer, off: Int, end: Int, vs: WaveVoiceState, first: Boolean, pm: DoubleArray?,
        ) {
            var phase = vs.phase
            val dt = vs.dt
            val gain = vs.gain
            val riseEnd = vs.riseEnd
            val highEnd = vs.highEnd
            val fallEnd = vs.fallEnd
            val riseSlope = vs.riseSlope
            val fallSlope = vs.fallSlope
            val drift = vs.drift
            for (i in off until end) {
                val s = waveTrapezoid(phase, riseEnd, highEnd, fallEnd, riseSlope, fallSlope) * gain
                buffer[i] = if (first) s else buffer[i] + s
                var inc = dt
                if (pm != null) inc *= pm[i]
                if (drift != null) inc *= drift.nextMultiplier()
                phase += inc
                // No phaseMod ⇒ inc is small & positive ⇒ one conditional subtract; with phaseMod,
                // mod can be large/negative ⇒ keep the safe wrap.
                phase = if (pm != null) phase.wrapPhase(1.0) else phase.smallNumFastMod(1.0)
            }
            vs.phase = phase
        }
    }

    /** Unison saw / ramp ([polarity] ±1): the analog-flyback saw shape per voice. */
    private class SawStackIgnitor(
        freq: Ignitor, voices: Ignitor, detune: Ignitor, analog: Ignitor, rng: Random,
        polarity: Double, sideAtten: Double, gainJitter: Double, spreadPower: Double, centerJitterScale: Double,
        private val resetSamples: Double, private val shapeMax: Double,
    ) : TrapezoidStackIgnitor(
        freq, voices, detune, analog, rng, polarity, sideAtten, gainJitter, spreadPower, centerJitterScale,
    ) {
        override fun configureShape(vs: WaveVoiceState, dt: Double) {
            vs.setSawShape((resetSamples * dt).coerceAtMost(shapeMax))
        }
    }

    /** Unison pulse / square / triangle: the [waveTrapezoid] pulse shape per voice ([duty] + flanks). */
    private class PulseStackIgnitor(
        freq: Ignitor, voices: Ignitor, detune: Ignitor, analog: Ignitor, rng: Random,
        polarity: Double, sideAtten: Double, gainJitter: Double, spreadPower: Double, centerJitterScale: Double,
        private val duty: Double, private val riseFlank: Double, private val fallFlank: Double,
        private val flankSamples: Double,
    ) : TrapezoidStackIgnitor(
        freq, voices, detune, analog, rng, polarity, sideAtten, gainJitter, spreadPower, centerJitterScale,
    ) {
        override fun configureShape(vs: WaveVoiceState, dt: Double) {
            vs.setPulseShape(duty, riseFlank, fallFlank, flankSamples * dt)
        }
    }

    /** Unison sine: a pure sine per voice (no shape config; inherently band-limited). */
    private class SineStackIgnitor(
        freq: Ignitor, voices: Ignitor, detune: Ignitor, analog: Ignitor, rng: Random,
        sideAtten: Double, gainJitter: Double, spreadPower: Double, centerJitterScale: Double,
    ) : DetunedStackIgnitor(
        freq, voices, detune, analog, rng, 1.0, sideAtten, gainJitter, spreadPower, centerJitterScale,
    ) {
        override fun configureShape(vs: WaveVoiceState, dt: Double) { /* sine carries no shape */
        }

        override fun renderVoice(
            buffer: AudioBuffer, off: Int, end: Int, vs: WaveVoiceState, first: Boolean, pm: DoubleArray?,
        ) {
            var phase = vs.phase
            val dt = vs.dt
            val gain = vs.gain
            val drift = vs.drift
            for (i in off until end) {
                val s = sin(phase * TWO_PI) * gain
                buffer[i] = if (first) s else buffer[i] + s
                var inc = dt
                if (pm != null) inc *= pm[i]
                if (drift != null) inc *= drift.nextMultiplier()
                phase += inc
                phase = if (pm != null) phase.wrapPhase(1.0) else phase.smallNumFastMod(1.0)
            }
            vs.phase = phase
        }
    }

    /**
     * Supersine: a detuned stack of pure sines (mono) on the shared [SineStackIgnitor] / super-saw
     * unison engine (center-dominant gains, per-voice drift, centroid-anchored detune; its own
     * `SUPERSINE_*` knobs, seeded to the super-saw values). Inherently band-limited, no anti-aliasing
     * needed. Voice count is read lazily from the [voices] Ignitor param on the first block.
     */
    fun superSine(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        sideAtten: Double = SUPERSINE_SIDE_ATTEN,
        gainJitter: Double = SUPERSINE_GAIN_JITTER,
        spreadPower: Double = SUPERSINE_SPREAD_POWER,
        centerJitterScale: Double = SUPERSINE_CENTER_JITTER_SCALE,
    ): Ignitor = SineStackIgnitor(
        freq, voices, detune, analog, rng,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
        centerJitterScale = centerJitterScale,
    )

    /**
     * Supersquare: a detuned stack of the finite-slope pulse shape (duty 0.5, mono) on the shared
     * [PulseStackIgnitor] / super-saw unison engine (center-dominant gains, per-voice drift,
     * centroid-anchored detune; its own `SUPERSQUARE_*` knobs, seeded to the super-saw values). Edges
     * are finite-slope flanks (no PolyBLEP), like the mono square. Voice count is read lazily from the
     * [voices] Ignitor param on the first block.
     */
    fun superSquare(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        sideAtten: Double = SUPERSQUARE_SIDE_ATTEN,
        gainJitter: Double = SUPERSQUARE_GAIN_JITTER,
        spreadPower: Double = SUPERSQUARE_SPREAD_POWER,
        centerJitterScale: Double = SUPERSQUARE_CENTER_JITTER_SCALE,
    ): Ignitor = PulseStackIgnitor(
        freq, voices, detune, analog, rng,
        polarity = 1.0,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
        centerJitterScale = centerJitterScale,
        duty = 0.5, riseFlank = PULSE_RISE_FLANK, fallFlank = PULSE_FALL_FLANK, flankSamples = PULSE_MIN_FLANK_SAMPLES,
    )

    /**
     * Supertri: a detuned stack of triangles (mono) on the shared [PulseStackIgnitor] / super-saw
     * unison engine — the pulse shape with fully-open flanks (1.0/1.0, duty 0.5); its own `SUPERTRI_*`
     * knobs, seeded to the super-saw values. Piecewise linear, inherently band-limited. Voice count is
     * read lazily from the [voices] Ignitor param on the first block.
     */
    fun superTri(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        sideAtten: Double = SUPERTRI_SIDE_ATTEN,
        gainJitter: Double = SUPERTRI_GAIN_JITTER,
        spreadPower: Double = SUPERTRI_SPREAD_POWER,
        centerJitterScale: Double = SUPERTRI_CENTER_JITTER_SCALE,
    ): Ignitor = PulseStackIgnitor(
        freq, voices, detune, analog, rng,
        polarity = 1.0,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
        centerJitterScale = centerJitterScale,
        duty = 0.5, riseFlank = 1.0, fallFlank = 1.0, flankSamples = PULSE_MIN_FLANK_SAMPLES,
    )

    /**
     * Superramp: a detuned stack of the analog saw shape, **negated** (the mirror of [superSaw]).
     * Shares [SawStackIgnitor] with `polarity = −1`, the `RAMP_*` shape and its own
     * `SUPERRAMP_*` unison knobs (seeded to the super-saw values; change them to diverge). Voice
     * count is read lazily from the [voices] Ignitor param on the first block.
     */
    fun superRamp(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        analog: Ignitor = analogDefault,
        rng: Random = Random,
        sideAtten: Double = SUPERRAMP_SIDE_ATTEN,
        gainJitter: Double = SUPERRAMP_GAIN_JITTER,
        spreadPower: Double = SUPERRAMP_SPREAD_POWER,
        centerJitterScale: Double = SUPERRAMP_CENTER_JITTER_SCALE,
    ): Ignitor = SawStackIgnitor(
        freq, voices, detune, analog, rng,
        polarity = -1.0,
        sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
        centerJitterScale = centerJitterScale,
        resetSamples = RAMP_RESET_SAMPLES, shapeMax = RAMP_SHAPE_MAX,
    )

    /**
     * Karplus-Strong plucked string synthesis via noise-burst-excited delay line with filtered feedback.
     * Extended with pick position modeling and allpass stiffness filtering.
     */
    @Suppress("DuplicatedCode")
    fun karplusStrong(
        freq: Ignitor = FreqIgnitor,
        decay: Ignitor = decayDefault,
        brightness: Ignitor = brightnessDefault,
        pickPosition: Ignitor = pickPositionDefault,
        stiffness: Ignitor = stiffnessDefault,
        analog: Ignitor = analogDefault,
    ): Ignitor = KarplusStrongIgnitor(freq, decay, brightness, pickPosition, stiffness, analog)

    private class KarplusStrongIgnitor(
        private val freq: Ignitor,
        private val decay: Ignitor,
        private val brightness: Ignitor,
        private val pickPosition: Ignitor,
        private val stiffness: Ignitor,
        private val analog: Ignitor,
    ) : Ignitor {
        // Max delay line: supports down to ~20 Hz at 48kHz (2400 samples)
        private val maxDelay = 2500
        private val delayLine = AudioBuffer(maxDelay)
        private var writePos: Int = 0
        private var excited: Boolean = false
        private var drift: AnalogDrift? = null

        // One-pole lowpass state for brightness filtering
        private var lpState: Double = 0.0

        // Allpass state for stiffness
        private var apPrevIn: Double = 0.0
        private var apPrevOut: Double = 0.0

        private val rng: Random = Random

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)
            val d = drift ?: initAnalogDrift(analog, actualFreq, ctx).also { drift = it }

            val decayVal = readParam(decay, actualFreq, ctx)
            val brightnessVal = readParam(brightness, actualFreq, ctx)
            val stiffnessVal = readParam(stiffness, actualFreq, ctx)

            val lpAlpha = brightnessVal.coerceIn(0.01, 1.0)
            val hasStiffness = stiffnessVal > 0.0
            val apCoeff = stiffnessVal.coerceIn(0.0, 0.99) * 0.5

            val sr = ctx.sampleRateD
            val phaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length

            val baseDelay = (sr / actualFreq).coerceIn(2.0, (maxDelay - 1.0))

            if (!excited) {
                excited = true
                val pickPosVal = readParam(pickPosition, actualFreq, ctx)
                val delayLen = baseDelay.toInt()
                val pp = pickPosVal.coerceIn(0.0, 1.0)
                val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
                val burstStart = ((delayLen - burstLen) * pp).toInt()

                for (j in 0 until delayLen) {
                    delayLine[j] = if (j >= burstStart && j < burstStart + burstLen) {
                        (rng.nextDouble() * 2.0 - 1.0)
                    } else {
                        0.0
                    }
                }
                writePos = delayLen % maxDelay
            }

            for (i in ctx.offset until end) {
                var dl = baseDelay
                if (phaseMod != null) dl /= phaseMod[i]
                if (d.active) dl /= d.nextMultiplier()
                dl = dl.coerceIn(2.0, (maxDelay - 1.0))

                val readPosF = writePos - dl
                val readPosWrapped = if (readPosF < 0) readPosF + maxDelay else readPosF
                val readIdx = readPosWrapped.toInt() % maxDelay
                val frac = readPosWrapped - readPosWrapped.toInt()
                val nextIdx = (readIdx + 1) % maxDelay
                val sample = delayLine[readIdx] + (delayLine[nextIdx] - delayLine[readIdx]) * frac

                lpState = (lpState + lpAlpha * (sample - lpState)).flushDenormal()
                var filtered = lpState

                if (hasStiffness) {
                    val apOut = apCoeff * (filtered - apPrevOut) + apPrevIn
                    apPrevIn = filtered.flushDenormal()
                    apPrevOut = apOut.flushDenormal()
                    filtered = apOut
                }

                delayLine[writePos] = (filtered * decayVal)
                buffer[i] = sample
                writePos = (writePos + 1) % maxDelay
            }
        }
    }

    /**
     * Super Karplus-Strong: multiple detuned plucked strings summed together.
     * Each string has independent noise excitation and analog drift, creating rich evolving shimmer.
     * Voice count is read lazily from the [voices] Ignitor param on the first block.
     */
    @Suppress("DuplicatedCode")
    fun superKarplusStrong(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor = voicesDefault,
        detune: Ignitor = detuneDefault,
        decay: Ignitor = decayDefault,
        brightness: Ignitor = brightnessDefault,
        pickPosition: Ignitor = pickPositionDefault,
        stiffness: Ignitor = stiffnessDefault,
        analog: Ignitor = analogDefault,
    ): Ignitor = SuperKarplusStrongIgnitor(freq, voices, detune, decay, brightness, pickPosition, stiffness, analog)

    private class SuperKarplusStrongIgnitor(
        private val freq: Ignitor,
        private val voices: Ignitor,
        private val detune: Ignitor,
        private val decay: Ignitor,
        private val brightness: Ignitor,
        private val pickPosition: Ignitor,
        private val stiffness: Ignitor,
        private val analog: Ignitor,
    ) : Ignitor {
        private val maxDelay = 2500

        private class StringState(
            val delayLine: AudioBuffer,
            var writePos: Int = 0,
            var excited: Boolean = false,
            var lpState: Double = 0.0,
            var apPrevIn: Double = 0.0,
            var apPrevOut: Double = 0.0,
            var drift: AnalogDrift? = null,
        )

        private var v: Int = 0
        private var voiceGain: Double = 0.0
        private var strings: Array<StringState> = emptyArray()
        private val rng: Random = Random

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val actualFreq = resolveFreq(freq, freqHz, ctx)

            ctx.scratchBuffers.use { voicesBuf ->
                voices.generate(voicesBuf, actualFreq, ctx)
                val newV = maxOf(0, voicesBuf[ctx.offset].toInt())
                if (newV != v) {
                    v = newV
                    voiceGain = if (v > 0) 1.0 / v.toDouble() else 0.0
                    val old = strings
                    strings = Array(v) { i -> if (i < old.size) old[i] else StringState(AudioBuffer(maxDelay)) }
                }
                if (v <= 0) {
                    buffer.fill(0.0, ctx.offset, ctx.offset + ctx.length); return@use
                }

                // Read control-rate params once per block
                val spread = readParam(detune, actualFreq, ctx)
                val decayVal = readParam(decay, actualFreq, ctx)
                val brightnessVal = readParam(brightness, actualFreq, ctx)
                val stiffnessVal = readParam(stiffness, actualFreq, ctx)

                val lpAlpha = brightnessVal.coerceIn(0.01, 1.0)
                val hasStiffness = stiffnessVal > 0.0
                val apCoeff = stiffnessVal.coerceIn(0.0, 0.99) * 0.5

                val sr = ctx.sampleRateD
                val phaseMod = ctx.phaseMod
                val end = ctx.offset + ctx.length

                for (n in 0 until v) {
                    val s = strings[n]
                    val sd = s.drift ?: initAnalogDrift(analog, actualFreq, ctx).also { s.drift = it }
                    val detuneSemitones = getUnisonDetune(v, spread, n)
                    val detunedFreq = actualFreq.applySemitoneDetuneToFrequency(detuneSemitones)
                    val baseDelay = (sr / detunedFreq).coerceIn(2.0, (maxDelay - 1.0))

                    // Excite each string independently
                    if (!s.excited) {
                        s.excited = true
                        val pickPosVal = readParam(pickPosition, actualFreq, ctx)
                        val delayLen = baseDelay.toInt()
                        val pp = pickPosVal.coerceIn(0.0, 1.0)
                        val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
                        val burstStart = ((delayLen - burstLen) * pp).toInt()

                        for (j in 0 until delayLen) {
                            s.delayLine[j] = if (j >= burstStart && j < burstStart + burstLen) {
                                (rng.nextDouble() * 2.0 - 1.0)
                            } else {
                                0.0
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
                        dl = dl.coerceIn(2.0, (maxDelay - 1.0))

                        // Read with linear interpolation
                        val readPosF = s.writePos - dl
                        val readPosWrapped = if (readPosF < 0) readPosF + maxDelay else readPosF
                        val readIdx = readPosWrapped.toInt() % maxDelay
                        val frac = readPosWrapped - readPosWrapped.toInt()
                        val nextIdx = (readIdx + 1) % maxDelay
                        val sample = s.delayLine[readIdx] + (s.delayLine[nextIdx] - s.delayLine[readIdx]) * frac

                        // One-pole lowpass (brightness)
                        s.lpState = s.lpState + lpAlpha * (sample - s.lpState)
                        s.lpState = s.lpState.flushDenormal()
                        var filtered = s.lpState

                        // Allpass stiffness
                        if (hasStiffness) {
                            val apOut = apCoeff * (filtered - s.apPrevOut) + s.apPrevIn
                            s.apPrevIn = filtered.flushDenormal()
                            s.apPrevOut = apOut.flushDenormal()
                            filtered = apOut
                        }

                        // Write back with decay
                        s.delayLine[s.writePos] = (filtered * decayVal)

                        // Sum to output
                        val out = (sample * voiceGain)
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
    fun silence(): Ignitor = SilenceIgnitor

    private object SilenceIgnitor : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            buffer.fill(0.0, ctx.offset, ctx.offset + ctx.length)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Analog drift helper
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Read a control-rate parameter once per block: the scalar directly when the param is block-constant
     * (FreqIgnitor / ParamIgnitor / ConstantIgnitor and pointwise combinators over them — no scratch
     * buffer), otherwise one rendered sample. See [Ignitor.blockStartValue].
     */
    internal fun readParam(param: Ignitor, freqHz: Double, ctx: IgniteContext): Double =
        param.blockStartValue(freqHz, ctx)

    /** Resolve the effective oscillator frequency ([readParam] over [freq]; [FreqIgnitor] → the voice note). */
    internal fun resolveFreq(freq: Ignitor, voiceFreqHz: Double, ctx: IgniteContext): Double =
        readParam(freq, voiceFreqHz, ctx)

    /** Initialize [AnalogDrift] lazily from the [analog] param on the first block (read once, control rate). */
    internal fun initAnalogDrift(analog: Ignitor, freqHz: Double, ctx: IgniteContext): AnalogDrift =
        AnalogDrift(readParam(analog, freqHz, ctx), ctx.sampleRate)

    // ═════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════════════

    /** Base step per sample for Perlin/Berlin noise. rate=1.0 walks ~144 noise-units/sec at 48kHz. */
    private const val PERLIN_STEP = 0.003

    // ═════════════════════════════════════════════════════════════════════════════
    // wrapPhase(), smallNumFastMod(), applySemitoneDetuneToFrequency()
    // are in DspUtil.kt — imported via `import io.peekandpoke.klang.audio_be.*`

    // ═════════════════════════════════════════════════════════════════════════════
    // Unison / supersaw helpers
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Center-dominant unison gain profile for the super-saw. The least-detuned (center)
     * voices are loudest; the most-detuned (edge) voices form a quieter halo — the
     * JP-8000 / Szabo Super Saw character. Summing all voices flat (`1/v`) sounds uniform
     * and comb-filtered ("plastic"), and with random start phases an unlucky draw can sum
     * destructively so the note barely rings. A dominant center voice anchors the note
     * (always rings) while the quiet detuned halo only adds shimmer.
     *
     * Triangular falloff with distance-from-center, normalised to sum to 1 (same overall
     * level as the old flat `1/v`). [sideAtten] tunes the falloff: 0 = flat/equal, 1 = only the
     * center voice (defaults to [SUPERSAW_SIDE_ATTEN]; the super-ramp passes its own). Tune by ear.
     */
    internal fun superSawVoiceGains(v: Int, sideAtten: Double = SUPERSAW_SIDE_ATTEN): DoubleArray {
        if (v <= 0) return DoubleArray(0)
        if (v == 1) return doubleArrayOf(1.0)
        val c = (v - 1) * 0.5            // center index (fractional)
        val halfSpan = c                 // > 0 for v >= 2
        val gains = DoubleArray(v)
        var s = 0.0
        for (n in 0 until v) {
            val d = n - c
            val dn = (if (d < 0.0) -d else d) / halfSpan          // 0 at center .. 1 at edges
            val g = (1.0 - sideAtten * dn).coerceAtLeast(0.0)
            gains[n] = g
            s += g
        }
        val norm = if (s > 0.0) 1.0 / s else 0.0
        for (n in 0 until v) gains[n] *= norm
        return gains
    }

    // Oscillator character constants (SAW_* / SUPERSAW_* / RAMP_* / SUPERRAMP_*) live in OscillatorTuning.kt.

    internal fun getUnisonDetune(
        unison: Int,
        detune: Double,
        voiceIndex: Int,
        spreadPower: Double = SUPERSAW_SPREAD_POWER,
    ): Double {
        if (unison < 2) return 0.0
        val a = -detune * 0.5
        val b = detune * 0.5
        var n = voiceIndex.toDouble() / (unison - 1).toDouble()   // 0..1 across the spread
        if (spreadPower != 1.0) {
            // Signed power around the center (0.5) keeps the spacing symmetric (no detuning).
            val x = n * 2.0 - 1.0                                 // -1..+1
            val sx = (if (x < 0.0) -1.0 else 1.0) * abs(x).pow(spreadPower)
            n = (sx + 1.0) * 0.5
        }
        return n * (b - a) + a
    }
}
