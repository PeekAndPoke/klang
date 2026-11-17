/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.song.Note

// Which hand of the marimba player plays which note, and where its two mallets land: plain arithmetic on her notes
// and the bars' places, so it has a spec (KLANG-00451). The player turns the places into the figure's vectors. All
// places are in the figure's own measures.

/** How far apart a hand's two mallet heads are when it plays one note or none, in the figure's measure: two bars or so. */
const val MALLETS_APART = 0.11

/** Notes that begin within this many seconds of each other sound together: one blow. */
private const val TOGETHER = 0.03

/** How high above the bars the mallet rides that does not play. */
private const val RIDE = 0.045

/** How wide a hand's two mallets open at most: an octave in the middle of the instrument. */
private const val REACH = 0.36

/**
 * A place, or the way from one place to another, in the marimba player's own space: [x] to her left (where the low
 * notes lie), [y] up, [z] ahead of her.
 */
data class MarimbaPoint(val x: Double, val y: Double, val z: Double)

/**
 * One blow of one hand, with its two mallets: when ([t]), how hard ([v]), which hand ([side]), and where. [at] is the
 * place between the two heads and [half] the way from there to the outer mallet's head (the inner one's is as far
 * the other way). Where the hand plays one note, its inner mallet lands on the bar and the outer one rides beside it,
 * above the bars.
 */
data class MalletBlow(
    override val t: Double,
    val v: Double,
    val side: Side,
    val at: MarimbaPoint,
    val half: MarimbaPoint,
) : Timed

/**
 * Where the hands were before the first note known, where the song has forgotten its past: the hand that played
 * [last], and each hand's last blow ([blows]). The hands go on from there, so no note that is still known changes
 * hands, and each hand's list begins with its last blow forgotten: the mallet stays where that blow left it. As a
 * song begins: the right hand counts as the one that played last (so the left one begins), and no hand has played.
 */
data class HandsBefore(val last: Side = Side.R, val blows: Map<Side, MalletBlow> = emptyMap())

/**
 * The hands' plan, begun from [before]: every blow in order ([blows]), and each hand's blows in order ([byHand]),
 * each hand's list beginning with its last blow forgotten where it has one.
 */
data class MarimbaPlan(
    val before: HandsBefore,
    val blows: List<MalletBlow>,
    val byHand: Map<Side, List<MalletBlow>>,
) {
    /**
     * What the hands carry on to the next plan, where the song now begins at [from] (the time of the first note it
     * still knows; infinity where it knows none): of the blows that went, the hand of the last one and each hand's
     * last one; where nothing of a hand went, what this plan began from.
     */
    fun carryOn(from: Double): HandsBefore {
        // what the song has forgotten since this plan: the hands go on from the last of it
        val gone = blows.filter { it.t < from }
        val blowsBefore = before.blows.toMutableMap()

        for (side in Side.entries) {
            gone.lastOrNull { it.side == side }?.let { blowsBefore[side] = it }
        }

        return HandsBefore(last = gone.lastOrNull()?.side ?: before.last, blows = blowsBefore)
    }
}

/**
 * Which hand plays which of [notes] (sorted by time, as far as the song knows them), going on from [before]: a
 * single note with a hand's inner mallet, the hands in turn but never crossing over each other; notes that sound
 * together with as many mallets as they need, shared between the hands by [malletShare].
 *
 * [place] gives where a mallet lands on the bar of a note (its midi number): notes that lie on one bar have the same
 * place, and no two bars share one. [barWidth] is how wide a bar is, [s] the figure's measure. What comes back is
 * the plan, its places as [place] gives them.
 */
fun planMarimba(
    notes: List<Note>,
    before: HandsBefore,
    place: (Int) -> MarimbaPoint,
    barWidth: Double,
    s: Double,
): MarimbaPlan {
    val hands = Side.entries.associateWith { side -> listOfNotNull(before.blows[side]).toMutableList() }
    val all = mutableListOf<MalletBlow>()
    var last = before.last
    val where = before.blows.toMutableMap()

    /** The [hand] plays the [struck] bars (one or two) at [t], as hard as [v]. */
    fun blow(hand: Side, struck: List<MarimbaPoint>, t: Double, v: Double) {
        val half = when (struck.size) {
            // one note: the inner mallet plays it, the outer one rides beside it, further out and above the bars
            1 -> MarimbaPoint(x = hand.sign * MALLETS_APART / 2 * s, y = RIDE / 2 * s, z = 0.0)
            // two: the outer mallet takes the bar further out (the left hand's lower one, the right hand's higher).
            // (Halving is exact, and a sum of two is the same either way round, so the order of the sum changes no
            // bit. The `0.0 +` is the vector the figure's sum began with: it keeps even the sign of a zero as that
            // sum gave it, since 0.0 + -0.0 is 0.0.)
            else -> {
                val (inner, outer) = struck.sortedBy { it.x * hand.sign }

                MarimbaPoint(
                    x = 0.0 + outer.x * 0.5 + inner.x * -0.5,
                    y = 0.0 + outer.y * 0.5 + inner.y * -0.5,
                    z = 0.0 + outer.z * 0.5 + inner.z * -0.5,
                )
            }
        }
        val inner = struck.minBy { it.x * hand.sign }
        val hit = MalletBlow(
            t = t,
            v = v,
            side = hand,
            at = MarimbaPoint(x = inner.x + half.x, y = inner.y + half.y, z = inner.z + half.z),
            half = half,
        )

        all.add(hit)
        hands.getValue(hand).add(hit)
        where[hand] = hit
        last = hand
    }

    var i = 0

    while (i < notes.size) {
        // what sounds together is one moment
        var end = i + 1

        while (end < notes.size && notes[end].t - notes[i].t < TOGETHER) {
            end++
        }

        val together = notes.subList(i, end)
        val t = together.first().t

        i = end

        // (notes that lie on one bar, as a note doubled an octave beyond the bars does, are one note)
        val struck = together.map { place(it.midi) }.distinct()
        val v = together.maxOf { it.v }

        if (struck.size == 1) {
            // one note: the hands play in turn, but never crossing over each other
            val at = struck.single()
            var hand = last.other
            val other = hand.other
            val there = where[other]
            // where the other hand's inner mallet is, the one nearest this hand (its blow's middle less the way to its
            // outer one)
            val recent = if (there != null && t - there.t < 0.6) there.at.x - there.half.x else null
            val slack = barWidth / 2

            // the left hand is on the side of the low notes (x > 0): it never plays to the right of the right hand's
            // mallets, nor between them
            if (recent != null && ((hand == Side.L && at.x < recent - slack) || (hand == Side.R && at.x > recent + slack))) {
                hand = other
            }

            blow(hand = hand, struck = struck, t = t, v = v)

            continue
        }

        // several: shared between the hands, the low ones for the left (the low notes lie at x > 0)
        val share = malletShare(places = struck.map { -it.x }, reach = REACH * s)

        for ((hand, mine) in listOf(Side.L to share.low, Side.R to share.high)) {
            if (mine.isNotEmpty()) {
                blow(hand = hand, struck = mine.map { struck[it] }, t = t, v = v)
            }
        }
    }

    return MarimbaPlan(before = before, blows = all, byHand = hands)
}
