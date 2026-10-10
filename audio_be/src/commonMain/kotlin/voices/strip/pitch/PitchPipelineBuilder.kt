/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * The voice's pitch pipeline, the BlockRenderers in front of the ignite stage: EMPTY since pitch pipeline step 4.
 *
 * Sprudel's pitch envelope (`penv`), vibrato (`vib`), `accelerate` and `fm` left this pipeline in pitch pipeline steps 1
 * to 4: they are `classic()`'s pitch stages now (`docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`). The shell
 * stays until step 5 removes it with `BlockContext.freqModBuffer` and the bridging in `IgniteRenderer`.
 */
fun buildPitchPipeline(): List<BlockRenderer> = emptyList()
