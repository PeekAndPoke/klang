/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

/**
 * JS implementation of console output — routes to the browser console.
 */
actual fun platformConsoleOutput(level: ConsoleLevel, args: List<String>) {
    val message = args.joinToString(" ")
    when (level) {
        ConsoleLevel.LOG -> console.log(message)
        ConsoleLevel.INFO -> console.info(message)
        ConsoleLevel.WARN -> console.warn(message)
        ConsoleLevel.ERROR -> console.error(message)
        ConsoleLevel.DEBUG -> console.log("[DEBUG] $message") // console.debug may be filtered
    }
}
