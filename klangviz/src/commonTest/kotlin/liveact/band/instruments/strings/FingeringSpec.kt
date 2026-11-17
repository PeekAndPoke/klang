/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

class FingeringSpec : StringSpec({

    "every note is held where it sounds: on a string at a fret under the four fingers, or open" {
        val strums = listOf(40, 43, 47, 52, 55, 59, 64, 67, 71, 76, 72, 69, 62, 57, 50, 45, 41).map { listOf(it) }
        val grips = Tuning.GUITAR.grips(strums)

        grips.size shouldBe strums.size

        grips.zip(strums).forEach { (grip, strum) ->
            grip.stops.size shouldBe 1

            val stop = grip.stops.single()

            Tuning.GUITAR.open[stop.string] + stop.fret shouldBe strum.single()
            (stop.fret == 0 || stop.fret in grip.position..(grip.position + Tuning.SPAN)) shouldBe true
        }
    }

    "a riff on the low strings stays in the first position and uses the open strings" {
        val grips = Tuning.GUITAR.grips(listOf(40, 43, 45, 40, 47, 48).map { listOf(it) })

        grips.map { it.position }.distinct() shouldBe listOf(1)
        grips.map { it.stops.single() } shouldBe listOf(
            Stop(string = 0, fret = 0), Stop(string = 0, fret = 3), Stop(string = 1, fret = 0),
            Stop(string = 0, fret = 0), Stop(string = 1, fret = 2), Stop(string = 1, fret = 3),
        )
    }

    "a tune high up is played up the neck in one position: a lower note goes to a lower string" {
        val grips = Tuning.GUITAR.grips(listOf(76, 74, 72, 71, 72, 74).map { listOf(it) })

        // the tenth position holds all of it: the e string for the two highest, the b string for the others
        grips.map { it.position }.distinct() shouldBe listOf(10)
        grips.map { it.stops.single() } shouldBe listOf(
            Stop(string = 5, fret = 12), Stop(string = 5, fret = 10), Stop(string = 4, fret = 13),
            Stop(string = 4, fret = 12), Stop(string = 4, fret = 13), Stop(string = 5, fret = 10),
        )
    }

    "the hand moves only where the notes ask for it, and comes back down where a long passage can be played low" {
        // one note at the twelfth fret, then the open e string's note again and again: it could stay up there
        // (the g string at the ninth fret), and for a few notes it does; for many it comes down
        val few = Tuning.GUITAR.grips(listOf(listOf(76)) + List(4) { listOf(64) })
        val many = Tuning.GUITAR.grips(listOf(listOf(76)) + List(60) { listOf(64) })

        few.map { it.position }.distinct() shouldBe listOf(9)
        few.last().stops shouldBe listOf(Stop(string = 3, fret = 9))

        many.first().position shouldBeGreaterThanOrEqual 9
        many.last() shouldBe Grip(position = 1, stops = listOf(Stop(string = 5, fret = 0)))
        // and it moves once, not from note to note
        many.map { it.position }.zipWithNext().count { (a, b) -> a != b } shouldBe 1
    }

    "a chord: each note on a string of its own, the strings rising with the notes" {
        // E major as every beginner holds it
        Tuning.GUITAR.grips(listOf(listOf(64, 40, 52, 47, 59, 56))).single() shouldBe Grip(
            position = 1,
            stops = listOf(
                Stop(string = 0, fret = 0), Stop(string = 1, fret = 2), Stop(string = 2, fret = 2),
                Stop(string = 3, fret = 1), Stop(string = 4, fret = 0), Stop(string = 5, fret = 0),
            ),
        )
    }

    "a note that finds no string of its own is left out" {
        // the low e and the f beside it both lie on the lowest string only
        Tuning.GUITAR.grips(listOf(listOf(40, 41))).single().stops.size shouldBe 1
    }

    "a note below the lowest string or above the last fret is played octaves nearer" {
        Tuning.GUITAR.fitted(40) shouldBe 40
        Tuning.GUITAR.fitted(79) shouldBe 79
        Tuning.GUITAR.fitted(39) shouldBe 51
        Tuning.GUITAR.fitted(16) shouldBe 40
        Tuning.GUITAR.fitted(80) shouldBe 68
        Tuning.GUITAR.fitted(100) shouldBe 76

        // the bass's low e, asked of a guitar: its own low e
        Tuning.GUITAR.grips(listOf(listOf(28))).single() shouldBe Grip(position = 1, stops = listOf(Stop(string = 0, fret = 0)))
    }

    "notes that are the same once they are brought within the instrument are one note" {
        // a d and the d an octave below the bass's lowest string: both its open d string
        Tuning.BASS.grips(listOf(listOf(26, 38))).single() shouldBe Grip(position = 1, stops = listOf(Stop(string = 2, fret = 0)))
    }

    "where the hand has to go up the neck it goes at once, not in steps" {
        // from the eighth position, two notes on the highest string: the first can be had two frets up, the second
        // only four up. One move of four, though the hand is then high a note sooner
        val grips = Tuning.GUITAR.grips(
            strums = listOf(listOf(75), listOf(77), listOf(79)),
            held = listOf(Grip(position = 8, stops = listOf(Stop(string = 5, fret = 11)))),
        )

        grips.map { it.position } shouldBe listOf(8, 12, 12)
    }

    "a bass: four strings, an octave below" {
        Tuning.BASS.grips(listOf(28, 31, 33, 38, 43, 45).map { listOf(it) }) shouldBe listOf(
            Grip(position = 1, stops = listOf(Stop(string = 0, fret = 0))),
            Grip(position = 1, stops = listOf(Stop(string = 0, fret = 3))),
            Grip(position = 1, stops = listOf(Stop(string = 1, fret = 0))),
            Grip(position = 1, stops = listOf(Stop(string = 2, fret = 0))),
            Grip(position = 1, stops = listOf(Stop(string = 3, fret = 0))),
            Grip(position = 1, stops = listOf(Stop(string = 3, fret = 2))),
        )
    }

    "what was held stays as it was, and the rest goes on from it" {
        val strums = List(4) { listOf(64) }
        val was = Grip(position = 5, stops = listOf(Stop(string = 4, fret = 5)))

        // alone, the note is the open e string
        Tuning.GUITAR.grips(strums).map { it.position }.distinct() shouldBe listOf(1)
        // from the fifth position the hand stays there for these few
        Tuning.GUITAR.grips(strums = strums, held = listOf(was)) shouldBe List(4) { was }
        // all of them held: nothing is worked out
        Tuning.GUITAR.grips(strums = strums, held = List(4) { was }) shouldBe List(4) { was }
    }

    "no strums: no grips; more held than strums is refused; a tuning is given from the lowest string up" {
        Tuning.GUITAR.grips(emptyList()).shouldBeEmpty()

        shouldThrow<IllegalArgumentException> {
            Tuning.GUITAR.grips(strums = emptyList(), held = listOf(Grip(position = 1, stops = emptyList())))
        }

        shouldThrow<IllegalArgumentException> { Tuning(open = listOf(45, 40), frets = 12) }
        shouldThrow<IllegalArgumentException> { Tuning(open = emptyList(), frets = 12) }
        shouldThrow<IllegalArgumentException> { Tuning(open = listOf(40), frets = 3) }
        shouldThrow<IllegalArgumentException> { Tuning(open = listOf(40), frets = 10) }
    }
})
