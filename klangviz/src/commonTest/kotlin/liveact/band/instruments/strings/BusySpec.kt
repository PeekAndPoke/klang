/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Timed
import kotlin.math.abs

private class At(override val t: Double) : Timed

private fun every(seconds: Double, until: Double): List<At> = generateSequence(0.0) { it + seconds }.takeWhile { it <= until }.map { At(it) }.toList()

class BusySpec : StringSpec({

    "a note does not make the body jump: a frame before it and a frame after it the body is as busy" {
        val slow = every(seconds = 3.0, until = 30.0)

        for (note in slow) {
            val before = slow.busyAt(note.t - 0.01) { 1.0 }
            val after = slow.busyAt(note.t + 0.01) { 1.0 }

            abs(after - before) shouldBeLessThan 0.001
        }

        // and nowhere at all does it step, at a frame's pace
        for (i in 0 until 3000) {
            abs(slow.busyAt((i + 1) / 100.0) { 1.0 } - slow.busyAt(i / 100.0) { 1.0 }) shouldBeLessThan 0.01
        }
    }

    "a lone note stirs the body a little, most at its moment, and not at all far from it" {
        val one = listOf(At(10.0))

        one.busyAt(10.0) { 1.0 } shouldBe (BUSY_EACH plusOrMinus 1e-12)
        one.busyAt(9.0) { 1.0 } shouldBeLessThan BUSY_EACH
        one.busyAt(9.0) { 1.0 } shouldBeGreaterThan 0.0
        one.busyAt(11.0) { 1.0 } shouldBe (one.busyAt(9.0) { 1.0 } plusOrMinus 1e-12)
        one.busyAt(10.0 - BUSY_REACH) { 1.0 } shouldBe (0.0 plusOrMinus 1e-12)
        one.busyAt(13.0) { 1.0 } shouldBe 0.0
    }

    "a run of notes keeps the body going, and a soft note counts for less" {
        every(seconds = 0.25, until = 20.0).busyAt(10.0) { 1.0 } shouldBe 1.0
        listOf(At(10.0)).busyAt(10.0) { 0.5 } shouldBe (BUSY_EACH / 2 plusOrMinus 1e-12)
    }

    "nobody is busy without notes, or at no time" {
        emptyList<At>().busyAt(3.0) { 1.0 } shouldBe 0.0
        listOf(At(1.0)).busyAt(Double.NaN) { 1.0 } shouldBe 0.0
    }
})
