/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime

class LookVariantsSpec : StringSpec({

    "without a switch the look is the one it was made with: the wall and the plain floor" {
        LookVariants.of(Switches.parse("")) shouldBe LookVariants(stage = StageLook.Wall, floor = FloorLook.Plain)
        LookVariants() shouldBe LookVariants.of(Switches.parse(""))
        LookVariants().haze shouldBe HazeLook.Whole
        LookVariants().eyes shouldBe EyesLook.Face
        LookVariants().skin shouldBe SkinLook.Suit
    }

    "?skin=lacquer is the lacquer; no switch, an empty one or another word the suit as made" {
        SkinLook.of(Switches.parse("?skin=lacquer")) shouldBe SkinLook.Lacquer
        SkinLook.of(Switches.parse("?skin")) shouldBe SkinLook.Suit
        SkinLook.of(Switches.parse("?skin=suit")) shouldBe SkinLook.Suit
        SkinLook.of(Switches.parse("?skin=gold")) shouldBe SkinLook.Suit
        LookVariants.of(Switches.parse("?skin=lacquer")) shouldBe LookVariants(skin = SkinLook.Lacquer)
        LookVariants.of(Switches.parse("?stage=mesh&floor=mirror&haze=parts&eyes=shadow&skin=lacquer")) shouldBe
            LookVariants(
                stage = StageLook.Mesh,
                floor = FloorLook.Mirror,
                haze = HazeLook.Parts,
                eyes = EyesLook.Shadow,
                skin = SkinLook.Lacquer,
            )
    }

    "the key l turns the skin over and leaves the rest; twice is back where it was; its line says it" {
        val start = LookVariants(stage = StageLook.Mesh, floor = FloorLook.Mirror, eyes = EyesLook.Shadow)

        start.nextSkin() shouldBe start.copy(skin = SkinLook.Lacquer)
        start.nextSkin().nextSkin() shouldBe start
        start.nextStage().skin shouldBe SkinLook.Suit
        start.nextSkin().nextEyes() shouldBe start.copy(skin = SkinLook.Lacquer, eyes = EyesLook.Face)
        LookVariants().skinLine() shouldBe "skin: suit"
        LookVariants(skin = SkinLook.Lacquer).skinLine() shouldBe "skin: lacquer"
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

    "?world= is the time of day: day, night, ember or drift; no switch, an empty one or another word off" {
        WorldLook.of(Switches.parse("?world=day")) shouldBe WorldLook.Day
        WorldLook.of(Switches.parse("?world=night")) shouldBe WorldLook.Night
        WorldLook.of(Switches.parse("?world=ember")) shouldBe WorldLook.Ember
        WorldLook.of(Switches.parse("?world=drift")) shouldBe WorldLook.Drift
        WorldLook.of(Switches.parse("?world")) shouldBe WorldLook.Off
        WorldLook.of(Switches.parse("?world=dusk")) shouldBe WorldLook.Off
        LookVariants().world shouldBe WorldLook.Off
        LookVariants.of(Switches.parse("?stage=mesh&world=ember")) shouldBe LookVariants(stage = StageLook.Mesh, world = WorldLook.Ember)
    }

    "the key n steps through off, day, night, ember and drift, and back to off; it leaves the rest; its line says it" {
        val start = LookVariants(stage = StageLook.Mesh, skin = SkinLook.Lacquer)
        val seen = generateSequence(start) { it.nextWorld() }.take(6).map { it.world }.toList()

        seen shouldBe listOf(WorldLook.Off, WorldLook.Day, WorldLook.Night, WorldLook.Ember, WorldLook.Drift, WorldLook.Off)
        start.nextWorld() shouldBe start.copy(world = WorldLook.Day)
        start.nextWorld().worldLine() shouldBe "world: day"
        start.copy(world = WorldLook.Drift).worldLine() shouldBe "world: drift"
        LookVariants().worldLine() shouldBe "world: off"
        LookVariants(world = WorldLook.Night).worldLine() shouldBe "world: night (sun, haze, cold world with the fence only)"
    }

    "what time of day each look asks for: off none, day, night, ember, and the drift by the show's time" {
        WorldLook.Off.target(t = 10.0) shouldBe WorldTime.OFF
        WorldLook.Day.target(t = 10.0) shouldBe WorldTime(on = 1.0, night = 0.0, ember = 0.0)
        WorldLook.Night.target(t = 10.0) shouldBe WorldTime(on = 1.0, night = 1.0, ember = 0.0)
        WorldLook.Ember.target(t = 10.0) shouldBe WorldTime(on = 1.0, night = 1.0, ember = 1.0)
        WorldLook.Drift.target(t = 0.0) shouldBe WorldTime(on = 1.0, night = 0.0, ember = 0.0)
        WorldLook.Drift.target(t = 15.0, period = 30.0).night shouldBe (1.0 plusOrMinus 1e-12)
        WorldLook.Drift.target(t = 60.0).night shouldBe WorldLight.drift(t = 60.0, period = WorldLight.DRIFT_PERIOD)
    }

    "?drift=30 sets the drift's round; without it, or with no number above 1 s, four minutes" {
        WorldLook.period(Switches.parse("?drift=30")) shouldBe 30.0
        WorldLook.period(Switches.parse("")) shouldBe WorldLight.DRIFT_PERIOD
        WorldLook.period(Switches.parse("?drift")) shouldBe WorldLight.DRIFT_PERIOD
        WorldLook.period(Switches.parse("?drift=0.5")) shouldBe WorldLight.DRIFT_PERIOD
        WorldLook.period(Switches.parse("?drift=NaN")) shouldBe WorldLight.DRIFT_PERIOD
        WorldLook.period(Switches.parse("?drift=Infinity")) shouldBe WorldLight.DRIFT_PERIOD
        WorldLight.DRIFT_PERIOD shouldBe 240.0
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

    "?underworld=on is the underworld; no switch, an empty one or another word off" {
        UnderworldLook.of(Switches.parse("?underworld=on")) shouldBe UnderworldLook.On
        UnderworldLook.of(Switches.parse("?underworld")) shouldBe UnderworldLook.Off
        UnderworldLook.of(Switches.parse("?underworld=off")) shouldBe UnderworldLook.Off
        UnderworldLook.of(Switches.parse("?underworld=yes")) shouldBe UnderworldLook.Off
        LookVariants().underworld shouldBe UnderworldLook.Off
        LookVariants.of(Switches.parse("?floor=mirror&underworld=on")) shouldBe
            LookVariants(floor = FloorLook.Mirror, underworld = UnderworldLook.On)
        UnderworldLook.FADE_IN shouldBe 2.0
        UnderworldLook.FADE_OUT shouldBe 3.0
    }

    "the key v turns the underworld over and leaves the rest; it shows only where the mirror is drawn; its line says why not" {
        val start = LookVariants(stage = StageLook.Mesh, floor = FloorLook.Mirror, world = WorldLook.Night)
        val under = start.nextUnderworld()

        under shouldBe start.copy(underworld = UnderworldLook.On)
        under.nextUnderworld() shouldBe start
        under.underworlds(ReflectionQuality.On) shouldBe true
        under.underworlds(ReflectionQuality.Off) shouldBe false
        start.underworlds(ReflectionQuality.On) shouldBe false
        under.copy(floor = FloorLook.Plain).underworlds(ReflectionQuality.On) shouldBe false
        start.underworldLine(ReflectionQuality.On) shouldBe "underworld: off"
        start.underworldLine(ReflectionQuality.Off) shouldBe "underworld: off"
        under.underworldLine(ReflectionQuality.On) shouldBe "underworld: on"
        under.underworldLine(ReflectionQuality.Off) shouldBe "underworld: on (reflections off)"
        under.copy(floor = FloorLook.Plain).underworldLine(ReflectionQuality.On) shouldBe "underworld: on (with the mirror floor only)"
    }

    "?ripple= is how uneven the stone is: none, faint, gentle or strong; no switch, an empty one or another word gentle" {
        RippleLook.of(Switches.parse("?ripple=none")) shouldBe RippleLook.None
        RippleLook.of(Switches.parse("?ripple=faint")) shouldBe RippleLook.Faint
        RippleLook.of(Switches.parse("?ripple=gentle")) shouldBe RippleLook.Gentle
        RippleLook.of(Switches.parse("?ripple=strong")) shouldBe RippleLook.Strong
        RippleLook.of(Switches.parse("?ripple")) shouldBe RippleLook.Gentle
        RippleLook.of(Switches.parse("?ripple=wild")) shouldBe RippleLook.Gentle
        LookVariants().ripple shouldBe RippleLook.Gentle
        LookVariants.NEW.ripple shouldBe RippleLook.Gentle
        LookVariants.of(Switches.parse("?look=new&ripple=none")) shouldBe LookVariants.NEW.copy(ripple = RippleLook.None)
    }

    "the levels: none flat, faint half the gentle broad ripple, strong the ripple as first made (both layers, 0.006)" {
        (RippleLook.None.broad to RippleLook.None.fine) shouldBe (0.0 to 0.0)
        RippleLook.Faint.fine shouldBe 0.0
        RippleLook.Gentle.fine shouldBe 0.0
        RippleLook.Faint.broad shouldBe (RippleLook.Gentle.broad / 2 plusOrMinus 1e-4)
        RippleLook.Gentle.broad shouldBe (0.006 / 4 plusOrMinus 1e-12)
        (RippleLook.Strong.broad to RippleLook.Strong.fine) shouldBe (0.0 to 0.006)
    }

    "the key b steps through none, faint, gentle and strong, and back to none; it leaves the rest; its line says it" {
        val start = LookVariants.NEW
        val seen = generateSequence(start) { it.nextRipple() }.take(5).map { it.ripple }.toList()

        seen shouldBe listOf(RippleLook.Gentle, RippleLook.Strong, RippleLook.None, RippleLook.Faint, RippleLook.Gentle)
        start.nextRipple() shouldBe start.copy(ripple = RippleLook.Strong)
        start.copy(ripple = RippleLook.Faint).rippleLine(ReflectionQuality.On) shouldBe "floor: faint ripple"
        start.rippleLine(ReflectionQuality.Off) shouldBe "floor: gentle ripple (reflections off)"
        LookVariants().rippleLine(ReflectionQuality.On) shouldBe "floor: gentle ripple (with the mirror floor only)"
        LookVariants().rippleLine(ReflectionQuality.Off) shouldBe "floor: gentle ripple (with the mirror floor only)"
    }

    "?blur= is how blurred the reflection is: crisp, soft or stone; no switch, an empty one or another word crisp" {
        BlurLook.of(Switches.parse("?blur=crisp")) shouldBe BlurLook.Crisp
        BlurLook.of(Switches.parse("?blur=soft")) shouldBe BlurLook.Soft
        BlurLook.of(Switches.parse("?blur=stone")) shouldBe BlurLook.Stone
        BlurLook.of(Switches.parse("?blur")) shouldBe BlurLook.Crisp
        BlurLook.of(Switches.parse("?blur=sharp")) shouldBe BlurLook.Crisp
        LookVariants().blur shouldBe BlurLook.Crisp
        LookVariants.NEW.blur shouldBe BlurLook.Crisp
        LookVariants.of(Switches.parse("?look=new&blur=crisp")) shouldBe LookVariants.NEW.copy(blur = BlurLook.Crisp)
    }

    "the blur's cones: crisp narrow, soft between, stone the cone the floor was first made with" {
        BlurLook.entries.map { it.cone } shouldBe listOf(0.004, 0.012, 0.03)
    }

    "?stone=marble is the marble; no switch, an empty one or another word the plain stone" {
        StoneLook.of(Switches.parse("?stone=marble")) shouldBe StoneLook.Marble
        StoneLook.of(Switches.parse("?stone")) shouldBe StoneLook.Plain
        StoneLook.of(Switches.parse("?stone=plain")) shouldBe StoneLook.Plain
        StoneLook.of(Switches.parse("?stone=granite")) shouldBe StoneLook.Plain
        LookVariants().stone shouldBe StoneLook.Plain
        LookVariants.NEW.stone shouldBe StoneLook.Plain
        LookVariants.of(Switches.parse("?look=new&blur=crisp&stone=marble")) shouldBe
            LookVariants.NEW.copy(blur = BlurLook.Crisp, stone = StoneLook.Marble)
    }

    "the key k turns the stone over and leaves the rest; twice is back where it was; its line says it" {
        val start = LookVariants.NEW

        start.nextStone() shouldBe start.copy(stone = StoneLook.Marble)
        start.nextStone().nextStone() shouldBe start
        start.nextStone().stoneLine(ReflectionQuality.On) shouldBe "floor: marble"
        start.stoneLine(ReflectionQuality.On) shouldBe "floor: plain"
        start.nextStone().stoneLine(ReflectionQuality.Off) shouldBe "floor: marble (reflections off)"
        LookVariants(stone = StoneLook.Marble).stoneLine(ReflectionQuality.On) shouldBe "floor: marble (with the mirror floor only)"
    }

    "?edge=on is the stage's outline; no switch, an empty one or another word off; the key j turns it over" {
        OutlineLook.of(Switches.parse("?edge=on")) shouldBe OutlineLook.On
        OutlineLook.of(Switches.parse("?edge")) shouldBe OutlineLook.Off
        OutlineLook.of(Switches.parse("?edge=yes")) shouldBe OutlineLook.Off
        LookVariants().outline shouldBe OutlineLook.Off
        LookVariants.NEW.outline shouldBe OutlineLook.Off
        LookVariants.of(Switches.parse("?look=new&edge=on")) shouldBe LookVariants.NEW.copy(outline = OutlineLook.On)
        LookVariants.NEW.nextOutline() shouldBe LookVariants.NEW.copy(outline = OutlineLook.On)
        LookVariants.NEW.nextOutline().nextOutline() shouldBe LookVariants.NEW
        LookVariants.NEW.nextOutline().outlineLine() shouldBe "stage outline: on"
        LookVariants().outlineLine() shouldBe "stage outline: off"
    }

    "the key c steps through crisp, soft and stone, and back to crisp; it leaves the rest; its line says it" {
        val start = LookVariants.NEW
        val seen = generateSequence(start) { it.nextBlur() }.take(4).map { it.blur }.toList()

        seen shouldBe listOf(BlurLook.Crisp, BlurLook.Soft, BlurLook.Stone, BlurLook.Crisp)
        start.nextBlur() shouldBe start.copy(blur = BlurLook.Soft)
        start.copy(blur = BlurLook.Soft).blurLine(ReflectionQuality.On) shouldBe "floor: soft reflection"
        start.blurLine(ReflectionQuality.Off) shouldBe "floor: crisp reflection (reflections off)"
        LookVariants().blurLine(ReflectionQuality.On) shouldBe "floor: crisp reflection (with the mirror floor only)"
    }

    "the new look is the fence, the mirror, the parting haze, the eyes in the shadow, the lacquer and the drift; no underworld" {
        LookVariants.NEW shouldBe LookVariants(
            stage = StageLook.Mesh,
            floor = FloorLook.Mirror,
            haze = HazeLook.Parts,
            eyes = EyesLook.Shadow,
            skin = SkinLook.Lacquer,
            world = WorldLook.Drift,
            underworld = UnderworldLook.Off,
            ripple = RippleLook.Gentle,
            blur = BlurLook.Crisp,
            stone = StoneLook.Plain,
            outline = OutlineLook.Off,
        )
    }

    "?look=new starts with the new look; a single variant's switch beside it wins; another word is the look as made" {
        LookVariants.of(Switches.parse("?look=new")) shouldBe LookVariants.NEW
        LookVariants.of(Switches.parse("?look=new&world=night")) shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        LookVariants.of(Switches.parse("?look=new&world=night&underworld=on")) shouldBe
            LookVariants.NEW.copy(world = WorldLook.Night, underworld = UnderworldLook.On)
        LookVariants.of(Switches.parse("?look=new&stage=wall&skin")) shouldBe
            LookVariants.NEW.copy(stage = StageLook.Wall, skin = SkinLook.Suit)
        LookVariants.of(Switches.parse("?look")) shouldBe LookVariants()
        LookVariants.of(Switches.parse("?look=old")) shouldBe LookVariants()
        LookVariants.asksNew(Switches.parse("?look=new")) shouldBe true
        LookVariants.asksNew(Switches.parse("?look=today")) shouldBe false
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

    "the mirror is drawn where it is chosen and the reflections are on, never on the plain floor" {
        val mirror = LookVariants(floor = FloorLook.Mirror)

        ReflectionQuality.entries.filter { mirror.mirrors(it) } shouldBe listOf(ReflectionQuality.On)
        ReflectionQuality.entries.filter { LookVariants().mirrors(it) } shouldBe emptyList()
        ReflectionQuality.entries.filter { LookVariants.NEW.mirrors(it) } shouldBe listOf(ReflectionQuality.On)
    }

    "the lines say the variant in a few words, and why a chosen mirror is not drawn" {
        LookVariants().stageLine() shouldBe "stage: wall"
        LookVariants(stage = StageLook.Mesh).stageLine() shouldBe "stage: fence"
        LookVariants().floorLine(ReflectionQuality.On) shouldBe "floor: plain"
        LookVariants().floorLine(ReflectionQuality.Off) shouldBe "floor: plain"
        LookVariants(floor = FloorLook.Mirror).floorLine(ReflectionQuality.On) shouldBe "floor: mirror"
        LookVariants(floor = FloorLook.Mirror).floorLine(ReflectionQuality.Off) shouldBe "floor: mirror (reflections off)"
    }
})
