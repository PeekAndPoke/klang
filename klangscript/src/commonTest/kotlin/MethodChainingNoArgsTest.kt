/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
 * **Example failure**: `lfo.shifted().range(0.1, 0.9)`
 * 1. Parses `lfo.shifted` as memberExpr
 * 2. Sees `()` - parses as call with empty args
 * 3. Sees `.range` - tries new iteration, expects `(` but finds `.`
 * 4. KlangScriptSyntaxError!
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
        fun shifted(): ContinuousPattern {
            // Convert -1..1 to 0..1
            return ContinuousPattern((value + 1.0) / 2.0)
        }

        fun range(from: Double, to: Double): ContinuousPattern {
            // Scale to range
            return ContinuousPattern(from + value * (to - from))
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
            val engine = klangScriptEngine {
                registerFunction<Double, ContinuousPattern>("lfo") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("shifted") { shifted() }
                    registerMethod("range") { from: Double, to: Double -> range(from, to) }
                    registerMethod("getValue") { this.value }
                }
            }

            // This is the problematic pattern: shifted() has no args in the middle of the chain
            val script = """
                let pattern = lfo(0.5)
                pattern.shifted().range(0.1, 0.9).getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // lfo(0.5) -> 0.5
            // shifted() -> (0.5 + 1.0) / 2.0 = 0.75
            // range(0.1, 0.9) -> 0.1 + 0.75 * (0.9 - 0.1) = 0.1 + 0.75 * 0.8 = 0.7
            result.value shouldBe (0.7 plusOrMinus 0.0001)
        }

        "Multiple no-arg methods in chain" {
            val engine = klangScriptEngine {
                registerFunction<Double, ContinuousPattern>("lfo") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("shifted") { shifted() }
                    registerMethod("scale") { factor: Double -> scale(factor) }
                    registerMethod("range") { from: Double, to: Double -> range(from, to) }
                    registerMethod("getValue") { this.value }
                }
            }

            val script = """
                lfo(-1.0).shifted().scale(2.0).getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // lfo(-1.0) -> -1.0
            // shifted() -> (-1.0 + 1.0) / 2.0 = 0.0
            // scale(2.0) -> 0.0 * 2.0 = 0.0
            result.value shouldBe 0.0
        }

        "No-arg method at end of chain" {
            val engine = klangScriptEngine {
                registerFunction<Double, ContinuousPattern>("lfo") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("range") { from: Double, to: Double -> range(from, to) }
                    registerMethod("shifted") { shifted() }
                    registerMethod("getValue") { this.value }
                }
            }

            val script = """
                lfo(1.0).range(0.0, 2.0).shifted().getValue()
            """.trimIndent()

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()

            // lfo(1.0) -> 1.0
            // range(0.0, 2.0) -> 0.0 + 1.0 * (2.0 - 0.0) = 2.0
            // shifted() -> (2.0 + 1.0) / 2.0 = 1.5
            result.value shouldBe 1.5
        }

        "Complete Strudel-like pattern: note().pan(lfo.shifted().range())" {
            val engine = klangScriptEngine {
                registerFunction<String, NotePattern>("note") { notes ->
                    NotePattern(notes)
                }
                registerFunction<ContinuousPattern>("lfo") {
                    ContinuousPattern(0.0) // Default sine value
                }
                registerType<NotePattern> {
                    registerMethod("pan") { panValue: ContinuousPattern ->
                        pan(panValue)
                    }
                    registerMethod("toString") { toString() }
                }
                registerType<ContinuousPattern> {
                    registerMethod("shifted") { shifted() }
                    registerMethod("range") { from: Double, to: Double -> range(from, to) }
                }
            }

            // This is the exact pattern from the bug report
            val script = """note("a b c d").pan(lfo().shifted().range(0.1, 0.9)).toString()"""

            val result = engine.execute(script)

            // Check that it contains the expected pan value
            result.toDisplayString() shouldBe "a b c d[pan=0.5]"
        }

        "Inline chained no-arg method call" {
            val engine = klangScriptEngine {
                registerFunction<Double, ContinuousPattern>("lfo") { value ->
                    ContinuousPattern(value)
                }
                registerType<ContinuousPattern> {
                    registerMethod("shifted") { shifted() }
                    registerMethod("range") { from: Double, to: Double -> range(from, to) }
                    registerMethod("getValue") { this.value }
                }
            }

            // Test the exact syntax from the bug report: inline chaining
            val script = """lfo(0.5).shifted().range(0.1, 0.9).getValue()"""

            val result = engine.execute(script)
            result.shouldBeInstanceOf<NumberValue>()
            result.value shouldBe (0.7 plusOrMinus 0.0001)
        }
    }
}
