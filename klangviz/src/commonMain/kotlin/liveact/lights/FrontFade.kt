/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import kotlin.math.max
import kotlin.math.min

/**
 * A share from 0 to 1 that follows the front lamp at a pace of its own: what comes and goes with the band's big
 * shadows (the haze's clearings, Sirene's eyes in her shadow) does not jump where the lamp does (the key f turns it
 * on and off in one frame). It goes up from 0 to 1 in [rise] seconds and down from 1 to 0 in [fall] seconds, at an
 * even pace; whoever reads it eases it as they need.
 *
 * It goes by the time between the frames, not by the show's time: a frozen show (`?t=62.1`) or a paused one still
 * comes to the lamp's state.
 */
class FrontFade(val rise: Double, val fall: Double) {

    init {
        require(rise > 0) { "A fade rises in some time: $rise" }
        require(fall > 0) { "A fade falls in some time: $fall" }
    }

    /** Where it stands now, 0 to 1. */
    var share: Double = 0.0
        private set

    /**
     * Takes it toward [target] (0 to 1; what is not a number counts as 0, the lamp off) by what [dt] seconds allow,
     * never past it; gives where it stands then. A [dt] below 0 or no number moves nothing.
     */
    fun follow(target: Double, dt: Double): Double {
        // NaN-guard: a target of no number is the lamp off, a time of no number is no time
        val to = if (target != target) 0.0 else target.coerceIn(0.0, 1.0)
        val step = if (dt != dt) 0.0 else max(0.0, dt)

        share = if (to > share) min(to, share + step / rise) else max(to, share - step / fall)

        return share
    }

    companion object {
        /** From this level on the front lamp counts as on ([on]). */
        const val ON = 0.5

        /**
         * How much the front lamp [front] is on, 0 to 1: its level, and 1 all through while it strobes (it throws
         * the shadows between the flashes too). A level of no number (`?front`) is off.
         */
        fun level(front: FrontLight): Double {
            if (front.strobing) {
                return 1.0
            }

            // NaN-guard: a lamp at no number is off
            return if (front.level != front.level) 0.0 else front.level.coerceIn(0.0, 1.0)
        }

        /** Whether the front lamp [front] is on, as 1 or 0: from [ON] of its [level] on. */
        fun on(front: FrontLight): Double = if (level(front) >= ON) 1.0 else 0.0
    }
}
