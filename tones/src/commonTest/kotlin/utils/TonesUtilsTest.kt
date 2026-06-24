/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
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
