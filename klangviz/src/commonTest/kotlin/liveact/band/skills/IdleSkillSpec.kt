/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/** A note of a part, for the idle skills: only when it begins. */
private class IdlePlay(override val t: Double) : Timed

class IdleSkillSpec : StringSpec({

    // every roll one half: the use of slot 0 begins at 1 + 0.5 * (10 - 4) = 4 s and holds 2 s, so it is over at
    // 6.5 s; the pause must then reach from 3 s to 7.5 s
    val timing = ChanceInSlots(every = 10.0, chance = 1.0, least = 2.0, most = 2.0, earliest = 1.0, reserve = 4.0, rise = 0.5, fall = 0.5)
    val half = { _: Double -> 0.5 }
    val scratch = HeadScratch(timing)

    fun at(t: Double, plays: List<Double>): IdleInUse? =
        idleAt(skill = scratch, t = t, roll = half, plays = plays.map { IdlePlay(it) })

    /** The use's moments: in its rise, held, in its fall. */
    val during = listOf(4.2, 5.0, 6.3)

    "a use's span is where its timing has it in use, from its start to the end of its fall" {
        val show = Show(seed = 4)
        val roll = { i: Double -> show.own(i, 37.0) }
        // with a cool-down, so some chances are held back
        val rested = timing.copy(chance = 0.8, cooldown = 6.0, least = 1.0, most = 3.0, reserve = 5.0)
        var inside = 0
        var heldBack = 0

        for (i in 0 until 40000) {
            val t = i * 0.0137 - 30.0
            val span = rested.spanAt(t = t, roll = roll)
            val share = rested.inUse(t = t, roll = roll)

            withClue("at $t s, the span $span and the share $share") {
                if (share > 0) {
                    (span != null && t > span.start && t < span.endInclusive) shouldBe true
                    inside += 1
                }

                if (span != null && t > span.start + 1e-9 && t < span.endInclusive - 1e-9) {
                    share shouldNotBe 0.0
                }

                // the same span from anywhere in the slot
                if (span != null) {
                    rested.spanAt(t = rested.slotOf(t) * rested.every, roll = roll) shouldBe span
                }
            }

            // a chance its roll takes, held back by the cool-down: no span
            if (roll(rested.slotOf(t)) <= rested.chance && span == null) {
                heldBack += 1
            }
        }

        inside shouldBeGreaterThan 2000
        heldBack shouldBeGreaterThan 500
    }

    "used where no note begins within the use and a margin either side, as its timing gives" {
        withClue("no notes at all") {
            val used = at(t = 5.0, plays = emptyList())

            used shouldBe IdleInUse(skill = scratch, share = 1.0, since = 1.0, length = 2.5)
            during.map { at(t = it, plays = emptyList())?.share } shouldBe during.map { timing.inUse(t = it, roll = half) }
        }

        withClue("notes just outside the margins, and far off") {
            during.map { at(t = it, plays = listOf(0.0, 2.9, 7.6, 20.0))?.share } shouldBe
                during.map { timing.inUse(t = it, roll = half) }
        }

        withClue("outside the use, not in use") {
            listOf(3.9, 6.6, 9.0).map { at(t = it, plays = emptyList()) } shouldBe listOf(null, null, null)
        }
    }

    "never where a note begins within the use, or within the margin before or after it: all through the use" {
        for (note in listOf(3.1, 4.0, 5.0, 6.5, 7.4)) {
            withClue("a note at $note s") {
                during.map { at(t = it, plays = listOf(0.0, note, 20.0)) } shouldBe listOf(null, null, null)
            }
        }
    }

    "a note just a margin away is allowed, before the use and after it alike" {
        // the use is from 4 s to 6.5 s: notes exactly a margin before it and a margin after it (all exact in binary)
        val before = 4.0 - IDLE_MARGIN
        val after = 6.5 + IDLE_MARGIN

        during.map { at(t = it, plays = listOf(before, after))?.share } shouldBe during.map { timing.inUse(t = it, roll = half) }
        // and a little nearer on either side, not
        during.map { at(t = it, plays = listOf(before + 0.01)) } shouldBe listOf(null, null, null)
        during.map { at(t = it, plays = listOf(after - 0.01)) } shouldBe listOf(null, null, null)
    }

    "a pose may keep the uses further from the notes: a wider margin, on either side alike" {
        // the use is from 4 s to 6.5 s; with a margin of 3 s no note may begin from 1 s to 9.5 s (the edges allowed)
        fun kept(note: Double): Double? =
            idleAt(skill = scratch, t = 5.0, roll = half, plays = listOf(IdlePlay(note)), margin = 3.0)?.share

        listOf(1.1, 7.6, 9.4).map { kept(it) } shouldBe listOf(null, null, null)
        listOf(0.9, 1.0, 9.5, 9.6).map { kept(it) } shouldBe listOf(1.0, 1.0, 1.0, 1.0)
        // and handed through for every skill on the sheet
        idlesAt(skills = listOf(scratch), t = 5.0, roll = half, plays = listOf(IdlePlay(9.4)), margin = 3.0) shouldBe emptyList()
    }

    "never in a use that begins before the member has arrived; from then on as before" {
        fun arrived(t: Double, arrives: Double): Double? =
            idleAt(skill = scratch, t = t, roll = half, plays = emptyList(), arrives = arrives)?.share

        // the use of slot 0 begins at 4 s
        withClue("arrived after the use began") {
            during.map { arrived(t = it, arrives = 4.1) } shouldBe listOf(null, null, null)
        }

        withClue("arrived just as it begins, and before") {
            during.map { arrived(t = it, arrives = 4.0) } shouldBe during.map { timing.inUse(t = it, roll = half) }
            during.map { arrived(t = it, arrives = -20.0) } shouldBe during.map { timing.inUse(t = it, roll = half) }
        }

        withClue("handed through for every skill on the sheet") {
            idlesAt(skills = listOf(scratch), t = 5.0, roll = half, plays = emptyList(), arrives = 4.5) shouldBe emptyList()
        }
    }

    "every idle skill in use, in the sheet's order (the rank); the timed ones and the rest are not asked" {
        val slouch = Slouch(timing)
        val glance = CameraGlance(timing)
        val sheet = listOf(EyeContact, glance, slouch, CrossedMallets(), scratch)
        val plays = listOf(IdlePlay(0.0), IdlePlay(20.0))

        withClue("at 5 s, all in use") {
            idlesAt(skills = sheet, t = 5.0, roll = half, plays = plays).map { it.skill } shouldBe listOf(slouch, scratch)
            idlesAt(skills = sheet.reversed(), t = 5.0, roll = half, plays = plays).map { it.skill } shouldBe
                listOf(scratch, slouch)
            // and the looks do not take them for theirs
            usesAt(skills = sheet, t = 5.0, roll = half).map { it.skill } shouldBe listOf(glance)
        }

        withClue("with a note within the use") {
            idlesAt(skills = sheet, t = 5.0, roll = half, plays = listOf(IdlePlay(0.0), IdlePlay(5.5), IdlePlay(20.0))) shouldBe emptyList()
        }
    }

    "each idle kind takes its own rolls: none the same as another kind's, timed or idle" {
        val show = Show(seed = 2)
        val roll = { i: Double -> show.own(i, 11.0) }
        val every = timing.copy(chance = 0.5)
        val kinds: List<IdleSkill> = listOf(
            HeadScratch(every),
            Slouch(every),
            NeckStretch(every),
            ShakeOut(every),
            TuneUp(every),
            CapTug(every),
            BrowWipe(every),
            StickTwirl(every),
            NeckScratch(every),
        )
        val times = (0 until 20000).map { it * 0.05 }

        for (kind in kinds) {
            withClue("${kind::class.simpleName} rolls from its own place") {
                times.map { t -> idleAt(skill = kind, t = t, roll = roll, plays = emptyList())?.share ?: 0.0 } shouldBe
                    times.map { t -> every.inUse(t = t, roll = { i -> roll(i + kind.rolls) }) }
            }
        }

        val timed = listOf(CameraGlance(every), CameraStare(every), BandGlance(every), InwardStare(every)).map { it.rolls } +
            CrowdGaze(timing = every, spotLeast = 1.5, spotMost = 2.5, move = 0.8).rolls
        val all = timed + kinds.map { it.rolls }

        all.toSet().size shouldBe all.size
    }

    "a skill fits a member at most the fit's width apart in wildness, either way" {
        // for each kind, the members it fits: from its own wildness the fit's width down and up (looked at past 10 as
        // well, so the upper bound is seen too)
        for (kind in listOf<Mannerism>(Slouch(timing), HeadScratch(timing), CountIn())) {
            withClue("${kind::class.simpleName}, as wild as ${kind.wildness}") {
                (-20..20).filter { fits(skill = kind, wildness = it) } shouldBe
                    ((kind.wildness - WILDNESS_FIT)..(kind.wildness + WILDNESS_FIT)).toList()
            }
        }
    }

    "a quiet song keeps only a share of the idle moves, each one whole and the same way every time" {
        val starts = List(2000) { it * 7.37 }

        starts.count { keepsIdle(start = it, seed = 3.0, share = 1.0) } shouldBe 2000
        starts.count { keepsIdle(start = it, seed = 3.0, share = 0.0) } shouldBe 0
        starts.count { keepsIdle(start = it, seed = 3.0, share = Double.NaN) } shouldBe 0

        val kept = starts.count { keepsIdle(start = it, seed = 3.0, share = 0.1) }

        (kept in 120..280) shouldBe true
        // a use is asked for at every frame of it, its beginning worked out a hair differently each time
        keepsIdle(start = 41.3700001, seed = 3.0, share = 0.5) shouldBe keepsIdle(start = 41.3699999, seed = 3.0, share = 0.5)
        // and another member keeps other ones
        (starts.map { keepsIdle(start = it, seed = 3.0, share = 0.5) } == starts.map { keepsIdle(start = it, seed = 4.0, share = 0.5) }) shouldBe false
    }
})
