package io.peekandpoke.klang.sprudel.lang.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.sprudel.lang.note

class MiniNotationErrorReportingSpec : StringSpec() {

    fun parse(input: String, baseLocation: SourceLocation? = null) =
        parseMiniNotation(input, baseLocation) { text, _ -> note(text) }

    init {
        "Error without source location reports position" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("c3 [d3")  // Missing closing bracket
            }

            // Error spans from '[' (pos 3) to end (pos 6)
            exception.startPosition shouldBe 3
            exception.endPosition shouldBe 6
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

            // Error spans from '[' (pos 3) to end (pos 6)
            exception.startPosition shouldBe 3
            exception.endPosition shouldBe 6
            exception.baseLocation shouldBe baseLocation
            // Error message reports the end position: col 10 + 1 (quote) + 6 = 17
            exception.message shouldContain "5:17"
            // Location spans from col 14 (10+1+3) to col 17 (10+1+6)
            exception.location?.startColumn shouldBe 14
            exception.location?.endColumn shouldBe 17
        }

        "Error for unclosed angle bracket spans from opener" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("c3 <d3 e3")
            }

            // Error spans from '<' (pos 3) to end (pos 9)
            exception.startPosition shouldBe 3
            exception.endPosition shouldBe 9
        }

        "Invalid euclidean rhythm pulses" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("bd(abc,8)")
            }

            exception.message shouldContain "Invalid pulses"
        }

        "Unclosed euclidean paren spans from opener" {
            val exception = shouldThrow<MiniNotationParseException> {
                parse("bd(3,8")
            }

            // Error spans from '(' (pos 2) to end (pos 6)
            exception.startPosition shouldBe 2
            exception.endPosition shouldBe 6
        }
    }
}
