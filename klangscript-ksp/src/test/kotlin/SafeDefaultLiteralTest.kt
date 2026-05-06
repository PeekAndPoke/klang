package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Coverage for [SafeDefaultLiteral.isSafe] — the gate that decides whether an
 * extracted Kotlin default expression is safe to paste into a generated thunk.
 */
class SafeDefaultLiteralTest : StringSpec({

    // ── Accepted: pure literals ──────────────────────────────────────────────

    "null literal" { SafeDefaultLiteral.isSafe("null") shouldBe true }
    "true literal" { SafeDefaultLiteral.isSafe("true") shouldBe true }
    "false literal" { SafeDefaultLiteral.isSafe("false") shouldBe true }

    "integer" { SafeDefaultLiteral.isSafe("42") shouldBe true }
    "negative integer" { SafeDefaultLiteral.isSafe("-7") shouldBe true }
    "double" { SafeDefaultLiteral.isSafe("1000.0") shouldBe true }
    "negative double" { SafeDefaultLiteral.isSafe("-3.14") shouldBe true }
    "double with exponent" { SafeDefaultLiteral.isSafe("1.5e3") shouldBe true }
    "double with negative exponent" { SafeDefaultLiteral.isSafe("2.0E-5") shouldBe true }
    "float suffix" { SafeDefaultLiteral.isSafe("0.5f") shouldBe true }
    "long suffix" { SafeDefaultLiteral.isSafe("1000L") shouldBe true }
    "leading dot" { SafeDefaultLiteral.isSafe(".5") shouldBe true }

    "string literal" { SafeDefaultLiteral.isSafe("\"hello\"") shouldBe true }
    "string with escape" { SafeDefaultLiteral.isSafe("\"line\\nbreak\"") shouldBe true }
    "string with escaped quote" { SafeDefaultLiteral.isSafe("\"say \\\"hi\\\"\"") shouldBe true }
    "empty string" { SafeDefaultLiteral.isSafe("\"\"") shouldBe true }
    "raw string" { SafeDefaultLiteral.isSafe("\"\"\"multi\nline\"\"\"") shouldBe true }

    "char literal" { SafeDefaultLiteral.isSafe("'a'") shouldBe true }
    "char escape" { SafeDefaultLiteral.isSafe("'\\n'") shouldBe true }

    // ── Whitespace tolerance ─────────────────────────────────────────────────

    "trims leading whitespace" { SafeDefaultLiteral.isSafe("  42") shouldBe true }
    "trims trailing whitespace" { SafeDefaultLiteral.isSafe("42  ") shouldBe true }

    // ── Rejected: anything that needs scope ──────────────────────────────────

    "qualified reference (the IgnitorDsl.Freq case)" {
        SafeDefaultLiteral.isSafe("IgnitorDsl.Freq") shouldBe false
    }
    "function call" { SafeDefaultLiteral.isSafe("computeDefault()") shouldBe false }
    "factory call" { SafeDefaultLiteral.isSafe("emptyList()") shouldBe false }
    "arithmetic on literals" { SafeDefaultLiteral.isSafe("2 * 1000.0") shouldBe false }
    "concatenated strings" { SafeDefaultLiteral.isSafe("\"foo\" + \"bar\"") shouldBe false }
    "string with trailing expression" { SafeDefaultLiteral.isSafe("\"foo\" + bar") shouldBe false }
    "lambda" { SafeDefaultLiteral.isSafe("{ 1.0 }") shouldBe false }
    "this reference" { SafeDefaultLiteral.isSafe("this.x") shouldBe false }
    "bare identifier" { SafeDefaultLiteral.isSafe("FOO") shouldBe false }
    "empty string input" { SafeDefaultLiteral.isSafe("") shouldBe false }
    "blank string input" { SafeDefaultLiteral.isSafe("   ") shouldBe false }

    // ── Kotlin number-literal extensions ────────────────────────────────────

    "hex literal" { SafeDefaultLiteral.isSafe("0xFF") shouldBe true }
    "hex literal uppercase" { SafeDefaultLiteral.isSafe("0XAB12") shouldBe true }
    "binary literal" { SafeDefaultLiteral.isSafe("0b1010") shouldBe true }
    "binary literal uppercase" { SafeDefaultLiteral.isSafe("0B110") shouldBe true }
    "underscore-separated number" { SafeDefaultLiteral.isSafe("1_000_000") shouldBe true }
    "underscore-separated double" { SafeDefaultLiteral.isSafe("1_000.0") shouldBe true }
    "hex with underscores" { SafeDefaultLiteral.isSafe("0xFF_EC_DE") shouldBe true }
    "unsigned suffix" { SafeDefaultLiteral.isSafe("42u") shouldBe true }

    // ── Edge cases that look like literals but aren't ────────────────────────

    "two adjacent triple strings (concat) is rejected" {
        SafeDefaultLiteral.isSafe("\"\"\"a\"\"\" + \"\"\"b\"\"\"") shouldBe false
    }
    "char literal too long" { SafeDefaultLiteral.isSafe("'ab'") shouldBe false }
    "number followed by identifier" { SafeDefaultLiteral.isSafe("42x") shouldBe false }
})
