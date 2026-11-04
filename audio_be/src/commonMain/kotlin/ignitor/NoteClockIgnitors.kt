/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * The note's progress through its gate, per sample: the runtime of [io.peekandpoke.klang.audio_bridge.IgnitorDsl.Progress]
 * (KLANG-00245 (pitch pipeline into the tree) step 7f, decided 2026-10-13, Q13).
 *
 * `frame / gate`, where `frame` counts from the onset and `gate` is [IgniteContext.voiceDurationFrames]: 0 at the onset,
 * exactly 1.0 at the gate frame, and on past 1 at the same pace through the release. Each sample is computed from its
 * frame alone, so the output does not depend on how the voice is cut into blocks. A gate of zero frames or less
 * (sprudel's `legato(0)`) reads 1.0 from the first frame, the way `accelerate` holds its target from there (Q27). A
 * voice whose gate is not known ([IgniteContext.gateKnown] false, a held live note) reads 0 throughout, also after its
 * note-off: the note-off moves the voice's limits, never its scheduled gate. For the same reason a live note with a
 * gate length that a note-off cuts short keeps its scheduled gate as the base, the way `accelerate` does: stopped at a
 * quarter of its gate, it enters its release at 0.25, and reaches 1.0 only if its release outlasts the rest of its gate.
 *
 * Stateless, but not block-constant: it moves per sample.
 */
object ProgressIgnitor : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val start = ctx.offset
        val end = ctx.windowEnd

        if (!ctx.gateKnown) {
            buffer.fill(0.0, start, end)

            return
        }

        if (ctx.voiceDurationFrames <= 0) {
            buffer.fill(1.0, start, end)

            return
        }

        // A division per sample, not a multiply by the reciprocal: the gate frame reads exactly 1.0. The frame stays
        // inside Int: the gate is at most the held-voice horizon (`Int.MAX_VALUE / 2`) and a voice ends after its tail.
        val gate = ctx.voiceDurationFramesD
        val firstFrame = ctx.voiceElapsedFrames - start

        for (i in start until end) {
            buffer[i] = (firstFrame + i).toDouble() / gate
        }
    }
}

/**
 * Seconds since the note's onset, per sample: the runtime of [io.peekandpoke.klang.audio_bridge.IgnitorDsl.Elapsed]
 * (KLANG-00245 (pitch pipeline into the tree) step 7f, decided 2026-10-13, Q13). `frame / sampleRate`, each sample from
 * its frame alone, so the output does not depend on how the voice is cut into blocks. Inside an oversampling region
 * both count at the region's rate, so the seconds are the same, up to the region's frame limit: a held live note past
 * about 46 minutes at 16x saturates the region's elapsed count, and the frames after a block's first wrap
 * (`OversampleRegionIgnitor`'s KDoc), as every frame counter in the region does.
 *
 * Stateless, but not block-constant: it moves per sample.
 */
object ElapsedIgnitor : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val start = ctx.offset
        val end = ctx.windowEnd
        val rate = ctx.sampleRateD
        val firstFrame = ctx.voiceElapsedFrames - start

        for (i in start until end) {
            buffer[i] = (firstFrame + i).toDouble() / rate
        }
    }
}
