package io.peekandpoke.klang.audio_be.engines

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.NoOpAudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl

/**
 * Guards the engine registration round-trip: built-ins are seeded, custom engines register
 * and resolve by name, and a resolved [EngineDsl] drives the data-driven pipeline (arbitrary
 * stage order + omitted stages).
 */
class EngineRegistrySpec : StringSpec({

    "seeds built-ins, case-insensitive, falls back to modern" {
        val reg = EngineRegistry()
        reg.get("modern") shouldBe EngineDsl.modern
        reg.get("pedal") shouldBe EngineDsl.pedal
        reg.get("PEDAL") shouldBe EngineDsl.pedal
        reg.get(null) shouldBe EngineDsl.modern
        reg.get("does-not-exist") shouldBe EngineDsl.modern
    }

    "a registered custom engine is resolvable by name" {
        val custom = EngineDsl(listOf(StageDsl.Vca(expK = 2.0, declickSeconds = 0.0005), StageDsl.Filter()))
        val reg = EngineRegistry().apply { register("droney", custom) }
        reg.get("droney") shouldBe custom
    }

    "a custom engine drives arbitrary stage order + omitted stages" {
        // VCA first, then filter — and NO waveshaper stages, even though crush/distort are active.
        val custom = EngineDsl(listOf(StageDsl.Vca(), StageDsl.Filter()))

        val pipeline = buildFilterPipeline(
            engine = custom,
            modulators = emptyList(),
            startFrame = 0,
            gateEndFrame = 1000,
            crush = Voice.Crush(amount = 4.0),                 // active...
            coarse = Voice.Coarse(amount = 0.0),
            mainFilter = NoOpAudioFilter,
            envelope = Voice.Envelope(100.0, 100.0, 0.7, 100.0),
            distort = Voice.Distort(amount = 0.5, shape = "soft"), // ...active...
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            phaser = Voice.Phaser(0.0, 0.0, 1000.0, 1000.0),
            sampleRate = 48000,
        )

        // ...but the engine declares no Crush/Distort slot, so only Vca + Filter render — VCA first.
        pipeline.size shouldBe 2
        (pipeline.first() is EnvelopeRenderer) shouldBe true
    }
})
