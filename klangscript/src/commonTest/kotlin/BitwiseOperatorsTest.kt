package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for bitwise operators, shift operators, hex/octal/binary literals,
 * nullish coalescing, optional chaining, and related compound assignments.
 */
class BitwiseOperatorsTest : StringSpec({

    // ── Bitwise AND ──

    "bitwise AND: 5 & 3 should be 1" {
        val engine = klangScript()
        val result = engine.execute("5 & 3")
        (result as NumberValue).value shouldBe 1.0
    }

    // ── Bitwise OR ──

    "bitwise OR: 5 | 3 should be 7" {
        val engine = klangScript()
        val result = engine.execute("5 | 3")
        (result as NumberValue).value shouldBe 7.0
    }

    // ── Bitwise XOR ──

    "bitwise XOR: 5 ^ 3 should be 6" {
        val engine = klangScript()
        val result = engine.execute("5 ^ 3")
        (result as NumberValue).value shouldBe 6.0
    }

    // ── Bitwise NOT ──

    "bitwise NOT: ~5 should be -6" {
        val engine = klangScript()
        val result = engine.execute("~5")
        (result as NumberValue).value shouldBe -6.0
    }

    // ── Shift left ──

    "shift left: 1 << 3 should be 8" {
        val engine = klangScript()
        val result = engine.execute("1 << 3")
        (result as NumberValue).value shouldBe 8.0
    }

    // ── Shift right ──

    "shift right: 16 >> 2 should be 4" {
        val engine = klangScript()
        val result = engine.execute("16 >> 2")
        (result as NumberValue).value shouldBe 4.0
    }

    // ── Unsigned shift right ──

    "unsigned shift right: -1 >>> 28 should be 15" {
        val engine = klangScript()
        val result = engine.execute("-1 >>> 28")
        (result as NumberValue).value shouldBe 15.0
    }

    // ── Hex literal ──

    "hex literal: 0xFF should be 255" {
        val engine = klangScript()
        val result = engine.execute("0xFF")
        (result as NumberValue).value shouldBe 255.0
    }

    "hex literal: 0XAB should be 171" {
        val engine = klangScript()
        val result = engine.execute("0XAB")
        (result as NumberValue).value shouldBe 171.0
    }

    // ── Octal literal ──

    "octal literal: 0o77 should be 63" {
        val engine = klangScript()
        val result = engine.execute("0o77")
        (result as NumberValue).value shouldBe 63.0
    }

    "octal literal: 0O10 should be 8" {
        val engine = klangScript()
        val result = engine.execute("0O10")
        (result as NumberValue).value shouldBe 8.0
    }

    // ── Binary literal ──

    "binary literal: 0b1010 should be 10" {
        val engine = klangScript()
        val result = engine.execute("0b1010")
        (result as NumberValue).value shouldBe 10.0
    }

    "binary literal: 0B11111111 should be 255" {
        val engine = klangScript()
        val result = engine.execute("0B11111111")
        (result as NumberValue).value shouldBe 255.0
    }

    // ── Nullish coalescing ──

    "nullish coalescing: null ?? 'default' should be 'default'" {
        val engine = klangScript()
        val result = engine.execute("""null ?? "default" """)
        (result as StringValue).value shouldBe "default"
    }

    "nullish coalescing: 'value' ?? 'default' should be 'value'" {
        val engine = klangScript()
        val result = engine.execute(""" "value" ?? "default" """)
        (result as StringValue).value shouldBe "value"
    }

    "nullish coalescing: 0 ?? 'default' should be 0 (0 is NOT null)" {
        val engine = klangScript()
        val result = engine.execute("""0 ?? "default" """)
        (result as NumberValue).value shouldBe 0.0
    }

    // ── Optional chaining ──

    "optional chaining: obj.a?.b should return property value" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let obj = { a: { b: 1 } }
            obj.a?.b
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
    }

    "optional chaining: obj.c?.d should return null (no error)" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let obj = { a: { b: 1 } }
            obj.c?.d
        """.trimIndent()
        )
        result shouldBe NullValue
    }

    // ── Compound assignment: bitwise ──

    "compound assignment: x &= 0x0F" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0xFF
            x &= 0x0F
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 15.0
    }

    "compound assignment: x |= 0xF0" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0x0F
            x |= 0xF0
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 255.0
    }

    "compound assignment: x ^= 0xFF" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0xFF
            x ^= 0xFF
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 0.0
    }

    // ── Compound assignment: shift ──

    "compound assignment: x <<= 4" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 1
            x <<= 4
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 16.0
    }

    "compound assignment: x >>= 2" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 16
            x >>= 2
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 4.0
    }

    // ── Compound assignment: exponent ──

    "compound assignment: x **= 10" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 2
            x **= 10
            x
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1024.0
    }
})
