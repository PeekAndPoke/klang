/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollTime
import io.peekandpoke.klang.viz.liveact.song.Strum
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// When a string player with the stroll on their sheet (`GuitarStroll`) walks about the front of the stage with the
// instrument: plain arithmetic on their strums, going on from what the song has forgotten, so it has a spec. Where a
// stroll takes them, the trumpet player's route says (`Stroller`).

/** How long a silence splits two phrases, in seconds: more than this between one strum's end and the next. */
private const val SILENCE = 6.0

/** How far into a phrase the stroll begins, in seconds: the player plays a good while at their place first. */
private const val INTO = 12.0

/** How long a phrase must be for a stroll, in seconds: more than this from its first strum to its end. */
private const val LONG = 30.0

/** How long a stroll whose end is not known yet runs, in seconds (as the trumpet player's unknown end does). */
private const val HOUR = 3600.0

/**
 * How soon a stroll that has begun ends at the earliest, in seconds from now, where its phrase ends sooner than it
 * was planned (the part is muted, or rewritten): time to walk home without a hurry.
 */
private const val RETURN = 6.0

/**
 * How far ahead of now a song played live is known, in seconds: as far as it is read ahead of what is heard
 * (`SongReading.AHEAD_SEC`). That is 14 s at the least, but for a song faster than 137 cycles a minute, where the
 * read-ahead's cap of 32 cycles (`SongReading.MOST_AHEAD`) is less: there a phrase's end can seem known a moment too
 * soon, and a stroll that has begun then stays (see [planStringStrolls]).
 */
const val STROLL_AHEAD = 14.0

/**
 * The plan of a string player's strolls, from their [strums] as far as the song knew them: the [strolls], and what it
 * carries on where the song has forgotten its past: until when they played before the first strum known
 * ([playedUntil]) and where the phrase of that began ([phraseFrom]); null where it has not. The plan of nothing yet
 * is the one they begin with.
 */
data class StringStrollPlan(
    val strums: List<Strum> = emptyList(),
    val playedUntil: Double? = null,
    val phraseFrom: Double? = null,
    val strolls: List<StrollTime> = emptyList(),
)

/** Until when [strum] sounds: its slot or its gate, whichever is longer. */
private fun endOf(strum: Strum): Double = strum.t + max(strum.d, strum.g)

/**
 * A string player's strolls, from [strums] (sorted by time, as far as the song knows them at [now], seconds; minus
 * infinity before anything is played), going on from the plan [before]. What comes back is the plan, to be given as
 * [before] the next time. They stroll while they play, slowly (the maintainer, 2026-11-27: "walk around slowly"; in
 * rounds, standing between, only while she plays), and are home again within the silence after their playing.
 *
 * A phrase is split from the next by a silence of more than six seconds between one strum's end (its slot or its
 * gate, whichever is longer) and the next strum. A stroll runs from twelve seconds into a phrase to six seconds after
 * its end, where the phrase is longer than thirty seconds: the way home has the silence after the playing, which the
 * next phrase never cuts into. Where its end is not known yet (the last phrase known, whose last strum ends within
 * six seconds of what the song knows, [now] plus [ahead]) the stroll runs an hour, and is shortened once the end is
 * known; a song known whole is given an [ahead] of infinity.
 *
 * A stroll that has begun (it was planned before, and now is in it) stays, also where its phrase turns out short,
 * and it ends no sooner than six seconds from now (or its end before, where that is sooner): a part muted in the
 * middle of a stroll has the player walk home, not jump.
 */
fun planStringStrolls(strums: List<Strum>, now: Double, before: StringStrollPlan, ahead: Double = STROLL_AHEAD): StringStrollPlan {
    // what the song has forgotten since the last plan: the phrase it ended in goes on into the strums known
    val first = strums.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
    var until = before.playedUntil
    var phrase = before.phraseFrom

    for (n in before.strums) {
        if (n.t >= first) {
            break
        }

        val last = until

        if (last == null || n.t - last > SILENCE) {
            phrase = n.t
        }

        until = max(last ?: n.t, endOf(n))
    }

    // the phrases, each from its first strum to its end
    val starts = mutableListOf<Double>()
    val ends = mutableListOf<Double>()
    var start = phrase
    var end = until

    for (n in strums) {
        val from = start
        val to = end

        if (from == null || to == null || n.t - to > SILENCE) {
            if (from != null && to != null) {
                starts.add(from)
                ends.add(to)
            }

            start = n.t
            end = endOf(n)
        } else {
            end = max(to, endOf(n))
        }
    }

    val lastStart = start
    val lastEnd = end

    if (lastStart != null && lastEnd != null) {
        starts.add(lastStart)
        ends.add(lastEnd)
    }

    // NaN-guard: a song known whole at minus infinity knows up to no number; its last phrase is known (the comparison
    // with it is false)
    val known = now + ahead
    // every phrase's stroll, and which of them are walked: the long ones, and the one whose end is not known yet
    val all = mutableListOf<StrollTime>()
    val strolls = mutableListOf<StrollTime>()

    for (i in starts.indices) {
        val from = starts[i] + INTO
        val open = i == starts.lastIndex && ends[i] + SILENCE > known
        val stroll = StrollTime(from = from, to = if (open) from + HOUR else ends[i] + SILENCE)

        all.add(stroll)

        if (open || ends[i] - starts[i] > LONG) {
            strolls.add(stroll)
        }
    }

    // one that has begun stays, and ends no sooner than RETURN seconds from now, or than it was to end
    for (old in before.strolls) {
        if (!(old.from < now && old.to > now)) {
            continue
        }

        val least = min(old.to, now + RETURN)
        val planned = all.firstOrNull { it.from == old.from }?.to ?: Double.NEGATIVE_INFINITY
        val kept = StrollTime(from = old.from, to = max(planned, least))
        val at = strolls.indexOfFirst { it.from == old.from }

        if (at >= 0) {
            strolls[at] = kept
        } else {
            strolls.add(kept)
        }
    }

    strolls.sortBy { it.from }

    return StringStrollPlan(strums = strums, playedUntil = until, phraseFrom = phrase, strolls = strolls)
}

/**
 * Of [times] (when a player begins a note, sorted), those the camera may choose a look on the player by: none from
 * [margin] seconds before a stroll's start to its end (see [strolls]), so that no look is on one who walks about
 * (a look on a player goes round them with them, and swings as they walk). [margin]: as long as a look of the
 * camera's, so that a look that would reach into a stroll counts none of the notes before it either.
 */
fun outsideStrolls(times: List<Double>, strolls: List<StrollTime>, margin: Double): List<Double> {
    if (strolls.isEmpty()) {
        return times
    }

    return times.filter { t -> strolls.none { t >= it.from - margin && t < it.to } }
}

/**
 * The way walked on a stroll, in metres, from frame to frame: what the legs step by. [place]: where the stroll has
 * the figure at a time (seconds on the show's clock; x and z, in metres).
 *
 * It goes on from the frame before by the step between the two places, so it never jumps; on another stroll, or
 * where the clock went back or jumped ahead by more than [JUMP] seconds (a seek), it is measured anew from the
 * stroll's start, in steps of [STEP] seconds, or in [MOST] steps at the most where that is far back.
 */
class WayWalked(private val place: (Double) -> Offset) {
    companion object {
        /** The steps the way is measured anew in, in seconds. */
        const val STEP = 0.1

        /** The most steps the way is measured anew in: a seek deep into a long stroll takes coarser steps. */
        const val MOST = 300

        /** A step of the clock longer than this, in seconds, is a jump: the way is measured anew. */
        const val JUMP = 1.0
    }

    private var lastT: Double = Double.NaN
    private var lastFrom: Double? = null
    private var lastAt: Offset = Offset(x = 0.0, z = 0.0)
    private var far: Double = 0.0

    /** How far the figure has walked by [t] on the stroll that began at [from] (both seconds on the show's clock). */
    fun at(from: Double, t: Double): Double {
        val here = place(t)

        // NaN-guard: before the first frame the comparisons with no time are false, and the way is measured anew
        far = if (from == lastFrom && t >= lastT && t - lastT <= JUMP) {
            far + hypot(here.x - lastAt.x, here.z - lastAt.z)
        } else {
            anew(from = from, t = t, here = here)
        }

        lastFrom = from
        lastT = t
        lastAt = here

        return far
    }

    /** The figure is at its place, on no stroll: the next stroll's way is measured anew. */
    fun rest() {
        lastFrom = null
    }

    /** The way from [from] to [t], where the figure is [here] at the end, measured in steps (see [WayWalked]). */
    private fun anew(from: Double, t: Double, here: Offset): Double {
        val step = max(STEP, (t - from) / MOST)
        var sum = 0.0
        var at = place(from)
        var x = from + step

        while (x < t) {
            val next = place(x)

            sum += hypot(next.x - at.x, next.z - at.z)
            at = next
            x += step
        }

        return sum + hypot(here.x - at.x, here.z - at.z)
    }
}
