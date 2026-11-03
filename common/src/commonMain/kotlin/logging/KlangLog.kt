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
 * The part of the app a message comes from, a fixed set the Console filters by (the maintainer, 2026-11-03: "categories
 * of message ... like ui, backend etc"); the [Logger]'s source is the finer tag inside it. The engine is the audio
 * engine (the "backend" of the Motor), apart from a server side to come.
 */
enum class LogArea(val label: String) {
    /** Starting up, the shell around the pages, the browser's uncaught errors. */
    App("app"),

    /** The pages and their components. */
    Ui("ui"),

    /** The code editor. */
    Editor("editor"),

    /** The audio engine and its player: log a change of state or a summary, never a stream (see KLANG-00407, D7). */
    Engine("engine"),

    /** The MIDI devices and the rig. */
    Midi("midi"),

    /** What the browser keeps. */
    Storage("storage"),
}

/**
 * One message to the log: its [level], the [area] and the [source] that logged it, its [message], an [error] when there
 * is one, and its [timeMs] (epoch milliseconds).
 */
data class LogRecord(
    val level: LogLevel,
    val area: LogArea,
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
 * A logger for one [source] in its [area], the tags its records carry:
 * `private val log = Logger(area = LogArea.Midi, source = "MidiRig")`, then `log.warn("Could not keep the play code", e)`.
 *
 * Never log from a component's render: the Console shows the records, and an error logged in a render redraws the
 * workbench that renders it again.
 */
class Logger(val area: LogArea, val source: String) {

    fun debug(message: String, cause: Any? = null) = log(level = LogLevel.Debug, message = message, cause = cause)

    fun info(message: String, cause: Any? = null) = log(level = LogLevel.Info, message = message, cause = cause)

    fun warn(message: String, cause: Any? = null) = log(level = LogLevel.Warn, message = message, cause = cause)

    fun error(message: String, cause: Any? = null) = log(level = LogLevel.Error, message = message, cause = cause)

    /**
     * A record at [level] to the [KlangLog]. A [cause] that is an error goes with it as its [LogRecord.error]; any other
     * (what a promise was rejected with, say) is told after the message.
     */
    fun log(level: LogLevel, message: String, cause: Any? = null) {
        val error = cause as? Throwable
        val text = if (cause != null && error == null) "$message: $cause" else message

        KlangLog.publish(
            LogRecord(level = level, area = area, source = source, message = text, error = error, timeMs = KlangLog.nowMs())
        )
    }
}
