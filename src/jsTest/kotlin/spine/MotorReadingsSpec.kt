/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The Motor's readings (KLANG-00389, step 4): each derived once in the Spine, so every gauge that shows a reading listens
 * to the one smoothing of it, not to one of its own.
 */
class MotorReadingsSpec : StringSpec({

    "every gauge gets the same reading: one smoothing each of the cylinders, the heat and the voices" {
        withClue("the cylinders") { (MotorReadings.cylinders === MotorReadings.cylinders) shouldBe true }
        withClue("the heat") { (MotorReadings.heat === MotorReadings.heat) shouldBe true }
        withClue("the voices") { (MotorReadings.voices === MotorReadings.voices) shouldBe true }
    }
})
