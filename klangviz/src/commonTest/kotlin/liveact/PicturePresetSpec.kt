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

    "what each sets: high the picture the show starts with, medium and low lighter, max the most" {
        PicturePreset.Low.let { listOf(it.fog, it.shadows, it.reflections) } shouldBe
            listOf(FogQuality.Low, ShadowQuality.Low, ReflectionQuality.Off)
        PicturePreset.Medium.let { listOf(it.fog, it.shadows, it.reflections) } shouldBe
            listOf(FogQuality.High, ShadowQuality.Low, ReflectionQuality.Low)
        PicturePreset.High.let { listOf(it.fog, it.shadows, it.reflections) } shouldBe
            listOf(FogQuality.High, ShadowQuality.High, ReflectionQuality.Mid)
        PicturePreset.Max.let { listOf(it.fog, it.shadows, it.reflections) } shouldBe
            listOf(FogQuality.High, ShadowQuality.High, ReflectionQuality.High)

        // the show without any switch starts as high, at any oversampling
        val none = Switches()

        PicturePreset.matching(FogQuality.of(none), ShadowQuality.of(none), ReflectionQuality.of(none)) shouldBe PicturePreset.High
    }

    "three settings that are a preset are that preset; any other combination is custom" {
        for (preset in PicturePreset.entries) {
            PicturePreset.matching(preset.fog, preset.shadows, preset.reflections) shouldBe preset
        }

        PicturePreset.matching(FogQuality.High, ShadowQuality.High, ReflectionQuality.Low) shouldBe null
        PicturePreset.matching(FogQuality.Off, ShadowQuality.Low, ReflectionQuality.Off) shouldBe null
        PicturePreset.matching(FogQuality.Low, ShadowQuality.Low, ReflectionQuality.Low) shouldBe null
    }

    "the address's quality sets the three; each one's own switch wins over it; the oversampling is not part of it" {
        val low = Switches.parse("?quality=low")

        FogQuality.of(low) shouldBe FogQuality.Low
        ShadowQuality.of(low) shouldBe ShadowQuality.Low
        ReflectionQuality.of(low) shouldBe ReflectionQuality.Off
        Oversampling.parse(low[Switch.Oversample]) shouldBe Oversampling.PLAIN

        val medium = Switches.parse("?quality=medium")

        ShadowQuality.of(medium) shouldBe ShadowQuality.Low
        ReflectionQuality.of(medium) shouldBe ReflectionQuality.Low

        val mixed = Switches.parse("?quality=low&fog=high&shadows=high&reflections=mid")

        FogQuality.of(mixed) shouldBe FogQuality.High
        ShadowQuality.of(mixed) shouldBe ShadowQuality.High
        ReflectionQuality.of(mixed) shouldBe ReflectionQuality.Mid

        FogQuality.of(Switches.parse("?quality=max&nofog")) shouldBe FogQuality.Off
        // a word of its own that names none leaves the quality's
        FogQuality.of(Switches.parse("?quality=low&fog=thick")) shouldBe FogQuality.Low
        ReflectionQuality.of(Switches.parse("?quality=max")) shouldBe ReflectionQuality.High
        ReflectionQuality.of(Switches.parse("?quality=nonsense")) shouldBe ReflectionQuality.Mid
    }
})
