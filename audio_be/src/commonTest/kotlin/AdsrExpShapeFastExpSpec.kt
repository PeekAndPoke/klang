/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.exp

/** The exponential envelope shape `adsrExpShape` on `fastExp` (`utils/FastExpSpec` pins `fastExp` itself). */
class AdsrExpShapeFastExpSpec : StringSpec({

    "the exponential envelope shape starts at exactly zero, ends within an ulp of one, and keeps its law to -180 dB" {
        // g(0) = 0 EXACTLY (the polynomial's pinned start): the release's last frame is 0.0 and
        // the envelope specs pin the sustain and gate-end levels to 1e-12. g(1) is A · (1 / A) of
        // one value, 1.0 or one ulp under (k = 7 is such a k). In between, (e^(kx) - 1) / (e^k - 1)
        // to an absolute 1e-9 (a level is an absolute quantity; near zero the difference
        // e^(kx) - 1 is tiny, so its RELATIVE error is not the bound), for the default curvature
        // and for the curvatures a pipeline's VCA stage may set.
        for (k in listOf(0.5, 3.0, 7.0, 8.0)) {
            val norm = adsrExpNorm(k)

            withClue("k = $k: g(0)") { adsrExpShape(x = 0.0, k = k, norm = norm) shouldBe 0.0 }
            withClue("k = $k: g(1)") { abs(adsrExpShape(x = 1.0, k = k, norm = norm) - 1.0) shouldBeLessThan 3e-16 }

            for (i in 1 until 1000) {
                val x = i / 1000.0
                val expected = (exp(k * x) - 1.0) / (exp(k) - 1.0)

                withClue("k = $k, x = $x") { abs(adsrExpShape(x = x, k = k, norm = norm) - expected) shouldBeLessThan 1e-9 }
            }
        }
    }
})
