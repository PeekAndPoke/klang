/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.effects.DelayLine
import kotlin.math.abs

/**
 * Resource warehouse step 2b: a delay ring exists only once a voice asks for one, sized to its
 * class, rented from the shelf, and refused gracefully (`docs/plans/resource-warehouse.md`).
 *
 * The headline row is the third one. A `Cylinder` used to construct a 10-second `DelayLine` in its
 * constructor — 7.68 MB, 97 % of the cylinder — for every orbit, delay or not; eight of those
 * zero-filled on the audio thread on first play were the "Der Schmetterling" stutter. Eight
 * cylinders now allocate **zero ring bytes** until an orbit configures a delay.
 */
class LazyRingSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val base = (sampleRate * ResourceWarehouse.MIN_RING_SECONDS).toInt() // 22 050 — class 0

    /** A shelf whose allocator records sizes and can be told to fail. */
    class Recording(var failing: Boolean = false) : (Int) -> StereoBuffer? {
        val asked = mutableListOf<Int>()
        override fun invoke(frames: Int): StereoBuffer? {
            asked += frames
            // A hopeless size is refused here rather than attempted: this is a recording double,
            // and asking the JVM for Int.MAX frames would be a real OOM, not a simulated one.
            return if (failing || frames == Int.MAX_VALUE) null else StereoBuffer(frames)
        }
    }

    fun shelf(alloc: Recording = Recording()) = SizedBuffers.forRings(sampleRate, allocate = alloc) to alloc

    fun effect(rings: SizedBuffers) = KatalystDelayEffect(rings = rings, sampleRate = sampleRate, blockFrames = blockFrames)

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    // ── Nothing until asked ──────────────────────────────────────────────────────────────────────

    "a fresh cylinder holds NO ring" {
        val (rings, alloc) = shelf()
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, rings = rings)

        cylinder.delay.delayLine.shouldBeNull()
        alloc.asked shouldBe emptyList()
    }

    "a fresh effect renders as a no-op, without a ring and without throwing" {
        val (rings, _) = shelf()
        val fx = effect(rings)
        val c = ctx()
        c.delaySendBuffer.left.fill(0.5)

        fx.process(c)

        c.mixBuffer.left.all { it == 0.0 } shouldBe true
        fx.delayLine.shouldBeNull()
    }

    "eight cylinders without a delay allocate ZERO ring bytes — the Der Schmetterling case" {
        val (rings, alloc) = shelf()
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate, rings = rings)

        // Touching an orbit constructs its cylinder; that used to cost 7.68 MB each, eight times.
        val eight = List(8) { id -> Cylinder(id = id, blockFrames = blockFrames, sampleRate = sampleRate, rings = rings) }

        // The shelf was never asked...
        alloc.asked shouldBe emptyList()
        rings.allocations shouldBe 0
        // ...AND no cylinder holds a ring. The second half is the one that matters: a cylinder that
        // allocated its ring through DelayLine's own constructor would bypass the shelf entirely,
        // and the counters above would stay at zero while 63 MB went out the door. (A mutation did
        // exactly that and the counters alone did not see it.)
        eight.forEach { it.delay.delayLine.shouldBeNull() }
        cylinders.anyActive() shouldBe false
    }

    // ── The first activation rents, sized to the class ───────────────────────────────────────────

    "the first activating configure rents exactly one ring, of the class that holds the time" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)

        fx.configure(timeSeconds = 0.3, feedback = 0.4, cap = 1.0)

        // 0.3 s + the 64-frame margin fits class 0 (0.5 s = 22 050 frames).
        alloc.asked shouldBe listOf(base)
        fx.delayLine.shouldNotBeNull().capacityFrames shouldBe base
        fx.delayLine!!.delayTimeSeconds shouldBe 0.3
    }

    "a time just past a class boundary gets the next class — the margin is real" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)

        // Exactly 0.5 s needs 22 050 + 64 frames: one class up.
        fx.configure(timeSeconds = 0.5, feedback = 0.0, cap = 1.0)

        alloc.asked shouldBe listOf(2 * base)
    }

    "an off-config on a fresh effect rents nothing — 'off' does not need a ring" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)

        fx.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        alloc.asked shouldBe emptyList()
        fx.delayLine.shouldBeNull()
    }

    // ── Never shrink; keep the ring across off/on ────────────────────────────────────────────────

    "a shorter time keeps the ring it has — never shrink" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)
        fx.configure(timeSeconds = 0.9, feedback = 0.0, cap = 1.0) // class 1 (1 s)
        val ring = fx.delayLine.shouldNotBeNull()

        fx.configure(timeSeconds = 0.1, feedback = 0.0, cap = 1.0)

        fx.delayLine shouldBeSameInstanceAs ring
        alloc.asked.size shouldBe 1
        rings.shelfCount shouldBe 0 // nothing was given back
    }

    "off then on again re-uses the same ring — reset keeps it, eviction is what returns it" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)
        val ring = fx.delayLine.shouldNotBeNull()

        fx.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0) // off
        fx.reset()
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0) // on

        fx.delayLine shouldBeSameInstanceAs ring
        alloc.asked.size shouldBe 1
    }

    // ── Growing (2b: rent bigger, give back, NO copy — 2c changes this) ─────────────────────────

    "a longer time past the class rents the next class and returns the old ring to the shelf" {
        val (rings, alloc) = shelf()
        val fx = effect(rings)
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)
        val small = fx.delayLine.shouldNotBeNull()

        fx.configure(timeSeconds = 1.5, feedback = 0.0, cap = 1.0) // needs class 2 (2 s)

        val big = fx.delayLine.shouldNotBeNull()
        big shouldNotBeSameInstanceAs small
        big.capacityFrames shouldBe 4 * base
        alloc.asked shouldBe listOf(base, 4 * base)
        rings.shelfCount shouldBe 1 // the small one is back on the shelf
    }

    "a grow PRESERVES the tail — the tripwire from 2b, flipped by 2c" {
        // This row was pinned RED-on-fix in 2b ("a grow loses the tail; flip when 2c migrates").
        // 2c migrated. A delay ringing when a longer time arrives keeps ringing.
        val (rings, _) = shelf()
        val fx = effect(rings)
        val c = ctx()
        fx.configure(timeSeconds = 0.05, feedback = 0.0, cap = 1.0)

        c.delaySendBuffer.left.fill(0.5)
        repeat(20) { fx.process(c) }
        c.delaySendBuffer.left.fill(0.0)
        c.mixBuffer.clear()
        fx.process(c)
        val tailBefore = c.mixBuffer.left.maxOf { abs(it) }
        tailBefore shouldBeGreaterThan 1e-3

        fx.configure(timeSeconds = 0.06, feedback = 0.0, cap = 1.0) // still class 0 — no grow yet
        fx.configure(timeSeconds = 1.5, feedback = 0.0, cap = 1.0)  // grow to class 2

        // The new tap reaches 1.5 s back, past everything the old ring recorded, so the FIRST block
        // after the grow reads the honest zeros of never-recorded history. What must survive is
        // the history itself: shorten the time back into the recorded span and the tail is there.
        fx.configure(timeSeconds = 0.05, feedback = 0.0, cap = 1.0)
        c.mixBuffer.clear()
        fx.process(c)
        val tailAfter = c.mixBuffer.left.maxOf { abs(it) }
        tailAfter shouldBeGreaterThan 1e-3
    }

    "the seam is inaudible: a grown ring is BIT-IDENTICAL to a ring that was big from the start" {
        // The real proof. Two effects get the same input and the same configure sequence; one
        // starts on a class-0 ring and grows mid-way, the other was handed a 4 s ring up front and
        // never grows. From the grow onward every output sample must match exactly — not "close",
        // exactly — because "n samples ago" reads the same sample in both rings by construction.
        val (rings, _) = shelf()
        val grown = effect(rings)
        val big = KatalystDelayEffect(delayLine = DelayLine(maxDelaySeconds = 4.0, sampleRate = sampleRate), blockFrames = blockFrames)

        fun step(fx: KatalystDelayEffect, c: KatalystContext, block: Int): DoubleArray {
            // A deterministic, non-periodic input so a misaligned copy cannot hide behind repetition.
            for (i in 0 until blockFrames) {
                val t = block * blockFrames + i
                c.delaySendBuffer.left[i] = ((t * 7919) % 1000) / 1000.0 - 0.5
                c.delaySendBuffer.right[i] = ((t * 104729) % 1000) / 1000.0 - 0.5
            }
            c.mixBuffer.clear()
            fx.process(c)
            return DoubleArray(blockFrames) { c.mixBuffer.left[it] }
        }

        val cg = ctx()
        val cb = ctx()
        grown.configure(timeSeconds = 0.05, feedback = 0.6, cap = 1.0)
        big.configure(timeSeconds = 0.05, feedback = 0.6, cap = 1.0)
        repeat(30) { block -> step(grown, cg, block); step(big, cb, block) }

        // The grow: same instant, same new time, on both. Only one of them changes rings.
        grown.configure(timeSeconds = 0.9, feedback = 0.6, cap = 1.0) // class 1: this is the grow
        big.configure(timeSeconds = 0.9, feedback = 0.6, cap = 1.0)
        grown.delayLine.shouldNotBeNull().capacityFrames shouldBe 2 * base // it did grow

        // Then shorten the tap back INSIDE the old ring's span, on both. This is the part that
        // makes the row see the migration at all: a 0.9 s tap reads past the 0.087 s either ring
        // had recorded, into never-recorded zeros in BOTH — so the first cut of this row compared
        // zeros to zeros for 270 blocks and could not tell a migrated ring from an empty one (two
        // mutations survived it). At 0.06 s the tap reads the history itself, which exists in
        // `big` because it recorded it and in `grown` only if the migration carried it.
        grown.configure(timeSeconds = 0.06, feedback = 0.6, cap = 1.0)
        big.configure(timeSeconds = 0.06, feedback = 0.6, cap = 1.0)

        var readSomething = false
        for (block in 30 until 300) {
            val og = step(grown, cg, block)
            val ob = step(big, cb, block)
            for (i in 0 until blockFrames) {
                if (og[i] != ob[i]) {
                    throw AssertionError("seam broke at block $block frame $i: grown=${og[i]} big=${ob[i]}")
                }
                if (block < 33 && ob[i] != 0.0) readSomething = true
            }
        }
        // Positive control: the first blocks after the grow must have echoed real history, or the
        // identity above was zeros against zeros again.
        readSomething shouldBe true
    }

    // ── Out of memory: degrade, count, never throw ───────────────────────────────────────────────

    "a refused first rent leaves the effect Off, dry, counted — and rendering does not throw" {
        val alloc = Recording(failing = true)
        val (rings, _) = shelf(alloc)
        val fx = effect(rings)
        val c = ctx()

        fx.configure(timeSeconds = 0.3, feedback = 0.4, cap = 1.0)

        fx.delayLine.shouldBeNull()
        fx.deniedRents shouldBe 1
        fx.hasTail() shouldBe false

        c.delaySendBuffer.left.fill(0.5)
        fx.process(c) // would have been a NullPointerException — or an OOM — inside process()
        c.mixBuffer.left.all { it == 0.0 } shouldBe true
    }

    "a refused GROW keeps the ring it has and the time clamps to it" {
        val alloc = Recording()
        val (rings, _) = shelf(alloc)
        val fx = effect(rings)
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)
        val ring = fx.delayLine.shouldNotBeNull()
        alloc.failing = true

        fx.configure(timeSeconds = 5.0, feedback = 0.0, cap = 1.0) // needs 8 s; refused

        fx.delayLine shouldBeSameInstanceAs ring
        fx.deniedRents shouldBe 1
        // DelayLine's own physical bound is the clamp: the REQUESTED time is kept (5.0, so the
        // frontend can show what was asked), the EFFECTIVE time cannot exceed the ring.
        ring.delayTimeSeconds shouldBe 5.0
        ring.effectiveDelaySeconds shouldBeLessThan 5.0
        (ring.effectiveDelaySeconds * sampleRate <= ring.capacityFrames) shouldBe true
    }

    "a refused rent is NOT retried every block — the refusal is latched until reset" {
        // Review round 1, both reviewers: Cylinder.applyBusEffects re-applies the owner's config on
        // EVERY block. Without a latch a refusal became a 344 Hz allocate-and-catch storm on the
        // audio thread (with a full GC per catch on the JVM), and `deniedRents` counted blocks.
        val alloc = Recording(failing = true)
        val (rings, _) = shelf(alloc)
        val fx = effect(rings)

        repeat(100) { fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0) } // 100 blocks

        alloc.asked.size shouldBe 1 // asked ONCE
        fx.deniedRents shouldBe 1
        fx.delayLine.shouldBeNull()

        // A SMALLER request than the refused size is still tried (it might fit)...
        alloc.asked.clear()
        alloc.failing = false
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0) // same size: still latched
        alloc.asked shouldBe emptyList()
        // ...and reset() clears the latch — a new owner life starts clean.
        fx.reset()
        fx.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)
        fx.delayLine.shouldNotBeNull()
    }

    "a hopeless time (past Int range, or non-finite) degrades to null rather than renting the smallest ring" {
        // Review round 1: framesFor's toInt() saturated at Int.MAX and the + margin wrapped
        // NEGATIVE, classFrames(negative) returned the base, and delaytime(60000) got a 0.5 s ring.
        val alloc = Recording()
        val (rings, _) = shelf(alloc)
        val fx = effect(rings)

        fx.configure(timeSeconds = 60_000.0, feedback = 0.0, cap = 1.0)
        fx.configure(timeSeconds = Double.POSITIVE_INFINITY, feedback = 0.0, cap = 1.0)

        // The recording allocator is asked for Int.MAX_VALUE and — being a real StereoBuffer
        // constructor — cannot serve it; production's allocateOrNull returns null the same way.
        alloc.asked.all { it == Int.MAX_VALUE } shouldBe true
        fx.delayLine.shouldBeNull()
    }

    "the shelf serves a refused-allocator effect from its idle rings — no allocation needed" {
        val alloc = Recording()
        val (rings, _) = shelf(alloc)
        // Put a class-0 ring on the shelf the way eviction (2f) will: rent it, give it back.
        // (The first cut of this row gave back a ring an effect still HELD — a double-owner, the
        // exact mistake the shelf now guards against.)
        rings.giveBack(rings.rent(base).shouldNotBeNull())
        alloc.failing = true

        val second = effect(rings)
        second.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)

        second.delayLine.shouldNotBeNull()
        second.deniedRents shouldBe 0
    }
})
