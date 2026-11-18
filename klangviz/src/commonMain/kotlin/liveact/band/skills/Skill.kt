/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.band.Player
import kotlin.math.floor
import kotlin.math.min

// What a member can do on their own, beyond playing and moving with the beat: their skills, equipped on their sheet.
// The names of the looks (agreed 2026-11-17): the first word says where the look goes, the second what kind of
// look it is. A Gaze is slow and wanders from spot to spot, a Glance is short, there and back, a Stare is long,
// fixed and still.

/**
 * Something a member can do, equipped on their sheet (`Member.skills`): plain data, and what acts on the figure is
 * an ordinary function that takes it. A member without a skill simply does not do it (the maintainer, 2026-11-17:
 * "If one does not have it, they will simply not answer to eye-contact").
 *
 * Rank: the sheet's order is the order of rank. Where two timed skills want the head at the same moment, the first
 * on the sheet has it: [usesAt] gives every one in use in the sheet's order, and the pose turns the head to each in
 * turn from the last to the first, each over the ones before it as far as it is in use. So the first in use has the
 * head once it is all there, and one that begins while another is in use eases the head over from it instead of
 * snapping. The eye contact stays apart: it acts after the poses and over them (see `eyeContact`), as it did before
 * there were skills, and it stands first on every sheet that has it.
 *
 * Not every skill is timed: the crossed mallets ([CrossedMallets]) and the count in ([CountIn]) go by the member's
 * part, an idle skill ([IdleSkill]) by chance within the pauses of the part. The idle ones rank among themselves in
 * the sheet's order.
 */
sealed interface Skill

/** A skill used now and then by chance, at the moments its [timing] gives (see [SkillTiming]). */
sealed interface TimedSkill : Skill {
    /** When it is used, and how much. */
    val timing: SkillTiming

    /**
     * Where its rolls are taken from: added to every index it asks the member's chance for (see [own]). Each kind
     * of timed skill has its own, so two kinds never take the same roll for the same slot.
     */
    val rolls: Double

    /** The skill's own chance, from the member's [roll]: the same, its indices moved on by [rolls]. */
    fun own(roll: (Double) -> Double): (Double) -> Double = { index -> roll(index + rolls) }
}

/**
 * Eye contact: the member returns a look another member offers (the pairs and their moments: `eyeContact`). One
 * without it does not answer; the other still offers the look.
 */
data object EyeContact : Skill

/**
 * A quick look at the camera, there and back, at the moments its [timing] gives. Only where the camera is in sight.
 */
data class CameraGlance(override val timing: SkillTiming) : TimedSkill {
    /** The member's own rolls, unmoved: those the glance took before it was a skill (Klaue's picture stays as it was). */
    override val rolls: Double get() = 0.0
}

/**
 * A long, still look at the camera, at the moments its [timing] gives: the head turns to it and stays, and the
 * body's nodding and wandering calm down while it lasts. Only where the camera is in sight.
 */
data class CameraStare(override val timing: SkillTiming) : TimedSkill {
    override val rolls: Double get() = 1000.0
}

/**
 * The look "inward": a short look at one of the other members, there and back, at the moments its [timing] gives.
 * Whom, the roll chooses for each use (see [whom]). The head turns as far as a head can, also to one who stands
 * behind, over the shoulder; not to one straight behind (see `overShoulder`).
 */
data class BandGlance(override val timing: SkillTiming) : TimedSkill {
    override val rolls: Double get() = 2000.0

    /**
     * Whom [who] glances at in the use at [t]: one of the band's other players, chosen by the roll of the use's
     * slot, so the same all through a use. [roll]: the member's own chance (see [usesAt]).
     */
    fun whom(t: Double, roll: (Double) -> Double, who: Player): Player {
        val others = Player.entries.filter { it != who }
        val drawn = floor(own(roll)(timing.slotOf(t) + 0.5) * others.size).toInt()

        return others[min(drawn, others.size - 1)]
    }
}

/**
 * The other look "inward" (the maintainer, 2026-11-18: "not looking anywhere"): a long, still look at nothing, the
 * head a little down and aside, at the moments its [timing] gives. The body's nodding and wandering calm down
 * while it lasts, as in a [CameraStare].
 */
data class InwardStare(override val timing: SkillTiming) : TimedSkill {
    override val rolls: Double get() = 4000.0
}

/** A timed [skill] in use, and how much ([share], above 0 up to 1). */
data class SkillInUse(val skill: TimedSkill, val share: Double)

/**
 * Every one of [skills] that is in use at [t], with how much, in the sheet's order: the rank (see [Skill]). Empty
 * where none is. Only timed skills are asked. [roll]: the member's own chance (see [SkillTiming.inUse]); each skill
 * takes its rolls from its own place in it ([TimedSkill.rolls]).
 */
fun usesAt(skills: List<Skill>, t: Double, roll: (Double) -> Double): List<SkillInUse> {
    val out = mutableListOf<SkillInUse>()

    for (skill in skills) {
        if (skill !is TimedSkill) {
            continue
        }

        val share = skill.timing.inUse(t = t, roll = skill.own(roll))

        if (share > 0) {
            out.add(SkillInUse(skill = skill, share = share))
        }
    }

    return out
}
