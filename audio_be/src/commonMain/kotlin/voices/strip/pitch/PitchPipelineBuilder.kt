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
 * Pipeline: FM Synthesis (frequency modulation), the one stage left.
 *
 * Sprudel's pitch envelope (`penv`), vibrato (`vib`) and `accelerate` left this pipeline in pitch pipeline steps 1 to
 * 3: they are `classic()`'s pitch stages now (`docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`).
 *
 * Only active stages are included (e.g. FM is skipped if depth == 0).
 * Returns empty list if no pitch modulation is active.
 */
fun buildPitchPipeline(
    fm: Voice.Fm?,
    freqHz: Double,
    sampleRate: Int,
): List<BlockRenderer> = buildList {
    if (fm != null && fm.depth != 0.0) {
        add(FmRenderer(fm, freqHz, sampleRate))
    }
}
