/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song

private fun hit(t: Double, drum: Drum, limb: Limb) = DrumHit(t = t, drum = drum, limb = limb, v = 1.0)

/** When the [k]th sixteenth of [beat] is, at 120 beats a minute. */
private fun sixteenth(k: Int) = 0.5 + k * 0.125

/**
 * A drum part of 40 bars, each of fifteen sixteenths and a rest: the kick on the beats and in runs, the snare on the
 * back beats and in fills, the closed hat on the eighths, a crash or the open hat now and then; four bars of rest in
 * the middle.
 */
private fun beat(): List<RecordedEvent> {
    val out = mutableListOf<RecordedEvent>()

    fun add(k: Int, tag: String, sound: String? = null) {
        out.add(RecordedEvent(t = sixteenth(k), d = 0.1, tags = listOf(tag), sound = sound))
    }

    for (bar in 0 until 40) {
        if (bar in 18 until 22) {
            continue
        }

        for (step in 0 until 15) {
            val k = bar * 16 + step
            val chance = hash01(k * 1.3 + 0.9)

            if (step % 4 == 0 || chance < 0.25) {
                add(k = k, tag = "kick")
            }

            if (step % 8 == 4 || (step >= 11 && chance > 0.4)) {
                add(k = k, tag = "snare")
            }

            when {
                step == 0 && bar % 4 == 0 -> add(k = k, tag = "hats", sound = "cr")
                step % 2 == 0 && chance > 0.85 -> add(k = k, tag = "hats", sound = "oh")
                step % 2 == 0 -> add(k = k, tag = "hats", sound = "hh")
            }
        }
    }

    return out
}

class DrumPlanSpec : StringSpec({

    "each limb gets its hits, in order, and every limb has a list" {
        val hits = listOf(
            hit(0.0, Drum.Kick, Limb.KickR), hit(0.0, Drum.Hat, Limb.R), hit(0.5, Drum.Snare, Limb.L),
            hit(0.6, Drum.Kick, Limb.KickL), hit(1.0, Drum.Ride, Limb.R),
        )
        val p = planDrums(hits = hits, before = emptyMap())

        withClue("by limb") {
            p.byLimb shouldBe mapOf(
                Limb.L to listOf(hits[2]),
                Limb.R to listOf(hits[1], hits[4]),
                Limb.KickL to listOf(hits[3]),
                Limb.KickR to listOf(hits[0]),
            )
        }

        planDrums(hits = emptyList(), before = emptyMap()).byLimb shouldBe Limb.entries.associateWith { emptyList<DrumHit>() }
    }

    "each limb's list begins with its last hit forgotten, and a limb none of whose hits went keeps what it carried" {
        val hits = listOf(
            hit(0.0, Drum.Snare, Limb.L), hit(0.2, Drum.Hat, Limb.R), hit(0.4, Drum.Snare, Limb.L),
            hit(0.6, Drum.Hat, Limb.R), hit(0.8, Drum.Kick, Limb.KickR),
        )
        val whole = planDrums(hits = hits, before = emptyMap())
        val carried = whole.carryOn(from = 0.5)

        carried shouldBe mapOf(Limb.L to hits[2], Limb.R to hits[1])

        val rest = planDrums(hits = hits.drop(3), before = carried)

        rest.byLimb.getValue(Limb.L) shouldBe listOf(hits[2])
        rest.byLimb.getValue(Limb.R) shouldBe listOf(hits[1], hits[3])
        rest.byLimb.getValue(Limb.KickR) shouldBe listOf(hits[4])

        // forgotten before 0.7: only the right hand's hit went; the left hand keeps its own
        rest.carryOn(from = 0.7) shouldBe mapOf(Limb.L to hits[2], Limb.R to hits[3])
        // all of it forgotten: the last hit of each limb
        whole.carryOn(from = Double.POSITIVE_INFINITY) shouldBe mapOf(Limb.L to hits[2], Limb.R to hits[3], Limb.KickR to hits[4])
    }

    "a song that grows and forgets is played as the whole song: every hit known keeps its limb's list as it was" {
        val events = beat()
        val whole = planDrums(hits = Song(Recording(rpm = 30.0, events = events)).drumHits(), before = emptyMap())
        val song = Song(rpm = 30.0)
        var planned: DrumPlan? = null
        var until = 0.0
        var steps = 0
        var emptied = 0

        // as the drummer does it whenever the song has changed: plan anew from what the last plan carries on. [how]:
        // what changed the song since the last plan
        fun step(how: String) {
            val hits = song.drumHits()
            val from = hits.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
            val next = planDrums(hits = hits, before = planned?.carryOn(from = from) ?: emptyMap())

            planned = next
            steps++

            if (hits.isEmpty()) {
                emptied++
            }

            withClue("forgetting: step $steps ($how), the song from $from to $until") {
                for (limb in Limb.entries) {
                    val list = whole.byLimb.getValue(limb)

                    next.byLimb.getValue(limb) shouldBe
                        listOfNotNull(list.lastOrNull { it.t < minOf(from, until) }) + list.filter { it.t >= from && it.t < until }
                }
            }
        }

        for (bar in 0 until 40) {
            // the song grows by a bar, cut in the rest at the bar's end (no hit is then told anew from one after it)
            until = sixteenth(bar * 16 + 15) + 0.06

            val more = events.filter { it.t >= sixteenth(bar * 16 - 1) + 0.06 && it.t < until }

            // and forgets what lies some three seconds behind, in the middle of a bar
            if (bar % 2 == 1) {
                // as the show does it: it forgets and adds in one go, and the drummer plans once
                song.forget(before = sixteenth(bar * 16 - 17) + 0.01)
                song.add(more)
                step(how = "forgot and added in one go")
            } else {
                song.add(more)
                step(how = "added")
                song.forget(before = sixteenth(bar * 16 - 17) + 0.01)
                step(how = "forgot")
            }
        }

        steps shouldBe 60
        // (in the rest of four bars, the song once knew none of the hits)
        emptied shouldBeGreaterThan 0
    }
})
