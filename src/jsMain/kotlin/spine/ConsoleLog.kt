/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.common.logging.KlangLog
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.LogBuffer
import io.peekandpoke.klang.common.logging.LogLevel
import io.peekandpoke.klang.common.logging.LogListener
import io.peekandpoke.klang.common.logging.LogRecord
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.window

/**
 * The app's console, a part of the Spine (KLANG-00407): the log's last records ([LogBuffer], bounded), which the
 * Console tab of the bottom tools shows on every page, and the errors logged since the tab was last looked at, which its
 * key wears. Fed by the log as one of its listeners ([install]), and by the browser's uncaught errors and rejected
 * promises ([captureUncaught]).
 */
class ConsoleLog {

    companion object {
        /**
         * The unseen errors counted at most: the key shows "99+" beyond 99, and an error that repeats every frame stops
         * changing the count here, so the workbench that shows it is not redrawn every frame.
         */
        const val MAX_COUNTED = 100

        /** The source of the uncaught errors' records. */
        const val UNCAUGHT = "Uncaught"

        /** The source of the rejected promises' records. */
        const val UNHANDLED = "Unhandled promise"

        /**
         * The record of an uncaught error, an error of the app's from [UNCAUGHT]; null for what the browser reports there but does not
         * show in its own console: the ResizeObserver's "loop completed with undelivered notifications", harmless, which
         * comes with no error.
         */
        fun uncaughtRecord(message: String?, file: String?, line: Int?, column: Int?, error: Any?, timeMs: Double): LogRecord? {
            if (error == null && message?.startsWith("ResizeObserver loop") == true) {
                return null
            }

            return LogRecord(
                level = LogLevel.Error,
                area = LogArea.App,
                source = UNCAUGHT,
                message = uncaughtMessage(message = message, file = file, line = line, column = column),
                error = error as? Throwable,
                timeMs = timeMs,
            )
        }

        /** The record of a rejected promise nobody caught, an error from [UNHANDLED]: its reason's message, or the reason. */
        fun rejectionRecord(reason: Any?, timeMs: Double): LogRecord {
            val error = reason as? Throwable
            val message = when {
                error != null -> error.message ?: error.toString()
                reason != null -> (reason.asDynamic().message as? String) ?: reason.toString()
                else -> "Rejected with nothing"
            }

            return LogRecord(
                level = LogLevel.Error,
                area = LogArea.App,
                source = UNHANDLED,
                message = message,
                error = error,
                timeMs = timeMs,
            )
        }

        /** An uncaught error's message, where the browser has one, and where it was thrown: `Boom (app.js:12:7)`. */
        fun uncaughtMessage(message: String?, file: String?, line: Int?, column: Int?): String {
            val at = file?.takeIf { it.isNotEmpty() }
                ?.let { " (" + listOfNotNull(it, line, column).joinToString(":") + ")" }
                .orEmpty()

            return (message?.takeIf { it.isNotEmpty() } ?: "An error") + at
        }
    }

    /** The records kept, and the errors among the newer ones the Console has not shown yet (at most [MAX_COUNTED]). */
    data class State(
        val buffer: LogBuffer = LogBuffer(),
        val unseenErrors: Int = 0,
    ) {
        /** The state with [record] kept, counted where it is an error and [counted] (no Console shows it as it comes). */
        fun add(record: LogRecord, counted: Boolean = true): State = State(
            buffer = buffer.add(record),
            unseenErrors = when {
                counted && record.level == LogLevel.Error -> minOf(unseenErrors + 1, MAX_COUNTED)
                else -> unseenErrors
            },
        )
    }

    private val source = StreamSource(State())

    /** What the console holds now. */
    val state: Stream<State> = source

    /** How many Consoles show the records now. */
    private var shownBy = 0

    /** The listener that keeps each record of the log here; an error that comes while a Console is open is not counted. */
    val listener = LogListener { record -> source(source().add(record = record, counted = shownBy == 0)) }

    /**
     * A Console is open from now on: the errors there count as seen, and so do those that come while it is open, even
     * ones its filters hide (the user chose those), so the key's count does not go up and down with each.
     */
    fun showing() {
        shownBy++
        seen()
    }

    /** A Console that showed the records ([showing]) shows them no more. */
    fun notShowing() {
        shownBy = maxOf(0, shownBy - 1)
    }

    /** Fed from now on by the log, once however often called. */
    fun install() {
        KlangLog.addListener(listener)
    }

    private var capturing = false

    /**
     * The browser's uncaught errors and rejected promises kept here from now on (KLANG-00407, D3), once however often
     * called. Kept here only, not handed to the log's other listeners: the browser's console shows them itself.
     */
    fun captureUncaught() {
        if (capturing) {
            return
        }

        capturing = true

        window.addEventListener("error", { event ->
            val e = event.asDynamic()
            val record = uncaughtRecord(
                message = e.message as? String,
                file = e.filename as? String,
                line = e.lineno as? Int,
                column = e.colno as? Int,
                error = e.error,
                timeMs = KlangLog.nowMs(),
            )

            record?.let { listener.onRecord(it) }
        })

        window.addEventListener("unhandledrejection", { event ->
            listener.onRecord(rejectionRecord(reason = event.asDynamic().reason, timeMs = KlangLog.nowMs()))
        })
    }

    /** The Console shows the records: the errors among them are seen. */
    fun seen() {
        if (source().unseenErrors != 0) {
            source(source().copy(unseenErrors = 0))
        }
    }

    /** The records gone, the count with them. */
    fun clear() {
        source(State(buffer = source().buffer.cleared()))
    }
}
