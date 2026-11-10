/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

// The Kotlin/JS half of `_probe_platform.kt` (KLANG-00420). The clock is `performance.now()`, a high-resolution
// monotonic clock in node and in a browser (milliseconds as a fraction, microsecond resolution or finer in node).

actual fun nanoNow(): Double {
    val millis: Double = js("performance.now()").unsafeCast<Double>()

    return millis * 1e6
}

/**
 * The byte counter of the Kotlin/JS cost table: NaN (V8 has no per-thread allocation counter) unless
 * [jsAllocationCounter] is set. The JS entry ([runStageCostJs]) sets it in its allocation mode.
 */
actual fun allocatedBytesNow(): Double = jsAllocationCounter?.invoke() ?: Double.NaN

/** The byte counter [allocatedBytesNow] reads on Kotlin/JS, null for none. */
var jsAllocationCounter: (() -> Double)? = null

actual fun formatFixed(x: Double, digits: Int): String = x.asDynamic().toFixed(digits).unsafeCast<String>()
