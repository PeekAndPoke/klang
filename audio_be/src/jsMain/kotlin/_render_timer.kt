/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.js.Date

/**
 * The clock the worklet times its blocks with, for the render headroom ([PlaybackEngineDispatcher]'s
 * `performanceTimeMs`): `performance.now()` where the scope has it, monotonic and finer than a millisecond, else
 * `Date.now()`. Chosen once. `Date.now()` alone read a 2.67 ms block as 0, 1, 2 or 3 ms, and a step of the wall clock
 * put an outlier of any size into the average (audit L9-16); not every AudioWorkletGlobalScope exposes `performance`,
 * which is why the fallback stays.
 */
fun renderTimerMs(): () -> Double {
    val hasPerformance = js("typeof performance !== 'undefined' && typeof performance.now === 'function'") as Boolean

    return if (hasPerformance) {
        { js("performance.now()") as Double }
    } else {
        { Date.now() }
    }
}
