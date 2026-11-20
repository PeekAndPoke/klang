/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

/**
 * When the show's drawer ([ShowDrawer]) is [shown], by what the pointer does; times are in milliseconds, given by the
 * caller, so it can be judged without a browser.
 *
 * A mouse: a move brings the drawer, and it goes [MOUSE_MS] after the last move, unless the pointer rests on it then
 * (a visitor reading it does not move). A touch or a pen (a tap): a tap anywhere brings it, and it goes [TOUCH_MS]
 * after the last touch on it (each touch on the drawer, its buttons or the volume counts); a tap on the stage outside
 * it while it is shown hides it at once, and that tap is the drawer's ([tapped] says so), so the show takes nothing
 * from it. A finger never rests on the drawer the way a mouse pointer does: a tap on it never holds it.
 *
 * A tap's own intent wins over the time running out: a tap on the stage when the drawer's time has come (it is about
 * to go, and [due] has not been asked yet) shows it anew, as a tap on a hidden drawer does; it does not hide it.
 *
 * Ask [due] now and then (a timer): it hides the drawer where its time has come.
 */
class DrawerShowing {

    /** Whether the drawer is shown. */
    var shown: Boolean = false
        private set

    /** When the drawer goes, where nothing comes before. */
    private var hideAt: Double = 0.0

    /** A mouse pointer rests on the drawer: it stays. */
    private var resting: Boolean = false

    /** The mouse moved at [now], [onDrawer] or over the stage: the drawer is shown, and goes [MOUSE_MS] later. */
    fun mouseMoved(now: Double, onDrawer: Boolean) {
        shown = true
        resting = onDrawer
        hideAt = now + MOUSE_MS
    }

    /**
     * A touch or a pen came down at [now], [onDrawer] or on the stage. Returns whether the tap is the drawer's alone
     * (one on the stage that hid it): the show must not take it as its own click.
     */
    fun tapped(now: Double, onDrawer: Boolean): Boolean {
        resting = false

        // a drawer whose time has come is as good as gone: the tap shows it anew
        if (shown && !onDrawer && now < hideAt) {
            shown = false

            return true
        }

        shown = true
        hideAt = now + TOUCH_MS

        return false
    }

    /** Hides the drawer where its time has come at [now], unless a mouse pointer rests on it. Returns whether it hid. */
    fun due(now: Double): Boolean {
        if (!shown || resting || now < hideAt) {
            return false
        }

        shown = false

        return true
    }

    companion object {
        /**
         * Whether a key pressed while a control of the drawer has the focus goes on to the show, with `?dev` (the
         * show's keys work while the drawer is open): a single character, as the show's keys are (`n`, `x`, `1`), but
         * not the space, which a button takes, and none with Ctrl, Alt or Meta ([modified]). The rest (the space,
         * Enter, the arrows a slider takes, Tab, Escape) stays in the drawer. [key] is the event's `key`.
         */
        fun passesKey(key: String, modified: Boolean): Boolean = !modified && key.length == 1 && key != " "

        /** How long the drawer stays after the mouse last moved, in milliseconds. */
        const val MOUSE_MS = 3000.0

        /** How long the drawer stays after the last touch on it, in milliseconds. */
        const val TOUCH_MS = 10_000.0
    }
}
