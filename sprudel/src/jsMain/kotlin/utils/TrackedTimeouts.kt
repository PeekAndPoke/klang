/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.utils

import kotlinx.browser.window

/**
 * Window timeouts that can be cancelled together: [schedule] runs an action after a delay and tracks its timer id
 * until it fires, and [cancelAll] clears every timer still pending. The mini-notation editors schedule their
 * playback highlights with it and drop them all on stop, on a live update and on unmount.
 */
class TrackedTimeouts {

    private val pending = mutableSetOf<Int>()

    /** The number of timers scheduled and not yet fired or cancelled. */
    val pendingCount: Int get() = pending.size

    /** Runs [action] after [delayMs] milliseconds; the timer un-tracks itself when it fires. */
    fun schedule(delayMs: Int, action: () -> Unit) {
        var id = 0

        id = window.setTimeout({
            pending.remove(id)
            action()
        }, delayMs)

        pending.add(id)
    }

    /** Clears every pending timer: none of their actions runs. */
    fun cancelAll() {
        pending.forEach { window.clearTimeout(it) }
        pending.clear()
    }
}
