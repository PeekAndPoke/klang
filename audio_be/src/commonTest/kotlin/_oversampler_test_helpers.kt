/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * TEST ONLY. One oversampled round trip as the production caller (`DistortionCore`, under both shaper nodes) writes it:
 * lease the work buffer from [scratch], [Oversampler.upsample], [shape] `work[0 until count]` in place,
 * [Oversampler.decimate] back into `buffer[offset, offset + length)`. Inline, so a spec's lambda is no object either.
 */
inline fun Oversampler.roundTrip(
    buffer: AudioBuffer,
    offset: Int,
    length: Int,
    scratch: ScratchBuffers,
    shape: (work: AudioBuffer, count: Int) -> Unit,
) {
    scratch.oversample(factor).use { work ->
        val count = upsample(source = buffer, offset = offset, length = length, work = work)

        shape(work, count)

        decimate(work = work, target = buffer, offset = offset, length = length)
    }
}
