/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.abc

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AbcExamplesTest : StringSpec({
    "Abc.abcToScientificNotation" {
        AbcNotation.toScientificNotation("c") shouldBe "C5"
    }

    "Abc.scientificToAbcNotation" {
        AbcNotation.fromScientificNotation("C#4") shouldBe "^C"
    }

    "Abc.transposeNote" {
        AbcNotation.transpose("=C", "P19") shouldBe "g'"
    }

    "Abc.distance" {
        AbcNotation.distance("=C", "g") shouldBe "12P"
    }
})
