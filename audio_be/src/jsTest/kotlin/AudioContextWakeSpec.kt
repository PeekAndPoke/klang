/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.await
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.Promise

/**
 * [keepRunning] (KLANG-00399, problem 9): what asks a stopped AudioContext to run again. The browser's own policy (which
 * events it takes as a gesture, Safari's waiting `resume()`) is a device check; this guards the wiring.
 */
class AudioContextWakeSpec : StringSpec({

    class Rig(var state: String, var visible: Boolean = true) {
        val target: EventTarget = js("new EventTarget()").unsafeCast<EventTarget>()
        var asked = 0

        /** The promise each ask gets: waiting until [settle], as a `resume()` the browser does not allow yet. */
        private var settle: (() -> Unit)? = null

        val stop = keepRunning(
            target = target,
            visible = { visible },
            state = { state },
            resume = {
                asked++
                Promise { resolve, _ -> settle = { resolve(Unit) } }
            },
        )

        fun fire(type: String) {
            target.dispatchEvent(Event(type))
        }

        /** The waiting ask resolves; the promise's callback runs on the next turn. */
        suspend fun settle() {
            settle?.invoke()
            Promise.resolve(Unit).await()
        }
    }

    "a gesture asks a stopped context to run, every time, touch's activation events included" {
        val rig = Rig(state = "suspended")

        rig.fire("click")
        rig.fire("pointerup")
        rig.fire("touchend")
        rig.fire("keydown")

        rig.asked shouldBe 4
    }

    "an interrupted context is asked too; a running one is not" {
        val interrupted = Rig(state = "interrupted")

        interrupted.fire("click")
        interrupted.asked shouldBe 1

        val running = Rig(state = "running")

        running.fire("click")
        running.fire("visibilitychange")
        running.asked shouldBe 0
    }

    "a tap's pointerdown is not a gesture: it asks nothing" {
        val rig = Rig(state = "suspended")

        rig.fire("pointerdown")

        rig.asked shouldBe 0
    }

    "the page shown again asks once while that ask waits, and not while hidden" {
        val rig = Rig(state = "suspended", visible = false)

        rig.fire("visibilitychange")
        rig.asked shouldBe 0

        rig.visible = true
        rig.fire("visibilitychange")
        rig.fire("visibilitychange")
        rig.asked shouldBe 1

        rig.settle()
        rig.fire("visibilitychange")
        rig.asked shouldBe 2
    }

    "after the removal nothing asks" {
        val rig = Rig(state = "suspended")

        rig.stop()
        rig.fire("click")
        rig.fire("visibilitychange")

        rig.asked shouldBe 0
    }
})
