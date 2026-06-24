/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesUtilsTest : StringSpec({
    "fillStr" {
        TonesUtils.fillStr("#", 5) shouldBe "#####"
        TonesUtils.fillStr("b", 0) shouldBe ""
        TonesUtils.fillStr("a", 3) shouldBe "aaa"
    }
})
