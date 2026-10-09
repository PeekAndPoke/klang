/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Builds the pitch pipeline (BlockRenderer chain) from voice parameters.
 *
 * Pipeline order:
 * 1. Accelerate (pitch glide over voice lifetime)
 * 2. FM Synthesis (frequency modulation)
 *
 * Sprudel's pitch envelope (`penv`) and vibrato (`vib`) left this pipeline in pitch pipeline steps 1 and 2: they are
 * `classic()`'s pitch stages now (`docs/tasks/pitch-pipeline-into-the-tree.md`).
 *
 * Only active stages are included (e.g. FM is skipped if depth == 0).
 * Returns empty list if no pitch modulation is active.
 */
fun buildPitchPipeline(
    accelerate: Voice.Accelerate,
    fm: Voice.Fm?,
    freqHz: Double,
    sampleRate: Int,
    // Absolute backend frame — Double, see RenderClock.cursorFrame. Relative offsets stay Int.
    startFrame: Double,
    // Baked deliberately: the accelerate glide base must NOT move on a realtime note-off
    // (decided semantics, docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md).
    endFrame: Double,
): List<BlockRenderer> = buildList {
    if (accelerate.semitones != 0.0 && endFrame > startFrame) {
        add(AccelerateRenderer(accelerate, totalFrames = endFrame - startFrame))
    }

    if (fm != null && fm.depth != 0.0) {
        add(FmRenderer(fm, freqHz, sampleRate))
    }
}
