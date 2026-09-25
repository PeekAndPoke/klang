/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FILTER_MAX_PASSES
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.optimize

/**
 * A filter's `passes` is a KNOB read once at voice build (phase 3 step 5, maintainer 2026-09-25), so
 * `classic()` fills it from `lpf.passes` / `hpf.passes` and sprudel's `lpf(passes = n)` keeps working on
 * the built-ins. The oracle for every row is the node with a literal count (the Kotlin door's, which was
 * the only form before), so each row says exactly which count the knob became.
 */
class FilterPassesKnobSpec : StringSpec({

    val saw = IgnitorDsl.Sawtooth()

    fun lp(passes: IgnitorDsl): IgnitorDsl = IgnitorDsl.Lowpass(inner = saw, freq = IgnitorDsl.Constant(900.0), q = IgnitorDsl.Constant(2.0), passes = passes)

    fun literal(n: Int): DoubleArray = renderVoiceWindows(saw.lowpass(900.0, 2.0, passes = n))

    fun shouldBeCount(clue: String, n: Int, actual: DoubleArray) {
        withClue("$clue renders $n passes") { firstBitMismatch(literal(n), actual) shouldBe -1 }

        val other = if (n == 1) 2 else 1

        withClue("$clue is not $other passes") { firstBitMismatch(literal(other), actual) shouldNotBe -1 }
    }

    val slot = IgnitorDsl.Param("p", 1.0)

    "a slot written through the bag sets the count" {
        shouldBeCount("p = 2", 2, renderVoiceWindows(lp(slot), mapOf("p" to 2.0)))
        shouldBeCount("p = 3", 3, renderVoiceWindows(lp(slot), mapOf("p" to 3.0)))
    }

    "an unwritten slot is its default" {
        shouldBeCount("unwritten, default 1", 1, renderVoiceWindows(lp(slot), emptyMap()))
        shouldBeCount("unwritten, default 3", 3, renderVoiceWindows(lp(IgnitorDsl.Param("p", 3.0)), emptyMap()))
    }

    "a fractional count ROUNDS, as the strip rounds sprudel's value (coercePasses)" {
        shouldBeCount("p = 2.6", 3, renderVoiceWindows(lp(slot), mapOf("p" to 2.6)))
        shouldBeCount("p = 2.4", 2, renderVoiceWindows(lp(slot), mapOf("p" to 2.4)))
    }

    "the count is bounded to 1..FILTER_MAX_PASSES" {
        shouldBeCount("p = 100", FILTER_MAX_PASSES, renderVoiceWindows(lp(slot), mapOf("p" to 100.0)))
        shouldBeCount("p = 0", 1, renderVoiceWindows(lp(IgnitorDsl.Param("p", 2.0)), mapOf("p" to 0.0)))
    }

    "a non-finite count is one pass, whether it comes from the bag or is authored" {
        shouldBeCount("bag NaN takes the default 2", 2, renderVoiceWindows(lp(IgnitorDsl.Param("p", 2.0)), mapOf("p" to Double.NaN)))
        shouldBeCount("authored +Inf", 1, renderVoiceWindows(lp(IgnitorDsl.Constant(Double.POSITIVE_INFINITY)), null))
    }

    "a non-leaf count has no build-time answer and is one pass" {
        shouldBeCount("Constant(2).mul(Constant(1))", 1, renderVoiceWindows(lp(IgnitorDsl.Constant(2.0).mul(IgnitorDsl.Constant(1.0))), null))
    }

    "the optimizer does not fuse a slotted count: an optimized tree still reads the slot per note" {
        shouldBeCount("optimized, p = 2", 2, renderVoiceWindows(lp(slot).optimize(), mapOf("p" to 2.0)))
        shouldBeCount("optimized, p = 3", 3, renderVoiceWindows(lp(slot).optimize(), mapOf("p" to 3.0)))
    }

    "the highpass reads the same knob" {
        val hp = IgnitorDsl.Highpass(inner = saw, freq = IgnitorDsl.Constant(900.0), q = IgnitorDsl.Constant(2.0), passes = slot)
        val actual = renderVoiceWindows(hp, mapOf("p" to 2.0))

        withClue("hpf p = 2") {
            firstBitMismatch(renderVoiceWindows(saw.highpass(900.0, 2.0, passes = 2)), actual) shouldBe -1
            firstBitMismatch(renderVoiceWindows(saw.highpass(900.0, 2.0, passes = 1)), actual) shouldNotBe -1
        }
    }
})
