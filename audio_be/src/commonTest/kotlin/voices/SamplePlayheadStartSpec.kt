/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Where a sample voice's playhead STARTS — the two-line root cause of the soundfont looping bug
 * (`docs/tasks/soundfont-looping-investigation.md`).
 *
 * `VoiceFactory` used to start a looped sample AT `loopStart`, skipping the attack `[0, loopStart)`
 * entirely: the FluidR3 violin lost 1.27 s of bow onset and looped a 180 ms slice of steady state,
 * which does loop and does not sound like a violin. And it started a non-looped sample at
 * `meta.anchor`, which is not a start offset — measured against the decoded audio it is the position
 * of the loudest sample, a normalisation artefact of the converter — so the nylon guitar lost its
 * pluck. Both SoundFont 2 and WebAudioFont start at frame 0, play THROUGH the attack, and loop only
 * once the playhead reaches `loopStart`.
 *
 * A **ramp** PCM makes the output value literally the playhead position, so "started at the wrong
 * frame" shows up as the wrong number rather than as more of the same one. The VCA is off so the
 * buffer carries raw sample values, and `pitchHz == freqHz` with matching sample rates gives a
 * playback rate of exactly 1.0: output frame k must read `pcm[k]`.
 */
class SamplePlayheadStartSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val pcmSize = 4410 // 100 ms
    val loopStartFrame = 2205 // 50 ms in — a long attack, like a real bowed or blown zone
    val loopEndFrame = 3307 // 75 ms

    fun ramp() = AudioBuffer(pcmSize) { it.toDouble() / (pcmSize - 1) }

    /** Renders [blocks] blocks of a sample voice built through the real [VoiceFactory]. */
    fun render(meta: SampleMetadata, begin: Double? = null, blocks: Int = 30): DoubleArray {
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = ramp(), meta = meta)
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
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "playhead",
                data = VoiceData.empty.copy(
                    freqHz = 220.0,
                    sound = "playheadprobe", // unregistered => the sample branch
                    adsr = AdsrDef.Std(release = 0.01, on = false), // VCA off: raw sample values
                    begin = begin,
                ),
                startTime = 0.0,
                gateEndTime = 0.5,
                playbackStartTime = 0.0,
            ),
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "playhead", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = 220.0, sample = pcm) },
        ) ?: error("makeVoice returned null for the sample path")

        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = DoubleArray(blocks * blockFrames)
        for (b in 0 until blocks) {
            voiceBuffer.fill(0.0)
            rc.blockStart = (b * blockFrames).toDouble()
            if (!voice.render(rc)) break
            for (i in 0 until blockFrames) out[b * blockFrames + i] = voiceBuffer[i]
        }
        return out
    }

    fun pcmAt(frame: Int): Double = frame.toDouble() / (pcmSize - 1)

    val loopedMeta = SampleMetadata(
        loop = SampleMetadata.LoopRange(
            startSec = loopStartFrame.toDouble() / sampleRate,
            endSec = loopEndFrame.toDouble() / sampleRate,
        ),
        adsr = AdsrDef.empty,
        anchor = 0.0,
    )

    "a looped sample plays its ATTACK first — the playhead starts at frame 0, not at loopStart" {
        val out = render(loopedMeta)

        // Frame k reads pcm[k]. The old code read pcm[loopStart + k]: for k = 100 that is
        // 2305/4409 = 0.523 instead of 0.023 — a whole attack's worth of difference.
        for (k in listOf(0, 1, 100, 1000)) {
            withClue("frame $k") { out[k] shouldBe pcmAt(k).plusOrMinus(1e-9) }
        }
    }

    "...and the loop still engages once the playhead gets there" {
        val out = render(loopedMeta)

        // After loopEnd the playhead has wrapped to loopStart: frame loopEnd + k reads pcm[loopStart + k].
        // This is what the fix must NOT break — it moved the start, not the loop.
        for (k in listOf(0, 10, 500)) {
            withClue("frame ${loopEndFrame + k}, one loop in") {
                out[loopEndFrame + k] shouldBe pcmAt(loopStartFrame + k).plusOrMinus(1e-9)
            }
        }
    }

    "a non-looped sample starts at frame 0 even when anchor says otherwise" {
        val meta = SampleMetadata(loop = null, adsr = AdsrDef.empty, anchor = 0.05) // 2205 frames in
        val out = render(meta)

        // anchor is the loudest sample's position, not a start offset. The old code began here at
        // pcm[2205 + k] and dropped 50 ms — for a real pluck, the whole transient.
        for (k in listOf(0, 100, 1000)) {
            withClue("frame $k") { out[k] shouldBe pcmAt(k).plusOrMinus(1e-9) }
        }
    }

    "the user's begin() still wins over both" {
        val out = render(loopedMeta, begin = 0.25) // 25% in => frame 1102

        // `begin` is the user asking for an offset, and that path is untouched by the fix.
        // 0.25 * 4410 = 1102.5 — a FRACTIONAL frame, which the interpolator honours, so the
        // expectation is the ramp evaluated at 1102.5, not at pcm[1102].
        out[0] shouldBe ((0.25 * pcmSize) / (pcmSize - 1)).plusOrMinus(1e-9)
    }
})
