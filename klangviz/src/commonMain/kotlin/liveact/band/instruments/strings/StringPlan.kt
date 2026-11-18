/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.song.Strum
import kotlin.math.abs
import kotlin.math.round

// How a string player plays each pluck or strum: how the left hand holds it (see Tuning.grips) and which way the
// right hand goes through the strings, kept as it was where the song grows or forgets. Plain arithmetic on the
// strums, so it has a spec (KLANG-00451). Where the hands are for it, the player works out from the grip.

/** A note this many seconds ahead is settled: the left hand is on its way to it, and it stays held as planned. */
private const val SETTLED = 0.4

/** A note after more than this many seconds without one is struck down again, as after a pause. */
private const val PAUSE = 0.28

/** A note that begins within this many seconds of a bar's start begins on it. */
private const val ON_BAR = 0.02

/**
 * One pluck or strum as a string player plays it: the [strum] (when, how long, how hard, and its notes as the song
 * has them), how the left hand holds it ([grip]), and which [way] the right hand goes through the strings: 1 down,
 * -1 up.
 */
data class Fingered(val strum: Strum, val grip: Grip, val way: Double) : Timed {
    override val t: Double get() = strum.t
}

/**
 * A string player's plan: each pluck or strum as it is played ([plucks]), in the order of their times, beginning
 * with the last one the song has forgotten ([forgotten]) where it has forgotten its past: the hands stay where that
 * one left them. The plan of nothing yet is the plan a player begins with.
 */
data class StringPlan(val plucks: List<Fingered> = emptyList(), val forgotten: Fingered? = null)

/**
 * How a string player plays each of [strums] (sorted by time, as far as the song knows them) on the strings of
 * [tuning], at [now] (seconds; minus infinity where nothing is played yet), going on from the plan [before]. [bar]:
 * how long a bar (a cycle of the song) is, in seconds; the bars begin at 0. With a bar of 0 or less (a song that
 * gives no tempo) no note begins one.
 *
 * The left hand holds each as a player would on the instrument's real strings: the string and the fret of each
 * note, in a position on the neck it leaves as seldom as the passage allows (see [Tuning.grips]); a note too low or
 * too high for the instrument is played octaves nearer. The right hand goes through the strings down after a pause
 * and down on a note that begins a bar (the maintainer, 2026-11-18: "on a full beat (start of cycle) the player should
 * do a down-stroke"), else down and up in turn.
 *
 * Where the song grows or changes, what was played up to [now] (and the note the hand is already going to) stays as
 * it was held in [before]; only what is still to come is worked out anew. Where the song has forgotten its past, the
 * plan begins with the last pluck forgotten (of [before]'s plucks), and the hands go on from it. What comes back
 * is the plan, to be given as [before] the next time.
 */
fun planStrings(strums: List<Strum>, now: Double, before: StringPlan, tuning: Tuning, bar: Double): StringPlan {
    // what the song has forgotten since the last plan: the hands go on from the last of it
    val from = strums.firstOrNull()?.t ?: Double.POSITIVE_INFINITY

    // (none where the song begins again before it: the plucks stay in the order of their times)
    val forgotten = before.plucks.lastOrNull { it.t < from }

    val planned = before.plucks.associateBy { it.t }
    val kept = mutableListOf<Grip>()

    for (n in strums) {
        val was = planned[n.t]

        if (was == null || n.t > now + SETTLED || was.strum.all.sorted() != n.all.sorted()) {
            break
        }

        kept.add(was.grip)
    }

    val lead = listOfNotNull(forgotten)
    val grips = tuning.grips(
        strums = lead.map { it.strum.all } + strums.map { it.all },
        held = lead.map { it.grip } + kept,
    ).drop(lead.size)

    // the right hand's way through the strings at each note: down after a pause and where a bar begins, else down
    // and up in turn
    var way = forgotten?.way ?: 1.0

    val plucks = lead + strums.mapIndexed { i, n ->
        val gap = if (i > 0) n.t - strums[i - 1].t else forgotten?.let { n.t - it.t } ?: 9.0
        val beginsBar = bar > 0 && abs(n.t - round(n.t / bar) * bar) <= ON_BAR

        way = if (gap > PAUSE || beginsBar) 1.0 else -way

        Fingered(strum = n, grip = grips[i], way = way)
    }

    return StringPlan(plucks = plucks, forgotten = forgotten)
}
