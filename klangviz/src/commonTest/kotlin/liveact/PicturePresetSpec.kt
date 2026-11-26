/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality

class PicturePresetSpec : StringSpec({

    "four presets by their words, lightest first; a word that names none is none" {
        PicturePreset.entries.map { it.word } shouldBe listOf("low", "medium", "high", "max")
        PicturePreset.named("max") shouldBe PicturePreset.Max
        PicturePreset.named("custom") shouldBe null
        PicturePreset.named(null) shouldBe null
    }

    "what each sets: medium the picture the show starts with, high the same at 2x, low lighter, max all" {
        PicturePreset.Medium.let { listOf(it.samples, it.fog, it.shadows, it.reflections) } shouldBe
            listOf(1.0, FogQuality.High, ShadowQuality.High, ReflectionQuality.Mid)
        PicturePreset.High.let { listOf(it.samples, it.fog, it.shadows, it.reflections) } shouldBe
            listOf(2.0, FogQuality.High, ShadowQuality.High, ReflectionQuality.Mid)
        PicturePreset.Low.let { listOf(it.samples, it.fog, it.shadows, it.reflections) } shouldBe
            listOf(1.0, FogQuality.Low, ShadowQuality.Low, ReflectionQuality.Off)
        PicturePreset.Max.let { listOf(it.samples, it.fog, it.shadows, it.reflections) } shouldBe
            listOf(4.0, FogQuality.High, ShadowQuality.High, ReflectionQuality.High)

        // the show without any switch starts as medium
        val none = Switches()

        PicturePreset.matching(Oversampling.of(none), FogQuality.of(none), ShadowQuality.of(none), ReflectionQuality.of(none)) shouldBe
            PicturePreset.Medium
    }

    "four settings that are a preset are that preset; any other combination is custom" {
        for (preset in PicturePreset.entries) {
            PicturePreset.matching(preset.samples, preset.fog, preset.shadows, preset.reflections) shouldBe preset
        }

        PicturePreset.matching(1.0, FogQuality.High, ShadowQuality.High, ReflectionQuality.Low) shouldBe null
        PicturePreset.matching(0.5, FogQuality.Low, ShadowQuality.Low, ReflectionQuality.Off) shouldBe null
        PicturePreset.matching(2.0, FogQuality.Off, ShadowQuality.High, ReflectionQuality.Mid) shouldBe null
    }

    "the address's quality sets the four; each one's own switch wins over it" {
        val high = Switches.parse("?quality=high")

        Oversampling.of(high) shouldBe 2.0
        FogQuality.of(high) shouldBe FogQuality.High
        ReflectionQuality.of(high) shouldBe ReflectionQuality.Mid

        val low = Switches.parse("?quality=low")

        Oversampling.of(low) shouldBe 1.0
        FogQuality.of(low) shouldBe FogQuality.Low
        ShadowQuality.of(low) shouldBe ShadowQuality.Low
        ReflectionQuality.of(low) shouldBe ReflectionQuality.Off

        val mixed = Switches.parse("?quality=low&fog=high&oversample=4&shadows=high&reflections=mid")

        Oversampling.of(mixed) shouldBe 4.0
        FogQuality.of(mixed) shouldBe FogQuality.High
        ShadowQuality.of(mixed) shouldBe ShadowQuality.High
        ReflectionQuality.of(mixed) shouldBe ReflectionQuality.Mid

        FogQuality.of(Switches.parse("?quality=max&nofog")) shouldBe FogQuality.Off
        Oversampling.of(Switches.parse("?quality=max&oversample=nonsense")) shouldBe Oversampling.PLAIN
        Oversampling.of(Switches.parse("?quality=nonsense")) shouldBe Oversampling.PLAIN
        Oversampling.of(Switches.parse("?quality=max")) shouldBe 4.0
    }
})
