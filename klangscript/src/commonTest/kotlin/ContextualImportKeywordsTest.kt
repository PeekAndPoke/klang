/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ExportStatement
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * `from` and `as` are contextual keywords, as in JavaScript: the import and export grammar reads them as keywords, and
 * everywhere else they are ordinary names. The range values of every DSL are named `from` and `to`
 * (`range(from = 200, to = 400)`), so a hard keyword would make the named call a syntax error
 * (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`, maintainer decisions of 2026-10-05).
 */
class ContextualImportKeywordsTest : StringSpec({

    fun run(code: String): Double {
        val result = klangScriptEngine().execute(code)
        result.shouldBeInstanceOf<NumberValue>()

        return result.value
    }

    for (word in listOf("from", "as")) {

        "$word is a named argument" {
            run("let f = ($word, to) => $word - to\nf($word = 10, to = 3)") shouldBe 7.0
            run("let f = ($word, to) => $word - to\nf(to = 3, $word = 10)") shouldBe 7.0
        }

        "$word is a function parameter name" {
            run("let f = ($word, to) => $word + to\nf(1, 2)") shouldBe 3.0
            run("let f = $word => $word * 2\nf(4)") shouldBe 8.0
        }

        "$word is a variable" {
            run("let $word = 5\n$word + 1") shouldBe 6.0
            run("const $word = 5\n$word * 3") shouldBe 15.0
            run("let $word = 5\n$word = 9\n$word") shouldBe 9.0
        }

        "$word is an object key and a member name" {
            run("let x = { $word: 2, to: 4 }\nx.$word + x.to") shouldBe 6.0
            run("let $word = 7\nlet x = { $word }\nx.$word") shouldBe 7.0
            run("let x = { $word: 2 }\nx?.$word") shouldBe 2.0
        }
    }

    "every import and export form still parses" {
        val star = KlangScriptParser.parse("""import * from "x"""").statements.single()
        star.shouldBeInstanceOf<ImportStatement>()
        star.libraryName shouldBe "x"
        star.imports shouldBe null
        star.namespaceAlias shouldBe null

        val namespace = KlangScriptParser.parse("""import * as y from "x"""").statements.single()
        namespace.shouldBeInstanceOf<ImportStatement>()
        namespace.libraryName shouldBe "x"
        namespace.namespaceAlias shouldBe "y"

        val selective = KlangScriptParser.parse("""import { a, b as c } from "x"""").statements.single()
        selective.shouldBeInstanceOf<ImportStatement>()
        selective.libraryName shouldBe "x"
        selective.imports shouldBe listOf("a" to "a", "b" to "c")

        val export = KlangScriptParser.parse("""export { a, b as c }""").statements.single()
        export.shouldBeInstanceOf<ExportStatement>()
        export.exports shouldBe listOf("a" to "a", "b" to "c")
    }

    "the keywords are also names inside the import and export grammar" {
        // A library value named `as` or `from`, imported, aliased and exported under those names
        val selective = KlangScriptParser.parse("""import { as, from as as } from "x"""").statements.single()
        selective.shouldBeInstanceOf<ImportStatement>()
        selective.imports shouldBe listOf("as" to "as", "from" to "as")

        val namespace = KlangScriptParser.parse("""import * as as from "x"""").statements.single()
        namespace.shouldBeInstanceOf<ImportStatement>()
        namespace.namespaceAlias shouldBe "as"

        val export = KlangScriptParser.parse("""export { from as as }""").statements.single()
        export.shouldBeInstanceOf<ExportStatement>()
        export.exports shouldBe listOf("from" to "as")
    }

    "an import still runs, and names called from and as can be imported and used next to it" {
        val engine = klangScriptEngine {
            registerLibrary(
                "lib",
                """
                    let from = 40
                    let as = 2
                    export { from, as }
                """.trimIndent(),
            )
        }

        engine.execute("""import { from, as as two } from "lib"""")
        val result = engine.execute("from + two")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "a missing keyword or name is the same syntax error as before" {
        // The source, the message, and the 1-based column of the token the parser stopped at
        val cases = listOf(
            // missing `from`
            Triple("""import * "x"""", "Expected 'from' after import", 10),
            Triple("""import * as y "x"""", "Expected 'from' after import", 15),
            Triple("""import { a } "x"""", "Expected 'from'", 14),
            Triple("""import { a } to "x"""", "Expected 'from'", 14),
            // missing `as`: the name after `*` is where `from` was expected
            Triple("""import * y from "x"""", "Expected 'from' after import", 10),
            // missing name after `as`
            Triple("""import * as "x"""", "Expected namespace name", 13),
            Triple("""import { a as } from "x"""", "Expected alias", 15),
            Triple("""export { a as }""", "Expected exported name", 15),
            // a stray word where `as` or `}` belongs
            Triple("""import { a b } from "x"""", "Expected '}'", 12),
            Triple("""export { a b }""", "Expected '}'", 12),
        )

        for ((code, message, column) in cases) {
            withClue(code) {
                val error = shouldThrow<KlangScriptSyntaxError> { KlangScriptParser.parse(code) }
                error.message shouldBe message
                val location = error.location.shouldNotBeNull()
                location.startLine shouldBe 1
                location.startColumn shouldBe column
            }
        }
    }
})
