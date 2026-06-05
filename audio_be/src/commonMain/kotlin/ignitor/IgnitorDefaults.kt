package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.onePoleLowpass
import io.peekandpoke.klang.audio_bridge.plus

/**
 * Registers all built-in oscillators with explicit Param slots for sprudel oscParam() compatibility.
 *
 * IgnitorDsl types default to Constant (locked, no oscParam override). Here we explicitly
 * open Param slots — pulled from [IgnitorDsl.Slots] — for the parameters that sprudel's
 * oscParam() functions target:
 * - freq uses [IgnitorDsl.Freq] (voice note frequency) on all pitched oscillators
 * - "analog" on all pitched oscillators
 * - "voices", "freqSpread" on super oscillators
 * - "duty" on square/sqr/pulse/pulze (one pulse oscillator; 0.5 = square)
 * - "density" on dust/crackle
 * - "decay", "brightness", "pickPosition", "stiffness" on pluck
 */
fun IgnitorRegistry.registerDefaults() {

    val slots = IgnitorDsl.Slots

    // ─── Basic waveforms ─────────────────────────────────────────────────────
    // Each gets "analog" as an overridable param

    val sine = IgnitorDsl.Sine(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "sine", dsl = sine)
    register(name = "sin", dsl = sine)

    val saw = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "sawtooth", dsl = saw)
    register(name = "saw", dsl = saw)

    // square / sqr / pulse / pulze are one pulse oscillator with a "duty" osc-param (0.5 = square).
    val pulse = IgnitorDsl.Pulze(freq = IgnitorDsl.Freq, duty = slots.duty, analog = slots.analog)
    register(name = "square", dsl = pulse)
    register(name = "sqr", dsl = pulse)
    register(name = "pulse", dsl = pulse)
    register(name = "pulze", dsl = pulse)

    val triangle = IgnitorDsl.Triangle(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "triangle", dsl = triangle)
    register(name = "tri", dsl = triangle)

    val ramp = IgnitorDsl.Ramp(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "ramp", dsl = ramp)

    val zawtooth = IgnitorDsl.Zawtooth(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "zawtooth", dsl = zawtooth)
    register(name = "zaw", dsl = zawtooth)

    val impulse = IgnitorDsl.Impulse(freq = IgnitorDsl.Freq, analog = slots.analog)
    register(name = "impulse", dsl = impulse)

    val silence = IgnitorDsl.Silence
    register(name = "silence", dsl = silence)

    // ─── Super oscillators ───────────────────────────────────────────────────
    // Each gets "voices", "freqSpread", "analog" as overridable params

    val superSaw = IgnitorDsl.SuperSaw(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        analog = slots.analog,
    )
    register(name = "supersaw", dsl = superSaw)

    val superSine = IgnitorDsl.SuperSine(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        analog = slots.analog,
    )
    register(name = "supersine", dsl = superSine)

    val superSquare = IgnitorDsl.SuperSquare(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        analog = slots.analog,
    )
    register(name = "supersquare", dsl = superSquare)
    register(name = "supersqr", dsl = superSquare)
    register(name = "superpulse", dsl = superSquare)

    val superTri = IgnitorDsl.SuperTri(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        analog = slots.analog,
    )
    register(name = "supertri", dsl = superTri)

    val superRamp = IgnitorDsl.SuperRamp(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        analog = slots.analog,
    )
    register(name = "superramp", dsl = superRamp)

    // ─── Noises ──────────────────────────────────────────────────────────────

    val whiteNoise = IgnitorDsl.WhiteNoise()
    register(name = "whitenoise", dsl = whiteNoise)
    register(name = "white", dsl = whiteNoise)

    val brownNoise = IgnitorDsl.BrownNoise()
    register(name = "brownnoise", dsl = brownNoise)
    register(name = "brown", dsl = brownNoise)

    val pinkNoise = IgnitorDsl.PinkNoise()
    register(name = "pinknoise", dsl = pinkNoise)
    register(name = "pink", dsl = pinkNoise)

    val perlinNoise = IgnitorDsl.PerlinNoise(rate = slots.rate)
    register(name = "perlinnoise", dsl = perlinNoise)
    register(name = "perlin", dsl = perlinNoise)

    val berlinNoise = IgnitorDsl.BerlinNoise(rate = slots.rate)
    register(name = "berlinnoise", dsl = berlinNoise)
    register(name = "berlin", dsl = berlinNoise)

    register(name = "dust", dsl = IgnitorDsl.Dust(density = slots.density))
    register(name = "crackle", dsl = IgnitorDsl.Crackle(density = slots.density))

    // ─── Physical models ────────────────────────────────────────────────────

    val pluck = IgnitorDsl.Pluck(
        freq = IgnitorDsl.Freq,
        decay = slots.decay,
        brightness = slots.brightness,
        pickPosition = slots.pickPosition,
        stiffness = slots.stiffness,
        analog = slots.analog,
    )
    register(name = "pluck", dsl = pluck)
    register(name = "ks", dsl = pluck)
    register(name = "string", dsl = pluck)

    val superPluck = IgnitorDsl.SuperPluck(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        freqSpread = slots.freqSpread,
        decay = slots.decay,
        brightness = slots.brightness,
        pickPosition = slots.pickPosition,
        stiffness = slots.stiffness,
        analog = slots.analog,
    )
    register(name = "superpluck", dsl = superPluck)

    // ─── Ignitor compositions ──────────────────────────────────────────────

    // Rich detuned pad: two saws slightly detuned, mixed and lowpass filtered
    register(
        name = "sgpad",
        dsl = (IgnitorDsl.Sawtooth() + IgnitorDsl.Sawtooth().detune(semitones = 0.1))
            .div(other = IgnitorDsl.Param(name = "divisor", default = 2.0))
            .onePoleLowpass(cutoffHz = 3000.0)
    )

    // FM bell: sine carrier with sine modulator
    register(
        name = "sgbell",
        dsl = IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
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
        dsl = IgnitorDsl.Square().lowpass(cutoffHz = 2000.0),
    )
}

// ═════════════════════════════════════════════════════════════════════════════════
// Ignitor Composition Recipes
//
// Catalog of useful IgnitorDsl combinations for future presets.
// Any Ignitor can fill any param slot — maximum flexibility.
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
