package io.peekandpoke.klang.strudel.lang.locations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.strudelLib

/**
 * Integration tests for source location tracking through the complete pipeline:
 * KlangScript source → Parser → Interpreter → Pattern → Events
 */
class SourceLocationIntegrationTest : StringSpec({

    fun executeAndGetPattern(code: String): StrudelPattern {
        val engine = klangScript {
            registerLibrary(strudelLib)
        }

        val result = engine.execute(code)

        result shouldNotBe null
        result.shouldBeInstanceOf<NativeObjectValue<*>>()

        val pattern = result.value.shouldBeInstanceOf<StrudelPattern>()

        return pattern
    }

    "sound(\"bd hh sd oh\") - basic pattern with correct atom locations" {
        val code = """
import * from "stdlib"
import * from "strudel"

sound("bd hh sd oh")
        """.trimIndent()

        val pattern = executeAndGetPattern(code)

        // Query first cycle to get all events
        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4

        // Each atom should have the correct source location
        // Line 4:  sound("bd hh sd oh")
        // Columns: 123456789012345678901
        //                 ^  ^  ^  ^
        //                 8  11 14 17

        val locations = events.mapNotNull { it.sourceLocations?.outermost }
        locations shouldHaveSize 4

        // "bd" starts at line 4, column 7 (after opening quote at column 6)
        locations[0].startLine shouldBe 4
        locations[0].startColumn shouldBe 8

        // "hh" starts at column 10
        locations[1].startLine shouldBe 4
        locations[1].startColumn shouldBe 11

        // "sd" starts at column 13
        locations[2].startLine shouldBe 4
        locations[2].startColumn shouldBe 14

        // "oh" starts at column 16
        locations[3].startLine shouldBe 4
        locations[3].startColumn shouldBe 17
    }

    "sound(\" bd  hh   sd    oh  \") - pattern with extra whitespace" {
        val code = """
import * from "stdlib"
import * from "strudel"

sound(" bd  hh   sd    oh  ")
        """.trimIndent()

        val pattern = executeAndGetPattern(code)

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4

        // Line #4: sound(" bd  hh   sd    oh  ")
        // Columns: 123456789012345678901234567890
        //                  ^   ^    ^     ^
        //                  9   13   18    24

        val locations = events.mapNotNull { it.sourceLocations?.outermost }
        locations shouldHaveSize 4

        // "bd" starts at column 8 (after space)
        locations[0].startColumn shouldBe 9

        // "hh" starts at column 12
        locations[1].startColumn shouldBe 13

        // "sd" starts at column 17
        locations[2].startColumn shouldBe 18

        // "oh" starts at column 23
        locations[3].startColumn shouldBe 24
    }

    "\"bd hh sd oh\".sound() - method call syntax" {
        val code = """
import * from "stdlib"
import * from "strudel"

"bd hh sd oh".sound()
        """.trimIndent()

        val pattern = executeAndGetPattern(code)

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4

        // Line 4:  "bd hh sd oh".sound()
        // Columns: 12345678901234567890
        //           ^  ^  ^  ^
        //           2  5  8  11

        val locations = events.mapNotNull { it.sourceLocations?.outermost }
        locations shouldHaveSize 4

        // "bd" starts at line 4, column 2
        locations[0].startLine shouldBe 4
        locations[0].startColumn shouldBe 2

        // "hh" starts at column 5
        locations[1].startLine shouldBe 4
        locations[1].startColumn shouldBe 5

        // "sd" starts at column 8
        locations[2].startLine shouldBe 4
        locations[2].startColumn shouldBe 8

        // "oh" starts at column 11
        locations[3].startLine shouldBe 4
        locations[3].startColumn shouldBe 11
    }

    "sound(`bd hh sd oh`) - multiline string" {
        val code = """
import * from "stdlib"
import * from "strudel"

sound(`
bd hh sd oh
`)
        """.trimIndent()

        val pattern = executeAndGetPattern(code)

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4

        // Line 5: bd hh sd oh
        // Columns: 12345678901
        //          ^  ^  ^  ^
        //          1  4  7  10

        val locations = events.mapNotNull { it.sourceLocations?.outermost }
        locations shouldHaveSize 4

        // "bd" starts at line 5, column 1
        locations[0].startLine shouldBe 5
        locations[0].startColumn shouldBe 1

        // "hh" starts at column 4
        locations[1].startLine shouldBe 5
        locations[1].startColumn shouldBe 4

        // "sd" starts at column 7
        locations[2].startLine shouldBe 5
        locations[2].startColumn shouldBe 7

        // "oh" starts at column 10
        locations[3].startLine shouldBe 5
        locations[3].startColumn shouldBe 10
    }

    "`bd hh sd oh`.sound() - multiline method call" {
        val code = """
import * from "stdlib"
import * from "strudel"

`
bd hh sd oh
`.sound()
        """.trimIndent()

        val pattern = executeAndGetPattern(code)

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4

        // Line 5: bd hh sd oh
        // Columns: 12345678901
        //          ^  ^  ^  ^
        //          1  4  7  10

        val locations = events.mapNotNull { it.sourceLocations?.outermost }
        locations shouldHaveSize 4

        // "bd" starts at line 5, column 1
        locations[0].startLine shouldBe 5
        locations[0].startColumn shouldBe 1

        // "hh" starts at column 4
        locations[1].startLine shouldBe 5
        locations[1].startColumn shouldBe 4

        // "sd" starts at column 7
        locations[2].startLine shouldBe 5
        locations[2].startColumn shouldBe 7

        // "oh" starts at column 10
        locations[3].startLine shouldBe 5
        locations[3].startColumn shouldBe 10
    }
})
