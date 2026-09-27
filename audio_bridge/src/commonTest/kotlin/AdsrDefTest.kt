/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AdsrDefTest : StringSpec({

    "AdsrCurve enum has Linear, Square, Cube, SCurve, InvSquare, Exponential" {
        AdsrCurve.entries.size shouldBe 6
        AdsrCurve.entries shouldBe listOf(
            AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube,
            AdsrCurve.SCurve, AdsrCurve.InvSquare, AdsrCurve.Exponential,
        )
    }
})
