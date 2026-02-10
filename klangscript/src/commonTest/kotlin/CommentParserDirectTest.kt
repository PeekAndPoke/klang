package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.parser.KlangScriptParser

/**
 * Direct parser tests to check for tokenization issues with comments and division
 */
class CommentParserDirectTest : StringSpec({

    "parse division with inline comment" {
        val code = "10 / 2 // comment"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "parse division immediately followed by comment" {
        val code = "10/2//comment"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "parse multiple divisions with comment" {
        val code = "20/4/2 // three divisions"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }

    "parse incomplete division before comment" {
        val code = "10 / // comment"
        val exception = shouldThrow<ParseException> {
            KlangScriptParser.parse(code, "test")
        }
        println("✓ Expected parse error: ${exception.message}")
    }

    "parse division on multiple lines with comment" {
        val code = """
            10 /
            // comment on its own line
            2
        """.trimIndent()
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed multiline division with comment")
    }

    "parse expression ending with division and comment" {
        val code = "let x = 100 / 5 // result is 20"
        val ast = KlangScriptParser.parse(code, "test")
        println("✓ Parsed: $code")
    }
})
