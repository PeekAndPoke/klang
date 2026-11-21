/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import kotlin.random.Random

class ShowHelpersSpec : StringSpec({

    "the stroboscope switched off on the start screen sets nostrobe, and the address's other switches stay" {
        val switches = showSwitches(query = "?seed=7&still", strobe = false)

        switches.has(Switch.NoStrobe) shouldBe true
        switches[Switch.Seed] shouldBe "7"
        switches.has(Switch.Still) shouldBe true
    }

    "the stroboscope switched on takes nostrobe away, also where the address had it" {
        val switches = showSwitches(query = "?nostrobe&still", strobe = true)

        switches.has(Switch.NoStrobe) shouldBe false
        switches.has(Switch.Still) shouldBe true
    }

    "an address without switches gives only what the app decides: no line of text at the top, and the stroboscope" {
        showSwitches(query = "", strobe = true) shouldBe Switches.parse("nohud&look=new&opening=night")
        showSwitches(query = "?", strobe = false) shouldBe Switches.parse("nostrobe&nohud&look=new&opening=night")
    }

    "the line of text at the top shows only where the address asks for it with dev" {
        showSwitches(query = "?seed=3", strobe = true).has(Switch.NoHud) shouldBe true
        showSwitches(query = "?nohud", strobe = true) shouldBe Switches.parse("nohud&look=new&opening=night")
        showSwitches(query = "?dev&seed=3", strobe = true) shouldBe Switches.parse("seed=3&look=new&opening=night")
        showSwitches(query = "?dev&nohud", strobe = true).has(Switch.NoHud) shouldBe false
    }

    "the former hud names nothing now: no line at the top, and a result is still kept" {
        val switches = showSwitches(query = "?hud&seed=3", strobe = true)

        switches.has(Switch.NoHud) shouldBe true
        switches[Switch.Seed] shouldBe "3"
        keepsTest("?hud") shouldBe true
    }

    "notest in the address skips the machine's tests, and is not handed on to the live act" {
        skipsTests("?seed=3&notest") shouldBe true
        skipsTests("?notesting&seed=3") shouldBe false
        skipsTests("") shouldBe false
        showSwitches(query = "?notest&dev", strobe = true) shouldBe Switches.parse("look=new&opening=night")
    }

    "the test's oversampling is handed on, unless the address sets it by hand; 1 sets nothing" {
        showSwitches(query = "", strobe = true, oversample = 2)[Switch.Oversample] shouldBe "2"
        showSwitches(query = "", strobe = true, oversample = 1).has(Switch.Oversample) shouldBe false
        showSwitches(query = "?oversample=4", strobe = true, oversample = 2)[Switch.Oversample] shouldBe "4"
        setsOversample("?seed=1&oversample=1") shouldBe true
        setsOversample("?seed=1") shouldBe false
    }

    "the address's frozen time is not taken; the app's is" {
        showSwitches(query = "?t=62.1&seed=3", strobe = true).has(Switch.Time) shouldBe false
        showSwitches(query = "?t=62.1", strobe = true, frozenAt = 0.0).double(Switch.Time) shouldBe 0.0
    }

    "the seed is the address's where it gives one, else drawn" {
        showSeed(switches = Switches.parse("seed=42"), random = Random(1)) shouldBe 42
        showSeed(switches = Switches.parse("seed=x"), random = Random(1)) shouldBe Random(1).nextInt(100_000)
        showSeed(switches = Switches(), random = Random(5)) shouldBe Random(5).nextInt(100_000)
    }

    "the test's fog is handed on, unless the address sets it (fog= or nofog); high sets nothing" {
        showSwitches(query = "", strobe = true, fog = FogQuality.Low)[Switch.Fog] shouldBe "low"
        showSwitches(query = "", strobe = true, fog = FogQuality.High).has(Switch.Fog) shouldBe false
        showSwitches(query = "?fog=off", strobe = true, fog = FogQuality.Low)[Switch.Fog] shouldBe "off"
        showSwitches(query = "?nofog", strobe = true, fog = FogQuality.Low) shouldBe Switches.parse("nofog&nohud&look=new&opening=night")
        setsFog("?seed=1&fog=low") shouldBe true
        setsFog("?nofog") shouldBe true
        setsFog("?foggy&seed=1") shouldBe false
    }

    "a test's result is kept only under an address with no switch but the app's own" {
        keepsTest("") shouldBe true
        keepsTest("?nostrobe&dev&notest") shouldBe true
        keepsTest("?nofog") shouldBe false
        keepsTest("?shadows=low") shouldBe false
        keepsTest("?reflections=off") shouldBe false
        keepsTest("?reflections=on&dev") shouldBe false
        keepsTest("?fog=low&nostrobe") shouldBe false
        keepsTest("?nosky") shouldBe false
        keepsTest("?seed=3") shouldBe false
        keepsTest("?t=5&nohud") shouldBe true
        // the look notstrom shows anyway changes nothing; another look does
        keepsTest("?look=new&dev") shouldBe true
        keepsTest("?look=today") shouldBe false
        keepsTest("?look=new&world=night") shouldBe false
    }

    "dev is the app's own: it shows the line at the top and the drawer's look controls, and is not handed on" {
        showsDev("?notest&dev") shouldBe true
        showsDev("?dev=1") shouldBe true
        showsDev("?devices&seed=1") shouldBe false
        showsDev("") shouldBe false
        showSwitches(query = "?dev&seed=3&notest", strobe = true) shouldBe Switches.parse("seed=3&look=new&opening=night")
        keepsTest("?dev&notest") shouldBe true
    }

    "keys that name no live-act switch (a shared link's) do not stop a result from being kept" {
        keepsTest("?fbclid=x") shouldBe true
        keepsTest("?utm_source=feed&nostrobe") shouldBe true
        keepsTest("?fbclid=x&still") shouldBe false
    }

    "the address's shadows are carried into the show; the test chooses none" {
        showSwitches(query = "?shadows=low", strobe = true, fog = FogQuality.Low)[Switch.Shadows] shouldBe "low"
        showSwitches(query = "", strobe = true, fog = FogQuality.Low).has(Switch.Shadows) shouldBe false
    }

    "the address's reflections are carried into the show; the test chooses none, whatever fog it chose" {
        showSwitches(query = "?reflections=off", strobe = true, fog = FogQuality.Low)[Switch.Reflections] shouldBe "off"
        showSwitches(query = "", strobe = true, fog = FogQuality.Low).has(Switch.Reflections) shouldBe false
        showSwitches(query = "", strobe = true, fog = FogQuality.High).has(Switch.Reflections) shouldBe false
    }

    "the result's line says the oversampling and the fog, or which of them the address sets" {
        qualityLine(query = "", oversample = 1, fog = FogQuality.Low) shouldBe "Picture quality: standard, fog low."
        qualityLine(query = "?seed=2", oversample = 2, fog = FogQuality.High) shouldBe
            "Picture quality: high (2x oversampling), fog high."
        qualityLine(query = "?oversample=4", oversample = 2, fog = FogQuality.High) shouldBe
            "Picture quality: oversampling as the address sets it, fog high."
        qualityLine(query = "?nofog", oversample = 2, fog = FogQuality.Low) shouldBe
            "Picture quality: high (2x oversampling), fog as the address sets it."
        qualityLine(query = "?fog=low&oversample=1", oversample = 1, fog = FogQuality.Low) shouldBe
            "Picture quality: as the address sets it (oversampling and fog)."
    }

    "the new look is notstrom's own, and an address that sets the look wins" {
        showSwitches(query = "", strobe = true)[Switch.Look] shouldBe "new"
        showSwitches(query = "?look=today", strobe = true)[Switch.Look] shouldBe "today"
        showSwitches(query = "?look=new&world=night", strobe = true)[Switch.Look] shouldBe "new"
    }

    "the show opens in the night, unless the address sets the opening; a frozen show (the picture test's) has none" {
        showSwitches(query = "", strobe = true)[Switch.Opening] shouldBe "night"
        showSwitches(query = "?opening=off", strobe = true)[Switch.Opening] shouldBe "off"
        showSwitches(query = "?opening=night&look=today", strobe = true)[Switch.Opening] shouldBe "night"
        showSwitches(query = "", strobe = false, frozenAt = 0.0).has(Switch.Opening) shouldBe false
        showSwitches(query = "?opening=ember", strobe = false, frozenAt = 0.0)[Switch.Opening] shouldBe "ember"
        setsOpening("?seed=1&opening=off") shouldBe true
        setsOpening("?openings&seed=1") shouldBe false
    }

    "the opening never stops a result from being kept: the test's stage stands at the song's start, where none opens" {
        keepsTest("?opening=ember") shouldBe true
        keepsTest("?opening=off&dev") shouldBe true
        keepsTest("?opening=night&nostrobe") shouldBe true
        keepsTest("?opening=ember&seed=3") shouldBe false
    }
})
