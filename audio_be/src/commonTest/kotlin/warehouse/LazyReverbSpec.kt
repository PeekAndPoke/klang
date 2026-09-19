/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystReverbEffect
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.master.MasterBus
import io.peekandpoke.klang.audio_be.master.MasterChain
import io.peekandpoke.klang.audio_be.master.MasterRegistry
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import kotlin.math.abs

/**
 * Resource warehouse step 2d: a reverb network exists only once an owner asks for `reverb`, rented
 * from the backend's one unit shelf, and refused gracefully. A Freeverb unit is ~200 KB; every
 * `Cylinder` used to build one in its constructor, eight of them per playback before any note.
 * `docs/plans/resource-warehouse.md`.
 */
class LazyReverbSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // Holds the allocator lambda rather than implementing the function type (forbidden on Kotlin/JS).
    class Recording(var failing: Boolean = false) {
        var asked = 0
        val allocate: (Int) -> Reverb? = { sampleRate ->
            asked++
            if (failing) null else Reverb(sampleRate)
        }
    }

    fun shelf(alloc: Recording = Recording()) = ReverbUnits(sampleRate, allocate = alloc.allocate) to alloc

    fun effect(units: ReverbUnits) = KatalystReverbEffect(units = units, blockFrames = blockFrames)

    fun KatalystReverbEffect.configureSize(size: Double) =
        configure(size = size, lowpass = null, wet = 1.0)

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    fun noise(frames: Int, seed: Int): StereoBuffer {
        val b = StereoBuffer(frames)
        var x = seed
        for (i in 0 until frames) {
            x = x * 1103515245 + 12345
            b.left[i] = ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
            x = x * 1103515245 + 12345
            b.right[i] = ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
        }
        return b
    }

    // ── Nothing until asked ──────────────────────────────────────────────────────────────────────

    "eight cylinders without a reverb build ZERO reverb networks" {
        val (units, alloc) = shelf()
        val eight = List(8) { id ->
            Cylinder(id = id, blockFrames = blockFrames, sampleRate = sampleRate, reverbs = units)
        }

        alloc.asked shouldBe 0
        // ...AND no cylinder holds a unit: a cylinder that built its own Reverb would bypass the
        // shelf and the counter above would not see it (the delay's lesson, 2b).
        eight.forEach { it.reverb!!.reverb.shouldBeNull() }
    }

    "a fresh effect renders as a no-op, without a unit and without throwing" {
        val (units, _) = shelf()
        val fx = effect(units)
        val ctx = ctx()
        ctx.mixBuffer.left.fill(0.25)

        fx.process(ctx)

        fx.reverb.shouldBeNull()
        ctx.mixBuffer.left[7] shouldBe 0.25 // untouched
        fx.hasTail() shouldBe false
    }

    "the first activating configure rents exactly one unit; later configures keep it" {
        val (units, alloc) = shelf()
        val fx = effect(units)

        fx.configureSize(size = 0.6)
        val unit = fx.reverb.shouldNotBeNull()
        unit.size shouldBe 0.6

        fx.configureSize(size = 0.3)
        fx.configureSize(size = 0.9)

        fx.reverb shouldBeSameInstanceAs unit
        alloc.asked shouldBe 1
        units.allocations shouldBe 1
    }

    "an off-config on a fresh effect rents nothing — 'off' does not need a network" {
        val (units, alloc) = shelf()
        val fx = effect(units)

        fx.configureSize(size = 0.005)

        fx.reverb.shouldBeNull()
        alloc.asked shouldBe 0
    }

    "reset() keeps the unit — a re-leased orbit does not re-rent" {
        val (units, alloc) = shelf()
        val fx = effect(units)
        fx.configureSize(size = 0.6)
        val unit = fx.reverb!!

        fx.reset()
        fx.reverb shouldBeSameInstanceAs unit
        unit.size shouldBe 0.0 // params to factory, as before

        fx.configureSize(size = 0.4)
        fx.reverb shouldBeSameInstanceAs unit
        alloc.asked shouldBe 1
    }

    // ── Refusal: dry, counted, latched ───────────────────────────────────────────────────────────

    "a refused unit leaves the effect Off, dry, counted ONCE over many blocks — and reset clears the latch" {
        val alloc = Recording(failing = true)
        val (units, _) = shelf(alloc)
        val fx = effect(units)
        val ctx = ctx()
        ctx.mixBuffer.left.fill(0.5)

        repeat(100) {
            fx.configureSize(size = 0.6) // the owner re-applies every block
            fx.process(ctx)
        }

        fx.reverb.shouldBeNull()
        fx.deniedRents shouldBe 1
        alloc.asked shouldBe 1 // asked ONCE, not 100 times
        units.failures shouldBe 1
        ctx.mixBuffer.left[3] shouldBe 0.5 // dry: the mix passes untouched
        fx.hasTail() shouldBe false

        alloc.failing = false
        fx.reset()
        fx.configureSize(size = 0.6)
        fx.reverb.shouldNotBeNull()
        alloc.asked shouldBe 2
    }

    // ── The shelf: a returned unit is a new unit ────────────────────────────────────────────────

    "a returned unit is BIT-IDENTICAL to a fresh one — state zeroed, parameters at factory" {
        val (units, _) = shelf()

        // Used hard under other parameters, then returned.
        val used = units.rent().shouldNotBeNull().apply { size = 0.95; lowpass = 900.0 }
        val sink = StereoBuffer(blockFrames)
        repeat(200) { used.process(noise(blockFrames, seed = it + 1), sink, blockFrames) }
        units.giveBack(used)

        // Review round 3: the return is O(1) — parameters back now, the network still charged —
        // and housekeep() zeroes it later, one unit per call.
        used.size shouldBe 0.5
        used.hasTail(0.0) shouldBe true
        units.isClean shouldBe false
        units.housekeep() shouldBe true
        units.isClean shouldBe true
        units.housekeep() shouldBe false // nothing left
        units.housekeptUnits shouldBe 1

        val again = units.rent().shouldNotBeNull()
        units.syncCleans shouldBe 0
        again shouldBeSameInstanceAs used
        again.size shouldBe 0.5 // constructor defaults
        again.lowpass shouldBe null
        again.hasTail(0.0) shouldBe false

        // Same input, same output as a unit that was never used: the shelf leaves no fingerprint.
        val fresh = Reverb(sampleRate)
        val outA = StereoBuffer(blockFrames)
        val outB = StereoBuffer(blockFrames)
        var energy = 0.0
        for (block in 0 until 100) {
            val input = noise(blockFrames, seed = 1000 + block)
            outA.clear(); outB.clear()
            fresh.process(input, outA, blockFrames)
            again.process(input, outB, blockFrames)
            for (i in 0 until blockFrames) {
                outB.left[i] shouldBe outA.left[i]
                outB.right[i] shouldBe outA.right[i]
                energy += abs(outA.left[i])
            }
        }
        energy shouldBeGreaterThan 1.0 // positive control: the reverb did something
    }

    "rent of a dirty unit, when no clean one is idle, zeroes it on the spot — a rented unit is always clean" {
        val (units, _) = shelf()
        val used = units.rent().shouldNotBeNull()
        val sink = StereoBuffer(blockFrames)
        repeat(50) { used.process(noise(blockFrames, seed = it + 1), sink, blockFrames) }
        units.giveBack(used)

        val again = units.rent().shouldNotBeNull()

        again shouldBeSameInstanceAs used
        again.hasTail(0.0) shouldBe false
        units.syncCleans shouldBe 1
        units.isClean shouldBe true // the dirty count followed the unit out (round 5)
    }

    "rent prefers a CLEAN idle unit over a dirty one" {
        val (units, _) = shelf()
        val a = units.rent().shouldNotBeNull()
        val b = units.rent().shouldNotBeNull()
        units.giveBack(a)
        units.housekeep() // a is clean
        val sink = StereoBuffer(blockFrames)
        b.process(noise(blockFrames, seed = 3), sink, blockFrames)
        units.giveBack(b) // b is dirty, and newer

        units.rent() shouldBeSameInstanceAs a
        units.syncCleans shouldBe 0
    }

    "a latched refusal still takes a unit the SHELF can serve — only the allocation is skipped" {
        // The delay's round-2 lesson, re-learned for the reverb in round 3: a Boolean latch that
        // sat above the shelf lookup kept an orbit dry after other playbacks had returned units.
        val alloc = Recording(failing = true)
        val (units, _) = shelf(alloc)
        val fx = effect(units)
        fx.configureSize(size = 0.6)
        fx.reverb.shouldBeNull()
        fx.deniedRents shouldBe 1

        alloc.failing = false
        val returned = units.rent().shouldNotBeNull()
        units.giveBack(returned)
        alloc.failing = true
        val askedBefore = alloc.asked

        fx.configureSize(size = 0.6)

        fx.reverb shouldBeSameInstanceAs returned
        alloc.asked shouldBe askedBefore // no allocation attempted
        fx.deniedRents shouldBe 1 // a shelf miss while latched is not a refusal
    }

    "the shelf holds at most maxIdle units; a further return is dropped, a double return refused" {
        val units = ReverbUnits(sampleRate, maxIdle = 2)
        val a = units.rent()!!
        val b = units.rent()!!
        val c = units.rent()!!

        units.giveBack(a)
        units.giveBack(a) // a double return while the shelf still has room: refused, not shelved twice
        units.doubleReturns shouldBe 1
        units.idleCount shouldBe 1
        units.dropped shouldBe 0

        units.giveBack(b)
        units.giveBack(c)
        units.idleCount shouldBe 2
        units.dropped shouldBe 1

        // The next two rents are hits (LIFO), the third allocates.
        units.rent() shouldBeSameInstanceAs b
        units.rent() shouldBeSameInstanceAs a
        units.rent()!! shouldNotBeSameInstanceAs c
        units.hits shouldBe 2
        units.allocations shouldBe 4
    }

    "the default allocator builds a unit at the backend's rate (the catch itself is not reachable from here)" {
        // Reverb(sampleRate) cannot be made to fail without exhausting the JVM; the catch is the
        // same shape as SizedBuffers.allocateOrNull / SampleStore.allocatePcmOrNull, whose specs
        // exercise a real OOM. This row pins only the happy path (review round 3: the old name
        // claimed the catch).
        ReverbUnits.allocateOrNull(sampleRate).shouldNotBeNull().sampleRate shouldBe sampleRate
    }

    // ── The master bus: same shelf, same return path ────────────────────────────────────────────

    "a master reverb rents from the shelf; a refused unit skips the stage, counted" {
        val alloc = Recording()
        val (units, _) = shelf(alloc)
        val dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.4, size = 7.0))

        val chain = MasterChain.build(dsl, sampleRate, blockFrames, reverbs = units)
        chain.reverbs.size shouldBe 1
        alloc.asked shouldBe 1

        alloc.failing = true
        val denied = MasterChain.build(dsl, sampleRate, blockFrames, reverbs = units)
        denied.reverbs.size shouldBe 0
        denied.deniedRents shouldBe 1
        denied.isActive shouldBe false // nothing else in the chain
    }

    "an evicted master chain returns its unit, and the next master reverb takes it without allocating" {
        val (units, alloc) = shelf()
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = MasterRegistry(), reverbs = units)

        for (i in 0 until 8) {
            bus.register("m$i", MasterDsl.of(MasterStageDsl.Reverb(wet = 0.4, size = 5.0 + i * 0.1)))
        }
        units.idleCount shouldBe 0
        val askedBefore = alloc.asked

        bus.register("m8", MasterDsl.of(MasterStageDsl.Reverb(wet = 0.4, size = 6.0)))

        units.hits shouldBe 1
        units.idleCount shouldBe 0
        alloc.asked shouldBe askedBefore
    }
})
