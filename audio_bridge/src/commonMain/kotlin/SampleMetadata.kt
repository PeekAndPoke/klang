/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


data class SampleMetadata(
    val anchor: Double,
    val loop: LoopRange?,
    val adsr: AdsrDef?,
) {
    companion object {
        val default = SampleMetadata(anchor = 0.0, loop = null, adsr = null)
    }

    data class LoopRange(
        /** Loop start in seconds (sample-rate independent) */
        val startSec: Double,
        /** Loop end in seconds (sample-rate independent) */
        val endSec: Double,
    )
}
