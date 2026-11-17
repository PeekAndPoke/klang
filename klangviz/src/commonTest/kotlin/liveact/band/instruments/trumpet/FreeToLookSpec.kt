/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.song.Note
import kotlin.math.abs

private fun note(t: Double, d: Double) = Note(t = t, midi = 67, v = 1.0, d = d)

/**
 * Three phrases: four notes from 20 s on with pauses of 1, 4 and 5.5 s between them (the trumpet comes down in the
 * longer ones, and the phrase goes on), then 30 s of nothing, then two notes 6.5 s apart (two phrases of a note).
 */
private val NOTES = listOf(
    note(t = 20.0, d = 1.0),
    note(t = 22.0, d = 2.0),
    note(t = 28.0, d = 1.0),
    note(t = 34.5, d = 1.5),
    note(t = 66.0, d = 1.0),
    note(t = 73.5, d = 0.5),
)

class FreeToLookSpec : StringSpec({

    val times = (0 until 9000).map { it * 0.01 }

    "the trumpet: up at her lips from just before a note to a little after it, down between phrases" {
        trumpetUp(notes = NOTES, t = 10.0) shouldBe 0.0
        trumpetUp(notes = NOTES, t = 19.1) shouldBe 0.0
        trumpetUp(notes = NOTES, t = 19.85) shouldBe 1.0
        trumpetUp(notes = NOTES, t = 23.5) shouldBe 1.0
        // (in the pause of four seconds it comes down and goes up again)
        trumpetUp(notes = NOTES, t = 26.0) shouldBe 0.0
        trumpetUp(notes = NOTES, t = 36.6) shouldBe 1.0
        trumpetUp(notes = NOTES, t = 37.7) shouldBe 0.0
        trumpetUp(notes = emptyList(), t = 5.0) shouldBe 0.0
    }

    "a pause of exactly six seconds is still within a phrase; a little more ends it" {
        val within = listOf(note(t = 10.0, d = 1.0), note(t = 17.0, d = 1.0))
        val apart = listOf(note(t = 10.0, d = 1.0), note(t = 17.01, d = 1.0))

        withClue("in a pause of six seconds") {
            freeToLook(notes = within, t = 14.0) shouldBe 0.0
        }

        withClue("in a pause of a little more than six seconds") {
            freeToLook(notes = apart, t = 14.0) shouldBe 1.0
        }
    }

    "her head is free to look only where the trumpet is down, and lets go of a look before it comes up" {
        for (t in times) {
            val free = freeToLook(notes = NOTES, t = t)

            withClue("at $t s") {
                if (free > 0) {
                    // the trumpet is down, and stays down for a third of a second more at least
                    trumpetUp(notes = NOTES, t = t) shouldBe 0.0
                    trumpetUp(notes = NOTES, t = t + 0.34) shouldBe 0.0
                }
            }
        }
    }

    "not within a phrase, all the way between phrases" {
        // within the first phrase, also in its pauses where the trumpet is down
        withClue("within a phrase") {
            (1900..3700).maxOf { freeToLook(notes = NOTES, t = it / 100.0) } shouldBe 0.0
        }

        // between phrases: before the first, between the two, after the last
        withClue("between phrases") {
            listOf(5.0, 17.0, 39.0, 50.0, 62.0, 70.0, 77.0, 500.0).map { freeToLook(notes = NOTES, t = it) } shouldBe
                List(8) { 1.0 }
        }

        freeToLook(notes = emptyList(), t = 3.0) shouldBe 1.0
    }

    "the head lets go of a look and takes it up again eased, never with a jump" {
        val free = times.map { freeToLook(notes = NOTES, t = it) }

        withClue("the largest step in a hundredth of a second") {
            free.zipWithNext { a, b -> abs(b - a) }.max() shouldBeLessThan 0.02
        }

        // it lets go before a phrase: over 1.3 s, done 1.2 s before the first note; free again after the trumpet is
        // down, over a second
        freeToLook(notes = NOTES, t = 17.5) shouldBe 1.0
        freeToLook(notes = NOTES, t = 18.15) shouldBe (0.5 plusOrMinus 1e-9)
        freeToLook(notes = NOTES, t = 18.85) shouldBe 0.0
        freeToLook(notes = NOTES, t = 37.6) shouldBe 0.0
        freeToLook(notes = NOTES, t = 38.2) shouldBe (0.5 plusOrMinus 1e-9)
        freeToLook(notes = NOTES, t = 38.8) shouldBe 1.0

        // and it is free in some of the time, not in the rest
        free.count { it == 0.0 } shouldBeGreaterThan 2000
        free.count { it == 1.0 } shouldBeGreaterThan 4000
    }
})
