/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import kotlin.math.max
import kotlin.math.min

// The frontwoman's phrases, her range and her strolls, when the trumpet is at her lips and when her head is free to
// look about: plain arithmetic on her notes, going on from what the song has forgotten, so it has a spec
// (KLANG-00451). Where a stroll takes her, the player works out.

/** A stretch in which she has nothing to play and strolls about the front of the stage, [from] to [to] seconds. */
data class StrollTime(val from: Double, val to: Double)

/**
 * The frontwoman's plan, from her [notes] as far as the song knew them: where a phrase begins ([phrases]: a note
 * after more than six seconds of silence, and her first note), her [lowest] and her [highest] note so far, and her
 * [strolls].
 *
 * What it carries on: the [notes] it was made from, and, where the song has forgotten its past, until when she
 * played before the first note known ([playedUntil]) and her last note of it ([lastBefore]); null where it has
 * not. Her strolls go on from the one, her phrases from the other. The plan of nothing yet is the one she begins
 * with.
 */
data class TrumpetPlan(
    val notes: List<Note> = emptyList(),
    val playedUntil: Double? = null,
    val lastBefore: Note? = null,
    val lowest: Int = 60,
    val highest: Int = 72,
    val phrases: List<Double> = emptyList(),
    val strolls: List<StrollTime> = emptyList(),
)

/**
 * Her phrases and her strolls, from [notes] (sorted by time, as far as the song knows them at [now], seconds; minus
 * infinity before anything is played), going on from the plan [before]. What comes back is the plan, to be given as
 * [before] the next time.
 *
 * After the last note known she is free for as long as nothing more is known, and strolls. So a stroll can have
 * begun before the song said that her next note comes sooner than a stroll needs: one that has begun stays, and
 * she is back four seconds before that note. Her way back takes up to nine seconds, so she comes back without a
 * jump only where the song is known thirteen seconds ahead or more (who reads the song sees to that).
 */
fun planTrumpet(notes: List<Note>, now: Double, before: TrumpetPlan): TrumpetPlan {
    // what the song has forgotten since the last plan: she is free from the end of the last of it
    val from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
    var until = before.playedUntil
    var last = before.lastBefore

    for (n in before.notes) {
        if (n.t < from) {
            until = max(until ?: 0.0, n.t + n.d)
            last = n
        }
    }

    // (a note forgotten still counts for her range)
    val lowest = listOfNotNull(notes.minOfOrNull { it.midi }, before.lowest.takeIf { until != null }).minOrNull() ?: 60
    val highest = listOfNotNull(notes.maxOfOrNull { it.midi }, before.highest.takeIf { until != null }).maxOrNull() ?: 72
    val phrases = notes.filterIndexed { i, n ->
        val previous = if (i == 0) last else notes[i - 1]

        previous == null || n.t - (previous.t + previous.d) > 6
    }.map { it.t }

    val strolls = mutableListOf<StrollTime>()
    var free = max(3.0, until ?: 0.0)

    for (i in 0..notes.size) {
        val next = if (i < notes.size) notes[i].t else 1e9

        // she stands a good while first (the maintainer, 2026-11-16): twelve seconds after her last note, or after
        // the start
        val start = free + 12
        // one that has begun: it was planned before, and now is in it
        val begun = start < now && next > now && before.strolls.any { it.from == start }

        if (next - free > 30 || begun) {
            strolls.add(StrollTime(from = start, to = min(next - 4, free + 12 + 3600)))
        }

        if (i < notes.size) {
            free = max(free, notes[i].t + notes[i].d)
        }
    }

    return TrumpetPlan(
        notes = notes,
        playedUntil = until,
        lastBefore = last,
        lowest = lowest,
        highest = highest,
        phrases = phrases,
        strolls = strolls,
    )
}

/**
 * How far the trumpet is up at her lips at [t] (seconds), 0 (it hangs) to 1, by her [notes] (sorted by time): it
 * starts to come up 0.85 s before a note and is all the way up 0.15 s before it; it starts to go down 0.9 s after a
 * note ends and hangs again 1.7 s after. Between two notes close enough it stays up.
 */
fun trumpetUp(notes: List<Note>, t: Double): Double {
    val (before, after) = notes.around(t)
    val until = if (after != null) after.t - t else 99.0
    val since = if (before != null) t - before.t - before.d else 99.0

    return max(smooth(1 - (until - 0.15) / 0.7), 1 - smooth((since - 0.9) / 0.8))
}

/**
 * How free her head is at [t] (seconds) to look where her skills ask, 0 to 1, by her [notes] (sorted by time): not at
 * all while she plays a phrase (within it, or in a pause of six seconds or less within it, as [planTrumpet] cuts her
 * phrases), and all the way between phrases. Before a phrase the head lets go of a look over 1.3 s, and is back at
 * her playing 1.2 s before the first note, before the trumpet starts to come up (0.85 s before it, see
 * [trumpetUp]); after a phrase it is free again over the second after the trumpet is down (1.7 s after the last
 * note ends).
 */
fun freeToLook(notes: List<Note>, t: Double): Double {
    val (before, after) = notes.around(t)

    if (before != null && after != null && after.t - (before.t + before.d) <= 6) {
        return 0.0
    }

    val until = if (after != null) after.t - t else Double.POSITIVE_INFINITY
    val since = if (before != null) t - before.t - before.d else Double.POSITIVE_INFINITY

    return smooth((until - 1.2) / 1.3) * smooth((since - 1.7) / 1.0)
}
