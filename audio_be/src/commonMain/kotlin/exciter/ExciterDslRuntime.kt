package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_bridge.ExciterDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Walks the DSL tree and produces a runtime [Exciter] instance.
 *
 * Each call creates **fresh instances** with independent mutable state.
 * Calling it twice produces two independent oscillators (different phase, filter state, etc.).
 *
 * [oscParams] provides runtime overrides from [VoiceData.oscParams]. Only leaf oscillator nodes
 * read overrides; composition nodes do NOT pass oscParams through — the tree structure IS the recipe.
 */
fun ExciterDsl.toExciter(oscParams: Map<String, Double>? = null): Exciter {
    val analog = oscParams?.get("analog") ?: 0.0

    return when (this) {
        // Primitives
        is ExciterDsl.Sine -> Exciters.sine(gain, analog)
        is ExciterDsl.Sawtooth -> Exciters.sawtooth(gain, analog)
        is ExciterDsl.Square -> Exciters.square(gain, analog)
        is ExciterDsl.Triangle -> Exciters.triangle(gain, analog)
        is ExciterDsl.Ramp -> Exciters.ramp(gain, analog)
        is ExciterDsl.Zawtooth -> Exciters.zawtooth(gain, analog)
        is ExciterDsl.Pulze -> Exciters.pulze(duty, gain, analog)

        is ExciterDsl.WhiteNoise -> Exciters.whiteNoise(Random, gain)
        is ExciterDsl.Impulse -> Exciters.impulse(gain)
        is ExciterDsl.BrownNoise -> Exciters.brownNoise(Random, gain)
        is ExciterDsl.PinkNoise -> Exciters.pinkNoise(Random, gain)

        is ExciterDsl.Dust -> Exciters.dust(
            Random,
            density = oscParams?.get("density") ?: density,
            gain = gain,
        )

        is ExciterDsl.Crackle -> Exciters.crackle(
            Random,
            density = oscParams?.get("density") ?: density,
            gain = gain,
        )

        is ExciterDsl.SuperSaw -> Exciters.superSaw(
            voices = oscParams?.get("voices")?.toInt() ?: voices,
            freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
            gain = gain,
            analog = analog,
        )

        is ExciterDsl.SuperSine -> Exciters.superSine(
            voices = oscParams?.get("voices")?.toInt() ?: voices,
            freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
            gain = gain,
            analog = analog,
        )

        is ExciterDsl.SuperSquare -> Exciters.superSquare(
            voices = oscParams?.get("voices")?.toInt() ?: voices,
            freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
            gain = gain,
            analog = analog,
        )

        is ExciterDsl.SuperTri -> Exciters.superTri(
            voices = oscParams?.get("voices")?.toInt() ?: voices,
            freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
            gain = gain,
            analog = analog,
        )

        is ExciterDsl.SuperRamp -> Exciters.superRamp(
            voices = oscParams?.get("voices")?.toInt() ?: voices,
            freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
            gain = gain,
            analog = analog,
        )

        is ExciterDsl.Silence -> Exciters.silence()

        // Arithmetic — compositions do NOT pass oscParams through
        is ExciterDsl.Plus -> left.toExciter() + right.toExciter()
        is ExciterDsl.Times -> left.toExciter() * right.toExciter()
        is ExciterDsl.Mul -> inner.toExciter().mul(factor)
        is ExciterDsl.Div -> inner.toExciter().div(divisor)

        // Frequency
        is ExciterDsl.Detune -> inner.toExciter().detune(semitones)

        // Filters
        is ExciterDsl.Lowpass -> inner.toExciter().lowpass(cutoffHz, q)
        is ExciterDsl.Highpass -> inner.toExciter().highpass(cutoffHz, q)
        is ExciterDsl.OnePoleLowpass -> inner.toExciter().onePoleLowpass(cutoffHz)

        // Envelope
        is ExciterDsl.Adsr -> inner.toExciter().adsr(attackSec, decaySec, sustainLevel, releaseSec)

        // FM
        is ExciterDsl.Fm -> carrier.toExciter().fm(
            modulator = modulator.toExciter(),
            ratio = ratio,
            depth = depth,
            envAttackSec = envAttackSec,
            envDecaySec = envDecaySec,
            envSustainLevel = envSustainLevel,
            envReleaseSec = envReleaseSec,
        )
    }
}
