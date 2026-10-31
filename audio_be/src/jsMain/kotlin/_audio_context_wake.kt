/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.Promise

/**
 * The events that ask a stopped AudioContext to run again: the ones that count as a user gesture on every engine,
 * touch included (a tap's `pointerdown` does not: its activation comes with `pointerup`, `touchend` and `click`), and
 * the page shown again.
 */
private val WAKE_EVENTS = listOf("pointerup", "touchend", "click", "keydown", "visibilitychange")

/**
 * Puts a stopped AudioContext back to running (KLANG-00399, problem 9): after the machine slept, a call on a phone
 * (iOS says "interrupted"), a tab hidden for long. `resume()` is asked on [WAKE_EVENTS] seen on [target], while
 * [state] says the context stands still; never from the state change itself, as Safari's `resume()` waits for a user
 * gesture.
 *
 * A `resume()` that may not start yet is not refused: it waits, and resolves when the context runs. So a gesture asks
 * every time (it may be the one that is allowed), while the page shown again, no gesture, asks only when [visible] and
 * no such ask is still waiting. Listens in the capture phase, so a component that stops a press cannot swallow it.
 * While the context stands still the backend reports nothing, and the playback clock stands still with it
 * (`PlaybackClock`). Returns the removal.
 */
internal fun keepRunning(
    target: EventTarget,
    visible: () -> Boolean,
    state: () -> String,
    resume: () -> Promise<Unit>,
): () -> Unit {
    var waiting = false

    val wake: (Event) -> Unit = { event ->
        val stopped = state() == "suspended" || state() == "interrupted"
        val gesture = event.type != "visibilitychange"

        if (stopped && (gesture || (visible() && !waiting))) {
            waiting = true

            resume().then(
                onFulfilled = { waiting = false },
                onRejected = { e ->
                    waiting = false
                    console.warn("JsAudioBackend: the AudioContext did not resume", e)
                },
            )
        }
    }

    for (type in WAKE_EVENTS) {
        target.addEventListener(type, wake, true)
    }

    return {
        for (type in WAKE_EVENTS) {
            target.removeEventListener(type, wake, true)
        }
    }
}
