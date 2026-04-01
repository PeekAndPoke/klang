package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * Walks the DSL tree and produces a runtime [Ignitor] instance.
 *
 * Each call creates **fresh instances** with independent mutable state.
 * Calling it twice produces two independent oscillators (different phase, filter state, etc.).
 *
 * [oscParams] provides runtime overrides from [VoiceData.oscParams][io.peekandpoke.klang.audio_bridge.VoiceData.oscParams].
 * Only [IgnitorDsl.Param] leaf nodes read overrides; the tree structure IS the recipe.
 */
fun IgnitorDsl.toExciter(oscParams: Map<String, Double>? = null): Ignitor {
    return when (this) {
        is IgnitorDsl.Param -> ParamIgnitor(name, oscParams?.get(name) ?: default)
        is IgnitorDsl.Constant -> ParamIgnitor("", value)  // no name = no oscParam override

        // Primitives
        is IgnitorDsl.Sine -> Ignitors.sine(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Sawtooth -> Ignitors.sawtooth(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Square -> Ignitors.square(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Triangle -> Ignitors.triangle(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Ramp -> Ignitors.ramp(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Zawtooth -> Ignitors.zawtooth(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.Pulze -> Ignitors.pulze(
            freq = this.freq.toExciter(oscParams),
            duty = this.duty.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.WhiteNoise -> Ignitors.whiteNoise(Random)
        is IgnitorDsl.Impulse -> Ignitors.impulse(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is IgnitorDsl.BrownNoise -> Ignitors.brownNoise(Random)
        is IgnitorDsl.PinkNoise -> Ignitors.pinkNoise(Random)

        is IgnitorDsl.PerlinNoise -> Ignitors.perlinNoise(Random, this.rate.toExciter(oscParams))
        is IgnitorDsl.BerlinNoise -> Ignitors.berlinNoise(Random, this.rate.toExciter(oscParams))

        is IgnitorDsl.Dust -> Ignitors.dust(Random, this.density.toExciter(oscParams))
        is IgnitorDsl.Crackle -> Ignitors.crackle(Random, this.density.toExciter(oscParams))

        // Super oscillators
        is IgnitorDsl.SuperSaw -> Ignitors.superSaw(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.SuperSine -> Ignitors.superSine(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.SuperSquare -> Ignitors.superSquare(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.SuperTri -> Ignitors.superTri(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.SuperRamp -> Ignitors.superRamp(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.Silence -> Ignitors.silence()

        // Physical models
        is IgnitorDsl.Pluck -> Ignitors.karplusStrong(
            freq = this.freq.toExciter(oscParams),
            decay = this.decay.toExciter(oscParams),
            brightness = this.brightness.toExciter(oscParams),
            pickPosition = this.pickPosition.toExciter(oscParams),
            stiffness = this.stiffness.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is IgnitorDsl.SuperPluck -> Ignitors.superKarplusStrong(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            decay = this.decay.toExciter(oscParams),
            brightness = this.brightness.toExciter(oscParams),
            pickPosition = this.pickPosition.toExciter(oscParams),
            stiffness = this.stiffness.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        // Arithmetic
        is IgnitorDsl.Plus -> left.toExciter(oscParams) + right.toExciter(oscParams)
        is IgnitorDsl.Times -> left.toExciter(oscParams) * right.toExciter(oscParams)
        is IgnitorDsl.Mul -> left.toExciter(oscParams).mul(right.toExciter(oscParams))
        is IgnitorDsl.Div -> left.toExciter(oscParams).div(right.toExciter(oscParams))

        // Frequency
        is IgnitorDsl.Detune -> inner.toExciter(oscParams).detune(this.semitones.toExciter(oscParams))

        // Filters
        is IgnitorDsl.Lowpass -> inner.toExciter(oscParams).lowpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is IgnitorDsl.Highpass -> inner.toExciter(oscParams).highpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is IgnitorDsl.OnePoleLowpass -> inner.toExciter(oscParams).onePoleLowpass(this.cutoffHz.toExciter(oscParams))
        is IgnitorDsl.Bandpass -> inner.toExciter(oscParams).bandpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is IgnitorDsl.Notch -> inner.toExciter(oscParams).notch(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))

        // Envelope
        is IgnitorDsl.Adsr -> inner.toExciter(oscParams).adsr(
            this.attackSec.toExciter(oscParams),
            this.decaySec.toExciter(oscParams),
            this.sustainLevel.toExciter(oscParams),
            this.releaseSec.toExciter(oscParams),
        )

        // FM
        is IgnitorDsl.Fm -> carrier.toExciter(oscParams).fm(
            modulator = modulator.toExciter(oscParams),
            ratio = this.ratio.toExciter(oscParams),
            depth = this.depth.toExciter(oscParams),
            envAttackSec = this.envAttackSec.toExciter(oscParams),
            envDecaySec = this.envDecaySec.toExciter(oscParams),
            envSustainLevel = this.envSustainLevel.toExciter(oscParams),
            envReleaseSec = this.envReleaseSec.toExciter(oscParams),
        )

        // Effects
        is IgnitorDsl.Distort -> inner.toExciter(oscParams).distort(this.amount.toExciter(oscParams), shape)
        is IgnitorDsl.Drive -> inner.toExciter(oscParams).drive(this.amount.toExciter(oscParams), driveType)
        is IgnitorDsl.Clip -> inner.toExciter(oscParams).clip(shape)
        is IgnitorDsl.Crush -> inner.toExciter(oscParams).crush(this.amount.toExciter(oscParams))
        is IgnitorDsl.Coarse -> inner.toExciter(oscParams).coarse(this.amount.toExciter(oscParams))
        is IgnitorDsl.Phaser -> inner.toExciter(oscParams).phaser(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
            this.center.toExciter(oscParams),
            this.sweep.toExciter(oscParams),
        )

        is IgnitorDsl.Tremolo -> inner.toExciter(oscParams).tremolo(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
        )

        // Pitch modulation
        is IgnitorDsl.Vibrato -> inner.toExciter(oscParams).vibrato(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
        )

        is IgnitorDsl.Accelerate -> inner.toExciter(oscParams).accelerate(this.amount.toExciter(oscParams))
        is IgnitorDsl.PitchEnvelope -> inner.toExciter(oscParams).pitchEnvelope(
            attackSec = this.attackSec.toExciter(oscParams),
            decaySec = this.decaySec.toExciter(oscParams),
            releaseSec = this.releaseSec.toExciter(oscParams),
            amount = this.amount.toExciter(oscParams),
            curve = this.curve.toExciter(oscParams),
            anchor = this.anchor.toExciter(oscParams),
        )
    }
}
