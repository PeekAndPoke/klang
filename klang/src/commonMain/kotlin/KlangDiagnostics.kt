/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.ultra.streams.ops.history
import io.peekandpoke.ultra.streams.ops.map
import kotlin.concurrent.Volatile

/**
 * The player's diagnostics (KLANG-00385; the maintainer, 2026-10-28: "The motor stats need to have some kind a "hook"
 * where additional stats can be added ... By doing so also other "things" could add diagnostics"):
 *
 * - [stream]: the latest diagnostics from the backend, null before the first; what the gauges and the warehouse read.
 * - [metric]: a metric any owner registers (the player its Motor readings, a playback its pattern work), posts plain
 *   numbers to, and closes when it ends: metrics belong to their owner, so the registry never grows with use.
 * - [history]: every metric's last [capacity] readings, one per tick of [ticks], each the metric's latest value when it
 *   posted within [FRESH_MS], else a gap (null). A closed metric's line stays until it scrolls out. A line is a metric's
 *   [Metric.id]: a metric registered again under an id continues its line, after a gap (the maintainer, 2026-10-29:
 *   "when i play and stop the same song the sprudel entries pile up", "would need an identifier that "survives" stop and
 *   play").
 *
 * A clock reads the registry rather than combining the metrics' streams: those come and go with their owners. The tick
 * reads in a map; an operator that samples a stream on a clock is ULTRA-00267.
 */
class KlangDiagnostics(
    /** The latest SYSTEM diagnostics from the backend; null before the first. */
    val stream: Stream<KlangCommLink.Feedback.Diagnostics?>,
    /** The clock: a reading per value. */
    ticks: Stream<*>,
    /** The readings kept. */
    val capacity: Int,
    /** Now, in milliseconds, for when a value was posted. */
    private val nowMs: () -> Double,
) {
    companion object {
        /** A reading four times a second. */
        const val TICK_MS = 250

        /** Two minutes of readings. */
        const val TWO_MINUTES = 120 * 1000 / TICK_MS

        /** A value older than this is a gap: its owner went quiet (a stalled Motor, a stopped playback). */
        const val FRESH_MS = 1000.0
    }

    /**
     * A metric: what it measures ([name], in [unit], from [min] to [max]) and where it is drawn ([category]: one graph
     * per category, so a category's metrics share a unit). [post] writes a number and its time, nothing else, so the
     * hottest owner can post as often as it likes; [close] takes it off the registry.
     */
    class Metric internal constructor(
        val id: String,
        val name: String,
        val category: String,
        val unit: String,
        val min: Double,
        val max: Double,
        private val owner: KlangDiagnostics,
    ) {
        // Written by the owner's thread, read by the clock's: a reading may be a post late, never torn
        @Volatile
        internal var value: Double = Double.NaN

        @Volatile
        internal var postedAtMs: Double = Double.NEGATIVE_INFINITY

        fun post(value: Double) {
            this.value = value
            postedAtMs = owner.nowMs()
        }

        fun close() {
            owner.remove(this)
        }
    }

    /** A metric's readings, oldest first, null for a gap; [metric] is the latest registered under its id. */
    class Series(val metric: Metric, val values: List<Double?>) {
        /** The newest reading; null while the metric gives none. */
        val latest: Double? get() = values.lastOrNull()
    }

    /** The last [capacity] readings of every metric id that gave one, in the order they first did. */
    class History(val series: List<Series>, val capacity: Int) {
        /** The series by their metric's category, the categories in the order they first came. */
        val byCategory: Map<String, List<Series>> get() = series.groupBy { it.metric.category }
    }

    private val lock = KlangLock()

    private val registry = mutableListOf<Metric>()

    /** Registers a metric; its owner [Metric.close]s it when the owner ends. */
    fun metric(id: String, name: String, category: String, unit: String, min: Double, max: Double): Metric {
        val metric = Metric(id = id, name = name, category = category, unit = unit, min = min, max = max, owner = this)

        lock.withLock { registry.add(metric) }

        return metric
    }

    /** The metrics registered now. */
    val metrics: List<Metric> get() = lock.withLock { registry.toList() }

    private fun remove(metric: Metric) {
        lock.withLock { registry.remove(metric) }
    }

    /** A metric's reading at one tick. */
    private class Reading(val metric: Metric, val value: Double)

    /** One tick's readings, by metric id: each registered metric's latest value when it is fresh. */
    private fun read(): Map<String, Reading> {
        val now = nowMs()

        return metrics.mapNotNull { metric ->
            // NaN-guard: a metric that never posted has a NaN value and no time; it gives no reading
            metric.value.takeIf { !it.isNaN() && now - metric.postedAtMs <= FRESH_MS }?.let { metric.id to Reading(metric, it) }
        }.toMap()
    }

    /** The last [capacity] readings, one per tick. */
    private val readings: Stream<List<Map<String, Reading>>> = ticks.map { read() }.history(capacity = capacity)

    /** The history, anew per tick: a line per metric id, named by the metric that read last under it. */
    val history: Stream<History> = readings.map { all ->
        val latest = LinkedHashMap<String, Metric>()

        for (reading in all) {
            for ((id, r) in reading) {
                // Kept in the order the ids first came; the newest metric under an id names its line
                latest[id] = r.metric
            }
        }

        History(
            series = latest.map { (id, metric) -> Series(metric = metric, values = all.map { it[id]?.value }) },
            capacity = capacity,
        )
    }

    private var running: List<Unsubscribe> = emptyList()

    /** Starts keeping the history: until [stop], whether anyone looks or not. A second call does nothing. */
    fun start() {
        if (running.isNotEmpty()) {
            return
        }

        running = listOf(stream.subscribeToStream { }, history.subscribeToStream { })
    }

    fun stop() {
        running.forEach { it() }
        running = emptyList()
    }
}
