/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.PlaybackEngine.Phase
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The engine's end of life as one [PlaybackEngine.Phase] (tidy-up step 9, audit item C3.1), one row per transition
 * of the table in the `PlaybackEngine` KDoc: stop, resume, the release of an endless tail after the hold, finite
 * tails ringing out, the detach of a releasing engine, disposal and its order. The release LAW (bit for bit against
 * a control) is `EngineStopReleaseSpec`'s; here the phases and the dispatcher's two orders.
 */
class PlaybackEnginePhaseSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val holdBlocks = 20 * sampleRate / blockFrames
    val law = ReleaseLaw(sampleRate)

    /** A delay at feedback 1 on the voice's orbit: a tail that never ends. */
    val endless = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0)

    /** Feedback 0.9 at 0.25 s: still ringing 20 s after the note, but it ends. */
    val finite = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.9)

    class Rig {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = 44100,
            blockFrames = 128,
            commLink = KlangCommLink(capacity = 4096).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

        private val clock = d.clockForTest as BackendClock
        private val out = StereoBuffer(128)
        private val alone = StereoBuffer(128)
        var block = 0

        /** [blocks] blocks through the dispatcher: render, house stage, the disposal sweep. */
        fun render(blocks: Int = 1) {
            repeat(blocks) {
                d.renderBlock(cursorFrame = block * 128.0, out = out)
                block++
            }
        }

        /** One block of [engine] alone, without the dispatcher (no sweep), on the same clock; returns its peak. */
        fun renderAlone(engine: PlaybackEngine): Double {
            clock.cursorFrame = block * 128.0
            alone.clear()
            engine.renderInto(alone, block * 128.0)
            clock.cursorFrame = (block + 1) * 128.0
            block++

            return alone.peak()
        }

        fun engine(pid: String): PlaybackEngine = d.engine(pid).shouldNotBeNull()

        fun schedule(
            pid: String,
            orbit: Int = 0,
            startSec: Double = 0.0,
            gateSec: Double = 0.05,
            params: Map<String, Double>? = null,
        ) {
            d.handle(
                KlangCommLink.Cmd.ScheduleVoice(
                    playbackId = pid,
                    voice = ScheduledVoice(
                        playbackId = pid,
                        startTime = startSec,
                        gateEndTime = startSec + gateSec,
                        data = VoiceData.empty.copy(
                            sound = "sine",
                            freqHz = 440.0,
                            gain = 0.5,
                            cylinder = orbit,
                            katalystParams = params,
                        ),
                        playbackStartTime = 0.0,
                    ),
                )
            )
        }

        fun stop(pid: String) = d.handle(KlangCommLink.Cmd.Cleanup(playbackId = pid))

        /** Renders until [pid]'s engine is gone (bounded); returns the blocks it took, or -1. */
        fun renderUntilGone(pid: String, bound: Int): Int {
            for (n in 1..bound) {
                render()

                if (d.engine(pid) == null) {
                    return n
                }
            }

            return -1
        }

        /** Renders until [engine]'s phase is past Stopped (bounded). */
        fun renderUntilReleasing(engine: PlaybackEngine, bound: Int) {
            var n = 0

            while (engine.phase == Phase.Stopped && n < bound) {
                render()
                n++
            }

            engine.phase shouldBe Phase.Releasing
        }
    }

    "Playing until the stop, then Stopped; a second stop changes nothing, not even the disposal order" {
        val rig = Rig()

        rig.schedule(pid = "a", gateSec = 5.0)
        rig.schedule(pid = "b", gateSec = 5.0)
        rig.render()

        val a = rig.engine("a")
        val b = rig.engine("b")

        a.phase shouldBe Phase.Playing
        rig.d.endingForTest shouldBe emptyList()

        rig.stop("a")
        rig.stop("b")

        a.phase shouldBe Phase.Stopped
        rig.d.endingForTest shouldBe listOf(a, b)

        rig.stop("a")

        withClue("the second stop of a: still Stopped, still first in line") {
            a.phase shouldBe Phase.Stopped
            rig.d.endingForTest shouldBe listOf(a, b)
        }
    }

    "a stopped engine scheduled again resumes: the same engine, Playing, and never disposed for that stop" {
        val rig = Rig()

        rig.schedule(pid = "song", gateSec = 1.0)
        rig.render()

        val engine = rig.engine("song")

        rig.stop("song")
        engine.phase shouldBe Phase.Stopped

        rig.schedule(pid = "song", gateSec = 0.05)

        rig.engine("song") shouldBeSameInstanceAs engine
        engine.phase shouldBe Phase.Playing
        rig.d.endingForTest shouldBe emptyList()

        // Long after every note and tail has ended: a playing engine stays.
        rig.render(2000)

        withClue("quiet, but playing: kept") {
            engine.isIdle() shouldBe true
            rig.d.engine("song") shouldBeSameInstanceAs engine
            engine.phase shouldBe Phase.Playing
        }
    }

    "a playing engine is never released, an endless tail past the hold notwithstanding" {
        val rig = Rig()

        rig.schedule(pid = "song", params = endless)
        rig.render(holdBlocks + 500)

        val engine = rig.engine("song")

        engine.phase shouldBe Phase.Playing
        engine.cylinders.anySustainsItself() shouldBe true
    }

    "an endless tail: Stopped through the hold, then Releasing, then Released in the block it falls under its floor" {
        val rig = Rig()

        rig.schedule(pid = "song", params = endless)

        val engine = rig.engine("song")
        var lastVoiceBlock = -1
        var releasingAt = -1
        var releasedAt = -1

        while (rig.block < 100) {
            val blk = rig.block

            rig.render()

            if (engine.scheduler.getActiveVoiceCount() != 0) {
                lastVoiceBlock = blk
            }
        }

        rig.stop("song")

        while (releasedAt < 0 && rig.block < 100 + holdBlocks + law.floorFrame / blockFrames + 400) {
            val blk = rig.block
            val before = engine.phase

            rig.renderAlone(engine)

            if (engine.scheduler.getActiveVoiceCount() != 0) {
                lastVoiceBlock = blk
            }

            if (before != engine.phase) {
                when (engine.phase) {
                    Phase.Releasing -> {
                        releasingAt = blk
                    }

                    Phase.Released -> {
                        releasedAt = blk
                    }

                    Phase.Playing, Phase.Stopped, Phase.Disposed -> {
                        throw AssertionError("block $blk: a stopped engine went to ${engine.phase}")
                    }
                }
            } else if (releasingAt < 0) {
                withClue("block $blk: held, not yet released") { engine.isIdle() shouldBe false }
            }
        }

        withClue("Releasing one hold after the notes ended (last voice block $lastVoiceBlock)") {
            releasingAt shouldBeGreaterThan lastVoiceBlock + holdBlocks - 2
            releasingAt shouldBeLessThan lastVoiceBlock + holdBlocks + 2
        }

        withClue("Released in the block the gain reached 0") {
            releasedAt shouldBe releasingAt + law.floorFrame / blockFrames
            engine.isIdle() shouldBe true
        }

        withClue("Released stays Released, and what it renders is not heard: the endless tail still rings under it") {
            rig.renderAlone(engine) shouldBe 0.0
            engine.phase shouldBe Phase.Released
            engine.cylinders.anySustainsItself() shouldBe true
        }

        rig.render()

        withClue("the dispatcher disposes it at the end of its next block") {
            rig.d.engine("song") shouldBe null
            engine.phase shouldBe Phase.Disposed
        }
    }

    "finite tails ring out: Stopped through the hold and past it, never released, Disposed once quiet" {
        val rig = Rig()

        rig.schedule(pid = "song", params = finite)
        rig.render(100)

        val engine = rig.engine("song")

        rig.stop("song")

        var blocks = 0

        while (rig.d.engine("song") != null && blocks < 30000) {
            withClue("block ${rig.block}: never released") { engine.phase shouldBe Phase.Stopped }
            rig.render()
            blocks++
        }

        withClue("disposed, and only after the hold: the tail rang past it") {
            rig.d.engine("song") shouldBe null
            blocks shouldBeGreaterThan holdBlocks
            engine.phase shouldBe Phase.Disposed
        }
    }

    "a stopped engine with nothing left is Disposed in its first quiet block" {
        val rig = Rig()

        rig.schedule(pid = "song")
        rig.render(200)

        val engine = rig.engine("song")

        engine.isIdle() shouldBe true
        rig.stop("song")
        rig.render()

        rig.d.engine("song") shouldBe null
        engine.phase shouldBe Phase.Disposed
    }

    "scheduled again mid-release: detached and still Releasing, first in the disposal order; the id plays on fresh" {
        val rig = Rig()

        rig.schedule(pid = "song", params = endless)
        rig.render(100)

        val releasing = rig.engine("song")

        rig.schedule(pid = "other", gateSec = 60.0)
        rig.stop("other")
        rig.stop("song")
        rig.renderUntilReleasing(engine = releasing, bound = holdBlocks + 200)

        rig.d.endingForTest shouldBe listOf(rig.engine("other"), releasing)

        rig.stop("song")

        withClue("a stop while releasing changes nothing") {
            releasing.phase shouldBe Phase.Releasing
            rig.d.endingForTest shouldBe listOf(rig.engine("other"), releasing)
        }

        // Far in the future, so the fresh engine is silent over the release window.
        rig.schedule(pid = "song", startSec = 1000.0)

        val fresh = rig.engine("song")

        withClue("detached: the id has a fresh engine, the releasing one renders on, first in line") {
            fresh shouldNotBeSameInstanceAs releasing
            fresh.phase shouldBe Phase.Playing
            releasing.phase shouldBe Phase.Releasing
            rig.d.detachedCountForTest shouldBe 1
            rig.d.endingForTest.first() shouldBeSameInstanceAs releasing
        }

        releasing.resume()

        withClue("a release has no way back: a resume changes nothing") { releasing.phase shouldBe Phase.Releasing }

        var guard = 0

        while (rig.d.detachedCountForTest > 0 && guard < law.floorFrame / blockFrames + 10) {
            rig.render()
            guard++
        }

        withClue("Disposed once its release ran out; the fresh engine stays") {
            releasing.phase shouldBe Phase.Disposed
            rig.d.engine("song") shouldBeSameInstanceAs fresh
            fresh.phase shouldBe Phase.Playing
        }
    }

    "cleanupHard disposes at once, a playing engine and a stopped one alike" {
        val rig = Rig()

        rig.schedule(pid = "a", gateSec = 5.0)
        rig.schedule(pid = "b", gateSec = 5.0)
        rig.render()

        val a = rig.engine("a")
        val b = rig.engine("b")

        rig.stop("b")
        rig.d.cleanupHard("a")
        rig.d.cleanupHard("b")

        a.phase shouldBe Phase.Disposed
        b.phase shouldBe Phase.Disposed
        rig.d.renderedEngineCountForTest shouldBe 0
        rig.d.endingForTest shouldBe emptyList()
    }

    "Disposed is the end: a stop or a resume changes nothing, and it is idle" {
        val rig = Rig()

        rig.schedule(pid = "song", gateSec = 5.0)
        rig.render()

        val engine = rig.engine("song")

        rig.d.cleanupHard("song")
        engine.stop()
        engine.phase shouldBe Phase.Disposed
        engine.resume()
        engine.phase shouldBe Phase.Disposed
        engine.isIdle() shouldBe true
    }

    "engines idle in one block are disposed in stop order: their cylinders reach the shelf in that order" {
        val rig = Rig()

        rig.schedule(pid = "a")
        rig.schedule(pid = "b")
        rig.schedule(pid = "c")
        rig.render(200)

        val cylinderOf = listOf("a", "b", "c").associateWith { rig.engine(it).cylinders.cylinders.single() }

        // Stopped c, a, b, all quiet already: the next block's sweep disposes them in that order, so b's cylinder
        // is the last one shelved and the first one rented.
        rig.stop("c")
        rig.stop("a")
        rig.stop("b")
        rig.render()

        listOf("a", "b", "c").forEach { rig.d.engine(it) shouldBe null }

        rig.schedule(pid = "next", orbit = 0)
        rig.schedule(pid = "next", orbit = 1, startSec = 0.0005)
        rig.schedule(pid = "next", orbit = 2, startSec = 0.001)
        rig.render()

        rig.engine("next").cylinders.cylinders shouldBe listOf(cylinderOf["b"], cylinderOf["a"], cylinderOf["c"])
    }

    "detached engines are disposed newest first: x detached before y, y goes first" {
        val rig = Rig()

        rig.schedule(pid = "x", orbit = 1, params = endless)
        rig.schedule(pid = "y", orbit = 2, params = endless)
        rig.render(100)

        val x = rig.engine("x")
        val y = rig.engine("y")
        val cylinderOfX = x.cylinders.cylinders.single()

        rig.stop("x")
        rig.stop("y")
        rig.renderUntilReleasing(engine = x, bound = holdBlocks + 200)
        y.phase shouldBe Phase.Releasing

        rig.schedule(pid = "x", startSec = 1000.0)
        rig.schedule(pid = "y", startSec = 1000.0)

        rig.d.endingForTest shouldBe listOf(y, x)

        var guard = 0

        while (rig.d.detachedCountForTest > 0 && guard < law.floorFrame / blockFrames + 10) {
            rig.render()
            guard++
        }

        withClue("both released in the same block, disposed y then x: x's cylinder is on top of the shelf") {
            x.phase shouldBe Phase.Disposed
            y.phase shouldBe Phase.Disposed

            rig.schedule(pid = "next", orbit = 0)
            rig.render()

            rig.engine("next").cylinders.cylinders.single() shouldBeSameInstanceAs cylinderOfX
        }
    }

    "without a master or a release, an engine sums its orbits straight into the shared mix, not through its bus" {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            phasePoolSeed = 1,
        )
        val first = PlaybackEngine.create(context = context, playbackId = "first")
        val second = PlaybackEngine.create(context = context, playbackId = "second")

        fun voice(pid: String, orbit: Int, freqHz: Double) = ScheduledVoice(
            playbackId = pid,
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = freqHz, gain = 0.3, cylinder = orbit),
            playbackStartTime = 0.0,
        )

        first.scheduler.scheduleVoice(voice(pid = "first", orbit = 0, freqHz = 331.0))
        second.scheduler.scheduleVoice(voice(pid = "second", orbit = 1, freqHz = 443.0))
        second.scheduler.scheduleVoice(voice(pid = "second", orbit = 2, freqHz = 557.0))

        val mix = StereoBuffer(blockFrames)
        var throughABus = 0

        for (block in 0 until 100) {
            mix.clear()
            first.renderInto(mix, clock.cursorFrame)

            val before = mix.left.copyOf()

            second.renderInto(mix, clock.cursorFrame)

            val orbits = second.cylinders.cylinders

            for (i in 0 until blockFrames) {
                val o1 = orbits[0].mixBuffer.left[i]
                val o2 = orbits[1].mixBuffer.left[i]
                val straight = (before[i] + o1) + o2

                if (mix.left[i].toRawBits() != straight.toRawBits()) {
                    withClue("block $block sample $i") { mix.left[i] shouldBe straight }
                }

                if ((before[i] + (o1 + o2)).toRawBits() != straight.toRawBits()) {
                    throughABus++
                }
            }

            clock.cursorFrame += blockFrames
        }

        withClue("positive control: summed through a bus first, some samples would differ") {
            throughABus shouldBeGreaterThan 0
        }
    }
})
