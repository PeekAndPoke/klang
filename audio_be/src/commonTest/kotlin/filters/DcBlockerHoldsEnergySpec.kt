/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * [LowPassHighPassFilters.DcBlocker.holdsEnergy], the question the Katalyst `distort` stage asks before a chain swap
 * may retire it: can the blocker still put a sample above the floor into a SILENT input?
 */
class DcBlockerHoldsEnergySpec : StringSpec({

    val floor = 1e-5

    "a settled offset has an output of 0 but still holds energy: the next silent sample steps by the whole offset" {
        // A constant 0.5 in: the output decays to 0 (the offset is what the blocker removes), the last input is 0.5.
        val blocker = LowPassHighPassFilters.DcBlocker(HOUSE_DC_BLOCK_COEFF)
        val dc = DoubleArray(40_000) { 0.5 }

        blocker.process(buffer = dc, offset = 0, length = dc.size)

        abs(dc.last()) shouldBeLessThan floor
        blocker.holdsEnergy(floor) shouldBe true

        // ...and it does: silence in, minus the offset out.
        val silence = DoubleArray(1)

        blocker.process(buffer = silence, offset = 0, length = 1)

        silence[0] shouldBe (-0.5 plusOrMinus 1e-3)
    }

    "a blocker fed silence long enough holds nothing, and a fresh or reset one holds nothing" {
        val blocker = LowPassHighPassFilters.DcBlocker(HOUSE_DC_BLOCK_COEFF)

        blocker.holdsEnergy(floor) shouldBe false

        blocker.process(buffer = DoubleArray(64) { 0.3 }, offset = 0, length = 64)
        blocker.holdsEnergy(floor) shouldBe true

        blocker.reset()
        blocker.holdsEnergy(floor) shouldBe false

        blocker.process(buffer = DoubleArray(64) { 0.3 }, offset = 0, length = 64)
        blocker.process(buffer = DoubleArray(40_000), offset = 0, length = 40_000)
        blocker.holdsEnergy(floor) shouldBe false
    }
})
