/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.NoOpAudioFilter
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors

/**
 * The engine end of the `on` chain (see `AdsrOnFlagSpec` for the value side).
 *
 * Two things are asserted here and both matter: that `on = false` leaves the signal unshaped, and
 * that it does so by rendering a GATE rather than by skipping the stage. The reason a gate is
 * rendered is the TEARDOWN FADE (`VcaOffTeardownSpec`) — skipping the stage would drop the voice on
 * whatever sample it happened to be at. There is no de-click smoother on this path; see
 * `EnvelopeRenderer.renderGate` for why one would break the exact-zero endpoint.
 *
 * See `docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md` Phase 3.
 */
class VcaOnFlagRenderSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 48000

    fun envelope() = Voice.Envelope(
        attackFrames = 4800.0,   // 100 ms: well past this block, so the ADSR path is mid-attack
        decayFrames = 100.0,
        sustainLevel = 0.7,
        releaseFrames = 100.0,
    )

    fun ctxWith(buf: AudioBuffer) = BlockContext(
        audioBuffer = buf,
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
        sampleRate = sampleRate,
        startFrame = 0.0,
        endFrame = 100_000.0,
        gateEndFrame = 50_000.0,
        freqHz = 440.0,
        // The VCA stage reads neither, but BlockContext requires both.
        signal = Ignitors.silence(),
        signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = 50_000,
            gateEndFrame = 50_000,
            releaseFrames = 100,
            scratchBuffers = ScratchBuffers(blockFrames),
        ),
        cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
    ).apply {
        updateOffsetAndLength(0, blockFrames)
    }

    /** Renders a constant-1.0 block through one EnvelopeRenderer and returns it. */
    fun renderDc(on: Boolean): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        for (i in 0 until blockFrames) buf[i] = 1.0
        EnvelopeRenderer(envelope(), startFrame = 0.0, on = on)
            .render(ctxWith(buf))
        return buf
    }

    // ── What the flag does to the signal ──────────────────────────────────────

    "on = false leaves the signal at unity" {
        val out = renderDc(on = false)
        // Unity from the very first sample: outside the teardown window the gate does not touch
        // the buffer at all, so there is nothing to fade in from.
        out[0] shouldBe (1.0 plusOrMinus 1e-12)
        out[blockFrames - 1] shouldBe (1.0 plusOrMinus 1e-12)
    }

    "on = true shapes the signal, so the flag is not inert" {
        // 100 ms attack means this whole block sits early in the attack ramp, far below unity.
        val out = renderDc(on = true)
        (out[blockFrames - 1] < 0.5) shouldBe true
    }

    "on = false holds unity regardless of leftover smoother state" {
        // renderGate deliberately carries NO de-click smoother: its target would be a constant 1.0,
        // and Voice.Envelope.of always starts unprimed (a steal or a live edit builds a NEW Voice),
        // so it provably never leaves unity — per-sample work for a known constant. This pins that
        // decision: even seeded away from unity, the gate does not ramp toward it.
        val env = envelope().apply {
            smoothedLevel = 0.0
            smoothPrimed = true
        }
        val buf = AudioBuffer(blockFrames)
        for (i in 0 until blockFrames) buf[i] = 1.0

        EnvelopeRenderer(env, startFrame = 0.0, on = false)
            .render(ctxWith(buf))

        buf[0] shouldBe (1.0 plusOrMinus 1e-12)
        buf[blockFrames - 1] shouldBe (1.0 plusOrMinus 1e-12)
    }

    "the teardown fade is what ends an on = false voice, not the smoother" {
        // The block that contains the voice end IS faded — the guard is live on this path.
        val buf = AudioBuffer(blockFrames)
        for (i in 0 until blockFrames) buf[i] = 1.0
        val ctx = ctxWith(buf).apply { blockStart = 100_000.0 - blockFrames }
        EnvelopeRenderer(envelope(), startFrame = 0.0, on = false)
            .render(ctx)

        buf[blockFrames - 1] shouldBe 0.0
    }

    // ── The resolution chain, voice over pipeline ─────────────────────────────

    fun pipelineWith(vcaOn: Boolean, voiceOn: Boolean?) = buildFilterPipeline(
        pipeline = PipelineDsl(listOf(StageDsl.Vca(on = vcaOn))),
        modulators = emptyList(),
        startFrame = 0.0,
        crush = Voice.Crush(amount = 0.0),
        coarse = Voice.Coarse(amount = 0.0),
        mainFilter = NoOpAudioFilter,
        envelope = envelope(),
        distort = Voice.Distort(amount = 0.0, shape = "soft"),
        tremolo = Voice.Tremolo(rate = 0.0, depth = 0.0, skew = 0.0, phase = 0.0, shape = null),
        phaser = Voice.Phaser(rate = 0.0, depth = 0.0, center = 1000.0, sweep = 1000.0),
        sampleRate = sampleRate,
        vcaOn = voiceOn,
    )

    fun lastSampleOf(vcaOn: Boolean, voiceOn: Boolean?): Double {
        val buf = AudioBuffer(blockFrames)
        for (i in 0 until blockFrames) buf[i] = 1.0
        val ctx = ctxWith(buf)
        pipelineWith(vcaOn, voiceOn).forEach { it.render(ctx) }
        return buf[blockFrames - 1]
    }

    "an unset voice flag falls through to the pipeline's Vca" {
        // The layer that would be dead if AdsrDef.Std.on defaulted to true instead of null.
        lastSampleOf(vcaOn = false, voiceOn = null) shouldBe (1.0 plusOrMinus 1e-12)
        (lastSampleOf(vcaOn = true, voiceOn = null) < 0.5) shouldBe true
    }

    "a gate stage does not poison a LATER envelope stage in the same pipeline" {
        // FilterPipelineBuilder hands ONE Voice.Envelope to every Vca stage. If renderGate primed
        // that shared smoother, a following ADSR stage would skip its own seeding and render the
        // note ONSET fading down from unity instead of following the attack. `vca(on=false) ->
        // vca()` is reachable from KlangScript, so renderGate deliberately writes no envelope state.
        val env = Voice.Envelope(
            attackFrames = 4800.0,   // 100 ms: the whole first block sits deep in the attack
            decayFrames = 100.0,
            sustainLevel = 0.7,
            releaseFrames = 100.0,
        )
        val buf = AudioBuffer(blockFrames)
        for (i in 0 until blockFrames) buf[i] = 1.0
        val ctx = ctxWith(buf)

        // Gate first, ADSR second — both sharing `env`.
        EnvelopeRenderer(env, startFrame = 0.0, on = false).render(ctx)
        EnvelopeRenderer(env, startFrame = 0.0, on = true).render(ctx)

        // Early in a 100 ms attack the envelope is far below unity. If the gate had primed the
        // smoother, the ADSR stage would start at 1.0 and lag down instead.
        (buf[0] < 0.1) shouldBe true
    }

    "the voice flag overrides the pipeline, in both directions" {
        lastSampleOf(vcaOn = true, voiceOn = false) shouldBe (1.0 plusOrMinus 1e-12)
        (lastSampleOf(vcaOn = false, voiceOn = true) < 0.5) shouldBe true
    }
})
