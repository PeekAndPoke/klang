/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.KlangScriptErrorType
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError

class KlangScriptErrorReportingTest : StringSpec() {
    init {
        "Parse error includes line and column" {
            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse("let x = @")
            }

            exception.errorType shouldBe KlangScriptErrorType.SyntaxError
            exception.location?.startLine shouldBe 1
            exception.location?.startColumn shouldBe 9
        }

        "Parse error on multiline code" {
            val code = """
                let x = 10
                let y = @
            """.trimIndent()

            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse(code)
            }

            exception.location?.startLine shouldBe 2
        }

        "Missing closing bracket" {
            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse("let arr = [1, 2, 3")
            }

            exception.message shouldContain "']'"
        }

        "An unclosed block comment is a syntax error at its start" {
            val code = """
                let x = 1 /* never closed
                let y = 2
            """.trimIndent()

            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse(code)
            }

            exception.message shouldContain "Unterminated block comment"
            exception.location?.startLine shouldBe 1
            exception.location?.startColumn shouldBe 11
            // ... to the end of the text, its last character included
            exception.location?.endLine shouldBe 2
            exception.location?.endColumn shouldBe 10
        }

        "An unclosed block comment that ends in a star is a syntax error too" {
            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse("1 /* *")
            }

            exception.message shouldContain "Unterminated block comment"
        }

        "The slash that opens a block comment does not close it" {
            val exception = shouldThrow<KlangScriptSyntaxError> {
                KlangScriptParser.parse("/*/")
            }

            exception.message shouldContain "Unterminated block comment"
        }
    }
}
