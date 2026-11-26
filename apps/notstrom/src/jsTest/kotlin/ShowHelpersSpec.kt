/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.random.Random

class ShowHelpersSpec : StringSpec({

    "the programme goes round: after the last song the first, before the first the last" {
        stepSong(now = 0, by = 1, songs = 2) shouldBe 1
        stepSong(now = 1, by = 1, songs = 2) shouldBe 0
        stepSong(now = 0, by = -1, songs = 2) shouldBe 1
        stepSong(now = 0, by = -1, songs = 3) shouldBe 2
        stepSong(now = 2, by = 5, songs = 3) shouldBe 1
        stepSong(now = 4, by = 1, songs = 0) shouldBe 0
    }

    "the programme opens with Der Schmetterling, then Kokon and Noch seltsamere Dinge, each with a score" {
        PROGRAMME.map { it.song.title } shouldBe listOf(
            BuiltInSongs.derSchmetterling.title,
            BuiltInSongs.kokon.title,
            BuiltInSongs.nochSeltsamereDinge.title,
        )
        SHOW_SCORE shouldBe PROGRAMME.first().score
    }

    "a tempo is said in rounds a minute, without a fraction where it has none" {
        // (a tempo given here: a song's own moves freely)
        tempoLine(BuiltInSongs.derSchmetterling.copy(rpm = 35.0)) shouldBe "35 rpm"
        tempoLine(BuiltInSongs.kokon.copy(rpm = 19.0)) shouldBe "19 rpm"
        tempoLine(BuiltInSongs.kokon.copy(rpm = 19.5)) shouldBe "19.5 rpm"
    }

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

    "the test's picture quality is handed on, unless the address sets it by hand; medium sets nothing" {
        showSwitches(query = "", strobe = true, quality = PicturePreset.High)[Switch.Quality] shouldBe "high"
        showSwitches(query = "", strobe = true, quality = PicturePreset.Low)[Switch.Quality] shouldBe "low"
        showSwitches(query = "", strobe = true, quality = PicturePreset.Medium).has(Switch.Quality) shouldBe false
        showSwitches(query = "?quality=max", strobe = true, quality = PicturePreset.Low)[Switch.Quality] shouldBe "max"
        // one setting of the address is handed on beside the test's quality, and wins over it in the live act
        val both = showSwitches(query = "?oversample=4", strobe = true, quality = PicturePreset.High)

        both[Switch.Oversample] shouldBe "4"
        both[Switch.Quality] shouldBe "high"
        setsOversample("?seed=1&oversample=1") shouldBe true
        setsOversample("?seed=1") shouldBe false
        setsQuality("?quality=low") shouldBe true
        setsQuality("?qualityx=low") shouldBe false
    }

    "the machine test's choice: high where its 2x part holds, medium where its 1x part holds 55, else low" {
        presetFor(fps1x = 60.0, fps2x = 60.0, density = 1.0) shouldBe PicturePreset.High
        presetFor(fps1x = 60.0, fps2x = 40.0, density = 1.0) shouldBe PicturePreset.Medium
        presetFor(fps1x = 60.0, fps2x = null, density = 1.0) shouldBe PicturePreset.Medium
        // a dense display never gets 2x: medium where 1x holds
        presetFor(fps1x = 60.0, fps2x = 60.0, density = 2.0) shouldBe PicturePreset.Medium
        presetFor(fps1x = 55.0, fps2x = null, density = 2.0) shouldBe PicturePreset.Medium
        presetFor(fps1x = 54.9, fps2x = null, density = 1.0) shouldBe PicturePreset.Low
        presetFor(fps1x = Double.NaN, fps2x = null, density = 1.0) shouldBe PicturePreset.Low
        // a 2x rate that holds is no high where 1x did not (the flow never measures it so, but the rule holds anyway)
        presetFor(fps1x = 40.0, fps2x = 60.0, density = 1.0) shouldBe PicturePreset.Low
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

    "the address's fog is handed on beside the test's quality (fog= or nofog), and wins over it in the live act" {
        showSwitches(query = "?fog=off", strobe = true, quality = PicturePreset.Low)[Switch.Fog] shouldBe "off"
        showSwitches(query = "?nofog", strobe = true) shouldBe Switches.parse("nofog&nohud&look=new&opening=night")
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
        showSwitches(query = "?shadows=low", strobe = true, quality = PicturePreset.High)[Switch.Shadows] shouldBe "low"
        showSwitches(query = "", strobe = true, quality = PicturePreset.Low).has(Switch.Shadows) shouldBe false
    }

    "the address's reflections are carried into the show; the test's quality sets them only through the quality" {
        showSwitches(query = "?reflections=off", strobe = true, quality = PicturePreset.High)[Switch.Reflections] shouldBe "off"
        showSwitches(query = "", strobe = true, quality = PicturePreset.Low).has(Switch.Reflections) shouldBe false
    }

    "the result's line says the picture's quality the test chose, or that the address sets it" {
        qualityLine(query = "", preset = PicturePreset.Low) shouldBe "Picture quality: low."
        qualityLine(query = "?seed=2", preset = PicturePreset.High) shouldBe "Picture quality: high."
        for (query in listOf("?quality=max", "?oversample=4", "?nofog", "?fog=low", "?shadows=low", "?reflections=high")) {
            qualityLine(query = query, preset = PicturePreset.Medium) shouldBe "Picture quality: as the address sets it."
        }
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
