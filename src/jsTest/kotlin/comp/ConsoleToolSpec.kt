/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.peekandpoke.klang.common.logging.LogLevel
import io.peekandpoke.klang.common.logging.LogRecord
import io.peekandpoke.klang.spine.ConsoleLog
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import org.w3c.dom.HTMLElement

/** The Console (KLANG-00407): what a record reads as, on its line and in Copy. */
class ConsoleToolSpec : StringSpec({

    fun record(message: String, error: Throwable? = null) =
        LogRecord(level = LogLevel.Warn, source = "MidiRig", message = message, error = error, timeMs = 0.0)

    "each level its lamp" {
        LogLevel.entries.map { ConsoleTool.lampOf(it) } shouldBe
                listOf(KlangLamp.State.Off, KlangLamp.State.On, KlangLamp.State.Warning, KlangLamp.State.Critical)
    }

    "a line for Copy: time, level, source, message and the error" {
        ConsoleTool.lineOf(record("Could not keep it", IllegalStateException("full"))) shouldMatch
                Regex("""\d\d:\d\d:\d\d\.\d\d\d WARN \[MidiRig\] Could not keep it: full""")
    }

    "the tab's title counts the unseen errors, more than 99 as 99+" {
        ConsoleTool.tab(unseenErrors = 0).title shouldBe "Console"
        ConsoleTool.tab(unseenErrors = 7).title shouldBe "Console (7)"
        ConsoleTool.tab(unseenErrors = 99).title shouldBe "Console (99)"
        ConsoleTool.tab(unseenErrors = 100).title shouldBe "Console (99+)"
    }

    "shown, the Console sees the errors there and those that come; gone, they count again; a level switched off hides its records" {
        fun error(message: String) = LogRecord(level = LogLevel.Error, source = "S", message = message, error = null, timeMs = 1.0)

        ConsoleLog.clear()

        try {
            ConsoleLog.listener.onRecord(error("before"))
            ConsoleLog.listener.onRecord(record("a warning"))
            ConsoleLog.state().unseenErrors shouldBe 1

            TestBed.preact(view = { ConsoleTool() }) { root ->
                val r = root.single()

                withClue("shown: the one there seen, the records drawn") {
                    eventually {
                        ConsoleLog.state().unseenErrors shouldBe 0
                        r.textContent!!.contains("before") shouldBe true
                    }
                }

                ConsoleLog.listener.onRecord(error("while shown"))

                withClue("one that comes while shown: seen as it comes") {
                    ConsoleLog.state().unseenErrors shouldBe 0
                    eventually { r.textContent!!.contains("while shown") shouldBe true }
                }

                val warn = r.querySelectorAll("button").let { all -> (0 until all.length).map { all.item(it) as HTMLElement } }
                    .first { it.textContent?.trim() == "Warn" }

                warn.click()

                withClue("Warn switched off: the warning hidden, the errors still there") {
                    eventually {
                        r.textContent!!.contains("a warning") shouldBe false
                        r.textContent!!.contains("before") shouldBe true
                    }
                }
            }

            ConsoleLog.listener.onRecord(error("after"))

            withClue("no Console shown any more: counted again") { ConsoleLog.state().unseenErrors shouldBe 1 }
        } finally {
            ConsoleLog.clear()
        }
    }

    "the error's text only where the message does not say it already" {
        withClue("said already: not again") {
            ConsoleTool.errorSuffixOf(record("Uncaught Error: full (app.js:1:2)", IllegalStateException("full"))) shouldBe ""
        }
        withClue("not said: after it") {
            ConsoleTool.errorSuffixOf(record("Could not keep it", IllegalStateException("full"))) shouldBe ": full"
        }
        withClue("no error: nothing") { ConsoleTool.errorSuffixOf(record("Fine")) shouldBe "" }
    }
})
