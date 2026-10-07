/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The scheduler's order-keeping removal sweep (`removeDoneVoices`), through its two callers. The hard kill
 * (lifecycle step 3): `cleanupHard` sends a playback's voices to `Done` with an event on the voice (`Voice.kill`)
 * and removes them at once, because the engine is disposed right after and no render follows that could remove
 * them. The cut (step 4): a silent voice of the cut group (`Pending`, `Zombie`) is `Done` at once and leaves by
 * the sweep, keeping the order (F3); a swap-with-last removal would hand its place, and so a shared orbit, to
 * another voice.
 *
 * In production the survivors' order cannot be seen: each playback has its own scheduler and `cleanupHard` kills
 * every voice in it. This spec hosts three playbacks on one scheduler only to pin what the sweep does (it removes
 * exactly the killed voices and keeps the rest in order), read through `renderingVoiceData`, which lists the
 * active timeline voices in list order; each voice is told apart by its frequency.
 */
class VoiceSchedulerRemovalSpec : StringSpec({

    val sampleRate = 48_000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    class Rig {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            phasePoolSeed = 1,
        )
        val engine = PlaybackEngine.create(context)
        private val mix = StereoBuffer(blockFrames)

        fun schedule(
            playbackId: String,
            startSec: Double,
            freqHz: Double,
            data: VoiceData = VoiceData.empty.copy(sound = "sine", freqHz = freqHz),
            durSec: Double = 30.0,
        ) {
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = playbackId,
                    startTime = startSec,
                    gateEndTime = startSec + durSec,
                    data = data,
                    playbackStartTime = 0.0,
                )
            )
        }

        fun render(blocks: Int) {
            repeat(blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                clock.cursorFrame += blockFrames
            }
        }

        /** The active timeline voices in list order, by frequency. */
        fun order(): List<Double> = engine.scheduler.renderingVoiceData().map { it.freqHz ?: -1.0 }
    }

    /** Three playbacks interleaved on one scheduler: a1 b1 c1 a2 b2 c2, each onset one block after the last. */
    fun interleaved(): Rig {
        val rig = Rig()
        val block = blockFrames.toDouble() / sampleRate
        val plan = listOf("a" to 101.0, "b" to 201.0, "c" to 301.0, "a" to 102.0, "b" to 202.0, "c" to 302.0)

        plan.forEachIndexed { i, (pid, freq) -> rig.schedule(pid, startSec = i * block, freqHz = freq) }
        rig.render(plan.size + 4)

        return rig
    }

    "the list order the rows below start from" {
        interleaved().order() shouldBe listOf(101.0, 201.0, 301.0, 102.0, 202.0, 302.0)
    }

    "cleanupHard kills one playback's voices and removes them, the survivors keep their order" {
        val rig = interleaved()

        rig.engine.scheduler.cleanupHard("b")

        // A swap-with-last removal would leave 101, 302, 301, 102.
        withClue("order-preserving") { rig.order() shouldBe listOf(101.0, 301.0, 102.0, 302.0) }
        rig.engine.scheduler.getActiveVoiceCount() shouldBe 4

        rig.render(2)
        withClue("and the survivors render on, in that order") { rig.order() shouldBe listOf(101.0, 301.0, 102.0, 302.0) }
    }

    "a cut sends a zombie of its group to Done and removes it at once, the others keep their order" {
        val rig = Rig()
        val block = blockFrames.toDouble() / sampleRate
        // Percussive and cullable at once: silent 0.5 ms in, culled on the first silent release block.
        val hat = VoiceData.empty.copy(sound = "triangle", freqHz = 201.0, cut = 1, cull = 0.0)
            .withClassicSlots(DoorFields(adsr = DoorAdsr(attack = 0.0, decay = 0.0005, sustain = 0.0, release = 2.0)))

        rig.schedule("a", startSec = 0.0, freqHz = 101.0)
        rig.schedule("a", startSec = block, freqHz = 201.0, data = hat, durSec = 0.01)
        rig.schedule("a", startSec = 2 * block, freqHz = 301.0)
        rig.render(20)

        withClue("the hat is a zombie: listed, not rendering") {
            rig.engine.scheduler.getActiveVoiceCount() shouldBe 3
            rig.order() shouldBe listOf(101.0, 301.0)
        }

        // A new voice of the hat's group cuts it.
        rig.schedule("a", startSec = rig.clock.cursorFrame / sampleRate, freqHz = 401.0, data = hat.copy(freqHz = 401.0))
        rig.render(1)

        // The zombie left at once and the list kept its order: 101, 301, then the new hat. Left to the render
        // loop's swap-with-last it would read 101, 401, 301.
        rig.engine.scheduler.getActiveVoiceCount() shouldBe 3
        rig.engine.scheduler.renderingVoiceData().map { it.freqHz } shouldBe listOf(101.0, 301.0, 401.0)
    }
})
