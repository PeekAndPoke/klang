/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class RestingSpec : StringSpec({

    "a drummer whose part keeps going never rests" {
        // a hit every two seconds: at every moment between two of them
        for (i in 0..200) {
            val since = i / 100.0

            restShare(since = since, until = 2.0 - since) shouldBe 0.0
        }
    }

    "in a long silence he lets go, and rests all the way" {
        restShare(since = 2.9, until = 60.0) shouldBe 0.0
        restShare(since = 4.0, until = 60.0) shouldBeGreaterThan 0.0
        restShare(since = 4.0, until = 60.0) shouldBeLessThan 1.0
        restShare(since = 10.0, until = 60.0) shouldBe 1.0
        // before the song's first hit, and after its last
        restShare(since = Double.POSITIVE_INFINITY, until = 120.0) shouldBe 1.0
        restShare(since = 30.0, until = Double.POSITIVE_INFINITY) shouldBe 1.0
    }

    "he is ready in time for his next hit" {
        restShare(since = 100.0, until = 10.0) shouldBe 1.0
        restShare(since = 100.0, until = 2.0) shouldBeGreaterThan 0.0
        restShare(since = 100.0, until = REST_READY) shouldBe 0.0
        restShare(since = 100.0, until = 0.5) shouldBe 0.0
    }

    "the hands sink and rise without a jump" {
        for (i in 0 until 2000) {
            val a = i / 100.0
            val b = (i + 1) / 100.0

            abs(restShare(since = b, until = 60.0) - restShare(since = a, until = 60.0)) shouldBeLessThan 0.02
            abs(restShare(since = 60.0, until = 20.0 - b) - restShare(since = 60.0, until = 20.0 - a)) shouldBeLessThan 0.02
        }
    }

    "where a time is no number he is ready" {
        restShare(since = Double.NaN, until = 60.0) shouldBe 0.0
        restShare(since = 60.0, until = Double.NaN) shouldBe 0.0
    }
})
