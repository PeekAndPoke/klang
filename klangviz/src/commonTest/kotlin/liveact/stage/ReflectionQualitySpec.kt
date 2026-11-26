/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class ReflectionQualitySpec : StringSpec({

    "four qualities, off, low, mid and high, by their words; on is mid; a word that names none is none" {
        ReflectionQuality.entries.map { it.word } shouldBe listOf("off", "low", "mid", "high")
        ReflectionQuality.named("off") shouldBe ReflectionQuality.Off
        ReflectionQuality.named("low") shouldBe ReflectionQuality.Low
        ReflectionQuality.named("mid") shouldBe ReflectionQuality.Mid
        ReflectionQuality.named("high") shouldBe ReflectionQuality.High
        ReflectionQuality.named("on") shouldBe ReflectionQuality.Mid
        ReflectionQuality.named("medium") shouldBe null
        ReflectionQuality.named(null) shouldBe null
    }

    "mid is the reflections as they were; low draws a quarter of the pixels; high all of them, every frame, up to 4K" {
        ReflectionQuality.Mid.size shouldBe 1.0
        ReflectionQuality.Mid.budget shouldBe 0.5
        ReflectionQuality.Mid.mostPixels shouldBe 1920.0 * 1200.0
        ReflectionQuality.Low.size shouldBe 0.5
        ReflectionQuality.Low.budget shouldBe ReflectionQuality.Mid.budget
        ReflectionQuality.High.size shouldBe 1.0
        ReflectionQuality.High.budget shouldBe 1.0
        ReflectionQuality.High.mostPixels shouldBe 3840.0 * 2160.0
        ReflectionQuality.Off.size shouldBe 0.0
    }

    "the address's reflections by their word; without it, empty or with another word, mid" {
        ReflectionQuality.of(Switches.parse("?reflections=off")) shouldBe ReflectionQuality.Off
        ReflectionQuality.of(Switches.parse("?reflections=low")) shouldBe ReflectionQuality.Low
        ReflectionQuality.of(Switches.parse("?reflections=high&seed=2")) shouldBe ReflectionQuality.High
        ReflectionQuality.of(Switches.parse("?reflections=on&seed=2")) shouldBe ReflectionQuality.Mid
        ReflectionQuality.of(Switches.parse("?reflections")) shouldBe ReflectionQuality.Mid
        ReflectionQuality.of(Switches.parse("?reflections=none")) shouldBe ReflectionQuality.Mid
        ReflectionQuality.of(Switches.parse("")) shouldBe ReflectionQuality.Mid
    }

    "the fog has no say in them" {
        ReflectionQuality.of(Switches.parse("?fog=low")) shouldBe ReflectionQuality.Mid
        ReflectionQuality.of(Switches.parse("?nofog")) shouldBe ReflectionQuality.Mid
        ReflectionQuality.of(Switches.parse("?fog=off&reflections=off")) shouldBe ReflectionQuality.Off
    }
})
