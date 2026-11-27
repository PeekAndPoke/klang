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
 *
 * A frame longer than [GAP_MS] after a shorter one does not count either: the browser stopped drawing for a while
 * without a word (a window covered, a machine asleep; the maintainer, 2026-11-27: "when i leave the window i get
 * 'slowest frame of seconds'"). A long frame after a long one is the machine's pace and counts: a machine that draws
 * slower than a frame a second is still measured (and gets the lighter picture).
 */
class FrameClock(there: Boolean) {
    companion object {
        /** A frame longer than this, in milliseconds, is a pause, not the picture's cost (the live act's `PAUSE_MS`). */
        const val GAP_MS = 1000.0
    }

    /** How long the frames that counted took together, in milliseconds. */
    var drawnMs: Double = 0.0
        private set

    private var gone = !there
    private var spans = there

    /** Whether the last frame with a number was longer than [GAP_MS]. */
    private var lastLong = false

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
     * away, its time is a number above 0, and it is no longer than [GAP_MS], or the frame before it was longer than
     * [GAP_MS] too; then it adds to [drawnMs].
     */
    fun frame(ms: Double): Boolean {
        // NaN-guard: what is no number counts nothing, and says nothing of the frames' pace
        if (!(ms.isFinite() && ms > 0.0)) {
            return false
        }

        val long = ms > GAP_MS
        val pause = long && !lastLong

        lastLong = long

        if (gone) {
            return false
        }

        if (spans) {
            spans = false

            return false
        }

        // a single long frame between shorter ones is a pause, and counts nothing
        if (pause) {
            return false
        }

        drawnMs += ms

        return true
    }
}
