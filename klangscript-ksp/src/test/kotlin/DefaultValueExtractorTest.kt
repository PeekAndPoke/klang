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

    "generic type arg with comma does not confuse scanner" {
        val src = "fun foo(map: Map<String, Int> = mutableMapOf())"
        DefaultValueExtractor.extractFromWindow(src, "map") shouldBe "mutableMapOf()"
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
