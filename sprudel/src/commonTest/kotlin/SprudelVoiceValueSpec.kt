/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

@Suppress("RegExpRedundantEscape")
class SprudelVoiceValueSpec : StringSpec({

    "VoiceValue.Num: 0.0 is falsy" {
        0.0.asVoiceValue().isTruthy() shouldBe false
        0.asVoiceValue().isTruthy() shouldBe false // Number converts to Num(0.0)
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
        (10.asVoiceValue() + 5.0.asVoiceValue())?.asDouble shouldBe 15.0

        // Num + Num
        (10.asVoiceValue() + 5.asVoiceValue())?.asDouble shouldBe 15.0

        // Text(Numeric) + Double
        ("10".asVoiceValue() + 5.0.asVoiceValue())?.asDouble shouldBe 15.0

        // Text + Text (concatenation)
        ("a".asVoiceValue() + "b".asVoiceValue())?.asString shouldBe "ab"
    }

    "VoiceValue math operations: minus" {
        (10.asVoiceValue() - 5.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue() - "5".asVoiceValue())?.asDouble shouldBe 5.0
    }

    "VoiceValue math operations: times" {
        (10.asVoiceValue() * 5.asVoiceValue())?.asDouble shouldBe 50.0
        ("10".asVoiceValue() * "5".asVoiceValue())?.asDouble shouldBe 50.0
    }

    "VoiceValue math operations: div" {
        (10.asVoiceValue() / 2.asVoiceValue())?.asDouble shouldBe 5.0
        ("10".asVoiceValue() / "2".asVoiceValue())?.asDouble shouldBe 5.0
        (10.asVoiceValue() / 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: rem (mod)" {
        (10.asVoiceValue() % 3.asVoiceValue())?.asDouble shouldBe 1.0
        ("10".asVoiceValue() % "3".asVoiceValue())?.asDouble shouldBe 1.0
        (10.asVoiceValue() % 0.asVoiceValue()) shouldBe null
    }

    "VoiceValue math operations: pow" {
        (2.asVoiceValue() pow 3.asVoiceValue())?.asDouble shouldBe 8.0
        ("2".asVoiceValue() pow "3".asVoiceValue())?.asDouble shouldBe 8.0
    }

    "VoiceValue bitwise operations: band" {
        (3.asVoiceValue() band 1.asVoiceValue())?.asInt shouldBe 1
        (3.asVoiceValue() band 0.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue bitwise operations: bor" {
        (1.asVoiceValue() bor 2.asVoiceValue())?.asInt shouldBe 3
        (1.asVoiceValue() bor 0.asVoiceValue())?.asInt shouldBe 1
    }

    "VoiceValue bitwise operations: bxor" {
        (3.asVoiceValue() bxor 1.asVoiceValue())?.asInt shouldBe 2
        (3.asVoiceValue() bxor 3.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue bitwise operations: shl" {
        (1.asVoiceValue() shl 1.asVoiceValue())?.asInt shouldBe 2
        (1.asVoiceValue() shl 2.asVoiceValue())?.asInt shouldBe 4
    }

    "VoiceValue bitwise operations: shr" {
        (2.asVoiceValue() shr 1.asVoiceValue())?.asInt shouldBe 1
        (4.asVoiceValue() shr 2.asVoiceValue())?.asInt shouldBe 1
    }

    "VoiceValue comparison operations: lt" {
        (1.asVoiceValue() lt 2.asVoiceValue())?.asInt shouldBe 1
        (2.asVoiceValue() lt 1.asVoiceValue())?.asInt shouldBe 0
        (1.asVoiceValue() lt 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: gt" {
        (2.asVoiceValue() gt 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() gt 2.asVoiceValue())?.asInt shouldBe 0
        (1.asVoiceValue() gt 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: lte" {
        (1.asVoiceValue() lte 2.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() lte 1.asVoiceValue())?.asInt shouldBe 1
        (2.asVoiceValue() lte 1.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: gte" {
        (2.asVoiceValue() gte 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() gte 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() gte 2.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: eq" {
        (1.asVoiceValue() eq 1.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() eq 2.asVoiceValue())?.asInt shouldBe 0
        ("a".asVoiceValue() eq "a".asVoiceValue())?.asInt shouldBe 1
        ("a".asVoiceValue() eq "b".asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue comparison operations: ne" {
        (1.asVoiceValue() ne 2.asVoiceValue())?.asInt shouldBe 1
        (1.asVoiceValue() ne 1.asVoiceValue())?.asInt shouldBe 0
        ("a".asVoiceValue() ne "b".asVoiceValue())?.asInt shouldBe 1
        ("a".asVoiceValue() ne "a".asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue logical operations: and" {
        (1.asVoiceValue() and 5.asVoiceValue())?.asInt shouldBe 5
        (0.asVoiceValue() and 5.asVoiceValue())?.asInt shouldBe 0
    }

    "VoiceValue logical operations: or" {
        (1.asVoiceValue() or 5.asVoiceValue())?.asInt shouldBe 1
        (0.asVoiceValue() or 5.asVoiceValue())?.asInt shouldBe 5
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
        SprudelVoiceValue.of(true) shouldBe SprudelVoiceValue.Bool(true)
        SprudelVoiceValue.of(false) shouldBe SprudelVoiceValue.Bool(false)
    }

    // Tests for VoiceValue.Seq
    "VoiceValue.Seq: empty list is falsy" {
        SprudelVoiceValue.Seq(emptyList()).isTruthy() shouldBe false
    }

    "VoiceValue.Seq: non-empty list is truthy" {
        SprudelVoiceValue.Seq(listOf(1.0.asVoiceValue())).isTruthy() shouldBe true
        SprudelVoiceValue.Seq(listOf(0.0.asVoiceValue())).isTruthy() shouldBe true
    }

    "VoiceValue.Seq: asBoolean returns isEmpty/isNotEmpty" {
        SprudelVoiceValue.Seq(emptyList()).asBoolean shouldBe false
        SprudelVoiceValue.Seq(listOf(1.0.asVoiceValue())).asBoolean shouldBe true
    }

    "VoiceValue.Seq: asString returns comma-separated values" {
        SprudelVoiceValue.Seq(
            listOf(
                1.0.asVoiceValue(),
                2.0.asVoiceValue(),
                3.0.asVoiceValue()
            )
        ).asString shouldBeIn listOf(
            "1.0, 2.0, 3.0",
            "1, 2, 3", // js behaves differently
        )
        SprudelVoiceValue.Seq(listOf("a".asVoiceValue(), "b".asVoiceValue())).asString shouldBe "a, b"
        SprudelVoiceValue.Seq(emptyList()).asString shouldBe ""
    }

    "VoiceValue.Seq: asDouble returns first element's double" {
        SprudelVoiceValue.Seq(listOf(42.0.asVoiceValue(), 99.0.asVoiceValue())).asDouble shouldBe 42.0
        SprudelVoiceValue.Seq(listOf("10".asVoiceValue())).asDouble shouldBe 10.0
        SprudelVoiceValue.Seq(emptyList()).asDouble shouldBe null
    }

    "VoiceValue.Seq: asInt returns first element's int" {
        SprudelVoiceValue.Seq(listOf(42.0.asVoiceValue(), 99.0.asVoiceValue())).asInt shouldBe 42
        SprudelVoiceValue.Seq(listOf("10".asVoiceValue())).asInt shouldBe 10
        SprudelVoiceValue.Seq(emptyList()).asInt shouldBe null
    }

    "VoiceValue.Seq: toString formats as [values]" {
        SprudelVoiceValue.Seq(
            listOf(1.0.asVoiceValue(), 2.0.asVoiceValue())
        ).toString() shouldBeIn listOf(
            "[1.0, 2.0]",
            "[1, 2]" // js exception
        )

        SprudelVoiceValue.Seq(emptyList()).toString() shouldBe "[]"
    }

    "VoiceValue.Seq: math operations - plus with number" {
        val seq = SprudelVoiceValue.Seq(listOf(5.0.asVoiceValue(), 10.0.asVoiceValue()))
        (seq + 3.0.asVoiceValue())?.asDouble shouldBe 8.0 // 5 + 3
    }

    "VoiceValue.Seq: math operations - plus with text (concatenation)" {
        val seq = SprudelVoiceValue.Seq(listOf(5.0.asVoiceValue(), 10.0.asVoiceValue()))
        (seq + "x".asVoiceValue())?.asString shouldBeIn listOf(
            "5.0, 10.0x",
            "5, 10x", // js exception
        )
    }

    "VoiceValue.Seq: math operations - minus" {
        val seq = SprudelVoiceValue.Seq(listOf(10.0.asVoiceValue(), 20.0.asVoiceValue()))
        (seq - 3.0.asVoiceValue())?.asDouble shouldBe 7.0 // 10 - 3
    }

    "VoiceValue.Seq: math operations - times" {
        val seq = SprudelVoiceValue.Seq(listOf(5.0.asVoiceValue(), 10.0.asVoiceValue()))
        (seq * 2.0.asVoiceValue())?.asDouble shouldBe 10.0 // 5 * 2
    }

    "VoiceValue.Seq: logical operations - and" {
        val seq = SprudelVoiceValue.Seq(listOf(1.0.asVoiceValue()))
        (seq and 5.asVoiceValue())?.asInt shouldBe 5

        val emptySeq = SprudelVoiceValue.Seq(emptyList())
        (emptySeq and 5.asVoiceValue()) shouldBe emptySeq
    }

    "VoiceValue.Seq: logical operations - or" {
        val seq = SprudelVoiceValue.Seq(listOf(1.0.asVoiceValue()))
        (seq or 5.asVoiceValue()) shouldBe seq

        val emptySeq = SprudelVoiceValue.Seq(emptyList())
        (emptySeq or 5.asVoiceValue())?.asInt shouldBe 5
    }

})
