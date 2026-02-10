package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Test to reproduce the division + comment bug
 * Issue: "Expected number after '/'" error when using // comments after division
 */
class CommentDivisionBugTest : StringSpec({

    "should handle inline comment after division" {
        val script = klangScript()

        val result = script.execute("10 / 2 // divide by two")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 5.0
    }

    "should handle division with comment on same line" {
        val script = klangScript()

        val result = script.execute("100 / 5 // this is division")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 20.0
    }

    "should handle multiple divisions with comment" {
        val script = klangScript()

        val result = script.execute("20 / 4 / 2 // divide twice")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 2.5
    }

    "should handle division in expression with comment" {
        val script = klangScript()

        val result = script.execute("5 + 10 / 2 // mix operators")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 10.0
    }

    "should handle comment immediately after division operator" {
        val script = klangScript()

        val result = script.execute("10 //// comment without space")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 10.0
    }

    "should handle division followed by whitespace and comment" {
        val script = klangScript()

        val result = script.execute("15 /   // comment with extra spaces\n 3")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 5.0
    }

    "should handle standalone division operator before comment on next line" {
        val script = klangScript()

        val result = script.execute(
            """
            10 /
            // comment
            2
        """.trimIndent()
        )

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 5.0
    }
})
