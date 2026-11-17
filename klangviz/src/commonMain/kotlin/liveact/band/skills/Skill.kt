/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

// What a member can do on their own, beyond playing and moving with the beat: their skills, equipped on their sheet.
// The names of the looks (agreed 2026-11-17): the first word says where the look goes, the second what kind of
// look it is. A Gaze is slow and wanders from spot to spot, a Glance is short, there and back, a Stare is long,
// fixed and still.

/**
 * Something a member can do, equipped on their sheet (`Member.skills`): plain data, and what acts on the figure is
 * an ordinary function that takes it. A member without a skill simply does not do it (the maintainer, 2026-11-17:
 * "If one does not have it, they will simply not answer to eye-contact").
 *
 * Rank: the sheet's order is the order of rank. Where two skills want the head at the same moment, the first on
 * the sheet has it (see [firstInUse], which goes by it among the skills that act in the pose). Not yet between the eye
 * contact and the rest: today a look in the pose (Klaue's [CameraGlance]) and an eye contact can act in one frame,
 * the eye contact after the pose and so over it. That is kept as it is, since changing it would change the picture;
 * it agrees with the sheets, where [EyeContact] comes first. The next skill that turns the head (Sirene's) applies
 * the rank to all of them.
 */
sealed interface Skill

/** A skill used now and then by chance, at the moments its [timing] gives (see [SkillTiming]). */
sealed interface TimedSkill : Skill {
    /** When it is used, and how much. */
    val timing: SkillTiming
}

/**
 * Eye contact: the member returns a look another member offers (the pairs and their moments: `eyeContact`). One
 * without it does not answer; the other still offers the look.
 */
data object EyeContact : Skill

/**
 * A quick look at the camera, there and back, at the moments its [timing] gives. A string player performs it (the
 * pose reads it through `lookAt`); on another member it does nothing yet.
 */
data class CameraGlance(override val timing: SkillTiming) : TimedSkill

/** A timed [skill] in use, and how much ([share], above 0 up to 1). */
data class SkillInUse(val skill: TimedSkill, val share: Double)

/**
 * The first of [skills], in the sheet's order, that is in use at [t], with how much: the rank (see [Skill]). Null
 * where none is. Only timed skills are asked. [roll]: the member's own chance (see [SkillTiming.inUse]).
 */
fun firstInUse(skills: List<Skill>, t: Double, roll: (Double) -> Double): SkillInUse? {
    for (skill in skills) {
        if (skill !is TimedSkill) {
            continue
        }

        val share = skill.timing.inUse(t = t, roll = roll)

        if (share > 0) {
            return SkillInUse(skill = skill, share = share)
        }
    }

    return null
}
