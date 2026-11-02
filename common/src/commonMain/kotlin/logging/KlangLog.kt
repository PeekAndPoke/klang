/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** How much a [LogRecord] matters, least first. */
enum class LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

/**
 * One message to the log: its [level], the [source] that logged it, its [message], an [error] when there is one, and its
 * [timeMs] (epoch milliseconds).
 */
data class LogRecord(
    val level: LogLevel,
    val source: String,
    val message: String,
    val error: Throwable?,
    val timeMs: Double,
)

/** Takes every [LogRecord] the [KlangLog] hands on: the browser's console, the app's Console tab. */
fun interface LogListener {
    fun onRecord(record: LogRecord)
}

/**
 * The log (KLANG-00407): every [Logger] hands its records here, and here they go to each of the [listeners]; the app's
 * start sets them. With none, a record goes nowhere.
 *
 * The files of this directory (`logging/`) stand on the source maps' ignore list (`webpack.config.d`), so the browser's
 * developer tools show a message at the line that logged it, not at the line in here that calls `console`. A function
 * that logs on someone's behalf therefore lives in this directory, and code that is not logging does not.
 *
 * A record logged while the log hands one on (a listener that logs) waits until that one has reached every listener,
 * so each listener hears the records in the order they were logged; at most [MAX_WAITING] wait, the rest are dropped,
 * so a listener that logs on every record cannot keep the log busy for ever.
 */
object KlangLog {

    /** The records that may wait while the log hands one on, counted from the one it began with. */
    const val MAX_WAITING = 100

    private val registered = mutableListOf<LogListener>()

    /** Records logged while one is handed on, in order; null while none is. */
    private var waiting: ArrayDeque<LogRecord>? = null

    /** How many have waited since the log began handing on: all of them, not only those waiting now. */
    private var waited = 0

    /** The listeners now, in the order they were added. */
    val listeners: List<LogListener> get() = registered.toList()

    /** [listener] added, once: a second add of the same one changes nothing. */
    fun addListener(listener: LogListener) {
        if (listener !in registered) {
            registered.add(listener)
        }
    }

    /** [listener] taken out. */
    fun removeListener(listener: LogListener) {
        registered.remove(listener)
    }

    /**
     * [record] to every listener; one that throws does not keep it from the others. Logged while another is handed on,
     * it waits for that one (see [KlangLog]).
     */
    fun publish(record: LogRecord) {
        waiting?.let { queue ->
            if (waited < MAX_WAITING) {
                waited++
                queue.addLast(record)
            }

            return
        }

        val queue = ArrayDeque<LogRecord>()

        waiting = queue
        waited = 0

        try {
            var next: LogRecord? = record

            while (next != null) {
                handOn(next)
                next = queue.removeFirstOrNull()
            }
        } finally {
            waiting = null
        }
    }

    private fun handOn(record: LogRecord) {
        registered.toList().forEach { listener ->
            try {
                listener.onRecord(record)
            } catch (_: Throwable) {
                // A listener that fails has nowhere to report it but the log itself: dropped, so it cannot loop
            }
        }
    }

    /** Now, in epoch milliseconds. */
    @OptIn(ExperimentalTime::class)
    fun nowMs(): Double = Clock.System.now().toEpochMilliseconds().toDouble()
}

/**
 * A logger for one [source], the tag its records carry: `private val log = Logger("MidiRig")`, then
 * `log.warn("Could not keep the play code", e)`.
 *
 * Never log from a component's render: the Console shows the records, and an error logged in a render redraws the
 * workbench that renders it again.
 */
class Logger(val source: String) {

    fun debug(message: String, error: Throwable? = null) = log(LogLevel.Debug, message, error)

    fun info(message: String, error: Throwable? = null) = log(LogLevel.Info, message, error)

    fun warn(message: String, error: Throwable? = null) = log(LogLevel.Warn, message, error)

    fun error(message: String, error: Throwable? = null) = log(LogLevel.Error, message, error)

    /** A record at [level] to the [KlangLog]. */
    fun log(level: LogLevel, message: String, error: Throwable? = null) {
        KlangLog.publish(
            LogRecord(level = level, source = source, message = message, error = error, timeMs = KlangLog.nowMs())
        )
    }
}
