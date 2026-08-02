/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.SourceLocationChain
import kotlin.math.abs

/**
 * The master chain must apply to **offline renders** too, not just live playback — otherwise a
 * recorded WAV would not match what the song sounds like.
 *
 * The offline path differs from live in one way that matters: it registers masters directly on the
 * renderer's *parent* registry rather than sending `Cmd.RegisterMaster`, so the per-engine fork
 * resolves them through its parent and builds the chain lazily.
 */
class KlangOfflineRendererMasterTest : StringSpec({

    /** A held note, optionally preceded by a control-only `master(…)` carrier event. */
    fun pattern(master: MasterDsl?): KlangPattern = object : KlangPattern {
        override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
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
                override val startCycles = 0.0
                override val durationCycles = 1.0
                override val sourceLocations: SourceLocationChain? = null
                override val master: MasterValue = MasterValue.Dsl(master)
                override fun toVoiceData() = VoiceData.empty.copy(
                    master = master.uniqueId(), control = true,
                )
            }

            return listOf(carrier, note)
        }
    }

    suspend fun renderPeak(master: MasterDsl?): Double {
        var peak = 0.0

        KlangOfflineRenderer(sampleRate = 44100, blockFrames = 128).render(
            pattern = pattern(master),
            cycles = 4,
            cyclesPerSecond = 0.5,
            tailSec = 0.0,
        ) { samples, count ->
            for (i in 0 until count) {
                val v = abs(samples[i].toDouble() / Short.MAX_VALUE)
                if (v > peak) {
                    peak = v
                }
            }
        }

        return peak
    }

    "an offline render applies the song's master chain" {
        val plain = renderPeak(master = null)
        val boosted = renderPeak(master = MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)))

        plain shouldBeGreaterThan 0.0
        // Same note, same render — the only difference is the master, so the recording must be louder.
        (boosted / plain) shouldBeGreaterThan 3.0
    }
})
