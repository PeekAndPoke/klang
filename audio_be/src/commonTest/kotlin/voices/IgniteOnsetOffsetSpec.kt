/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * A note must sound the same wherever it lands inside a render block.
 *
 * `IgniteRenderer` sets `IgniteContext.voiceElapsedFrames`, which every ignitor-side envelope reads
 * as the elapsed count AT buffer index `ctx.offset` (`AdsrIgnitor` seeds `absPos` from it and loops
 * from `ctx.offset`; `IgnitorFilters` adds `sampleOffsetWithinBlock`; `PitchModFactories` uses
 * `i - ctx.offset`). It used to be computed at index 0 instead, i.e. short by `offset`.
 *
 * `offset` is non-zero only on a voice's FIRST block, so the clock went NEGATIVE there: the exp
 * shape returned a negative value, clamped to zero, and the note's first `offset` samples rendered
 * silent and then stepped. Measured before the fix at 44.1 kHz / 128-frame blocks with a 10 ms
 * attack and `startFrame = 76`: 52 silent frames, then a jump from 0 to 0.0222 in one sample —
 * larger than the teardown step `VCA_OFF_TEARDOWN_FADE_SECONDS` exists to remove.
 *
 * It hid because the strip VCA was simultaneously in its own correctly-timed, de-clicked attack and
 * attenuated the step by 10-20x. `.adsrOff()` replaces that with unity and exposes it at full size,
 * which is how it surfaced. Present since f36b9740.
 */
class IgniteOnsetOffsetSpec : StringSpec({
    val sampleRate = 44100
    val blockFrames = 128

    // A DC source with a 10 ms attack, so the rendered output IS the ignitor's envelope.
    val instrument = IgnitorDsl.Adsr(
        inner = IgnitorDsl.Constant(1.0),
        attackSec = IgnitorDsl.Constant(0.010),
        decaySec = IgnitorDsl.Constant(2.0),
        sustainLevel = IgnitorDsl.Constant(1.0),
        releaseSec = IgnitorDsl.Constant(0.05),
    )

    fun render(startFrameWanted: Int): DoubleArray {
        val registry = IgnitorRegistry().apply { registerDefaults(); register("probe", instrument) }
        val voiceBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate, sampleRateDouble = sampleRate.toDouble(), blockFrames = blockFrames,
            ignitorRegistry = registry, pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = voiceBuffer, freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val startTime = startFrameWanted.toDouble() / sampleRate
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "p",
                data = VoiceData.empty.copy(
                    freqHz = 220.0, sound = "probe",
                    // VCA off, so what we see is the INSTRUMENT's envelope alone
                    adsr = AdsrDef.Std(on = false),
                ),
                startTime = startTime, gateEndTime = startTime + 0.2, playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "p", ignitorRegistry = registry, phasePools = PhasePools(kotlin.random.Random(1))),
            getSample = { null },
        ) ?: error("null voice")

        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate, blockFrames = blockFrames, voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames), scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = ArrayList<Double>()
        var b = 0.0
        while (b < 2000) {
            voiceBuffer.fill(0.0); rc.blockStart = b
            if (!voice.render(rc)) break
            for (v in voiceBuffer) out.add(v)
            b += blockFrames
        }
        return out.toDoubleArray()
    }

    "the attack is identical wherever the note lands in a block" {
        // The rendered output IS the instrument's envelope (DC source, VCA off), so this compares
        // the attack curve itself. Before the fix the mid-block cases were all zero here.
        val aligned = render(0)
        for (start in listOf(1, 37, 76, 127)) {
            val shifted = render(start)
            for (i in 0 until 400) {
                withClue("startFrame=$start, frame $i of the note") {
                    shifted[start + i] shouldBe (aligned[i] plusOrMinus 1e-12)
                }
            }
        }
    }

    "a mid-block onset does not start with silence" {
        // The specific symptom: `offset` frames of nothing, then a step. With a 10 ms attack the
        // envelope is already climbing by the second sample.
        val out = render(76)
        (out[76 + 1] > 0.0) shouldBe true
        (out[76 + 40] > 0.0) shouldBe true
    }

    "there is no step at the first block seam" {
        // The step was 0 -> 0.0222 in one sample at index 128. Neighbouring samples of a 10 ms
        // attack differ by well under 0.001.
        val out = render(76)
        val jump = kotlin.math.abs(out[128] - out[127])
        (jump < 0.001) shouldBe true
    }
})
