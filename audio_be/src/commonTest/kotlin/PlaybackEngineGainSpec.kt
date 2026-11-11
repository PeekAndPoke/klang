/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.toKlangMap

/**
 * A playback's gain in the engine (`Cmd.SetGain`, KLANG-00406 step 5): the engine's output times its gain, back at 1
 * the control's bit for bit, another playback untouched; the command never resumes a stopped engine, creates one when
 * none is filed, and the gain belongs to the id across a detach; a playback stopped at gain 0 lives exactly as long
 * as at gain 1, because what decides its end is measured before the gain.
 *
 * The oracle is a CONTROL: the same commands without the gain. Engines are rendered one by one through `renderInto`,
 * without the house stage, whose limiter would hide the gain; the end of life through the dispatcher's own blocks.
 */
class PlaybackEngineGainSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** A ramp of exactly [blocks] blocks. */
    fun rampOf(blocks: Int): Double = blocks * blockFrames.toDouble() / sampleRate

    /** A dispatcher whose engines are rendered one by one, without the house stage. */
    class Rig {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = 44100,
            blockFrames = 128,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

        private val clock = d.clockForTest as BackendClock
        private val buf = StereoBuffer(128)
        private val out = StereoBuffer(128)

        fun engine(id: String): PlaybackEngine = d.engine(id).shouldNotBeNull()

        /** Block [b] of the engine [id] alone; returns its left channel. */
        fun block(id: String, b: Int): DoubleArray {
            clock.cursorFrame = b * 128.0
            buf.clear()
            engine(id).renderInto(buf, b * 128.0)
            clock.cursorFrame = (b + 1) * 128.0

            return buf.left.copyOf()
        }

        /** Block [b] through the dispatcher: every engine, the house stage, and the disposal of the drained ones. */
        fun dispatched(b: Int) {
            d.renderBlock(cursorFrame = b * 128.0, out = out)
        }
    }

    fun note(id: String, gateSec: Double = 1.0, katalystParams: Map<String, Double>? = null) = ScheduledVoice(
        playbackId = id,
        startTime = 0.0,
        gateEndTime = gateSec,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5, katalystParams = katalystParams?.toKlangMap()),
        playbackStartTime = 0.0,
    )

    fun Rig.play(id: String, voice: ScheduledVoice = note(id)) {
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = id, voices = listOf(voice)))
    }

    "the output is the control's times the gain, and back at 1 after a ramp the control's bit for bit" {
        val a = Rig()
        val control = Rig()

        a.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 0.5, rampSec = 0.0))
        a.play("song")
        control.play("song")

        for (b in 0 until 30) {
            if (b == 10) {
                a.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 1.0, rampSec = rampOf(4)))
            }

            val got = a.block("song", b)
            val want = control.block("song", b)

            when {
                b < 10 -> withClue("block $b at gain 0.5") { got.toList() shouldBe want.map { it * 0.5 } }
                b >= 14 -> withClue("block $b back at 1") { got.toList() shouldBe want.toList() }
                else -> Unit
            }
        }

        withClue("positive control: the note sounds") { control.block("song", 30).any { it != 0.0 } shouldBe true }
    }

    "another playback is untouched by a gain sent to one" {
        val a = Rig()
        val control = Rig()

        a.play("one")
        a.play("two")
        control.play("two")
        a.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "one", gain = 0.0, rampSec = 0.0))

        for (b in 0 until 10) {
            withClue("one is silent in block $b") { a.block("one", b).all { it == 0.0 } shouldBe true }
            withClue("two is the control in block $b") { a.block("two", b).toList() shouldBe control.block("two", b).toList() }
        }
    }

    "a gain sent to a stopped playback does not resume its engine" {
        val r = Rig()

        r.play("song")
        r.block("song", 0)
        r.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))

        val stopped = r.engine("song")

        r.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 0.0, rampSec = 2.0))

        r.engine("song") shouldBeSameInstanceAs stopped
        withClue("still stopped, not resumed") { stopped.phase shouldBe PlaybackEngine.Phase.Stopped }
    }

    "a gain sent before a start's first voices creates the engine, at that gain" {
        val r = Rig()

        r.d.engine("song").shouldBeNull()

        r.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 0.0, rampSec = 0.0))
        val created = r.engine("song")
        r.play("song")

        r.engine("song") shouldBeSameInstanceAs created
        withClue("the first block is silent") { r.block("song", 0).all { it == 0.0 } shouldBe true }
    }

    "a playback stopped at gain 0 lives exactly as long as one at gain 1: its end is measured before the gain" {
        /** The block the engine is disposed in, stopped at block 5. */
        fun lifetime(gain: Double): Int {
            val r = Rig()

            r.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = gain, rampSec = 0.0))
            // The stop gives the note's gate its note-off (KLANG-00427), so what outlives the stop is a finite echo
            val echo = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 0.5)

            r.play("song", note("song", gateSec = 0.3, katalystParams = echo))

            for (b in 0 until 2000) {
                if (b == 5) {
                    r.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
                }

                r.dispatched(b)

                if (r.d.engine("song") == null) {
                    return b
                }
            }

            return -1
        }

        val atOne = lifetime(1.0)

        withClue("positive control: the echo outlives the stop") { (atOne > 50) shouldBe true }
        lifetime(0.0) shouldBe atOne
    }

    "a gain sent while the id's engine releases goes to a fresh engine; the release fades on undisturbed" {
        val r = Rig()
        val endless = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)
        val holdBlocks = 20 * sampleRate / blockFrames

        r.play("song", note("song", gateSec = 0.05, katalystParams = endless))

        var b = 0

        // The hold counts from the note's end: stopped long after it
        while (b < 100) {
            r.dispatched(b++)
        }

        r.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        val stopped = r.engine("song")

        while (!stopped.releaseStarted && b < 100 + holdBlocks + 10) {
            r.dispatched(b++)
        }

        withClue("positive control: the stopped engine is releasing") { stopped.releaseStarted shouldBe true }

        r.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 0.25, rampSec = 0.0))
        val fresh = r.engine("song")

        withClue("the start's gain on a fresh engine") {
            fresh shouldNotBeSameInstanceAs stopped
            fresh.gainTargetForTest shouldBe 0.25
        }
        withClue("the release goes on, its gain untouched") {
            stopped.releaseStarted shouldBe true
            stopped.gainTargetForTest shouldBe 1.0
            r.d.detachedCountForTest shouldBe 1
        }

        r.play("song")

        withClue("the start plays on the engine its gain made") { r.engine("song") shouldBeSameInstanceAs fresh }
    }

    "the gain comes after the song's master chain: the master hears the song at full level" {
        val drive = KatalystDsl.of(KatalystStageDsl.Distort(amount = KatalystKnobDsl.Constant(0.8)))
        val a = Rig()
        val control = Rig()

        for (r in listOf(a, control)) {
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "drive", dsl = drive))
            r.d.handle(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(
                        ScheduledVoice(
                            playbackId = "song",
                            startTime = 0.0,
                            gateEndTime = 1.0,
                            data = VoiceData.empty.copy(master = "drive", control = true),
                            playbackStartTime = 0.0,
                        ),
                        note("song"),
                    ),
                )
            )
        }

        a.d.handle(KlangCommLink.Cmd.SetGain(playbackId = "song", gain = 0.25, rampSec = 0.0))

        for (b in 0 until 20) {
            val got = a.block("song", b)
            val want = control.block("song", b)

            withClue("block $b: the driven song, then a quarter of it") { got.toList() shouldBe want.map { it * 0.25 } }
        }

        withClue("positive control: the master is in play") { control.engine("song").masterBusForTest.isActive shouldBe true }
    }
})
