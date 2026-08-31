/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.SpyFilter
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for Voice pipeline ordering.
 * Verifies that the Pitch → Ignite → Filter pipeline executes correctly.
 */
class VoicePipelineTest : StringSpec({

    "pipeline executes main filter" {
        val spyMainFilter = SpyFilter("mainFilter")

        val voice = createSynthVoice(filter = spyMainFilter)

        val ctx = createContext()
        voice.render(ctx)

        // Main filter should have been called
        spyMainFilter.processCalls.size shouldBe 1
    }

    "pipeline with crush renders successfully" {
        val voice = createSynthVoice(crush = Voice.Crush(4.0))

        val ctx = createContext()
        val result = voice.render(ctx)

        result shouldBe true
    }

    "pipeline with distortion renders successfully" {
        val voice = createSynthVoice(distort = Voice.Distort(0.5))

        val ctx = createContext()
        val result = voice.render(ctx)

        result shouldBe true
    }

    "pipeline with multiple pre-filters renders successfully" {
        val voice = createSynthVoice(
            crush = Voice.Crush(4.0),
            coarse = Voice.Coarse(2.0),
        )

        val ctx = createContext()
        val result = voice.render(ctx)

        result shouldBe true
    }

    "tremolo renders successfully" {
        val voice = createSynthVoice(
            tremolo = Voice.Tremolo(
                rate = 5.0, depth = 0.5, skew = 0.0, phase = 0.0, shape = null,
            )
        )

        val ctx = createContext()
        voice.render(ctx)
        // Second render to verify state continuity
        voice.render(ctx)
    }

    "phaser renders successfully with defaults" {
        val voice = createSynthVoice(
            phaser = Voice.Phaser(
                rate = 1.0, depth = 0.5,
                center = 0.0,  // Should default to 1000.0
                sweep = 0.0,   // Should default to 1000.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)
        voice.render(ctx)
    }

    "voice with no effects renders successfully" {
        val voice = createSynthVoice(
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            distort = Voice.Distort(0.0),
        )

        val ctx = createContext()
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice with all effects enabled renders successfully" {
        val voice = createSynthVoice(
            crush = Voice.Crush(4.0),
            coarse = Voice.Coarse(2.0),
            distort = Voice.Distort(0.5),
            tremolo = Voice.Tremolo(
                rate = 5.0, depth = 0.5, skew = 0.0, phase = 0.0, shape = null,
            ),
            phaser = Voice.Phaser(
                rate = 1.0, depth = 0.5, center = 1000.0, sweep = 1000.0,
            ),
        )

        val ctx = createContext()
        val result = voice.render(ctx)

        result shouldBe true
    }

    "filter modulation updates cutoff before filter processes" {
        val spyFilter = VoiceTestHelpers.TunableSpyFilter("modulated")
        val baseCutoff = 1000.0

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0, decayFrames = 0.0,
                sustainLevel = 1.0, releaseFrames = 0.0,
            ),
            depth = 1.0,
            baseCutoff = baseCutoff,
        )

        val voice = createSynthVoice(
            filter = spyFilter,
            filterModulators = listOf(modulator),
        )

        val ctx = createContext(blockStart = 100.0) // At peak of attack
        voice.render(ctx)

        // Filter should have been modulated
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.cutoffHistory[0] shouldBeGreaterThan baseCutoff

        // Filter should have been processed
        spyFilter.processCalls.size shouldBe 1

        // The "before" in the name (audit finding F13). Two independent counters compared after the
        // fact cannot distinguish the orders — both are 1 either way. This records how many
        // setCutoff calls had landed WHEN process began, so it can only be 1 if modulation ran first.
        spyFilter.cutoffCountAtProcess shouldBe listOf(1)
    }

    "envelope is applied after main filter" {
        val spyMainFilter = SpyFilter("mainFilter")

        val voice = createSynthVoice(
            filter = spyMainFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0, decayFrames = 0.0,
                sustainLevel = 1.0, releaseFrames = 0.0,
            ),
        )

        val ctx = createContext()
        voice.render(ctx)

        spyMainFilter.processCalls.size shouldBe 1

        // The ordering claim itself (audit finding F13): the assertion above is identical to the one
        // in "pipeline executes main filter" and says nothing about ORDER. A 100-frame attack means
        // the VCA's gain at frame 0 is ~0, so what the filter was HANDED settles the sequence — the
        // raw exciter (1.0) if the filter runs first, a near-silent signal if the VCA already ran.
        spyMainFilter.seenAtProcess[0] shouldBe (1.0 plusOrMinus 1e-9)

        // ...and the envelope did then apply, so this is not just a filter running on a dry chain.
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.05)
    }

    "voice renders correct number of samples" {
        // Audit finding F15(a): this named a countable property and counted nothing — it rendered
        // and asserted absolutely zero. A voice spanning exactly one block must fill exactly that
        // block, so the sentinel makes "how many samples" literally countable.
        val voice = createSynthVoice(startFrame = 0.0, endFrame = 100.0)

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)
        val sentinel = 7.0
        ctx.voiceBuffer.fill(sentinel)

        voice.render(ctx)

        ctx.voiceBuffer.count { it != sentinel } shouldBe 100
    }

    "voice starting mid-block renders partial buffer" {
        val voice = createSynthVoice(startFrame = 50.0, endFrame = 150.0)

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)

        // A SENTINEL, not the default zeros. "Partial buffer" means the voice writes only
        // [offset, offset+length) and leaves the rest alone — and against a pre-zeroed buffer that
        // is indistinguishable from a voice that writes silence across the whole block. Checking
        // for zeros first looked right and was toothless: a mutant that ignored the onset entirely
        // (`vStart = ctx.blockStart`) still produced zeros there, because the ENVELOPE floors a
        // negative position, so the zeros were never evidence of windowing.
        val sentinel = 7.0
        ctx.voiceBuffer.fill(sentinel)

        val result = voice.render(ctx)

        result shouldBe true

        // Untouched before the onset...
        (0 until 50).all { ctx.voiceBuffer[it] == sentinel } shouldBe true
        // ...and written from it (audit finding F13: only the lifecycle boolean was ever checked
        // here, while the identically-named rows in VoiceLifecycleTest DO inspect content — the two
        // specs disagreed about what the name meant).
        (50 until 100).all { ctx.voiceBuffer[it] != sentinel } shouldBe true
    }

    "voice ending mid-block renders partial buffer" {
        val voice = createSynthVoice(startFrame = 0.0, endFrame = 50.0)

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice before startFrame returns true without rendering" {
        val voice = createSynthVoice(startFrame = 100.0, endFrame = 200.0)

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice after endFrame returns false" {
        val voice = createSynthVoice(startFrame = 0.0, endFrame = 100.0)

        val ctx = createContext(blockStart = 100.0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe false
    }
})
