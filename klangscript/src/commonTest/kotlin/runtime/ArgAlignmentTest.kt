/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The trailing-lambda alignment rule, in isolation. The interpreter and the analyzer both
 * call this, so every case here is a contract for both.
 */
class ArgAlignmentTest : StringSpec({

    fun align(args: List<Boolean>, params: List<Boolean>): List<Int> =
        ArgAlignment.positionalTargets(
            argCount = args.size,
            paramCount = params.size,
            isFunctionArg = { args[it] },
            isFunctionParam = { params[it] },
        )

    "no arguments: empty mapping" {
        align(emptyList(), listOf(false, true)) shouldBe emptyList()
    }

    "plain values map by position" {
        align(listOf(false, false), listOf(false, false, true)) shouldBe listOf(0, 1)
    }

    "a sole trailing lambda floats past one non-function slot to the single function slot" {
        // Osc.supersaw(x => ...) with params (freq, configure)
        align(listOf(true), listOf(false, true)) shouldBe listOf(1)
    }

    "a trailing lambda floats past several non-function slots" {
        // phaser(rate, center, sweep, configure) called as phaser(0.5, x => ...)
        align(listOf(false, true), listOf(false, false, false, true)) shouldBe listOf(0, 3)
    }

    "a lambda already on a function slot stays where it is" {
        align(listOf(true), listOf(true, true)) shouldBe listOf(0)
    }

    "a lambda that is not the last argument does not float" {
        // f(lambda, 2): the lambda stays on slot 0, the caller sees the mismatch downstream
        align(listOf(true, false), listOf(false, false, true)) shouldBe listOf(0, 1)
    }

    "two trailing function slots are ambiguous: no float" {
        align(listOf(true), listOf(false, true, true)) shouldBe listOf(0)
    }

    "no function slot after the lambda: no float" {
        align(listOf(true), listOf(false, false)) shouldBe listOf(0)
    }

    "only slots AFTER the lambda count as candidates" {
        // (configure, n) called as f(1, lambda): the function slot is before, not after
        align(listOf(false, true), listOf(true, false)) shouldBe listOf(0, 1)
    }

    "more arguments than parameters: identity, the caller decides (vararg or error)" {
        align(listOf(true, true), listOf(true)) shouldBe listOf(0, 1)
    }
})
