package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.SourceLocationChain

class KlangOfflineRendererTest : StringSpec({

    /**
     * Minimal pattern that produces a single event at a given sound name.
     */
    fun singleNotePattern(sound: String, freqHz: Double = 440.0): KlangPattern {
        return object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                return listOf(
                    object : KlangPatternEvent {
                        override val startCycles = 0.0
                        override val durationCycles = 1.0
                        override val sourceLocations: SourceLocationChain? = null
                        override fun toVoiceData() = VoiceData.empty.copy(
                            sound = sound,
                            freqHz = freqHz,
                        )
                    }
                )
            }
        }
    }

    "render with default ignitors produces non-zero audio" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        val result = renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { samples, _ -> blocks.add(samples.copyOf()) },
        )

        result.totalFrames shouldBeGreaterThan 0
        result.durationSec shouldBeGreaterThan 0.0
        blocks.any { block -> block.any { it != 0.toShort() } } shouldBe true
    }

    "render with empty customIgnitors still uses defaults" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        renderer.render(
            pattern = singleNotePattern("triangle"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = emptyList(),
            onBlock = { samples, _ -> blocks.add(samples.copyOf()) },
        )

        blocks.any { block -> block.any { it != 0.toShort() } } shouldBe true
    }

    "render with custom ignitor produces non-zero audio" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        val customDsl = IgnitorDsl.Sine()

        renderer.render(
            pattern = singleNotePattern("myCustomOsc"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("myCustomOsc" to customDsl),
            onBlock = { samples, _ -> blocks.add(samples.copyOf()) },
        )

        blocks.any { block -> block.any { it != 0.toShort() } } shouldBe true
    }

    "render with unknown sound produces silence" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        renderer.render(
            pattern = singleNotePattern("nonExistentSound_xyz"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { samples, _ -> blocks.add(samples.copyOf()) },
        )

        blocks.all { block -> block.all { it == 0.toShort() } } shouldBe true
    }

    "custom ignitor overrides built-in default" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocksDefault = mutableListOf<ShortArray>()
        val blocksOverride = mutableListOf<ShortArray>()

        // Render with default sine
        renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { samples, _ -> blocksDefault.add(samples.copyOf()) },
        )

        // Render with custom "sine" that's actually a sawtooth
        renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("sine" to IgnitorDsl.Sawtooth()),
            onBlock = { samples, _ -> blocksOverride.add(samples.copyOf()) },
        )

        // Both should produce non-zero audio
        blocksDefault.any { block -> block.any { it != 0.toShort() } } shouldBe true
        blocksOverride.any { block -> block.any { it != 0.toShort() } } shouldBe true

        // But they should differ (sawtooth != sine)
        val defaultFirst = blocksDefault.first()
        val overrideFirst = blocksOverride.first()
        (defaultFirst.contentEquals(overrideFirst)) shouldBe false
    }

    "multiple custom ignitors are all registered" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)

        val customs = listOf(
            "oscA" to IgnitorDsl.Sine(),
            "oscB" to IgnitorDsl.Sawtooth(),
        )

        // Render oscA
        val blocksA = mutableListOf<ShortArray>()
        renderer.render(
            pattern = singleNotePattern("oscA"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = customs,
            onBlock = { samples, _ -> blocksA.add(samples.copyOf()) },
        )

        // Render oscB
        val blocksB = mutableListOf<ShortArray>()
        renderer.render(
            pattern = singleNotePattern("oscB"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = customs,
            onBlock = { samples, _ -> blocksB.add(samples.copyOf()) },
        )

        // Both produce audio
        blocksA.any { block -> block.any { it != 0.toShort() } } shouldBe true
        blocksB.any { block -> block.any { it != 0.toShort() } } shouldBe true

        // And they differ (sine vs sawtooth)
        (blocksA.first().contentEquals(blocksB.first())) shouldBe false
    }

    "custom ignitor with composed DSL renders correctly" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        // A more complex DSL: sine with lowpass filter
        val composedDsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            cutoffHz = IgnitorDsl.Constant(2000.0),
        )

        renderer.render(
            pattern = singleNotePattern("composedSound"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("composedSound" to composedDsl),
            onBlock = { samples, _ -> blocks.add(samples.copyOf()) },
        )

        blocks.any { block -> block.any { it != 0.toShort() } } shouldBe true
    }
})
