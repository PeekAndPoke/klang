/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * After a playback STOPS, no hard cut, ever (phase 3 step 12 decision (j), maintainer 2026-09-28,
 * refined the same day: release only ENDLESS tails). A finite tail rings out in full, however long;
 * a tail that can never end (a delay at |feedback| >= 1, at the master or on an orbit) still
 * ringing `PlaybackEngine.MAX_TAIL_HOLD_SECONDS` (20 s) after the notes ended gets the engine's
 * whole output released by [TailRelease], and the engine is idle only after it; a master swap in
 * flight is waited for; and an engine with nothing left ringing goes exactly as before.
 *
 * The oracle is a CONTROL engine: the same commands, never stopped, so it keeps ringing. The
 * engines are driven block by block through `renderInto` (the dispatcher's own order, minus the
 * house stage, whose limiter would hide the law), and the stopped one must be the control times
 * [ReleaseLaw] from the release's first block, bit for bit.
 */
class EngineStopReleaseSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val holdBlocks = 20 * sampleRate / blockFrames
    val law = ReleaseLaw(sampleRate)

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    /** A dispatcher and its one engine "song", rendered block by block without the house stage. */
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

    fun blip(katalystParams: Map<String, Double>? = null) = ScheduledVoice(
        playbackId = "song",
        startTime = 0.0,
        gateEndTime = 0.05,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5, katalystParams = katalystParams),
        playbackStartTime = 0.0,
    )

    fun masterEvent(name: String, startTime: Double = 0.0) = ScheduledVoice(
        playbackId = "song",
        startTime = startTime,
        gateEndTime = startTime + 1.0,
        data = VoiceData.empty.copy(master = name, control = true),
        playbackStartTime = 0.0,
    )

    /** A delay that sustains itself: feedback 1 never decays. */
    val runaway = KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.6), time = c(0.25), feedback = c(1.0)))

    /**
     * Stops A at block 100 and renders both until A is idle (bounded). Asserts A is B times the
     * release law from the block A's release began, that it began one hold after the notes ended,
     * and that A went idle exactly in the block the gain reached 0. Returns the release's first block.
     */
    fun releasedAgainstControl(a: Rig, b: Rig): Int {
        var lastVoiceBlock = -1
        var releaseAt = -1
        var idleAt = -1
        var ring = 0.0
        val bound = 100 + holdBlocks + law.floorFrame / blockFrames + 400

        for (blk in 0 until bound) {
            if (blk == 100) {
                a.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
            }

            val got = a.block(blk)
            val want = b.block(blk)

            if (b.engine.scheduler.getActiveVoiceCount() != 0) {
                lastVoiceBlock = blk
            }

            if (releaseAt < 0 && a.engine.isReleasing) {
                releaseAt = blk
            }

            for (i in 0 until blockFrames) {
                val weight = if (releaseAt < 0) 1.0 else law.gain((blk - releaseAt) * blockFrames + i)

                if (got[i] != want[i] * weight) {
                    withClue("block $blk sample $i (release from block $releaseAt): got ${got[i]}, want ${want[i]} x $weight") {
                        got[i] shouldBe want[i] * weight
                    }
                }
            }

            if (releaseAt >= 0) {
                ring = maxOf(ring, want.maxOf { abs(it) })
            }

            if (a.engine.isIdle()) {
                idleAt = blk
                break
            }
        }

        withClue("the release began one hold after the notes ended (last voice block $lastVoiceBlock)") {
            releaseAt shouldBeGreaterThan lastVoiceBlock + holdBlocks - 2
            releaseAt shouldBeLessThan lastVoiceBlock + holdBlocks + 2
        }

        withClue("the engine went idle in the block the gain reached 0, not before, not after") {
            idleAt shouldBe releaseAt + law.floorFrame / blockFrames
        }

        withClue("positive control: the control still rang at full level through the release") {
            ring shouldBeGreaterThan 0.05
        }

        return releaseAt
    }

    "a stopped playback whose MASTER tail sustains itself is released after the hold, by the law, then idle" {
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "runaway", dsl = runaway))
            r.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(masterEvent("runaway"), blip())))
        }

        releasedAgainstControl(a, b)

        withClue("the control, never stopped, is never released: its drone is the authored sound") {
            b.engine.isReleasing shouldBe false
            b.engine.isIdle() shouldBe false
        }
    }

    "a stopped playback whose ORBIT tail sustains itself is released after the hold, by the law, then idle" {
        // The owner's feedback-1 delay stays ACTIVE when the owner lapses (nobody configures it
        // off), its charged ring recirculating without loss, so the orbit stays active: before
        // decision (j) such an engine was never idle. The infinite drain countdown is the
        // owner-off row's.
        val params = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(blip(params))))
        }

        releasedAgainstControl(a, b)

        withClue("the control's orbit still rings: it was the release that ended the stopped one") {
            b.engine.cylinders.anyActive() shouldBe true
        }
    }

    "a stopped playback whose orbit owner turned an endless delay OFF (a drain that never ends) is released too" {
        // The second note owns the orbit without a delay, so the feedback-1 ring goes Draining on
        // an infinite countdown: endless in the delay's other state.
        val params = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(blip(params), blip().copy(startTime = 0.2, gateEndTime = 0.25)),
                )
            )
        }

        releasedAgainstControl(a, b)

        withClue("the control's orbit still rings on the drain: it was the release that ended the stopped one") {
            b.engine.cylinders.anyActive() shouldBe true
            b.engine.cylinders.cylinders.single().delay.shouldNotBeNull().sustainsItself() shouldBe true
        }
    }

    "an orbit's endless chain already RELEASED by its swap does not get the whole engine released: a finite echo elsewhere rings out" {
        // Orbit 1: a feedback-1 loop, swapped away at 0.2 s (its capped drain is released from
        // about 20.3 s to 24.8 s). Orbit 2: a finite echo (0.25 s, feedback 0.9) from 1.0 s. The
        // engine's hold ends about 21 s, inside orbit 1's release: nothing ENDLESS is left then
        // (the releasing chain is on its way out), so the echo must ring out, never released.
        val loopParams = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)
        val echoParams = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.9)
        val dry = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.9)))

        fun note(orbit: Int, start: Double, params: Map<String, Double>? = null, katalyst: String? = null) = ScheduledVoice(
            playbackId = "song",
            startTime = start,
            gateEndTime = start + 0.05,
            data = VoiceData.empty.copy(
                sound = "sine", freqHz = 440.0, gain = 0.5, cylinder = orbit, katalystParams = params, katalyst = katalyst,
            ),
            playbackStartTime = 0.0,
        )

        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "dry", dsl = dry))
            r.d.handle(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(note(1, 0.0, loopParams), note(1, 0.2, katalyst = "dry"), note(2, 1.0, echoParams)),
                )
            )
        }

        var lastVoiceBlock = -1
        var releasingAtHoldEnd = false
        var idleAt = -1

        for (blk in 0 until 30000) {
            if (blk == 400) {
                a.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
            }

            val got = a.block(blk)
            val want = b.block(blk)

            if (b.engine.scheduler.getActiveVoiceCount() != 0) {
                lastVoiceBlock = blk
            }

            if (lastVoiceBlock >= 0 && blk == lastVoiceBlock + holdBlocks + 1) {
                val orbit1 = a.engine.cylinders.cylinders.single { it.id == 1 }
                val orbit2 = a.engine.cylinders.cylinders.single { it.id == 2 }

                releasingAtHoldEnd = orbit1.chainSwap.isReleasing && orbit2.isActive
            }

            if (!got.contentEquals(want)) {
                withClue("block $blk: the stopped engine is the control, sample for sample") {
                    got.contentEquals(want) shouldBe true
                }
            }

            withClue("block $blk: nothing endless is left, so the engine is never released") {
                a.engine.isReleasing shouldBe false
            }

            if (a.engine.isIdle()) {
                idleAt = blk
                break
            }
        }

        withClue("the case this row is for: at the hold's end orbit 1's swap was releasing and orbit 2's echo rang") {
            releasingAtHoldEnd shouldBe true
        }

        withClue("idle once the echo had rung out, after the hold") {
            idleAt shouldBeGreaterThan lastVoiceBlock + holdBlocks
        }
    }

    "a stopped playback whose master swap still drains when the hold ends waits for it, then goes without a release" {
        // The master swaps from the runaway loop to a dry chain at 2 s, long after the note: its
        // drain is capped at 20 s and released (the swap's own law), so it settles at about
        // 26.6 s, after the engine's hold (about 20.1 s). The engine must neither drop the swap
        // mid-drain nor release on top of it: it waits, and once the swap has settled nothing
        // sounds, so it is idle then. Its output is the control's throughout.
        val dry = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5)))
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "runaway", dsl = runaway))
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "dry", dsl = dry))
            r.d.handle(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(masterEvent("runaway"), blip(), masterEvent("dry", startTime = 2.0)),
                )
            )
        }

        var idleAt = -1
        var settledAt = -1
        var heldPastHold = false

        for (blk in 0 until 12000) {
            // Stopped once the swap has begun (a stop drops what is scheduled and not yet started).
            if (blk == 800) {
                a.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
            }

            val got = a.block(blk)
            val want = b.block(blk)

            withClue("block $blk: the stopped engine is the control, sample for sample") {
                got.contentEquals(want) shouldBe true
            }

            a.engine.isReleasing shouldBe false

            if (settledAt < 0 && blk > 700 && a.engine.masterBusForTest.isSettled) {
                settledAt = blk
            }

            if (blk > 800 && blk > holdBlocks + 100 && !a.engine.masterBusForTest.isSettled) {
                heldPastHold = true
            }

            if (a.engine.isIdle()) {
                idleAt = blk
                break
            }
        }

        withClue("the swap was still draining after the hold ended (the case this row is for)") {
            heldPastHold shouldBe true
        }

        withClue("idle in the block the swap settled, not before") {
            idleAt shouldBe settledAt
        }
    }

    "a stopped playback with nothing ringing is disposed as before: in the first block it has nothing left" {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }
        val out = StereoBuffer(blockFrames)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(blip())))
        d.renderBlock(cursorFrame = 0.0, out = out)
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))

        val engine = d.engine("song").shouldNotBeNull()
        var soundedBefore = true
        var disposedAt = -1

        for (blk in 1 until 2000) {
            d.renderBlock(cursorFrame = blk * blockFrames.toDouble(), out = out)

            if (!d.activePlaybackIds.contains("song")) {
                disposedAt = blk
                break
            }

            soundedBefore = engine.scheduler.getActiveVoiceCount() != 0 || engine.cylinders.anyActive()
        }

        withClue("disposed, long before any hold, and never released") {
            disposedAt shouldBeGreaterThan 0
            disposedAt shouldBeLessThan holdBlocks
            engine.isReleasing shouldBe false
        }

        withClue("in the block before, it still had sound of its own: no extra hold") {
            soundedBefore shouldBe true
        }
    }

    "a stopped playback scheduled again mid-release: the releasing engine is detached, heard to its end, and returns its units" {
        // Two dispatchers with their own warehouses. The orbit's feedback-1 delay is the endless
        // tail. In A the playback is scheduled again mid-release (a note far in the future, so the
        // fresh engine is silent over the release window); in C it is not, so C's engine simply
        // finishes its release in place. The detached engine must be HEARD: A's output is C's.
        class Fixture {
            val clock = BackendClock(sampleRate)
            val commLink = KlangCommLink(capacity = 1024).backend
            val warehouse = ResourceWarehouse(sampleRate = sampleRate, blockFrames = blockFrames, samples = SampleStore(commLink))
            val d = PlaybackEngineDispatcher(
                context = AudioBackendContext(
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                    commLink = commLink,
                    ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
                    katalystRegistry = KatalystRegistry(),
                    clock = clock,
                    performanceTimeMs = { 0.0 },
                    warehouse = warehouse,
                ),
                clock = clock,
            ).also { it.setBackendStartTime(0.0) }
            val out = StereoBuffer(blockFrames)

            fun render(blk: Int): DoubleArray {
                d.renderBlock(cursorFrame = blk * blockFrames.toDouble(), out = out)

                return out.interleavedCopy()
            }
        }

        val params = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)
        val a = Fixture()
        val c = Fixture()

        for (f in listOf(a, c)) {
            f.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(blip(params))))
        }

        var blk = 0
        while (blk < 100) {
            a.render(blk)
            c.render(blk)
            blk++
        }

        for (f in listOf(a, c)) {
            f.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        }

        val stopped = a.d.engine("song").shouldNotBeNull()

        while (!stopped.isReleasing && blk < 100 + holdBlocks + 10) {
            a.render(blk)
            c.render(blk)
            blk++
        }

        stopped.isReleasing shouldBe true

        val later = 1000.0
        a.d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(blip().copy(startTime = later, gateEndTime = later + 0.05)),
            )
        )

        withClue("the id has a fresh engine; the releasing one is detached and still renders") {
            a.d.engine("song").shouldNotBeNull() shouldNotBeSameInstanceAs stopped
            a.d.detachedCountForTest shouldBe 1
        }

        withClue("positive control: the ring was on the orbit's shelf-rented ring, not returned yet") {
            a.warehouse.sized.shelfCount shouldBe 0
        }

        var loud = 0
        var guard = 0
        while (a.d.detachedCountForTest > 0 && guard < law.floorFrame / blockFrames + 10) {
            val got = a.render(blk)
            val want = c.render(blk)

            withClue("block $blk: the detached release is heard, exactly as the engine that was not detached") {
                got.contentEquals(want) shouldBe true
            }

            if (want.any { abs(it) > 0.03 }) { // about 1000 16-bit counts
                loud++
            }

            blk++
            guard++
        }

        withClue("the detached engine finished its release and was disposed, its ring back on the shelf") {
            a.d.detachedCountForTest shouldBe 0
            stopped.isIdle() shouldBe true
            a.warehouse.sized.shelfCount shouldBe 1
        }

        withClue("positive control: the release window was loud, so the comparison is not of silence") {
            loud shouldBeGreaterThan 10
        }
    }

    /**
     * Stops A at block [stopAt] and renders A and B until A is idle (bounded by [bound]): A must be
     * B sample for sample and never released. Returns the block A went idle, or -1.
     */
    fun ringsOutAgainstControl(a: Rig, b: Rig, stopAt: Int, bound: Int): Int {
        for (blk in 0 until bound) {
            if (blk == stopAt) {
                a.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
            }

            val got = a.block(blk)
            val want = b.block(blk)

            if (!got.contentEquals(want)) {
                withClue("block $blk: the stopped engine is the control, sample for sample") {
                    got.contentEquals(want) shouldBe true
                }
            }

            withClue("block $blk: a finite tail is never released") {
                a.engine.isReleasing shouldBe false
            }

            if (a.engine.isIdle()) {
                return blk
            }
        }

        return -1
    }

    "a stopped playback whose FINITE orbit tail outlasts the hold rings out in full, then idle" {
        // Feedback 0.9 at 0.25 s: still well above -100 dBFS 20 s after the note, but it ends.
        val params = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.9)
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(blip(params))))
        }

        val idleAt = ringsOutAgainstControl(a, b, stopAt = 100, bound = 30000)

        withClue("idle, and only after the hold: the tail was still ringing when the hold ended") {
            idleAt shouldBeGreaterThan 100 + holdBlocks
        }

        withClue("idle because it was quiet: the orbit had gone silent in the control too") {
            b.engine.cylinders.anyActive() shouldBe false
        }
    }

    "a stopped playback whose FINITE master tail outlasts the hold rings out in full, then idle" {
        val echo = KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.6), time = c(0.25), feedback = c(0.9)))
        val a = Rig()
        val b = Rig()

        for (r in listOf(a, b)) {
            r.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "echo", dsl = echo))
            r.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(masterEvent("echo"), blip())))
        }

        val idleAt = ringsOutAgainstControl(a, b, stopAt = 100, bound = 30000)

        withClue("idle, and only after the hold: the tail was still ringing when the hold ended") {
            idleAt shouldBeGreaterThan 100 + holdBlocks
        }
    }

    "a stopped playback is not idle while a master swap is in flight, even with no declared tail" {
        // A lookahead compressor has no reverb or delay, so the bus reports nothing ringing; its
        // swap still fades (and drains its lookahead) after the stop. Idle only once it settled.
        val lim = KatalystDsl.of(KatalystStageDsl.Compressor(lookahead = 0.005))
        val dry = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5)))
        val a = Rig()

        a.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "lim", dsl = lim))
        a.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "dry", dsl = dry))
        a.d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(masterEvent("lim"), blip(), masterEvent("dry", startTime = 1.0)),
            )
        )

        var unsettledAfterStop = 0
        var idleAt = -1
        var stopAt = -1

        for (blk in 0 until 2000) {
            a.block(blk)

            if (stopAt < 0 && !a.engine.masterBusForTest.isSettled) {
                // The swap has begun: stop now (a stop drops what is not yet started).
                a.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
                stopAt = blk
            }

            if (stopAt >= 0 && blk > stopAt) {
                if (!a.engine.masterBusForTest.isSettled) {
                    unsettledAfterStop++

                    withClue("block $blk: the swap is in flight, the engine is not idle") {
                        a.engine.isIdle() shouldBe false
                    }
                } else if (a.engine.isIdle()) {
                    idleAt = blk
                    break
                }
            }
        }

        withClue("the case this row is for: the swap ran on after the stop, the orbits quiet, nothing ringing") {
            unsettledAfterStop shouldBeGreaterThan 5
            a.engine.cylinders.anyActive() shouldBe false
            a.engine.masterBusForTest.isRinging shouldBe false
        }

        withClue("idle once the swap settled") {
            idleAt shouldBeGreaterThan stopAt
        }
    }
})
