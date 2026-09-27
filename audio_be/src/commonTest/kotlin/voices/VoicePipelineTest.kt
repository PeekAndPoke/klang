/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for the voice's block windowing and lifecycle. (Its strip-stage ordering rows retired with the voice
 * strip in phase 3 step 9: the stages and their order are the instrument's tree now.)
 */
class VoicePipelineTest : StringSpec({

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
