/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollTime
import io.peekandpoke.klang.viz.liveact.song.Strum
import kotlin.math.cos
import kotlin.math.sin

private fun strum(t: Double, d: Double = 0.5, g: Double = d) = Strum(t = t, d = d, midi = 52, v = 1.0, all = listOf(52), g = g)

/** A strum every second from [from] to [to], both included. */
private fun run(from: Int, to: Int, d: Double = 0.5): List<Strum> = (from..to).map { strum(t = it.toDouble(), d = d) }

private const val WHOLE = Double.POSITIVE_INFINITY

private fun plan(
    strums: List<Strum>,
    now: Double = Double.NEGATIVE_INFINITY,
    before: StringStrollPlan = StringStrollPlan(),
    ahead: Double = WHOLE,
) = planStringStrolls(strums = strums, now = now, before = before, ahead = ahead)

class StringStrollsSpec : StringSpec({

    "a stroll runs from twelve seconds into a phrase longer than thirty seconds to six seconds after its end" {
        // 0 to 40.5; then 47 to 60.5 after 6.5 s of silence (a new phrase); 66.5 on after 6 s (the same phrase)
        val strums = run(0, 40) + run(47, 60) + (0..33).map { strum(t = 66.5 + it) }

        plan(strums).strolls shouldBe listOf(StrollTime(from = 12.0, to = 46.5), StrollTime(from = 59.0, to = 106.0))
    }

    "a phrase of thirty seconds is too short for one" {
        withClue("30.5 s") {
            plan(run(0, 30)).strolls shouldBe listOf(StrollTime(from = 12.0, to = 36.5))
        }

        withClue("30 s") {
            plan(run(0, 29, d = 1.0)).strolls shouldBe emptyList()
        }
    }

    "a strum sounds for its slot or its gate, whichever is longer, and a long one holds the phrase over a shorter one" {
        // the strum at 21 is held to 31, the one at 22 ends at 22.5 within it: 36 comes 5 s after the phrase's sound
        val held = run(0, 20) + strum(t = 21.0, d = 0.5, g = 10.0) + strum(t = 22.0) + run(36, 50)
        val slot = run(0, 20) + strum(t = 21.0, d = 10.0, g = 0.5) + strum(t = 22.0) + run(36, 50)
        val short = run(0, 20) + strum(t = 21.0) + strum(t = 22.0) + run(36, 50)

        plan(held).strolls shouldBe listOf(StrollTime(from = 12.0, to = 56.5))
        plan(slot).strolls shouldBe listOf(StrollTime(from = 12.0, to = 56.5))
        plan(short).strolls shouldBe emptyList()
    }

    "where the end is not known yet the stroll runs an hour, only for the last phrase known" {
        val strums = run(0, 20)

        withClue("the last strum ends within six seconds of what is known") {
            plan(strums, now = 12.4, ahead = 14.0).strolls shouldBe listOf(StrollTime(from = 12.0, to = 3612.0))
            plan(strums, ahead = 14.0).strolls shouldBe listOf(StrollTime(from = 12.0, to = 3612.0))
        }

        withClue("six seconds of silence known after it") {
            plan(strums, now = 12.5, ahead = 14.0).strolls shouldBe emptyList()
        }

        withClue("a phrase with another one known after it") {
            plan(run(0, 40) + run(50, 52), now = 30.0, ahead = 14.0).strolls shouldBe
                listOf(StrollTime(from = 12.0, to = 46.5), StrollTime(from = 62.0, to = 3662.0))
        }

        withClue("a song known whole knows its last phrase's end from the start") {
            plan(strums).strolls shouldBe emptyList()
        }
    }

    "a stroll that has begun stays when its phrase turns out short, and is shortened to six seconds after its end" {
        val first = plan(run(0, 20), ahead = 14.0)

        first.strolls shouldBe listOf(StrollTime(from = 12.0, to = 3612.0))

        val begun = plan(run(0, 20), now = 15.0, before = first, ahead = 14.0)

        withClue("begun") {
            begun.strolls shouldBe listOf(StrollTime(from = 12.0, to = 26.5))
        }

        withClue("in the order of their starts") {
            plan(run(0, 20) + run(30, 40), now = 15.0, before = first, ahead = 14.0).strolls shouldBe
                listOf(StrollTime(from = 12.0, to = 26.5), StrollTime(from = 42.0, to = 3642.0))
        }

        // before it began, after its end, or where it was not planned: none
        plan(run(0, 20), now = 11.0, before = first).strolls shouldBe emptyList()
        plan(run(0, 20), now = 26.5, before = begun).strolls shouldBe emptyList()
        plan(run(0, 20), now = 15.0).strolls shouldBe emptyList()
    }

    "a stroll that has begun ends no sooner than six seconds from now, or than it was to end, where its part is muted" {
        val whole = plan(run(0, 60))
        // the strums after 29 are gone: the phrase ends at 29.5, and the stroll would end at 35.5
        val muted = plan(run(0, 29), now = 30.0, before = whole)

        whole.strolls shouldBe listOf(StrollTime(from = 12.0, to = 66.5))
        muted.strolls shouldBe listOf(StrollTime(from = 12.0, to = 36.0))

        withClue("it stays so") {
            plan(run(0, 29), now = 31.0, before = muted).strolls shouldBe listOf(StrollTime(from = 12.0, to = 36.0))
        }

        withClue("one that was to end sooner than that ends then") {
            val ending = StringStrollPlan(strums = run(0, 60), strolls = listOf(StrollTime(from = 12.0, to = 33.0)))

            plan(run(0, 20), now = 30.0, before = ending).strolls shouldBe listOf(StrollTime(from = 12.0, to = 33.0))
        }

        withClue("one whose phrase is gone altogether") {
            val begun = StringStrollPlan(strolls = listOf(StrollTime(from = 12.0, to = 66.5)))

            plan(emptyList(), now = 30.0, before = begun).strolls shouldBe listOf(StrollTime(from = 12.0, to = 36.0))
        }
    }

    "where the song has forgotten the start of a phrase, the phrase goes on from it" {
        val whole = plan(run(0, 40) + run(50, 90), now = 20.0)

        whole.strolls shouldBe listOf(StrollTime(from = 12.0, to = 46.5), StrollTime(from = 62.0, to = 96.5))

        // the song keeps 60 on: the phrase begun at 50 goes on, the one before has ended
        val forgot = plan(run(60, 90), now = 70.0, before = whole)

        forgot.strolls shouldBe listOf(StrollTime(from = 62.0, to = 96.5))
        forgot.playedUntil shouldBe 59.5
        forgot.phraseFrom shouldBe 50.0

        withClue("and again, from what the last plan carried") {
            val again = plan(run(75, 90), now = 80.0, before = forgot)

            again.strolls shouldBe listOf(StrollTime(from = 62.0, to = 96.5))
            again.playedUntil shouldBe 74.5
            again.phraseFrom shouldBe 50.0
        }

        withClue("without the plan before, the phrase seems to begin with the first strum known") {
            plan(run(75, 90), now = 80.0).strolls shouldBe emptyList()
        }
    }

    "the camera chooses no look on one who strolls by the notes of a stroll, nor of a look's length before one" {
        val times = (0..10).map { it.toDouble() }

        outsideStrolls(times = times, strolls = listOf(StrollTime(from = 4.0, to = 7.0)), margin = 2.0) shouldBe
            listOf(0.0, 1.0, 7.0, 8.0, 9.0, 10.0)
        outsideStrolls(times = times, strolls = emptyList(), margin = 2.0) shouldBe times
    }

    "the way walked goes on from frame to frame, and is measured anew on another stroll, a step back or a jump" {
        // round a circle of one metre, a metre a second: measured anew in steps of a tenth of a second, it is the
        // chords' sum; from frame to frame, the chord of the step
        val way = WayWalked { t -> Offset(x = cos(t), z = sin(t)) }

        fun at(from: Double, t: Double, expected: Double) {
            withClue("$from, $t") {
                way.at(from = from, t = t) shouldBe (expected plusOrMinus 1e-9)
            }
        }

        at(from = 0.0, t = 1.0, expected = 0.9995833854135666)
        // on: the step's chord
        at(from = 0.0, t = 1.5, expected = 1.4943913039226124)
        // a step back
        at(from = 0.0, t = 1.2, expected = 1.19950006249628)
        // a jump of more than a second
        at(from = 0.0, t = 3.0, expected = 2.9987501562406993)
        // a step of a second is none
        at(from = 0.0, t = 4.0, expected = 3.9576012334491053)
        // another stroll, half a second on
        at(from = 3.5, t = 4.5, expected = 0.9995833854135666)

        way.rest()

        withClue("after a rest at the place") {
            at(from = 3.5, t = 4.9, expected = 1.3994167395789936)
        }
    }

    "a way measured anew from far back takes coarser steps" {
        var asked = 0
        val way = WayWalked { t ->
            asked++
            Offset(x = t, z = 0.0)
        }

        way.at(from = 0.0, t = 1000.0) shouldBe (1000.0 plusOrMinus 1e-6)
        asked shouldBeLessThanOrEqual WayWalked.MOST + 2
    }
})
