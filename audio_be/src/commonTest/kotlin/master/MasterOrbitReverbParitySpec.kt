/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * **The guard for the bug this whole change exists to fix.**
 *
 * The shared Freeverb is driven from two hosts — the per-orbit bus and the master bus. For a while
 * the same authored number meant different things on each: sprudel divided `roomsize` by 10, the
 * master did not, so `roomSize(3)` was a ~1 s tail on an orbit and a ~12.5 s one on the master (it
 * clamped to the maximum). Nothing compared the two, so nothing noticed.
 *
 * Both sides here go through their **real** production path — `VoiceFactory` for the orbit,
 * `MasterChain.build` for the master — so a regression in either one fails this spec.
 */
class MasterOrbitReverbParitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** What the ORBIT path hands the Freeverb for a given authored room size. */
    fun orbitRoomSize(authored: Double): Double {
        val registry = IgnitorRegistry().apply { registerDefaults() }
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
                data = VoiceData.empty.copy(
                    freqHz = 440.0,
                    sound = "triangle",
                    room = 0.5,
                    roomSize = authored,
                ),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        // ...and then through the cylinder, which is what actually writes the DSP. Reading
        // `voice.reverb.roomSize` here would stop one step short and miss a second /10 introduced
        // in `Cylinder` — exactly the class of bug this spec exists to catch.
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        return cylinder.reverb.reverb.roomSize
    }

    /** What the MASTER path hands the Freeverb for the same authored room size. */
    fun masterReverb(
        roomSize: Double,
        damp: Double = 0.5,
        roomFade: Double? = null,
        roomLp: Double? = null,
    ) = MasterChain.build(
        dsl = MasterDsl.of(
            MasterStageDsl.Reverb(
                wet = 0.5, roomSize = roomSize, damp = damp,
                roomFade = roomFade, roomLp = roomLp
            )
        ),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    ).reverbs.firstOrNull().shouldNotBeNull()

    "the same authored roomSize reaches the DSP identically on both buses" {
        listOf(3.0, 5.0, 8.0, 10.0).forEach { authored ->
            masterReverb(authored).roomSize shouldBe orbitRoomSize(authored)
        }
    }

    "an authored 3 is the ~1 s tail it reads like, on both buses" {
        // 3 / 10 = 0.3 -> comb feedback 0.784 -> ~1 s. Before the fix the master clamped this to
        // 1.0 (feedback 0.98, ~12.5 s) — the reported symptom.
        masterReverb(3.0).roomSize shouldBe 0.3
        orbitRoomSize(3.0) shouldBe 0.3
    }

    "both buses apply the same bound — a comb can never be driven past unity on either" {
        // Not a taste clamp: past 1.0 the comb feedback exceeds unity and the network latches to
        // DC (AC-RMS 0.0) instead of ringing longer. What matters for parity is that BOTH buses
        // agree on where that boundary is.
        masterReverb(30.0).roomSize shouldBe 1.0
        orbitRoomSize(30.0) shouldBe 1.0
    }

    "the master exposes the orbit's tail/damping vocabulary, unchanged" {
        val reverb = masterReverb(
            roomSize = 8.0, damp = 0.3, roomFade = 0.12, roomLp = 12000.0
        )

        // roomFade / roomLp are raw pass-throughs on BOTH buses — same number, same meaning.
        reverb.roomFade shouldBe 0.12
        reverb.roomLp shouldBe 12000.0
        reverb.damp shouldBe 0.3
    }

    "the delay keeps its ceiling — there, unlike the reverb, self-oscillation is real" {
        val chain = MasterChain.build(
            dsl = MasterDsl.of(
                MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.25, feedback = 1.0, cap = 3.0)
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )

        chain.delays.firstOrNull().shouldNotBeNull().feedbackCap shouldBe 3.0
    }

    "roomFade alone is audible on both buses — it overrides roomSize, so it must gate on itself" {
        // The parity defect this spec exists to catch, in its second form: the orbit's roomSize
        // defaults to 0, so gating audibility on roomSize alone made `room(0.6).roomfade(0.1)`
        // silent on an orbit while the identical intent worked on the master (whose roomSize
        // defaults to 5). Both gates now ask `roomFade ?: roomSize`.
        val chain = MasterChain.build(
            dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.5, roomSize = 0.0, roomFade = 0.5)),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )

        chain.isActive shouldBe true
        chain.reverbs.firstOrNull().shouldNotBeNull().roomFade shouldBe 0.5
    }

    "an explicit roomFade renders on the ORBIT bus, at any value including 0.0" {
        // The orbit half of the gate fix. roomSize defaults to 0.0 there, so testing it alone made
        // a roomfade-only voice silent; and 0.0 is the engine's SHORTEST tail, not "off".
        fun rendersWith(roomFade: Double?, roomSize: Double): Boolean {
            val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
            cylinder.updateFromVoice(
                VoiceTestHelpers.createSynthVoice(
                    blockFrames = blockFrames,
                    reverb = Voice.Reverb(room = 0.6, roomSize = roomSize, roomFade = roomFade),
                ),
                blockStart = 0.0,
            )

            // Feed the send and look for wet output. Freeverb's shortest comb is 1116 samples, so
            // a single 128-frame block returns silence no matter what — render past that.
            val ctx = cylinder.katalystContext
            var heard = false

            repeat(30) {
                for (i in 0 until blockFrames) {
                    ctx.reverbSendBuffer.left[i] = 0.5
                    ctx.reverbSendBuffer.right[i] = 0.5
                }
                ctx.mixBuffer.clear()
                cylinder.reverb.process(ctx)

                if ((0 until blockFrames).any { ctx.mixBuffer.left[it] != 0.0 }) {
                    heard = true
                }
            }

            return heard
        }

        rendersWith(roomFade = 0.0, roomSize = 0.0) shouldBe true    // shortest tail, still a tail
        rendersWith(roomFade = 0.3, roomSize = 0.0) shouldBe true    // roomfade-only
        rendersWith(roomFade = null, roomSize = 0.0) shouldBe false  // genuinely nothing set
        rendersWith(roomFade = null, roomSize = 0.5) shouldBe true
    }
})
