/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.SourceLocationChain
import kotlin.math.abs

/**
 * The master chain must apply to **offline renders** too, not just live playback — otherwise a
 * recorded WAV would not match what the song sounds like.
 *
 * Since KLANG-00399 step 4 the offline renderer takes the live path: the chain goes over the wire
 * (`Cmd.RegisterKatalyst`) to the playback's engine, and the master bus builds it on request.
 */
class KlangOfflineRendererMasterTest : StringSpec({

    /**
     * A held note, optionally with a control-only `master(…)` carrier event at [masterAtCycles]. Like every pattern it
     * answers a query with the events that start in it: the renderer asks cycle by cycle, as a live playback does.
     */
    fun pattern(master: KatalystDsl?, masterAtCycles: Double = 0.0): KlangPattern = object : KlangPattern {
        override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
            allEvents(master, masterAtCycles).filter { it.startCycles >= fromCycles && it.startCycles < toCycles }

        private fun allEvents(master: KatalystDsl?, masterAtCycles: Double): List<KlangPatternEvent> {
            val note = object : KlangPatternEvent {
                override val startCycles = 0.0
                override val durationCycles = 4.0
                override val sourceLocations: SourceLocationChain? = null
                override fun toVoiceData() = VoiceData.empty.copy(
                    sound = "sine", freqHz = 440.0, gain = 0.1,
                )
            }

            if (master == null) {
                return listOf(note)
            }

            val carrier = object : KlangPatternEvent {
                override val startCycles = masterAtCycles
                override val durationCycles = 1.0
                override val sourceLocations: SourceLocationChain? = null
                override val master: KatalystValue = KatalystValue.Dsl(master)
                override fun toVoiceData() = VoiceData.empty.copy(
                    master = master.uniqueId(), control = true,
                )
            }

            return listOf(carrier, note)
        }
    }

    suspend fun renderPeak(master: KatalystDsl?): Double {
        var peak = 0.0

        KlangOfflineRenderer(sampleRate = 44100, blockFrames = 128).render(
            pattern = pattern(master),
            cycles = 4,
            cyclesPerSecond = 0.5,
            tailSec = 0.0,
        ) { out, frames ->
            for (i in 0 until frames) {
                val v = maxOf(abs(out.left[i]), abs(out.right[i]))
                if (v > peak) {
                    peak = v
                }
            }
        }

        return peak
    }

    /** The frames the render handed to `onBlock`, and the index of the first frame that is not silent. */
    suspend fun renderFrames(master: KatalystDsl?, masterAtCycles: Double): Pair<Int, Int> {
        var emitted = 0
        var firstSound = -1

        val result = KlangOfflineRenderer(sampleRate = 44100, blockFrames = 128).render(
            pattern = pattern(master = master, masterAtCycles = masterAtCycles),
            cycles = 4,
            cyclesPerSecond = 0.5,
            tailSec = 0.0,
        ) { out, frames ->
            for (i in 0 until frames) {
                if (firstSound < 0 && (out.left[i] != 0.0 || out.right[i] != 0.0)) {
                    firstSound = emitted + i
                }
            }

            emitted += frames
        }

        result.totalFrames shouldBe emitted

        return emitted to firstSound
    }

    // 4 cycles at 0.5 cps: 8 s of music at 44.1 kHz
    val musicFrames = 8 * 44100

    "an offline render begins at the music's first frame: the engine's latency at the head is trimmed (KLANG-00431 D11)" {
        // The note starts at frame 0. Before the trim the render began with the house limiter's 220 frames of silence
        // (at 44.1 kHz), more under a latent master; now its first frames are the note's attack.
        val (plainFrames, plainFirst) = renderFrames(master = null, masterAtCycles = 0.0)

        withClue("the plain render's first sound at frame $plainFirst") {
            (plainFirst in 0 until 8) shouldBe true
        }

        // The render is as long as the music (to the block): the head no longer adds the house latency's frames
        withClue("the plain render hands out $plainFrames frames") {
            (plainFrames >= musicFrames && plainFrames < musicFrames + 128) shouldBe true
        }

        // A master with a 10 ms lookahead from the first frame: its 441 frames are trimmed with the house's 220
        val (_, latentFirst) = renderFrames(master = KatalystDsl.of(KatalystStageDsl.Compressor(lookahead = 0.01)), masterAtCycles = 0.0)

        withClue("the latent master's render's first sound at frame $latentFirst") {
            (latentFirst in 0 until 8) shouldBe true
        }
    }

    "an offline render runs past its end by the master chain's latency too" {
        // A 10 ms lookahead delays the master bus by 441 frames at 44.1 kHz. The chain lands with the carrier event
        // halfway through the render, so the run-out must be read again while the render runs: what follows the
        // chain is 441 frames late, and the render hands out that much more than the music
        val (delayedFrames, _) = renderFrames(master = KatalystDsl.of(KatalystStageDsl.Compressor(lookahead = 0.01)), masterAtCycles = 2.0)

        withClue("the render with the master landing halfway hands out $delayedFrames frames") {
            (delayedFrames >= musicFrames + 441 && delayedFrames < musicFrames + 441 + 128) shouldBe true
        }
    }

    "an offline render applies the song's master chain" {
        val plain = renderPeak(master = null)
        val boosted = renderPeak(master = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(4.0))))

        plain shouldBeGreaterThan 0.0
        // Same note, same render — the only difference is the master, so the recording must be louder.
        (boosted / plain) shouldBeGreaterThan 3.0
    }
})
