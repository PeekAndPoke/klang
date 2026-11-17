/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlin.math.pow

private fun note(t: Double, d: Double = 1.0, midi: Int = 67) = Note(t = t, midi = midi, v = 1.0, d = d)

private fun plan(notes: List<Note>, now: Double = Double.NEGATIVE_INFINITY, before: TrumpetPlan = TrumpetPlan()) =
    planTrumpet(notes = notes, now = now, before = before)

/**
 * A held melody of twelve phrases of six to eight notes, each 1.2 s long and 1.5 s apart, the phrases apart by
 * 8 s (a new phrase), 40 s (a stroll), 25 s (too short for a stroll, unless one has begun before the next note is
 * known) or 3 s (the phrase goes on).
 */
private fun melody(): List<RecordedEvent> {
    val out = mutableListOf<RecordedEvent>()
    var t = 1.0

    for (phrase in 0 until 12) {
        repeat(6 + phrase % 3) { i ->
            val midi = 60 + (hash01(phrase * 7.1 + i * 1.3) * 14).toInt()

            out.add(RecordedEvent(t = t, d = 1.2, tags = listOf("trommel"), freq = 440.0 * 2.0.pow((midi - 69) / 12.0)))
            t += 1.5
        }

        t += listOf(8.0, 40.0, 25.0, 3.0)[phrase % 4]
    }

    return out
}

class TrumpetPlanSpec : StringSpec({

    "a phrase begins with her first note and with a note after more than six seconds of silence" {
        withClue("phrases") {
            // 9.5 comes 6.5 s after the note ending at 3; 16.4 comes 5.9 s after the one ending at 10.5
            plan(listOf(note(0.0), note(2.0), note(9.5), note(16.4))).phrases shouldBe listOf(0.0, 9.5)
        }
    }

    "she strolls where she is free for more than thirty seconds: from twelve seconds after her last note, until four before the next" {
        val p = plan(listOf(note(0.0), note(50.0)))

        // free from the start (three seconds in) and after each note; after the last one known, for as long as
        // nothing more is known (an hour at the most)
        p.strolls shouldBe listOf(StrollTime(from = 15.0, to = 46.0), StrollTime(from = 63.0, to = 3663.0))
        plan(listOf(note(0.0), note(30.0))).strolls shouldBe listOf(StrollTime(from = 43.0, to = 3643.0))
    }

    "a stroll that has begun stays when the song then says her next note comes sooner" {
        val first = plan(listOf(note(0.0)))
        val grown = listOf(note(0.0), note(30.0))

        first.strolls shouldBe listOf(StrollTime(from = 15.0, to = 3615.0))

        withClue("begun") {
            // at 20 she is in it: it stays, and she is back four seconds before the note
            plan(grown, now = 20.0, before = first).strolls shouldBe
                listOf(StrollTime(from = 15.0, to = 26.0), StrollTime(from = 43.0, to = 3643.0))
        }

        // before it began, or where it was not planned: none
        plan(grown, now = 10.0, before = first).strolls shouldBe listOf(StrollTime(from = 43.0, to = 3643.0))
        plan(grown, now = 20.0).strolls shouldBe listOf(StrollTime(from = 43.0, to = 3643.0))
    }

    "her range: her lowest and her highest note, also of those the song has forgotten" {
        plan(listOf(note(0.0, midi = 55), note(2.0, midi = 80))).let { listOf(it.lowest, it.highest) } shouldBe listOf(55, 80)
        plan(emptyList()).let { listOf(it.lowest, it.highest) } shouldBe listOf(60, 72)

        val whole = plan(listOf(note(0.0, midi = 55), note(2.0, midi = 80), note(20.0, midi = 65)))
        val rest = plan(listOf(note(20.0, midi = 65)), before = whole)

        listOf(rest.lowest, rest.highest) shouldBe listOf(55, 80)
        plan(listOf(note(20.0, midi = 65))).let { listOf(it.lowest, it.highest) } shouldBe listOf(65, 65)
    }

    "where the song forgets, her phrases go on from her last note forgotten and her strolls from until when she played" {
        val whole = plan(listOf(note(0.0, d = 10.0), note(12.0), note(48.0)))
        val rest = plan(listOf(note(12.0), note(48.0)), before = whole)

        rest.playedUntil shouldBe 10.0
        rest.lastBefore shouldBe note(0.0, d = 10.0)
        // 12 follows the note forgotten by two seconds: no new phrase; begun anew, it would be one
        rest.phrases shouldBe listOf(48.0)
        plan(listOf(note(12.0), note(48.0))).phrases shouldBe listOf(12.0, 48.0)
        // the stroll before 48 begins twelve seconds after 13; and all of it forgotten, after 49
        rest.strolls shouldBe whole.strolls
        plan(emptyList(), before = rest).let { listOf(it.playedUntil, it.strolls) } shouldBe
            listOf(49.0, listOf(StrollTime(from = 61.0, to = 3661.0)))
    }

    "a song that grows and forgets is played as the same song grown without forgetting" {
        // her phrases look back only: they are compared with the whole song's. Her strolls and her range go by what
        // is known so far (after the last note known she strolls; a stroll that has begun stays): they are compared
        // with the song grown in the same steps without forgetting
        val events = melody()
        val whole = plan(Song(Recording(rpm = 30.0, events = events)).notesOf("trommel"))
        val grown = Song(rpm = 30.0)
        val forgetting = Song(rpm = 30.0)
        var all = TrumpetPlan()
        var kept = TrumpetPlan()
        var until = 0.0
        var steps = 0
        var emptied = 0
        var begun = 0

        // [how]: what changed the song since the last plan
        fun step(now: Double, how: String) {
            val notes = forgetting.notesOf("trommel")
            val from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY

            kept = plan(notes, now = now, before = kept)
            steps++

            if (notes.isEmpty()) {
                emptied++
            }

            // (a stroll the whole song does not have, which ends before a note: one that had begun)
            if (kept.strolls.any { it.to < 3000 && it !in whole.strolls }) {
                begun++
            }

            withClue("forgetting: step $steps ($how) at $now, the song from $from to $until") {
                // (the strolls after her last note forgotten: that note taken from the song grown, not from the plan)
                val lastForgotten = all.notes.lastOrNull { it.t < from }?.t ?: Double.NEGATIVE_INFINITY

                kept.phrases shouldBe whole.phrases.filter { it >= from && it < until }
                kept.strolls shouldBe all.strolls.filter { it.to > lastForgotten }
                listOf(kept.lowest, kept.highest) shouldBe listOf(all.lowest, all.highest)
            }
        }

        var now = -15.0
        var n = 0

        while (now < 420.0) {
            // known nine seconds ahead of what is heard, forgotten six seconds behind it
            val more = events.filter { it.t >= now + 8.3 && it.t < now + 9.0 }

            until = now + 9.0
            grown.add(more)
            all = plan(grown.notesOf("trommel"), now = now, before = all)

            if (n % 2 == 1) {
                // as the show does it: it forgets and adds in one go, and she plans once
                forgetting.forget(before = now - 6.0)
                forgetting.add(more)
                step(now = now, how = "forgot and added in one go")
            } else {
                forgetting.add(more)
                step(now = now, how = "added")
                forgetting.forget(before = now - 6.0)
                step(now = now, how = "forgot")
            }

            now += 0.7
            n++
        }

        // (in the long silences the song knew none of her notes, and a stroll begun before a note known late stayed)
        emptied shouldBeGreaterThan 0
        begun shouldBeGreaterThan 0
        whole.notes.last().t shouldBeLessThan until
    }
})
