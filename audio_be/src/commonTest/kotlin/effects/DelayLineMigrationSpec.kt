/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.time.Duration.Companion.seconds

/**
 * `DelayLine.adoptHistory` on its own — the migration the resource warehouse performs when a ring
 * grows (`docs/plans/resource-warehouse.md` step 2c). `LazyRingSpec` proves the seam is bit-identical
 * through the effect at one arbitrary alignment; review round 1 asked for the alignments that
 * matter to be pinned directly: a source whose write cursor sits at 0, equal sizes (the branch
 * whose absence is a HANG, not a wrong sample), and the smaller-target direction, which a first cut
 * had wrong rather than truncated.
 *
 * The property, in one line: **"d samples ago" reads the same sample in the target as in the
 * source, for every d the target can reach.** Checked on the rings directly via the test seam.
 */
class DelayLineMigrationSpec : StringSpec({

    val sampleRate = 1000 // arbitrary; only frames matter here

    /** Sample t as it is written: distinct per t and INSIDE `softCap`'s identity region (|x| ≤ 0.95). */
    fun ramp(t: Int): Double = t * 1e-3

    /**
     * A ring whose contents are a recognisable ramp, written by driving [frames] samples through it.
     * The first cut ramped 0..249 unscaled — every store above 0.95 saturates to ~1.0 in the ring,
     * so every alignment "matched" and two mutations of the copy direction survived. Ones-vs-ones is
     * zeros-vs-zeros in a new costume; the positive control below now pins that samples differ.
     */
    fun rampedLine(size: Int, frames: Int): DelayLine {
        val line = DelayLine(StereoBuffer(size), sampleRate, delayTimeSeconds = 0.001, feedback = 0.0)
        val input = StereoBuffer(1)
        val output = StereoBuffer(1)
        for (t in 0 until frames) {
            input.left[0] = ramp(t)
            input.right[0] = -ramp(t)
            output.clear()
            line.process(input, output, 1)
        }
        return line
    }

    /** The sample written d frames ago, read straight off a ring through its cursor. */
    fun sampleAgo(line: DelayLine, d: Int): Double {
        var i = line.writePosForTest - d
        while (i < 0) i += line.capacityFrames
        return line.ring.left[i]
    }

    "positive control: the ramp survives the ring store, distinct and in the expected order" {
        val line = rampedLine(size = 100, frames = 250)
        sampleAgo(line, 1) shouldBe ramp(249)
        sampleAgo(line, 100) shouldBe ramp(150)
        line.ring.right[line.writePosForTest - 1] shouldBe -ramp(249)
    }

    "grow: every reachable 'd samples ago' reads the same sample after the migration" {
        val from = rampedLine(size = 100, frames = 250) // cursor at 50, ring holds 150..249
        val to = DelayLine(StereoBuffer(400), sampleRate)

        to.adoptHistory(from)

        for (d in 1..100) {
            withClue("d = $d") { sampleAgo(to, d) shouldBe sampleAgo(from, d) }
        }
        // The right channel came along too.
        to.ring.right[to.writePosForTest - 1] shouldBe from.ring.right[from.writePosForTest - 1]
    }

    "grow from a source whose cursor sits at 0 — the wrap-around start" {
        val from = rampedLine(size = 100, frames = 300) // exactly three revolutions: cursor back at 0
        from.writePosForTest shouldBe 0
        val to = DelayLine(StereoBuffer(250), sampleRate)

        to.adoptHistory(from)

        for (d in 1..100) {
            withClue("d = $d") { sampleAgo(to, d) shouldBe sampleAgo(from, d) }
        }
    }

    "equal sizes: the cursor wraps to 0, and processing a block afterwards terminates".config(timeout = 5.seconds) {
        // `writePos = if (n == bufferSize) 0 else n`. Dropping that branch leaves the cursor AT
        // bufferSize, process()'s chunk = min(length, bufferSize - pos) becomes 0, and its
        // `while (done < length)` spins forever on the audio thread. The timeout is the assertion.
        val from = rampedLine(size = 64, frames = 100)
        val to = DelayLine(StereoBuffer(64), sampleRate)

        to.adoptHistory(from)

        to.writePosForTest shouldBe 0
        for (d in 1..64) {
            withClue("d = $d") { sampleAgo(to, d) shouldBe sampleAgo(from, d) }
        }
        to.process(StereoBuffer(128), StereoBuffer(128), 128) // must return
    }

    "smaller target keeps the NEWEST samples, aligned — never the oldest, shifted" {
        // Review round 1: the first cut copied from the source's OLDEST sample forward, so a
        // smaller target kept the oldest slice and every tap read history shifted by the
        // difference — a pitch/time jump, not a truncation. Shrinks never happen today; the
        // function is simply right in both directions now.
        val from = rampedLine(size = 100, frames = 250)
        val to = DelayLine(StereoBuffer(40), sampleRate)

        to.adoptHistory(from)

        for (d in 1..40) {
            withClue("d = $d") { sampleAgo(to, d) shouldBe sampleAgo(from, d) }
        }
    }
})
