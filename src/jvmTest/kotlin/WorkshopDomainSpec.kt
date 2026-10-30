/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * The workshop's rule, checked: what Klang builds for Kraft speaks no Klang in its code, KDoc or tests
 * (`workshop/CLAUDE.md`: only what any app could use belongs there), so its pieces move into Kraft as a copy. Borrowed
 * from spross's `WorkshopDomainSpec` (weekly meeting 2026-10-30), a check for the future. It lives here, not in the
 * workshop: the workshop runs only in the browser, which cannot read files, and this spec adds no dependency to it.
 *
 * Left alone: the package and import lines (the package moves with the copy) and ticket numbers (`KLANG-00325`, the
 * record of where a decision was made). A Klang type cannot reach the workshop at all: its build names Kraft alone.
 */
class WorkshopDomainSpec : StringSpec({

    "no source of the workshop speaks of Klang" {
        // Gradle runs the root project's JVM tests in the root folder
        val sources = File("workshop/src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // Klang's own words anywhere, also inside names (`orbitId`, `KatalystChain`)
        val words = Regex("klang|sprudel|strudel|tidal|ignitor|katalyst|cylinder|orbit|lexikon", RegexOption.IGNORE_CASE)

        // Words that hide inside others (`promotor`, `songbird` would be fine), as words and as parts of a name
        val shortWords = Regex("""(?<![A-Za-z])(motor|song)s?(?![a-z])|(Motor|Song)s?(?![a-z])""")

        fun speaksOfIt(line: String): Boolean {
            val trimmed = line.trim()

            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                return false
            }

            val text = trimmed.replace(Regex("KLANG-\\d{5}"), "")

            return words.containsMatchIn(text) || shortWords.containsMatchIn(text)
        }

        val found = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> speaksOfIt(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        sources.size shouldBeGreaterThan 20
        found.shouldBeEmpty()
    }
})
