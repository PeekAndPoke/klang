/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.random.Random

/**
 * The seam nothing else crosses: `VoiceFactory` -> `buildFilterPipeline(vcaOn = resolvedAdsr.on)`.
 *
 * `AdsrOnFlagSpec` stops at `AdsrDef`, `LangAdsrOnOffSpec` stops at the wire, and
 * `VcaOnFlagRenderSpec` starts at `buildFilterPipeline`. Delete `vcaOn = resolvedAdsr.on` at
 * `VoiceFactory.kt:583` and it defaults to `null`, falls through to `StageDsl.Vca.on` = `true`, and
 * `.adsrOff()` becomes a silent no-op in every song while every other spec stays green.
 *
 * It also renders a REAL [Voice] rather than driving `BlockContext` by hand, so the exact-zero
 * endpoint is asserted against the actual `Voice.render` framing rather than a hand-built one.
 *
 * What it does NOT pin, and cannot: `renderGate`'s endpoint arithmetic assumes `startFrame` and
 * `blockStart` are integral. That is enforced UPSTREAM — `VoiceFactory` floors `startFrame`
 * (`:88`), the worklet advances `cursorFrame` by whole blocks — so a spec cannot construct the
 * fractional case through this door at all. Verified by mutation: making `startFrame` fractional
 * here does not go red, because the flooring of `gateEndFrame` keeps `endFrame` integral anyway.
 * If sub-sample onsets ever arrive, the assumption stated in `renderGate` is what needs revisiting;
 * this spec will not catch it for you.
 */
class VoiceFactoryVcaOffSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** Builds a voice through the real VoiceFactory and renders it to its end. */
    fun renderVoice(on: Boolean?): DoubleArray {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val voiceBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val data = VoiceData.empty.copy(
            freqHz = 220.0,
            sound = "sawtooth",
            // release 0.05 (defaultSynth) so the voice has a real teardown window
            adsr = AdsrDef.Std(attack = 0.001, decay = 0.05, sustain = 1.0, release = 0.05, on = on),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "t", data = data,
                startTime = 0.0, gateEndTime = 0.1, playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "t", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val renderCtx = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val out = ArrayList<Double>()
        var block = 0.0
        while (block <= sampleRate) {
            voiceBuffer.fill(0.0)
            renderCtx.blockStart = block
            if (!voice.render(renderCtx)) break
            for (v in voiceBuffer) out.add(v)
            block += blockFrames
        }
        return out.toDoubleArray()
    }

    "the on flag reaches the render path at all" {
        // A sawtooth with attack 1 ms: with the VCA ON the first sample is deep in the attack ramp;
        // with it OFF the exciter is passed through unshaped. If `vcaOn` stops being threaded, both
        // sides render identically and this goes red.
        val withVca = renderVoice(on = true)
        val withoutVca = renderVoice(on = false)

        var differs = false
        for (i in 0 until minOf(withVca.size, withoutVca.size)) {
            if (abs(withVca[i] - withoutVca[i]) > 1e-9) { differs = true; break }
        }
        differs shouldBe true
    }

    "an on = false voice ends on an EXACTLY zero last rendered frame" {
        // The half of this spec that pins the integral-frame precondition. startTime 0.0 /
        // gateEndTime 0.1 / release 0.05 at 44100 gives gateEnd 4410 and endFrame 6615, so the last
        // frame Voice.render produces is 6614. Remove the floor() at VoiceFactory.kt:88 (or let
        // sub-sample onsets arrive) and `remaining` stops reaching 0 there — the teardown click
        // returns, and the hand-driven specs cannot see it because they set startFrame themselves.
        val out = renderVoice(on = false)
        val lastRendered = 6614

        // 44100 / 128-frame blocks / gate 4410 / release 0.05 -> endFrame 6615, so the render
        // loop produces 52 whole blocks. Pinned so a change to the loop shape is visible here.
        out.size shouldBe 6656
        (out.size > lastRendered) shouldBe true
        abs(out[lastRendered]) shouldBe 0.0
        // ...and we are genuinely inside the rendered span, not reading zero-padding: 100 frames
        // earlier the ramp is around half way, so the signal there is still live.
        (abs(out[lastRendered - 100]) > 0.0) shouldBe true
    }

    "an unset flag renders exactly like an explicit on" {
        // null must fall through to the pipeline's Vca(on = true), not to "off".
        val unset = renderVoice(on = null)
        val explicit = renderVoice(on = true)
        unset.size shouldBe explicit.size
        for (i in unset.indices) {
            abs(unset[i] - explicit[i]) shouldBe 0.0
        }
    }
})
