/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

/**
 * JVM implementation of console output — routes all levels to stdout.
 */
actual fun platformConsoleOutput(level: ConsoleLevel, args: List<String>) {
    val message = args.joinToString(" ")
    when (level) {
        ConsoleLevel.LOG -> println(message)
        ConsoleLevel.INFO -> println("[INFO] $message")
        ConsoleLevel.WARN -> println("[WARN] $message")
        ConsoleLevel.ERROR -> System.err.println("[ERROR] $message")
        ConsoleLevel.DEBUG -> println("[DEBUG] $message")
    }
}
