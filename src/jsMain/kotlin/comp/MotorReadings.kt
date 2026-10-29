/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import org.khronos.webgl.get
import kotlin.math.abs
import kotlin.math.max

/**
 * The Motor's readings as the visuals show them (the plate's board, Klangamp), smoothed as the start page's needles
 * are: a fresh stream per call, so each visual subscribes to its own and lets go of it when it unmounts.
 */
object MotorReadings {

    /** How much of the scope's glow is kept each 10 ms: it rises and falls softly. */
    private const val GLOW_SMOOTHING = 0.85

    /** The current player's latest diagnostics; null while there is none. */
    private val online get() = Player.diagnostics.stream

    /** The cylinders at work, 0 to 32; null while the Motor gives none. */
    fun cylinders(): Stream<Double?> = online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 32 * PlayerMiniStats.NEEDLE_NEAR)

    /** The Motor's heat, 0 (idle) to 1 (no headroom left); null while the Motor gives none. */
    fun heat(): Stream<Double?> = online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)

    /** The voices sounding; null while the Motor gives none. */
    fun voices(): Stream<Double?> = online.map { d -> d?.activeVoiceCount?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 100 * PlayerMiniStats.NEEDLE_NEAR)

    /** The scope's signal now, 0 (silence) to 1 (full scale): its loudest sample in the newest frame, smoothed. */
    fun signal(): Stream<Double?> = Player.player.map { it?.getAnalyzer() }
        .switchMapNotNull { it.waveform }
        .map<AnalyzerBufferHistory?, Double?> { history ->
            if (history == null || history.size == 0) {
                0.0
            } else {
                val frame = history[0]
                var peak = 0.0

                for (i in 0 until history.bufferSize) {
                    peak = max(peak, abs(frame[i].toDouble()))
                }

                peak.coerceIn(0.0, 1.0)
            }
        }
        .smooth(sharePer10Ms = GLOW_SMOOTHING, near = 0.001)
}
