/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.math.pow

class FogQualitySpec : StringSpec({

    "the qualities, their sheets and their words, fewest first" {
        FogQuality.entries.map { it.layers } shouldBe listOf(0, 4, 8, 12, 16)
        FogQuality.entries.map { it.word } shouldBe listOf("off", "low", "medium", "high", "xhigh")
    }

    "the maps lie 2 by 2, 4 by 2, 4 by 3 and 4 by 4, with no map to spare" {
        FogQuality.entries.map { it.cols to it.rows } shouldBe listOf(0 to 0, 2 to 2, 4 to 2, 4 to 3, 4 to 4)
        FogQuality.entries.filterNot { it.cols * it.rows == it.layers } shouldBe emptyList()
    }

    "high's sheets each let 0.22 through, the fog as it was made; fewer sheets let more through each, more less" {
        FogQuality.High.share shouldBe (0.22 plusOrMinus 1e-12)
        FogQuality.Low.share shouldBe (0.525448 plusOrMinus 1e-6)
        FogQuality.Medium.share shouldBe (0.311123 plusOrMinus 1e-6)
        FogQuality.XHigh.share shouldBe (0.170014 plusOrMinus 1e-6)
        FogQuality.Off.share shouldBe 0.0
    }

    "all the sheets of a quality together veil as much as high's twelve do" {
        val high = (1.0 - 0.22).pow(12)

        for (quality in listOf(FogQuality.Low, FogQuality.Medium, FogQuality.XHigh)) {
            (1.0 - quality.share).pow(quality.layers) shouldBe (high plusOrMinus 1e-12)
        }
    }

    "the address's fog: fog= by its word, else nofog is off, else high" {
        FogQuality.of(Switches.parse("")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=low")) shouldBe FogQuality.Low
        FogQuality.of(Switches.parse("?fog=off")) shouldBe FogQuality.Off
        FogQuality.of(Switches.parse("?fog=xhigh&seed=3")) shouldBe FogQuality.XHigh
        FogQuality.of(Switches.parse("?nofog")) shouldBe FogQuality.Off
        // fog= wins over nofog: an app puts its own fog= after the address's switches
        FogQuality.of(Switches.parse("?nofog&fog=high")) shouldBe FogQuality.High
    }

    "a word that names no quality is high, the fog as it was made (or off with nofog)" {
        FogQuality.of(Switches.parse("?fog=thick")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=LOW")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=thick&nofog")) shouldBe FogQuality.Off
        FogQuality.named(null) shouldBe null
    }
})
