package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_bridge.*

/**
 * Registers all built-in SignalGen oscillators into the registry.
 *
 * Includes basic waveforms (with aliases) and SignalGen compositions (sgpad, sgbell, sgbuzz).
 */
fun SignalGenRegistry.registerDefaults() {
    // Basic waveforms
    val sine = SignalGenDsl.Sine()
    register("sine", sine)
    register("sin", sine)

    val saw = SignalGenDsl.Sawtooth()
    register("sawtooth", saw)
    register("saw", saw)

    val square = SignalGenDsl.Square()
    register("square", square)
    register("sqr", square)

    val triangle = SignalGenDsl.Triangle()
    register("triangle", triangle)
    register("tri", triangle)

    val whiteNoise = SignalGenDsl.WhiteNoise()
    register("white-noise", whiteNoise)
    register("whitenoise", whiteNoise)

    // ─── SignalGen compositions ──────────────────────────────────────────────

    // Rich detuned pad: two saws slightly detuned, mixed and lowpass filtered
    register(
        "sgpad",
        (SignalGenDsl.Sawtooth() + SignalGenDsl.Sawtooth().detune(0.1))
            .div(2.0)
            .onePoleLowpass(3000.0)
    )

    // FM bell: sine carrier with sine modulator
    register(
        "sgbell",
        SignalGenDsl.Sine().fm(
            modulator = SignalGenDsl.Sine(),
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
        SignalGenDsl.Square().lowpass(2000.0),
    )
}
