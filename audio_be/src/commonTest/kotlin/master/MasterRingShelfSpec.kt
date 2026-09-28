/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * Resource warehouse step 2e at the OUTPUT: the master bus rents its delay rings from the backend's
 * shelf, and a chain its bounded cache evicts returns them (`docs/tasks-archive/2026-09/20260927-resource-warehouse.md`).
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
        KatalystStageDsl.Delay(wet = IgnitorDsl.Constant(0.5), time = IgnitorDsl.Constant(time), feedback = IgnitorDsl.Constant(0.3))
    )

    "a master leaving service returns its ring at once, and the next master delay of that class takes it" {
        val alloc = Recording()
        val rings = SizedBuffers.forRings(sampleRate, allocate = alloc.allocate)
        val registry = KatalystRegistry()
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings)

        // Before the engine's first block every request is adopted at once, and the chain it
        // replaces leaves service there and then. A chain is built when its request lands.
        registry.register("m0", delayDsl(0.3))
        registry.register("m1", delayDsl(0.301))
        registry.register("m2", delayDsl(0.302))

        bus.requestSwap("m0")
        rings.shelfCount shouldBe 0

        bus.requestSwap("m1")
        // m1 had to allocate (m0 still held its ring while m1 was built), and m0's ring came back.
        alloc.asked.size shouldBe 2
        rings.shelfCount shouldBe 1

        bus.requestSwap("m2")
        // The newcomer rented m0's ring, and m1's went back in its place: no third allocation.
        rings.hits shouldBe 1
        rings.shelfCount shouldBe 1
        alloc.asked.size shouldBe 2
    }
})
