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

    "VoiceValue math operations" {
        // Num + Double
        (10.asVoiceValue()!! + 5.0)?.asDouble shouldBe 15.0

        // Num + Num
        (10.asVoiceValue()!! + 5.asVoiceValue())?.asDouble shouldBe 15.0

        // Text(Numeric) + Double
        ("10".asVoiceValue()!! + 5.0)?.asDouble shouldBe 15.0

        // Text(Non-Numeric) + Double -> null
        ("foo".asVoiceValue()!! + 5.0) shouldBe null
    }
})
