/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth

/**
 * The Motor's readings, smoothed as the needles show them, each derived once for every gauge that shows it (KLANG-00389,
 * step 4): the Motor's own gauges, the plate's board and Klang Amp share one smoothing of the heat, not one each. A
 * reading listens to the diagnostics only while some gauge listens to it, and not while the window has no attention; a
 * gauge that pauses on its own (the Motor under the visuals) cuts it off downstream and lets go of it.
 */
object MotorReadings {

    /** How slowly the needles follow new readings: the share of the way kept per 10 ms, the Motor's old pace. */
    const val NEEDLE_SMOOTHING = 0.933

    /** Where a needle counts as there: a thousandth of its gauge's sweep, times the sweep below. */
    const val NEEDLE_NEAR = 0.001

    /** The current player's latest diagnostics; null while there is none; not followed while the window has no attention. */
    private val online = Player.diagnostics.stream.cutoffWhen(UiWork.resting)

    /** The cylinders at work, 0 to 32; null while the Motor gives none. */
    val cylinders: Stream<Double?> by lazy {
        online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = 32 * NEEDLE_NEAR)
    }

    /** The Motor's heat, 0 (idle) to 1 (no headroom left): what the render does not have to spare; null while the Motor gives none. */
    val heat: Stream<Double?> by lazy {
        online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = NEEDLE_NEAR)
    }

    /** The voices sounding; null while the Motor gives none. */
    val voices: Stream<Double?> by lazy {
        online.map { d -> d?.activeVoiceCount?.toDouble() }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = 100 * NEEDLE_NEAR)
    }
}
