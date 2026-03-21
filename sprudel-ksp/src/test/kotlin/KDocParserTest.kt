package io.peekandpoke.klang.sprudel.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class KDocParserTest : StringSpec({

    "parse empty KDoc" {
        val result = KDocParser.parse(null)

        result.description shouldBe ""
        result.params shouldBe emptyMap()
        result.returnDoc shouldBe ""
        result.samples shouldBe emptyList()
        result.category shouldBe null
        result.tags shouldBe emptyList()
    }

    "parse description only" {
        val kdoc = """
            This is a simple function description.
            It can span multiple lines.
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldContain "This is a simple function description."
        result.description shouldContain "It can span multiple lines."
        result.params shouldBe emptyMap()
        result.returnDoc shouldBe ""
    }

    "parse description with single param" {
        val kdoc = """
            Creates a sequence pattern.

            @param patterns Patterns to play in sequence
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "Creates a sequence pattern."
        result.params shouldContainExactly mapOf("patterns" to "Patterns to play in sequence")
    }

    "parse description with multiple params" {
        val kdoc = """
            Echoes a pattern with decay.

            @param times Number of echoes
            @param decay Decay factor for each echo
            @param delay Delay between echoes
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "Echoes a pattern with decay."
        result.params shouldContainExactly mapOf(
            "times" to "Number of echoes",
            "decay" to "Decay factor for each echo",
            "delay" to "Delay between echoes"
        )
    }

    "parse multi-line param description" {
        val kdoc = """
            Function with long param.

            @param pattern The pattern to modify.
                           This can be any valid pattern.
                           It supports multiple formats.
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.params shouldContainExactly mapOf(
            "pattern" to "The pattern to modify. This can be any valid pattern. It supports multiple formats."
        )
    }

    "parse return tag" {
        val kdoc = """
            Creates a pattern.

            @return A new StrudelPattern instance
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.returnDoc shouldBe "A new StrudelPattern instance"
    }

    "parse multi-line return" {
        val kdoc = """
            Creates a pattern.

            @return A new StrudelPattern instance
                    that plays the sequence
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.returnDoc shouldBe "A new StrudelPattern instance that plays the sequence"
    }

    "parse single KlangScript fenced block" {
        val kdoc = """
            Creates a sequence.

            ```KlangScript
            sound(seq("bd sd", "hh oh"))
            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf("""sound(seq("bd sd", "hh oh"))""")
    }

    "parse multiple KlangScript fenced blocks" {
        val kdoc = """
            Creates a sequence.

            ```KlangScript
            sound(seq("bd sd", "hh oh"))
            ```

            ```KlangScript
            note(seq("c e", "g b"))
            ```

            ```KlangScript
            seq("bd", "sd", "hh").s()
            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf(
            """sound(seq("bd sd", "hh oh"))""",
            """note(seq("c e", "g b"))""",
            """seq("bd", "sd", "hh").s()"""
        )
    }

    "parse multi-line KlangScript fenced block" {
        val kdoc = """
            Creates a complex pattern.

            ```KlangScript
            stack(
                sound("bd").every(4),
                sound("hh").fast(2)
            )
            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples.size shouldBe 1
        result.samples[0] shouldBe "stack(\n    sound(\"bd\").every(4),\n    sound(\"hh\").fast(2)\n)"
    }

    "fenced blocks can appear anywhere — in description section" {
        val kdoc = """
            Creates a sequence.

            ```KlangScript
            seq("a b c").note()
            ```

            @category structural
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf("""seq("a b c").note()""")
        result.category shouldBe "structural"
        // description should not include the code block content
        result.description shouldBe "Creates a sequence."
    }

    "fenced blocks can appear anywhere — in tag section" {
        val kdoc = """
            Creates a sequence.

            @return A new pattern.

            ```KlangScript
            seq("a b c").note()
            ```

            @category structural
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf("""seq("a b c").note()""")
        result.returnDoc shouldBe "A new pattern."
        result.category shouldBe "structural"
    }

    "fenced blocks do not bleed into description" {
        val kdoc = """
            Short description.

            ```KlangScript
            example().code()
            ```

            @category structural
            @tags foo, bar
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "Short description."
        result.samples shouldContainExactly listOf("example().code()")
    }

    "parse category tag" {
        val kdoc = """
            A structural function.

            @category structural
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.category shouldBe "structural"
    }

    "parse tags" {
        val kdoc = """
            A sequence function.

            @tags sequence, timing, control
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.tags shouldContainExactly listOf("sequence", "timing", "control")
    }

    "parse complete KDoc with all fields" {
        val kdoc = """
            Creates a sequence pattern that plays patterns one after another.

            Each pattern in the sequence occupies exactly one cycle.

            @param patterns Patterns to play in sequence.
                            Accepts patterns, strings, numbers.
            @return A sequential pattern that cycles through each input pattern

            ```KlangScript
            sound(seq("bd sd", "hh oh"))
            ```

            ```KlangScript
            note(seq("c e", "g b"))
            ```

            @category structural
            @tags sequence, timing, control, order
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldContain "Creates a sequence pattern that plays patterns one after another."
        result.description shouldContain "Each pattern in the sequence occupies exactly one cycle."
        result.category shouldBe "structural"
        result.tags shouldContainExactly listOf("sequence", "timing", "control", "order")
        result.params shouldContainExactly mapOf(
            "patterns" to "Patterns to play in sequence. Accepts patterns, strings, numbers."
        )
        result.returnDoc shouldBe "A sequential pattern that cycles through each input pattern"
        result.samples shouldContainExactly listOf(
            """sound(seq("bd sd", "hh oh"))""",
            """note(seq("c e", "g b"))"""
        )
    }

    "handle tags in different order" {
        val kdoc = """
            Function description.

            @return Some value
            @category test
            @param x First param
            @tags tag1, tag2

            ```KlangScript
            example1()
            ```

            @param y Second param

            ```KlangScript
            example2()
            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "Function description."
        result.category shouldBe "test"
        result.tags shouldContainExactly listOf("tag1", "tag2")
        result.params shouldContainExactly mapOf(
            "x" to "First param",
            "y" to "Second param"
        )
        result.returnDoc shouldBe "Some value"
        result.samples shouldContainExactly listOf("example1()", "example2()")
    }

    "normalize excessive whitespace in params" {
        val kdoc = """
            Some description.

            @param  x    Parameter    with    spaces
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.params shouldContainExactly mapOf(
            "x" to "Parameter with spaces"
        )
    }

    "handle empty tags" {
        val kdoc = """
            Function.

            @tags  ,  , valid  ,  ,  another  ,
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.tags shouldContainExactly listOf("valid", "another")
    }

    "empty fenced block is ignored" {
        val kdoc = """
            Function.

            ```KlangScript
            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldBe emptyList()
    }

    "parse param-tool tag — single tool" {
        val kdoc = """
            ADSR envelope.

            @param params The envelope parameters
            @param-tool params StrudelAdsrEditor
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramTools shouldContainExactly mapOf("params" to listOf("StrudelAdsrEditor"))
    }

    "parse param-tool tag — multiple tools for one param" {
        val kdoc = """
            ADSR envelope.

            @param-tool params StrudelAdsrEditor, OtherTool
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramTools shouldContainExactly mapOf("params" to listOf("StrudelAdsrEditor", "OtherTool"))
    }

    "parse param-tool tag — multiple params" {
        val kdoc = """
            A function with tools on multiple params.

            @param-tool params StrudelAdsrEditor
            @param-tool rhythm StrudelPatternEditor
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramTools shouldContainExactly mapOf(
            "params" to listOf("StrudelAdsrEditor"),
            "rhythm" to listOf("StrudelPatternEditor"),
        )
    }

    "parse param-sub tag — single sub-field" {
        val kdoc = """
            Distortion effect.

            @param amount The distortion amount or compound string.
            @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramSubs shouldContainExactly mapOf(
            "amount" to mapOf("amount" to "Distortion drive level (0 = clean, 2 = extreme)")
        )
    }

    "parse param-sub tag — multiple sub-fields for one param" {
        val kdoc = """
            Distortion effect.

            @param amount The distortion amount or compound string.
            @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
            @param-sub amount shape Waveshaper curve type: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramSubs shouldContainExactly mapOf(
            "amount" to mapOf(
                "amount" to "Distortion drive level (0 = clean, 2 = extreme)",
                "shape" to "Waveshaper curve type: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp"
            )
        )
    }

    "parse param-sub tag — sub-fields for multiple params" {
        val kdoc = """
            Complex effect.

            @param-sub paramA fieldX Description of fieldX
            @param-sub paramA fieldY Description of fieldY
            @param-sub paramB fieldZ Description of fieldZ
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.paramSubs shouldContainExactly mapOf(
            "paramA" to mapOf(
                "fieldX" to "Description of fieldX",
                "fieldY" to "Description of fieldY"
            ),
            "paramB" to mapOf(
                "fieldZ" to "Description of fieldZ"
            )
        )
    }

    "whitespace-only fenced block is ignored" {
        val kdoc = """
            Function.

            ```KlangScript

            ```
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldBe emptyList()
    }
})
