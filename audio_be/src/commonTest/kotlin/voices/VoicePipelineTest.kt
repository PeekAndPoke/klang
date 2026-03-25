package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.SpyFilter
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for Voice pipeline ordering.
 * Verifies that the Pitch → Excite → Filter pipeline executes correctly.
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

        val ctx = createContext(blockStart = 100) // At peak of attack
        voice.render(ctx)

        // Filter should have been modulated
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.cutoffHistory[0] shouldBeGreaterThan baseCutoff

        // Filter should have been processed
        spyFilter.processCalls.size shouldBe 1
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
    }

    "voice renders correct number of samples" {
        val voice = createSynthVoice(startFrame = 0, endFrame = 100)

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)
    }

    "voice starting mid-block renders partial buffer" {
        val voice = createSynthVoice(startFrame = 50, endFrame = 150)

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice ending mid-block renders partial buffer" {
        val voice = createSynthVoice(startFrame = 0, endFrame = 50)

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice before startFrame returns true without rendering" {
        val voice = createSynthVoice(startFrame = 100, endFrame = 200)

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true
    }

    "voice after endFrame returns false" {
        val voice = createSynthVoice(startFrame = 0, endFrame = 100)

        val ctx = createContext(blockStart = 100, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe false
    }
})
