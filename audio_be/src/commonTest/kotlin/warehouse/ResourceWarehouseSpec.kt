/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * The warehouse in isolation — step 1 of `docs/plans/resource-warehouse.md`. No DSP anywhere in
 * here; the rules are the subject. Every row names the rule it pins, and every rule has a mutation
 * that turns its row red.
 *
 * Frames are small on purpose (base 8, so classes are 8/16/32/64…): the arithmetic is the same at
 * 22 050 and the runs stay fast.
 */
class ResourceWarehouseSpec : StringSpec({

    val base = 8

    /** An allocator that records every size it was asked for and can be told to fail. */
    class Recording(var failing: Boolean = false) : (Int) -> StereoBuffer? {
        val asked = mutableListOf<Int>()
        override fun invoke(frames: Int): StereoBuffer? {
            asked += frames
            return if (failing) null else StereoBuffer(frames)
        }
    }

    fun shelf(budgetBytes: Int = Int.MAX_VALUE, alloc: Recording = Recording()) =
        SizedBuffers(baseFrames = base, budgetBytes = budgetBytes, allocate = alloc) to alloc

    fun bytes(frames: Int) = frames * 2 * 8

    // ── Rule 1: class-sized, powers of two from the base, no ceiling ─────────────────────────────

    "classFrames rounds UP to the smallest power-of-two class that holds the request" {
        val (s, _) = shelf()
        s.classFrames(1) shouldBe 8      // below the base gets the base
        s.classFrames(8) shouldBe 8      // exactly a class is that class
        s.classFrames(9) shouldBe 16     // one over rounds up
        s.classFrames(16) shouldBe 16
        s.classFrames(17) shouldBe 32
        s.classFrames(1000) shouldBe 1024
    }

    "rent allocates exactly the class, not the request" {
        val (s, alloc) = shelf()
        val b = s.rent(9).shouldNotBeNull()
        b.left.size shouldBe 16
        alloc.asked shouldBe listOf(16)
    }

    "there is no ceiling — a request far above every class is served at its class" {
        val (s, alloc) = shelf()
        s.rent(3_000_000).shouldNotBeNull().left.size shouldBe 4_194_304 // 8 * 2^19
        alloc.asked shouldBe listOf(4_194_304)
    }

    // ── Rule 2: stocked by return, best-fit-up, allocate on a miss ───────────────────────────────

    "a fresh shelf allocates — nothing is pre-filled by prediction" {
        val (s, alloc) = shelf()
        s.shelfCount shouldBe 0
        s.rent(8).shouldNotBeNull()
        alloc.asked.size shouldBe 1
        s.allocations shouldBe 1
        s.hits shouldBe 0
    }

    "a returned buffer is what the next rent of that class gets back — the same instance, no allocation" {
        val (s, alloc) = shelf()
        val first = s.rent(8).shouldNotBeNull()
        s.giveBack(first)

        val again = s.rent(8).shouldNotBeNull()

        again shouldBeSameInstanceAs first
        alloc.asked.size shouldBe 1 // still just the one allocation
        s.hits shouldBe 1
    }

    "best-fit-up: with 4x and 8x idle, a 2x request takes the 4x — not the 8x, and not a fresh 2x" {
        val (s, alloc) = shelf()
        val four = s.rent(32).shouldNotBeNull()   // class 32
        val eight = s.rent(64).shouldNotBeNull()  // class 64
        s.giveBack(four)
        s.giveBack(eight)
        alloc.asked.clear()

        val got = s.rent(16).shouldNotBeNull()    // wants class 16

        got shouldBeSameInstanceAs four
        alloc.asked shouldBe emptyList()          // served from the shelf
        s.shelfCount shouldBe 1                   // the 64 is still there
    }

    "never a buffer that is too small: with only 1x idle, a 2x request allocates and leaves the 1x" {
        val (s, alloc) = shelf()
        val one = s.rent(8).shouldNotBeNull()
        s.giveBack(one)
        alloc.asked.clear()

        val got = s.rent(16).shouldNotBeNull()

        got shouldNotBeSameInstanceAs one
        got.left.size shouldBe 16
        alloc.asked shouldBe listOf(16)
        s.shelfCount shouldBe 1
    }

    // ── The zero invariant ───────────────────────────────────────────────────────────────────────

    "every rented buffer is all zeros — a returned buffer is cleared, so no previous owner's tail replays" {
        val (s, _) = shelf()
        val b = s.rent(8).shouldNotBeNull()
        b.left.fill(0.7)
        b.right.fill(-0.3)
        s.giveBack(b)

        val again = s.rent(8).shouldNotBeNull()

        again shouldBeSameInstanceAs b
        (again.left.all { it == 0.0 } && again.right.all { it == 0.0 }) shouldBe true
    }

    // ── Rule 3: the budget bounds IDLE bytes, largest freed first ────────────────────────────────

    "over budget on return, the LARGEST idle buffer is freed first, and the shelf ends under budget" {
        // Budget holds a 16 and a 32 (768 bytes) but not also a 64.
        val (s, _) = shelf(budgetBytes = bytes(16) + bytes(32))
        val b16 = s.rent(16).shouldNotBeNull()
        val b32 = s.rent(32).shouldNotBeNull()
        val b64 = s.rent(64).shouldNotBeNull()

        s.giveBack(b16)
        s.giveBack(b32)
        s.shelfBytes shouldBe bytes(16) + bytes(32)

        s.giveBack(b64) // tips it over: the 64 is the largest, so it is the one that goes

        s.shelfCount shouldBe 2
        s.shelfBytes shouldBe bytes(16) + bytes(32)
        s.dropped shouldBe 1
        // ...and the freed one is really gone: a 64 request now allocates.
        s.rent(64).shouldNotBeNull() shouldNotBeSameInstanceAs b64
    }

    "a single buffer larger than the whole budget never sits on the shelf" {
        val (s, _) = shelf(budgetBytes = bytes(8))
        val big = s.rent(64).shouldNotBeNull()
        big.left[3] = 0.5
        s.giveBack(big)
        s.shelfCount shouldBe 0
        s.shelfBytes shouldBe 0
        s.dropped shouldBe 1
        // Review round 2: giveBack runs on the audio thread (a grow returns the old ring inside
        // configure); a ring the same call drops is not worth an O(frames) zero-fill. The
        // eviction is decided first, so the dropped buffer keeps its contents — it is garbage.
        big.left[3] shouldBe 0.5
    }

    // ── Rule 4 (review round 3): a return is O(1); clearing is deferred housekeeping ─────────────

    "giveBack touches nothing — the buffer comes back DIRTY, and housekeep zeroes it a slice at a time" {
        // Every return site is on the audio thread (a grow inside configure, an engine's disposal
        // inside the render callback, a master chain's eviction inside a command drain), so the
        // clear cannot live there: sixteen warmup rings retired in one block were ~19 MB of stores.
        val (s, _) = shelf()
        val b = s.rent(64).shouldNotBeNull() // class 64 frames
        b.left.fill(0.7)
        b.right.fill(-0.3)

        s.giveBack(b)

        b.left[63] shouldBe 0.7 // untouched at return
        s.isClean shouldBe false

        s.housekeep(maxFrames = 40) shouldBe 40 // a bounded slice: the first 40 frames
        b.left[39] shouldBe 0.0
        b.right[39] shouldBe 0.0
        b.left[40] shouldBe 0.7
        s.isClean shouldBe false

        s.housekeep(maxFrames = 40) shouldBe 24 // the rest, and no more than there was
        b.left[63] shouldBe 0.0
        b.right[63] shouldBe 0.0
        s.isClean shouldBe true
        s.housekeep(maxFrames = 40) shouldBe 0 // nothing left to do
        s.housekeptFrames shouldBe 64.0
    }

    "rent prefers a CLEAN buffer, even a larger class, over a dirty exact fit" {
        val (s, _) = shelf()
        val dirty8 = s.rent(8).shouldNotBeNull()
        val clean16 = s.rent(16).shouldNotBeNull()
        s.giveBack(clean16)
        s.housekeep() // the 16 is clean now
        dirty8.left[0] = 0.5
        s.giveBack(dirty8) // and the 8 is dirty

        s.rent(8) shouldBeSameInstanceAs clean16
        s.syncCleans shouldBe 0
    }

    "rent of a dirty buffer, when nothing clean fits, zeroes it on the spot — and counts that" {
        val (s, _) = shelf()
        val b = s.rent(8).shouldNotBeNull()
        b.left[3] = 0.9
        b.right[5] = -0.2
        s.giveBack(b)

        val again = s.rent(8).shouldNotBeNull()

        again shouldBeSameInstanceAs b
        (again.left.all { it == 0.0 } && again.right.all { it == 0.0 }) shouldBe true
        s.syncCleans shouldBe 1
    }

    "housekeep zeroes the oldest return first, across buffers, within one budget" {
        val (s, _) = shelf()
        val first = s.rent(8).shouldNotBeNull().also { it.left.fill(1.0) }
        val second = s.rent(8).shouldNotBeNull().also { it.left.fill(1.0) }
        s.giveBack(first)
        s.giveBack(second)

        s.housekeep(maxFrames = 12) shouldBe 12

        (first.left.all { it == 0.0 }) shouldBe true // 8 of the 12
        first.right.all { it == 0.0 } shouldBe true
        second.left[3] shouldBe 0.0 // 4 more
        second.left[4] shouldBe 1.0
    }

    "a dropped buffer is never cleared — garbage needs no zero-fill" {
        val (s, _) = shelf(budgetBytes = bytes(16 + 8))
        val b16 = s.rent(16).shouldNotBeNull()
        val b8 = s.rent(8).shouldNotBeNull()
        s.giveBack(b16)
        s.giveBack(b8) // 24 bytes-of-frames on the shelf: at the budget, nothing dropped
        s.dropped shouldBe 0

        val b32 = s.rent(32).shouldNotBeNull()
        b32.left[2] = 0.4
        s.giveBack(b32) // 56 > 24: the 32 is the largest and goes; the 16 and 8 stay
        s.dropped shouldBe 1
        s.shelfCount shouldBe 2
        repeat(10) { s.housekeep() }
        b32.left[2] shouldBe 0.4 // not on the shelf, not housekept
    }

    "the budget never touches WORKING memory — rented buffers are not the shelf's business" {
        val (s, _) = shelf(budgetBytes = bytes(8))
        // Way past the budget, all rented, none returned: the shelf has nothing to say about it.
        repeat(5) { s.rent(64).shouldNotBeNull() }
        s.shelfBytes shouldBe 0
        s.dropped shouldBe 0
    }

    "a double return is refused and counted — one ring can never be on the shelf twice" {
        // Review round 1: without this, a returned-twice ring rents to two live orbits — two
        // delays writing one ring, heard as cross-talk. Eviction (2f) is where it is easiest to do.
        val (s, _) = shelf()
        val b = s.rent(8).shouldNotBeNull()
        s.giveBack(b)
        s.giveBack(b)

        s.shelfCount shouldBe 1
        s.doubleReturns shouldBe 1
        s.rent(8).shouldNotBeNull() shouldBeSameInstanceAs b
        s.shelfCount shouldBe 0 // and it came off the shelf exactly once
    }

    "byte accounting survives the uncapped ladder — a ring past Int bytes does not wrap the shelf negative" {
        // Review round 1: `left.size * 16` in Int wraps past ~134 M frames; a negative shelfBytes
        // would switch the budget off forever. The accounting is Double now; this asserts the value
        // for a synthetic huge ring without allocating one.
        val huge = 200_000_000 // frames — 3.2 GB as bytes, past Int
        SizedBuffers.bytesOfFrames(huge) shouldBe 3.2e9
        SizedBuffers.bytesOf(StereoBuffer(1)) shouldBe 16.0
    }

    // ── Out of memory: null at the one site, counted, not poisoned ───────────────────────────────

    "allocation failure returns null and is counted — it does not throw" {
        val alloc = Recording(failing = true)
        val (s, _) = shelf(alloc = alloc)

        s.rent(8).shouldBeNull()

        s.failures shouldBe 1
        s.allocations shouldBe 0
    }

    "a sufficient idle buffer is served even when the allocator is failing — the shelf needs no allocation" {
        val alloc = Recording()
        val (s, _) = shelf(alloc = alloc)
        val b = s.rent(8).shouldNotBeNull()
        s.giveBack(b)
        alloc.failing = true

        s.rent(8).shouldNotBeNull() shouldBeSameInstanceAs b
        s.failures shouldBe 0
    }

    "a failure does not poison the shelf — once memory is back, rent works again" {
        val alloc = Recording(failing = true)
        val (s, _) = shelf(alloc = alloc)
        s.rent(8).shouldBeNull()
        alloc.failing = false

        s.rent(8).shouldNotBeNull()
        s.failures shouldBe 1
        s.allocations shouldBe 1
    }

    "the default allocator turns a hopeless size into null rather than a throw" {
        // Int.MAX_VALUE frames is 34 GB per channel; no JVM heap in CI holds it. This is the one
        // row that exercises the real catch — everything above injects.
        SizedBuffers.allocateOrNull(Int.MAX_VALUE).shouldBeNull()
    }

    // ── Scratch: sized at build, never allocating in render, release guarded ─────────────────────

    "ensureCapacity pre-grows the pool so a graph of that depth acquires without allocating" {
        val scratch = ScratchBuffers(blockFrames = 128, initialCapacity = 1)
        scratch.ensureCapacity(6)
        scratch.capacity shouldBe 6

        // Six nested acquires — the shape of a six-deep ignitor graph — allocate nothing.
        scratch.use { scratch.use { scratch.use { scratch.use { scratch.use { scratch.use { } } } } } }

        scratch.lateAllocations shouldBe 0
    }

    "without ensureCapacity, the same depth allocates INSIDE render — and says so" {
        val scratch = ScratchBuffers(blockFrames = 128, initialCapacity = 1)

        scratch.use { scratch.use { scratch.use { } } }

        // This is the audit's §6.5 finding made visible: the pool grew under the render loop.
        scratch.lateAllocations shouldBe 2
    }

    "an unbalanced release is guarded and counted, not allowed to hand out a live buffer twice" {
        val scratch = ScratchBuffers(blockFrames = 128, initialCapacity = 2)
        scratch.use { }
        scratch.release() // one too many

        scratch.unbalancedReleases shouldBe 1

        // Still sane: the next two acquires get two DIFFERENT buffers.
        var a: Any? = null
        var b: Any? = null
        scratch.use { x -> a = x; scratch.use { y -> b = y } }
        withClue("distinct buffers after an unbalanced release") { (a !== b) shouldBe true }
    }

    // ── The warehouse itself ─────────────────────────────────────────────────────────────────────

    "the warehouse's class 0 is half a second PLUS the interpolation margin, and its budget is the named constant" {
        val w = ResourceWarehouse(sampleRate = 48_000, blockFrames = 128)
        w.sized.baseFrames shouldBe 24_000 + 64
        w.sized.budgetBytes shouldBe ResourceWarehouse.SHELF_BUDGET_BYTES
        ResourceWarehouse.SHELF_BUDGET_BYTES shouldBe 32 * 1024 * 1024
    }

    "the ring classes are 0.5, 1, 2, 4 … seconds, and a delay of 0.25–0.5 s fits class 0 THROUGH the real effect" {
        // Review round 2: the first cut of this row called classFrames with the bare frame count
        // and could not see that the effect adds a margin — LazyRingSpec proved the opposite of
        // this row's title for exactly 0.5 s. The request now goes through KatalystDelayEffect.
        val w = ResourceWarehouse(sampleRate = 44_100, blockFrames = 128)
        val class0 = 22_050 + ResourceWarehouse.RING_MARGIN_FRAMES
        w.sized.classFrames(1) shouldBe class0
        w.sized.classFrames(class0 + 1) shouldBe 2 * class0     // 1 s
        w.sized.classFrames(3 * class0) shouldBe 4 * class0     // 2 s
        w.sized.classFrames(7 * class0) shouldBe 8 * class0     // 4 s

        for (time in listOf(0.25, 0.375, 0.5)) {
            val fx = KatalystDelayEffect(rings = w.sized, sampleRate = 44_100, blockFrames = 128)
            fx.configure(timeSeconds = time, feedback = 0.0, cap = 1.0)
            withClue("delaytime $time") { fx.delayLine.shouldNotBeNull().capacityFrames shouldBe class0 }
        }
    }
})
