package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue

class VoiceValueSpec : StringSpec({

    "VoiceValue.Num: 0.0 is falsy" {
        0.0.asVoiceValue().isTruthy() shouldBe false
        0.asVoiceValue()?.isTruthy() shouldBe false // Number converts to Num(0.0)
    }

    "VoiceValue.Num: non-zero is truthy" {
        1.0.asVoiceValue().isTruthy() shouldBe true
        (-1.0).asVoiceValue().isTruthy() shouldBe true
        0.00001.asVoiceValue().isTruthy() shouldBe true
    }

    "VoiceValue.Text: string '0' or '0.0' is falsy (parsed as number)" {
        "0".asVoiceValue().isTruthy() shouldBe false
        "0.0".asVoiceValue().isTruthy() shouldBe false
        "-0.0".asVoiceValue().isTruthy() shouldBe false
    }

    "VoiceValue.Text: numeric strings are truthy if non-zero" {
        "1".asVoiceValue().isTruthy() shouldBe true
        "0.1".asVoiceValue().isTruthy() shouldBe true
        "-5".asVoiceValue().isTruthy() shouldBe true
    }

    "VoiceValue.Text: 'false' and blank strings are falsy" {
        "false".asVoiceValue().isTruthy() shouldBe false
        "".asVoiceValue().isTruthy() shouldBe false
        "   ".asVoiceValue().isTruthy() shouldBe false
    }

    "VoiceValue.Text: arbitrary strings are truthy" {
        "true".asVoiceValue().isTruthy() shouldBe true
        "x".asVoiceValue().isTruthy() shouldBe true
        "foo".asVoiceValue().isTruthy() shouldBe true
    }

    "VoiceValue math operations: plus" {
        // Num + Double
        (10.asVoiceValue()!! + 5.0.asVoiceValue())?.asDouble shouldBe 15.0

        // Num + Num
        (10.asVoiceValue()!! + 5.asVoiceValue())?.asDouble shouldBe 15.0

        // Text(Numeric) + Double
        ("10".asVoiceValue() + 5.0.asVoiceValue())?.asDouble shouldBe 15.0

        // Text + Text (concatenation)
        ("a".asVoiceValue() + "b".asVoiceValue())?.asString shouldBe "ab"
    }

    "VoiceValue math operations: minus" {
        (10.asVoiceValue()!! - 5.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue() - "5".asVoiceValue())?.asDouble shouldBe 5.0
    }

    "VoiceValue math operations: times" {
        (10.asVoiceValue()!! * 5.asVoiceValue())?.asDouble shouldBe 50.0
        ("10".asVoiceValue() * "5".asVoiceValue())?.asDouble shouldBe 50.0
    }

    "VoiceValue math operations: div" {
        (10.asVoiceValue()!! / 2.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue() / "2".asVoiceValue())?.asDouble shouldBe 5.0
        (10.asVoiceValue()!! / 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: rem (mod)" {
        (10.asVoiceValue()!! % 3.asVoiceValue())?.asDouble shouldBe 1.0
        ("10".asVoiceValue() % "3".asVoiceValue())?.asDouble shouldBe 1.0
        (10.asVoiceValue()!! % 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: pow" {
        (2.asVoiceValue()!! pow 3.asVoiceValue())?.asDouble shouldBe 8.0
        ("2".asVoiceValue() pow "3".asVoiceValue())?.asDouble shouldBe 8.0
    }

    "VoiceValue bitwise operations: band" {
        (3.asVoiceValue()!! band 1.asVoiceValue())?.asInt shouldBe 1
        (3.asVoiceValue()!! band 0.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue bitwise operations: bor" {
        (1.asVoiceValue()!! bor 2.asVoiceValue())?.asInt shouldBe 3
        (1.asVoiceValue()!! bor 0.asVoiceValue())?.asInt shouldBe 1
    }

    "VoiceValue bitwise operations: bxor" {
        (3.asVoiceValue()!! bxor 1.asVoiceValue())?.asInt shouldBe 2
        (3.asVoiceValue()!! bxor 3.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue bitwise operations: shl" {
        (1.asVoiceValue()!! shl 1.asVoiceValue())?.asInt shouldBe 2
        (1.asVoiceValue()!! shl 2.asVoiceValue())?.asInt shouldBe 4
    }

    "VoiceValue bitwise operations: shr" {
        (2.asVoiceValue()!! shr 1.asVoiceValue())?.asInt shouldBe 1
        (4.asVoiceValue()!! shr 2.asVoiceValue())?.asInt shouldBe 1
    }

    "VoiceValue comparison operations: lt" {
        (1.asVoiceValue()!! lt 2.asVoiceValue())?.asInt shouldBe 1
        (2.asVoiceValue()!! lt 1.asVoiceValue())?.asInt shouldBe 0
        (1.asVoiceValue()!! lt 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: gt" {
        (2.asVoiceValue()!! gt 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! gt 2.asVoiceValue())?.asInt shouldBe 0
        (1.asVoiceValue()!! gt 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: lte" {
        (1.asVoiceValue()!! lte 2.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! lte 1.asVoiceValue())?.asInt shouldBe 1
        (2.asVoiceValue()!! lte 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: gte" {
        (2.asVoiceValue()!! gte 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! gte 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! gte 2.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: eq" {
        (1.asVoiceValue()!! eq 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! eq 2.asVoiceValue())?.asInt shouldBe 0
        ("a".asVoiceValue() eq "a".asVoiceValue())?.asInt shouldBe 1
        ("a".asVoiceValue() eq "b".asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: ne" {
        (1.asVoiceValue()!! ne 2.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue()!! ne 1.asVoiceValue())?.asInt shouldBe 0
        ("a".asVoiceValue() ne "b".asVoiceValue())?.asInt shouldBe 1
        ("a".asVoiceValue() ne "a".asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue logical operations: and" {
        (1.asVoiceValue()!! and 5.asVoiceValue())?.asInt shouldBe 5
        (0.asVoiceValue()!! and 5.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue logical operations: or" {
        (1.asVoiceValue()!! or 5.asVoiceValue())?.asInt shouldBe 1
        (0.asVoiceValue()!! or 5.asVoiceValue())?.asInt shouldBe 5
    }

    // Tests for VoiceValue.Bool
    "VoiceValue.Bool: true is truthy" {
        true.asVoiceValue().isTruthy() shouldBe true
    }

    "VoiceValue.Bool: false is falsy" {
        false.asVoiceValue().isTruthy() shouldBe false
    }

    "VoiceValue.Bool: asBoolean returns the boolean value" {
        true.asVoiceValue().asBoolean shouldBe true
        false.asVoiceValue().asBoolean shouldBe false
    }

    "VoiceValue.Bool: asString returns string representation" {
        true.asVoiceValue().asString shouldBe "true"
        false.asVoiceValue().asString shouldBe "false"
    }

    "VoiceValue.Bool: asDouble returns 1.0 for true, 0.0 for false" {
        true.asVoiceValue().asDouble shouldBe 1.0
        false.asVoiceValue().asDouble shouldBe 0.0
    }

    "VoiceValue.Bool: asInt returns 1 for true, 0 for false" {
        true.asVoiceValue().asInt shouldBe 1
        false.asVoiceValue().asInt shouldBe 0
    }

    "VoiceValue.Bool: math operations - plus with number" {
        (true.asVoiceValue() + 5.asVoiceValue())?.asDouble shouldBe 6.0
        (false.asVoiceValue() + 5.asVoiceValue())?.asDouble shouldBe 5.0
    }

    "VoiceValue.Bool: math operations - plus with text (concatenation)" {
        (true.asVoiceValue() + "x".asVoiceValue())?.asString shouldBe "truex"
        (false.asVoiceValue() + "x".asVoiceValue())?.asString shouldBe "falsex"
    }

    "VoiceValue.Bool: math operations - minus" {
        (true.asVoiceValue() - false.asVoiceValue())?.asDouble shouldBe 1.0
        (false.asVoiceValue() - true.asVoiceValue())?.asDouble shouldBe -1.0
    }

    "VoiceValue.Bool: math operations - times" {
        (true.asVoiceValue() * 5.asVoiceValue())?.asDouble shouldBe 5.0
        (false.asVoiceValue() * 5.asVoiceValue())?.asDouble shouldBe 0.0
    }

    "VoiceValue.Bool: math operations - div" {
        (true.asVoiceValue() / 1.asVoiceValue())?.asDouble shouldBe 1.0
        (false.asVoiceValue() / 1.asVoiceValue())?.asDouble shouldBe 0.0
    }

    "VoiceValue.Bool: math operations - rem" {
        (true.asVoiceValue() % 1.asVoiceValue())?.asDouble shouldBe 0.0
    }

    "VoiceValue.Bool: math operations - pow" {
        (true.asVoiceValue() pow 5.asVoiceValue())?.asDouble shouldBe 1.0
        (false.asVoiceValue() pow 5.asVoiceValue())?.asDouble shouldBe 0.0
    }

    "VoiceValue.Bool: bitwise operations - band" {
        (true.asVoiceValue() band true.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() band false.asVoiceValue())?.asInt shouldBe 0
        (false.asVoiceValue() band false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: bitwise operations - bor" {
        (true.asVoiceValue() bor true.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() bor false.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() bor false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: bitwise operations - bxor" {
        (true.asVoiceValue() bxor true.asVoiceValue())?.asInt shouldBe 0
        (true.asVoiceValue() bxor false.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() bxor false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: bitwise operations - shl" {
        (true.asVoiceValue() shl 1.asVoiceValue())?.asInt shouldBe 2
        (false.asVoiceValue() shl 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: bitwise operations - shr" {
        (true.asVoiceValue() shr 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - lt" {
        (false.asVoiceValue() lt true.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() lt false.asVoiceValue())?.asInt shouldBe 0
        (true.asVoiceValue() lt true.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - gt" {
        (true.asVoiceValue() gt false.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() gt true.asVoiceValue())?.asInt shouldBe 0
        (true.asVoiceValue() gt true.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - lte" {
        (false.asVoiceValue() lte true.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() lte true.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() lte false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - gte" {
        (true.asVoiceValue() gte false.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() gte true.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() gte true.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - eq" {
        (true.asVoiceValue() eq true.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() eq false.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() eq false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - ne" {
        (true.asVoiceValue() ne false.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() ne true.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - eqt" {
        (true.asVoiceValue() eqt true.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() eqt false.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() eqt false.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: comparison operations - net" {
        (true.asVoiceValue() net false.asVoiceValue())?.asInt shouldBe 1
        (true.asVoiceValue() net true.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: logical operations - and" {
        (true.asVoiceValue() and 5.asVoiceValue())?.asInt shouldBe 5
        (false.asVoiceValue() and 5.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue.Bool: logical operations - or" {
        (true.asVoiceValue() or 5.asVoiceValue())?.asInt shouldBe 1
        (false.asVoiceValue() or 5.asVoiceValue())?.asInt shouldBe 5
    }

    "VoiceValue.Bool: from() converts Boolean to Bool" {
        VoiceValue.from(true) shouldBe VoiceValue.Bool(true)
        VoiceValue.from(false) shouldBe VoiceValue.Bool(false)
    }
})
