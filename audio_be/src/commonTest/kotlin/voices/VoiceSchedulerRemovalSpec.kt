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
 * The scheduler's one removal law: a `Done` voice leaves and the others keep their order. Between blocks the
 * sweep (`removeDoneVoices`) takes the voices a hard kill (`cleanupHard`, lifecycle step 3; the engine is disposed
 * right after, so they must leave at once) or a cut sent to `Done`; in the render loop the same in-place removal
 * takes a voice that ended while it rendered, a culled one included (lifecycle step 5). The order is read through
 * `renderingVoiceData`, which lists the active timeline voices in list order; each voice is told apart by its
 * frequency. A scheduler serves one playback (tidy-up step 8), so `cleanupHard` kills every voice in it; the
 * between-blocks sweep with survivors is seen through a cut that finds a voice not yet rendered.
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
        val engine = PlaybackEngine.create(context = context, playbackId = "song")
        private val mix = StereoBuffer(blockFrames)

        fun schedule(
            startSec: Double,
            freqHz: Double,
            data: VoiceData = VoiceData.empty.copy(sound = "sine", freqHz = freqHz),
            durSec: Double = 30.0,
        ) {
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "song",
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

    /** Six voices, each onset one block after the last. */
    fun sixInARow(): Rig {
        val rig = Rig()
        val block = blockFrames.toDouble() / sampleRate
        val plan = listOf(101.0, 201.0, 301.0, 102.0, 202.0, 302.0)

        plan.forEachIndexed { i, freq -> rig.schedule(startSec = i * block, freqHz = freq) }
        rig.render(plan.size + 4)

        return rig
    }

    "the list order the rows below start from" {
        sixInARow().order() shouldBe listOf(101.0, 201.0, 301.0, 102.0, 202.0, 302.0)
    }

    "cleanupHard kills every voice of the playback and removes them at once, between blocks" {
        val rig = sixInARow()

        rig.engine.scheduler.cleanupHard()

        withClue("gone before the next block") {
            rig.engine.scheduler.getActiveVoiceCount() shouldBe 0
            rig.order() shouldBe emptyList()
        }

        rig.render(2)
        withClue("and nothing comes back") { rig.engine.scheduler.getActiveVoiceCount() shouldBe 0 }
    }

    "a cut that finds a voice not yet rendered removes it between blocks, the survivors keep their order" {
        val rig = Rig()
        val block = blockFrames.toDouble() / sampleRate
        val frame = 1.0 / sampleRate

        rig.schedule(startSec = 0.0, freqHz = 101.0)
        rig.render(2)

        // All four promoted in the block about to render, none rendered yet: the cutting 501 finds 201 Pending,
        // which is Done at once and swept before 501 joins.
        val at = 2 * block
        val cut = VoiceData.empty.copy(sound = "sine", cut = 1)

        rig.schedule(startSec = at + 5 * frame, freqHz = 201.0, data = cut.copy(freqHz = 201.0))
        rig.schedule(startSec = at + 10 * frame, freqHz = 301.0)
        rig.schedule(startSec = at + 15 * frame, freqHz = 401.0)
        rig.schedule(startSec = at + 20 * frame, freqHz = 501.0, data = cut.copy(freqHz = 501.0))

        // A swap-with-last removal would leave 101, 401, 301, 501.
        withClue("order-preserving, before any block rendered them") {
            rig.order() shouldBe listOf(101.0, 301.0, 401.0, 501.0)
        }

        rig.render(2)
        withClue("and the survivors render on, in that order") { rig.order() shouldBe listOf(101.0, 301.0, 401.0, 501.0) }
    }

    "a culled voice leaves at once and the others keep their order" {
        val rig = Rig()
        val block = blockFrames.toDouble() / sampleRate
        // Percussive and cullable at once: silent 0.5 ms in, culled on the first silent release block.
        val hat = VoiceData.empty.copy(sound = "triangle", freqHz = 201.0, cull = 0.0)
            .withClassicSlots(DoorFields(adsr = DoorAdsr(attack = 0.0, decay = 0.0005, sustain = 0.0, release = 2.0)))

        rig.schedule(startSec = 0.0, freqHz = 101.0)
        rig.schedule(startSec = block, freqHz = 201.0, data = hat, durSec = 0.01)
        rig.schedule(startSec = 2 * block, freqHz = 301.0)
        rig.schedule(startSec = 3 * block, freqHz = 401.0)
        rig.render(6)
        withClue("all four listed") { rig.order() shouldBe listOf(101.0, 201.0, 301.0, 401.0) }

        rig.render(14)

        // The hat was culled and removed in place: a swap-with-last removal would read 101, 401, 301.
        withClue("culled and gone") { rig.engine.scheduler.culledVoicesTotal() shouldBe 1 }
        rig.order() shouldBe listOf(101.0, 301.0, 401.0)
    }
})
