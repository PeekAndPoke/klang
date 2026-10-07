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
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.optimizer
import io.peekandpoke.klang.audio_bridge.pregain
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * **Every voice is one Ignitor tree** (phase 3 step 9 retired the voice strip): a built-in sound, a sample (the
 * sample instrument over its PCM), an authored instrument. This spec pins the doors on a sample and on an authored
 * `classic()` tree, the by-ear A/B on such a tree, the one path `BuiltInVoiceMatrixSpec` and `SampleInstrumentSpec`
 * cannot reach (a realtime note-off on a built-in whose envelope is switched off, where the teardown fade has to
 * follow the moved end), and the negative-release lifetime. An instrument WITHOUT `classic()` is
 * `BareTreeVoiceSpec`'s.
 *
 * Until step 9 this was `BuiltInStripOffSpec`, which also pinned who still ran the strip.
 */
class TreeVoiceSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    val registry = IgnitorRegistry().apply {
        registerDefaults()
        // An author's instrument that ends in `classic()` (the built-in saw's own tree).
        register("authoredsaw", builtInSources().getValue("saw").pregain().classic())
        // The by-ear A/B on an authored classic() tree: the optimizer hint LAST, as its KDoc says to put it.
        register("authoredsawab", builtInSources().getValue("saw").pregain().classic().optimizer(0))
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
    fun render(
        data: VoiceData,
        doors: DoorFields = DoorFields(),
        blocks: Int = 120,
        gateSec: Double = 0.25,
        noteOffAtFrame: Double? = null,
    ): DoubleArray {
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data.withClassicSlots(doors),
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

            val cylinder = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    val lpf = DoorFields(filters = listOf(DoorFilter.LowPass(300.0, 0.707)))
    val base = VoiceData.empty.copy(freqHz = 220.0)

    "a SAMPLE voice runs the sample instrument: its lowpass and its envelope apply (as slots)" {
        val plain = render(base.copy(sound = "probe"))
        val filtered = render(base.copy(sound = "probe"), lpf)
        val slowAttack = render(base.copy(sound = "probe"), DoorFields(adsr = DoorAdsr(attack = 0.1)))

        withClue("the lowpass applies") { filtered.toList() shouldNotBe plain.toList() }
        withClue("the envelope applies") { slowAttack.toList() shouldNotBe plain.toList() }
    }

    "an AUTHORED instrument that ends in classic(): the doors reach its slots" {
        val plain = render(base.copy(sound = "authoredsaw"))

        withClue("the lowpass") { render(base.copy(sound = "authoredsaw"), lpf).toList() shouldNotBe plain.toList() }
        withClue("the envelope") { render(base.copy(sound = "authoredsaw"), DoorFields(adsr = DoorAdsr(attack = 0.05))).toList() shouldNotBe plain.toList() }
    }

    "an AUTHORED classic() tree with the optimizer hint last (the by-ear A/B) renders what the tree renders optimized" {
        val doors = lpf.copy(adsr = DoorAdsr(attack = 0.05))
        val ab = render(base.copy(sound = "authoredsawab"), doors)
        val optimized = render(base.copy(sound = "authoredsaw"), doors)

        withClue("the optimizer off renders the same voice as the optimizer on, first mismatch") {
            (ab.indices.firstOrNull { ab[it].toRawBits() != optimized[it].toRawBits() } ?: -1) shouldBe -1
        }
    }

    "a realtime note-off on a built-in with adsrOff fades over the MOVED end" {
        // The gate is scheduled far away; the note-off moves it to frame 7000, and the voice then ends
        // one release (0.05 s) later, through the voice's teardown stage. (Until step 9 this row also
        // pinned it bit for bit to the strip's `adsrOff` path, the fade's first host.)
        val off = DoorFields(adsr = DoorAdsr(on = false))
        val noteOff = 7000.0
        val builtIn = render(base.copy(sound = "saw"), off, gateSec = 10.0, noteOffAtFrame = noteOff)
        val lastFrame = (noteOff + 0.05 * sampleRate).toInt() - 1

        withClue("the voice is gone after the moved end") {
            (lastFrame + 1 until lastFrame + 1000).all { builtIn[it] == 0.0 } shouldBe true
        }
        withClue("engaged: the moved end is faded to an exact zero, and the body before it is not") {
            builtIn[lastFrame] shouldBe 0.0
            builtIn[lastFrame - 400] shouldNotBe 0.0
        }
    }
    "a built-in with a NEGATIVE release plays to its gate: the lifetime is floored at 0 (the raw release is a zero-length stage)" {
        // `adsr(0.01, 0.1, 1, -0.1)`: the envelope reads the raw -0.1 (a zero-length release), but the voice
        // must not end 0.1 s before its gate, cut from the sustain level, as it would if the tree's negative
        // tail set the lifetime.
        val negative = DoorFields(adsr = DoorAdsr(attack = 0.01, decay = 0.1, sustain = 1.0, release = -0.1))
        val gateFrame = (0.25 * sampleRate).toInt()
        val out = render(base.copy(sound = "saw"), negative)

        withClue("the frames just before the gate still sound") {
            (gateFrame - 64 until gateFrame).any { out[it] != 0.0 } shouldBe true
        }
        withClue("and the zero-length release ends it at the gate") {
            (gateFrame + 64 until gateFrame + 1024).all { out[it] == 0.0 } shouldBe true
        }
    }

    "a built-in with a NEGATIVE release and a gate SHORTER than it still renders its note" {
        val negative = DoorFields(adsr = DoorAdsr(attack = 0.005, decay = 0.1, sustain = 1.0, release = -0.1))
        val out = render(base.copy(sound = "saw"), negative, gateSec = 0.05)

        out.any { it != 0.0 } shouldBe true
    }
})
