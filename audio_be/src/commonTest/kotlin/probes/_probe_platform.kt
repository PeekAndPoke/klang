/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

// The few things a probe asks of its platform (KLANG-00420): a clock, a byte counter, a number formatter. The rest of
// the cost probe is common code, so the JVM and the Kotlin/JS tables come from one catalogue and one method.

/** The wall clock in nanoseconds, as a Double at once (a Long, where the platform has one, only at this boundary). */
expect fun nanoNow(): Double

/**
 * The bytes this thread has allocated so far, as a Double at once; NaN where the platform cannot count them (Kotlin/JS:
 * V8 has no per-thread counter, see the JS entry's KDoc).
 */
expect fun allocatedBytesNow(): Double

/** [x] with exactly [digits] digits after the point, no grouping, a point as the separator (the JVM's `Locale.ROOT` `%.nf`). */
expect fun formatFixed(x: Double, digits: Int): String
