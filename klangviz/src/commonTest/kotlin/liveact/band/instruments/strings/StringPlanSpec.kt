/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.song.Strum
import kotlin.math.pow

private fun strum(t: Double, vararg notes: Int) = Strum(t = t, d = 0.1, midi = notes.min(), v = 1.0, all = notes.toList())

private fun plan(strums: List<Strum>, now: Double = Double.NEGATIVE_INFINITY, before: StringPlan = StringPlan()) =
    planStrings(strums = strums, now = now, before = before, tuning = Tuning.GUITAR)

/** When the [k]th moment of [riff] is: eighths at 120 beats a minute. */
private fun moment(k: Int) = 0.5 + k * 0.125

/**
 * A rhythm guitar's part of 600 moments: runs of single notes that wander over the neck (low riffs, and passages
 * high up that move the hand), a chord on every eighth moment, short pauses, and a long one of thirty seconds in the
 * middle. Each note is doubled 2 ms later on the second part, as the song's two rhythm guitars are.
 */
private fun riff(): List<RecordedEvent> {
    val out = mutableListOf<RecordedEvent>()
    var midi = 45

    for (k in 0 until 600) {
        val chance = hash01(k * 2.3 + 0.7)

        if (k in 300 until 540 || k % 32 in 24..27) {
            continue
        }

        midi = when {
            chance < 0.1 -> 40 + (chance * 300).toInt()
            else -> (midi + (chance * 9).toInt() - 4).coerceIn(40, 79)
        }

        val notes = if (k % 8 == 0) listOf(midi, midi + 7, midi + 12).filter { it <= 79 } else listOf(midi)

        for ((i, m) in notes.withIndex()) {
            for ((tag, late) in listOf("guitar2" to 0.0, "guitar3" to 0.002)) {
                val freq = 440.0 * 2.0.pow((m - 69) / 12.0)

                out.add(RecordedEvent(t = moment(k) + i * 0.004 + late, d = 0.1, tags = listOf(tag), freq = freq))
            }
        }
    }

    return out
}

class StringPlanSpec : StringSpec({

    "the pick goes down after a pause, else down and up in turn" {
        val p = plan(listOf(strum(0.0, 40), strum(0.2, 43), strum(0.4, 45), strum(1.0, 40), strum(1.25, 43)))

        withClue("the pick's way") {
            p.plucks.map { it.way } shouldBe listOf(1.0, -1.0, 1.0, 1.0, -1.0)
        }
    }

    "a song known whole is held as the tuning holds it, nothing forgotten" {
        val strums = listOf(strum(0.0, 40), strum(0.5, 45, 52, 57), strum(1.0, 76), strum(1.5, 79))
        val p = plan(strums)

        p.plucks.map { it.strum } shouldBe strums
        p.plucks.map { it.grip } shouldBe Tuning.GUITAR.grips(strums.map { it.all })
        p.forgotten shouldBe null
    }

    "what was played up to now, and the note the hand goes to, stays held as it was when the song grows" {
        // e alone is played low (the d string at its second fret); with the high notes after it, the whole passage is
        // played at the twelfth fret, the e on the low string
        val first = plan(listOf(strum(0.0, 52)))
        val grown = listOf(strum(0.0, 52), strum(1.0, 76), strum(1.5, 79))

        first.plucks.single().grip shouldBe Grip(position = 1, stops = listOf(Stop(string = 2, fret = 2)))
        plan(grown).plucks.first().grip shouldBe Grip(position = 12, stops = listOf(Stop(string = 0, fret = 12)))

        withClue("settled") {
            // played already, or the hand on its way to it (0.4 s ahead or less): it stays
            plan(grown, now = 0.0, before = first).plucks.first().grip shouldBe first.plucks.single().grip
            plan(grown, now = -0.3, before = first).plucks.first().grip shouldBe first.plucks.single().grip
        }

        // further ahead: worked out anew
        plan(grown, now = -0.5, before = first).plucks.first().grip.position shouldBe 12
        // and where the song now says other notes at that moment, too
        plan(listOf(strum(0.0, 53), strum(1.0, 76), strum(1.5, 79)), now = 0.0, before = first).plucks.first().grip.position shouldBe 12
    }

    "where the song forgets, the plan begins with the last pluck forgotten, and the hands go on from it" {
        val strums = listOf(strum(0.0, 40), strum(0.2, 76), strum(0.4, 79), strum(0.6, 77))
        val whole = plan(strums)

        whole.plucks.map { it.way } shouldBe listOf(1.0, -1.0, 1.0, -1.0)

        // the first one forgotten: the plan begins with it, and goes on as before
        val rest = plan(strums.drop(1), before = whole)

        rest.forgotten shouldBe whole.plucks[0]
        rest.plucks shouldBe whole.plucks

        // two forgotten: the pick goes on up and down from the second (begun anew, the third would go down)
        val less = plan(strums.drop(2), before = rest)

        less.forgotten shouldBe whole.plucks[1]
        less.plucks shouldBe whole.plucks.drop(1)

        // nothing more forgotten: the one forgotten before stays; all forgotten: only the last of them is left
        plan(strums.drop(2), before = less).forgotten shouldBe whole.plucks[1]
        plan(emptyList(), before = less).plucks shouldBe listOf(whole.plucks[3])
        plan(emptyList(), before = less).forgotten shouldBe whole.plucks[3]
    }

    "where the song begins again before the pluck forgotten, nothing is carried" {
        val strums = listOf(strum(0.0, 40), strum(0.2, 43), strum(0.4, 45))
        val less = plan(strums.drop(2), before = plan(strums))

        less.forgotten?.t shouldBe 0.2

        val again = plan(listOf(strum(0.1, 40)), before = less)

        again.forgotten shouldBe null
        again.plucks.map { it.t } shouldBe listOf(0.1)
        again.plucks.single().way shouldBe 1.0
    }

    "a song that grows and forgets is played as the same song grown without forgetting" {
        // (the grips look ahead: what is still to come is worked out anew as the song grows, so the reading to
        // compare with is the song grown in the same steps, as far as it is known, without forgetting)
        val events = riff()
        val grown = Song(rpm = 30.0)
        val forgetting = Song(rpm = 30.0)
        var all = StringPlan()
        var kept = StringPlan()
        var steps = 0
        var emptied = 0
        val tags = listOf("guitar2", "guitar3")

        // [how]: what changed the song since the last plan
        fun step(now: Double, how: String) {
            val strums = forgetting.strumsOf(tags)
            val from = strums.firstOrNull()?.t ?: Double.POSITIVE_INFINITY

            kept = plan(strums, now = now, before = kept)
            steps++

            if (strums.isEmpty()) {
                emptied++
            }

            withClue("forgetting: step $steps ($how) at $now, the song from $from") {
                val last = all.plucks.lastOrNull { it.t < from }

                kept.forgotten shouldBe last
                kept.plucks shouldBe listOfNotNull(last) + all.plucks.filter { it.t >= from }
            }
        }

        for (n in 0 until 45) {
            // the song is known sixteen seconds ahead of what is heard, and forgotten five seconds behind it
            val now = moment(17 * n)
            val start = if (n == 0) Double.NEGATIVE_INFINITY else moment(17 * n + 128) - 0.06
            val more = events.filter { it.t >= start && it.t < moment(17 * (n + 1) + 128) - 0.06 }

            grown.add(more)
            all = plan(grown.strumsOf(tags), now = now, before = all)

            if (n % 2 == 1) {
                // as the show does it: it forgets and adds in one go, and the player plans once
                forgetting.forget(before = now - 5.0)
                forgetting.add(more)
                step(now = now, how = "forgot and added in one go")
            } else {
                forgetting.add(more)
                step(now = now, how = "added")
                forgetting.forget(before = now - 5.0)
                step(now = now, how = "forgot")
            }
        }

        steps shouldBe 68
        // (in the long pause the song once knew none of her notes)
        emptied shouldBeGreaterThan 0
        all.plucks.size shouldBe grown.strumsOf(tags).size
    }
})
