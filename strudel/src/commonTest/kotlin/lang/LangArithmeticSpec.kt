package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue

/**
 * Combined tests for arithmetic operations: add, sub, mul, div, mod
 */
class LangArithmeticSpec : StringSpec({

    // ========== add() tests ==========

    "add() adds amount to numeric values" {
        // seq("0 1") -> value=0, value=1
        // add("2") -> value=2, value=3
        val p = seq("0 1").add("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "add() sets event value as Rational (not Double)" {
        val p = seq("0 1").add("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        // Verify that the value is stored as Num (Rational), not Text (Double converted to string)
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        // Verify the underlying Rational value
        val value0 = events[0].data.value as StrudelVoiceValue.Num
        val value1 = events[1].data.value as StrudelVoiceValue.Num
        value0.value.toInt() shouldBe 2
        value1.value.toInt() shouldBe 3
    }

    "add() works as string extension" {
        // "0 1".add("2") -> value=2, value=3
        val p = "0 1".add("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "add() works as top-level function " {
        val p = add("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "add() works with scale logic when placed before scale" {
        // seq("0 2").add("1").scale("C4:major")
        // seq("0 2") -> 0, 2
        // add("1") -> 1, 3
        // scale uses these values as degrees
        // 1 -> D4, 3 -> F4
        val p = seq("0 2").add("1").scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBe "D4"
        events[1].data.note shouldBe "F4"
    }

    "add() works in compiled code" {
        val p = StrudelPattern.compile("""seq("0 1").add("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    // ========== sub() tests ==========

    "sub() subtracts amount from numeric values" {
        val p = seq("10 20").sub("5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 15
    }

    "sub() sets event value as Rational (not Double)" {
        val p = seq("10 20").sub("5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        val value0 = events[0].data.value as StrudelVoiceValue.Num
        val value1 = events[1].data.value as StrudelVoiceValue.Num
        value0.value.toInt() shouldBe 5
        value1.value.toInt() shouldBe 15
    }

    "sub() works as top-level function" {
        val p = sub("5")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "sub() works as string extension" {
        val p = "10 20".sub("5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 15
    }

    // ========== mul() tests ==========

    "mul() multiplies numeric values" {
        val p = seq("2 3").mul("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }

    "mul() sets event value as Rational (not Double)" {
        val p = seq("2 3").mul("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        val value0 = events[0].data.value as StrudelVoiceValue.Num
        val value1 = events[1].data.value as StrudelVoiceValue.Num
        value0.value.toInt() shouldBe 8
        value1.value.toInt() shouldBe 12
    }

    "mul() works as top-level function" {
        val p = mul("4")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "mul() works as string extension" {
        val p = "2 3".mul("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }

    // ========== div() tests ==========

    "div() divides numeric values" {
        val p = seq("10 20").div("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }

    "div() sets event value as Rational (not Double)" {
        val p = seq("10 20").div("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        val value0 = events[0].data.value as StrudelVoiceValue.Num
        val value1 = events[1].data.value as StrudelVoiceValue.Num
        value0.value.toInt() shouldBe 5
        value1.value.toInt() shouldBe 10
    }

    "div() works as top-level function" {
        val p = div("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "div() works as string extension" {
        val p = "10 20".div("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }

    // ========== mod() tests ==========

    "mod() calculates modulo" {
        val p = seq("10 11").mod("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    "mod() sets event value as Rational (not Double)" {
        val p = seq("10 11").mod("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        val value0 = events[0].data.value as StrudelVoiceValue.Num
        val value1 = events[1].data.value as StrudelVoiceValue.Num
        value0.value.toInt() shouldBe 1
        value1.value.toInt() shouldBe 2
    }

    "mod() works as top-level function" {
        val p = mod("3")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "mod() works as string extension" {
        val p = "10 11".mod("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    // ========== Combined arithmetic tests ==========

    "Arithmetic operations can be chained" {
        // (10 + 5) * 2 - 3 = 27
        val p = seq("10").add("5").mul("2").sub("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asInt shouldBe 27
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
    }

    "Arithmetic operations preserve Rational type through chain" {
        val p = seq("2").add("3").mul("4").div("2").sub("1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        // ((2 + 3) * 4) / 2 - 1 = (20 / 2) - 1 = 9
        events[0].data.value?.asInt shouldBe 9

        // Verify it's still Rational
        val value = events[0].data.value
        value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        (value as StrudelVoiceValue.Num).value.toInt() shouldBe 9
    }

    "Arithmetic with fractional results uses Rational precision" {
        // 10 / 3 = 3.333... (stored as Rational)
        val p = seq("10").div("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()

        val value = events[0].data.value as StrudelVoiceValue.Num
        // Check that it's approximately 3.333
        val doubleValue = value.value.toDouble()
        doubleValue shouldBe (3.333333 plusOrMinus 1e-5)
    }

    // ========== pow() tests ==========

    "pow() raises values to power" {
        val p = seq("2 3").pow("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8  // 2^3
        events[1].data.value?.asInt shouldBe 27 // 3^3
    }

    "pow() sets event value as Rational" {
        val p = seq("2 3").pow("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        events[1].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
    }

    // ========== Bitwise operations tests ==========

    "band() performs bitwise AND" {
        val p = seq("12 15").band("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8  // 12 & 10 = 8
        events[1].data.value?.asInt shouldBe 10 // 15 & 10 = 10
    }

    "bor() performs bitwise OR" {
        val p = seq("8 4").bor("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 8 | 2 = 10
        events[1].data.value?.asInt shouldBe 6  // 4 | 2 = 6
    }

    "bxor() performs bitwise XOR" {
        val p = seq("12 10").bxor("6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 12 ^ 6 = 10
        events[1].data.value?.asInt shouldBe 12 // 10 ^ 6 = 12
    }

    "blshift() performs bitwise left shift" {
        val p = seq("1 2").blshift("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 4 // 1 << 2 = 4
        events[1].data.value?.asInt shouldBe 8 // 2 << 2 = 8
    }

    "brshift() performs bitwise right shift" {
        val p = seq("8 12").brshift("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2 // 8 >> 2 = 2
        events[1].data.value?.asInt shouldBe 3 // 12 >> 2 = 3
    }

    // ========== log2() tests ==========

    "log2() calculates logarithm base 2" {
        val p = seq("8 16").log2()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus 1e-5) // log2(8) = 3
        events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus 1e-5) // log2(16) = 4
    }

    // ========== Comparison operations tests ==========

    "lt() performs less than comparison" {
        val p = seq("5 10").lt("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 5 < 8 = true (1)
        events[1].data.value?.asInt shouldBe 0 // 10 < 8 = false (0)
    }

    "gt() performs greater than comparison" {
        val p = seq("5 10").gt("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 5 > 8 = false (0)
        events[1].data.value?.asInt shouldBe 1 // 10 > 8 = true (1)
    }

    "lte() performs less than or equal comparison" {
        val p = seq("5 8 10").lte("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1 // 5 <= 8 = true
        events[1].data.value?.asInt shouldBe 1 // 8 <= 8 = true
        events[2].data.value?.asInt shouldBe 0 // 10 <= 8 = false
    }

    "gte() performs greater than or equal comparison" {
        val p = seq("5 8 10").gte("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // 5 >= 8 = false
        events[1].data.value?.asInt shouldBe 1 // 8 >= 8 = true
        events[2].data.value?.asInt shouldBe 1 // 10 >= 8 = true
    }

    // ========== Equality operations tests ==========

    "eq() performs equality comparison" {
        val p = seq("5 8").eq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 5 == 8 = false
        events[1].data.value?.asInt shouldBe 1 // 8 == 8 = true
    }

    "ne() performs inequality comparison" {
        val p = seq("5 8").ne("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 5 != 8 = true
        events[1].data.value?.asInt shouldBe 0 // 8 != 8 = false
    }

    "eqt() performs truthiness equality" {
        val p = seq("0 5").eqt("0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 0 (falsy) == 0 (falsy) = true
        events[1].data.value?.asInt shouldBe 0 // 5 (truthy) == 0 (falsy) = false
    }

    "net() performs truthiness inequality" {
        val p = seq("0 5").net("0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 0 (falsy) != 0 (falsy) = false
        events[1].data.value?.asInt shouldBe 1 // 5 (truthy) != 0 (falsy) = true
    }

    // ========== Logical operations tests ==========

    "and() performs logical AND" {
        val p = seq("0 5").and("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 0 && 10 = 0 (falsy)
        events[1].data.value?.asInt shouldBe 10 // 5 && 10 = 10 (truthy)
    }

    "or() performs logical OR" {
        val p = seq("0 5").or("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 0 || 10 = 10
        events[1].data.value?.asInt shouldBe 5  // 5 || 10 = 5
    }

    // ========== Rounding operations tests ==========

    "round() rounds to nearest integer" {
        val p = seq("2.4 2.5 2.6").round()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 2 // 2.4 rounds to 2
        events[1].data.value?.asInt shouldBe 3 // 2.5 rounds to 3
        events[2].data.value?.asInt shouldBe 3 // 2.6 rounds to 3
    }

    "floor() rounds down to integer" {
        val p = seq("2.1 2.9 -2.1").floor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 2  // floor(2.1) = 2
        events[1].data.value?.asInt shouldBe 2  // floor(2.9) = 2
        events[2].data.value?.asInt shouldBe -3 // floor(-2.1) = -3
    }

    "ceil() rounds up to integer" {
        val p = seq("2.1 2.9 -2.9").ceil()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 3  // ceil(2.1) = 3
        events[1].data.value?.asInt shouldBe 3  // ceil(2.9) = 3
        events[2].data.value?.asInt shouldBe -2 // ceil(-2.9) = -2
    }

    "Rounding operations preserve Rational type" {
        val p = seq("2.5").round()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        (events[0].data.value as StrudelVoiceValue.Num).value.toInt() shouldBe 3
    }
})