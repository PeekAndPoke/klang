/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * The same knob value, for a stage's cached-config compare (`configure` returning early on an unchanged config): equal
 * with `-0.0` and `0.0` told apart, or both NaN. Exact: a config that passes it would write the same numbers, so
 * skipping the write changes nothing. A NaN is a value like any other here, because a raw `NaN != NaN` would defeat the
 * compare on every block (the cached-config rule of `/review-loop`).
 *
 * Inline, so no double crosses a call on V8 (`audio/ref/performance.md`). The phaser's and the compressor's
 * `configure` use it (katalyst knob expressions step 2 moved it here from the phaser).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun sameKnob(stored: Double, arriving: Double): Boolean {
    // NaN-guard on a self-comparison: two NaN knobs are the same value.
    if (stored != stored) {
        return arriving != arriving
    }

    return stored == arriving && (stored != 0.0 || 1.0 / stored == 1.0 / arriving)
}
