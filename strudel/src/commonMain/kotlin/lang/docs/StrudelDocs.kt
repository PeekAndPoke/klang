package io.peekandpoke.klang.strudel.lang.docs

import io.peekandpoke.klang.script.docs.*

/**
 * Registers all Strudel DSL function documentation with the KlangScript documentation registry.
 *
 * This should be called during initialization to make Strudel documentation available
 * for IDE completion, CodeMirror autocomplete, and documentation pages.
 *
 * @param registry The registry to register docs into (defaults to global registry)
 */
fun registerStrudelDocs(registry: DslDocsRegistry = DslDocsRegistry.global) {
    registry.registerAll(strudelFunctionDocs)
}

/**
 * All Strudel DSL function documentation.
 */
private val strudelFunctionDocs = mapOf(
    "seq" to FunctionDoc(
        name = "seq",
        category = "structural",
        tags = listOf("sequence", "timing", "control", "order"),
        library = "strudel",
        variants = listOf(
            VariantDoc(
                type = DslType.TOP_LEVEL,
                signature = "seq(vararg patterns: PatternLike): StrudelPattern",
                description = """
                    Creates a sequence pattern that plays patterns one after another.

                    Each pattern in the sequence occupies exactly one cycle. If you have
                    three patterns, the sequence takes three cycles to complete before
                    looping back to the start.

                    Sequences are fundamental for creating musical phrases, chord progressions,
                    and melodic patterns that unfold over time.
                """.trimIndent(),
                params = listOf(
                    ParamDoc(
                        name = "patterns",
                        type = "vararg PatternLike",
                        description = "Patterns to play in sequence. Accepts patterns, strings, numbers, " +
                                "and other values that can be converted to patterns."
                    )
                ),
                returnDoc = "A sequential pattern that cycles through each input pattern",
                samples = listOf(
                    """seq("bd sd", "hh oh").sound()  // Two drum patterns, each plays for 1 cycle""",
                    """seq("c e", "g b").note()       // Two note patterns in sequence""",
                    """seq("bd", "sd", "hh").s()      // Three samples in sequence"""
                )
            ),
            VariantDoc(
                type = DslType.EXTENSION_METHOD,
                signature = "StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern",
                description = """
                    Appends patterns to this pattern in sequence.

                    Extends the current pattern by adding more patterns that play after it.
                    Each appended pattern plays for one cycle.
                """.trimIndent(),
                params = listOf(
                    ParamDoc(
                        name = "patterns",
                        type = "vararg PatternLike",
                        description = "Additional patterns to append to the sequence"
                    )
                ),
                returnDoc = "Combined sequential pattern",
                samples = listOf(
                    """sound("bd sd").seq(sound("hh oh"))         // bd-sd for cycle 1, then hh-oh for cycle 2""",
                    """note("c e").seq(note("g b"), "c d".note()) // Three cycles of notes"""
                )
            ),
            VariantDoc(
                type = DslType.EXTENSION_METHOD,
                signature = "String.seq(vararg patterns: PatternLike): StrudelPattern",
                description = "Converts string to pattern and appends additional patterns in sequence.",
                params = listOf(
                    ParamDoc(
                        name = "patterns",
                        type = "vararg PatternLike",
                        description = "Additional patterns to append after this string"
                    )
                ),
                returnDoc = "Combined sequential pattern",
                samples = listOf(
                    """sound("bd sd".seq("hh oh"))  // String converted and sequenced"""
                )
            )
        )
    )
    // More Strudel functions will be added here as we convert them...
)
