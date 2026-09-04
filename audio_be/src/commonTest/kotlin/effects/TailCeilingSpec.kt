/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * The content ceiling on its own: the recurrence, the two-window lag, the geometric decay, the
 * steady floor under sub-threshold input, self-oscillation, and the window arithmetic for periods
 * both longer and shorter than a block. `ClosedFormTailSpec` proves it against the real DSP with the
 * old scans as the oracle; this one pins the rules that make it a BOUND.
 */
class TailCeilingSpec : StringSpec({

    "never fed: no tail" {
        val c = TailCeiling()
        c.hasTail shouldBe false
        c.observe(inputPeak = 0.0, frames = 128, windowSamples = 1000.0, feedback = 0.5, lapsPerWindow = 1)
        c.hasTail shouldBe false
    }

    "fed once at feedback 0: reachable through the next window, then gone" {
        // A ring with fb 0 keeps a sample for one period; the ceiling keeps it visible for the
        // window it was written in and the one after (every read reaches at most one window back).
        val c = TailCeiling()
        c.observe(inputPeak = 0.5, frames = 100, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 1) // window 0 closes
        c.hasTail shouldBe true // previous = 0.5
        c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 1) // window 1 closes
        c.hasTail shouldBe false
    }

    "silent input decays the ceiling by |feedback| per window — the drain proof's geometric series" {
        val c = TailCeiling()
        c.observe(inputPeak = 1.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 1)
        // 1.0 → 0.5 → 0.25 … below 1e-5 after 17 halvings of the previous window's ceiling.
        var windows = 0
        while (c.hasTail) {
            c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 1)
            windows++
            (windows < 100) shouldBe true
        }
        windows shouldBe 17
        // Negative feedback decays the same: only the magnitude recirculates. At fb −0.9 a signed
        // version would pile the input up as 0.1× (1 + fb) and decay in a fraction of the windows.
        fun decayWindows(fb: Double): Int {
            val x = TailCeiling()
            x.observe(inputPeak = 1.0, frames = 100, windowSamples = 100.0, feedback = fb, lapsPerWindow = 2)
            var w = 0
            while (x.hasTail) {
                x.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = fb, lapsPerWindow = 2)
                w++
                (w < 1000) shouldBe true
            }
            return w
        }
        decayWindows(-0.9) shouldBe decayWindows(0.9)
    }

    "sub-threshold input is not 'silent': the ceiling converges to the true floor peak / (1 − fb)" {
        // 1e-5 in at fb 0.99 is a ring holding ~1e-3 (−60 dBFS) — audible energy a silence test
        // would have called empty (review round 1 of the closed form).
        val c = TailCeiling()
        repeat(2000) { c.observe(inputPeak = 0.00001, frames = 100, windowSamples = 100.0, feedback = 0.99, lapsPerWindow = 1) }
        c.hasTail shouldBe true
        // And it decays once the input really stops.
        var windows = 0
        while (c.hasTail) {
            c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.99, lapsPerWindow = 1)
            windows++
            (windows < 10000) shouldBe true
        }
        (windows > 400) shouldBe true // ln(1e-5/1e-3)/ln(0.99) ≈ 458
    }

    "|feedback| >= 1 never decays: the orbit is pinned until the owner turns the unit off" {
        val c = TailCeiling()
        c.observe(inputPeak = 0.1, frames = 100, windowSamples = 100.0, feedback = 1.0, lapsPerWindow = 1)
        repeat(100_000) { c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 1.0, lapsPerWindow = 1) }
        c.hasTail shouldBe true
    }

    "laps per window pile the input up through the shortest path: 4 laps at fb 0.5 is 1.875x the input" {
        // Freeverb's shortest comb recirculates twice per longest-comb window: a window's input can
        // be written once and come back inside the same window. Four laps here so the extra
        // content crosses a halving boundary and shows as exactly one more window of decay.
        fun decay(laps: Int): Int {
            val c = TailCeiling()
            c.observe(inputPeak = 0.4, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = laps)
            var w = 0
            while (c.hasTail) {
                c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = laps)
                w++
                (w < 100) shouldBe true
            }
            return w
        }
        // 0.4 → 16 halvings to 1e-5; 0.4 · 1.875 = 0.75 → 17.
        decay(1) shouldBe 16
        decay(4) shouldBe 17
    }

    "a window shorter than a block closes several times inside one observe; a period change is followed" {
        val c = TailCeiling()
        c.observe(inputPeak = 1.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        // 128 frames = 16 windows of 8: the 1.0 written in the first window has halved ~15 times
        // by the end of the block — still above 1e-5 (2^-15 ≈ 3e-5)...
        c.hasTail shouldBe true
        c.observe(inputPeak = 0.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        // ...and gone after the next block's 16 more.
        c.hasTail shouldBe false

        // A longer period from here on decays slower: the same content survives longer.
        val slow = TailCeiling()
        slow.observe(inputPeak = 1.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        slow.observe(inputPeak = 0.0, frames = 128, windowSamples = 100_000.0, feedback = 0.5, lapsPerWindow = 1)
        slow.hasTail shouldBe true
    }

    "reset(): the unit holds nothing" {
        val c = TailCeiling()
        c.observe(inputPeak = 1.0, frames = 100, windowSamples = 100.0, feedback = 0.9, lapsPerWindow = 1)
        c.reset()
        c.hasTail shouldBe false
    }

    "peakOf sees both channels, the sign, and only the first `frames`" {
        val b = StereoBuffer(8)
        TailCeiling.peakOf(b, 8) shouldBe 0.0
        b.right[5] = -0.3
        b.left[2] = 0.1
        TailCeiling.peakOf(b, 8) shouldBe 0.3
        TailCeiling.peakOf(b, 5) shouldBe 0.1 // index 5 is past the first five
    }
})
