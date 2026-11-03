/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** The log (KLANG-00407): a logger's records reach every listener, at their level, with their source. */
class KlangLogSpec : StringSpec({

    /** [block] with [listeners] on the log, taken off again after, so no spec leaves one behind. */
    fun withListeners(vararg listeners: LogListener, block: () -> Unit) {
        listeners.forEach { KlangLog.addListener(it) }

        try {
            block()
        } finally {
            listeners.forEach { KlangLog.removeListener(it) }
        }
    }

    "a logger's record reaches every listener, with its level, source, message and error" {
        val first = mutableListOf<LogRecord>()
        val second = mutableListOf<LogRecord>()
        val boom = IllegalStateException("boom")

        withListeners(LogListener { first.add(it) }, LogListener { second.add(it) }) {
            val log = Logger(area = LogArea.Midi, source = "MidiRig")

            log.debug("d")
            log.info("i")
            log.warn("w", boom)
            log.error("e")
        }

        first.map { Triple(it.level, it.source, it.message) } shouldBe listOf(
            Triple(LogLevel.Debug, "MidiRig", "d"),
            Triple(LogLevel.Info, "MidiRig", "i"),
            Triple(LogLevel.Warn, "MidiRig", "w"),
            Triple(LogLevel.Error, "MidiRig", "e"),
        )
        first.map { it.error } shouldBe listOf(null, null, boom, null)
        withClue("each with the logger's area") { first.map { it.area }.distinct() shouldBe listOf(LogArea.Midi) }
        withClue("the second listener the same records") { second shouldBe first }
        withClue("each with its time") { first.all { it.timeMs > 0.0 } shouldBe true }
    }

    "a listener added twice hears each record once; one taken off hears no more" {
        val heard = mutableListOf<LogRecord>()
        val listener = LogListener { heard.add(it) }

        withListeners(listener, listener) {
            KlangLog.listeners.count { it == listener } shouldBe 1
            Logger(area = LogArea.App, source = "A").info("once")
        }

        withClue("heard once") { heard.map { it.message } shouldBe listOf("once") }

        Logger(area = LogArea.App, source = "A").info("after")

        withClue("off the log: not heard") { heard.map { it.message } shouldBe listOf("once") }
        KlangLog.listeners.filter { it == listener }.shouldBeEmpty()
    }

    "a listener that throws keeps the record from no other" {
        val heard = mutableListOf<String>()

        withListeners(LogListener { throw IllegalStateException("broken") }, LogListener { heard.add(it.message) }) {
            Logger(area = LogArea.App, source = "A").warn("still here")
        }

        heard shouldBe listOf("still here")
    }

    "a record logged by a listener waits for the one it hears: every listener hears them in the order logged" {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()

        withListeners(
            LogListener { record ->
                first.add(record.message)

                if (record.message == "one") {
                    Logger(area = LogArea.App, source = "Echo").info("two")
                }
            },
            LogListener { second.add(it.message) },
        ) {
            Logger(area = LogArea.App, source = "A").info("one")
        }

        first shouldBe listOf("one", "two")
        withClue("the second listener: one before two, though two was logged while one was handed on") {
            second shouldBe listOf("one", "two")
        }
    }

    "a listener that logs on every record keeps the log busy only so long" {
        var heard = 0

        val echo = LogListener {
            heard++
            Logger(area = LogArea.App, source = "Echo").info("again")
        }

        withListeners(echo) {
            Logger(area = LogArea.App, source = "A").info("start")
        }

        heard shouldBe 1 + KlangLog.MAX_WAITING

        withListeners(echo) {
            Logger(area = LogArea.App, source = "A").info("start again")
        }

        withClue("a second time as many: the bound is each record's, not used up") { heard shouldBe 2 * (1 + KlangLog.MAX_WAITING) }

        withClue("the next record handed on as any other") {
            withListeners(LogListener { heard++ }) {
                Logger(area = LogArea.App, source = "A").info("after")
            }

            heard shouldBe 2 * (1 + KlangLog.MAX_WAITING) + 1
        }
    }

    "a cause that is no error is told after the message" {
        val heard = mutableListOf<LogRecord>()

        withListeners(LogListener { heard.add(it) }) {
            Logger(area = LogArea.App, source = "Copy").warn("could not copy", "NotAllowedError")
        }

        heard.single().message shouldBe "could not copy: NotAllowedError"
        heard.single().error shouldBe null
    }

    "with no listener a record goes nowhere and nothing fails" {
        KlangLog.listeners.shouldBeEmpty()

        Logger(area = LogArea.App, source = "A").error("into the void")
    }
})
