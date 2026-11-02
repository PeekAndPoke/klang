/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

/**
 * Writes each record to the browser's console at its level, `[source] message`, the error after it, so the developer
 * tools show its stack. This file is on the source maps' ignore list with the rest of `logging/` (see [KlangLog]).
 */
object BrowserConsoleListener : LogListener {

    /** The console's method for [level]. */
    fun methodOf(level: LogLevel): String = when (level) {
        LogLevel.Debug -> "debug"
        LogLevel.Info -> "info"
        LogLevel.Warn -> "warn"
        LogLevel.Error -> "error"
    }

    override fun onRecord(record: LogRecord) {
        val text = "[${record.source}] ${record.message}"
        val args: Array<Any?> = when (val error = record.error) {
            null -> arrayOf(text)
            else -> arrayOf(text, error)
        }

        console.asDynamic()[methodOf(record.level)].apply(console, args)
    }
}
