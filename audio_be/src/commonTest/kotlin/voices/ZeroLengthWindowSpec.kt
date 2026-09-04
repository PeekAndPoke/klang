/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Pins the emergent property the oscillator audit named O8: **a zero-length render window can only
 * ever be a voice's TERMINAL block.** It follows from three independent facts — `blockStart` is
 * integral (all drivers advance by whole blocks), `startFrame` is integral (`VoiceFactory` floors
 * it), and only `endFrame` is fractional — and it is what makes E5's deterministic 0.0 and every
 * lazy note-on init in `Ignitors.kt` safe.
 *
 * It is NOT a law of `Voice.render` itself: with a FRACTIONAL start the property fails (the third
 * test constructs the violation), so if the B1 startup redesign ever makes `startFrame` fractional,
 * the flooring pin below goes red — that is the tripwire, and the moment the six lazy-init latch
 * sites need re-auditing (ledger O8).
 */
class ZeroLengthWindowSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    "VoiceFactory floors startFrame to a whole sample — the tripwire (ledger O8)" {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate, sampleRateDouble = sampleRate.toDouble(), blockFrames = blockFrames,
            ignitorRegistry = registry, pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames), freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "z",
                data = VoiceData.empty.copy(freqHz = 220.0, sound = "sine"),
                startTime = 1000.6 / sampleRate,   // fractional on purpose
                gateEndTime = 5000.0 / sampleRate,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "z", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("null voice")
        voice.startFrame shouldBe 1000.0
    }

    /** Records every (elapsed, length) window the strip hands the exciter. */
    class RecordingIgnitor : Ignitor {
        val windows = mutableListOf<Pair<Int, Int>>()
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            windows.add(ctx.voiceElapsedFrames to ctx.length)
        }
    }

    fun renderAll(startFrame: Double, endFrame: Double): List<Pair<Int, Int>> {
        val rec = RecordingIgnitor()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = startFrame, endFrame = endFrame, gateEndFrame = endFrame,
            sampleRate = sampleRate, blockFrames = blockFrames, signal = rec,
        )
        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate, blockFrames = blockFrames, voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames), scratchBuffers = ScratchBuffers(blockFrames),
        )
        var b = 0.0
        while (b < 10_000) {
            rc.blockStart = b
            if (!voice.render(rc)) break
            b += blockFrames
        }
        return rec.windows
    }

    "with an INTEGRAL start, a zero-length window only ever appears as the LAST window" {
        for (endFrame in listOf(400.5, 437.25, 499.9, 500.75, 623.0001)) {
            val windows = renderAll(0.0, endFrame)
            windows.forEachIndexed { i, (_, len) ->
                if (len == 0) {
                    (i == windows.lastIndex) shouldBe true
                }
            }
        }
    }

    "with a FRACTIONAL start the property FAILS — why the flooring above is load-bearing" {
        // startFrame 399.5: the first block [300,400) renders a zero-length window, then rendering
        // continues — a zero-length FIRST block. Every lazy note-on init would latch there.
        val windows = renderAll(399.5, 475.0)
        val zeroAt = windows.indexOfFirst { it.second == 0 }
        (zeroAt >= 0 && zeroAt < windows.lastIndex) shouldBe true
    }
})
