package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.common.SourceLocationChain
import kotlinx.coroutines.runBlocking

class KlangOfflineRendererSampleTest : StringSpec({

    val samples = runBlocking { Samples.create(catalogue = SampleCatalogue.default) }

    /**
     * Creates a pattern that plays one sample sound per cycle, cycling through the given sounds.
     * e.g. sounds = ["bd", "hh", "sd", "cp"] → bd in cycle 0, hh in cycle 1, sd in cycle 2, cp in cycle 3
     */
    fun alternatingPattern(sounds: List<String>): KlangPattern {
        return object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                val events = mutableListOf<KlangPatternEvent>()
                val fromInt = kotlin.math.floor(fromCycles).toInt().coerceAtLeast(0)
                val toInt = kotlin.math.ceil(toCycles).toInt()

                for (cycle in fromInt until toInt) {
                    val sound = sounds[cycle % sounds.size]
                    val startCycles = cycle.toDouble()
                    if (startCycles < fromCycles || startCycles >= toCycles) continue

                    events.add(object : KlangPatternEvent {
                        override val startCycles = startCycles
                        override val durationCycles = 0.5
                        override val sourceLocations: SourceLocationChain? = null
                        override fun toVoiceData() = VoiceData.empty.copy(sound = sound)
                    })
                }
                return events
            }
        }
    }

    "render with sample sounds produces non-zero audio" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        renderer.render(
            pattern = alternatingPattern(listOf("bd", "sd", "hh", "oh")),
            cycles = 2,
            cyclesPerSecond = 1.0,
            tailSec = 0.5,
            samples = samples,
            onBlock = { s, _ -> blocks.add(s.copyOf()) },
        )

        blocks.any { block -> block.any { it != 0.toShort() } } shouldBe true
    }

    "render with alternating samples loads a new sample every cycle" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        renderer.render(
            pattern = alternatingPattern(listOf("bd", "hh", "sd", "cp")),
            cycles = 4,
            cyclesPerSecond = 1.0,
            tailSec = 0.5,
            samples = samples,
            onBlock = { s, _ -> blocks.add(s.copyOf()) },
        )

        // Each cycle should produce audio (4 different samples across 4 cycles)
        val blocksPerCycle = 48_000 / 512

        for (cycle in 0 until 4) {
            val cycleStart = cycle * blocksPerCycle
            val cycleEnd = minOf(cycleStart + blocksPerCycle, blocks.size)
            val cycleBlocks = blocks.subList(cycleStart, cycleEnd)

            val hasAudio = cycleBlocks.any { block -> block.any { it != 0.toShort() } }
            hasAudio shouldBe true
        }
    }

    "render without samples parameter produces silence for sample sounds" {
        val renderer = KlangOfflineRenderer(sampleRate = 48_000, blockFrames = 512)
        val blocks = mutableListOf<ShortArray>()

        renderer.render(
            pattern = alternatingPattern(listOf("bd", "sd")),
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { s, _ -> blocks.add(s.copyOf()) },
        )

        blocks.all { block -> block.all { it == 0.toShort() } } shouldBe true
    }
})
