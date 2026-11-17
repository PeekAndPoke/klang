/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.band.instruments.Equipment

// What a strung instrument says of itself: its shape, its strings and tuning, how it is played and how it hangs. The
// guitar and the bass each give their own (see Guitar, Bass).

/**
 * A body's outline, seen from the front with the neck to the right (x along the neck, y across, in the figure's
 * measure): from the neck pocket's lower corner round the body to its upper corner, as [points] a smooth curve goes
 * through. Each instrument has its own (none is a maker's model).
 */
class BodyOutline(val points: List<Pair<Double, Double>>)

/**
 * A strung instrument and how it is played. [scale]: from the nut to the bridge; [bodyHalfLength] and
 * [bodyHalfWidth]: the body's half length and half width; [strings]: how many; [neck]: the neck's width; [tilt]:
 * the neck's tilt; how the right hand plays: [swing] how far it goes through the strings, [tau] how quickly (in
 * seconds), [arm] the share of the swing the arm does (the rest is the wrist); [hang]: how low it hangs from the
 * trunk; [outline]: the body's outline unless the player has another; [pickups]: where the pickups sit along it;
 * [head]: the head's outline, as the body's is drawn; [tuning]: its strings' notes, by which the left hand finds
 * each note's string and fret; [equipment]: what its musician holds for it.
 */
class StringDesign(
    val scale: Double,
    val bodyHalfLength: Double,
    val bodyHalfWidth: Double,
    val strings: Int,
    val neck: Double,
    val tilt: Double,
    val swing: Double,
    val tau: Double,
    val hang: Double,
    val arm: Double,
    val outline: BodyOutline,
    val pickups: List<Double>,
    val head: List<Pair<Double, Double>>,
    val tuning: Tuning,
    val equipment: Equipment,
)
