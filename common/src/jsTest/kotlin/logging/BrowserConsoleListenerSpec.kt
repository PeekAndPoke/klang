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

    "a record reaches the console at its level as `[area/source] message`, its error after it" {
        val heard = mutableListOf<Pair<String, List<Any?>>>()
        val console = console.asDynamic()
        val kept = LogLevel.entries.associate { level -> level to console[BrowserConsoleListener.methodOf(level)] }

        try {
            LogLevel.entries.forEach { level ->
                val method = BrowserConsoleListener.methodOf(level)

                console[method] = { a: Any?, b: Any? -> heard.add(method to listOfNotNull(a, b)) }
            }

            val boom = IllegalStateException("boom")

            fun write(level: LogLevel, area: LogArea, source: String, message: String, error: Throwable? = null) =
                BrowserConsoleListener.onRecord(
                    LogRecord(level = level, area = area, source = source, message = message, error = error, timeMs = 1.0)
                )

            write(level = LogLevel.Warn, area = LogArea.Midi, source = "Rig", message = "careful", error = boom)
            write(level = LogLevel.Info, area = LogArea.App, source = "Start", message = "hello")

            withClue("the warning at warn, the error after the text") { heard[0] shouldBe ("warn" to listOf("[midi/Rig] careful", boom)) }
            withClue("the info at info, the text alone") { heard[1] shouldBe ("info" to listOf("[app/Start] hello")) }

            BrowserConsoleListener.least = LogLevel.Info
            write(level = LogLevel.Debug, area = LogArea.App, source = "Start", message = "quiet")
            write(level = LogLevel.Info, area = LogArea.App, source = "Start", message = "loud")

            withClue("below the least level: not written; at it: written") {
                heard.drop(2) shouldBe listOf("info" to listOf("[app/Start] loud"))
            }
        } finally {
            BrowserConsoleListener.least = LogLevel.Debug
            kept.forEach { (level, function) -> console[BrowserConsoleListener.methodOf(level)] = function }
        }
    }
})
