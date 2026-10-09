/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.audio_bridge.plus

/**
 * Registers every built-in sound: its source from [builtInSources] in the built-in voice shape
 * ([IgnitorRegistry.builtInVoice], `source.pregain().classic()`). The tree ends in `classic()`, so the pattern's voice
 * doors reach it.
 */
fun IgnitorRegistry.registerDefaults() {
    for ((name, source) in builtInSources()) {
        register(name = name, dsl = IgnitorRegistry.builtInVoice(source))
    }
}

/**
 * The built-in sounds' SOURCES by name (several names share one tree), in registration order: what a
 * built-in is before [IgnitorRegistry.builtInVoice] wraps it in the classic voice. The one list
 * [registerDefaults] registers, and the list a spec renders each built-in against its own raw source from.
 *
 * Every tree opens explicit Param slots, pulled from [IgnitorDsl.Slots], for the parameters that
 * sprudel's ignitorParam() functions target:
 * - freq uses [IgnitorDsl.Freq] (voice note frequency) on all pitched oscillators
 * - "analog" on all pitched oscillators
 * - "voices", "spread" on super oscillators
 * - "duty" on square/sqr/pulse/pulze (one pulse oscillator; 0.5 = square)
 * - "density" on dust/crackle
 * - "leak" on brownnoise
 * - "feedback", "brightness", "pickPosition", "stiffness" on pluck
 */
internal fun builtInSources(): Map<String, IgnitorDsl> = buildMap {

    val slots = IgnitorDsl.Slots

    // ─── Basic waveforms ─────────────────────────────────────────────────────
    // Each gets "analog" as an overridable param

    val sine = IgnitorDsl.Sine(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("sine", sine)
    put("sin", sine)

    val saw = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("sawtooth", saw)
    put("saw", saw)

    // Rounded pulse (square / sqr / pulse) — one band-limited pulse with a "duty" Ignitor slot (0.5 = square).
    val pulse = IgnitorDsl.Pulze(freq = IgnitorDsl.Freq, duty = slots.duty, analog = slots.analog)
    put("square", pulse)
    put("sqr", pulse)
    put("pulse", pulse)
    // Raw pulse (pulze) — naive/aliased counterpart, same "duty" Ignitor slot.
    val rawPulse = IgnitorDsl.RawPulze(freq = IgnitorDsl.Freq, duty = slots.duty, analog = slots.analog)
    put("pulze", rawPulse)

    val tri = IgnitorDsl.Tri(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("triangle", tri)
    put("tri", tri)

    // ramp = rounded reverse-saw; zamp = its raw/aliased counterpart.
    val ramp = IgnitorDsl.Ramp(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("ramp", ramp)

    val zamp = IgnitorDsl.Zamp(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("zamp", zamp)

    // zaw = raw/aliased saw (counterpart of saw/sawtooth).
    val zawtooth = IgnitorDsl.Zawtooth(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("zawtooth", zawtooth)
    put("zaw", zawtooth)

    val impulse = IgnitorDsl.Impulse(freq = IgnitorDsl.Freq, analog = slots.analog)
    put("impulse", impulse)

    val silence = IgnitorDsl.Silence
    put("silence", silence)

    // ─── Super oscillators ───────────────────────────────────────────────────
    // Each gets "voices", "spread", "analog" as overridable params

    val superSaw = IgnitorDsl.SuperSaw(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        analog = slots.analog,
    )
    put("supersaw", superSaw)

    val superSine = IgnitorDsl.SuperSine(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        analog = slots.analog,
    )
    put("supersine", superSine)

    val superSquare = IgnitorDsl.SuperSquare(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        analog = slots.analog,
    )
    put("supersquare", superSquare)
    put("supersqr", superSquare)
    put("superpulse", superSquare)

    val superTri = IgnitorDsl.SuperTri(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        analog = slots.analog,
    )
    put("supertri", superTri)

    val superRamp = IgnitorDsl.SuperRamp(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        analog = slots.analog,
    )
    put("superramp", superRamp)

    // ─── Noises ──────────────────────────────────────────────────────────────

    val whiteNoise = IgnitorDsl.WhiteNoise()
    put("whitenoise", whiteNoise)
    put("white", whiteNoise)

    val brownNoise = IgnitorDsl.BrownNoise()
    put("brownnoise", brownNoise)
    put("brown", brownNoise)

    val pinkNoise = IgnitorDsl.PinkNoise()
    put("pinknoise", pinkNoise)
    put("pink", pinkNoise)

    val perlinNoise = IgnitorDsl.PerlinNoise(rate = slots.rate)
    put("perlinnoise", perlinNoise)
    put("perlin", perlinNoise)

    val berlinNoise = IgnitorDsl.BerlinNoise(rate = slots.rate)
    put("berlinnoise", berlinNoise)
    put("berlin", berlinNoise)

    put("dust", IgnitorDsl.Dust(density = slots.density))
    put("crackle", IgnitorDsl.Crackle(chaos = slots.chaos))

    // ─── Physical models ────────────────────────────────────────────────────

    val pluck = IgnitorDsl.Pluck(
        freq = IgnitorDsl.Freq,
        feedback = slots.feedback,
        brightness = slots.brightness,
        pickPosition = slots.pickPosition,
        stiffness = slots.stiffness,
        analog = slots.analog,
    )
    put("pluck", pluck)
    put("ks", pluck)
    put("string", pluck)

    val superPluck = IgnitorDsl.SuperPluck(
        freq = IgnitorDsl.Freq,
        voices = slots.voices,
        spread = slots.spread,
        feedback = slots.feedback,
        brightness = slots.brightness,
        pickPosition = slots.pickPosition,
        stiffness = slots.stiffness,
        analog = slots.analog,
    )
    put("superpluck", superPluck)

    // ─── Ignitor compositions ──────────────────────────────────────────────

    // Rich detuned pad: two saws slightly detuned, mixed and lowpass filtered
    put(
        "sgpad",
        (IgnitorDsl.Saw() + IgnitorDsl.Saw().detune(semitones = 0.1))
            .div(other = IgnitorDsl.Param(name = "divisor", default = 2.0))
            .onepole(freq = 3000.0)
    )

    // FM bell: sine carrier with sine modulator
    put(
        "sgbell",
        IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            attack = 0.001,
            decay = 0.5,
            sustain = 0.0,
            // Non-zero on purpose: with release 0 the depth collapses to zero in ONE sample at
            // gate end — a hard frequency step that ticks on every note-off. Release 0 is raw
            // engine semantics (maintainer, 2026-08-28: "0 means 0"), so the PRESET carries the
            // ramp. Found when the per-sample depth envelope made the collapse deterministic.
            release = 0.05,
        )
    )

    // Buzzy filtered square
    put(
        "sgbuzz",
        IgnitorDsl.Square().lowpass(freq = 2000.0),
    )

    // ─── Unified-EQ demo: the smallest sound that exercises the fused EqCore end to end
    //     (the authoring surface for songs is `.eq(e => e.band(...))`; this preset stays as the
    //     knob-per-param test sound) ─────────────────────────────────────────────────────

    // Sawtooth through one fused Eq: a bell (0 dB by default = bit-transparent, so the
    // sound equals a plain saw until "eqdb" moves) followed by a gentle cabinet lowpass.
    // All knobs are Ignitor slots — override per note via `.ignitorParam("eqdb", 9)` etc.
    put(
        "eqdemo",
        IgnitorDsl.Eq(
            inner = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = slots.analog),
            sections = listOf(
                IgnitorDsl.EqSection.Bell(
                    freq = IgnitorDsl.Param(name = "eqhz", default = 1200.0, description = "Bell centre frequency"),
                    q = IgnitorDsl.Param(name = "eqq", default = 0.707, description = "Bell pre-gain bandwidth"),
                    db = IgnitorDsl.Param(name = "eqdb", default = 0.0, description = "Bell gain in dB (0 = transparent)"),
                ),
                IgnitorDsl.EqSection.Lowpass(
                    freq = IgnitorDsl.Param(name = "eqlp", default = 12000.0, description = "Cabinet lowpass cutoff"),
                    q = IgnitorDsl.Constant(0.707),
                ),
            ),
        ),
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
//   Lowpass(freq = PerlinNoise(rate=0.5))       — wandering filter
//   SuperSaw(detune = BerlinNoise)              — evolving detune
//   Tremolo(rate = PerlinNoise(rate=0.2))           — irregular tremolo speed
//   Distort(amount = PerlinNoise(rate=2.0))         — breathing distortion
//
// ── Filtered Sources ─────────────────────────────────────────────────────────
//   SuperSaw.lowpass(freq)                          — classic subtractive synth
//   WhiteNoise.lowpass(freq)                        — wind / ocean / breath
//   Square.lowpass(1000).distort(0.3)               — gritty bass
//   Saw.highpass(200).lowpass(4000)                  — bandpass character
//
// ── FM Synthesis ─────────────────────────────────────────────────────────────
//   Sine.fm(Sine, ratio=2.0, depth=200)             — bell / metallic
//   Sine.fm(Sine, ratio=1.0, depth=500)             — harsh brass
//   Tri.fm(Sine, ratio=3.0, depth=100)              — softer FM
//   Sine.fm(Sine, ratio=1.4, depth=300,             — decaying FM bell
//           decay=0.5, sustain=0.0)
//
// ── Effects Chains ───────────────────────────────────────────────────────────
//   SuperSaw.distort(0.4).lowpass(3000)             — heavy lead
//   Pluck.phaser(0.5, 0.3)                           : spacey pluck (wet, rate)
//   Square.crush(6.0)                               — retro / chiptune
//   Saw.coarse(8.0)                                 — sample-rate reduced lo-fi
//
// ── Percussive ───────────────────────────────────────────────────────────────
//   WhiteNoise.adsr(0.001, 0.05, 0.0, 0.01)        — hi-hat
//       .highpass(8000)
//   Impulse.lowpass(200)                             — kick body
//   Sine.pitchEnvelope(24, x => x.adsr(0.01, 0.05, 0, 0)): kick with pitch sweep
//   Dust.mul(PinkNoise)                             — textured crackle
//
// ═════════════════════════════════════════════════════════════════════════════════
