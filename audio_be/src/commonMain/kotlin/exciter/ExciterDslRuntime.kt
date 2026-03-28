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

        // Primitives
        is ExciterDsl.Sine -> Exciters.sine(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Sawtooth -> Exciters.sawtooth(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Square -> Exciters.square(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Triangle -> Exciters.triangle(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Ramp -> Exciters.ramp(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Zawtooth -> Exciters.zawtooth(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Pulze -> Exciters.pulze(this.duty.toExciter(oscParams), this.analog.toExciter(oscParams))
            .withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.WhiteNoise -> Exciters.whiteNoise(Random).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Impulse -> Exciters.impulse(this.analog.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.BrownNoise -> Exciters.brownNoise(Random).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.PinkNoise -> Exciters.pinkNoise(Random).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.Dust -> Exciters.dust(Random, this.density.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))
        is ExciterDsl.Crackle -> Exciters.crackle(Random, this.density.toExciter(oscParams)).withGain(this.gain.toExciter(oscParams))

        // Super oscillators
        is ExciterDsl.SuperSaw -> Exciters.superSaw(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.SuperSine -> Exciters.superSine(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.SuperSquare -> Exciters.superSquare(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.SuperTri -> Exciters.superTri(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.SuperRamp -> Exciters.superRamp(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.Silence -> Exciters.silence()

        // Physical models
        is ExciterDsl.Pluck -> Exciters.karplusStrong(
            decay = this.decay.toExciter(oscParams),
            brightness = this.brightness.toExciter(oscParams),
            pickPosition = this.pickPosition.toExciter(oscParams),
            stiffness = this.stiffness.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        is ExciterDsl.SuperPluck -> Exciters.superKarplusStrong(
            voices = this.voices.toExciter(oscParams),
            freqSpread = this.freqSpread.toExciter(oscParams),
            decay = this.decay.toExciter(oscParams),
            brightness = this.brightness.toExciter(oscParams),
            pickPosition = this.pickPosition.toExciter(oscParams),
            stiffness = this.stiffness.toExciter(oscParams),
            analog = this.analog.toExciter(oscParams),
        ).withGain(this.gain.toExciter(oscParams))

        // Arithmetic
        is ExciterDsl.Plus -> left.toExciter(oscParams) + right.toExciter(oscParams)
        is ExciterDsl.Times -> left.toExciter(oscParams) * right.toExciter(oscParams)
        is ExciterDsl.Mul -> inner.toExciter(oscParams).mul(this.factor.toExciter(oscParams))
        is ExciterDsl.Div -> inner.toExciter(oscParams).div(this.divisor.toExciter(oscParams))

        // Frequency
        is ExciterDsl.Detune -> inner.toExciter(oscParams).detune(this.semitones.toExciter(oscParams))

        // Filters
        is ExciterDsl.Lowpass -> inner.toExciter(oscParams).lowpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is ExciterDsl.Highpass -> inner.toExciter(oscParams).highpass(this.cutoffHz.toExciter(oscParams), this.q.toExciter(oscParams))
        is ExciterDsl.OnePoleLowpass -> inner.toExciter(oscParams).onePoleLowpass(this.cutoffHz.toExciter(oscParams))

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
