/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * High-precision time source for audio timing.
 * Not epoch-based - just a steady, monotonic clock for relative timing.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class KlangTime {
    /**
     * Returns current time in milliseconds (relative to start, not epoch)
     */
    fun internalMsNow(): Double

    companion object {
        /**
         * Creates a KlangTime instance appropriate for the current context
         */
        fun create(): KlangTime
    }
}
