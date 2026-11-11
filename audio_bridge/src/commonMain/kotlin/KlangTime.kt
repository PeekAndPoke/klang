/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * High-precision time source for audio timing.
 * Seeded with the wall clock (epoch milliseconds) at creation, then advanced by a steady, monotonic source, so it
 * never jumps with the wall clock.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class KlangTime {
    /**
     * Returns current time in milliseconds: the epoch milliseconds at creation plus the monotonic time elapsed since
     */
    fun internalMsNow(): Double

    companion object {
        /**
         * Creates a KlangTime instance appropriate for the current context
         */
        fun create(): KlangTime
    }
}
