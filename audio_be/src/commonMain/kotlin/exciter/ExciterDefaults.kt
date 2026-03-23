package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_bridge.*

/**
 * Registers all built-in Exciter oscillators into the registry.
 *
 * Includes basic waveforms (with aliases) and Exciter compositions (sgpad, sgbell, sgbuzz).
 */
fun ExciterRegistry.registerDefaults() {
    // Basic waveforms
    val sine = ExciterDsl.Sine()
    register("sine", sine)
    register("sin", sine)

    val saw = ExciterDsl.Sawtooth()
    register("sawtooth", saw)
    register("saw", saw)

    val square = ExciterDsl.Square()
    register("square", square)
    register("sqr", square)
    register("pulse", square)

    val triangle = ExciterDsl.Triangle()
    register("triangle", triangle)
    register("tri", triangle)

    val ramp = ExciterDsl.Ramp()
    register("ramp", ramp)

    val zawtooth = ExciterDsl.Zawtooth()
    register("zawtooth", zawtooth)
    register("zaw", zawtooth)

    val pulze = ExciterDsl.Pulze()
    register("pulze", pulze)

    val impulse = ExciterDsl.Impulse()
    register("impulse", impulse)

    val silence = ExciterDsl.Silence
    register("silence", silence)

    // ─── SuperOsc ────────────────────────────────────────────────────────────

    val superSaw = ExciterDsl.SuperSaw()
    register("supersaw", superSaw)

    val superSine = ExciterDsl.SuperSine()
    register("supersine", superSine)

    val superSquare = ExciterDsl.SuperSquare()
    register("supersquare", superSquare)
    register("supersqr", superSquare)
    register("superpulse", superSquare)

    val superTri = ExciterDsl.SuperTri()
    register("supertri", superTri)

    val superRamp = ExciterDsl.SuperRamp()
    register("superramp", superRamp)

    // ─── Noises ──────────────────────────────────────────────────────────────

    val whiteNoise = ExciterDsl.WhiteNoise()
    register("whitenoise", whiteNoise)
    register("white", whiteNoise)

    val brownNoise = ExciterDsl.BrownNoise()
    register("brownnoise", brownNoise)
    register("brown", brownNoise)

    val pinkNoise = ExciterDsl.PinkNoise()
    register("pinknoise", pinkNoise)
    register("pink", pinkNoise)

    register("dust", ExciterDsl.Dust())
    register("crackle", ExciterDsl.Crackle())

    // ─── Exciter compositions ──────────────────────────────────────────────

    // Rich detuned pad: two saws slightly detuned, mixed and lowpass filtered
    register(
        "sgpad",
        (ExciterDsl.Sawtooth() + ExciterDsl.Sawtooth().detune(0.1))
            .div(2.0)
            .onePoleLowpass(3000.0)
    )

    // FM bell: sine carrier with sine modulator
    register(
        "sgbell",
        ExciterDsl.Sine().fm(
            modulator = ExciterDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
    )

    // Buzzy filtered square
    register(
        "sgbuzz",
        ExciterDsl.Square().lowpass(2000.0),
    )
}
