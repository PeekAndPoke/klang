/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceLimits
import io.peekandpoke.klang.audio_be.voices.strip.pitch.FmRenderer

/**
 * Block-framing **P4**, the control-rate half: a voice that starts MID-BLOCK must read its
 * control-rate envelopes at its own onset, not at the block's first frame.
 *
 * The voice's stages derive their position as `ctx.blockStart + ctx.offset`. [FmRenderer] does not:
 * it hands `controlRatePos` the raw `ctx.blockStart` (so did the voice strip's filter modulator until the
 * strip retired in phase 3 step 9). That is **correct, but only by construction elsewhere**, and the
 * coupling is invisible from either end:
 *
 * - `Voice.render` derives `offset = maxOf(blockStart, startFrame) - blockStart`
 * - `controlRatePos` (`EnvelopeCalc`) is `maxOf(blockStart, startFrame) - startFrame`
 *
 * which are the same expression, so the callee's clamp *is* the caller's offset compensation.
 *
 * **This also settles the open question in audit finding F3** — *"the clamp may be genuinely
 * redundant with the trailing `coerceIn`"*. It is not. The two disagree whenever `attackFrames == 0`,
 * because a negative `absPos` then satisfies `absPos < attackFrames` and takes the **attack** branch
 * (coercing to 0.0), while the clamped `absPos = 0` falls through to **sustain**. Zero attack is the
 * common case for a filter or FM envelope, and F3's mutation survived the whole 1373-test suite, so
 * nothing anywhere held this. This row does.
 */
class MidBlockOnsetControlRateSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // The voice starts half way into the block. `Voice.render` would compute exactly this pair, so
    // the fixture reproduces the framing rather than inventing one.
    val startFrame = 64.0
    val offset = 64
    val length = 64

    fun ctx(): BlockContext = BlockContext(
        audioBuffer = AudioBuffer(blockFrames),
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
        sampleRate = sampleRate,
        limits = VoiceLimits(startFrame = startFrame, gateEndFrame = 100_000.0, endFrame = 100_000.0),
    ).apply {
        updateOffsetAndLength(offset, length)
        blockStart = 0.0
    }

    // Zero attack, full sustain: the envelope is 1.0 from the voice's very first frame.
    fun flatEnvelope() = Voice.Envelope(
        attackFrames = 0.0,
        decayFrames = 0.0,
        sustainLevel = 1.0,
        releaseFrames = 0.0,
    )

    "FmRenderer reads its depth envelope at the voice's onset, not the block's first frame" {
        val fm = Voice.Fm(ratio = 1.0, depth = 50.0, envelope = flatEnvelope())
        val c = ctx()

        FmRenderer(fm = fm, freqHz = 440.0, sampleRate = sampleRate).render(c)

        // Sustained depth modulates the pitch multiplier away from 1.0 across the rendered window.
        // At blockStart the envelope reads 0.0, effectiveDepth collapses to 0, and every multiplier
        // in the window stays exactly 1.0.
        (offset until offset + length).any { c.freqModBuffer[it] != 1.0 } shouldBe true
    }
})
