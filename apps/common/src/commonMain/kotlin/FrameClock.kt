/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

/**
 * The picture test's clock: how long the stage has been drawn ([drawnMs]), counted from the frames themselves, and
 * only while the window is there to be drawn in (the maintainer, 2026-11-26: "stop when the window loses focus, as
 * this prevents drawing and we will measure something like 100 seconds for one frame", KLANG-00492).
 *
 * While the window is [away] (it lost the focus, or the page is hidden) no frame counts, and the first frame after it
 * is [back] does not count either: it spans the time away. So the test pauses while nobody looks and goes on where it
 * stopped, and its frames are the ones drawn as the show would draw them.
 *
 * It starts as [there] says: there, its first frame does not count (it spans the time before the drawing began, the
 * stage being made); not there, it starts [away].
 */
class FrameClock(there: Boolean) {
    /** How long the frames that counted took together, in milliseconds. */
    var drawnMs: Double = 0.0
        private set

    private var gone = !there
    private var spans = there

    /** The window lost the focus, or the page was hidden: frames count no more until it is [back]. */
    fun away() {
        gone = true
    }

    /** The window is there again: frames count again, from the one after the next (which spans the time away). */
    fun back() {
        if (gone) {
            gone = false
            spans = true
        }
    }

    /**
     * A frame that took [ms]: whether it counts. It does where the window is there, it is not the one across a time
     * away, and its time is a number above 0 (NaN-guard: what is no number counts nothing); then it adds to [drawnMs].
     */
    fun frame(ms: Double): Boolean {
        if (gone) {
            return false
        }

        if (spans) {
            spans = false

            return false
        }

        if (!(ms.isFinite() && ms > 0.0)) {
            return false
        }

        drawnMs += ms

        return true
    }
}
