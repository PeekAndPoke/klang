/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

/**
 * The last records of the log, oldest first, never more than [capacity] (KLANG-00407, D2): full, a new record pushes out
 * the oldest `debug` one there is, and only where there is none the oldest of all, so a run of debug messages does not
 * push out the warning before it. Each record kept gets the next number ([Entry.seq]), so a list of them can tell a
 * row that stays from one that came. A value: [add] gives the next buffer.
 */
data class LogBuffer(
    val entries: List<Entry> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY,
    /** The number the next record kept gets. */
    val nextSeq: Int = 0,
) {
    companion object {
        /** The records the app's Console keeps (D2, the ticket's proposal). */
        const val DEFAULT_CAPACITY = 1000
    }

    /** A record kept, with its number: the order it came in, never given twice by one buffer and the ones it leads to. */
    data class Entry(val seq: Int, val record: LogRecord)

    init {
        require(capacity > 0) { "A log buffer holds at least one record, not $capacity" }
    }

    /** The records kept, oldest first. */
    val records: List<LogRecord> get() = entries.map { it.record }

    /** The buffer with [record] at the end; full, without the oldest debug record, else without the oldest. */
    fun add(record: LogRecord): LogBuffer {
        val entry = Entry(seq = nextSeq, record = record)

        if (entries.size < capacity) {
            return copy(entries = entries + entry, nextSeq = nextSeq + 1)
        }

        val oldestDebug = entries.indexOfFirst { it.record.level == LogLevel.Debug }
        val dropped = if (oldestDebug >= 0) oldestDebug else 0

        return copy(entries = entries.filterIndexed { i, _ -> i != dropped } + entry, nextSeq = nextSeq + 1)
    }

    /** The buffer emptied, its capacity kept; the numbers go on, so no row is taken for one gone. */
    fun cleared(): LogBuffer = copy(entries = emptyList())
}
