/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Render-effect guard: each newly-exposed *single-oscillator shape* knob must actually reach the audio,
 * end-to-end (DSL → `IgnitorDslRuntime` dispatch → `Ignitors.*` factory → `WaveIgnitor`). Perturbing the
 * knob must change the rendered block; if a future edit drops/swaps a field anywhere in that chain, the
 * knob silently becomes a no-op and the corresponding case here goes red.
 *
 * These oscillators render deterministically (no rng) once `analog` is pinned to a constant, so a plain
 * "default block != perturbed block" comparison isolates the knob's effect. The unison `Super*` character
 * knobs (`spreadPower`/`sideAtten`/`gainJitter`/`centerJitterScale`) can't be isolated this way — the engine
 * draws per-voice random gains/phases — so their audible effect is guarded on the shared `DetunedStackIgnitor`
 * by `AnalogSawSpec`, and their DSL binding by the `KlangScriptSuper*Spec` dual-language specs.
 */
class OscShapeEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    fun createCtx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        releaseFrames = (0.1 * sampleRate).toInt(),
        voiceEndFrame = sampleRate + (0.1 * sampleRate).toInt(),
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    fun render(dsl: IgnitorDsl, freqHz: Double): List<Double> {
        val buffer = AudioBuffer(blockFrames)
        dsl.toExciter().generate(buffer, freqHz, createCtx())
        return buffer.toList()
    }

    /** Perturbing knob from [default] to [perturbed] must change the rendered block. */
    fun assertTakesEffect(default: IgnitorDsl, perturbed: IgnitorDsl, freqHz: Double = 440.0) {
        // sanity: the default renders the same twice (deterministic), so any diff below is the knob.
        render(default, freqHz) shouldNotBe render(perturbed, freqHz)
    }

    val clean = IgnitorDsl.Constant(0.0) // analog off → deterministic render

    "Sawtooth.resetSamples reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Sawtooth(analog = clean, resetSamples = 2.0),
            IgnitorDsl.Sawtooth(analog = clean, resetSamples = 20.0),
        )
    }

    "Sawtooth.shapeMax reaches the audio (clamps the flyback at high pitch)" {
        assertTakesEffect(
            IgnitorDsl.Sawtooth(analog = clean, resetSamples = 10.0, shapeMax = 0.1),
            IgnitorDsl.Sawtooth(analog = clean, resetSamples = 10.0, shapeMax = 0.5),
            freqHz = 4000.0,
        )
    }

    "Ramp.resetSamples reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Ramp(analog = clean, resetSamples = 2.0),
            IgnitorDsl.Ramp(analog = clean, resetSamples = 20.0),
        )
    }

    "Pulze.flankSamples reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Pulze(analog = clean, flankSamples = 2.0),
            IgnitorDsl.Pulze(analog = clean, flankSamples = 20.0),
        )
    }

    "Pulze.riseFlank reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Pulze(analog = clean, riseFlank = 0.0),
            IgnitorDsl.Pulze(analog = clean, riseFlank = 0.9),
        )
    }

    "Pulze.fallFlank reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Pulze(analog = clean, fallFlank = 0.0),
            IgnitorDsl.Pulze(analog = clean, fallFlank = 0.9),
        )
    }

    "Pulze.duty reaches the audio" {
        assertTakesEffect(
            IgnitorDsl.Pulze(analog = clean, duty = IgnitorDsl.Constant(0.5)),
            IgnitorDsl.Pulze(analog = clean, duty = IgnitorDsl.Constant(0.2)),
        )
    }
})
