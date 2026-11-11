/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * The note-ring shelf's rules (KLANG-00429, audit finding L5-03): the per-note rings a voice's tree rents (the Karplus
 * lines, the shimmer ring). Class-sized in powers of two; an empty class allocates (the fallback, counted); a returned
 * ring is DIRTY and is never rented until housekeeping has zeroed it; the idle bytes stay inside the byte bound; a
 * double return is refused; a lease gives each ring back once.
 */
class NoteRingsSpec : StringSpec({

    /** Writes a marker into every frame, as a string or a shimmer leaves its ring. */
    fun DoubleArray.scribble(): DoubleArray {
        for (i in indices) {
            this[i] = 0.25 + i
        }

        return this
    }

    "the classes are powers of two from 4096 to 131,072 frames; a Karplus line rents 4096, a request past the top its own size" {
        val shelf = NoteRings()

        shelf.classFrames(1) shouldBe 4096
        shelf.classFrames(2500) shouldBe 4096
        shelf.classFrames(4096) shouldBe 4096
        shelf.classFrames(4097) shouldBe 8192
        shelf.classFrames(14_402) shouldBe 16_384
        shelf.classFrames(96_000) shouldBe 131_072
        shelf.classFrames(131_072) shouldBe 131_072
        shelf.classFrames(131_073) shouldBe 131_073

        NoteRings.classIndexOf(4096) shouldBe 0
        NoteRings.classIndexOf(131_072) shouldBe 5
        NoteRings.classIndexOf(2500) shouldBe -1
        NoteRings.classIndexOf(6000) shouldBe -1
        NoteRings.classIndexOf(262_144) shouldBe -1
    }

    "an empty shelf allocates a zeroed ring of the class, counted as an allocation (the fallback)" {
        val shelf = NoteRings()
        val ring = shelf.rent(2500)

        ring.size shouldBe 4096
        ring.all { it == 0.0 } shouldBe true
        shelf.allocations shouldBe 1
        shelf.hits shouldBe 0
    }

    "a returned ring is dirty and is NEVER rented before housekeeping zeroes it; then it comes back, all zero" {
        val shelf = NoteRings()
        val first = shelf.rent(2500).scribble()

        shelf.giveBack(first)
        shelf.dirtyCount shouldBe 1
        shelf.idleCount shouldBe 1
        shelf.isClean shouldBe false

        // Nothing clean on the shelf: the rent allocates rather than hand out (or clean on the spot) the dirty ring
        val second = shelf.rent(2500)
        second shouldNotBeSameInstanceAs first
        second.all { it == 0.0 } shouldBe true
        shelf.allocations shouldBe 2
        shelf.hits shouldBe 0
        withClue("the dirty ring is still untouched on the shelf: nothing was cleared on the rent") {
            first[4095] shouldBe 0.25 + 4095
        }

        // A slice smaller than the ring leaves it dirty: still not rentable
        shelf.housekeep(maxFrames = 1000) shouldBe 1000
        shelf.dirtyCount shouldBe 1
        shelf.rent(2500) shouldNotBeSameInstanceAs first
        shelf.allocations shouldBe 3

        // The rest of it: clean now, and rented again
        shelf.housekeep(maxFrames = 1_000_000) shouldBe 3096
        shelf.dirtyCount shouldBe 0
        shelf.isClean shouldBe true

        val again = shelf.rent(2500)
        again shouldBeSameInstanceAs first
        again.all { it == 0.0 } shouldBe true
        shelf.hits shouldBe 1
        shelf.idleCount shouldBe 0
        shelf.idleBytes shouldBe 0.0
    }

    "housekeeping zeroes a bounded slice per call and nothing while the shelf is clean" {
        val shelf = NoteRings()
        val rings = List(5) { shelf.rent(2500).scribble() }

        rings.forEach { shelf.giveBack(it) }

        shelf.housekeep() shouldBe NoteRings.HOUSEKEEP_FRAMES
        shelf.dirtyCount shouldBe 1
        shelf.housekeep() shouldBe 4096
        shelf.dirtyCount shouldBe 0
        shelf.housekeep() shouldBe 0
        rings.all { r -> r.all { it == 0.0 } } shouldBe true
    }

    "the idle bytes stay inside the byte bound: a return that would pass it is dropped" {
        val shelf = NoteRings(budgetBytes = 2 * 4096 * 8)
        val rings = List(3) { shelf.rent(2500) }

        rings.forEach { shelf.giveBack(it) }

        shelf.idleCount shouldBe 2
        shelf.idleBytes shouldBe 2 * 4096 * 8.0
        shelf.dropped shouldBe 1
    }

    "a double return is refused and counted, also of a ring being zeroed or already clean" {
        val shelf = NoteRings()
        val ring = shelf.rent(2500)

        shelf.giveBack(ring)
        shelf.giveBack(ring)
        shelf.doubleReturns shouldBe 1
        shelf.idleCount shouldBe 1

        shelf.housekeep(maxFrames = 100)
        shelf.giveBack(ring)
        shelf.doubleReturns shouldBe 2

        shelf.housekeep(maxFrames = 1_000_000)
        shelf.giveBack(ring)
        shelf.doubleReturns shouldBe 3
        shelf.idleCount shouldBe 1
        shelf.dirtyCount shouldBe 0
    }

    "a ring of no class (allocated past the top class) is never shelved" {
        val shelf = NoteRings()
        val big = shelf.rent(200_000)

        big.size shouldBe 200_000
        shelf.giveBack(big)
        shelf.idleCount shouldBe 0
        shelf.dropped shouldBe 1
    }

    "the warmup's stock tops the class up to its count with clean rings, inside the bound" {
        val shelf = NoteRings()
        val returned = shelf.rent(2500)

        shelf.giveBack(returned)
        shelf.stock(minFrames = 2500, count = 4)

        shelf.idleCount shouldBe 4
        shelf.dirtyCount shouldBe 1
        shelf.allocations shouldBe 4

        // Three clean rings are rented without allocating; the fourth waits for housekeeping
        repeat(3) { shelf.rent(2500) }
        shelf.allocations shouldBe 4
        shelf.hits shouldBe 3

        val small = NoteRings(budgetBytes = 3 * 4096 * 8)
        small.stock(minFrames = 2500, count = 10)
        small.idleCount shouldBe 3
    }

    "a lease gives every ring it rented back once; a second call gives nothing" {
        val shelf = NoteRings()
        val lease = NoteRingLease(shelf)
        val rings = List(9) { lease.rent(2500) }

        lease.count shouldBe 9
        lease.giveBackAll()
        lease.count shouldBe 0
        shelf.idleCount shouldBe 9

        lease.giveBackAll()
        shelf.idleCount shouldBe 9
        shelf.doubleReturns shouldBe 0
        rings.distinct().size shouldBe 9
    }

    "without a lease a node's ring is its own array of exactly the length it asked for" {
        val none: NoteRingLease? = null

        none.ringOf(2500).size shouldBe 2500
    }
})
