/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.ultra.streams.ops.history
import io.peekandpoke.ultra.streams.ops.map

/**
 * The Motor's diagnostics, kept by the player (the maintainer, 2026-10-28: "The MotorHistory should live close to the
 * KlangPlayer and not in the ui. As a user i want to simply subscribe to it and get the aggregated history"; then
 * "Maybe it should be called MotorDiagnostics and expose a stream of diagnostic events and the aggregates history"):
 *
 * - [stream]: the latest diagnostics from the backend, null before the first; what the gauges read.
 * - [history]: the Motor heat, the active cylinders and the active voices, one reading per tick of [ticks], the last
 *   [capacity] kept. A tick with no fresh diagnostics since the one before is a gap (null): the Motor stalled or stopped.
 *
 * Diagnostics come about every 20 ms, so they are sampled on [ticks] before [history] keeps them: two minutes of them
 * would be 6000 kept and copied 50 times a second, and time would run at the producer's pace. The ticks read the latest
 * diagnostics in a map; an operator that samples a stream on a clock is ULTRA-00267.
 */
class KlangMotorDiagnostics(
    /** The latest SYSTEM diagnostics; null before the first. */
    val stream: Stream<KlangCommLink.Feedback.Diagnostics?>,
    /** The clock: a reading per value. */
    ticks: Stream<*>,
    /** The readings kept. */
    val capacity: Int,
) {
    companion object {
        /** A reading four times a second. */
        const val TICK_MS = 250

        /** Two minutes of readings. */
        const val TWO_MINUTES = 120 * 1000 / TICK_MS
    }

    /** One reading of the Motor. */
    data class Reading(
        /** What the render does not have to spare, 0 (idle) to 1 (all of it), above 1 when it overruns. */
        val heat: Double,
        val cylinders: Int,
        val voices: Int,
    )

    /** The history: [readings] oldest first, at most [capacity], null where the Motor gave none. */
    data class History(val readings: List<Reading?>, val capacity: Int) {
        val heat: List<Double?> get() = readings.map { it?.heat }
        val cylinders: List<Int?> get() = readings.map { it?.cylinders }
        val voices: List<Int?> get() = readings.map { it?.voices }

        /** The newest reading; null while the Motor gives none. */
        val latest: Reading? get() = readings.lastOrNull()
    }

    /** The diagnostics a tick last read: the same again means none came since. */
    private var lastRead: KlangCommLink.Feedback.Diagnostics? = null

    /** The history, anew per tick. */
    val history: Stream<History> = ticks
        .map { read() }
        .history(capacity = capacity)
        .map { History(readings = it, capacity = capacity) }

    private var running: List<Unsubscribe> = emptyList()

    /** Starts keeping the history: until [stop], whether anyone looks or not. A second call does nothing. */
    fun start() {
        if (running.isNotEmpty()) {
            return
        }

        // The stream too, so a tick reads the latest
        running = listOf(stream.subscribeToStream { }, history.subscribeToStream { })
    }

    fun stop() {
        running.forEach { it() }
        running = emptyList()
    }

    private fun read(): Reading? {
        val d = stream()
        val fresh = d != null && d !== lastRead

        lastRead = d

        return d?.takeIf { fresh }?.let {
            Reading(
                heat = 1.0 - it.renderHeadroom,
                cylinders = it.cylinders.count { c -> c.active },
                voices = it.activeVoiceCount,
            )
        }
    }
}
