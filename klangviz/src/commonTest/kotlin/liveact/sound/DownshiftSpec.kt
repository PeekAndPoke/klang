/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** What [downshift] says to each of [rates], in order. */
private fun told(downshift: Downshift, vararg rates: Double): List<Boolean> = rates.map { downshift.judge(it) }

class DownshiftSpec : StringSpec({

    "fast enough, it never asks for a step" {
        told(Downshift(), *DoubleArray(20) { 45.0 }).any { it } shouldBe false
        told(Downshift(), *DoubleArray(20) { 60.0 }).any { it } shouldBe false
    }

    "it asks for a step at the sixth slow stretch in a row, not before" {
        told(Downshift(), 44.9, 30.0, 30.0, 30.0, 30.0, 30.0) shouldBe listOf(false, false, false, false, false, true)
    }

    "a fast stretch between starts the count anew" {
        told(Downshift(), 30.0, 30.0, 30.0, 30.0, 30.0, 60.0, 30.0, 30.0, 30.0, 30.0, 30.0).any { it } shouldBe false
    }

    "after a step it counts anew: another six slow stretches for the next" {
        val downshift = Downshift()

        told(downshift, 30.0, 30.0, 30.0, 30.0, 30.0, 30.0).last() shouldBe true
        told(downshift, 30.0, 30.0, 30.0, 30.0, 30.0) shouldBe listOf(false, false, false, false, false)
        downshift.judge(30.0) shouldBe true
    }

    "a choice by hand holds: however slow, no step is asked for" {
        val downshift = Downshift()

        (1..12).map { downshift.judge(fps = 30.0, byHand = true) }.any { it } shouldBe false
    }

    "a rate that is no number does not count as slow" {
        told(Downshift(), 30.0, 30.0, 30.0, 30.0, 30.0, Double.NaN, 30.0).any { it } shouldBe false
    }
})
