package io.peekandpoke.klang.audio_be.engines

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.NoOpAudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.filter.CrushRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.DistortionRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline

class AudioEngineSpec : StringSpec({

    "fromName resolves modern (case-insensitive)" {
        AudioEngine.fromName("modern") shouldBe AudioEngine.Modern
        AudioEngine.fromName("MODERN") shouldBe AudioEngine.Modern
        AudioEngine.fromName("Modern") shouldBe AudioEngine.Modern
    }

    "fromName resolves pedal (case-insensitive)" {
        AudioEngine.fromName("pedal") shouldBe AudioEngine.Pedal
        AudioEngine.fromName("PEDAL") shouldBe AudioEngine.Pedal
        AudioEngine.fromName("Pedal") shouldBe AudioEngine.Pedal
    }

    "fromName falls back to Modern for null" {
        AudioEngine.fromName(null) shouldBe AudioEngine.Modern
    }

    "fromName falls back to Modern for unknown name" {
        AudioEngine.fromName("classic") shouldBe AudioEngine.Modern
        AudioEngine.fromName("does-not-exist") shouldBe AudioEngine.Modern
        AudioEngine.fromName("") shouldBe AudioEngine.Modern
    }

    "Modern engine: ADSR is the LAST renderer in the pipeline" {
        val pipeline = activePipeline(AudioEngine.Modern)
        // Last renderer must be EnvelopeRenderer
        (pipeline.last() is EnvelopeRenderer) shouldBe true
    }

    "Modern engine: waveshapers come BEFORE the envelope" {
        val pipeline = activePipeline(AudioEngine.Modern)
        val crushIdx = pipeline.indexOfFirst { it is CrushRenderer }
        val distIdx = pipeline.indexOfFirst { it is DistortionRenderer }
        val envIdx = pipeline.indexOfFirst { it is EnvelopeRenderer }
        (crushIdx < envIdx) shouldBe true
        (distIdx < envIdx) shouldBe true
    }

    "Pedal engine: ADSR is BEFORE all waveshapers" {
        val pipeline = activePipeline(AudioEngine.Pedal)
        val envIdx = pipeline.indexOfFirst { it is EnvelopeRenderer }
        val crushIdx = pipeline.indexOfFirst { it is CrushRenderer }
        val distIdx = pipeline.indexOfFirst { it is DistortionRenderer }
        (envIdx < crushIdx) shouldBe true
        (envIdx < distIdx) shouldBe true
    }

    "Pedal engine: envelope is NOT the last renderer" {
        val pipeline = activePipeline(AudioEngine.Pedal)
        (pipeline.last() is EnvelopeRenderer) shouldBe false
    }
})

/**
 * Builds a pipeline with crush + distort active so we can locate the
 * waveshaper renderers in the assertions above.
 */
private fun activePipeline(engine: AudioEngine) = buildFilterPipeline(
    engine = engine,
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
