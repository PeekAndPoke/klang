/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * Removes every element [keep] rejects, keeping the order of the rest, in one pass and without
 * allocating: survivors move forward, the tail is dropped by last-index removals (a JS pop, never a
 * splice). Inline, so the lambda is no object on the audio thread.
 *
 * It must stay `inline`, and [keep] must never become `noinline`: otherwise every call allocates a closure on
 * the audio thread (the render loop's lambda captures the block's context), the same trap the engine audit found
 * in `ShapeIgnitor` through the non-inline `Oversampler.process` (B4.1, fixed in tidy-up step 2 by splitting it
 * into `upsample` and `decimate` halves).
 *
 * [keep] is called exactly once per element, in order, so a caller may do the element's work inside it (the
 * scheduler's render loop renders each voice there). Not `removeLast()` for the tail: the JDK 21
 * `List.removeLast` member clashes with the Kotlin extension.
 */
inline fun <T> MutableList<T>.retainInOrder(keep: (T) -> Boolean) {
    var write = 0

    for (read in 0 until size) {
        val element = this[read]

        if (keep(element)) {
            if (write != read) {
                this[write] = element
            }

            write++
        }
    }

    while (size > write) {
        removeAt(lastIndex)
    }
}
