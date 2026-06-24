/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

/**
 * Console output levels for routing.
 */
enum class ConsoleLevel { LOG, WARN, ERROR, INFO, DEBUG }

/**
 * Platform-specific default console output.
 *
 * JS: routes to browser `console.log`, `console.warn`, etc.
 * JVM: routes to `println` / `System.err`.
 */
expect fun platformConsoleOutput(level: ConsoleLevel, args: List<String>)

/**
 * Console object for KlangScript — provides logging functions.
 *
 * Exposed as `console` in KlangScript, matching the JavaScript console API.
 * Registered manually in [KlangStdLib] because the output handler is injected at creation time.
 */
internal object KlangScriptConsole {
    override fun toString(): String = "[Console object]"
}
