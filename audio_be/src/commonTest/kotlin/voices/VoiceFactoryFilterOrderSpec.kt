/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.ChainAudioFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Guards the contract that [VoiceFactory] bakes the filter chain in the EXACT order it
 * receives from [VoiceData.filters] — it must NOT reorder. The chain-order decision
 * (highpass-first / lowpass-last) is owned by the language layer (SprudelVoiceData), so
 * the engine stays a faithful, order-preserving consumer. If anyone re-adds a sort inside
 * VoiceFactory, these tests fail.
 */
class VoiceFactoryFilterOrderSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // Builds a voice through VoiceFactory and returns the baked main-filter chain, in order.
    fun bakedChainOf(filters: List<FilterDef>): List<AudioFilter> {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val data = VoiceData.empty.copy(
            freqHz = 440.0,
            sound = "triangle",
            filters = FilterDefs(filters),
        )
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = data,
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )

        val voice = factory.makeVoice(
            scheduled = scheduled,
            nowFrame = 0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val chain = voice.mainFilter.shouldBeInstanceOf<ChainAudioFilter>()
        return chain.filters
    }

    "VoiceFactory bakes the chain in the exact order received — it does NOT reorder" {
        // Deliberately NON-canonical order (LowPass first). VoiceFactory must keep it as-is;
        // the canonical highpass-first/lowpass-last sort lives upstream in SprudelVoiceData.
        val chain = bakedChainOf(
            listOf(
                FilterDef.LowPass(cutoffHz = 1000.0, q = 1.0),
                FilterDef.HighPass(cutoffHz = 200.0, q = 1.0),
                FilterDef.BandPass(cutoffHz = 600.0, q = 1.0),
            )
        )

        chain.size shouldBe 3
        chain[0].shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
        chain[1].shouldBeInstanceOf<LowPassHighPassFilters.SvfHPF>()
        chain[2].shouldBeInstanceOf<LowPassHighPassFilters.SvfBPF>()
    }

    "VoiceFactory preserves an already-canonical order too" {
        val chain = bakedChainOf(
            listOf(
                FilterDef.HighPass(cutoffHz = 200.0, q = 1.0),
                FilterDef.LowPass(cutoffHz = 1000.0, q = 1.0),
            )
        )

        chain.size shouldBe 2
        chain[0].shouldBeInstanceOf<LowPassHighPassFilters.SvfHPF>()
        chain[1].shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
    }
})
