/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.overShoulder
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// How the frontwoman's head lets go of a look that goes out of reach as she turns on a stroll: slowly, as a look
// falls, never with a snap (the maintainer, 2026-11-19: "One sudden movement is fine, but the way back then needs
// to be slower"). Plain arithmetic, so it has a spec.

/**
 * How far the head still takes a look at [t] (seconds) that it may take [now] (0 to 1) and took as far as
 * [before] says a moment ago: as far as it may now, or, where it took it further, letting go of that over [fall]
 * seconds, eased as a look's fall is (see `ChanceInSlots`). So a look that goes out of reach at once still goes back
 * over [fall] seconds; one that comes into reach is taken at once. [before]: the moments before [t], each when
 * (seconds) and how far (0 to 1); those older than [fall], or after [t], count for nothing. With moments at fixed
 * times (a grid, not a step back from [t]) the answer moves smoothly with [t].
 */
fun heldReach(t: Double, now: Double, before: List<Pair<Double, Double>>, fall: Double): Double {
    var held = now

    for ((at, reach) in before) {
        val age = t - at

        if (age < 0.0 || age >= fall) {
            continue
        }

        held = max(held, reach * (1 - smooth(age / fall)))
    }

    return held
}

/**
 * How far the head takes a look at [t] (seconds) whose place lies at [yaw] off the way she faces now (radians,
 * either side): as far as a head takes a look there ([reach]: `headReach` for the look's kind), and where she has
 * turned away from it, letting go of it over [fall] seconds ([heldReach]), from the moments on a grid of [step]
 * seconds before [t] ([yawBefore]: where the place lay at the grid's [k]th moment back, k from 0, at
 * `floor(t / step) * step - k * step`; null where her way before is not known, and the look is as far as it is now).
 *
 * Never more than a look over the shoulder goes ([overShoulder]): nothing straight behind her, so a look that
 * crosses behind her (where the head's turn changes from one shoulder to the other) goes to nothing there, and
 * the share never jumps.
 */
fun lookReach(
    t: Double,
    yaw: Double,
    reach: (Double) -> Double,
    yawBefore: ((Int) -> Double)?,
    fall: Double,
    step: Double,
): Double {
    val now = reach(yaw)

    if (yawBefore == null) {
        return now
    }

    val grid = floor(t / step) * step
    // the moments that can count: younger than the fall
    val before = (0 until (fall / step).roundToInt()).map { k -> (grid - k * step) to reach(yawBefore(k)) }

    return min(heldReach(t = t, now = now, before = before, fall = fall), overShoulder(yaw))
}
