/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots
import io.peekandpoke.klang.viz.liveact.triggers.PartPause
import kotlin.math.abs

// What a member does with the hands while the part pauses (the maintainer, 2026-11-18: "more skills for when a player
// is idle... relaxing poses, head-scratching, brush hair back"). Used by chance as a timed skill is, but only within a
// pause of the member's own part (see idleAt and PartPause). Each kind is as wild as it looks, and fits the members about as wild
// (see fits): the maintainer, 2026-11-18: "we should apply stats ... 0 = elegant and 10 = animal ... Depending on that
// stat we assign the skills".

/**
 * A skill that shows a member's manner, and so has a wildness of its own: the idle skills ([IdleSkill]), and the count
 * in before a phrase ([CountIn]). It fits a member about as wild (see [fits]).
 */
sealed interface Mannerism : Skill {
    /** How wild it looks, a whole number from 0 (elegant) to 10 (animal): each kind has its own. */
    val wildness: Int
}

/** How far apart a member's wildness and a skill's may be for the skill to fit the member (see [fits]). */
const val WILDNESS_FIT: Int = 3

/**
 * Whether [skill] fits a member of the [wildness] (0 elegant to 10 animal, see `Character.wildness`): where the two
 * are at most [WILDNESS_FIT] apart. Which member has which skill is still chosen on the sheets.
 */
fun fits(skill: Mannerism, wildness: Int): Boolean = abs(skill.wildness - wildness) <= WILDNESS_FIT

/**
 * How many seconds a pause must reach past a use of an idle skill on either side: no note of the part begins within
 * them. The hand is back on the instrument a while before the next note, and was off it only a while after the last.
 * A pose that does more of its own around a phrase asks for a longer margin (see [idleAt]).
 */
const val IDLE_MARGIN: Double = 1.0

/**
 * A skill used now and then by chance, at the moments its [timing] gives (see [ChanceInSlots]), but only where the
 * member's part pauses for the whole of the use and [IDLE_MARGIN] either side of it (see [idleAt]): so a hand is
 * never off the instrument when a note comes, and no use is cut off halfway. That holds as far as the part is known
 * around now: a use is judged by the notes known when it is asked for, so one is never cut off only where the song
 * is known its longest use plus its margin ahead of now and behind it (who reads the song sees to that; a song played
 * live is read fourteen seconds ahead and kept sixteen behind). The binding case is Rune's stretch of the neck: 4.5 s
 * and a margin of at most nine (see `MarimbaPlayer.idleMargin`), 13.5 s. What it does with the figure is the pose's
 * own.
 */
sealed interface IdleSkill : Mannerism {
    /** When it may be used, and how much. */
    val timing: ChanceInSlots

    /** Where its rolls are taken from, as for a timed skill (see [TimedSkill.rolls]): each kind its own. */
    val rolls: Double

    /** The skill's own chance, from the member's [roll]: the same, its indices moved on by [rolls]. */
    fun own(roll: (Double) -> Double): (Double) -> Double = { index -> roll(index + rolls) }
}

/** The free hand (a string player's strumming hand) goes to the side of the head, scratches there a little, and comes back. */
data class HeadScratch(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 5000.0

    override val wildness: Int get() = 8
}

/**
 * A slouch: the member lets go for a while, the relaxed pose. A string player's strumming hand comes off the strings
 * and hangs limp before the instrument, the shoulders drop and the weight settles. Only a string player's pose shows
 * it so far.
 */
data class Slouch(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 7000.0

    override val wildness: Int get() = 7
}

/**
 * The head tilts slowly ear to shoulder: to one side, back through the middle, to the other side, and back; the
 * shoulders still. One stretch to a use.
 */
data class NeckStretch(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 6000.0

    override val wildness: Int get() = 2
}

/** A string player shakes out the fretting hand: it comes off the neck, hangs, shakes loosely from the wrist, and returns. */
data class ShakeOut(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 8000.0

    override val wildness: Int get() = 4
}

/**
 * A string player tunes: the fretting hand goes up to a peg at the head of the neck and turns it a little, the head
 * leans in toward the instrument as if listening, and the strumming hand plucks a string softly now and then.
 */
data class TuneUp(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 9000.0

    override val wildness: Int get() = 4
}

/** The strumming hand goes to the peak of the cap, tugs it down a little, and comes back. For one with a cap. */
data class CapTug(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 11000.0

    override val wildness: Int get() = 7
}

/** The strumming arm wipes the brow: the forearm goes across it in one swipe, the head dipping a little into it. */
data class BrowWipe(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 12000.0

    override val wildness: Int get() = 8
}

/** The drummer twirls a stick: the right hand held up beside him, the stick spins in the fingers a few turns. */
data class StickTwirl(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 13000.0

    override val wildness: Int get() = 7
}

/**
 * The drummer scratches the back of his neck with the butt end of the left stick, the head tilting forward a little.
 */
data class NeckScratch(override val timing: ChanceInSlots) : IdleSkill {
    override val rolls: Double get() = 14000.0

    override val wildness: Int get() = 10
}

/**
 * An idle [skill] in use: how much ([share], above 0 up to 1), how long ago the use began ([since], seconds: the
 * share rising over the first of them), and how long the whole use lasts ([length], seconds, from its start to the
 * end of its fall): a gesture goes by them.
 */
data class IdleInUse(val skill: IdleSkill, val share: Double, val since: Double, val length: Double)

/**
 * The use of [skill] at [t] (seconds into the show), where it is in use: how much, as its timing gives (see
 * [ChanceInSlots.inUse]), and since when; null where it is not, and all through a use where a note of the part begins
 * within it or less than [margin] seconds either side of it (a note just a margin away is allowed, on either side),
 * and all through one that begins before [arrives] (when the member is at their place: not on the walk-in).
 * [margin]: [IDLE_MARGIN], or more where the member's pose does more of its own around a phrase.
 * [plays]: the member's notes (or hits), sorted by when they begin. [roll]: the member's own chance (see [usesAt]);
 * the skill takes its rolls from its own place in it ([IdleSkill.rolls]).
 */
fun idleAt(
    skill: IdleSkill,
    t: Double,
    roll: (Double) -> Double,
    plays: List<Timed>,
    arrives: Double = Double.NEGATIVE_INFINITY,
    margin: Double = IDLE_MARGIN,
): IdleInUse? {
    val own = skill.own(roll)
    val span = skill.timing.spanAt(t = t, roll = own) ?: return null

    if (span.start < arrives) {
        return null
    }

    // the pause the margin before the use lies in (a note just at it is allowed) lasts until the margin after the use
    // is over (a note just at it is allowed)
    if (!PartPause.at(plays = plays, t = span.start - margin).lastsUntil(span.endInclusive + margin)) {
        return null
    }

    val share = skill.timing.inUse(t = t, roll = own)

    if (share <= 0) {
        return null
    }

    return IdleInUse(skill = skill, share = share, since = t - span.start, length = span.endInclusive - span.start)
}

/**
 * Every idle one of [skills] that is in use at [t], in the sheet's order (see [idleAt]): the rank, as for the timed
 * skills (see [Skill]). Empty where none is. [plays]: the member's notes (or hits), sorted by when they begin;
 * [roll]: the member's own chance; [arrives] and [margin]: as for [idleAt].
 */
fun idlesAt(
    skills: List<Skill>,
    t: Double,
    roll: (Double) -> Double,
    plays: List<Timed>,
    arrives: Double = Double.NEGATIVE_INFINITY,
    margin: Double = IDLE_MARGIN,
): List<IdleInUse> {
    val out = mutableListOf<IdleInUse>()

    for (skill in skills) {
        if (skill !is IdleSkill) {
            continue
        }

        val used = idleAt(skill = skill, t = t, roll = roll, plays = plays, arrives = arrives, margin = margin)

        if (used != null) {
            out.add(used)
        }
    }

    return out
}
