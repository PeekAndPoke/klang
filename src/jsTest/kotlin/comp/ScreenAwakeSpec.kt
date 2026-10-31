/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.utils.WakeLockController
import io.peekandpoke.kraft.utils.WakeLockSentinel
import io.peekandpoke.ultra.streams.StreamSource
import kotlin.js.Promise

/** The screen stays awake while something plays: the wake lock follows the playing stream. */
class ScreenAwakeSpec : StringSpec({

    "the lock is held while playing, and let go when it stops" {
        // A lock the browser never grants: the spec counts who holds it
        val ctrl = WakeLockController(
            requestLock = { Promise<WakeLockSentinel> { _, _ -> } },
            isPageVisible = { true },
        )
        val playing = StreamSource(false)

        TestBed.preact(
            appSetup = { wakeLockCtrl(ctrl) },
            view = { ScreenAwake(playing = playing) },
        ) {
            ctrl.holderCount shouldBe 0

            playing(true)
            eventually { ctrl.holderCount shouldBe 1 }

            playing(false)
            eventually { ctrl.holderCount shouldBe 0 }

            playing(true)
            eventually { ctrl.holderCount shouldBe 1 }
        }
    }
})
