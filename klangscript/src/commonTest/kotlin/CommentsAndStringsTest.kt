package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for Step 1.3: Lexer - Comments & Strings
 *
 * This test suite verifies:
 * - Single-line comments (//)
 * - Multi-line comments (/* */)
 * - Backtick string literals with multi-line support
 * - Basic string tokenization
 *
 * Note: Escape sequence processing is handled at the lexer token level,
 * so strings are captured with their escape sequences intact.
 */
class CommentsAndStringsTest : StringSpec({

    // ============================================================
    // Single-line Comments
    // ============================================================

    "should ignore single-line comments" {
        val script = klangScript()

        val result = script.execute(
            """
                // This is a comment
                42
            """.trimIndent()
        )

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should handle inline single-line comments" {
        val script = klangScript()

        val result = script.execute("10 + 20 // add numbers")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 30.0
    }

    "should handle multiple single-line comments" {
        val script = klangScript()

        val result = script.execute(
            """
                // First comment
                // Second comment
                // Third comment
                123
            """.trimIndent()
        )

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 123.0
    }

    // ============================================================
    // Multi-line Comments
    // ============================================================

    "should ignore multi-line comments" {
        val script = klangScript()

        val result = script.execute(
            """
                /* This is a
                   multi-line
                   comment */
                99
            """.trimIndent()
        )

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 99.0
    }

    "should handle inline multi-line comments" {
        val script = klangScript()

        val result = script.execute("5 /* comment */ + /* another */ 3")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 8.0
    }

    "should handle multi-line comment with special characters" {
        val script = klangScript()

        val result = script.execute(
            """
                /* Comment with symbols: !@#$%^&*()_+-=[]|:";'<>?,./~ */
                55
            """.trimIndent()
        )

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 55.0
    }

    // ============================================================
    // Backtick Strings (Multi-line)
    // ============================================================

    "should parse simple backtick string" {
        val script = klangScript()

        val result = script.execute("`hello world`")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "hello world"
    }

    "should parse multi-line backtick string" {
        val script = klangScript()

        val result = script.execute(
            """
            `line one
line two
line three`
        """.trimIndent()
        )

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "line one\nline two\nline three"
    }

    "should parse backtick string from Strudel example" {
        val script = klangScript()
        script.registerVariable("sound", script.createNativeFunction("sound") { args: List<RuntimeValue> ->
            val pattern = (args[0] as StringValue).value
            StringValue("sound:$pattern")
        })

        val result = script.execute(
            """
            sound(`bd*2, - cp,
- - - oh, hh*4,
[- casio]*2`)
        """.trimIndent()
        )

        result.shouldBeInstanceOf<StringValue>()
        val str = result.value
        str shouldBe "sound:bd*2, - cp,\n- - - oh, hh*4,\n[- casio]*2"
    }

    "should handle backtick string with embedded quotes" {
        val script = klangScript()

        val result = script.execute("""`He said "hello" and she said 'hi'`""")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe """He said "hello" and she said 'hi'"""
    }

    "should use backtick string as object key" {
        val script = klangScript()

        val result = script.execute("{ `multi-line-key`: 100 }")

        result.shouldBeInstanceOf<ObjectValue>()
        (result.getProperty("multi-line-key") as NumberValue).value shouldBe 100.0
    }

    // ============================================================
    // String Types
    // ============================================================

    "should handle double-quoted strings" {
        val script = klangScript()

        val result = script.execute(""""hello world"""")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "hello world"
    }

    "should handle single-quoted strings" {
        val script = klangScript()

        val result = script.execute("""'hello world'""")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "hello world"
    }

    // ============================================================
    // Mixed Comments and Strings
    // ============================================================

    "should not treat comment markers inside strings as comments" {
        val script = klangScript()

        val result = script.execute(""""This // is not a comment"""")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "This // is not a comment"
    }

    "should not treat comment markers inside backtick strings as comments" {
        val script = klangScript()

        val result = script.execute("""`This /* is */ not a comment`""")

        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "This /* is */ not a comment"
    }
})
