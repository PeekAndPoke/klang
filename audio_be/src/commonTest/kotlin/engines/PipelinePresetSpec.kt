/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.engines

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.NoOpAudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.filter.CrushRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.DistortionRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline

class PipelinePresetSpec : StringSpec({

    "fromName resolves modern (case-insensitive)" {
        PipelinePreset.fromName("modern") shouldBe PipelinePreset.Modern
        PipelinePreset.fromName("MODERN") shouldBe PipelinePreset.Modern
        PipelinePreset.fromName("Modern") shouldBe PipelinePreset.Modern
    }

    "fromName resolves pedal (case-insensitive)" {
        PipelinePreset.fromName("pedal") shouldBe PipelinePreset.Pedal
        PipelinePreset.fromName("PEDAL") shouldBe PipelinePreset.Pedal
        PipelinePreset.fromName("Pedal") shouldBe PipelinePreset.Pedal
    }

    "fromName falls back to Modern for null" {
        PipelinePreset.fromName(null) shouldBe PipelinePreset.Modern
    }

    "fromName falls back to Modern for unknown name" {
        PipelinePreset.fromName("classic") shouldBe PipelinePreset.Modern
        PipelinePreset.fromName("does-not-exist") shouldBe PipelinePreset.Modern
        PipelinePreset.fromName("") shouldBe PipelinePreset.Modern
    }

    "Modern engine: ADSR is the LAST renderer in the pipeline" {
        val pipeline = activePipeline(PipelinePreset.Modern)
        // Last renderer must be EnvelopeRenderer
        (pipeline.last() is EnvelopeRenderer) shouldBe true
    }

    "Modern engine: waveshapers come BEFORE the envelope" {
        val pipeline = activePipeline(PipelinePreset.Modern)
        val crushIdx = pipeline.indexOfFirst { it is CrushRenderer }
        val distIdx = pipeline.indexOfFirst { it is DistortionRenderer }
        val envIdx = pipeline.indexOfFirst { it is EnvelopeRenderer }
        (crushIdx < envIdx) shouldBe true
        (distIdx < envIdx) shouldBe true
    }

    "Pedal engine: ADSR is BEFORE all waveshapers" {
        val pipeline = activePipeline(PipelinePreset.Pedal)
        val envIdx = pipeline.indexOfFirst { it is EnvelopeRenderer }
        val crushIdx = pipeline.indexOfFirst { it is CrushRenderer }
        val distIdx = pipeline.indexOfFirst { it is DistortionRenderer }
        (envIdx < crushIdx) shouldBe true
        (envIdx < distIdx) shouldBe true
    }

    "Pedal engine: envelope is NOT the last renderer" {
        val pipeline = activePipeline(PipelinePreset.Pedal)
        (pipeline.last() is EnvelopeRenderer) shouldBe false
    }

    // ── Minimal pipeline tests (all waveshapers inactive) ─────────────────────

    "Modern minimal: with all effects off, envelope is still last" {
        val pipeline = minimalPipeline(PipelinePreset.Modern)
        // Pipeline should be: AudioFilterRenderer + EnvelopeRenderer (at minimum)
        pipeline.size shouldBe 2
        (pipeline.last() is EnvelopeRenderer) shouldBe true
    }

    "Pedal minimal: with all effects off, envelope is still first" {
        val pipeline = minimalPipeline(PipelinePreset.Pedal)
        // Pipeline should be: EnvelopeRenderer + AudioFilterRenderer (at minimum)
        pipeline.size shouldBe 2
        (pipeline.first() is EnvelopeRenderer) shouldBe true
    }
})

/** Builds a pipeline with everything OFF (no crush, no distort, no tremolo, no phaser). */
private fun minimalPipeline(preset: PipelinePreset) = buildFilterPipeline(
    pipeline = preset.dsl,
    modulators = emptyList(),
    startFrame = 0,
    gateEndFrame = 1000,
    crush = Voice.Crush(amount = 0.0),
    coarse = Voice.Coarse(amount = 0.0),
    mainFilter = NoOpAudioFilter,
    envelope = Voice.Envelope(
        attackFrames = 100.0,
        decayFrames = 100.0,
        sustainLevel = 0.7,
        releaseFrames = 100.0,
    ),
    distort = Voice.Distort(amount = 0.0, shape = "soft"),
    tremolo = Voice.Tremolo(rate = 0.0, depth = 0.0, skew = 0.0, phase = 0.0, shape = null),
    phaser = Voice.Phaser(rate = 0.0, depth = 0.0, center = 1000.0, sweep = 1000.0),
    sampleRate = 48000,
)

/**
 * Builds a pipeline with crush + distort active so we can locate the
 * waveshaper renderers in the assertions above.
 */
private fun activePipeline(preset: PipelinePreset) = buildFilterPipeline(
    pipeline = preset.dsl,
    modulators = emptyList(),
    startFrame = 0,
    gateEndFrame = 1000,
    crush = Voice.Crush(amount = 4.0),
    coarse = Voice.Coarse(amount = 0.0),
    mainFilter = NoOpAudioFilter,
    envelope = Voice.Envelope(
        attackFrames = 100.0,
        decayFrames = 100.0,
        sustainLevel = 0.7,
        releaseFrames = 100.0,
    ),
    distort = Voice.Distort(amount = 0.5, shape = "soft"),
    tremolo = Voice.Tremolo(rate = 0.0, depth = 0.0, skew = 0.0, phase = 0.0, shape = null),
    phaser = Voice.Phaser(rate = 0.0, depth = 0.0, center = 1000.0, sweep = 1000.0),
    sampleRate = 48000,
)
