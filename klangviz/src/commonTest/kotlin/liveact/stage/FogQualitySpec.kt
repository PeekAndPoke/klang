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
        FogQuality.entries.map { it.layers } shouldBe listOf(0, 2, 4)
        FogQuality.entries.map { it.word } shouldBe listOf("off", "low", "high")
    }

    "the maps lie 2 by 1 and 2 by 2, with no map to spare" {
        FogQuality.entries.map { it.cols to it.rows } shouldBe listOf(0 to 0, 2 to 1, 2 to 2)
        FogQuality.entries.filterNot { it.cols * it.rows == it.layers } shouldBe emptyList()
    }

    "the fog was made with twelve sheets of 0.22 each; fewer sheets let more through each" {
        FogQuality.High.share shouldBe (0.525448 plusOrMinus 1e-6)
        FogQuality.Low.share shouldBe (0.774800 plusOrMinus 1e-6)
        FogQuality.Off.share shouldBe 0.0
    }

    "all the sheets of a quality together veil as much as the twelve the fog was made with" {
        val made = (1.0 - 0.22).pow(12)

        for (quality in listOf(FogQuality.Low, FogQuality.High)) {
            (1.0 - quality.share).pow(quality.layers) shouldBe (made plusOrMinus 1e-12)
        }
    }

    "the address's fog: fog= by its word, else nofog is off, else high" {
        FogQuality.of(Switches.parse("")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=low")) shouldBe FogQuality.Low
        FogQuality.of(Switches.parse("?fog=off")) shouldBe FogQuality.Off
        FogQuality.of(Switches.parse("?fog=high&seed=3")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?nofog")) shouldBe FogQuality.Off
        // fog= wins over nofog: an app puts its own fog= after the address's switches
        FogQuality.of(Switches.parse("?nofog&fog=high")) shouldBe FogQuality.High
    }

    "a word that names no quality is high (or off with nofog), the former medium and xhigh among them" {
        FogQuality.of(Switches.parse("?fog=thick")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=medium")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=xhigh")) shouldBe FogQuality.High
        FogQuality.named("medium") shouldBe null
        FogQuality.of(Switches.parse("?fog=LOW")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog")) shouldBe FogQuality.High
        FogQuality.of(Switches.parse("?fog=thick&nofog")) shouldBe FogQuality.Off
        FogQuality.named(null) shouldBe null
    }
})
