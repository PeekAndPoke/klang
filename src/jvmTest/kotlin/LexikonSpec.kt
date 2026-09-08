/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.lexikon.allLexikonEntries
import io.peekandpoke.klang.pages.docs.lexikon.lexikonEntryBySlug
import java.io.File

/**
 * The Lexikon slug is a URL, and DSL KDoc links to it, so it is a contract.
 *
 * A slug is derived from the term, which means renaming a term silently rewrites its URL and breaks
 * every link pointing at it. Nothing else in the build would notice: a dead docs link throws no
 * exception and fails no render. These rows are the reason it is safe to derive the slug at all.
 */
class LexikonSpec : StringSpec({

    "every entry has a usable slug" {
        allLexikonEntries.forEach { entry ->
            withClue(entry.term) {
                entry.slug.isNotBlank() shouldBe true
                entry.slug.startsWith("-") shouldBe false
                entry.slug.endsWith("-") shouldBe false
                // A slug goes in a URL, so it may only hold what a URL can carry unescaped
                entry.slug.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } shouldBe true
            }
        }
    }

    "slugs are unique, so a link cannot become ambiguous" {
        val duplicates = allLexikonEntries
            .groupBy { it.slug }
            .filterValues { it.size > 1 }
            .map { (slug, entries) -> "$slug <- ${entries.map { it.term }}" }

        duplicates.shouldBeEmpty()
    }

    "a slug resolves back to its entry" {
        allLexikonEntries.forEach { entry ->
            withClue(entry.slug) {
                lexikonEntryBySlug(entry.slug).shouldNotBeNull().term shouldBe entry.term
            }
        }
    }

    "every Lexikon link written in DSL KDoc points at an entry that exists" {
        // The guard that earns the derived slug: docs link by URL, and a renamed term changes it.
        val linkPattern = Regex("""/manuals/lexikon/([a-z0-9-]+)""")

        val sources = listOf("sprudel/src/commonMain/kotlin/lang", "klangscript-libs/src/commonMain/kotlin")
            .map { File(it) }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }

        val found = sources.flatMap { file ->
            linkPattern.findAll(file.readText()).map { file.path to it.groupValues[1] }.toList()
        }

        // Guard the guard: if the links are gone, or the paths moved, or the link syntax changed,
        // this row must fail rather than quietly pass with nothing to check.
        withClue("no /manuals/lexikon/ links found in the DSL sources, so this row checked nothing") {
            found.isNotEmpty() shouldBe true
        }

        val broken = found
            .filter { (_, slug) -> lexikonEntryBySlug(slug) == null }
            .map { (path, slug) -> "$path: /manuals/lexikon/$slug" }

        broken.shouldBeEmpty()
    }

    "the scope vocabulary the DSL docs lean on is present" {
        // These three carry the per-voice / bus / send distinction that `@scope` badges point at.
        listOf("voice", "orbit-bus", "send", "orbit").forEach { slug ->
            withClue(slug) { lexikonEntryBySlug(slug).shouldNotBeNull() }
        }
    }
})
