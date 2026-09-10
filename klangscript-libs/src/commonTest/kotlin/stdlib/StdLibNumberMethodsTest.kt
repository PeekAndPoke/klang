/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Methods called on a number literal, end to end: `2.pow(7/12)`, `(-1).mod(12)`, `7.semitones()`.
 *
 * `toString` had been registered on numbers for a long time but was unreachable on a literal until the
 * lexer stopped eating the dot (2026-09-08, `docs/tasks-archive/2026-09/20260908-klangscript-number-methods.md`). The parser side
 * is pinned in `NumberLiteralMethodCallSpec`; this is the proof that the stdlib dispatch sees the call
 * and that every method computes what its KDoc promises.
 *
 * The expected values are computed by hand (or from a table of intervals), never from the function
 * under test, so a wrong delegate cannot agree with the assertion.
 */
class StdLibNumberMethodsTest : StringSpec({

    fun eval(code: String): RuntimeValue {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        return engine.execute("import * from \"stdlib\"\n$code")
    }

    fun num(code: String): Double = eval(code).shouldBeInstanceOf<NumberValue>().value

    "toString() on a number literal" {
        listOf(
            "2.toString()" to "2",
            "2.5.toString()" to "2.5",
            "0.5.toString()" to "0.5",
            "1e3.toString()" to "1000",
            "(-2).toString()" to "-2",
            "(-1.5).toString()" to "-1.5",
        ).forEach { (code, expected) ->
            withClue(code) {
                eval(code).shouldBeInstanceOf<StringValue>().value shouldBe expected
            }
        }
    }

    "toString() on a literal inside a template" {
        eval("`fifth: ${'$'}{7.toString()}`").shouldBeInstanceOf<StringValue>().value shouldBe "fifth: 7"
    }

    // ── Tier 1 and 2: the values a hand calculation pins exactly ────────

    "tier 1 and 2: the exact values" {
        listOf(
            // pow
            "2.5.pow(2)" to 6.25,
            "2.pow(10)" to 1024.0,
            "2.pow(0)" to 1.0,
            "2.pow(-1)" to 0.5,
            // abs, sqrt
            "(-8).abs()" to 8.0,
            "8.abs()" to 8.0,
            "16.sqrt()" to 4.0,
            // rounding
            "1.7.round()" to 2.0,
            "1.2.round()" to 1.0,
            "3.7.floor()" to 3.0,
            "(-3.2).floor()" to -4.0,
            "3.2.ceil()" to 4.0,
            "(-3.7).ceil()" to -3.0,
            // min, max are clamps, not selections: "a.max(b)" reads "a, at most b".
            // Math.min(a, b) / Math.max(a, b) are the selecting pair, see StdLibTest.
            "7.min(3)" to 7.0,   // 7 is already at least 3
            "3.min(7)" to 7.0,   // 3 raised to the floor of 7
            "7.max(3)" to 3.0,   // 7 lowered to the cap of 3
            "3.max(7)" to 3.0,   // 3 is already at most 7
            // clamp
            "5.clamp(0, 3)" to 3.0,
            "(-1).clamp(0, 3)" to 0.0,
            "2.clamp(0, 3)" to 2.0,
            // the two remainders, which differ exactly where the sign differs
            "7.rem(12)" to 7.0,
            "7.mod(12)" to 7.0,
            "(-1).rem(12)" to -1.0,
            "(-1).mod(12)" to 11.0,
            "7.rem(-12)" to 7.0,
            "7.mod(-12)" to -5.0,
            "12.rem(12)" to 0.0,
            "12.mod(12)" to 0.0,
            // logarithmic
            "8.log2()" to 3.0,
            "1.log2()" to 0.0,
            "1000.log10()" to 3.0,
            "1.ln()" to 0.0,
            "0.exp()" to 1.0,
            "(-3).sign()" to -1.0,
            "0.sign()" to 0.0,
            "3.sign()" to 1.0,
        ).forEach { (code, expected) ->
            withClue(code) {
                num(code) shouldBe expected
            }
        }
    }

    "round is ties to even, the same rule the Math door uses" {
        // kotlin.math.round rounds a tie towards the even neighbour: 2.5 goes down, 3.5 goes up.
        num("2.5.round()") shouldBe 2.0
        num("3.5.round()") shouldBe 4.0

        // The method and the Math object must not disagree about it.
        num("2.5.round()") shouldBe num("Math.round(2.5)")
        num("3.5.round()") shouldBe num("Math.round(3.5)")
    }

    "rem is the method form of %" {
        listOf("-1", "7", "12", "5.5").forEach { left ->
            listOf("12", "-12", "3").forEach { right ->
                withClue("$left % $right") {
                    num("($left).rem($right)") shouldBe num("$left % $right")
                }
            }
        }
    }

    "a zero divisor throws the same error as the operator" {
        listOf("5.rem(0)", "5.mod(0)", "5 % 0").forEach { code ->
            withClue(code) {
                val error = shouldThrow<KlangScriptTypeError> { eval(code) }
                error.message shouldContain "Modulo by zero"
                // and with a source location, so the editor can underline the call
                error.location shouldNotBe null
            }
        }
    }

    "a method that takes no argument refuses one instead of dropping it" {
        // 3.14159.round(2) used to return 3 with the 2 silently ignored: the zero-parameter bridge had no
        // arity check. The Python and JS habit (round(x, digits), toFixed(n)) must fail loudly.
        listOf("3.14159.round(2)", "2.sqrt(9)", "7.semitones(12)", "\"M3\".toRatio(1)").forEach { code ->
            withClue(code) {
                val error = shouldThrow<KlangScriptArgumentError> { eval(code) }
                error.message shouldContain "expected 0 arguments"
            }
        }
    }

    "clamp with the bounds the wrong way round says so" {
        val error = shouldThrow<KlangScriptTypeError> { eval("5.clamp(3, 0)") }
        error.message shouldContain "clamp"
        error.message shouldContain "greater than hi"
        error.location shouldNotBe null
    }

    // ── Tier 3: the musical values ──────────────────────────────────────

    "tier 3: semitones, cents, decibels and named intervals" {
        listOf(
            // ln and exp at a point where log2, log10, cos and cosh all give a different answer
            "2.718281828459045.ln()" to 1.0,
            "1.exp()" to 2.7183,
            "2.log2()" to 1.0,
            // 2^(n/12)
            "0.semitones()" to 1.0,
            "12.semitones()" to 2.0,
            "(-12).semitones()" to 0.5,
            "7.semitones()" to 1.4983,
            "(-7).semitones()" to 0.6674,
            // 2^(n/1200)
            "1200.cents()" to 2.0,
            "50.cents()" to 1.0293,
            "100.cents()" to 1.0595,
            // 12 * log2(r)
            "1.toSemitones()" to 0.0,
            "2.toSemitones()" to 12.0,
            "1.5.toSemitones()" to 7.0196,
            // 10^(dB/20)
            "0.db()" to 1.0,
            "(-6).db()" to 0.5012,
            "(-20).db()" to 0.1,
            "20.db()" to 10.0,
            // 20 * log10(gain)
            "1.toDb()" to 0.0,
            "0.5.toDb()" to -6.0206,
            "10.toDb()" to 20.0,
            // named intervals
            "\"P1\".toRatio()" to 1.0,
            "\"m3\".toRatio()" to 1.1892,
            "\"M3\".toRatio()" to 1.2599,
            "\"P5\".toRatio()" to 1.4983,
            "\"P8\".toRatio()" to 2.0,
            "\"-5P\".toRatio()" to 0.6674,
            "\"P-5\".toRatio()" to 0.6674,
            "\"-2m\".toRatio()" to 0.9439,
        ).forEach { (code, expected) ->
            withClue(code) {
                num(code) shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "an interval name that is not one says what a name looks like" {
        val error = shouldThrow<KlangScriptTypeError> { eval("\"xyz\".toRatio()") }
        error.message shouldContain "not an interval name"
        error.message shouldContain "xyz"
        // The error points at the call, like the interpreter's own "Modulo by zero" does
        error.location shouldNotBe null

        // A descending interval carries the sign in front of the NUMBER, so "-5P" is the fifth down
        // and "-P5" is not a name at all. It is rejected rather than silently read as something else.
        // "P0" and "0P" tokenize but there is no interval number 0, and a number that overflows an Int is not
        // a name either: both used to crash inside the tones parser and surface as an internal error.
        listOf(
            "\"-P5\".toRatio()", "\"\".toRatio()", "\"P\".toRatio()", "\"5\".toRatio()",
            "\"P0\".toRatio()", "\"0P\".toRatio()", "\"M0\".toRatio()", "\"99999999999P\".toRatio()",
        ).forEach { code ->
            withClue(code) {
                shouldThrow<KlangScriptTypeError> { eval(code) }.message shouldContain "not an interval name"
            }
        }
    }

    // ── The reason the task exists, and the parser decision made visible ─

    "the intervals that the ^ operator gets wrong" {
        // 2^(7/12) is 2 (bitwise XOR of 2 and 0); the method spelling is the fifth.
        num("2^(7/12)") shouldBe 2.0
        num("2.pow(7/12)") shouldBe (1.4983 plusOrMinus 1e-4)

        // Middle C, a major sixth below concert A.
        num("440 * 2.pow(-9/12)") shouldBe (261.6256 plusOrMinus 1e-4)

        // Chained, the shape patch code is written in.
        num("2.pow(2).sqrt()") shouldBe 2.0
        num("7.semitones().toSemitones()") shouldBe (7.0 plusOrMinus 1e-9)
    }

    "a negative receiver is spelled with parentheses; the bare form is refused" {
        // Kotlin precedence: (-1.0).clamp(0, 1) clamps -1.0 into [0, 1]; -(1.0.clamp(0, 1)) negates the
        // clamped 1.0. The bare -1.0.clamp(0, 1) is a syntax error that names both spellings.
        num("(-1.0).clamp(0, 1)") shouldBe 0.0
        num("-(1.0.clamp(0, 1))") shouldBe -1.0

        val error = shouldThrow<KlangScriptSyntaxError> { eval("-6.db()") }
        error.message shouldContain "is ambiguous"
        error.message shouldContain "(-6).db(...)"

        // A variable behaves the same as in Kotlin: the minus applies to the result
        num("let g = 6; -g.db()") shouldBe (-1.9953 plusOrMinus 1e-4)
        num("let g = -6; g.db()") shouldBe (0.5012 plusOrMinus 1e-4)
    }

    "Math.* keeps working: the methods are an additional spelling" {
        num("Math.pow(2, 10)") shouldBe 1024.0
        num("Math.sqrt(16)") shouldBe 4.0
        num("Math.pow(2, 7/12)") shouldBe num("2.pow(7/12)")
    }
})
