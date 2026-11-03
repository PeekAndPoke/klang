/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.common.logging.KlangLog
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.LogLevel
import io.peekandpoke.klang.common.logging.LogRecord
import io.peekandpoke.klang.common.logging.Logger

/**
 * The app's console (KLANG-00407): fed by the log, it keeps the records and counts the errors the Console has not shown
 * yet; shown, they are seen; cleared, all of it goes.
 */
class ConsoleLogSpec : StringSpec({

    "fed by the log: the records kept, the errors counted until seen; cleared, all gone" {
        ConsoleLog.clear()
        ConsoleLog.install()

        try {
            val log = Logger(area = LogArea.App, source = "Spec")

            log.info("one")
            log.error("two")
            log.warn("three")
            log.error("four")

            withClue("the records, in order") {
                ConsoleLog.state().buffer.records.map { it.message } shouldBe listOf("one", "two", "three", "four")
            }
            withClue("the errors, unseen") { ConsoleLog.state().unseenErrors shouldBe 2 }

            ConsoleLog.seen()

            withClue("seen: none unseen, the records kept") {
                ConsoleLog.state().unseenErrors shouldBe 0
                ConsoleLog.state().buffer.records.size shouldBe 4
            }

            log.error("five")

            withClue("a new error after: one") { ConsoleLog.state().unseenErrors shouldBe 1 }

            withClue("installed twice: each record kept once") {
                ConsoleLog.install()
                log.info("six")
                ConsoleLog.state().buffer.records.count { it.message == "six" } shouldBe 1
            }

            ConsoleLog.clear()

            withClue("cleared: nothing kept, nothing unseen") {
                ConsoleLog.state().buffer.records shouldBe emptyList()
                ConsoleLog.state().unseenErrors shouldBe 0
            }
        } finally {
            KlangLog.removeListener(ConsoleLog.listener)
            ConsoleLog.clear()
        }
    }

    "the unseen errors counted at most so far, the records still kept" {
        val many = (1..ConsoleLog.MAX_COUNTED + 5).fold(ConsoleLog.State()) { state, i ->
            state.add(LogRecord(level = LogLevel.Error, area = LogArea.App, source = "S", message = "e$i", error = null, timeMs = 1.0))
        }

        many.unseenErrors shouldBe ConsoleLog.MAX_COUNTED
        many.buffer.records.size shouldBe ConsoleLog.MAX_COUNTED + 5
    }

    "an uncaught error: an error from Uncaught, where it was thrown; the ResizeObserver's notice, which comes without one, not" {
        val boom = IllegalStateException("Boom")
        val record = ConsoleLog.uncaughtRecord(message = "Boom", file = "app.js", line = 1, column = 2, error = boom, timeMs = 5.0)

        record?.level shouldBe LogLevel.Error
        record?.source shouldBe ConsoleLog.UNCAUGHT
        record?.message shouldBe "Boom (app.js:1:2)"
        record?.error shouldBe boom

        val notice = "ResizeObserver loop completed with undelivered notifications."

        withClue("the notice: not kept") {
            ConsoleLog.uncaughtRecord(message = notice, file = "", line = 0, column = 0, error = null, timeMs = 5.0) shouldBe null
        }
        withClue("the same words with an error object: an error of its own, kept") {
            ConsoleLog.uncaughtRecord(message = notice, file = "", line = 0, column = 0, error = boom, timeMs = 5.0) shouldNotBe null
        }
        withClue("another error without an error object: kept") {
            ConsoleLog.uncaughtRecord(message = "Script error.", file = "", line = 0, column = 0, error = null, timeMs = 5.0)
                ?.message shouldBe "Script error."
        }
    }

    "a rejected promise: an error from Unhandled promise, with its reason's message, or the reason itself" {
        val boom = IllegalStateException("Boom")

        ConsoleLog.rejectionRecord(reason = boom, timeMs = 5.0).let {
            it.level shouldBe LogLevel.Error
            it.source shouldBe ConsoleLog.UNHANDLED
            it.message shouldBe "Boom"
            it.error shouldBe boom
        }
        withClue("a plain value") { ConsoleLog.rejectionRecord(reason = "nope", timeMs = 5.0).message shouldBe "nope" }
        withClue("an object with a message") {
            ConsoleLog.rejectionRecord(reason = js("({ message: 'from js' })"), timeMs = 5.0).message shouldBe "from js"
        }
        withClue("nothing") { ConsoleLog.rejectionRecord(reason = null, timeMs = 5.0).message shouldBe "Rejected with nothing" }
    }

    "an uncaught error's message: what the browser says, and where; without either, still a line" {
        ConsoleLog.uncaughtMessage(message = "Boom", file = "app.js", line = 12, column = 7) shouldBe "Boom (app.js:12:7)"
        ConsoleLog.uncaughtMessage(message = "Boom", file = "", line = 0, column = 0) shouldBe "Boom"
        ConsoleLog.uncaughtMessage(message = null, file = "app.js", line = null, column = null) shouldBe "An error (app.js)"
        ConsoleLog.uncaughtMessage(message = "", file = null, line = null, column = null) shouldBe "An error"
    }
})
