/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pure-scanner tests for [DefaultValueExtractor].
 *
 * These exercise [DefaultValueExtractor.extractFromWindow] directly with
 * synthetic source windows so we don't need a real KSP environment.
 */
class DefaultValueExtractorTest : StringSpec({

    "literal number default" {
        val src = "fun filter(cutoff: Double = 1000.0)"
        DefaultValueExtractor.extractFromWindow(src, "cutoff") shouldBe "1000.0"
    }

    "literal string default" {
        val src = """fun greet(name: String = "world")"""
        DefaultValueExtractor.extractFromWindow(src, "name") shouldBe "\"world\""
    }

    "expression default — math" {
        val src = "fun phase(theta: Double = 2.0 * 3.14159)"
        DefaultValueExtractor.extractFromWindow(src, "theta") shouldBe "2.0 * 3.14159"
    }

    "function call default" {
        val src = "fun rng(seed: Int = computeDefaultSeed())"
        DefaultValueExtractor.extractFromWindow(src, "seed") shouldBe "computeDefaultSeed()"
    }

    "nested call with internal commas" {
        val src = "fun mix(weights: List<Double> = listOf(0.5, 0.3, 0.2))"
        DefaultValueExtractor.extractFromWindow(src, "weights") shouldBe "listOf(0.5, 0.3, 0.2)"
    }

    "lambda default" {
        val src = "fun trig(curve: () -> Double = { 1.0 })"
        DefaultValueExtractor.extractFromWindow(src, "curve") shouldBe "{ 1.0 }"
    }

    "second of two defaulted params" {
        val src = "fun pair(a: Int = 1, b: Int = 2)"
        DefaultValueExtractor.extractFromWindow(src, "b") shouldBe "2"
    }

    "first of two defaulted params" {
        val src = "fun pair(a: Int = 1, b: Int = 2)"
        DefaultValueExtractor.extractFromWindow(src, "a") shouldBe "1"
    }

    "string with comma inside" {
        val src = """fun fmt(sep: String = ",")"""
        DefaultValueExtractor.extractFromWindow(src, "sep") shouldBe "\",\""
    }

    "string with closing paren inside" {
        val src = """fun lbl(text: String = "(test)")"""
        DefaultValueExtractor.extractFromWindow(src, "text") shouldBe "\"(test)\""
    }

    "char literal default" {
        val src = "fun delim(sep: Char = ',')"
        DefaultValueExtractor.extractFromWindow(src, "sep") shouldBe "','"
    }

    "comparison in default — not misread" {
        val src = "fun gate(threshold: Boolean = a == b)"
        DefaultValueExtractor.extractFromWindow(src, "threshold") shouldBe "a == b"
    }

    "default with arrow inside lambda — not misread as `=>`" {
        val src = "fun pat(map: (Int) -> Int = { x -> x + 1 })"
        DefaultValueExtractor.extractFromWindow(src, "map") shouldBe "{ x -> x + 1 }"
    }

    "block comment inside type does not confuse scanner" {
        val src = "fun foo(a: Int /* inline */ = 5)"
        DefaultValueExtractor.extractFromWindow(src, "a") shouldBe "5"
    }

    "line comment after default does not eat the value" {
        val src = "fun foo(a: Int = 5, // trailing\n  b: Int = 6)"
        DefaultValueExtractor.extractFromWindow(src, "a") shouldBe "5"
        DefaultValueExtractor.extractFromWindow(src, "b") shouldBe "6"
    }

    "multi-line default expression" {
        val src = """
            fun complex(
                weights: List<Double> = listOf(
                    0.5,
                    0.3,
                    0.2,
                ),
            )
        """.trimIndent()
        val expected = "listOf(\n        0.5,\n        0.3,\n        0.2,\n    )"
        DefaultValueExtractor.extractFromWindow(src, "weights") shouldBe expected
    }

    "param without default — returns null" {
        val src = "fun f(x: Int)"
        DefaultValueExtractor.extractFromWindow(src, "x") shouldBe null
    }

    "param name not in window — returns null" {
        val src = "fun f(y: Int = 1)"
        DefaultValueExtractor.extractFromWindow(src, "x") shouldBe null
    }

    "glued generic before the `=` (`List<Int>= x`) — deliberately null, never misread" {
        // Distinguishing a glued generic close from a real `>=` comparison on
        // false-candidate scans proved unsafe (wrong runtime defaults via
        // safeDefaultThunk), so glued `>=` fail-softs to null. Formatted
        // source never glues these.
        val src = "fun a(gain: List<Int>= listOf(1))"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe null
    }

    "glued nested generic (`>>=`) — deliberately null" {
        val src = "fun a(gain: Map<String, List<Int>>= mapOf())"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe null
    }

    "real `>=` comparison in an earlier lambda default — stays compound, retry finds the real param" {
        // The `=` of `gain >= 0.5` must NOT be taken as a default marker: this
        // extraction would "succeed" with "0.5", which is literal-shaped and
        // would ship as a WRONG RUNTIME DEFAULT via safeDefaultThunk.
        val src = "fun f(pred: (Double) -> Boolean = { gain: Double -> gain >= 0.5 }, gain: Double = 1.0)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "`<` comparison before the `>=` in an earlier lambda default — still safe" {
        val src = "fun f(pred: (Double) -> Boolean = { gain: Double -> a < b && gain >= 0.5 }, gain: Double = 1.0)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "assignment with literal RHS in an earlier lambda default — brace dead-end, retry wins" {
        // Without the `}`-dead-end rule in findValueEnd this extracted "0.5" —
        // literal-shaped, i.e. a WRONG RUNTIME DEFAULT via safeDefaultThunk.
        val src = "fun f(cb: (Double) -> Unit = { gain: Double -> threshold = 0.5 }, gain: Double = 1.0)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "assignment with identifier RHS in an earlier lambda default — retry wins" {
        val src = "fun f(cb: (Int) -> Unit = { gain: Int -> acc = gain }, gain: Int = 2)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "2"
    }

    "typed `when (val …)` binding in an earlier default — pinned known residual" {
        // A typed subject binding is structurally indistinguishable from a real
        // parameter (its value legitimately ends at `)`), so this extracts the
        // binding's initializer. Accepted: a `val`-lookbehind fix would regress
        // constructor val-params, and the untyped common form `when (val gain = …)`
        // is already rejected by the `:` rule. Pinned so a change is conscious.
        val src = "fun f(x: Int = when (val gain: Int = 5) { else -> 0 }, gain: Int = 2)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "5"
    }

    "generic type arg with comma does not confuse scanner" {
        val src = "fun foo(map: Map<String, Int> = mutableMapOf())"
        DefaultValueExtractor.extractFromWindow(src, "map") shouldBe "mutableMapOf()"
    }

    "param named like its function — must skip the function name (MasterFx.gain bug)" {
        val src = "fun gain(gain: Double = 1.0): MasterStageDsl.Gain = MasterStageDsl.Gain(gain = gain)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "same-name param must not swallow the expression body and following declarations" {
        val src = """
            fun gain(gain: Double = 1.0): Gain = Gain(gain = gain)

            @KlangScript.Method
            fun limiter(): Limiter = Limiter()
        """.trimIndent()
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "named argument with same name in earlier default — must not match" {
        val src = "fun f(a: X = g(gain = 1), gain: Int = 2)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "2"
    }

    "method reference with same name in earlier default — must not match" {
        val src = "fun f(x: T = gain::prop, gain: Int = 3)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "3"
    }

    "same name inside a function TYPE — dead-end candidate is retried" {
        val src = "fun on(handler: (gain: Double) -> Unit = {}, gain: Double = 1.0)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "1.0"
    }

    "empty param name — returns null instead of spinning" {
        DefaultValueExtractor.extractFromWindow("fun f(x : Int)", "") shouldBe null
    }

    "dead-end candidate retries into a later declaration — pinned tradeoff" {
        // Production is double-guarded against this (extract() requires hasDefault
        // and the window starts at the param's own line), so realistically this
        // drift fires only when KSP metadata disagrees with the source or the
        // compound-`=` heuristic misreads an exotic corner. Pinned so a future
        // refactor changes the behavior consciously, not by accident.
        val src = "fun a(gain: Int)\nfun b(gain: Int = 7)"
        DefaultValueExtractor.extractFromWindow(src, "gain") shouldBe "7"
    }

    "param name appears in earlier KDoc — must not match" {
        val src = """
            /** @param cutoff The cutoff in Hz */
            fun filter(cutoff: Double = 1000.0)
        """.trimIndent()
        // Scanner must see the `cutoff:` declaration, not the KDoc reference (which is in a comment).
        DefaultValueExtractor.extractFromWindow(src, "cutoff") shouldBe "1000.0"
    }

    // The extractor itself just returns whatever's after `=` — it doesn't decide
    // whether the text is safe to paste. That decision lives in the KSP processor's
    // `safeDefaultThunk` / `isSafeLiteralForThunk`. We round-trip both here so a
    // future refactor doesn't accidentally let unsafe text into the generated code.

    "extractor returns dotted reference verbatim (caller decides safety)" {
        val src = "fun sine(freq: IgnitorDslLike = IgnitorDsl.Freq)"
        DefaultValueExtractor.extractFromWindow(src, "freq") shouldBe "IgnitorDsl.Freq"
    }

    "extractor returns 'this'-referencing default (caller must skip)" {
        val src = "fun bar(x: Int = this.fallback)"
        DefaultValueExtractor.extractFromWindow(src, "x") shouldBe "this.fallback"
    }
})
