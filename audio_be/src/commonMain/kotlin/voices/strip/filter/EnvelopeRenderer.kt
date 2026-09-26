/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.adsrExpNorm
import io.peekandpoke.klang.audio_be.envDeclickCoeff
import io.peekandpoke.klang.audio_be.ignitor.finiteOr
import io.peekandpoke.klang.audio_be.voices.TeardownFadeRenderer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL

/**
 * ADSR amplitude envelope (VCA stage).
 * Multiplies the audio buffer by the envelope value per sample.
 *
 * The level is [EnvelopeCore]'s, the engine's one envelope law (per-stage curves, fractional attack and
 * decay frames, the release on `floor(N)` frames from the level at the gate frame). This host reads a
 * non-finite sustain as unset (`VOICE_ADSR_SUSTAIN_LEVEL`), floors the level at 0 (a negative gain would
 * invert the signal) and always runs the de-click ([EnvelopeDeclick]).
 *
 * All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
 * Voice-relative offsets are computed once at the block boundary.
 */
class EnvelopeRenderer(
    private val envelope: Voice.Envelope,
    // Absolute backend frame — Double, see RenderClock.cursorFrame. Relative offsets stay Int.
    private val startFrame: Double,
    // Per-engine VCA character (the PipelineDsl Vca stage). Defaults == the globals,
    // so the built-in engines render byte-for-byte as before.
    private val expK: Double = ADSR_EXP_K,
    private val declickSeconds: Double = ENV_DECLICK_SECONDS,
    /**
     * `false` = this voice's amplitude is not shaped here (the ignitor owns it). The stage still
     * RUNS: see [renderGate]. Resolved before construction, voice over pipeline over `true`.
     */
    private val on: Boolean = true,
) : BlockRenderer {

    // Exp-curve normalisation for this engine's curvature (precomputed once per voice).
    private val expNorm: Double = adsrExpNorm(expK)

    private val core = EnvelopeCore()

    override fun render(ctx: BlockContext) {
        if (!on) {
            renderGate(ctx)
            return
        }

        val env = envelope
        // One-pole de-click coefficient for the VCA gain (rounds segment-join corners).
        val declickCoeff = envDeclickCoeff(declickSeconds, ctx.sampleRateD)

        // Voice-relative gate end position (Int, avoids Long in the per-sample loop). Read from
        // the ctx PER RENDER CALL: a realtime note-off may move the gate between blocks
        // (Voice.releaseGate); never bake this at construction.
        val gateEndPos = (ctx.gateEndFrame - startFrame).toInt()

        core.prepare(
            attackFrames = env.attackFrames,
            decayFrames = env.decayFrames,
            // NaN-guard: a non-finite sustain reads as UNSET, the voice envelope's sustain.
            sustainLevel = finiteOr(env.sustainLevel, VOICE_ADSR_SUSTAIN_LEVEL),
            releaseFrames = env.releaseFrames,
            gateEndPos = gateEndPos,
            attackCurve = env.attackCurve,
            decayCurve = env.decayCurve,
            releaseCurve = env.releaseCurve,
            k = expK,
            norm = expNorm,
        )

        // Compute voice-relative position as Int (once per block, not per sample)
        var absPos = (ctx.blockStart + ctx.offset - startFrame).toInt()
        val declick = env.declick

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            // The amplitude floors at 0: a negative gain would invert the signal.
            val level = core.at(absPos)
            val gain = if (level < 0.0) 0.0 else level

            ctx.audioBuffer[idx] = (ctx.audioBuffer[idx] * declick.next(gain, declickCoeff))
            absPos++
        }
    }

    /**
     * The `on = false` path: a unity gate with a short linear fade to zero at teardown, the one law
     * in [TeardownFadeRenderer] (why it exists and its known limits live there).
     *
     * Deliberately NOT touching env.declick, the smoother state. The Envelope instance
     * is shared by every Vca stage in the pipeline (FilterPipelineBuilder hands the same one to
     * each), so priming it here would make a LATER ADSR Vca skip its own seeding and render the
     * note ONSET fading down from unity. A pipeline like
     * `vca().on(false) -> distort -> vca()` is reachable from KlangScript.
     */
    private fun renderGate(ctx: BlockContext) {
        TeardownFadeRenderer.render(ctx)
    }
}
