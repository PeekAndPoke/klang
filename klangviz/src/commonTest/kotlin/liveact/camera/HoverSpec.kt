/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/** The drift every tenth of a second over ten minutes. */
private fun course(phase: Double): List<Double> = (0 until 6000).map { hover(t = it / 10.0, phase = phase) }

class HoverSpec : StringSpec({

    "it stays between -1 and 1, uses most of that, and is off to both sides about as much" {
        val course = course(phase = 0.0)

        course.max() shouldBeLessThan 1.0001
        course.min() shouldBeGreaterThan -1.0001
        course.max() shouldBeGreaterThan 0.9
        course.min() shouldBeLessThan -0.9
        course.average() shouldBe (0.0 plusOrMinus 0.02)
    }

    "it is almost steady: in a second it never goes further than about half the way to one side" {
        val perSecond = (0 until 6000).maxOf { abs(hover(t = it / 10.0 + 1.0, phase = 0.0) - hover(t = it / 10.0, phase = 0.0)) }

        perSecond shouldBeLessThan 0.52
        // and it does not tremble: from one picture to the next (a sixtieth of a second) under a hundredth
        (0 until 3600).maxOf { abs(hover(t = (it + 1) / 60.0, phase = 0.0) - hover(t = it / 60.0, phase = 0.0)) } shouldBeLessThan 0.01
    }

    "it never comes to rest: over any ten seconds it has moved" {
        val course = course(phase = 0.0)

        (0 until 59).minOf { s -> (0 until 100).sumOf { abs(course[s * 100 + it + 1] - course[s * 100 + it]) } } shouldBeGreaterThan 0.2
    }

    "two phases go their own ways, in the faster drift and in the slower one" {
        val a = course(phase = 0.0)
        val b = course(phase = 1.7)

        a.indices.sumOf { abs(a[it] - b[it]) } / a.size shouldBeGreaterThan 0.6
    }
})
