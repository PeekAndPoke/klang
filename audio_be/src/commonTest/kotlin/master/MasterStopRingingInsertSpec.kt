/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.releaseStarted
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.abs

/**
 * A stopped playback whose master holds only insert stages is kept alive while they ring, and disposed only once its
 * output has fallen under the audibility floor (KLANG-00415 (master stop click on insert-only chains)). Before, the
 * master's keep-alive asked only chains that declare a tail, so the engine was disposed as soon as its voices and
 * orbits were done, with the insert still ringing: a 220 Hz sine through a +24 dB eq bell at q 30 left a step of 0.30,
 * a bell body 0.026, a phaser centred at 200 Hz 7e-4. A dry master goes exactly when an unmastered engine does, and a
 * master that sounds on silence (an offset) is released after the hold, never kept for ever. The floor is the engine's
 * one, `SILENCE_FLOOR` (-100 dBFS; the master's own 1e-4 until 2026-11-05, KLANG-00290 (engine follow-ups) item 25).
 *
 * The engine is driven block by block through `renderInto` (the dispatcher's own order, minus the house stage, whose
 * limiter would hide the step); the step is the last sample before the engine reads idle, which the dispatcher then
 * drops.
 */
class MasterStopRingingInsertSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun k(v: Double) = KatalystKnobDsl.Constant(v)

    val eqBell = KatalystStageDsl.Eq(
        sections = listOf(
            IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Constant(220.0), q = IgnitorDsl.Constant(30.0), db = IgnitorDsl.Constant(24.0)),
        ),
    )
    val bellBody = KatalystStageDsl.Body(material = k(BodyMaterials.names.indexOf("bell").toDouble()), wet = k(1.0))
    val lowPhaser = KatalystStageDsl.Phaser(wet = k(1.0), center = k(200.0), sweep = k(100.0))
    val dry = KatalystStageDsl.Gain(gain = k(0.9))

    class Rig {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = 44100,
            blockFrames = 128,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

        private val clock = d.clockForTest as BackendClock
        private val buf = StereoBuffer(128)

        val engine: PlaybackEngine get() = d.engine("song").shouldNotBeNull()

        /** Block [b] of this engine alone; returns the left channel. */
        fun block(b: Int): DoubleArray {
            clock.cursorFrame = b * 128.0
            buf.clear()
            engine.renderInto(buf, b * 128.0)
            clock.cursorFrame = (b + 1) * 128.0

            return buf.left.copyOf()
        }
    }

    /** [step]: the last sample before the engine read idle. [idleAt]: that block, -1 when it never did. */
    class Stopped(val step: Double, val idleAt: Int, val released: Boolean)

    /**
     * A 220 Hz sine for 0.5 s with a 5 ms release, through a master of [stages] (none: no master at all); the playback
     * is stopped at once and rendered until its engine reads idle, at most 30 s.
     */
    fun stopThrough(vararg stages: KatalystStageDsl): Stopped {
        val rig = Rig()
        val tone = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.5,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 220.0, gain = 0.5, ignitorParams = klangMapOf("adsr.release" to 0.005)),
            playbackStartTime = 0.0,
        )
        val voices = if (stages.isEmpty()) {
            listOf(tone)
        } else {
            rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "m", dsl = KatalystDsl.of(*stages)))

            listOf(
                ScheduledVoice(
                    playbackId = "song",
                    startTime = 0.0,
                    gateEndTime = 1.0,
                    data = VoiceData.empty.copy(master = "m", control = true),
                    playbackStartTime = 0.0,
                ),
                tone,
            )
        }

        rig.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = voices))
        rig.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))

        var last = 0.0

        for (blk in 0 until 30 * sampleRate / blockFrames) {
            last = rig.block(blk)[blockFrames - 1]

            if (rig.engine.isIdle()) {
                return Stopped(step = abs(last), idleAt = blk, released = rig.engine.releaseStarted)
            }
        }

        return Stopped(step = abs(last), idleAt = -1, released = rig.engine.releaseStarted)
    }

    "a master of insert stages keeps a stopped engine alive while it rings: disposed under -100 dB, not mid-ring" {
        val unmastered = stopThrough()

        for ((name, stage) in listOf("eq bell" to eqBell, "bell body" to bellBody, "low phaser" to lowPhaser)) {
            val stopped = stopThrough(stage)

            withClue("$name: idle at block ${stopped.idleAt}, the unmastered engine at ${unmastered.idleAt}") {
                stopped.idleAt shouldBeGreaterThan unmastered.idleAt
            }

            withClue("$name: the step the disposal leaves") {
                stopped.step shouldBeLessThan SILENCE_FLOOR
            }

            withClue("$name: rung out, not released") {
                stopped.released shouldBe false
            }
        }
    }

    "a dry master is disposed in the very block an unmastered engine is" {
        stopThrough(dry).idleAt shouldBe stopThrough().idleAt
    }

    "a master switched back to the empty chain while the music sounds lets the stopped engine go" {
        // The empty chain leaves the bus inactive, so it is no longer processed: its last answer, taken on a block
        // that still sounded, must not hold the engine.
        val rig = Rig()

        rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "m", dsl = KatalystDsl.of(dry)))
        rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "none", dsl = KatalystDsl.of()))

        fun master(name: String, at: Double) = ScheduledVoice(
            playbackId = "song",
            startTime = at,
            gateEndTime = at + 0.1,
            data = VoiceData.empty.copy(master = name, control = true),
            playbackStartTime = 0.0,
        )

        val tone = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 220.0, gain = 0.5, ignitorParams = klangMapOf("adsr.release" to 0.005)),
            playbackStartTime = 0.0,
        )

        rig.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(master("m", 0.0), master("none", 0.3), tone)))

        var idleAt = -1
        var unmasteredWhileSounding = false

        for (blk in 0 until 3 * sampleRate / blockFrames) {
            // Stopped once the switch has landed: a stop drops the events still scheduled
            if (blk == sampleRate / 2 / blockFrames) {
                rig.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
            }

            val out = rig.block(blk)

            if (blk * blockFrames > sampleRate / 2 && blk * blockFrames < sampleRate * 9 / 10) {
                unmasteredWhileSounding = unmasteredWhileSounding || (!rig.engine.masterBusForTest.isActive && out.any { abs(it) > 1e-2 })
            }

            if (rig.engine.isIdle()) {
                idleAt = blk
                break
            }
        }

        withClue("positive control: the bus went inactive while the tone still sounded") {
            unmasteredWhileSounding shouldBe true
        }

        withClue("idle within 0.2 s of the tone's end (block ${idleAt})") {
            idleAt shouldBeGreaterThan sampleRate / blockFrames
            idleAt shouldBeLessThan sampleRate * 12 / 10 / blockFrames
        }
    }

    "an orbit's long finite echo under a dry master is not released at the hold: it is the engine's own sound" {
        // The master sounds past the hold only because the orbit feeds it; the release is for a master that sounds
        // with nothing to feed it.
        val holdBlocks = 20 * sampleRate / blockFrames
        val rig = Rig()

        rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "m", dsl = KatalystDsl.of(dry)))
        rig.d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    ScheduledVoice(
                        playbackId = "song",
                        startTime = 0.0,
                        gateEndTime = 1.0,
                        data = VoiceData.empty.copy(master = "m", control = true),
                        playbackStartTime = 0.0,
                    ),
                    ScheduledVoice(
                        playbackId = "song",
                        startTime = 0.0,
                        gateEndTime = 0.05,
                        data = VoiceData.empty.copy(
                            sound = "sine",
                            freqHz = 440.0,
                            gain = 0.5,
                            katalystParams = klangMapOf("delay.wet" to 0.5, "delay.time" to 0.5, "delay.feedback" to 0.97),
                        ),
                        playbackStartTime = 0.0,
                    ),
                ),
            )
        )
        rig.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))

        for (blk in 0 until holdBlocks + 2 * sampleRate / blockFrames) {
            rig.block(blk)
        }

        withClue("positive control: the echo still sounds two seconds after the hold") {
            rig.engine.cylinders.anyActive() shouldBe true
        }

        withClue("never released") {
            rig.engine.releaseStarted shouldBe false
        }
    }

    "a master that sounds on silence is released after the hold, never kept for ever" {
        // An offset sounds for ever and declares no tail: the hold (20 s from the last note) is over, then the release
        // (4.5 s) fades it to nothing, and the engine is disposed with no step.
        val holdBlocks = 20 * sampleRate / blockFrames
        val stopped = stopThrough(KatalystStageDsl.Plus(k(0.1)))

        withClue("idle at block ${stopped.idleAt}") {
            stopped.released shouldBe true
            stopped.idleAt shouldBeGreaterThan holdBlocks
            stopped.idleAt shouldBeLessThan holdBlocks + 6 * sampleRate / blockFrames
            stopped.step shouldBe 0.0
        }
    }
})
