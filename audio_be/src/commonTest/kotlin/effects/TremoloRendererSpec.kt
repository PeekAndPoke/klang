/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.strip.filter.TremoloRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace

/** The strip half of ledger W2: the tremolo phase wrap survives hostile rates. */
class TremoloRendererSpec : StringSpec({

    "a non-finite rate no longer NaN-poisons the voice — wrapPhase scrubs the phase to 0" {
        // Old code: phase += NaN once -> sin(NaN) -> NaN output for the voice's life. New:
        // the phase reads 0 every sample -> a steady gain of 1 - depth/2 (a level change, not
        // silence, not NaN — the reading recorded in the ledger).
        val renderer = TremoloRenderer(rate = Double.NaN, depth = 0.5, sampleRate = 44100)
        val buffer = AudioBuffer(256) { 0.8 }
        renderer.renderInPlace(buffer)

        buffer.all { it == 0.8 * 0.75 } shouldBe true
    }

    "a plain rate modulates: the wrap change is inert in range" {
        // 8820 samples = 1.6 LFO cycles at 8 Hz, so the gain genuinely sweeps its full
        // [1 - depth, 1] range (review round 1: a fifth of a cycle proved nothing).
        val renderer = TremoloRenderer(rate = 8.0, depth = 0.33, sampleRate = 44100)
        val buffer = AudioBuffer(8820) { 0.8 }
        renderer.renderInPlace(buffer)

        (buffer.max() > 0.79) shouldBe true
        (buffer.min() < 0.8 * (1.0 - 0.33) + 0.01) shouldBe true
        buffer.all { it.isFinite() } shouldBe true
    }
})
