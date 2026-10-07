/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * Copies `this[startIndex until endIndex]` into [destination] from [destinationOffset] on, with a plain loop. The
 * parameters mean what they mean in `copyInto`; use this one on the audio path.
 *
 * Why not `copyInto`: on Kotlin/JS it copies a typed array through `destination.set(source.subarray(...))`, and the
 * `subarray` view is a new object on every call, so a per-block copy allocates on the audio thread
 * (`audio/ref/performance.md`). This loop allocates nothing on either platform.
 *
 * What `copyInto` does that this does not: no range check (on the JVM an index past either array throws
 * `ArrayIndexOutOfBoundsException`; on JS a read past the end gives NaN and a write past it is dropped), and no
 * overlap handling. It copies forwards, so a range moved to a LATER offset inside the same array overwrites
 * its own source; copying a range onto itself (same array, same offset) is fine. An empty or reversed range
 * copies nothing.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun DoubleArray.copyRangeInto(destination: DoubleArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
    val shift = destinationOffset - startIndex

    for (i in startIndex until endIndex) {
        destination[i + shift] = this[i]
    }
}
