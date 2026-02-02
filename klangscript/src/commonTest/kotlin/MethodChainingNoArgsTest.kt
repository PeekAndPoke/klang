package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for method chaining with no-argument methods
 *
 * **PARSER BUG IDENTIFIED**: The current parser cannot handle member access after a no-arg method call.
 *
 * **Root Cause** (KlangScriptParser.kt:294-309):
 * The callExpr parser has this structure:
 * ```
 * memberExpr and zeroOrMore(
 *     (leftParen ... rightParen) and zeroOrMore(-dot and identifier)
 * )
 * ```
 *
 * **Problem**: After parsing a call like `obj.method()`, the parser only looks for
 * member accesses immediately following the `)` in the same iteration. When it
 * encounters the next `.method`, it tries to start a new iteration which REQUIRES
 * a `(` first, causing a parse error.
 *
 * **Example failure**: `sine2.fromBipolar().range(0.1, 0.9)`
 * 1. Parses `sine2.fromBipolar` as memberExpr
 * 2. Sees `()` - parses as call with empty args
 * 3. Sees `.range` - tries new iteration, expects `(` but finds `.`
 * 4. ParseException!
 *
 * **Fix needed**: Allow alternating calls and member accesses:
 * ```
 * memberExpr and zeroOrMore(
 *     (call: leftParen...rightParen) OR
 *     (member: -dot and identifier)
 * )
 * ```
 */
class MethodChainingNoArgsTest : StringSpec() {

    /**
     * Mock class simulating a continuous pattern with method chaining
     */
    class ContinuousPattern(val value: Double) {
        fun fromBipolar(): ContinuousPattern {
            // Convert -1..1 to 0..1
            return ContinuousPattern((value + 1.0) / 2.0)
        }

        fun range(min: Double, max: Double): ContinuousPattern {
            // Scale to range
            return ContinuousPattern(min + value * (max - min))
        }

        fun scale(factor: Double): ContinuousPattern {
            return ContinuousPattern(value * factor)
        }

        override fun toString(): String = "ContinuousPattern($value)"
    }

    /**
     * Mock class simulating a note pattern
     */
    class NotePattern(val notes: String) {
        fun pan(panValue: ContinuousPattern): NotePattern {
            return NotePattern("$notes[pan=${panValue.value}]")
        }

        override fun toString(): String = notes
    }

    init {

        "No-arg method in middle of chain" {
            val engine = klangScript {
                registerFunction<Double, ContinuousPattern>("sine2") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("fromBipolar") { fromBipolar() }
                    registerMethod("range") { min: Double, max: Double -> range(min, max) }
                    registerMethod("getValue") { this.value }
                }
            }

            // This is the problematic pattern: fromBipolar() has no args in the middle of the chain
            val script = """
                let pattern = sine2(0.5)
                pattern.fromBipolar().range(0.1, 0.9).getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // sine2(0.5) -> 0.5
            // fromBipolar() -> (0.5 + 1.0) / 2.0 = 0.75
            // range(0.1, 0.9) -> 0.1 + 0.75 * (0.9 - 0.1) = 0.1 + 0.75 * 0.8 = 0.7
            result.value shouldBe (0.7 plusOrMinus 0.0001)
        }

        "Multiple no-arg methods in chain" {
            val engine = klangScript {
                registerFunction<Double, ContinuousPattern>("sine2") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("fromBipolar") { fromBipolar() }
                    registerMethod("scale") { factor: Double -> scale(factor) }
                    registerMethod("range") { min: Double, max: Double -> range(min, max) }
                    registerMethod("getValue") { this.value }
                }
            }

            val script = """
                sine2(-1.0).fromBipolar().scale(2.0).getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // sine2(-1.0) -> -1.0
            // fromBipolar() -> (-1.0 + 1.0) / 2.0 = 0.0
            // scale(2.0) -> 0.0 * 2.0 = 0.0
            result.value shouldBe 0.0
        }

        "No-arg method at end of chain" {
            val engine = klangScript {
                registerFunction<Double, ContinuousPattern>("sine2") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("range") { min: Double, max: Double -> range(min, max) }
                    registerMethod("fromBipolar") { fromBipolar() }
                    registerMethod("getValue") { this.value }
                }
            }

            val script = """
                sine2(1.0).range(0.0, 2.0).fromBipolar().getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // sine2(1.0) -> 1.0
            // range(0.0, 2.0) -> 0.0 + 1.0 * (2.0 - 0.0) = 2.0
            // fromBipolar() -> (2.0 + 1.0) / 2.0 = 1.5
            result.value shouldBe 1.5
        }

        "Complete Strudel-like pattern: note().pan(sine2.fromBipolar().range())" {
            val engine = klangScript {
                registerFunction<String, NotePattern>("note") { notes ->
                    NotePattern(notes)
                }
                registerFunction<ContinuousPattern>("sine2") {
                    ContinuousPattern(0.0) // Default sine value
                }
                registerType<NotePattern> {
                    registerMethod("pan") { panValue: ContinuousPattern ->
                        pan(panValue)
                    }
                    registerMethod("toString") { toString() }
                }
                registerType<ContinuousPattern> {
                    registerMethod("fromBipolar") { fromBipolar() }
                    registerMethod("range") { min: Double, max: Double -> range(min, max) }
                }
            }

            // This is the exact pattern from the bug report
            val script = """note("a b c d").pan(sine2().fromBipolar().range(0.1, 0.9)).toString()"""

            val result = engine.execute(script)

            // Check that it contains the expected pan value
            result.toDisplayString() shouldBe "a b c d[pan=0.5]"
        }

        "Inline chained no-arg method call" {
            val engine = klangScript {
                registerFunction<Double, ContinuousPattern>("sine2") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("fromBipolar") { fromBipolar() }
                    registerMethod("range") { min: Double, max: Double -> range(min, max) }
                    registerMethod("getValue") { this.value }
                }
            }

            // Test the exact syntax from the bug report: inline chaining
            val script = """sine2(0.5).fromBipolar().range(0.1, 0.9).getValue()"""

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()
            result.value shouldBe (0.7 plusOrMinus 0.0001)
        }
    }
}
