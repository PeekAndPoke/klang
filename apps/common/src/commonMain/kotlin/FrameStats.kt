/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

/**
 * How fast a picture was drawn, from the times of its frames: [fps] from the median frame (so a few slow frames do
 * not decide it), and the [slowestMs] frame (a stutter shows there), leaving out the outliers: a frame longer than
 * [OUTLIER] medians is not the picture's cost but a pause (a tab switch, the window away, a garbage collection; the
 * maintainer, 2026-11-26: "we have to drop outliers", KLANG-00492).
 *
 * A display caps the frames at its refresh rate (60 a second on most): a machine at the cap draws as fast as the
 * display shows, and above the cap nothing is told apart.
 */
data class FrameStats(val fps: Double, val slowestMs: Double) {
    companion object {
        /** How many medians a frame may take before it is an outlier, left out of [slowestMs]. */
        const val OUTLIER = 8.0

        /** No frames at all: nothing was drawn. */
        val none = FrameStats(fps = 0.0, slowestMs = 0.0)

        /** The stats of frames that took [frameMs] each, in milliseconds; what is not a positive number is left out. */
        fun of(frameMs: List<Double>): FrameStats {
            val sorted = frameMs.filter { it.isFinite() && it > 0.0 }.sorted()

            if (sorted.isEmpty()) {
                return none
            }

            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2

            // the slowest frame that is no outlier (the median itself is none)
            val slowest = sorted.last { it <= OUTLIER * median }

            return FrameStats(fps = 1000.0 / median, slowestMs = slowest)
        }
    }
}
