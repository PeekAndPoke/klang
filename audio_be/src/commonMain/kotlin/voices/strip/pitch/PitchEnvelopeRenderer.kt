/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.ignitor.renderPitchEnvelopeRatios
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.prepareControlRateEnvelope

/**
 * The voice strip's pitch envelope (sprudel's `penv`): an ADSR on the pitch ratio, a thin host of
 * [EnvelopeCore], the engine's one envelope law (phase 3 step 5b (c1), decision D3).
 *
 * The level rises from 0 to 1 over the attack, falls to the sustain over the decay, holds, and from the
 * gate's end falls back to 0 over the release; the pitch is `2^(semitones * level / 12)`. The release
 * does NOT extend the voice's life. The level becomes a ratio in `renderPitchEnvelopeRatios`, the one
 * mapping this renderer shares with the Ignitor pitch envelope, so the same stages, sustain and curves
 * render the same numbers on both hosts.
 *
 * Per sample, from the voice-relative frame `blockStart + offset - startFrame` (block-framing Class 1).
 * The gate is read from the [BlockContext] on every call, as the strip's filter and FM envelopes read
 * it, so a realtime note-off moves the release. The first pitch stage of a block WRITES the frequency
 * modulation buffer, a later one multiplies into it.
 */
class PitchEnvelopeRenderer(
    private val pitchEnvelope: Voice.PitchEnvelope,
    // Absolute backend frame, Double, see RenderClock.cursorFrame.
    private val startFrame: Double,
) : BlockRenderer {
    private val core = EnvelopeCore()

    override fun render(ctx: BlockContext) {
        core.prepareControlRateEnvelope(pitchEnvelope.envelope, startFrame, ctx.gateEndFrame)

        // Voice-relative position of the block's first rendered frame, Int (no Long on Kotlin/JS).
        val firstPos = (ctx.blockStart + ctx.offset - startFrame).toInt()
        val from = ctx.offset
        val to = ctx.offset + ctx.length

        renderPitchEnvelopeRatios(
            core = core,
            amount = pitchEnvelope.semitones,
            buffer = ctx.freqModBuffer,
            from = from,
            to = to,
            firstPos = firstPos,
            multiply = ctx.freqModBufferWritten,
        )

        ctx.freqModBufferWritten = true
    }
}
