/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.kraft.addons.threejs.js.Vector3

/**
 * Where a member's head is asked to look at one moment, and how much: [share] from 0 (not at all) to 1 (all the
 * way), toward [at], a place in the world. [at] is a live vector (the camera's place, which the show keeps up to
 * date): it is read when the head turns. How the head turns to it is the pose's own.
 */
class Look(val share: Double, val at: Vector3)

/**
 * Where a member's [skills] ask the head to look at [t]: the first on the sheet that wants it (see [Skill] for the
 * rank); null where none does. [roll]: the member's own chance under the show's seed (`Show.own` with the
 * member's salt); [camera]: where the camera is, the vector the show keeps up to date.
 *
 * Only the skills that act in a player's pose answer here. The eye contact acts after the poses, where the two
 * look at each other (see `eyeContact`).
 */
fun lookAt(skills: List<Skill>, t: Double, roll: (Double) -> Double, camera: Vector3): Look? {
    val used = firstInUse(skills = skills, t = t, roll = roll) ?: return null

    return when (used.skill) {
        is CameraGlance -> Look(share = used.share, at = camera)
    }
}
