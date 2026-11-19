/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class ShadowQualitySpec : StringSpec({

    "high is the shadows as they were made: maps of 2048, and the inner lamp behind casts" {
        ShadowQuality.High.mapSize shouldBe 2048
        ShadowQuality.High.casts(BackPlace.INNER) shouldBe true
        ShadowQuality.High.casts(BackPlace.OUTER) shouldBe true
        ShadowQuality.High.casts(null) shouldBe true
    }

    "low: maps of 1024, and the inner lamp behind casts none; the outer ones and the front lamp still do" {
        ShadowQuality.Low.mapSize shouldBe 1024
        ShadowQuality.Low.casts(BackPlace.INNER) shouldBe false
        ShadowQuality.Low.casts(BackPlace.OUTER) shouldBe true
        ShadowQuality.Low.casts(null) shouldBe true
    }

    "the address's shadows by their word; without it, or with another word, high" {
        ShadowQuality.entries.map { it.word } shouldBe listOf("low", "high")
        ShadowQuality.of(Switches.parse("?shadows=low")) shouldBe ShadowQuality.Low
        ShadowQuality.of(Switches.parse("?shadows=high&seed=2")) shouldBe ShadowQuality.High
        ShadowQuality.of(Switches.parse("?shadows=soft")) shouldBe ShadowQuality.High
        ShadowQuality.of(Switches.parse("")) shouldBe ShadowQuality.High
    }
})
