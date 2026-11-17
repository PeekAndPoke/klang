/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlin.math.min
import kotlin.math.pow

/**
 * A marimba of bars 0.05 apart, the low notes at x > 0 (the middle c at 0.6, the c above at 0): the naturals on the
 * near row (z 0.5), the others on the far one. A note above the highest bar (84) lies on the bar an octave lower.
 */
private fun place(midi: Int): MarimbaPoint {
    if (midi > 84) {
        return place(midi - 12)
    }

    val natural = midi % 12 in listOf(0, 2, 4, 5, 7, 9, 11)

    return MarimbaPoint(x = (72 - midi) * 0.05, y = 0.9, z = if (natural) 0.5 else 0.8)
}

private fun note(t: Double, midi: Int, v: Double = 1.0) = Note(t = t, midi = midi, v = v, d = 0.1)

private fun plan(notes: List<Note>, before: HandsBefore = HandsBefore(), barWidth: Double = 0.05) =
    planMarimba(notes = notes, before = before, place = ::place, barWidth = barWidth, s = 1.0)

private fun MarimbaPlan.sides() = blows.map { it.side }

/** One event of the song's tune ([midi]), as the recorder writes it. */
private fun lead(t: Double, midi: Int) =
    RecordedEvent(t = t, d = 0.1, tags = listOf("lead"), freq = 440.0 * 2.0.pow((midi - 69) / 12.0))

/** When the [k]th moment of [tune] is. */
private fun moment(k: Int) = 0.5 + k * 0.15

/**
 * A tune of 180 moments 0.15 s apart, each doubled 2 ms later as a part doubled left and right is: single notes that
 * wander, now and then a leap across the instrument, a chord on every fifth moment, a pause of five moments in every
 * forty (longer than a hand stays in the other's way); and a kick between, which is not hers.
 */
private fun tune(): List<RecordedEvent> {
    val out = mutableListOf<RecordedEvent>()
    var midi = 66

    for (k in 0 until 180) {
        val t = moment(k)
        val chance = hash01(k * 1.7 + 0.3)

        out.add(RecordedEvent(t = t + 0.07, d = 0.1, tags = listOf("kick")))

        if (k % 40 in 30..34) {
            continue
        }

        midi = when {
            chance < 0.15 -> 50 + (chance * 200).toInt()
            else -> (midi + (chance * 7).toInt() - 3).coerceIn(48, 84)
        }

        val notes = when {
            k % 10 == 0 -> listOf(midi, midi + 4, midi + 7, midi + 12)
            k % 5 == 0 -> listOf(midi, midi + 4, midi + 7)
            else -> listOf(midi)
        }

        for ((i, m) in notes.withIndex()) {
            out.add(lead(t = t + i * 0.004, midi = m))
            out.add(lead(t = t + i * 0.004 + 0.002, midi = m))
        }
    }

    return out
}

class MarimbaPlanSpec : StringSpec({

    "single notes: the hands play in turn, the left one first" {
        val p = plan(listOf(note(0.0, 60), note(0.5, 72), note(1.0, 62), note(1.5, 71)))

        withClue("hands in turn") {
            p.sides() shouldBe listOf(Side.L, Side.R, Side.L, Side.R)
        }

        p.byHand.getValue(Side.L).map { it.t } shouldBe listOf(0.0, 1.0)
        p.byHand.getValue(Side.R).map { it.t } shouldBe listOf(0.5, 1.5)
    }

    "a single note is never played across the other hand: the hand on its side plays it" {
        // the right hand is at 0.5 (midi 62) when the left one's turn asks for midi 74, to the right of it (x -0.1):
        // the right hand plays it; then the right hand's turn asks for midi 50, left of the left hand: the left plays it
        val p = plan(listOf(note(0.0, 60), note(0.2, 62), note(0.4, 74), note(0.6, 60), note(0.8, 50)))

        withClue("no crossing") {
            p.sides() shouldBe listOf(Side.L, Side.R, Side.R, Side.L, Side.L)
        }
    }

    "the other hand is in the way for 0.6 seconds after its blow, and its inner mallet within half a bar is not" {
        // as before, but the leap comes a second after the right hand's blow: the left hand may cross
        plan(listOf(note(0.0, 60), note(0.2, 62), note(1.2, 74))).sides() shouldBe listOf(Side.L, Side.R, Side.L)

        // midi 63 lies 0.05 right of the right hand's mallet on 62: within half a bar of 0.12, the left hand plays it
        plan(listOf(note(0.0, 60), note(0.2, 62), note(0.4, 63)), barWidth = 0.12).sides() shouldBe
            listOf(Side.L, Side.R, Side.L)
        // and with bars of 0.05 it is beyond: the right hand plays it
        plan(listOf(note(0.0, 60), note(0.2, 62), note(0.4, 63))).sides() shouldBe listOf(Side.L, Side.R, Side.R)
    }

    "one note: the inner mallet on the bar, the outer one beside it, further out and above the bars" {
        val p = plan(listOf(note(0.0, 60, v = 0.7), note(0.5, 72)))
        val left = p.blows[0]
        val right = p.blows[1]

        left.v shouldBe 0.7
        left.half shouldBe MarimbaPoint(x = 0.055, y = 0.0225, z = 0.0)
        left.at.x shouldBe (0.655 plusOrMinus 1e-12)
        left.at.y shouldBe (0.9225 plusOrMinus 1e-12)
        left.at.z shouldBe 0.5
        right.half shouldBe MarimbaPoint(x = -0.055, y = 0.0225, z = 0.0)
        right.at.x shouldBe (-0.055 plusOrMinus 1e-12)
    }

    "notes that sound together are one moment, shared between the hands: the low ones for the left" {
        // c, e, g, c within 30 ms: the left hand takes c and e, its mallets across them; the right hand g and c
        val p = plan(listOf(note(0.0, 60, v = 0.5), note(0.01, 64, v = 0.9), note(0.02, 67), note(0.025, 72, v = 0.3)))

        p.blows.map { it.t } shouldBe listOf(0.0, 0.0)
        p.blows.map { it.v } shouldBe listOf(1.0, 1.0)
        p.sides() shouldBe listOf(Side.L, Side.R)

        // the left hand: between e (0.4) and c (0.6), the way to the outer one (c, further left)
        p.blows[0].at.x shouldBe (0.5 plusOrMinus 1e-12)
        p.blows[0].half.x shouldBe (0.1 plusOrMinus 1e-12)
        // the right hand: between g (0.25) and c (0), the way to the outer one (c, further right)
        p.blows[1].at.x shouldBe (0.125 plusOrMinus 1e-12)
        p.blows[1].half.x shouldBe (-0.125 plusOrMinus 1e-12)

        // a note 30 ms after the first is a moment of its own
        plan(listOf(note(0.0, 60), note(0.03, 72))).blows.map { it.t } shouldBe listOf(0.0, 0.03)
    }

    "notes that lie on one bar are one note: a note doubled an octave above the bars" {
        // 86 lies on the bar of 74: one note, the left hand's as the first of all
        val p = plan(listOf(note(0.0, 74), note(0.01, 86)))

        p.sides() shouldBe listOf(Side.L)
        p.blows.single().half.x shouldBe 0.055
    }

    "a plan goes on from what the one before carries on: the hand that played last, and each hand's last blow" {
        val notes = listOf(note(0.0, 60), note(0.5, 72), note(1.0, 62), note(1.5, 71), note(2.0, 60))
        val whole = plan(notes)
        val (l0, r05, l1, r15, l2) = whole.blows

        // forgotten before 1.2: the left hand played last; each hand's list begins with its last blow forgotten
        val carried = whole.carryOn(from = 1.2)

        carried shouldBe HandsBefore(last = Side.L, blows = mapOf(Side.L to l1, Side.R to r05))

        val rest = plan(notes.drop(3), before = carried)

        rest.blows shouldBe listOf(r15, l2)
        rest.byHand.getValue(Side.L) shouldBe listOf(l1, l2)
        rest.byHand.getValue(Side.R) shouldBe listOf(r05, r15)

        // forgotten before 1.8: only the right hand's blow went; the left hand keeps what it carried
        rest.carryOn(from = 1.8) shouldBe HandsBefore(last = Side.R, blows = mapOf(Side.L to l1, Side.R to r15))

        // all of it forgotten: the last blows of all; and a plan of nothing carries on what it was given
        whole.carryOn(from = Double.POSITIVE_INFINITY) shouldBe HandsBefore(last = Side.L, blows = mapOf(Side.L to l2, Side.R to r15))
        plan(emptyList(), before = carried).carryOn(from = Double.POSITIVE_INFINITY) shouldBe carried
        l0.side shouldBe Side.L
    }

    "a song that grows and forgets is played as the whole song: every note known keeps its hand and its place" {
        val events = tune()
        val whole = plan(Song(Recording(rpm = 30.0, events = events)).notesOf("lead"))
        val song = Song(rpm = 30.0)
        var planned: MarimbaPlan? = null
        var until = 0.0
        var steps = 0

        // as the player does it whenever the song has changed: plan anew from what the last plan carries on, and
        // compare with the whole reading: the blows of the notes known, and each hand's list from its last blow
        // forgotten. [how]: what changed the song since the last plan
        fun step(how: String) {
            val notes = song.notesOf("lead")
            val from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
            val next = plan(notes, before = planned?.carryOn(from = from) ?: HandsBefore())
            val cut = min(from, until)

            planned = next
            steps++

            withClue("forgetting: step $steps ($how), the song from $from to $until") {
                next.blows shouldBe whole.blows.filter { it.t >= from && it.t < until }

                for (side in Side.entries) {
                    val hand = whole.byHand.getValue(side)

                    next.byHand.getValue(side) shouldBe
                        listOfNotNull(hand.lastOrNull { it.t < cut }) + hand.filter { it.t >= from && it.t < until }
                }
            }
        }

        for (n in 0 until 20) {
            // the song grows by nine moments, cut in the pause between two
            until = moment(9 * (n + 1)) - 0.075

            val more = events.filter { it.t >= moment(9 * n) - 0.075 && it.t < until }

            fun forget() {
                when (n) {
                    // once, all of it is forgotten: none of her notes is left, and the hands go on from the last of them
                    12 -> song.forget(before = until)
                    // else it forgets what lies three seconds behind, just after a moment's first note (on every fifth
                    // forget, a chord: the song is cut before it)
                    else -> song.forget(before = moment(9 * n - 11) + 0.003)
                }
            }

            if (n % 2 == 1) {
                // as the show does it: it forgets and adds in one go, and the player plans once
                forget()
                song.add(more)
                step(how = "forgot and added in one go")
            } else {
                song.add(more)
                step(how = "added")
                forget()
                step(how = "forgot")
            }
        }

        steps shouldBe 30
        whole.blows.size shouldBe whole.blows.count { it.t < until }
    }
})
