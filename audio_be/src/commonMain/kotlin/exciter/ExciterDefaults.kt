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
    register(name = "sine", dsl = sine)
    register(name = "sin", dsl = sine)

    val saw = ExciterDsl.Sawtooth()
    register(name = "sawtooth", dsl = saw)
    register(name = "saw", dsl = saw)

    val square = ExciterDsl.Square()
    register(name = "square", dsl = square)
    register(name = "sqr", dsl = square)
    register(name = "pulse", dsl = square)

    val triangle = ExciterDsl.Triangle()
    register(name = "triangle", dsl = triangle)
    register(name = "tri", dsl = triangle)

    val ramp = ExciterDsl.Ramp()
    register(name = "ramp", dsl = ramp)

    val zawtooth = ExciterDsl.Zawtooth()
    register(name = "zawtooth", dsl = zawtooth)
    register(name = "zaw", dsl = zawtooth)

    val pulze = ExciterDsl.Pulze()
    register(name = "pulze", dsl = pulze)

    val impulse = ExciterDsl.Impulse()
    register(name = "impulse", dsl = impulse)

    val silence = ExciterDsl.Silence
    register(name = "silence", dsl = silence)

    // ─── SuperOsc ────────────────────────────────────────────────────────────

    val superSaw = ExciterDsl.SuperSaw()
    register(name = "supersaw", dsl = superSaw)

    val superSine = ExciterDsl.SuperSine()
    register(name = "supersine", dsl = superSine)

    val superSquare = ExciterDsl.SuperSquare()
    register(name = "supersquare", dsl = superSquare)
    register(name = "supersqr", dsl = superSquare)
    register(name = "superpulse", dsl = superSquare)

    val superTri = ExciterDsl.SuperTri()
    register(name = "supertri", dsl = superTri)

    val superRamp = ExciterDsl.SuperRamp()
    register(name = "superramp", dsl = superRamp)

    // ─── Noises ──────────────────────────────────────────────────────────────

    val whiteNoise = ExciterDsl.WhiteNoise
    register(name = "whitenoise", dsl = whiteNoise)
    register(name = "white", dsl = whiteNoise)

    val brownNoise = ExciterDsl.BrownNoise
    register(name = "brownnoise", dsl = brownNoise)
    register(name = "brown", dsl = brownNoise)

    val pinkNoise = ExciterDsl.PinkNoise
    register(name = "pinknoise", dsl = pinkNoise)
    register(name = "pink", dsl = pinkNoise)

    register(name = "dust", dsl = ExciterDsl.Dust())
    register(name = "crackle", dsl = ExciterDsl.Crackle())

    // ─── Physical models ───────────────────────────────────────────────────

    val pluck = ExciterDsl.Pluck()
    register(name = "pluck", dsl = pluck)
    register(name = "ks", dsl = pluck)
    register(name = "string", dsl = pluck)

    val superPluck = ExciterDsl.SuperPluck()
    register(name = "superpluck", dsl = superPluck)

    // ─── Exciter compositions ──────────────────────────────────────────────

    // Rich detuned pad: two saws slightly detuned, mixed and lowpass filtered
    register(
        name = "sgpad",
        dsl = (ExciterDsl.Sawtooth() + ExciterDsl.Sawtooth().detune(semitones = 0.1))
            .div(other = ExciterDsl.Param(name = "divisor", default = 2.0))
            .onePoleLowpass(cutoffHz = 3000.0)
    )

    // FM bell: sine carrier with sine modulator
    register(
        name = "sgbell",
        dsl = ExciterDsl.Sine().fm(
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
        name = "sgbuzz",
        dsl = ExciterDsl.Square().lowpass(cutoffHz = 2000.0),
    )

    // TESTs
    register(
        name = "noisysaw",
        dsl = ExciterDsl.Sawtooth()
            .plus(other = ExciterDsl.BerlinNoise().mul(other = ExciterDsl.Param(name = "density", default = 0.5)))
    )

    register(
        name = "sinesaw",
        dsl = ExciterDsl.Sawtooth().plus(other = ExciterDsl.Sine()).div(other = ExciterDsl.Param(name = "divisor", default = 2.0))
    )
}

// ═════════════════════════════════════════════════════════════════════════════════
// Exciter Composition Recipes
//
// Catalog of useful ExciterDsl combinations for future presets.
// Any Exciter can fill any param slot — maximum flexibility.
//
// ── Layering (Plus) ──────────────────────────────────────────────────────────
//   Saw + Saw.detune(0.1)                          — classic detuned pair, thick analog lead
//   Saw + Square                                    — mixed waveform, fuller spectrum
//   Pluck + Sine                                    — body from pluck, sustain/sub from sine
//   SuperSaw + WhiteNoise.mul(Param("mix", 0.05))  — breathy supersaw
//
// ── Amplitude Shaping (Mul/Times) ────────────────────────────────────────────
//   Saw.mul(PerlinNoise)                            — organic amplitude drift, lo-fi texture
//   WhiteNoise.mul(Sine)                            — sine-shaped noise bursts (hi-hat like)
//   Any.times(BerlinNoise(rate=0.3))                — random amplitude gating
//
// ── Noise as Modulation (in param slots) ─────────────────────────────────────
//   Lowpass(cutoffHz = PerlinNoise(rate=0.5))       — wandering filter
//   SuperSaw(freqSpread = BerlinNoise)              — evolving detune
//   Tremolo(rate = PerlinNoise(rate=0.2))           — irregular tremolo speed
//   Distort(amount = PerlinNoise(rate=2.0))         — breathing distortion
//
// ── Filtered Sources ─────────────────────────────────────────────────────────
//   SuperSaw.lowpass(cutoffHz)                      — classic subtractive synth
//   WhiteNoise.lowpass(cutoffHz)                    — wind / ocean / breath
//   Square.lowpass(1000).distort(0.3)               — gritty bass
//   Saw.highpass(200).lowpass(4000)                  — bandpass character
//
// ── FM Synthesis ─────────────────────────────────────────────────────────────
//   Sine.fm(Sine, ratio=2.0, depth=200)             — bell / metallic
//   Sine.fm(Sine, ratio=1.0, depth=500)             — harsh brass
//   Triangle.fm(Sine, ratio=3.0, depth=100)         — softer FM
//   Sine.fm(Sine, ratio=1.4, depth=300,             — decaying FM bell
//           envDecaySec=0.5, envSustainLevel=0.0)
//
// ── Effects Chains ───────────────────────────────────────────────────────────
//   SuperSaw.distort(0.4).lowpass(3000)             — heavy lead
//   Pluck.phaser(0.3, 0.5)                          — spacey pluck
//   Square.crush(6.0)                               — retro / chiptune
//   Saw.coarse(8.0)                                 — sample-rate reduced lo-fi
//
// ── Percussive ───────────────────────────────────────────────────────────────
//   WhiteNoise.adsr(0.001, 0.05, 0.0, 0.01)        — hi-hat
//       .highpass(8000)
//   Impulse.lowpass(200)                             — kick body
//   Sine.pitchEnvelope(amount=24, decaySec=0.05)    — kick with pitch sweep
//   Dust.mul(PinkNoise)                             — textured crackle
//
// ═════════════════════════════════════════════════════════════════════════════════
