/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class LookVariantsSpec : StringSpec({

    "without a switch the look is the one it was made with: the wall and the plain floor" {
        LookVariants.of(Switches.parse("")) shouldBe LookVariants(stage = StageLook.Wall, floor = FloorLook.Plain)
        LookVariants() shouldBe LookVariants.of(Switches.parse(""))
        LookVariants().haze shouldBe HazeLook.Whole
        LookVariants().eyes shouldBe EyesLook.Face
    }

    "?haze=parts and ?eyes=shadow are the new variants; no switch, an empty one or another word the look as made" {
        HazeLook.of(Switches.parse("?haze=parts")) shouldBe HazeLook.Parts
        HazeLook.of(Switches.parse("?haze")) shouldBe HazeLook.Whole
        HazeLook.of(Switches.parse("?haze=whole")) shouldBe HazeLook.Whole
        HazeLook.of(Switches.parse("?haze=clear")) shouldBe HazeLook.Whole
        EyesLook.of(Switches.parse("?eyes=shadow")) shouldBe EyesLook.Shadow
        EyesLook.of(Switches.parse("?eyes")) shouldBe EyesLook.Face
        EyesLook.of(Switches.parse("?eyes=face")) shouldBe EyesLook.Face
        EyesLook.of(Switches.parse("?eyes=wall")) shouldBe EyesLook.Face
        LookVariants.of(Switches.parse("?stage=mesh&haze=parts&eyes=shadow")) shouldBe
            LookVariants(stage = StageLook.Mesh, floor = FloorLook.Plain, haze = HazeLook.Parts, eyes = EyesLook.Shadow)
    }

    "the keys h and i turn the haze and the eyes over and leave the rest; twice is back where it was" {
        val start = LookVariants(stage = StageLook.Mesh, floor = FloorLook.Mirror)

        start.nextHaze() shouldBe start.copy(haze = HazeLook.Parts)
        start.nextHaze().nextHaze() shouldBe start
        start.nextEyes() shouldBe start.copy(eyes = EyesLook.Shadow)
        start.nextEyes().nextEyes() shouldBe start
        start.nextHaze().nextEyes().nextStage() shouldBe
            LookVariants(stage = StageLook.Wall, floor = FloorLook.Mirror, haze = HazeLook.Parts, eyes = EyesLook.Shadow)
    }

    "the haze parts only where it is chosen and the fence stands" {
        LookVariants(stage = StageLook.Mesh, haze = HazeLook.Parts).partsHaze shouldBe true
        LookVariants(stage = StageLook.Wall, haze = HazeLook.Parts).partsHaze shouldBe false
        LookVariants(stage = StageLook.Mesh, haze = HazeLook.Whole).partsHaze shouldBe false
        LookVariants().partsHaze shouldBe false
    }

    "the haze's and the eyes' lines, and why a chosen parting does nothing" {
        val parts = LookVariants(stage = StageLook.Mesh, haze = HazeLook.Parts)

        LookVariants().hazeLine(FogQuality.High) shouldBe "haze: whole"
        parts.hazeLine(FogQuality.High) shouldBe "haze: parts"
        parts.hazeLine(FogQuality.Low) shouldBe "haze: parts"
        parts.hazeLine(FogQuality.Off) shouldBe "haze: parts (no haze at fog off)"
        parts.copy(stage = StageLook.Wall).hazeLine(FogQuality.High) shouldBe "haze: parts (with the fence only)"
        LookVariants().eyesLine() shouldBe "eyes: face"
        LookVariants(eyes = EyesLook.Shadow).eyesLine() shouldBe "eyes: shadow"
    }

    "?floor=mirror is the mirror; no switch, an empty one or another word the plain floor" {
        FloorLook.of(Switches.parse("?floor=mirror")) shouldBe FloorLook.Mirror
        FloorLook.of(Switches.parse("?floor")) shouldBe FloorLook.Plain
        FloorLook.of(Switches.parse("?floor=plain")) shouldBe FloorLook.Plain
        FloorLook.of(Switches.parse("?floor=stone")) shouldBe FloorLook.Plain
    }

    "the address's switches are the starting values, each on its own" {
        LookVariants.of(Switches.parse("?stage=mesh&floor=mirror")) shouldBe
            LookVariants(stage = StageLook.Mesh, floor = FloorLook.Mirror)
        LookVariants.of(Switches.parse("?floor=mirror")) shouldBe LookVariants(stage = StageLook.Wall, floor = FloorLook.Mirror)
        LookVariants.of(Switches.parse("?stage=mesh")) shouldBe LookVariants(stage = StageLook.Mesh, floor = FloorLook.Plain)
    }

    "a key turns one variant over and leaves the other; twice is back where it was" {
        val start = LookVariants()

        start.nextStage() shouldBe LookVariants(stage = StageLook.Mesh, floor = FloorLook.Plain)
        start.nextStage().nextStage() shouldBe start
        start.nextFloor() shouldBe LookVariants(stage = StageLook.Wall, floor = FloorLook.Mirror)
        start.nextFloor().nextFloor() shouldBe start
        start.nextStage().nextFloor() shouldBe LookVariants(stage = StageLook.Mesh, floor = FloorLook.Mirror)
    }

    "the mirror is drawn where it is chosen and the fog is medium or finer, never on the plain floor" {
        val mirror = LookVariants(floor = FloorLook.Mirror)

        FogQuality.entries.filter { mirror.mirrors(it) } shouldBe listOf(FogQuality.Medium, FogQuality.High, FogQuality.XHigh)
        FogQuality.entries.filter { LookVariants().mirrors(it) } shouldBe emptyList()
    }

    "the lines say the variant in a few words, and why a chosen mirror is not drawn" {
        LookVariants().stageLine() shouldBe "stage: wall"
        LookVariants(stage = StageLook.Mesh).stageLine() shouldBe "stage: fence"
        LookVariants().floorLine(FogQuality.High) shouldBe "floor: plain"
        LookVariants().floorLine(FogQuality.Off) shouldBe "floor: plain"
        LookVariants(floor = FloorLook.Mirror).floorLine(FogQuality.Medium) shouldBe "floor: mirror"
        LookVariants(floor = FloorLook.Mirror).floorLine(FogQuality.Low) shouldBe "floor: mirror (not drawn at fog low)"
        LookVariants(floor = FloorLook.Mirror).floorLine(FogQuality.Off) shouldBe "floor: mirror (not drawn at fog off)"
    }
})
