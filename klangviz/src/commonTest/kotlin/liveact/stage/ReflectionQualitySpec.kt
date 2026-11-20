/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class ReflectionQualitySpec : StringSpec({

    "two qualities, off and on, by their words; a word that names none is none" {
        ReflectionQuality.entries.map { it.word } shouldBe listOf("off", "on")
        ReflectionQuality.named("off") shouldBe ReflectionQuality.Off
        ReflectionQuality.named("on") shouldBe ReflectionQuality.On
        ReflectionQuality.named("low") shouldBe null
        ReflectionQuality.named(null) shouldBe null
    }

    "the address's reflections by their word; without it, empty or with another word, on" {
        ReflectionQuality.of(Switches.parse("?reflections=off")) shouldBe ReflectionQuality.Off
        ReflectionQuality.of(Switches.parse("?reflections=on&seed=2")) shouldBe ReflectionQuality.On
        ReflectionQuality.of(Switches.parse("?reflections")) shouldBe ReflectionQuality.On
        ReflectionQuality.of(Switches.parse("?reflections=none")) shouldBe ReflectionQuality.On
        ReflectionQuality.of(Switches.parse("")) shouldBe ReflectionQuality.On
    }

    "the fog has no say in them" {
        ReflectionQuality.of(Switches.parse("?fog=low")) shouldBe ReflectionQuality.On
        ReflectionQuality.of(Switches.parse("?nofog")) shouldBe ReflectionQuality.On
        ReflectionQuality.of(Switches.parse("?fog=off&reflections=off")) shouldBe ReflectionQuality.Off
    }
})
