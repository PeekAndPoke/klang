package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_bridge.SignalGenDsl
import kotlin.random.Random

/**
 * Walks the DSL tree and produces a runtime [SignalGen] instance.
 *
 * Each call creates **fresh instances** with independent mutable state.
 * Calling it twice produces two independent oscillators (different phase, filter state, etc.).
 *
 * [oscParams] provides runtime overrides from [VoiceData.oscParams]. Only leaf oscillator nodes
 * read overrides; composition nodes do NOT pass oscParams through — the tree structure IS the recipe.
 */
fun SignalGenDsl.toSignalGen(oscParams: Map<String, Double>? = null): SignalGen = when (this) {
    // Primitives
    is SignalGenDsl.Sine -> SignalGens.sine(gain)
    is SignalGenDsl.Sawtooth -> SignalGens.sawtooth(gain)
    is SignalGenDsl.Square -> SignalGens.square(gain)
    is SignalGenDsl.Triangle -> SignalGens.triangle(gain)
    is SignalGenDsl.WhiteNoise -> SignalGens.whiteNoise(Random, gain)
    is SignalGenDsl.Zawtooth -> SignalGens.zawtooth(gain)
    is SignalGenDsl.Impulse -> SignalGens.impulse(gain)
    is SignalGenDsl.Pulze -> SignalGens.pulze(duty, gain)
    is SignalGenDsl.BrownNoise -> SignalGens.brownNoise(Random, gain)
    is SignalGenDsl.PinkNoise -> SignalGens.pinkNoise(Random, gain)

    is SignalGenDsl.Dust -> SignalGens.dust(
        Random,
        density = oscParams?.get("density") ?: density,
        gain = gain,
    )

    is SignalGenDsl.Crackle -> SignalGens.crackle(
        Random,
        density = oscParams?.get("density") ?: density,
        gain = gain,
    )

    is SignalGenDsl.Supersaw -> SignalGens.supersaw(
        voices = oscParams?.get("voices")?.toInt() ?: voices,
        freqSpread = oscParams?.get("freqSpread") ?: freqSpread,
        gain = gain,
    )

    is SignalGenDsl.Silence -> SignalGens.silence()

    // Arithmetic — compositions do NOT pass oscParams through
    is SignalGenDsl.Plus -> left.toSignalGen() + right.toSignalGen()
    is SignalGenDsl.Times -> left.toSignalGen() * right.toSignalGen()
    is SignalGenDsl.Mul -> inner.toSignalGen().mul(factor)
    is SignalGenDsl.Div -> inner.toSignalGen().div(divisor)

    // Frequency
    is SignalGenDsl.Detune -> inner.toSignalGen().detune(semitones)

    // Filters
    is SignalGenDsl.Lowpass -> inner.toSignalGen().lowpass(cutoffHz, q)
    is SignalGenDsl.Highpass -> inner.toSignalGen().highpass(cutoffHz, q)
    is SignalGenDsl.OnePoleLowpass -> inner.toSignalGen().onePoleLowpass(cutoffHz)

    // Envelope
    is SignalGenDsl.Adsr -> inner.toSignalGen().adsr(attackSec, decaySec, sustainLevel, releaseSec)

    // FM
    is SignalGenDsl.Fm -> carrier.toSignalGen().fm(
        modulator = modulator.toSignalGen(),
        ratio = ratio,
        depth = depth,
        envAttackSec = envAttackSec,
        envDecaySec = envDecaySec,
        envSustainLevel = envSustainLevel,
        envReleaseSec = envReleaseSec,
    )
}
