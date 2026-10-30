/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth

/**
 * The Motor's readings as the visuals show them (the plate's board, Klangamp), smoothed as the start page's needles
 * are: a fresh stream per call, so each visual subscribes to its own and lets go of it when it unmounts.
 */
object MotorReadings {

    /** The current player's latest diagnostics; null while there is none; not followed while the window has no attention. */
    private val online get() = Player.diagnostics.stream.cutoffWhen(UiWork.resting)

    /** The cylinders at work, 0 to 32; null while the Motor gives none. */
    fun cylinders(): Stream<Double?> = online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 32 * PlayerMiniStats.NEEDLE_NEAR)

    /** The Motor's heat, 0 (idle) to 1 (no headroom left); null while the Motor gives none. */
    fun heat(): Stream<Double?> = online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)

    /** The voices sounding; null while the Motor gives none. */
    fun voices(): Stream<Double?> = online.map { d -> d?.activeVoiceCount?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 100 * PlayerMiniStats.NEEDLE_NEAR)
}
