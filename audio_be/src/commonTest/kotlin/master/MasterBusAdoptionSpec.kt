/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * Master round M1, pinned at the bus itself rather than end to end: the FIRST master is adopted
 * at full weight, and the discriminator is "has the owning engine rendered a block", checked in
 * the exact order the engine calls it.
 *
 * The end-to-end row in `MasterBusTest` proves the audible effect, but it cannot see the
 * ORDERING — moving `markRendered()` to before `scheduler.process` survives it. This spec is
 * that mutation's designated killer.
 */
class MasterBusAdoptionSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    fun newBus(): MasterBus {
        val registry = MasterRegistry()
        registry.register("loud", MasterDsl.of(MasterStageDsl.Gain(gain = 4.0)))
        return MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry)
    }

    fun renderPeak(bus: MasterBus): Double {
        val buf = StereoBuffer(blockFrames)
        for (i in 0 until blockFrames) {
            buf.left[i] = 0.1
            buf.right[i] = 0.1
        }
        bus.process(buf, blockFrames)

        var peak = 0.0
        for (i in 0 until blockFrames) {
            val v = if (buf.left[i] < 0.0) -buf.left[i] else buf.left[i]
            if (v > peak) {
                peak = v
            }
        }
        return peak
    }

    "a master arriving before the first rendered block is adopted at FULL weight" {
        // The crossfade protects a swap between two AUDIBLE chains. Here nothing has been
        // rendered, so there is nothing to fade from and nothing that could click — and the
        // fade was itself audible: it ramped a song's opening 60 ms up from unmastered.
        val bus = newBus()
        bus.requestSwap("loud")

        // 0.1 x gain 4.0, in the very first block. Under the old always-fade behaviour this
        // block sat at the start of the ramp and read ~0.1.
        renderPeak(bus) shouldBe (0.4 plusOrMinus 1e-9)
    }

    "a master arriving AFTER a rendered block still crossfades" {
        // The mid-song case: there IS audible signal, so a hard cut would step. This is the
        // half that `markRendered()`'s placement decides — call it before the scheduler
        // instead of after and the FIRST master lands here too, which is the bug M1 fixed.
        val bus = newBus()
        bus.markRendered()
        bus.requestSwap("loud")

        // Still ramping: the first block is far below the settled 0.4.
        renderPeak(bus) shouldBeLessThan 0.2
    }
})
