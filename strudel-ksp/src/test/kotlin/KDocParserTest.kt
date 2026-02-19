package io.peekandpoke.klang.strudel.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

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

        result.description shouldBe "This is a simple function description. It can span multiple lines."
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

    "parse single sample" {
        val kdoc = """
            Creates a sequence.

            @sample sound(seq("bd sd", "hh oh"))
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf("""sound(seq("bd sd", "hh oh"))""")
    }

    "parse multiple samples" {
        val kdoc = """
            Creates a sequence.

            @sample sound(seq("bd sd", "hh oh"))
            @sample note(seq("c e", "g b"))
            @sample seq("bd", "sd", "hh").s()
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf(
            """sound(seq("bd sd", "hh oh"))""",
            """note(seq("c e", "g b"))""",
            """seq("bd", "sd", "hh").s()"""
        )
    }

    "parse multi-line sample" {
        val kdoc = """
            Creates a complex pattern.

            @sample sound(seq("bd sd", "hh oh"))
                    .gain(0.8)
                    .pan(sine.range(0, 1))
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples shouldContainExactly listOf(
            """sound(seq("bd sd", "hh oh")) .gain(0.8) .pan(sine.range(0, 1))"""
        )
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

    "parse complete KDoc with all tags" {
        val kdoc = """
            Creates a sequence pattern that plays patterns one after another.

            Each pattern in the sequence occupies exactly one cycle.

            @category structural
            @tags sequence, timing, control, order
            @param patterns Patterns to play in sequence.
                            Accepts patterns, strings, numbers.
            @return A sequential pattern that cycles through each input pattern
            @sample sound(seq("bd sd", "hh oh"))
            @sample note(seq("c e", "g b"))
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "Creates a sequence pattern that plays patterns one after another. Each pattern in the sequence occupies exactly one cycle."
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
            @sample example1()
            @param y Second param
            @sample example2()
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

    "parse KDoc with code block in sample" {
        val kdoc = """
            Complex function.

            @sample stack(
                        sound("bd").every(4),
                        sound("hh").fast(2)
                    )
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.samples.size shouldBe 1
        result.samples[0] shouldBe """stack( sound("bd").every(4), sound("hh").fast(2) )"""
    }

    "normalize excessive whitespace" {
        val kdoc = """
            This    has     excessive

            whitespace    in    description.

            @param  x    Parameter    with    spaces
        """.trimIndent()

        val result = KDocParser.parse(kdoc)

        result.description shouldBe "This has excessive whitespace in description."
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
})
