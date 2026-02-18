package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.strudel.lang.note

class MiniNotationErrorReportingSpec : StringSpec() {

    fun parse(input: String, baseLocation: SourceLocation? = null) =
        parseMiniNotation(input, baseLocation) { text, _ -> note(text) }

    init {
        "Error without source location reports position" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("c3 [d3")  // Missing closing bracket
            }

            exception.position shouldBe 6
            exception.baseLocation shouldBe null
            exception.message shouldContain "Parse error"
        }

        "Error with source location combines positions" {
            val baseLocation = SourceLocation(
                source = "test.klang",
                startLine = 5,
                startColumn = 10,
                endLine = 5,
                endColumn = 20
            )

            val exception = shouldThrow<MiniNotationParseException> {
                parse("c3 [d3", baseLocation)
            }

            // Error at position 6 within mini-notation
            // Combined with baseLocation col 10 = actual col 16
            exception.message shouldContain "5:16"
            exception.position shouldBe 6
            exception.baseLocation shouldBe baseLocation
        }

        "Invalid euclidean rhythm pulses" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("bd(abc,8)")
            }

            exception.message shouldContain "Invalid pulses"
        }
    }
}
