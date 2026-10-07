/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The number helpers the editor tools write values back with. JS only on purpose: they print through the
 * platform's own number formatting, and the editor tools only run in the browser.
 */
class NumberFormattingSpec : StringSpec({

    "roundTo rounds to the given decimals" {
        1.23456.roundTo(2) shouldBe 1.23
        7.6.roundTo(0) shouldBe 8.0
        (-1.26).roundTo(1) shouldBe -1.3
    }

    "roundTo takes a half to the even neighbour" {
        2.5.roundTo(0) shouldBe 2.0
        3.5.roundTo(0) shouldBe 4.0
        0.125.roundTo(2) shouldBe 0.12
    }

    "roundToString prints the rounded value without trailing zeros" {
        1.23456.roundToString(4) shouldBe "1.2346"
        1.50001.roundToString(3) shouldBe "1.5"
        0.00004.roundToString(4) shouldBe "0"
    }

    "roundToString keeps the zeros of a whole number" {
        100.0.roundToString(3) shouldBe "100"
        20.0.roundToString(0) shouldBe "20"
    }

    "toFixedTrimmed formats with toFixed and trims trailing zeros and a bare dot" {
        0.25.toFixedTrimmed(3) shouldBe "0.25"
        0.1234.toFixedTrimmed(3) shouldBe "0.123"
        1.0.toFixedTrimmed(3) shouldBe "1"
        0.0.toFixedTrimmed(3) shouldBe "0"
    }

    "toFixedTrimmed keeps the zeros of a whole number" {
        1000.0.toFixedTrimmed(3) shouldBe "1000"
        1000.0.toFixedTrimmed(0) shouldBe "1000"
    }

    "on a tie the two laws differ: toFixedTrimmed rounds like JS toFixed, roundToString half to even" {
        2.5.toFixedTrimmed(0) shouldBe "3"
        2.5.roundToString(0) shouldBe "2"
        0.0125.toFixedTrimmed(3) shouldBe "0.013"
        0.0125.roundToString(3) shouldBe "0.012"
    }

    "an exponent form is never trimmed" {
        1.5e30.toFixedTrimmed(3) shouldBe "1.5e+30"
        1.5e30.roundToString(0) shouldBe "1.5e+30"
    }

    "decimalPlaces counts the printed decimals" {
        0.25.decimalPlaces() shouldBe 2
        0.1.decimalPlaces() shouldBe 1
        1.125.decimalPlaces() shouldBe 3
        3.0.decimalPlaces() shouldBe 0
    }
})
