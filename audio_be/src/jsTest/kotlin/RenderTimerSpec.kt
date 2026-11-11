/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [renderTimerMs] (audit L9-16): where the scope has `performance`, the render headroom is timed on it, not on
 * `Date.now()`, whose whole milliseconds read a 2.67 ms block as 0 to 3 ms. A browser window has it; whether a worklet
 * scope does is the browser's, and the fallback covers it.
 */
class RenderTimerSpec : StringSpec({

    "where the scope has performance, the timer reads it: monotonic, finer than a millisecond, not the wall clock" {
        val timer = renderTimerMs()
        val first = timer()

        // performance.now() counts from the page's start, Date.now() from 1970: one reading tells them apart
        (first < 1.0e9) shouldBe true

        // Finer than a millisecond: some reading in a short busy loop has a fraction, which Date.now() never has
        var fractional = false
        var last = first
        var monotonic = true
        var i = 0

        while (i < 200_000 && !fractional) {
            val now = timer()

            monotonic = monotonic && now >= last
            fractional = now != kotlin.math.floor(now)
            last = now
            i++
        }

        monotonic shouldBe true
        fractional shouldBe true
    }
})
