/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** The browser console listener (KLANG-00407): each level to the console's method of the same weight, the error after. */
class BrowserConsoleListenerSpec : StringSpec({

    "each level its console method" {
        LogLevel.entries.map { BrowserConsoleListener.methodOf(it) } shouldBe listOf("debug", "info", "warn", "error")
    }

    "a record reaches the console at its level as `[source] message`, its error after it" {
        val heard = mutableListOf<Pair<String, List<Any?>>>()
        val console = console.asDynamic()
        val kept = LogLevel.entries.associate { level -> level to console[BrowserConsoleListener.methodOf(level)] }

        try {
            LogLevel.entries.forEach { level ->
                val method = BrowserConsoleListener.methodOf(level)

                console[method] = { a: Any?, b: Any? -> heard.add(method to listOfNotNull(a, b)) }
            }

            val boom = IllegalStateException("boom")

            fun record(level: LogLevel, source: String, message: String, error: Throwable?) =
                LogRecord(level = level, source = source, message = message, error = error, timeMs = 1.0)

            BrowserConsoleListener.onRecord(record(level = LogLevel.Warn, source = "Rig", message = "careful", error = boom))
            BrowserConsoleListener.onRecord(record(level = LogLevel.Info, source = "App", message = "hello", error = null))

            withClue("the warning at warn, the error after the text") { heard[0] shouldBe ("warn" to listOf("[Rig] careful", boom)) }
            withClue("the info at info, the text alone") { heard[1] shouldBe ("info" to listOf("[App] hello")) }
        } finally {
            kept.forEach { (level, function) -> console[BrowserConsoleListener.methodOf(level)] = function }
        }
    }
})
