/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.theatre.CrowdLight
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.kraft.addons.threejs.js.Vector3

/**
 * Where a member's head is asked to look at one moment, and how much: [share] from 0 (not at all) to 1 (all the
 * way), toward [at], a place in the world. [at] may be a live vector (the camera's place, which the show keeps up to
 * date): it is read when the head turns. [behind]: the place may lie behind the member (another member), and the
 * head turns as far as a head can toward it, over the shoulder (not at one straight behind); else the look lets
 * go where the place is out of sight (see `headReach`). [still]: a stare, which calms the body's nod and wander while it lasts (the trumpet player's pose does; a string player's
 * does not yet). How the head turns to it is the pose's own.
 */
class Look(val share: Double, val at: Vector3, val behind: Boolean = false, val still: Boolean = false)

/**
 * Where a look can go, built once by the band: the [camera]'s place (the vector the show keeps up to date), where a
 * member's eyes are now ([eyes], read when it is asked), and the [crowd]'s spots (see [crowdSpots]).
 */
class LookPlaces(val camera: Vector3, val eyes: (Player) -> Vector3, val crowd: List<CrowdLight>)

/**
 * Where the skills of [who] ([skills], their sheet's) ask the head to look at [t]: one look for each timed skill in
 * use (see [usesAt]), the last on the sheet first, so each turns the head over the ones before it and the first on
 * the sheet has it (the rank, see [Skill]). Empty where none is in use. [roll]: the member's own chance under the
 * show's seed (`Show.own` with the member's salt); [places]: where a look can go.
 *
 * Only the skills that act in a player's pose answer here. The eye contact acts after the poses, where the two
 * look at each other (see `eyeContact`).
 */
fun lookAt(who: Player, skills: List<Skill>, t: Double, roll: (Double) -> Double, places: LookPlaces): List<Look> {
    return usesAt(skills = skills, t = t, roll = roll).asReversed().mapNotNull { used ->
        when (val skill = used.skill) {
            is CameraGlance -> Look(share = used.share, at = places.camera)

            is CameraStare -> Look(share = used.share, at = places.camera, still = true)

            is BandGlance -> Look(share = used.share, at = places.eyes(skill.whom(t = t, roll = roll, who = who)), behind = true)

            is CrowdGaze -> when {
                places.crowd.isEmpty() -> null
                else -> {
                    val move = skill.spotAt(t = t, roll = roll, spots = places.crowd.size)
                    val from = places.crowd[move.from]
                    val to = places.crowd[move.to]
                    val w = move.share
                    val at = vec(x = from.x + (to.x - from.x) * w, y = from.y + (to.y - from.y) * w, z = from.z + (to.z - from.z) * w)

                    Look(share = used.share, at = at)
                }
            }
        }
    }
}
