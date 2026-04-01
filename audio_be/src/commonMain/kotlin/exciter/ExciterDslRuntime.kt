package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_bridge.ExciterDsl
import kotlin.random.Random

/**
 * Walks the DSL tree and produces a runtime [Exciter] instance.
 *
 * Each call creates **fresh instances** with independent mutable state.
 * Calling it twice produces two independent oscillators (different phase, filter state, etc.).
 *
 * [oscParams] provides runtime overrides from [VoiceData.oscParams][io.peekandpoke.klang.audio_bridge.VoiceData.oscParams].
 * Only [ExciterDsl.Param] leaf nodes read overrides; the tree structure IS the recipe.
 */
fun ExciterDsl.toExciter(oscParams: Map<String, Double>? = null): Exciter {
    return when (this) {
        is ExciterDsl.Param -> ParamExciter(name, oscParams?.get(name) ?: default)
        is ExciterDsl.Constant -> ParamExciter("", value)  // no name = no oscParam override

        // Primitives
        is ExciterDsl.Sine -> Exciters.sine(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Sawtooth -> Exciters.sawtooth(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Square -> Exciters.square(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Triangle -> Exciters.triangle(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Ramp -> Exciters.ramp(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Zawtooth -> Exciters.zawtooth(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.Pulze -> Exciters.pulze(
            freq = this.freq.toExciter(oscParams),
            duty = this.duty.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.WhiteNoise -> Exciters.whiteNoise(Random)
        is ExciterDsl.Impulse -> Exciters.impulse(freq = this.freq.toExciter(oscParams), analog = this.analog.toExciter(oscParams))
        is ExciterDsl.BrownNoise -> Exciters.brownNoise(Random)
        is ExciterDsl.PinkNoise -> Exciters.pinkNoise(Random)

        is ExciterDsl.PerlinNoise -> Exciters.perlinNoise(Random, this.rate.toExciter(oscParams))
        is ExciterDsl.BerlinNoise -> Exciters.berlinNoise(Random, this.rate.toExciter(oscParams))

        is ExciterDsl.Dust -> Exciters.dust(Random, this.density.toExciter(oscParams))
        is ExciterDsl.Crackle -> Exciters.crackle(Random, this.density.toExciter(oscParams))

        // Super oscillators
        is ExciterDsl.SuperSaw -> Exciters.superSaw(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.SuperSine -> Exciters.superSine(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.SuperSquare -> Exciters.superSquare(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.SuperTri -> Exciters.superTri(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.SuperRamp -> Exciters.superRamp(
            freq = this.freq.toExciter(oscParams),
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.Silence -> Exciters.silence()

        // Physical models
        is ExciterDsl.Pluck -> Exciters.karplusStrong(
            freq = this.freq.toExciter(oscParams),
            decay = this.decay.toExciter(oscParams),
            brightness = this.brightness.toExciter(oscParams),
            pickPosition = this.pickPosition.toExciter(oscParams),
            stiffness = this.stiffness.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        )

        is ExciterDsl.SuperPluck -> Exciters.superKarplusStrong(
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
        is ExciterDsl.Plus -> left.toExciter(oscParams) + right.toExciter(oscParams)
        is ExciterDsl.Times -> left.toExciter(oscParams) * right.toExciter(oscParams)
        is ExciterDsl.Mul -> left.toExciter(oscParams).mul(right.toExciter(oscParams))
        is ExciterDsl.Div -> left.toExciter(oscParams).div(right.toExciter(oscParams))

        // Frequency
        is ExciterDsl.Detune -> inner.toExciter(oscParams).detune(this.semitones.toExciter(oscParams))

        // Filters
        is ExciterDsl.Lowpass -> inner.toExciter(oscParams).lowpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is ExciterDsl.Highpass -> inner.toExciter(oscParams).highpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is ExciterDsl.OnePoleLowpass -> inner.toExciter(oscParams).onePoleLowpass(this.cutoffHz.toExciter(oscParams))
        is ExciterDsl.Bandpass -> inner.toExciter(oscParams).bandpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is ExciterDsl.Notch -> inner.toExciter(oscParams).notch(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))

        // Envelope
        is ExciterDsl.Adsr -> inner.toExciter(oscParams).adsr(
            this.attackSec.toExciter(oscParams),
            this.decaySec.toExciter(oscParams),
            this.sustainLevel.toExciter(oscParams),
            this.releaseSec.toExciter(oscParams),
        )

        // FM
        is ExciterDsl.Fm -> carrier.toExciter(oscParams).fm(
            modulator = modulator.toExciter(oscParams),
            ratio = this.ratio.toExciter(oscParams),
            depth = this.depth.toExciter(oscParams),
            envAttackSec = this.envAttackSec.toExciter(oscParams),
            envDecaySec = this.envDecaySec.toExciter(oscParams),
            envSustainLevel = this.envSustainLevel.toExciter(oscParams),
            envReleaseSec = this.envReleaseSec.toExciter(oscParams),
        )

        // Effects
        is ExciterDsl.Distort -> inner.toExciter(oscParams).distort(this.amount.toExciter(oscParams), shape)
        is ExciterDsl.Drive -> inner.toExciter(oscParams).drive(this.amount.toExciter(oscParams), driveType)
        is ExciterDsl.Clip -> inner.toExciter(oscParams).clip(shape)
        is ExciterDsl.Crush -> inner.toExciter(oscParams).crush(this.amount.toExciter(oscParams))
        is ExciterDsl.Coarse -> inner.toExciter(oscParams).coarse(this.amount.toExciter(oscParams))
        is ExciterDsl.Phaser -> inner.toExciter(oscParams).phaser(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
            this.center.toExciter(oscParams),
            this.sweep.toExciter(oscParams),
        )
        is ExciterDsl.Tremolo -> inner.toExciter(oscParams).tremolo(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
        )

        // Pitch modulation
        is ExciterDsl.Vibrato -> inner.toExciter(oscParams).vibrato(
            this.rate.toExciter(oscParams),
            this.depth.toExciter(oscParams),
        )
        is ExciterDsl.Accelerate -> inner.toExciter(oscParams).accelerate(this.amount.toExciter(oscParams))
        is ExciterDsl.PitchEnvelope -> inner.toExciter(oscParams).pitchEnvelope(
            attackSec = this.attackSec.toExciter(oscParams),
            decaySec = this.decaySec.toExciter(oscParams),
            releaseSec = this.releaseSec.toExciter(oscParams),
            amount = this.amount.toExciter(oscParams),
            curve = this.curve.toExciter(oscParams),
            anchor = this.anchor.toExciter(oscParams),
        )
    }
}
