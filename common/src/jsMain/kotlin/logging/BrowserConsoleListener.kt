/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

/**
 * Writes each record to the browser's console at its level, `[area/source] message`, the error after it, so the developer
 * tools show its stack. This file is on the source maps' ignore list with the rest of `logging/` (see [KlangLog]).
 */
object BrowserConsoleListener : LogListener {

    /**
     * The least level written (KLANG-00407, D4): everything in a development build, `info` and up in a release; the
     * app's start sets it. The Console keeps every level either way.
     */
    var least: LogLevel = LogLevel.Debug

    /** The console's method for [level]. */
    fun methodOf(level: LogLevel): String = when (level) {
        LogLevel.Debug -> "debug"
        LogLevel.Info -> "info"
        LogLevel.Warn -> "warn"
        LogLevel.Error -> "error"
    }

    override fun onRecord(record: LogRecord) {
        if (record.level < least) {
            return
        }

        val text = "[${record.area.label}/${record.source}] ${record.message}"
        val args: Array<Any?> = when (val error = record.error) {
            null -> arrayOf(text)
            else -> arrayOf(text, error)
        }

        console.asDynamic()[methodOf(record.level)].apply(console, args)
    }
}
