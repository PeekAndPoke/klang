/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/**
 * C4.2 guard (docs/tasks-archive/2026-09/20260927-filter-unification.md): the phaser's `floor` knob follows the shared
 * wet/dry law, `dry = max(floor, cos²(w·π/2))`, on the cylinder-bus [Phaser]. (The voice strip's
 * per-voice phaser, the law's second path, retired with the Pipeline DSL in phase 3 step 9.)
 *
 * Method: the wet path of two phaser instances fed the same input is sample-identical (the
 * allpass state is input-determined), so `outA − outB = (dryC_A − dryC_B) · dry` exactly.
 */
class PhaserFloorLawSpec : StringSpec({

    val frames = 512
    val depth = 0.9
    // cos²(0.9·π/2) ≈ 0.0245 — well below the floors used here, so max() genuinely engages.
    val cos2 = cos(depth * PI / 2.0).let { it * it }

    fun noise(seed: Int): DoubleArray {
        val r = Random(seed)
        return DoubleArray(frames) { r.nextDouble() * 2.0 - 1.0 }
    }

    // ── cylinder-bus Phaser (stereo) ─────────────────────────────────────────

    fun runBusPhaser(floor: Double?, dry: DoubleArray): DoubleArray {
        val p = Phaser(sampleRate = 48000)
        p.rate = 1.0
        p.depth = depth
        if (floor != null) {
            p.floor = floor
        }
        val buf = StereoBuffer(frames)
        for (i in 0 until frames) {
            buf.left[i] = dry[i]
            buf.right[i] = dry[i]
        }
        p.process(buf, frames)
        return DoubleArray(frames) { buf.left[it] }
    }

    "bus phaser: default floor is 1.0 — bit-identical to an explicit floor(1.0)" {
        val dry = noise(11)
        val a = runBusPhaser(null, dry)
        val b = runBusPhaser(1.0, dry)
        for (i in 0 until frames) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    "bus phaser: floor scales the dry by max(floor, cos²(w·π/2)) — the C4 law" {
        val dry = noise(12)
        val a = runBusPhaser(0.3, dry) // dryC = max(0.3, cos2) = 0.3
        val b = runBusPhaser(0.0, dry) // dryC = cos2
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.3 - cos2) * dry[i] plusOrMinus 1e-12)
        }
    }

    "sanity: the wet path is audible in these fixtures (not a two-silent-renders pass)" {
        val dry = noise(23)
        val out = runBusPhaser(0.0, dry)
        var diff = 0.0
        for (i in 0 until frames) {
            val d = abs(out[i] - dry[i])
            if (d > diff) {
                diff = d
            }
        }
        (diff > 1e-3) shouldBe true
    }
})
