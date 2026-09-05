/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

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

    "several boundaries in one block: the block's peak is credited to every window it touches" {
        // Window 8, block 128: sixteen closes per call. A full-scale sample anywhere in the block
        // must still be a ceiling of 1.0 for the window the block continues — the first cut
        // credited the peak to the first window only and under-bounded by fb^16 (review round 2).
        val c = TailCeiling()
        c.observe(inputPeak = 1.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        c.hasTail shouldBe true
        // The next block, silent: the ceiling is ~1.0 at its start and decays 16 halvings inside it.
        c.observe(inputPeak = 0.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        c.hasTail shouldBe true // 2^-16 ≈ 1.5e-5 is still over the threshold
        c.observe(inputPeak = 0.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        c.hasTail shouldBe false

        // A longer period from here on decays slower: the same content survives longer.
        val slow = TailCeiling()
        slow.observe(inputPeak = 1.0, frames = 128, windowSamples = 8.0, feedback = 0.5, lapsPerWindow = 1)
        slow.observe(inputPeak = 0.0, frames = 128, windowSamples = 100_000.0, feedback = 0.5, lapsPerWindow = 1)
        slow.hasTail shouldBe true
    }

    "a block that does not divide the window carries its remainder — boundaries land on the sample, not the block" {
        // 300-sample windows, 128-frame blocks. The fed block closes no window; the first close
        // is at sample 300 (block 3), carrying 1.0 into `previous`; the k-th close at 300·k leaves
        // previous = 0.5^(k−1), below 1e-5 from k = 18, i.e. sample 5400, inside block 43.
        // Dropping the carry (elapsed = 0 each block) would never close a window at all.
        val c = TailCeiling()
        c.observe(inputPeak = 1.0, frames = 128, windowSamples = 300.0, feedback = 0.5, lapsPerWindow = 1)
        var blocks = 1
        while (c.hasTail) {
            c.observe(inputPeak = 0.0, frames = 128, windowSamples = 300.0, feedback = 0.5, lapsPerWindow = 1)
            blocks++
            (blocks < 500) shouldBe true
        }
        blocks shouldBe 43
    }

    "a period that shrinks under a running window seals it ONCE, undecayed — the shorter tap still reaches it" {
        // Review round 3: the elapsed time carried from the old window closed `elapsed / newWindow`
        // new windows at once, each decaying content that had never recirculated. Nine blocks
        // into a 1000-sample window, then the window drops to 100: the content written 100
        // samples ago is exactly what the new tap reads, and the ceiling must still say so.
        val c = TailCeiling()
        repeat(9) { c.observe(inputPeak = 1.0, frames = 100, windowSamples = 1000.0, feedback = 0.5, lapsPerWindow = 1) }
        c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 1)
        c.hasTail shouldBe true // sealed at 1.0, then ONE real close: previous 0.5

        // From here it halves per new window: previous = 0.5^(1+k) ≤ 1e-5 from k = 16. Ten stale
        // closes (the old behaviour) would have left only 7; a seal that decays once, 15.
        var more = 0
        while (c.hasTail) {
            c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 1)
            more++
            (more < 100) shouldBe true
        }
        more shouldBe 16
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


    "a non-finite input peak counts as 'louder than any audio', never as NaN — no tail is cut, no orbit pinned by accident" {
        // Review round 2: peakOf propagates +Inf; at feedback 0 `Inf · 0` made the ceiling NaN,
        // NaN compares false, and the effect reported no tail while the ring still rang.
        val c = TailCeiling()
        c.observe(inputPeak = Double.POSITIVE_INFINITY, frames = 100, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 2)
        c.hasTail shouldBe true // finite, saturated, still a tail (the window closed; previous holds it)
        c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 2)
        c.hasTail shouldBe false // and at feedback 0 it is gone after the next window, like any content

        // With feedback it decays from the saturation value in a finite number of windows.
        val d = TailCeiling()
        d.observe(inputPeak = Double.POSITIVE_INFINITY, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 2)
        var w = 0
        while (d.hasTail) {
            d.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 2)
            w++
            (w < 1000) shouldBe true
        }
        (w in 30..40) shouldBe true // log2(1e6 · 1.5 / 1e-5) ≈ 37 halvings

        // NaN samples are skipped by peakOf (a NaN compares false), so they contribute nothing.
        val b = StereoBuffer(4)
        b.left[1] = Double.NaN
        b.right[2] = 0.2
        TailCeiling.peakOf(b, 4) shouldBe 0.2
    }


    "self-oscillation then a sane feedback again: the ceiling saturates, so it can still decay — and feedback 0 does not NaN it" {
        // Review round 2: without a cap, fb 10 overflowed the ceiling to +Inf in ~3 s; a later
        // fb 0.5 kept it at Inf (pinned for life) and fb 0.0 made it NaN (no tail while ringing).
        val c = TailCeiling()
        c.observe(inputPeak = 0.5, frames = 100, windowSamples = 100.0, feedback = 10.0, lapsPerWindow = 2)
        repeat(1000) { c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 10.0, lapsPerWindow = 2) }
        c.hasTail shouldBe true

        var w = 0
        while (c.hasTail) {
            c.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 0.5, lapsPerWindow = 2)
            w++
            (w < 1000) shouldBe true
        }
        (w in 30..40) shouldBe true // from the saturation value, not from infinity

        val z = TailCeiling()
        z.observe(inputPeak = 0.5, frames = 100, windowSamples = 100.0, feedback = 10.0, lapsPerWindow = 2)
        repeat(1000) { z.observe(inputPeak = 0.0, frames = 100, windowSamples = 100.0, feedback = 10.0, lapsPerWindow = 2) }
        // At feedback 0 the ring emits its content once more, during the window in progress...
        z.observe(inputPeak = 0.0, frames = 50, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 2)
        z.hasTail shouldBe true // previous still holds the saturated value inside this window
        // ...and holds nothing after that window closes: a real false, not a NaN one.
        z.observe(inputPeak = 0.0, frames = 50, windowSamples = 100.0, feedback = 0.0, lapsPerWindow = 2)
        z.hasTail shouldBe false
    }
})
