/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

import kotlin.math.max
import kotlin.math.min

/**
 * How finely the picture is drawn, following how fast it can be drawn. Nearly all of the stage's cost is paid per
 * pixel (the lamps, their shadows, the fog), and a fine screen has four times the pixels of a plain one: where the
 * frames come too slowly the picture is drawn with fewer pixels and scaled up, a little softer and much faster.
 *
 * [ratio] is the pixels drawn for each of the page's, each way. It starts at [full] (the screen's own) and goes down
 * in steps, never below [least]: a plain screen's one pixel, so it is never coarser than a plain screen shows it. It
 * comes up again only through [reset], or all the way to full where fewer pixels did not help (see [judge]): a
 * picture that goes soft and sharp by turns is worse than a soft one.
 *
 * Tell it the frame rate of each stretch of frames with [judge].
 *
 * A known limit: whether fewer pixels help is asked once. A slow machine in a browser that draws 30 frames a second
 * at most gains up to those 30 and then goes on down to the floor for nothing; never coarser than a plain screen,
 * and a new window size starts it fine again.
 */
class Sharpness(full: Double) {

    /** The screen's own pixels for each of the page's, each way. */
    var full: Double = full
        private set

    /** The fewest it goes down to ([leastFor]). */
    val least: Double get() = leastFor(full = full)

    /** The pixels drawn for each of the page's, each way. */
    var ratio: Double = full
        private set

    /** How many stretches in a row were too slow. */
    private var slow = 0

    /** How many steps down were asked for since the picture was full (at the floor a step changes nothing). */
    private var steps = 0

    /** The frame rate that made it take the first step. */
    private var before = 0.0

    /** Fewer pixels did not help: it stays at full. */
    private var helpless = false

    /** Fewer pixels did help once: that is not asked again. */
    private var proven = false

    /**
     * Takes the frame rate of the last stretch of frames ([fps], a second). After [PATIENCE] stretches in a row below
     * [ENOUGH] it takes a step down; a single slow stretch (a stutter) is not enough.
     *
     * Where the first two steps did not make the frames come faster, pixels are not what holds them back (a browser
     * that saves power draws 30 frames a second, whatever there is to draw): it goes back to full and stays there.
     * That is asked once, right after those two steps; frames that came fast enough after a step, and faster
     * than before it by the same measure, answer it too.
     * Later a slow stretch always means a step: by then the scene may have become heavier.
     *
     * Tell it only of stretches that were seen: a page in the background draws a frame a second in some browsers,
     * which says nothing about the machine.
     *
     * Returns whether [ratio] changed.
     */
    fun judge(fps: Double): Boolean {
        if (helpless) {
            return false
        }

        if (fps >= ENOUGH) {
            slow = 0
            proven = proven || (steps > 0 && fps >= before * HELPED)

            return false
        }

        slow++

        if (slow < PATIENCE) {
            return false
        }

        slow = 0

        if (steps == 0) {
            before = fps
        }

        if (!proven && steps >= TRIED) {
            if (fps < before * HELPED) {
                helpless = true
                steps = 0

                return change(full)
            }

            proven = true
        }

        steps++

        return change(max(least, ratio * STEP))
    }

    /**
     * Back to full, and judging from the start: after the picture's size changed. [full] is the screen's own ratio
     * as it is now (a window moved to another screen has another). Returns whether [ratio] changed.
     */
    fun reset(full: Double = this.full): Boolean {
        this.full = full
        slow = 0
        steps = 0
        helpless = false
        proven = false

        return change(full)
    }

    private fun change(to: Double): Boolean {
        val changed = to != ratio

        ratio = to

        return changed
    }

    companion object {
        /**
         * Frames a second that are enough: below it the picture is too slow. A little under 50, which a screen of 50
         * pictures a second gives when nothing holds the frames back, measured now a hair above and now a hair below.
         */
        const val ENOUGH = 47.0

        /** So many slow stretches in a row before a step down. */
        const val PATIENCE = 4

        /** A step down: this share of the pixels each way (about 0.7 of the pixels drawn). */
        const val STEP = 0.84

        /** After so many steps the frames must have come faster. */
        const val TRIED = 2

        /** Faster by this factor at least, against the rate before the first step. */
        const val HELPED = 1.12

        /**
         * The fewest pixels the picture goes down to on a screen of [full] pixels for each of the page's, each way: a
         * plain screen's one, or the screen's own where it has fewer. The pixels a show on that screen may settle at,
         * and what notstrom's picture test draws at (`?soft`).
         */
        fun leastFor(full: Double): Double {
            // NaN-guard: a screen whose ratio is no number, or none at all, counts as a plain screen
            if (!(full > 0.0)) {
                return 1.0
            }

            return min(full, 1.0)
        }
    }
}
