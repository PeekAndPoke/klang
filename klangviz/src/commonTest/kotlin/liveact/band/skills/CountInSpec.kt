/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Timed

/** A note of a part, for the count in: only when it begins. */
private class CountPlay(override val t: Double) : Timed

/** Notes every quarter of a second from [from] to [until] seconds: a phrase. */
private fun notes(from: Double, until: Double): List<CountPlay> = (0..((until - from) * 4).toInt()).map { CountPlay(from + it * 0.25) }

class CountInSpec : StringSpec({

    val count = CountIn(pause = 2.0)

    // a beat of half a second, so a bar of two: the phrase from 10 s is counted in from 8 s, on 8, 8.5, 9 and 9.5
    fun at(t: Double, plays: List<Timed>, arrives: Double = -100.0): Double =
        count.at(t = t, plays = plays, beat = 0.5, arrives = arrives)

    val part = notes(from = 0.0, until = 4.0) + notes(from = 10.0, until = 14.0)

    "the last bar before a phrase: a nod on each of its four beats, and between them nearly none" {
        withClue("on the beats") {
            listOf(8.0, 8.5, 9.0, 9.5).map { at(it, part) } shouldBe listOf(1.0, 1.0, 1.0, 1.0)
        }

        withClue("a tenth of a beat off, still well down") {
            at(8.05, part) shouldBe (0.7788 plusOrMinus 1e-3)
        }

        withClue("between the beats, before the first and after the last") {
            listOf(8.25, 8.75, 9.25, 7.75, 9.75, 9.99).maxOf { at(it, part) } shouldBeLessThan 0.01
        }

        withClue("well before the bar, within the phrase, and after the last note") {
            listOf(5.0, 7.0, 7.7, 10.0, 12.1, 13.9, 14.5, 30.0).map { at(it, part) } shouldBe List(8) { 0.0 }
        }
    }

    "only before a phrase: after a pause of at least two bars, the edge included" {
        val short = notes(from = 0.0, until = 6.25) + notes(from = 10.0, until = 14.0)
        val edge = notes(from = 0.0, until = 6.0) + notes(from = 10.0, until = 14.0)

        withClue("a pause of 3.75 s") {
            (780 until 1000).maxOf { at(it / 100.0, short) } shouldBe 0.0
        }

        withClue("a pause of just two bars") {
            at(9.0, edge) shouldBe 1.0
        }
    }

    "the bar goes by the beat: at another tempo the four nods fall on its own beats" {
        // a beat of 0.4 s: the phrase from 10 s is counted in from 8.4 s
        val fast = notes(from = 0.0, until = 4.0) + notes(from = 10.0, until = 14.0)

        withClue("on the beats of a bar of 1.6 s") {
            listOf(8.4, 8.8, 9.2, 9.6).map { count.at(t = it, plays = fast, beat = 0.4, arrives = -100.0) } shouldBe
                listOf(1.0, 1.0, 1.0, 1.0)
        }

        withClue("where a bar of two seconds would have counted") {
            listOf(8.0, 8.1).map { count.at(t = it, plays = fast, beat = 0.4, arrives = -100.0) } shouldBe listOf(0.0, 0.0)
        }
    }

    "a pause shorter than the bar counted in and the half beat before it is refused" {
        shouldThrow<IllegalArgumentException> { CountIn(pause = 1.1) }
        CountIn(pause = CountIn.LEAST_PAUSE).pause shouldBe CountIn.LEAST_PAUSE
    }

    "before the first note, only where the counted bar begins once the member is at their place" {
        val first = notes(from = 10.0, until = 14.0)

        withClue("arrived before the bar") {
            listOf(8.0, 9.5).map { at(it, first, arrives = 7.0) } shouldBe listOf(1.0, 1.0)
            at(9.0, first, arrives = 8.0) shouldBe 1.0
        }

        withClue("arrived within the bar") {
            (780 until 1000).maxOf { at(it / 100.0, first, arrives = 8.01) } shouldBe 0.0
        }

        withClue("no notes at all") {
            at(9.0, emptyList()) shouldBe 0.0
        }
    }
})
