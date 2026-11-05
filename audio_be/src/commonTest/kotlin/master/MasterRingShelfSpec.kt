/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * Resource warehouse step 2e at the OUTPUT: the master bus rents its delay rings from the backend's
 * shelf, and a chain its bounded cache evicts returns them (KLANG-00230 (resource warehouse)).
 *
 * Since phase 3 step 12 C3 the output runs a `KatalystChain`, so the ring a master delay rents, its
 * size class and its refusal are the orbit delay's own and are pinned where that stage is
 * (`LazyRingSpec`). What stays here is the host's half: a chain leaving service returns what it
 * rented at once (eager retire, step 12 C4), not when the cache evicts it.
 */
class MasterRingShelfSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // A recording double that HOLDS the allocator lambda rather than implementing the function
    // type: Kotlin/JS forbids a class implementing `(Int) -> T`, and this file runs there too.
    class Recording {
        val asked = mutableListOf<Int>()
        val allocate: (Int) -> StereoBuffer? = { frames ->
            asked += frames
            StereoBuffer(frames)
        }
    }

    fun delayDsl(time: Double) = KatalystDsl.of(
        KatalystStageDsl.Delay(wet = KatalystKnobDsl.Constant(0.5), time = KatalystKnobDsl.Constant(time), feedback = KatalystKnobDsl.Constant(0.3))
    )

    "a master leaving service returns its ring at once, and the next master delay of that class takes it" {
        val alloc = Recording()
        val rings = SizedBuffers.forRings(sampleRate, allocate = alloc.allocate)
        val registry = KatalystRegistry()
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings)

        // A chain rents its ring at its first configure, in the bus's first block after it lands, and returns it when it
        // leaves service (at the end of its drain, once the bus has rendered).
        registry.register("m0", delayDsl(0.3))
        registry.register("m1", delayDsl(0.301))
        registry.register("m2", delayDsl(0.302))

        val silence = StereoBuffer(blockFrames)

        fun blocksUntilSettled() {
            var guard = 0

            do {
                silence.clear()
                bus.process(silence, blockFrames)
                bus.markRendered()
                guard++
            } while (!bus.chainSwap.settled && guard < 20000)

            bus.chainSwap.settled shouldBe true
        }

        bus.requestSwap("m0")
        blocksUntilSettled()
        alloc.asked.size shouldBe 1
        rings.shelfCount shouldBe 0

        bus.requestSwap("m1")
        blocksUntilSettled()
        // m1 had to allocate (m0 still held its ring while m1 rented), and m0's ring came back.
        alloc.asked.size shouldBe 2
        rings.shelfCount shouldBe 1

        bus.requestSwap("m2")
        blocksUntilSettled()
        // The newcomer rented m0's ring, and m1's went back in its place: no third allocation.
        rings.hits shouldBe 1
        rings.shelfCount shouldBe 1
        alloc.asked.size shouldBe 2
    }
})
