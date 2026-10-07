/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
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
 * The offline path differs from live in one way that matters: it registers chains directly on the
 * renderer's *parent* Katalyst registry rather than sending `Cmd.RegisterKatalyst`, so the
 * per-engine fork resolves them through its parent and the master bus builds the chain on request.
 */
class KlangOfflineRendererMasterTest : StringSpec({

    /** A held note, optionally preceded by a control-only `master(…)` carrier event. */
    fun pattern(master: KatalystDsl?): KlangPattern = object : KlangPattern {
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

    "an offline render applies the song's master chain" {
        val plain = renderPeak(master = null)
        val boosted = renderPeak(master = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(4.0))))

        plain shouldBeGreaterThan 0.0
        // Same note, same render — the only difference is the master, so the recording must be louder.
        (boosted / plain) shouldBeGreaterThan 3.0
    }
})
