/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Resource warehouse step 2e: the master bus rents its delay rings from the backend's shelf, sized
 * by the SAME rule as the per-orbit delay, with no ceiling on either — and an evicted chain returns
 * its rings. `docs/plans/resource-warehouse.md`.
 *
 * The headline is parity. Between 2b and 2e the orbit delay had lost its 10 s ceiling while the
 * master kept `MAX_DELAY_SECONDS = 10.0`: `delay(20)` was a real 20 s echo on an orbit and a
 * silently re-timed 10 s one on the master — the roomSize-10× class of bug the project has a
 * standing rule against (same parameter, same meaning, on every surface).
 */
class MasterRingShelfSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    class Recording(var failing: Boolean = false) : (Int) -> StereoBuffer? {
        val asked = mutableListOf<Int>()
        override fun invoke(frames: Int): StereoBuffer? {
            asked += frames
            return if (failing || frames == Int.MAX_VALUE) null else StereoBuffer(frames)
        }
    }

    fun shelf(alloc: Recording = Recording()) = SizedBuffers.forRings(sampleRate, allocate = alloc) to alloc

    fun delayDsl(time: Double, feedback: Double = 0.3) =
        MasterDsl.of(MasterStageDsl.Delay(wet = 0.5, timeSeconds = time, feedback = feedback))

    /** What the ORBIT delay rents for [time] — the reference the master must match. */
    fun orbitRingFrames(time: Double, rings: SizedBuffers): Int {
        val fx = KatalystDelayEffect(rings = rings, sampleRate = sampleRate, blockFrames = blockFrames)
        fx.configure(timeSeconds = time, feedback = 0.0, cap = 1.0)
        return fx.delayLine!!.capacityFrames
    }

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

    // ── Parity: one time, one ring, on both buses ────────────────────────────────────────────────

    "a master delay rents the class the orbit delay rents for the same time — 0.25 s, 0.5 s, 3 s, 20 s, and a class edge" {
        // The last time sits 14 frames under the class-0 edge: WITH the interpolation margin it is
        // class 1 on both buses, without it class 0 — the one place the sizing rule shows.
        val classEdge = (ceil(ResourceWarehouse.MIN_RING_SECONDS * sampleRate) + ResourceWarehouse.RING_MARGIN_FRAMES - 14) / sampleRate
        for (time in listOf(0.25, 0.5, 3.0, 20.0, classEdge)) {
            val (rings, alloc) = shelf()
            val chain = MasterChain.build(delayDsl(time), sampleRate, blockFrames, rings)

            chain.delays.size shouldBe 1
            chain.delays[0].capacityFrames shouldBe orbitRingFrames(time, rings)
            // Two rents (master, then the orbit reference), both allocated, both the same class.
            alloc.asked.size shouldBe 2
            alloc.asked[0] shouldBe alloc.asked[1]
        }
    }

    "delay(20) on the master is a real 20 s echo — no ceiling, the parity gap is closed" {
        val (rings, _) = shelf()
        val chain = MasterChain.build(delayDsl(20.0), sampleRate, blockFrames, rings)

        chain.delays[0].delayTimeSeconds shouldBe 20.0
        chain.delays[0].effectiveDelaySeconds shouldBe 20.0
        (chain.delays[0].capacityFrames >= ceil(20.0 * sampleRate).toInt() + ResourceWarehouse.RING_MARGIN_FRAMES) shouldBe true
    }

    // ── Sound preservation: the ring's SIZE is not audible ──────────────────────────────────────

    "the shelf-rented master delay is BIT-IDENTICAL to the old time-plus-50-ms ring" {
        // The old code sized a private ring to `time + 0.05 s`; the shelf hands out a class-sized
        // one. DelayLine's read/write arithmetic is relative to the cursor, so the size cannot
        // reach the output as long as the tap fits — this row is the proof, block by block.
        val time = 0.3
        val feedback = 0.6
        val (rings, _) = shelf()
        val chain = MasterChain.build(delayDsl(time, feedback), sampleRate, blockFrames, rings)
        val old = DelayLine(maxDelaySeconds = time + 0.05, sampleRate = sampleRate, delayTimeSeconds = time, feedback = feedback)

        val busNew = StereoBuffer(blockFrames)
        val busOld = StereoBuffer(blockFrames)
        val sendOld = StereoBuffer(blockFrames)
        var energy = 0.0
        for (block in 0 until 400) { // 1.16 s: several echo periods
            val input = noise(blockFrames, seed = block + 1)
            for (i in 0 until blockFrames) {
                busNew.left[i] = input.left[i]; busNew.right[i] = input.right[i]
                busOld.left[i] = input.left[i]; busOld.right[i] = input.right[i]
                sendOld.left[i] = input.left[i] * 0.5; sendOld.right[i] = input.right[i] * 0.5 // wet
            }
            chain.process(busNew, blockFrames)
            old.process(sendOld, busOld, blockFrames)
            for (i in 0 until blockFrames) {
                busNew.left[i] shouldBe busOld.left[i]
                busNew.right[i] shouldBe busOld.right[i]
                energy += abs(busNew.left[i] - input.left[i]) // the wet part, not the dry copy
            }
        }
        // Positive control: the delay did something — the two buses agreeing on silence is no proof.
        energy shouldBeGreaterThan 1.0
    }

    // ── Refusal: dry, counted, no throw ─────────────────────────────────────────────────────────

    "a refused ring builds the chain WITHOUT the delay stage — counted, and it still processes" {
        val alloc = Recording(failing = true)
        val (rings, _) = shelf(alloc)
        val chain = MasterChain.build(
            MasterDsl.of(MasterStageDsl.Gain(gain = 2.0), MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.3)),
            sampleRate, blockFrames, rings,
        )

        chain.deniedRents shouldBe 1
        chain.delays.size shouldBe 0
        chain.isActive shouldBe true // the gain survived
        rings.failures shouldBe 1

        val bus = StereoBuffer(blockFrames)
        bus.left.fill(0.1)
        chain.process(bus, blockFrames)
        bus.left[0] shouldBe 0.2
    }

    "a hopeless master time (past Int range) is denied like a refused allocation, never a smaller ring" {
        val (rings, alloc) = shelf()
        val chain = MasterChain.build(delayDsl(60_000.0), sampleRate, blockFrames, rings)

        chain.deniedRents shouldBe 1
        chain.delays.size shouldBe 0
        alloc.asked shouldBe listOf(Int.MAX_VALUE)
    }

    // ── The return path: eviction restocks the shelf ────────────────────────────────────────────

    "an evicted chain returns its ring to the shelf, and the next master delay of that class takes it" {
        val (rings, alloc) = shelf()
        val registry = MasterRegistry()
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings)

        // Fill the cache with distinct delay masters; the ninth registration evicts the first.
        for (i in 0 until 8) {
            bus.register("m$i", delayDsl(0.3 + i * 0.001))
        }
        rings.shelfCount shouldBe 0
        val allocatedBefore = alloc.asked.size

        bus.register("m8", delayDsl(0.3))

        // One chain left the cache and its ring went back; the newcomer rented THAT ring.
        rings.hits shouldBe 1
        rings.shelfCount shouldBe 0
        alloc.asked.size shouldBe allocatedBefore // no allocation for the ninth
    }

    "releaseRings hands back exactly the chain's rings, and they are the instances the shelf lends next" {
        val (rings, _) = shelf()
        val chain = MasterChain.build(
            MasterDsl.of(
                MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.3),
                MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.7),
            ),
            sampleRate, blockFrames, rings,
        )
        val small = chain.delays[0].ring
        val large = chain.delays[1].ring

        chain.releaseRings(rings)

        rings.shelfCount shouldBe 2
        // Best-fit-up: a class-0 request takes the small ring, the next class-0 request the large one.
        rings.rent(1) shouldBeSameInstanceAs small
        rings.rent(1) shouldBeSameInstanceAs large
        rings.shelfCount shouldBe 0
    }
})
