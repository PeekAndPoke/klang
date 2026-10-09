/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay

/**
 * [TrackedTimeouts] on the browser's real timers: an action runs after its delay and its timer un-tracks itself,
 * and [TrackedTimeouts.cancelAll] stops every pending action. JS only: it wraps `window.setTimeout`.
 */
class TrackedTimeoutsSpec : StringSpec({

    "a scheduled action runs after its delay, and its timer un-tracks itself when it fires" {
        val timeouts = TrackedTimeouts()
        val ran = mutableListOf<String>()

        timeouts.schedule(delayMs = 5) { ran.add("first") }
        timeouts.schedule(delayMs = 10) { ran.add("second") }

        timeouts.pendingCount shouldBe 2
        ran shouldBe emptyList()

        delay(200)

        ran shouldBe listOf("first", "second")
        timeouts.pendingCount shouldBe 0
    }

    "cancelAll stops every pending action and forgets its timers" {
        val timeouts = TrackedTimeouts()
        val ran = mutableListOf<String>()

        timeouts.schedule(delayMs = 20) { ran.add("cancelled") }
        timeouts.schedule(delayMs = 30) { ran.add("cancelled too") }
        timeouts.cancelAll()

        timeouts.pendingCount shouldBe 0

        delay(200)

        ran shouldBe emptyList()
    }

    "after cancelAll the same instance schedules again" {
        val timeouts = TrackedTimeouts()
        val ran = mutableListOf<String>()

        timeouts.schedule(delayMs = 20) { ran.add("cancelled") }
        timeouts.cancelAll()
        timeouts.schedule(delayMs = 5) { ran.add("later") }

        delay(200)

        ran shouldBe listOf("later")
        timeouts.pendingCount shouldBe 0
    }
})
