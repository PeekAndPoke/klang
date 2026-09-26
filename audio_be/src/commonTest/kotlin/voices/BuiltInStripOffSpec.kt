/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * **Who runs the voice strip in phase 3 step 6.** A BUILT-IN sound is one Ignitor tree and runs no strip;
 * an AUTHORED instrument and a SAMPLE keep the strip until it retires (step 9). The two paths coexist per
 * voice, decided by the name's registry entry (`IgnitorRegistry.isBuiltIn`).
 *
 * The built-in side is pinned wide by `BuiltInVoiceMatrixSpec` and deep by `ClassicStripParitySpec`; this
 * spec pins the OTHER side (the strip still runs where it must) and the one path those two cannot reach,
 * a realtime note-off on a built-in whose envelope is switched off, where the teardown fade has to follow
 * the moved end.
 */
class BuiltInStripOffSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    val registry = IgnitorRegistry().apply {
        registerDefaults()
        register("stripsaw", builtInSources().getValue("saw"))
    }

    /** A 220 Hz sine as sample PCM: 0.5 s at the render rate, so the playback rate is exactly 1.0. */
    val pcm = MonoSamplePcm(
        sampleRate = sampleRate,
        pcm = DoubleArray(sampleRate / 2) { sin(2.0 * PI * 220.0 * it / sampleRate) },
        meta = SampleMetadata.default,
    )

    /**
     * Renders one voice through the real [VoiceFactory] for [blocks] blocks; [noteOffAtFrame] releases
     * the gate NOW at that frame, the realtime path (`Voice.releaseGate`).
     */
    fun render(data: VoiceData, blocks: Int = 120, gateSec: Double = 0.25, noteOffAtFrame: Double? = null): DoubleArray {
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = 0.0,
                gateEndTime = gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = 220.0, sample = pcm) },
        ) ?: error("makeVoice returned null for ${data.sound}")

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { block ->
            val blockStart = (block * blockFrames).toDouble()

            if (noteOffAtFrame != null && noteOffAtFrame >= blockStart && noteOffAtFrame < blockStart + blockFrames) {
                voice.releaseGate(noteOffAtFrame)
            }

            ctx.blockStart = blockStart
            voice.render(ctx)

            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    val lpf = FilterDefs(listOf(FilterDef.LowPass(300.0, 0.707)))
    val base = VoiceData.empty.copy(freqHz = 220.0)

    "a SAMPLE voice still runs the voice strip: its typed lowpass and its envelope apply" {
        val plain = render(base.copy(sound = "probe"))
        val filtered = render(base.copy(sound = "probe", filters = lpf))
        val slowAttack = render(base.copy(sound = "probe", adsr = AdsrDef.Std(attack = 0.1)))

        withClue("the lowpass applies") { filtered.toList() shouldNotBe plain.toList() }
        withClue("the envelope applies") { slowAttack.toList() shouldNotBe plain.toList() }
    }

    "an AUTHORED instrument still runs the voice strip: its typed lowpass applies" {
        val plain = render(base.copy(sound = "stripsaw"))
        val filtered = render(base.copy(sound = "stripsaw", filters = lpf))

        filtered.toList() shouldNotBe plain.toList()
    }

    "a realtime note-off on a built-in with adsrOff fades over the MOVED end, bit for bit the strip's fade" {
        // The gate is scheduled far away; the note-off moves it to frame 7000, and the voice then ends
        // one release (0.05 s) later. Both sides fade over the new end: the built-in through the voice's
        // teardown stage, the strip saw through its VCA's `adsrOff` path.
        val off = base.copy(adsr = AdsrDef.Std(on = false))
        val noteOff = 7000.0
        val builtIn = render(off.copy(sound = "saw"), gateSec = 10.0, noteOffAtFrame = noteOff)
        val strip = render(off.copy(sound = "stripsaw"), gateSec = 10.0, noteOffAtFrame = noteOff)
        val lastFrame = (noteOff + 0.05 * sampleRate).toInt() - 1

        withClue("first mismatching frame") {
            (strip.indices.firstOrNull { strip[it].toRawBits() != builtIn[it].toRawBits() } ?: -1) shouldBe -1
        }
        withClue("engaged: the moved end is faded to an exact zero, and the body before it is not") {
            builtIn[lastFrame] shouldBe 0.0
            builtIn[lastFrame - 400] shouldNotBe 0.0
        }
    }
    "a typed door WINS over a raw oscp of the same classic slot on one event (until step 8 makes it pattern order)" {
        val door = render(base.copy(sound = "saw", filters = lpf))
        val both = render(base.copy(sound = "saw", filters = lpf, oscParams = mapOf("lpf.freq" to 5000.0)))
        val slotOnly = render(base.copy(sound = "saw", oscParams = mapOf("lpf.freq" to 5000.0)))

        withClue("the door's 300 Hz, not the slot's 5 kHz") { both.toList() shouldBe door.toList() }
        withClue("engaged: the slot alone is a different filter") { slotOnly.toList() shouldNotBe door.toList() }
    }
    "a built-in with a NEGATIVE release plays to its gate: the lifetime is floored at 0 (the raw release is a zero-length stage)" {
        // `adsr(0.01, 0.1, 1, -0.1)`: the envelope reads the raw -0.1 (a zero-length release), but the voice
        // must not end 0.1 s before its gate, cut from the sustain level, as it would if the tree's negative
        // tail set the lifetime.
        val negative = base.copy(sound = "saw", adsr = AdsrDef.Std(attack = 0.01, decay = 0.1, sustain = 1.0, release = -0.1))
        val gateFrame = (0.25 * sampleRate).toInt()
        val out = render(negative)

        withClue("the frames just before the gate still sound") {
            (gateFrame - 64 until gateFrame).any { out[it] != 0.0 } shouldBe true
        }
        withClue("and the zero-length release ends it at the gate") {
            (gateFrame + 64 until gateFrame + 1024).all { out[it] == 0.0 } shouldBe true
        }
    }

    "a built-in with a NEGATIVE release and a gate SHORTER than it still renders its note" {
        val negative = base.copy(sound = "saw", adsr = AdsrDef.Std(attack = 0.005, decay = 0.1, sustain = 1.0, release = -0.1))
        val out = render(negative, gateSec = 0.05)

        out.any { it != 0.0 } shouldBe true
    }
})
