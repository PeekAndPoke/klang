/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.tones.pitch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PitchCoordinatesTest : StringSpec({
    "PitchCoordinates.toList" {
        PitchCoordinates.PitchClass(3).toList() shouldBe listOf(3)

        PitchCoordinates.Note(3, 3).toList() shouldBe listOf(3, 3)

        PitchCoordinates.Interval(1, 0, 1).toList() shouldBe listOf(1, 0, 1)
    }
})
