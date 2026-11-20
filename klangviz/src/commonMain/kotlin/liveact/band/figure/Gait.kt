/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

/**
 * How a member walks: [hips], how much the hips move as they walk (see [HipMotion]), 0 not at all, 1 as much as
 * Sirene's stroll had them move before there were gaits. What is in effect on a walk is this times the kind of walk's
 * own ([WalkKind], see [hipsOn]; the maintainer, 2026-11-20: "a setting for each char how much the hip moves. A
 * default and then an additional modifier depending on the 'kind of walk' the char is doing").
 */
data class Gait(val hips: Double) {
    /** How much the hips move on a walk of the [kind]: the member's own times the kind's. */
    fun hipsOn(kind: WalkKind): Double = hips * kind.hips

    /** How much the hips move where the kind of walk's modifier in effect is [modifier] (a blend of two kinds). */
    fun hipsWith(modifier: Double): Double = hips * modifier
}

/**
 * The kinds of walk, each with its modifier on the hips ([hips]: what a member's [Gait] is multiplied with). The one
 * table to tune them in.
 */
enum class WalkKind(val hips: Double) {
    /** The walk-in before the song: on their way to work. */
    WalkIn(0.7),

    /** A plain stroll about the front of the stage. */
    Stroll(1.0),

    /** The long arc along the stage's front edge, before the audience: she is seen. */
    Edge(1.15),

    /** The walk to the camera, and the walk right up to it: the catwalk. */
    Camera(1.4),

    /** The way home to her place at a stroll's end. */
    Home(0.85),
}
