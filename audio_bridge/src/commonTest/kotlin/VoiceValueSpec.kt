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
        ("10".asVoiceValue()!! + 5.0.asVoiceValue())?.asDouble shouldBe 15.0

        // Text + Text (concatenation)
        ("a".asVoiceValue()!! + "b".asVoiceValue())?.asString shouldBe "ab"
    }

    "VoiceValue math operations: minus" {
        (10.asVoiceValue()!! - 5.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue()!! - "5".asVoiceValue())?.asDouble shouldBe 5.0
    }

    "VoiceValue math operations: times" {
        (10.asVoiceValue()!! * 5.asVoiceValue())?.asDouble shouldBe 50.0
        ("10".asVoiceValue()!! * "5".asVoiceValue())?.asDouble shouldBe 50.0
    }

    "VoiceValue math operations: div" {
        (10.asVoiceValue()!! / 2.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue()!! / "2".asVoiceValue())?.asDouble shouldBe 5.0
        (10.asVoiceValue()!! / 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: rem (mod)" {
        (10.asVoiceValue()!! % 3.asVoiceValue())?.asDouble shouldBe 1.0
        ("10".asVoiceValue()!! % "3".asVoiceValue())?.asDouble shouldBe 1.0
        (10.asVoiceValue()!! % 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: pow" {
        (2.asVoiceValue()!! pow 3.asVoiceValue())?.asDouble shouldBe 8.0
        ("2".asVoiceValue()!! pow "3".asVoiceValue())?.asDouble shouldBe 8.0
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
})
