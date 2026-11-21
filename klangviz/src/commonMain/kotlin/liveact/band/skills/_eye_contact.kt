/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How long after the first one's glance the two look at each other, from the moment the other answers, in seconds. */
const val CONTACT_MEET: Double = 1.3

/** The least the other answers the first one's glance late, in seconds; the most is what the timing's rise leaves. */
const val CONTACT_LATE_LEAST: Double = 1.0

/**
 * How long the two take to be back at their playing once the first looks away, in seconds: the least fall of the
 * timing, so that both are back by the end of a use.
 */
const val CONTACT_BACK: Double = 2.15

/** How far a contact with the trumpet player keeps from her notes, before it and after it, in seconds. */
const val CONTACT_CLEAR: Double = 3.0

/**
 * An eye contact in use at one moment: the [first] starts it, the [second] answers. [firstTurn], [secondTurn]: how
 * far each head has turned toward the other (0 to 1, a good half at most); [firstNod], [secondNod]: the small nod
 * each gives as their eyes meet (0 to 1).
 */
data class ContactInUse(
    val first: Player,
    val second: Player,
    val firstTurn: Double,
    val secondTurn: Double,
    val firstNod: Double,
    val secondNod: Double,
)

/**
 * The eye contact at [t] (seconds into the show), where one is in use; null where none is. Every now and then two of
 * the band look at each other for a moment, then back to their playing.
 *
 * When: by chance in slots, the band's [timing] (the score's), with the band's [roll] (the show's chance under the
 * eye contact's own salt). A use goes from the first one's look to both being back: over its rise the first glances
 * over and is half back at the playing when the other notices and looks, one second late or more (up to what the rise
 * leaves); then the first looks again and they share it until its fall, with a small nod each as their eyes meet; over
 * the fall both look back, the second a moment after the first. A head never turns all the way: a good half, as people
 * look with their eyes; the beat's nod goes on underneath.
 *
 * Who: one of the [pairs], and which of the two starts, by the roll of the use's slot. Not on the walk-in: a use that
 * begins before the song does not happen, so one is always seen whole. And not with [her] (the trumpet player) where
 * her notes ([herNotes]) begin within the use or [CONTACT_CLEAR] seconds either side of it: the trumpet is at her lips.
 */
fun contactAt(
    t: Double,
    timing: ChanceInSlots,
    roll: (Double) -> Double,
    pairs: List<Pair<Player, Player>>,
    her: Player,
    herNotes: List<Double>,
): ContactInUse? {
    val span = timing.spanAt(t = t, roll = roll) ?: return null

    if (span.start < 0 || t !in span) {
        return null
    }

    // what the slot draws besides the timing's own: whole numbers are the timing's, these lie between them
    val slot = timing.slotOf(t)
    val drawn = pairs[min(floor(roll(slot + 0.25) * pairs.size).toInt(), pairs.size - 1)]
    val pair = if (roll(slot + 0.5) < 0.5) drawn else drawn.second to drawn.first
    val hers = pair.first == her || pair.second == her

    if (hers && herNotes.any { x -> x > span.start - CONTACT_CLEAR && x < span.endInclusive + CONTACT_CLEAR }) {
        return null
    }

    // the two look at each other from the end of the rise to the start of the fall; the other answers [late] seconds
    // after the first one's glance, and so the glance comes that much before they meet
    val meet = span.start + timing.rise
    val away = span.endInclusive - timing.fall
    val late = CONTACT_LATE_LEAST + (timing.rise - CONTACT_MEET - CONTACT_LATE_LEAST) * roll(slot + 0.75)
    val glanceAt = meet - CONTACT_MEET - late
    val x = t - glanceAt
    val shared = away - glanceAt
    val glance = 0.50 * smooth(x / 1.2) * smooth((2.0 - x) / 0.9)
    val again = 0.62 * smooth((x - late - 0.4) / 0.9) * smooth((shared + 0.9 - x) / 0.9)
    val answer = 0.58 * smooth((x - late) / 1.1) * smooth((shared + 0.35 + 0.9 - x) / 0.9)

    // as their eyes meet, a small nod to each other, the second a moment after the first
    fun greet(at: Double): Double {
        val d = (t - at) / 0.28

        return exp(-d * d)
    }

    return ContactInUse(
        first = pair.first,
        second = pair.second,
        firstTurn = max(glance, again),
        secondTurn = answer,
        firstNod = greet(meet + 0.25),
        secondNod = greet(meet + 0.45),
    )
}

/**
 * Who of the two in an eye contact turns their head, of [first] (who starts it) and [second], in that order: each
 * only where their [skills] hold [EyeContact]. One without it does not turn; the other turns all the same.
 */
fun turning(first: Player, second: Player, skills: (Player) -> List<Skill>): List<Player> =
    listOf(first, second).filter { player -> EyeContact in skills(player) }
