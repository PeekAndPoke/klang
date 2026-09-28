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
 * (`LazyRingSpec`). What stays here is the host's half: the cache returns what it drops.
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

    "an evicted chain returns its ring to the shelf, and the next master delay of that class takes it" {
        val alloc = Recording()
        val rings = SizedBuffers.forRings(sampleRate, allocate = alloc.allocate)
        val registry = KatalystRegistry()
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings)

        // Fill the cache with distinct delay masters; the ninth REQUEST evicts the first. A chain
        // is built on its first request, not at registration (one registry for both positions).
        for (i in 0 until 8) {
            registry.register("m$i", delayDsl(0.3 + i * 0.001))
            bus.requestSwap("m$i")
        }
        rings.shelfCount shouldBe 0
        val allocatedBefore = alloc.asked.size

        registry.register("m8", delayDsl(0.3))
        bus.requestSwap("m8")

        // One chain left the cache and its ring went back; the newcomer rented THAT ring.
        rings.hits shouldBe 1
        rings.shelfCount shouldBe 0
        alloc.asked.size shouldBe allocatedBefore // no allocation for the ninth
    }
})
