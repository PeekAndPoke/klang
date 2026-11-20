/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.GhostLight

private fun near(x: Double) = x plusOrMinus 1e-9

/** Each of three colour shares near the other's. */
private infix fun Triple<Double, Double, Double>.shouldBeNear(other: Triple<Double, Double, Double>) {
    first shouldBe near(other.first)
    second shouldBe near(other.second)
    third shouldBe near(other.third)
}

private val DAY = WorldTime(on = 1.0, night = 0.0, ember = 0.0)
private val NIGHT = WorldTime(on = 1.0, night = 1.0, ember = 0.0)
private val EMBER = WorldTime(on = 1.0, night = 1.0, ember = 1.0)
private val GHOSTS = WorldTime(on = 1.0, night = 1.0, ember = 0.0, ghosts = 1.0)

class WorldLightSpec : StringSpec({

    "with the time off every value is the one the stage was made with, whatever night and ember say" {
        for (off in listOf(WorldTime.OFF, WorldTime(on = 0.0, night = 1.0, ember = 1.0), WorldTime(on = 0.0, night = 0.4, ember = 0.0))) {
            WorldLight.sunStrength(off) shouldBe 1.0
            WorldLight.sunSize(off) shouldBe 1.0
            WorldLight.sunHeight(off) shouldBe 1.3
            WorldLight.sunDimming(off) shouldBe 1.0
            WorldLight.sunColor(off, made = 0x123456, front = 0xff8420, frontLevel = 1.0) shouldBe 0x123456
            WorldLight.frontScale(off) shouldBe 1.0
            WorldLight.frontColor(off, made = 0xff1408) shouldBe 0xff1408
            WorldLight.frontSoftness(off) shouldBe 1.0
            WorldLight.backLean(off) shouldBe 0.0
            WorldLight.backScale(off) shouldBe 1.0
            WorldLight.fillLean(off) shouldBe 0.0
            WorldLight.fillScale(off) shouldBe 1.0
            WorldLight.hazeTint(off) shouldBe Triple(1.0, 1.0, 1.0)
            WorldLight.hazeThickness(off) shouldBe 1.0
            WorldLight.hazeBase(off) shouldBe Triple(0.006, 0.006, 0.009)
            WorldLight.coatRoughness(off) shouldBe 0.05
            WorldLight.gold(off) shouldBe Triple(0.9, 0.64, 0.26)
            WorldLight.roomSun(off) shouldBe Triple(1.6, 0.62, 0.2)
            WorldLight.beyond(off) shouldBe 0.0
            WorldLight.wallCold(off) shouldBe 0.0
            WorldLight.wallLight(off) shouldBe 1.0
        }
    }

    "the light on the walls: by night leaning cold and dimmed to half; by day and at ember as made" {
        WorldLight.wallCold(DAY) shouldBe near(0.0)
        WorldLight.wallLight(DAY) shouldBe near(1.0)
        WorldLight.wallCold(NIGHT) shouldBe near(0.8)
        WorldLight.wallLight(NIGHT) shouldBe near(0.5)
        WorldLight.wallCold(EMBER) shouldBe near(0.0)
        WorldLight.wallLight(EMBER) shouldBe near(1.0)
        // at dusk, halfway
        WorldLight.wallCold(WorldTime(on = 1.0, night = 0.5, ember = 0.0)) shouldBe near(0.4)
        WorldLight.wallLight(WorldTime(on = 0.5, night = 1.0, ember = 0.0)) shouldBe near(0.75)
    }

    "the sun: big, strong and amber by day; a small cold white moon, higher, by night; a deep red furnace at ember" {
        WorldLight.sunSize(DAY) shouldBe near(1.7)
        WorldLight.sunSize(NIGHT) shouldBe near(0.5)
        WorldLight.sunSize(EMBER) shouldBe near(1.1)
        WorldLight.sunStrength(DAY) shouldBe near(1.3)
        WorldLight.sunStrength(NIGHT) shouldBe near(0.9)
        WorldLight.sunStrength(EMBER) shouldBe near(1.6)
        WorldLight.sunHeight(NIGHT) shouldBe near(2.5)
        WorldLight.sunHeight(EMBER) shouldBe near(1.0)
        WorldLight.sunColor(DAY, made = 0, front = 0, frontLevel = 0.0) shouldBe WorldLight.DAY_SUN
        WorldLight.sunColor(NIGHT, made = 0, front = 0, frontLevel = 0.0) shouldBe WorldLight.MOON
        WorldLight.sunColor(EMBER, made = 0, front = 0, frontLevel = 0.0) shouldBe WorldLight.FURNACE
    }

    "the sun leans toward the front lamp's colour by day as made, the moon only a quarter as much" {
        // the full lamp, white: by day 0.7 of the way, by night 0.175
        WorldLight.sunColor(DAY, made = 0, front = 0xffffff, frontLevel = 1.0) shouldBe Sun.mix(from = WorldLight.DAY_SUN, to = 0xffffff, share = 0.7)
        WorldLight.sunColor(NIGHT, made = 0, front = 0x000000, frontLevel = 1.0) shouldBe Sun.mix(from = WorldLight.MOON, to = 0, share = 0.175)
        // a lamp at no number leans nothing
        WorldLight.sunColor(DAY, made = 0, front = 0xffffff, frontLevel = Double.NaN) shouldBe WorldLight.DAY_SUN
    }

    "by day the front lamp is weaker, warmer and its shadows soft; by night and at ember at full, leaning a little" {
        WorldLight.frontScale(DAY) shouldBe near(0.5)
        WorldLight.frontScale(NIGHT) shouldBe near(1.0)
        WorldLight.frontScale(EMBER) shouldBe near(1.0)
        WorldLight.frontSoftness(DAY) shouldBe near(5.0)
        WorldLight.frontSoftness(NIGHT) shouldBe near(1.0)
        WorldLight.frontColor(DAY, made = 0xff1408) shouldBe Sun.mix(from = 0xff1408, to = WorldLight.DAY_FRONT, share = 0.5)
        WorldLight.frontColor(NIGHT, made = 0xff1408) shouldBe Sun.mix(from = 0xff1408, to = WorldLight.NIGHT_FRONT, share = 0.3)
        WorldLight.frontColor(EMBER, made = 0xff1408) shouldBe Sun.mix(from = 0xff1408, to = WorldLight.EMBER_FRONT, share = 0.25)
        WorldLight.sunDimming(DAY) shouldBe near(0.4)
        WorldLight.sunDimming(NIGHT) shouldBe near(1.0)
    }

    "the haze: warm and lit by day, cold and thinner by night, red smoke at ember" {
        val (dr, _, db) = WorldLight.hazeTint(DAY)
        val (nr, _, nb) = WorldLight.hazeTint(NIGHT)
        val (er, eg, eb) = WorldLight.hazeTint(EMBER)

        dr shouldBeGreaterThan db
        nb shouldBeGreaterThan nr
        er shouldBeGreaterThan 2 * eg
        er shouldBeGreaterThan 2 * eb
        WorldLight.hazeThickness(NIGHT) shouldBeLessThan WorldLight.hazeThickness(DAY)
        WorldLight.hazeThickness(EMBER) shouldBeGreaterThan WorldLight.hazeThickness(DAY)
    }

    "the lacquer: glossy and rich gold by day, a duller coat and pale cold gold by night, red gold at ember" {
        WorldLight.coatRoughness(DAY) shouldBe near(0.05)
        WorldLight.coatRoughness(NIGHT) shouldBe near(0.28)
        WorldLight.gold(NIGHT).let { (r, _, b) -> b shouldBeGreaterThan r }
        WorldLight.gold(EMBER).let { (r, g, _) -> r shouldBeGreaterThan 3 * g }
        WorldLight.roomSun(NIGHT).let { (r, g, b) -> (r + g + b) shouldBeLessThan 0.4 * (2.2 + 0.95 + 0.3) }
    }

    "the cold world: none by day and at dusk's start, all of it by night and at ember, smoothly between" {
        WorldLight.beyond(DAY) shouldBe near(0.0)
        WorldLight.beyond(WorldTime(on = 1.0, night = 0.35, ember = 0.0)) shouldBe near(0.0)
        WorldLight.beyond(WorldTime(on = 1.0, night = 0.6, ember = 0.0)) shouldBe (0.5 plusOrMinus 1e-9)
        WorldLight.beyond(NIGHT) shouldBe near(1.0)
        WorldLight.beyond(EMBER) shouldBe near(1.0)
        WorldLight.beyond(WorldTime(on = 0.5, night = 1.0, ember = 0.0)) shouldBe near(0.5)
    }

    "the ghosts: the front lamp and the fill give no light, the lamps behind a little, all in the colour's rim; no sun" {
        WorldLight.frontScale(GHOSTS) shouldBe 0.0
        WorldLight.fillScale(GHOSTS) shouldBe 0.0
        WorldLight.backScale(GHOSTS) shouldBe near(0.03)
        WorldLight.backLean(GHOSTS) shouldBe 1.0
        WorldLight.fillLean(GHOSTS) shouldBe 1.0
        WorldLight.lampTint(GHOSTS) shouldBe GhostLight.Cold.rim
        WorldLight.lampTint(GHOSTS.copy(ghostLight = GhostLight.Red)) shouldBe GhostLight.Red.rim
        WorldLight.sunStrength(GHOSTS) shouldBe 0.0
        WorldLight.wallLight(GHOSTS) shouldBe near(0.2)
        WorldLight.frontSoftness(GHOSTS) shouldBe 1.0
    }

    "the ghosts: the haze glows in the colour, the figures in its pale, the surface in its deep; the band mirrors little" {
        val red = GHOSTS.copy(ghostLight = GhostLight.Red)
        val (r, g, b) = GhostLight.Red.glow

        WorldLight.hazeBase(red) shouldBeNear Triple(WorldLight.GHOST_GLOW * r, WorldLight.GHOST_GLOW * g, WorldLight.GHOST_GLOW * b)
        WorldLight.hazeTint(red) shouldBe Triple(1.0, 1.0, 1.0)
        WorldLight.ghostPale(red) shouldBe GhostLight.Red.pale.let { (pr, pg, pb) ->
            Triple(WorldLight.GHOST_PALE * pr, WorldLight.GHOST_PALE * pg, WorldLight.GHOST_PALE * pb)
        }
        WorldLight.ghostDepth(red) shouldBe GhostLight.Red.depth
        WorldLight.roomLight(red) shouldBe near(0.35)
        WorldLight.roomSun(red) shouldBeNear Triple(WorldLight.GHOST_ROOM * r, WorldLight.GHOST_ROOM * g, WorldLight.GHOST_ROOM * b)
        WorldLight.roomLight(NIGHT) shouldBe 1.0
        WorldLight.roomLight(WorldTime.OFF) shouldBe 1.0
    }

    "the ghosts' figures are there all the time and as far as the time has gone over; none outside the ghosts" {
        WorldLight.ghostShare(GHOSTS) shouldBe 1.0
        WorldLight.ghostShare(GHOSTS.copy(ghosts = 0.25)) shouldBe 0.25
        WorldLight.ghostShare(GHOSTS.copy(on = 0.5)) shouldBe 0.5
        WorldLight.ghostShare(NIGHT) shouldBe 0.0
        WorldLight.ghostDepth(NIGHT) shouldBe Triple(0.0, 0.0, 0.0)
        // the windows open fully in the ghosts, also from the day, where the night opens none
        WorldLight.beyond(GHOSTS) shouldBe 1.0
        WorldLight.beyond(DAY.copy(ghosts = 1.0)) shouldBe 1.0
        WorldLight.beyond(DAY.copy(ghosts = 0.5)) shouldBe near(0.5)
        WorldLight.beyond(DAY) shouldBe 0.0
    }

    "in the ghosts the haze stays parted before the figures, but only part of the way: a veil of the glow stays" {
        WorldLight.ghostClearing(GHOSTS) shouldBe near(WorldLight.GHOST_CLEARING)
        WorldLight.ghostClearing(GHOSTS.copy(ghosts = 0.5)) shouldBe near(WorldLight.GHOST_CLEARING / 2)
        WorldLight.ghostClearing(NIGHT) shouldBe 0.0
        (WorldLight.GHOST_CLEARING < 1.0) shouldBe true
        (WorldLight.GHOST_CLEARING > 0.0) shouldBe true
    }

    "the turn into the ghosts is even: halfway, every value halfway between the night's and the ghosts'" {
        val half = NIGHT.copy(ghosts = 0.5)

        WorldLight.backScale(half) shouldBe near((0.8 + 0.03) / 2)
        WorldLight.sunStrength(half) shouldBe near(0.9 / 2)
        WorldLight.hazeThickness(half) shouldBe near((0.6 + 1.1) / 2)
        // night 1 to ghosts 5 halfway; ember 3 to ghosts 5 a quarter of the way
        WorldLight.blend(half, made = 9.0, day = 0.0, night = 1.0, ember = 3.0, ghosts = 5.0) shouldBe near(3.0)
        WorldLight.blend(EMBER.copy(ghosts = 0.25), made = 9.0, day = 0.0, night = 1.0, ember = 3.0, ghosts = 5.0) shouldBe
            near(3.5)
    }

    "values blend evenly from the day to the night to the ember, and from the stage as made by on" {
        val ends = { world: WorldTime, made: Double ->
            WorldLight.blend(world, made = made, day = 0.0, night = 1.0, ember = 3.0, ghosts = 5.0)
        }

        ends(WorldTime(on = 1.0, night = 0.5, ember = 0.0), 9.0) shouldBe near(0.5)
        ends(WorldTime(on = 1.0, night = 1.0, ember = 0.5), 9.0) shouldBe near(2.0)
        ends(WorldTime(on = 0.5, night = 0.0, ember = 0.0), 4.0) shouldBe near(2.0)
    }

    "the drift: day at 0 s, night at half the round, day again at its end; it rests at each end" {
        WorldLight.drift(t = 0.0) shouldBe near(0.0)
        WorldLight.drift(t = WorldLight.DRIFT_PERIOD / 2) shouldBe (1.0 plusOrMinus 1e-12)
        WorldLight.drift(t = WorldLight.DRIFT_PERIOD) shouldBe (0.0 plusOrMinus 1e-12)
        WorldLight.drift(t = WorldLight.DRIFT_PERIOD / 4) shouldBe (0.5 plusOrMinus 1e-12)
        // a tenth of the way from an end moves less than a plain cosine would: it rests there
        WorldLight.drift(t = WorldLight.DRIFT_PERIOD / 20) shouldBeLessThan 0.5 - 0.5 * kotlin.math.cos(kotlin.math.PI / 10)
        WorldLight.drift(t = 15.0, period = 30.0) shouldBe (1.0 plusOrMinus 1e-12)
    }

    "the drift at no time is the day; at no period, or none above 0, the period as made" {
        WorldLight.drift(t = Double.NaN) shouldBe near(0.0)
        WorldLight.drift(t = 60.0, period = Double.NaN) shouldBe WorldLight.drift(t = 60.0)
        WorldLight.drift(t = 60.0, period = 0.0) shouldBe WorldLight.drift(t = 60.0)
        WorldLight.drift(t = 60.0, period = Double.POSITIVE_INFINITY) shouldBe WorldLight.drift(t = 60.0)
    }
})
