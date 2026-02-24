package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.dslInterfaceTests

/**
 * Combined tests for arithmetic operations: add, sub, mul, div, mod
 */
class LangArithmeticSpec : StringSpec({

    // Combining multiple arithmatic operations

    "apply(add().mul())" {
        val p = seq("1 2").apply(add("1").mul("3"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 6  // (1+1)*3=6
            events[1].data.value?.asInt shouldBe 9  // (2+1)*3=9
        }
    }

    "script apply(add().mul())" {
        val p = StrudelPattern.compile("""seq("1 2").apply(add("1").mul("3"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 6  // (1+1)*3=6
            events[1].data.value?.asInt shouldBe 9  // (2+1)*3=9
        }
    }

    "apply(mul().add())" {
        val p = seq("1 2").apply(mul("3").add("4"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 7  // 1 * 3 + 4
            events[1].data.value?.asInt shouldBe 10 // 2 * 3 + 4
        }
    }

    "script apply(mul().add())" {
        val p = StrudelPattern.compile("""seq("1 2").apply(mul("3").add("4"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 7  // 1 * 3 + 4
            events[1].data.value?.asInt shouldBe 10 // 2 * 3 + 4
        }
    }

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

    "add() works as top-level PatternMapper" {
        val p = seq("0 1").apply(add("2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "add dsl interface" {
        val pat = "0 1"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.add(ctrl)" to
                    seq(pat).add(ctrl),
            "script pattern.add(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").add("$ctrl")"""),
            "string.add(ctrl)" to
                    pat.add(ctrl),
            "script string.add(ctrl)" to
                    StrudelPattern.compile(""""$pat".add("$ctrl")"""),
            "add(ctrl)" to
                    seq(pat).apply(add(ctrl)),
            "script add(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(add("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 2  // 0 + 2 = 2
            events[1].data.value?.asInt shouldBe 4  // 1 + 3 = 4
        }
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

    "sub() works as top-level PatternMapper" {
        val p = seq("10 20").apply(sub("5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 15
    }

    "sub dsl interface" {
        val pat = "10 20"
        val ctrl = "3 7"

        dslInterfaceTests(
            "pattern.sub(ctrl)" to
                    seq(pat).sub(ctrl),
            "script pattern.sub(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").sub("$ctrl")"""),
            "string.sub(ctrl)" to
                    pat.sub(ctrl),
            "script string.sub(ctrl)" to
                    StrudelPattern.compile(""""$pat".sub("$ctrl")"""),
            "sub(ctrl)" to
                    seq(pat).apply(sub(ctrl)),
            "script sub(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(sub("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 7   // 10 - 3 = 7
            events[1].data.value?.asInt shouldBe 13  // 20 - 7 = 13
        }
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

    "mul() works as top-level PatternMapper" {
        val p = seq("2 3").apply(mul("4"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }

    "mul dsl interface" {
        val pat = "2 3"
        val ctrl = "4 5"

        dslInterfaceTests(
            "pattern.mul(ctrl)" to
                    seq(pat).mul(ctrl),
            "script pattern.mul(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").mul("$ctrl")"""),
            "string.mul(ctrl)" to
                    pat.mul(ctrl),
            "script string.mul(ctrl)" to
                    StrudelPattern.compile(""""$pat".mul("$ctrl")"""),
            "mul(ctrl)" to
                    seq(pat).apply(mul(ctrl)),
            "script mul(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(mul("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 8   // 2 * 4 = 8
            events[1].data.value?.asInt shouldBe 15  // 3 * 5 = 15
        }
    }

    "mul() works as string extension" {
        val p = "2 3".mul("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }

    "apply(mul().div())" {
        val p = seq("10 20").apply(mul("2").div("4"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 5   // (10*2)/4=5
            events[1].data.value?.asInt shouldBe 10  // (20*2)/4=10
        }
    }

    "script apply(mul().div())" {
        val p = StrudelPattern.compile("""seq("10 20").apply(mul("2").div("4"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 5   // (10*2)/4=5
            events[1].data.value?.asInt shouldBe 10  // (20*2)/4=10
        }
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

    "div() works as top-level PatternMapper" {
        val p = seq("10 20").apply(div("2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }

    "div dsl interface" {
        val pat = "10 20"
        val ctrl = "2 4"

        dslInterfaceTests(
            "pattern.div(ctrl)" to
                    seq(pat).div(ctrl),
            "script pattern.div(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").div("$ctrl")"""),
            "string.div(ctrl)" to
                    pat.div(ctrl),
            "script string.div(ctrl)" to
                    StrudelPattern.compile(""""$pat".div("$ctrl")"""),
            "div(ctrl)" to
                    seq(pat).apply(div(ctrl)),
            "script div(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(div("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 5   // 10 / 2 = 5
            events[1].data.value?.asInt shouldBe 5   // 20 / 4 = 5
        }
    }

    "div() works as string extension" {
        val p = "10 20".div("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }

    "apply(add().mod())" {
        val p = seq("10 11").apply(add("1").mod("4"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 3  // (10+1)%4=3
            events[1].data.value?.asInt shouldBe 0  // (11+1)%4=0
        }
    }

    "script apply(add().mod())" {
        val p = StrudelPattern.compile("""seq("10 11").apply(add("1").mod("4"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 3  // (10+1)%4=3
            events[1].data.value?.asInt shouldBe 0  // (11+1)%4=0
        }
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

    "mod() works as top-level PatternMapper" {
        val p = seq("10 11").apply(mod("3"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    "mod() by zero silences the event" {
        val p = seq("10 11").mod("0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value shouldBe null
        events[1].data.value shouldBe null
    }

    "mod dsl interface" {
        val pat = "10 11"
        val ctrl = "3 4"

        dslInterfaceTests(
            "pattern.mod(ctrl)" to
                    seq(pat).mod(ctrl),
            "script pattern.mod(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").mod("$ctrl")"""),
            "string.mod(ctrl)" to
                    pat.mod(ctrl),
            "script string.mod(ctrl)" to
                    StrudelPattern.compile(""""$pat".mod("$ctrl")"""),
            "mod(ctrl)" to
                    seq(pat).apply(mod(ctrl)),
            "script mod(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(mod("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 1  // 10 % 3 = 1
            events[1].data.value?.asInt shouldBe 3  // 11 % 4 = 3
        }
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

    "apply(add().pow())" {
        val p = seq("2 3").apply(add("1").pow("2"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 9   // (2+1)^2=9
            events[1].data.value?.asInt shouldBe 16  // (3+1)^2=16
        }
    }

    "script apply(add().pow())" {
        val p = StrudelPattern.compile("""seq("2 3").apply(add("1").pow("2"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 9   // (2+1)^2=9
            events[1].data.value?.asInt shouldBe 16  // (3+1)^2=16
        }
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

    "pow() works as top-level PatternMapper" {
        val p = seq("2 3").apply(pow("3"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8   // 2^3
        events[1].data.value?.asInt shouldBe 27  // 3^3
    }

    "pow dsl interface" {
        val pat = "2 3"
        val ctrl = "3 2"

        dslInterfaceTests(
            "pattern.pow(ctrl)" to
                    seq(pat).pow(ctrl),
            "script pattern.pow(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").pow("$ctrl")"""),
            "string.pow(ctrl)" to
                    pat.pow(ctrl),
            "script string.pow(ctrl)" to
                    StrudelPattern.compile(""""$pat".pow("$ctrl")"""),
            "pow(ctrl)" to
                    seq(pat).apply(pow(ctrl)),
            "script pow(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(pow("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 8  // 2^3 = 8
            events[1].data.value?.asInt shouldBe 9  // 3^2 = 9
        }
    }

    "apply(add().band())" {
        val p = seq("12 15").apply(add("3").band("10"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 10  // (12+3)=15 & 10=10
            events[1].data.value?.asInt shouldBe 2   // (15+3)=18 & 10=2
        }
    }

    "script apply(add().band())" {
        val p = StrudelPattern.compile("""seq("12 15").apply(add("3").band("10"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 10  // (12+3)=15 & 10=10
            events[1].data.value?.asInt shouldBe 2   // (15+3)=18 & 10=2
        }
    }

    // ========== Bitwise operations tests ==========

    "band() performs bitwise AND" {
        val p = seq("12 15").band("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8  // 12 & 10 = 8
        events[1].data.value?.asInt shouldBe 10 // 15 & 10 = 10
    }

    "apply(add().bor())" {
        val p = seq("8 4").apply(add("1").bor("2"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 11  // (8+1)=9 | 2=11
            events[1].data.value?.asInt shouldBe 7   // (4+1)=5 | 2=7
        }
    }

    "script apply(add().bor())" {
        val p = StrudelPattern.compile("""seq("8 4").apply(add("1").bor("2"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 11  // (8+1)=9 | 2=11
            events[1].data.value?.asInt shouldBe 7   // (4+1)=5 | 2=7
        }
    }

    "bor() performs bitwise OR" {
        val p = seq("8 4").bor("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 8 | 2 = 10
        events[1].data.value?.asInt shouldBe 6  // 4 | 2 = 6
    }

    "apply(add().bxor())" {
        val p = seq("12 10").apply(add("2").bxor("6"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 8   // (12+2)=14 ^ 6=8
            events[1].data.value?.asInt shouldBe 10  // (10+2)=12 ^ 6=10
        }
    }

    "script apply(add().bxor())" {
        val p = StrudelPattern.compile("""seq("12 10").apply(add("2").bxor("6"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 8   // (12+2)=14 ^ 6=8
            events[1].data.value?.asInt shouldBe 10  // (10+2)=12 ^ 6=10
        }
    }

    "bxor() performs bitwise XOR" {
        val p = seq("12 10").bxor("6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 12 ^ 6 = 10
        events[1].data.value?.asInt shouldBe 12 // 10 ^ 6 = 12
    }

    "apply(add().blshift())" {
        val p = seq("1 2").apply(add("1").blshift("2"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 8   // (1+1)=2 << 2=8
            events[1].data.value?.asInt shouldBe 12  // (2+1)=3 << 2=12
        }
    }

    "script apply(add().blshift())" {
        val p = StrudelPattern.compile("""seq("1 2").apply(add("1").blshift("2"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 8   // (1+1)=2 << 2=8
            events[1].data.value?.asInt shouldBe 12  // (2+1)=3 << 2=12
        }
    }

    "blshift() performs bitwise left shift" {
        val p = seq("1 2").blshift("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 4 // 1 << 2 = 4
        events[1].data.value?.asInt shouldBe 8 // 2 << 2 = 8
    }

    "apply(mul().brshift())" {
        val p = seq("8 16").apply(mul("2").brshift("3"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 2  // (8*2)=16 >> 3=2
            events[1].data.value?.asInt shouldBe 4  // (16*2)=32 >> 3=4
        }
    }

    "script apply(mul().brshift())" {
        val p = StrudelPattern.compile("""seq("8 16").apply(mul("2").brshift("3"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 2  // (8*2)=16 >> 3=2
            events[1].data.value?.asInt shouldBe 4  // (16*2)=32 >> 3=4
        }
    }

    "brshift() performs bitwise right shift" {
        val p = seq("8 12").brshift("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2 // 8 >> 2 = 2
        events[1].data.value?.asInt shouldBe 3 // 12 >> 2 = 3
    }

    "apply(mul().log2())" {
        val p = seq("2 4").apply(mul("4").log2())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus 1e-5)  // log2(2*4)=log2(8)=3
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus 1e-5)  // log2(4*4)=log2(16)=4
        }
    }

    "script apply(mul().log2())" {
        val p = StrudelPattern.compile("""seq("2 4").apply(mul("4").log2())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus 1e-5)  // log2(2*4)=log2(8)=3
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus 1e-5)  // log2(4*4)=log2(16)=4
        }
    }

    // ========== log2() tests ==========

    "log2() calculates logarithm base 2" {
        val p = seq("8 16").log2()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus 1e-5) // log2(8) = 3
        events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus 1e-5) // log2(16) = 4
    }

    "apply(add().lt())" {
        val p = seq("5 10").apply(add("3").lt("9"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 < 9=true
            events[1].data.value?.asInt shouldBe 0  // (10+3)=13 < 9=false
        }
    }

    "script apply(add().lt())" {
        val p = StrudelPattern.compile("""seq("5 10").apply(add("3").lt("9"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 < 9=true
            events[1].data.value?.asInt shouldBe 0  // (10+3)=13 < 9=false
        }
    }

    // ========== Comparison operations tests ==========

    "lt() performs less than comparison" {
        val p = seq("5 10").lt("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 5 < 8 = true (1)
        events[1].data.value?.asInt shouldBe 0 // 10 < 8 = false (0)
    }

    "apply(add().gt())" {
        val p = seq("5 10").apply(add("3").gt("9"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 > 9=false
            events[1].data.value?.asInt shouldBe 1  // (10+3)=13 > 9=true
        }
    }

    "script apply(add().gt())" {
        val p = StrudelPattern.compile("""seq("5 10").apply(add("3").gt("9"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 > 9=false
            events[1].data.value?.asInt shouldBe 1  // (10+3)=13 > 9=true
        }
    }

    "gt() performs greater than comparison" {
        val p = seq("5 10").gt("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 5 > 8 = false (0)
        events[1].data.value?.asInt shouldBe 1 // 10 > 8 = true (1)
    }

    "apply(add().lte())" {
        val p = seq("5 10").apply(add("3").lte("11"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 <= 11=true
            events[1].data.value?.asInt shouldBe 0  // (10+3)=13 <= 11=false
        }
    }

    "script apply(add().lte())" {
        val p = StrudelPattern.compile("""seq("5 10").apply(add("3").lte("11"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 <= 11=true
            events[1].data.value?.asInt shouldBe 0  // (10+3)=13 <= 11=false
        }
    }

    "lte() performs less than or equal comparison" {
        val p = seq("5 8 10").lte("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1 // 5 <= 8 = true
        events[1].data.value?.asInt shouldBe 1 // 8 <= 8 = true
        events[2].data.value?.asInt shouldBe 0 // 10 <= 8 = false
    }

    "apply(add().gte())" {
        val p = seq("5 10").apply(add("3").gte("11"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 >= 11=false
            events[1].data.value?.asInt shouldBe 1  // (10+3)=13 >= 11=true
        }
    }

    "script apply(add().gte())" {
        val p = StrudelPattern.compile("""seq("5 10").apply(add("3").gte("11"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 >= 11=false
            events[1].data.value?.asInt shouldBe 1  // (10+3)=13 >= 11=true
        }
    }

    "gte() performs greater than or equal comparison" {
        val p = seq("5 8 10").gte("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // 5 >= 8 = false
        events[1].data.value?.asInt shouldBe 1 // 8 >= 8 = true
        events[2].data.value?.asInt shouldBe 1 // 10 >= 8 = true
    }

    "apply(add().eq())" {
        val p = seq("5 8").apply(add("3").eq("11"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 == 11=false
            events[1].data.value?.asInt shouldBe 1  // (8+3)=11 == 11=true
        }
    }

    "script apply(add().eq())" {
        val p = StrudelPattern.compile("""seq("5 8").apply(add("3").eq("11"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (5+3)=8 == 11=false
            events[1].data.value?.asInt shouldBe 1  // (8+3)=11 == 11=true
        }
    }

    // ========== Equality operations tests ==========

    "eq() performs equality comparison" {
        val p = seq("5 8").eq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 5 == 8 = false
        events[1].data.value?.asInt shouldBe 1 // 8 == 8 = true
    }

    "apply(add().ne())" {
        val p = seq("5 8").apply(add("3").ne("11"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 != 11=true
            events[1].data.value?.asInt shouldBe 0  // (8+3)=11 != 11=false
        }
    }

    "script apply(add().ne())" {
        val p = StrudelPattern.compile("""seq("5 8").apply(add("3").ne("11"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (5+3)=8 != 11=true
            events[1].data.value?.asInt shouldBe 0  // (8+3)=11 != 11=false
        }
    }

    "ne() performs inequality comparison" {
        val p = seq("5 8").ne("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 5 != 8 = true
        events[1].data.value?.asInt shouldBe 0 // 8 != 8 = false
    }

    "apply(mul().eqt())" {
        val p = seq("0 5").apply(mul("3").eqt("0"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (0*3)=0 ~= 0 (both falsy)=true
            events[1].data.value?.asInt shouldBe 0  // (5*3)=15 ~= 0 (truthy vs falsy)=false
        }
    }

    "script apply(mul().eqt())" {
        val p = StrudelPattern.compile("""seq("0 5").apply(mul("3").eqt("0"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 1  // (0*3)=0 ~= 0 (both falsy)=true
            events[1].data.value?.asInt shouldBe 0  // (5*3)=15 ~= 0 (truthy vs falsy)=false
        }
    }

    "eqt() performs truthiness equality" {
        val p = seq("0 5").eqt("0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 0 (falsy) == 0 (falsy) = true
        events[1].data.value?.asInt shouldBe 0 // 5 (truthy) == 0 (falsy) = false
    }

    "apply(mul().net())" {
        val p = seq("0 5").apply(mul("3").net("0"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (0*3)=0 ~!= 0 (both falsy)=false
            events[1].data.value?.asInt shouldBe 1  // (5*3)=15 ~!= 0 (truthy vs falsy)=true
        }
    }

    "script apply(mul().net())" {
        val p = StrudelPattern.compile("""seq("0 5").apply(mul("3").net("0"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (0*3)=0 ~!= 0 (both falsy)=false
            events[1].data.value?.asInt shouldBe 1  // (5*3)=15 ~!= 0 (truthy vs falsy)=true
        }
    }

    "net() performs truthiness inequality" {
        val p = seq("0 5").net("0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0 // 0 (falsy) != 0 (falsy) = false
        events[1].data.value?.asInt shouldBe 1 // 5 (truthy) != 0 (falsy) = true
    }

    "apply(sub().and())" {
        val p = seq("1 5").apply(sub("1").and("7"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (1-1)=0 && 7=0 (falsy)
            events[1].data.value?.asInt shouldBe 7  // (5-1)=4 && 7=7 (truthy)
        }
    }

    "script apply(sub().and())" {
        val p = StrudelPattern.compile("""seq("1 5").apply(sub("1").and("7"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 0  // (1-1)=0 && 7=0 (falsy)
            events[1].data.value?.asInt shouldBe 7  // (5-1)=4 && 7=7 (truthy)
        }
    }

    // ========== Logical operations tests ==========

    "and() performs logical AND" {
        val p = seq("0 5").and("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 0 && 10 = 0 (falsy)
        events[1].data.value?.asInt shouldBe 10 // 5 && 10 = 10 (truthy)
    }

    "apply(mul().or())" {
        val p = seq("0 5").apply(mul("1").or("7"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 7  // (0*1)=0 || 7=7
            events[1].data.value?.asInt shouldBe 5  // (5*1)=5 || 7=5 (truthy)
        }
    }

    "script apply(mul().or())" {
        val p = StrudelPattern.compile("""seq("0 5").apply(mul("1").or("7"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 7  // (0*1)=0 || 7=7
            events[1].data.value?.asInt shouldBe 5  // (5*1)=5 || 7=5 (truthy)
        }
    }

    "or() performs logical OR" {
        val p = seq("0 5").or("10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10 // 0 || 10 = 10
        events[1].data.value?.asInt shouldBe 5  // 5 || 10 = 5
    }

    "apply(mul().round())" {
        val p = seq("2.1 3.7").apply(mul("2").round())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 4  // round(2.1*2)=round(4.2)=4
            events[1].data.value?.asInt shouldBe 7  // round(3.7*2)=round(7.4)=7
        }
    }

    "script apply(mul().round())" {
        val p = StrudelPattern.compile("""seq("2.1 3.7").apply(mul("2").round())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 4  // round(2.1*2)=round(4.2)=4
            events[1].data.value?.asInt shouldBe 7  // round(3.7*2)=round(7.4)=7
        }
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

    "round() works as top-level PatternMapper" {
        val p = seq("2.4 2.6").apply(round())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2  // round(2.4) = 2
        events[1].data.value?.asInt shouldBe 3  // round(2.6) = 3
    }

    "round() works as string extension" {
        val p = "2.4 2.6".round()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "round dsl interface" {
        val pat = "2.4 2.6"

        dslInterfaceTests(
            "pattern.round()" to seq(pat).round(),
            "script pattern.round()" to StrudelPattern.compile("""seq("$pat").round()"""),
            "string.round()" to pat.round(),
            "script string.round()" to StrudelPattern.compile(""""$pat".round()"""),
            "round()" to seq(pat).apply(round()),
            "script round()" to StrudelPattern.compile("""seq("$pat").apply(round())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 2  // round(2.4) = 2
            events[1].data.value?.asInt shouldBe 3  // round(2.6) = 3
        }
    }

    "apply(mul().floor())" {
        val p = seq("2.1 3.9").apply(mul("2").floor())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 4  // floor(2.1*2)=floor(4.2)=4
            events[1].data.value?.asInt shouldBe 7  // floor(3.9*2)=floor(7.8)=7
        }
    }

    "script apply(mul().floor())" {
        val p = StrudelPattern.compile("""seq("2.1 3.9").apply(mul("2").floor())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 4  // floor(2.1*2)=floor(4.2)=4
            events[1].data.value?.asInt shouldBe 7  // floor(3.9*2)=floor(7.8)=7
        }
    }

    "floor() rounds down to integer" {
        val p = seq("2.1 2.9 -2.1").floor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 2  // floor(2.1) = 2
        events[1].data.value?.asInt shouldBe 2  // floor(2.9) = 2
        events[2].data.value?.asInt shouldBe -3 // floor(-2.1) = -3
    }

    "floor() works as top-level PatternMapper" {
        val p = seq("2.1 2.9").apply(floor())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2  // floor(2.1) = 2
        events[1].data.value?.asInt shouldBe 2  // floor(2.9) = 2
    }

    "floor() works as string extension" {
        val p = "2.1 2.9".floor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 2
    }

    "floor dsl interface" {
        val pat = "2.1 2.9"

        dslInterfaceTests(
            "pattern.floor()" to seq(pat).floor(),
            "script pattern.floor()" to StrudelPattern.compile("""seq("$pat").floor()"""),
            "string.floor()" to pat.floor(),
            "script string.floor()" to StrudelPattern.compile(""""$pat".floor()"""),
            "floor()" to seq(pat).apply(floor()),
            "script floor()" to StrudelPattern.compile("""seq("$pat").apply(floor())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 2  // floor(2.1) = 2
            events[1].data.value?.asInt shouldBe 2  // floor(2.9) = 2
        }
    }

    "apply(mul().ceil())" {
        val p = seq("2.1 3.9").apply(mul("2").ceil())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 5  // ceil(2.1*2)=ceil(4.2)=5
            events[1].data.value?.asInt shouldBe 8  // ceil(3.9*2)=ceil(7.8)=8
        }
    }

    "script apply(mul().ceil())" {
        val p = StrudelPattern.compile("""seq("2.1 3.9").apply(mul("2").ceil())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asInt shouldBe 5  // ceil(2.1*2)=ceil(4.2)=5
            events[1].data.value?.asInt shouldBe 8  // ceil(3.9*2)=ceil(7.8)=8
        }
    }

    "ceil() rounds up to integer" {
        val p = seq("2.1 2.9 -2.9").ceil()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 3  // ceil(2.1) = 3
        events[1].data.value?.asInt shouldBe 3  // ceil(2.9) = 3
        events[2].data.value?.asInt shouldBe -2 // ceil(-2.9) = -2
    }

    "ceil() works as top-level PatternMapper" {
        val p = seq("2.1 2.9").apply(ceil())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3  // ceil(2.1) = 3
        events[1].data.value?.asInt shouldBe 3  // ceil(2.9) = 3
    }

    "ceil() works as string extension" {
        val p = "2.1 2.9".ceil()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 3
    }

    "ceil dsl interface" {
        val pat = "2.1 2.9"

        dslInterfaceTests(
            "pattern.ceil()" to seq(pat).ceil(),
            "script pattern.ceil()" to StrudelPattern.compile("""seq("$pat").ceil()"""),
            "string.ceil()" to pat.ceil(),
            "script string.ceil()" to StrudelPattern.compile(""""$pat".ceil()"""),
            "ceil()" to seq(pat).apply(ceil()),
            "script ceil()" to StrudelPattern.compile("""seq("$pat").apply(ceil())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 3  // ceil(2.1) = 3
            events[1].data.value?.asInt shouldBe 3  // ceil(2.9) = 3
        }
    }

    "Rounding operations preserve Rational type" {
        val p = seq("2.5").round()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value.shouldBeInstanceOf<StrudelVoiceValue.Num>()
        (events[0].data.value as StrudelVoiceValue.Num).value.toInt() shouldBe 3
    }
})
