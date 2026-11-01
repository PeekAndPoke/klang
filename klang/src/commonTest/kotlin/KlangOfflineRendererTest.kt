/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.assertions.throwables.shouldThrow
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
     * True if two renders differ anywhere. Compares block-by-block via [DoubleArray.contentEquals],
     * each block being one block's left channel followed by its right (`out.left + out.right`).
     */
    fun rendersDiffer(a: List<DoubleArray>, b: List<DoubleArray>): Boolean =
        a.size != b.size || a.indices.any { !a[it].contentEquals(b[it]) }

    /**
     * Minimal pattern that produces a single event at a given sound name, at cycle 0. Like every pattern it answers a
     * query with the events that start in it: the renderer asks cycle by cycle, as a live playback does.
     */
    fun singleNotePattern(sound: String, freqHz: Double = 440.0): KlangPattern {
        return object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                if (!(fromCycles <= 0.0 && 0.0 < toCycles)) {
                    return emptyList()
                }

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

    "a render repeats bit for bit: a chord of random-seeded sounds, rendered twice" {
        // Three voices at once, each drawing random numbers (noise, unison phases): the playback id seeds their streams,
        // the order they are promoted in deals them (KLANG-00399, step 4)
        val chord = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                if (!(fromCycles <= 0.0 && 0.0 < toCycles)) {
                    return emptyList()
                }

                return listOf("white", "supersaw", "pink").map { sound ->
                    object : KlangPatternEvent {
                        override val startCycles = 0.0
                        override val durationCycles = 1.0
                        override val sourceLocations: SourceLocationChain? = null
                        override fun toVoiceData() = VoiceData.empty.copy(sound = sound, freqHz = 220.0, gain = 0.2)
                    }
                }
            }
        }

        suspend fun render(): List<DoubleArray> {
            val blocks = mutableListOf<DoubleArray>()

            KlangOfflineRenderer(sampleRate = 48_000).render(
                pattern = chord,
                cycles = 1,
                cyclesPerSecond = 2.0,
                tailSec = 0.1,
                onBlock = { out, _ -> blocks.add(out.left + out.right) },
            )

            return blocks
        }

        val first = render()

        first.any { block -> block.any { it != 0.0 } } shouldBe true
        rendersDiffer(first, render()) shouldBe false
    }

    "a pattern whose query fails makes the render fail, not spin" {
        val failsAtCycle3 = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                check(!(fromCycles <= 3.0 && 3.0 < toCycles)) { "cycle 3 does not query" }

                return emptyList()
            }
        }

        val error = shouldThrow<IllegalStateException> {
            KlangOfflineRenderer(sampleRate = 48_000).render(
                pattern = failsAtCycle3,
                cycles = 8,
                cyclesPerSecond = 1.0,
                tailSec = 0.0,
                onBlock = { _, _ -> },
            )
        }

        error.message shouldBe "cycle 3 does not query"
    }

    "a tempo under the 1 rpm floor renders at the floor, and its length follows" {
        val result = KlangOfflineRenderer(sampleRate = 8_000).render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 0.001,
            tailSec = 0.0,
            onBlock = { _, _ -> },
        )

        // One cycle at 1 rpm is a minute, not the 1000 s the asked tempo would give
        result.durationSec shouldBe 60.0
    }

    "render with default ignitors produces non-zero audio" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocks = mutableListOf<DoubleArray>()

        val result = renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { out, _ -> blocks.add(out.left + out.right) },
        )

        result.totalFrames shouldBeGreaterThan 0
        result.durationSec shouldBeGreaterThan 0.0
        blocks.any { block -> block.any { it != 0.0 } } shouldBe true
    }

    "render with empty customIgnitors still uses defaults" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocks = mutableListOf<DoubleArray>()

        renderer.render(
            pattern = singleNotePattern("triangle"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = emptyList(),
            onBlock = { out, _ -> blocks.add(out.left + out.right) },
        )

        blocks.any { block -> block.any { it != 0.0 } } shouldBe true
    }

    "render with custom ignitor produces non-zero audio" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocks = mutableListOf<DoubleArray>()

        val customDsl = IgnitorDsl.Sine()

        renderer.render(
            pattern = singleNotePattern("myCustomOsc"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("myCustomOsc" to customDsl),
            onBlock = { out, _ -> blocks.add(out.left + out.right) },
        )

        blocks.any { block -> block.any { it != 0.0 } } shouldBe true
    }

    "render with unknown sound produces silence" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocks = mutableListOf<DoubleArray>()

        renderer.render(
            pattern = singleNotePattern("nonExistentSound_xyz"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { out, _ -> blocks.add(out.left + out.right) },
        )

        blocks.all { block -> block.all { it == 0.0 } } shouldBe true
    }

    "custom ignitor overrides built-in default" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocksDefault = mutableListOf<DoubleArray>()
        val blocksOverride = mutableListOf<DoubleArray>()

        // Render with default sine
        renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { out, _ -> blocksDefault.add(out.left + out.right) },
        )

        // Render with custom "sine" that's actually a sawtooth
        renderer.render(
            pattern = singleNotePattern("sine"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("sine" to IgnitorDsl.Saw()),
            onBlock = { out, _ -> blocksOverride.add(out.left + out.right) },
        )

        // Both should produce non-zero audio
        blocksDefault.any { block -> block.any { it != 0.0 } } shouldBe true
        blocksOverride.any { block -> block.any { it != 0.0 } } shouldBe true

        // But they should differ (sawtooth != sine).
        // Compare the WHOLE render, not blocks.first(): at the canonical 128-frame block the first
        // block is still inside the master limiter's 5 ms lookahead delay, so it is silence in both.
        rendersDiffer(blocksDefault, blocksOverride) shouldBe true
    }

    "multiple custom ignitors are all registered" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)

        val customs = listOf(
            "oscA" to IgnitorDsl.Sine(),
            "oscB" to IgnitorDsl.Saw(),
        )

        // Render oscA
        val blocksA = mutableListOf<DoubleArray>()
        renderer.render(
            pattern = singleNotePattern("oscA"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = customs,
            onBlock = { out, _ -> blocksA.add(out.left + out.right) },
        )

        // Render oscB
        val blocksB = mutableListOf<DoubleArray>()
        renderer.render(
            pattern = singleNotePattern("oscB"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = customs,
            onBlock = { out, _ -> blocksB.add(out.left + out.right) },
        )

        // Both produce audio
        blocksA.any { block -> block.any { it != 0.0 } } shouldBe true
        blocksB.any { block -> block.any { it != 0.0 } } shouldBe true

        // And they differ (sine vs sawtooth) — whole render, see the note above.
        rendersDiffer(blocksA, blocksB) shouldBe true
    }

    "custom ignitor with composed DSL renders correctly" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000)
        val blocks = mutableListOf<DoubleArray>()

        // A more complex DSL: sine with lowpass filter
        val composedDsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            freq = IgnitorDsl.Constant(2000.0),
        )

        renderer.render(
            pattern = singleNotePattern("composedSound"),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            customIgnitors = listOf("composedSound" to composedDsl),
            onBlock = { out, _ -> blocks.add(out.left + out.right) },
        )

        blocks.any { block -> block.any { it != 0.0 } } shouldBe true
    }
})
