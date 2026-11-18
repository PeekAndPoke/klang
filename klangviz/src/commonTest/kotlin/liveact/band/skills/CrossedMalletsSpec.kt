/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Timed

/** A note of a part, for the crossed mallets: only when it begins. */
private class CrossPlay(override val t: Double) : Timed

/** Notes every half second from [from] to [until] seconds: a phrase. */
private fun phrase(from: Double, until: Double): List<CrossPlay> = (0..((until - from) * 2).toInt()).map { CrossPlay(from + it * 0.5) }

class CrossedMalletsSpec : StringSpec({

    val mallets = CrossedMallets(hold = 1.5, move = 1.2)

    // two phrases, from 10 s to 20 s and from 50 s to 60 s; the arms rise to play from 4.9 s before a note, and are
    // down from playing 2.6 s after it
    val part = phrase(from = 10.0, until = 20.0) + phrase(from = 50.0, until = 60.0)

    fun at(t: Double, plays: List<Timed> = part, arrives: Double = -5.0): Double =
        mallets.at(t = t, plays = plays, rises = 4.9, falls = 2.6, arrives = arrives)

    "between the notes of a phrase, never" {
        withClue("within the phrases") {
            (1000 until 2000).maxOf { at(it / 100.0) } shouldBe 0.0
            (5000 until 6000).maxOf { at(it / 100.0) } shouldBe 0.0
        }
    }

    "after a phrase: crossed from its last note on, held once the arms are down, then down to hanging" {
        // down from playing at 22.6 s, held to 24.1 s, hanging from 25.3 s (looked at a little inside each edge)
        withClue("after the first phrase") {
            listOf(20.0, 21.0, 22.6, 24.0).map { at(it) } shouldBe listOf(1.0, 1.0, 1.0, 1.0)
            at(24.7) shouldBe (0.5 plusOrMinus 1e-9)
            listOf(25.4, 30.0, 40.0).map { at(it) } shouldBe listOf(0.0, 0.0, 0.0)

            val falling = (2410..2530).map { at(it / 100.0) }

            falling shouldBe falling.sortedDescending()
        }
    }

    "before a phrase: from hanging into the pose, held, and crossed until the arms rise to play" {
        // into the pose from 42.4 s, all there at 43.6 s, the arms rising from it at 45.1 s, the note at 50 s
        withClue("before the second phrase") {
            listOf(41.0, 42.3).map { at(it) } shouldBe listOf(0.0, 0.0)
            at(43.0) shouldBe (0.5 plusOrMinus 1e-9)
            listOf(43.7, 45.1, 49.99).map { at(it) } shouldBe listOf(1.0, 1.0, 1.0)

            val rising = (4240..4360).map { at(it / 100.0) }

            rising shouldBe rising.sorted()
        }
    }

    "before the first note, only where the mallets begin to cross once she is at her place" {
        // into the pose from 2.4 s before the first note at 10 s
        withClue("arrived well before") {
            at(3.0) shouldBe (0.5 plusOrMinus 1e-9)
            at(9.9) shouldBe 1.0
        }

        withClue("arrived just in time") {
            at(3.0, arrives = 2.3) shouldBe (0.5 plusOrMinus 1e-9)
        }

        withClue("arrived too late") {
            (-500 until 1000).maxOf { at(it / 100.0, arrives = 3.0) } shouldBe 0.0
        }
    }

    "after the last note, once more" {
        withClue("after the last phrase") {
            listOf(60.0, 62.6, 64.0).map { at(it) } shouldBe listOf(1.0, 1.0, 1.0)
            at(64.7) shouldBe (0.5 plusOrMinus 1e-9)
            listOf(65.4, 100.0, 1000.0).map { at(it) } shouldBe listOf(0.0, 0.0, 0.0)
        }
    }

    "only at a pause where the arms hang for a while between the crossings, else never" {
        // after a note at 100 s the arms hang from 105.3 s; before one at b they cross from b - 7.6 s: so the arms
        // hang for 1.2 s (the move) from a pause of 14.1 s on
        val short = listOf(CrossPlay(100.0), CrossPlay(113.9))
        val long = listOf(CrossPlay(100.0), CrossPlay(114.2))

        withClue("a pause of 13.9 s") {
            (10000 until 11390).maxOf { at(it / 100.0, plays = short) } shouldBe 0.0
        }

        withClue("a pause of 14.2 s") {
            at(102.0, plays = long) shouldBe 1.0
            at(113.0, plays = long) shouldBe 1.0
            (10000..11420).minOf { at(it / 100.0, plays = long) } shouldBe 0.0
        }
    }

    "a part without notes never" {
        listOf(-10.0, 0.0, 10.0).map { at(it, plays = emptyList()) } shouldBe listOf(0.0, 0.0, 0.0)
        // (and the part above is really crossed somewhere)
        (0 until 7000).maxOf { at(it / 10.0) } shouldBeGreaterThan 0.99
    }
})
