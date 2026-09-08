/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.common.math.cents
import io.peekandpoke.klang.common.math.db
import io.peekandpoke.klang.common.math.semitones
import io.peekandpoke.klang.common.math.toDb
import io.peekandpoke.klang.common.math.toSemitones
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.tones.interval.Interval
import kotlin.math.pow

/**
 * Two doors, one DSL: every number method must mean the same thing from a script and from Kotlin.
 *
 * Tiers 1 and 2 need no new Kotlin door, `kotlin.math` already is it, so only the two cases where a
 * reader might doubt the delegate are pinned here: `pow` (the reason the task exists) and `mod` (the
 * one no operator can express). Tier 3 has a real Kotlin door, the `common` conversions and
 * `Interval.ratio`, and every method of it is compared here.
 *
 * The comparison is exact on purpose: the two doors must compute the same double, not merely a
 * similar one. A tolerance would hide a script door that quietly rounds or converts twice.
 */
class StdLibNumberMethodsDoorParitySpec : StringSpec({

    fun script(code: String): Double {
        val engine = klangScript()

        return engine.execute("import * from \"stdlib\"\n$code")
            .shouldBeInstanceOf<NumberValue>().value
    }

    "tier 3: the pitch conversions are the same on both doors" {
        withClue("semitones") { script("7.semitones()") shouldBe 7.0.semitones() }
        withClue("semitones, descending") { script("-12.semitones()") shouldBe (-12.0).semitones() }
        withClue("cents") { script("50.cents()") shouldBe 50.0.cents() }
        withClue("toSemitones") { script("1.5.toSemitones()") shouldBe 1.5.toSemitones() }
    }

    "tier 3: the gain conversions are the same on both doors" {
        withClue("db") { script("-6.db()") shouldBe (-6.0).db() }
        withClue("toDb") { script("0.5.toDb()") shouldBe 0.5.toDb() }
    }

    "tier 3: a named interval is the same on both doors" {
        withClue("P5") { script("\"P5\".toRatio()") shouldBe Interval.get("P5").ratio }
        withClue("M3") { script("\"M3\".toRatio()") shouldBe Interval.get("M3").ratio }
        withClue("-5P") { script("\"-5P\".toRatio()") shouldBe Interval.get("-5P").ratio }
    }

    "tier 1: pow and mod delegate to the Kotlin call a reader would expect" {
        withClue("pow") { script("2.pow(7/12)") shouldBe 2.0.pow(7.0 / 12.0) }
        withClue("mod") { script("-1.mod(12)") shouldBe (-1.0).mod(12.0) }
    }
})
