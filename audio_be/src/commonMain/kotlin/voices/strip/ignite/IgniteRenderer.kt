/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.ignite

import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Wraps a [Ignitor] as a [BlockRenderer].
 *
 * Reads pitch modulation from [BlockContext.freqModBuffer] (if [BlockContext.freqModBufferWritten])
 * and generates the raw waveform into [BlockContext.audioBuffer].
 *
 * Output bounding is the responsibility of the per-stage soft cap inside
 * `Ignitor.distort()` and `Ignitor.shape()` — see those for the C¹ piecewise
 * saturator that bounds each stage's output to ±1. This wrapper does not
 * apply any additional clipping; the stages after it (the teardown fade,
 * the send) get the ignitor output as-produced.
 */
class IgniteRenderer(
    private val signal: Ignitor,
    private val signalCtx: IgniteContext,
    private val freqHz: Double,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        // The voice's limits, read per block (absolute frames, Double, see RenderClock.cursorFrame).
        val startFrame = ctx.limits.startFrame

        signalCtx.updateOffsetAndLength(ctx.offset, ctx.length)
        // The voice-relative gate the ignitors read, derived here and only here from the one home, so a
        // realtime note-off (it moves `VoiceLimits.gateEndFrame`) reaches every ignitor envelope at the
        // next block ("amendment A1").
        signalCtx.gateEndFrame = (ctx.limits.gateEndFrame - startFrame).toInt()
        // + ctx.offset: voiceElapsedFrames is the elapsed count AT buffer index ctx.offset, which is
        // where every consumer starts counting (AdsrIgnitor seeds absPos from it and loops from
        // ctx.offset; IgnitorFilters adds sampleOffsetWithinBlock; PitchModFactories uses i - offset).
        // Without it, a voice whose first block starts mid-block gets a NEGATIVE clock: the exp
        // shape goes negative, clamps to 0, and the note's first `offset` samples render silent and
        // then step. Measured at 44.1k/128 with a 10 ms attack and startFrame 76: 52 silent frames
        // then a jump to 0.0222 in one sample. offset is non-zero ONLY on a voice's first block, so
        // this term changes nothing anywhere else. Matches PitchEnvelopeRenderer's `firstPos`.
        signalCtx.voiceElapsedFrames = (ctx.blockStart + ctx.offset - startFrame).toInt()
        signalCtx.phaseMod = if (ctx.freqModBufferWritten) ctx.freqModBuffer else null

        signal.generate(ctx.audioBuffer, freqHz, signalCtx)
    }
}
