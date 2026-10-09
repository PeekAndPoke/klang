/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * TEST ONLY. The frequency RATIO a voice's pitch doors apply, frame by frame, read through the real [VoiceFactory]
 * and the sample instrument (`classic()` over the sample playhead), with no probe inside the engine.
 *
 * The sample is a RAMP, `pcm[n] = n`, at the render rate and at the note's pitch, so the playhead runs at rate 1.0
 * times the voice's pitch ratio and its linear interpolation returns the playhead itself: frame `i` outputs the
 * playhead, and the playhead advances by the ratio of frame `i` (`SampleIgnitor`: `ph += rate * phaseMod[i]`). So
 * `ratio[i] = out[i + 1] - out[i]`, to the rounding of a sum near the playhead (about 1e-11 here). The ramp is long
 * enough for a playhead four octaves up for the whole render. The voice's
 * envelope is switched off (`adsr.on` 0), so the tree's output is the playhead.
 *
 * [doors] are written as slots ([withClassicSlots]) on top of [data]'s own bag, `adsrOff` included. The voice starts
 * [onsetFrames] into the first block; the result is note-relative, [frames] ratios from the onset. [releaseAtFrame]
 * (note-relative) releases the voice there the way a realtime note-off does (`Voice.releaseGate`, called before the
 * block that holds the frame), so a held voice (a far [gateFrames]) can be released mid-envelope.
 */
fun renderPitchRatios(
    doors: DoorFields,
    data: VoiceData = VoiceData.empty,
    frames: Int,
    gateFrames: Int,
    onsetFrames: Int = 0,
    releaseAtFrame: Int? = null,
): DoubleArray {
    val sampleRate = 48000
    val blockFrames = 128
    val pitchHz = 220.0
    val total = onsetFrames + frames + 1
    // Long enough for a playhead running four octaves up for the whole render: past its end the sample is silent.
    val ramp = MonoSamplePcm(sampleRate = sampleRate, pcm = DoubleArray(16 * (total + 2 * blockFrames)) { it.toDouble() }, meta = SampleMetadata.default)
    val voiceBuffer = DoubleArray(blockFrames)
    val factory = VoiceFactory(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
    )
    // +0.25 frames keeps every floor() stable against 1-ulp time wobble without moving a frame.
    val start = (onsetFrames + 0.25) / sampleRate
    val voice = factory.makeVoice(
        scheduled = ScheduledVoice(
            playbackId = "pitch-probe",
            data = data.copy(freqHz = pitchHz, sound = "pitch-probe-ramp")
                .withClassicSlots(doors.copy(adsr = (doors.adsr ?: DoorAdsr()).copy(on = false))),
            startTime = start,
            gateEndTime = start + gateFrames.toDouble() / sampleRate,
            playbackStartTime = 0.0,
        ),
        backendStartTimeSec = 0.0,
        playbackCtx = PlaybackCtx(
            playbackId = "pitch-probe",
            ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
            phasePools = PhasePools(Random(1)),
        ),
        getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = pitchHz, sample = ramp) },
    ) ?: error("makeVoice returned null")
    val rc = Voice.RenderContext(
        cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
    )
    val out = DoubleArray(total + blockFrames)
    var block = 0

    while (block * blockFrames < total) {
        voiceBuffer.fill(0.0)
        rc.blockStart = (block * blockFrames).toDouble()

        if (releaseAtFrame != null) {
            val at = onsetFrames + releaseAtFrame

            if (at >= block * blockFrames && at < (block + 1) * blockFrames) {
                voice.releaseGate(at.toDouble())
            }
        }

        voice.render(rc)
        voiceBuffer.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
        block++
    }

    return DoubleArray(frames) { out[onsetFrames + it + 1] - out[onsetFrames + it] }
}
