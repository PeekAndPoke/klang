/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.logging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** The log's buffer (KLANG-00407, D2): never more than its capacity; full, the oldest debug record goes first. */
class LogBufferSpec : StringSpec({

    fun record(level: LogLevel, message: String) =
        LogRecord(level = level, source = "S", message = message, error = null, timeMs = 1.0)

    fun LogBuffer.messages() = records.map { it.message }

    "records kept in order, the oldest first, up to the capacity" {
        val buffer = listOf("a", "b", "c").fold(LogBuffer(capacity = 3)) { b, m -> b.add(record(LogLevel.Info, m)) }

        buffer.messages() shouldBe listOf("a", "b", "c")
    }

    "full, the oldest debug record goes first, wherever it is" {
        val full = LogBuffer(capacity = 3)
            .add(record(LogLevel.Warn, "w"))
            .add(record(LogLevel.Debug, "d1"))
            .add(record(LogLevel.Debug, "d2"))

        val next = full.add(record(LogLevel.Info, "i"))

        withClue("the oldest debug, not the older warning") { next.messages() shouldBe listOf("w", "d2", "i") }
        withClue("the bound kept") { next.records.size shouldBe 3 }
    }

    "full with no debug record, the oldest goes" {
        val full = listOf("a", "b", "c").fold(LogBuffer(capacity = 3)) { b, m -> b.add(record(LogLevel.Error, m)) }

        full.add(record(LogLevel.Warn, "d")).messages() shouldBe listOf("b", "c", "d")
    }

    "never more than its capacity, however many come" {
        val levels = LogLevel.entries
        val buffer = (1..2500).fold(LogBuffer()) { b, i -> b.add(record(levels[i % levels.size], "m$i")) }

        buffer.records.size shouldBe LogBuffer.DEFAULT_CAPACITY
        withClue("the newest is the last one added") { buffer.records.last().message shouldBe "m2500" }
    }

    "cleared: empty, its capacity kept, its numbers going on" {
        val cleared = LogBuffer(capacity = 2).add(record(LogLevel.Info, "a")).cleared()

        cleared.records shouldBe emptyList()
        cleared.capacity shouldBe 2
        cleared.add(record(LogLevel.Info, "b")).entries.single().seq shouldBe 1
    }

    "each record kept its own number, in the order they came, the dropped ones' never given again" {
        val buffer = listOf("a", "b", "c", "d").fold(LogBuffer(capacity = 2)) { b, m -> b.add(record(LogLevel.Info, m)) }

        buffer.entries.map { it.seq to it.record.message } shouldBe listOf(2 to "c", 3 to "d")
    }

    "a capacity below one is refused" {
        shouldThrow<IllegalArgumentException> { LogBuffer(capacity = 0) }
    }
})
