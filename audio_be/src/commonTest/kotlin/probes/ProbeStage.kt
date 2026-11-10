/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

/**
 * One stage under the probe: renders a whole [ProbeSignal] through a FRESH instance of the stage (built inside every
 * call, so no state crosses from one signal to the next) in [PROBE_BLOCK_FRAMES] blocks, and hands back what came out.
 * Test code, never the engine's hot path, so a `fun interface` is fine here.
 */
fun interface ProbeStage {
    fun render(signal: ProbeSignal): ProbeOutput
}

/** What a [ProbeStage] put out: [left], and [right] for a stereo stage (null for a mono one, which reads the left input). */
class ProbeOutput(
    val left: DoubleArray,
    val right: DoubleArray?,
)
