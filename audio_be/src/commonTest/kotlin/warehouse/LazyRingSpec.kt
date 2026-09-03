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
            return if (failing) null else StereoBuffer(frames)
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

    "PINNED (2b): a grow LOSES the tail — the new ring starts empty. Flip this when 2c migrates." {
        // A tripwire, the same device the block-framing ledger uses: this row goes RED the moment
        // step 2c copies the old ring's contents across the resize, forcing that switch to be
        // deliberate. Until then, a delay that is ringing when a longer time arrives drops out.
        val (rings, _) = shelf()
        val fx = effect(rings)
        val c = ctx()
        fx.configure(timeSeconds = 0.05, feedback = 0.0, cap = 1.0)

        // Fill the ring with signal, then let the send go silent: the tail alone is what sounds.
        c.delaySendBuffer.left.fill(0.5)
        repeat(20) { fx.process(c) }
        c.delaySendBuffer.left.fill(0.0)
        c.mixBuffer.clear()
        fx.process(c)
        val tailBefore = c.mixBuffer.left.maxOf { abs(it) }
        tailBefore shouldBeGreaterThan 1e-3 // the old ring is ringing

        fx.configure(timeSeconds = 1.5, feedback = 0.0, cap = 1.0) // grow

        c.mixBuffer.clear()
        fx.process(c)
        val tailAfter = c.mixBuffer.left.maxOf { abs(it) }
        tailAfter shouldBeLessThan 1e-9 // ...and the new ring knows nothing of it. 2c: migrate.
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

    "the shelf serves a refused-allocator effect from its idle rings — no allocation needed" {
        val alloc = Recording()
        val (rings, _) = shelf(alloc)
        val first = effect(rings)
        first.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)
        val ring = first.delayLine.shouldNotBeNull()
        rings.giveBack(ring.ring) // what eviction (2f) will do
        alloc.failing = true

        val second = effect(rings)
        second.configure(timeSeconds = 0.3, feedback = 0.0, cap = 1.0)

        second.delayLine.shouldNotBeNull()
        second.deniedRents shouldBe 0
    }
})
