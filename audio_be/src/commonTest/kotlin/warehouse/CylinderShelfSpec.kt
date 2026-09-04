/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.WarmupRunner
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.master.MasterRegistry
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * Cylinders join the warehouse (maintainer, 2026-09-04, after the Fairphone measurement: the first
 * frame of Der Schmetterling built eight cylinders and four reverb networks at once and killed the
 * playback). A cylinder is built by the warmup, retired and shelved when its engine is disposed,
 * and taken from the shelf by the next engine's first voice on an orbit. `CylinderUnits`.
 *
 * And the warmup is BUCKETED: sixteen orbits, one per block, so no single render frame builds more
 * than one cylinder — the stall that would otherwise merely move from the first song's first frame
 * into the warmup's.
 */
class CylinderShelfSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    class Fixture(val dispatcher: PlaybackEngineDispatcher, val warehouse: ResourceWarehouse, val commLink: KlangCommLink) {
        val out = ShortArray(blockFrames * 2)
        fun render(blocks: Int): Double {
            var peak = 0.0
            repeat(blocks) {
                dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
                for (i in out.indices) peak = maxOf(peak, abs(out[i].toDouble()))
            }
            return peak
        }
    }

    fun fixture(): Fixture {
        val clock = BackendClock(sampleRate)
        val warehouse = ResourceWarehouse(sampleRate = sampleRate, blockFrames = blockFrames)
        val commLink = KlangCommLink(capacity = 4096)
        val context = AudioBackendContext(
            sampleRate = sampleRate, blockFrames = blockFrames, commLink = commLink.backend,
            sampleStore = SampleStore(commLink.backend),
            ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
            pipelineRegistry = PipelineRegistry(), masterRegistry = MasterRegistry(),
            clock = clock, performanceTimeMs = { 0.0 }, warehouse = warehouse,
        )
        val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also { it.setBackendStartTime(0.0) }
        return Fixture(dispatcher, warehouse, commLink)
    }

    fun wetVoice(pid: String, cylinder: Int, sound: String = "sine", start: Double = 0.0) = ScheduledVoice(
        playbackId = pid, startTime = start, gateEndTime = start + 0.05,
        data = VoiceData.empty.copy(
            sound = sound, freqHz = 330.0, cylinder = cylinder,
            delay = 0.5, delayTime = 0.3, delayFeedback = 0.3, room = 0.5, roomSize = 0.6,
            phaser = 0.5, phaserDepth = 0.6, cutoff = 1500.0, resonance = 0.2,
        ),
        playbackStartTime = 0.0,
    )

    // ── The shelf ────────────────────────────────────────────────────────────────────────────────

    "a disposed engine returns its cylinders, retired; the next engine's first voice takes one — same instance, new id" {
        val f = fixture()
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "a", voice = wetVoice("a", cylinder = 5)))
        f.render(4) shouldBeGreaterThan 0.0
        val used = f.dispatcher.engine("a")!!.cylinders.cylinders.single()
        used.id shouldBe 5
        f.warehouse.cylinders.allocations shouldBe 1

        f.dispatcher.cleanupHard("a")

        f.warehouse.cylinders.idleCount shouldBe 1
        used.isActive shouldBe false
        used.delay.delayLine.shouldBeNull() // the ring went to ITS shelf, not idle inside the cylinder
        used.reverb.reverb.shouldBeNull()
        f.warehouse.sized.shelfCount shouldBe 1
        f.warehouse.reverbs.idleCount shouldBe 1

        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "b", voice = wetVoice("b", cylinder = 2)))
        f.render(4) shouldBeGreaterThan 0.0

        val taken = f.dispatcher.engine("b")!!.cylinders.cylinders.single()
        taken shouldBeSameInstanceAs used
        taken.id shouldBe 2
        f.warehouse.cylinders.allocations shouldBe 1 // nothing built
        f.warehouse.cylinders.hits shouldBe 1
        f.warehouse.sized.hits shouldBe 1
        f.warehouse.reverbs.hits shouldBe 1
    }

    "a song on returned cylinders is BIT-IDENTICAL to the same song on fresh ones — no state crosses playbacks" {
        // The strongest form of "retired": playback A is loud and wet with different settings on
        // every orbit; playback B then runs on A's cylinders and must equal B on a fresh backend.
        fun songB(f: Fixture): DoubleArray {
            // Scheduled half a block after "now" on each backend, so both runs see the same onset
            // alignment whatever the clock reads (the reused backend has already rendered song A).
            val start = f.dispatcher.clockForTest.secAt(f.dispatcher.clockForTest.cursorFrame) + 0.5 * blockFrames / sampleRate
            val voices = (0 until 4).map { orbit ->
                wetVoice("b", cylinder = orbit, sound = listOf("saw", "square", "sine", "triangle")[orbit], start = start)
            }
            f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "b", voices = voices))
            val samples = DoubleArray(blockFrames * 2 * 60)
            repeat(60) { block ->
                f.dispatcher.renderBlock(f.dispatcher.clockForTest.cursorFrame, f.out)
                for (i in f.out.indices) samples[block * f.out.size + i] = f.out[i].toDouble()
            }
            return samples
        }

        // Both backends reach song B at the SAME clock position (44 blocks in), so the scheduled
        // time's frame conversion rounds identically; the only difference left is the cylinders.
        val fresh = fixture()
        fresh.render(44) shouldBe 0.0
        val reference = songB(fresh)

        val reused = fixture()
        val loud = (0 until 4).map { orbit ->
            ScheduledVoice(
                playbackId = "a", startTime = 0.0, gateEndTime = 0.2,
                data = VoiceData.empty.copy(
                    sound = "supersaw", freqHz = 110.0 + 50.0 * orbit, cylinder = orbit, gain = 2.0,
                    delay = 0.9, delayTime = 0.05 + 0.1 * orbit, delayFeedback = 0.8,
                    room = 0.9, roomSize = 0.95, phaser = 0.9, phaserDepth = 0.9, cutoff = 400.0 + 900.0 * orbit, resonance = 0.9,
                ),
                playbackStartTime = 0.0,
            )
        }
        reused.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "a", voices = loud))
        reused.render(40) shouldBeGreaterThan 0.0
        reused.dispatcher.cleanupHard("a")
        reused.warehouse.cylinders.idleCount shouldBe 4
        // The dispatcher-level master stage is NOT under test: its limiter holds song A in its
        // lookahead line and its envelope for a while. Reset it the way the warmup does before real
        // playback, so what remains is exactly the cylinders' business.
        reused.dispatcher.resetPostChain()
        withClue("residue after hard cleanup + post-chain reset, before song B") { reused.render(4) shouldBe 0.0 }

        val onReturned = songB(reused)

        reused.warehouse.cylinders.hits shouldBe 4
        reused.warehouse.cylinders.allocations shouldBe 4 // from playback A only
        var energy = 0.0
        for (i in reference.indices) {
            withClue("sample $i (block ${i / (blockFrames * 2)}): reference ${reference[i]} vs returned ${onReturned[i]}") {
                onReturned[i] shouldBe reference[i]
            }
            energy += abs(reference[i])
        }
        energy shouldBeGreaterThan 1.0 // positive control
    }

    "the shelf holds at most maxIdle cylinders and refuses a double return" {
        val rings = SizedBuffers.forRings(sampleRate)
        val units = CylinderUnits(blockFrames, sampleRate, rings, ReverbUnits(sampleRate), maxIdle = 2)
        val a = units.rent(0, 10)
        val b = units.rent(1, 10)
        val c = units.rent(2, 10)
        units.allocations shouldBe 3

        units.giveBack(a)
        units.giveBack(a)
        units.doubleReturns shouldBe 1
        units.idleCount shouldBe 1

        units.giveBack(b)
        units.giveBack(c)
        units.idleCount shouldBe 2
        units.dropped shouldBe 1

        units.rent(7, 10) shouldBeSameInstanceAs b
        units.rent(8, 10).let { it shouldBeSameInstanceAs a; it.id shouldBe 8 }
        units.hits shouldBe 2
    }

    // ── The warmup stocks the shelves, one orbit per block ──────────────────────────────────────

    "after warmup the shelves hold 16 cylinders, 16 rings and 16 networks, and the first song builds nothing" {
        val f = fixture()
        val warmup = WarmupRunner(sampleRate = sampleRate, dispatcher = f.dispatcher, feedback = f.commLink.backend)
        warmup.start()

        val builtPerBlock = mutableListOf<Int>()
        while (warmup.isWarming) {
            // A warmup that never becomes ready (housekeeping missing) must FAIL, not hang the suite.
            (builtPerBlock.size < 400) shouldBe true
            val before = f.warehouse.cylinders.allocations
            f.render(1)
            builtPerBlock.add(f.warehouse.cylinders.allocations - before)
            warmup.tick()
        }

        // BackendReady went out, and only after the shelves were stocked AND zeroed: the units
        // come back dirty (an O(1) return) and the dispatcher's per-block housekeeping clears them
        // a slice at a time; the warmup holds the ready signal until that is done (review round 3).
        val feedback = generateSequence { f.commLink.frontend.feedback.receive() }.toList()
        feedback.last().shouldBeInstanceOf<KlangCommLink.Feedback.BackendReady>()
        f.dispatcher.engine(WarmupRunner.WARMUP_PLAYBACK_ID).shouldBeNull()
        f.warehouse.cylinders.idleCount shouldBe WarmupRunner.WARMUP_ORBITS
        f.warehouse.sized.shelfCount shouldBe WarmupRunner.WARMUP_ORBITS
        f.warehouse.reverbs.idleCount shouldBe WarmupRunner.WARMUP_ORBITS
        f.warehouse.isClean shouldBe true
        (f.warehouse.sized.housekeptFrames > 0.0) shouldBe true // zeroed by housekeeping, not at return
        f.warehouse.reverbs.housekeptUnits shouldBe WarmupRunner.WARMUP_ORBITS
        // The whole warmup took longer than the voices alone: the teardown is bucketed too.
        (builtPerBlock.size > WarmupRunner.WARMUP_ORBITS + WarmupRunner.TAIL_BLOCKS) shouldBe true
        // Bucketed: never more than one cylinder built in one render frame.
        (builtPerBlock.max() <= 1) shouldBe true
        builtPerBlock.sum() shouldBe WarmupRunner.WARMUP_ORBITS

        // The first real song: eight wet orbits, every sound the warmup rotated through.
        val allocationsAfterWarmup = Triple(f.warehouse.cylinders.allocations, f.warehouse.sized.allocations, f.warehouse.reverbs.allocations)
        val song = (0 until 8).map { orbit -> wetVoice("song", cylinder = orbit, sound = WarmupRunner.WARMUP_SOUNDS[orbit % 5]) }
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = song))
        f.render(8) shouldBeGreaterThan 0.0

        Triple(f.warehouse.cylinders.allocations, f.warehouse.sized.allocations, f.warehouse.reverbs.allocations) shouldBe allocationsAfterWarmup
        f.warehouse.cylinders.hits shouldBe 8
        f.warehouse.sized.hits shouldBe 8
        f.warehouse.reverbs.hits shouldBe 8
        // ...and none of those hits had to zero anything on the spot: the shelves were clean.
        f.warehouse.sized.syncCleans shouldBe 0
        f.warehouse.reverbs.syncCleans shouldBe 0
    }

    "BackendReady is held while the shelves are still dirty — a warmup that ends before housekeeping is not ready" {
        val f = fixture()
        val warmup = WarmupRunner(sampleRate = sampleRate, dispatcher = f.dispatcher, feedback = f.commLink.backend)
        warmup.start()

        var readyAt = -1
        var block = 0
        var dirtyWhenReady = false
        while (warmup.isWarming) {
            (block < 400) shouldBe true // never ready = a failure, not a hang
            f.render(1)
            block++
            val stillWarming = warmup.tick()
            if (!stillWarming && readyAt < 0) {
                readyAt = block
                dirtyWhenReady = !f.warehouse.isClean
            }
        }

        dirtyWhenReady shouldBe false
        // And it was NOT ready on the disposal tick itself: sixteen dirty rings need sixteen slices.
        (readyAt > WarmupRunner.WARMUP_ORBITS + WarmupRunner.TAIL_BLOCKS + 8) shouldBe true
    }

    "the clean-shelf wait is BOUNDED — a warehouse that never gets clean still becomes ready" {
        // The shelf being clean is an optimisation (rent zeroes a dirty buffer itself), and the
        // warehouse is shared state other playbacks return into. An unbounded wait would keep the
        // output silenced while the frontend gives up and starts cold (review round 4).
        val f = fixture()
        // The warmup's own sixteen dirty rings and networks need sixteen housekeeping blocks; a
        // wait of two cannot get there, so the cap is what ends it.
        val warmup = WarmupRunner(sampleRate = sampleRate, dispatcher = f.dispatcher, feedback = f.commLink.backend, maxCleanWaitBlocks = 2)
        warmup.start()

        var block = 0
        while (warmup.isWarming) {
            (block < 400) shouldBe true
            f.render(1)
            block++
            warmup.tick()
        }

        block shouldBe WarmupRunner.WARMUP_ORBITS + WarmupRunner.TAIL_BLOCKS + 2
        warmup.readyWhileDirty shouldBe true
        f.warehouse.isClean shouldBe false
        generateSequence { f.commLink.frontend.feedback.receive() }.toList().last().shouldBeInstanceOf<KlangCommLink.Feedback.BackendReady>()
    }

    "the warmup reaches the phaser, compressor, body and vowel constructors too" {
        val f = fixture()
        val warmup = WarmupRunner(sampleRate = sampleRate, dispatcher = f.dispatcher, feedback = f.commLink.backend)
        warmup.start()
        repeat(WarmupRunner.WARMUP_ORBITS + WarmupRunner.TAIL_BLOCKS - 1) { f.render(1); warmup.tick() }

        val cylinders = f.dispatcher.engine(WarmupRunner.WARMUP_PLAYBACK_ID).shouldNotBeNull().cylinders.cylinders.sortedBy { it.id }
        cylinders[0].phaser.phaser.depth shouldBeGreaterThan 0.0
        cylinders[1].compressor.compressor.shouldNotBeNull()
        cylinders[2].body.isEngaged shouldBe true
        cylinders[3].vowel.isEngaged shouldBe true
    }

    "every warmup voice actually sounds through its orbit — the warmed paths are the real ones" {
        // A warmup voice that is dropped (late admission, unknown sound) warms nothing and rents
        // nothing; the shelf counts above would still pass if only SOME orbits rendered. Each
        // orbit's cylinder must have been active with a ring AND a network while warming.
        val f = fixture()
        val warmup = WarmupRunner(sampleRate = sampleRate, dispatcher = f.dispatcher, feedback = f.commLink.backend)
        warmup.start()
        // One block short of the disposal tick: every orbit has rented, the engine is still alive.
        repeat(WarmupRunner.WARMUP_ORBITS + WarmupRunner.TAIL_BLOCKS - 1) { f.render(1); warmup.tick() }

        val engine = f.dispatcher.engine(WarmupRunner.WARMUP_PLAYBACK_ID).shouldNotBeNull()
        engine.cylinders.cylinders.size shouldBe WarmupRunner.WARMUP_ORBITS
        engine.cylinders.cylinders.forEach { c ->
            c.isActive shouldBe true
            c.delay.delayLine.shouldNotBeNull()
            c.reverb.reverb.shouldNotBeNull()
        }
        engine.scheduler.droppedVoiceCount(WarmupRunner.WARMUP_PLAYBACK_ID) shouldBe 0
    }
})
