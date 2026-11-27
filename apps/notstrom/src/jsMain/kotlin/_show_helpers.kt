/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.apps.common.PictureQuality
import io.peekandpoke.klang.apps.common.Song
import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.Score
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.scores.DER_SCHMETTERLING
import io.peekandpoke.klang.viz.liveact.scores.KOKON
import io.peekandpoke.klang.viz.liveact.scores.NOCH_SELTSAMERE_DINGE
import kotlin.random.Random

/** One song of the show's programme: the built-in [song], and the [score] its band plays it by. */
data class Programmed(val song: Song, val score: Score)

/**
 * The show's programme, in its order: the drawer goes from one to the next and back ([stepSong]; KLANG-00481, the
 * maintainer, 2026-11-23: "a way to switch between songs: in the drawer 'next song' / 'previous song' and showing the
 * current title and rpm"). The show opens with the first.
 *
 * Each song has its own score: its band, who plays what, and where they are.
 */
val PROGRAMME: List<Programmed> = listOf(
    Programmed(song = BuiltInSongs.derSchmetterling, score = DER_SCHMETTERLING),
    Programmed(song = BuiltInSongs.kokon, score = KOKON),
    Programmed(song = BuiltInSongs.nochSeltsamereDinge, score = NOCH_SELTSAMERE_DINGE),
)

/**
 * The score of the programme's first song: Der Schmetterling's cast and stage plot. The page "Meet the band" says
 * what each one plays by its cast ([playsIn]), and the picture test stands its band by it.
 */
val SHOW_SCORE: Score = PROGRAMME.first().score

/**
 * The place in a programme of [songs] songs that lies [by] songs on from [now] (back for less than 0): after the
 * last comes the first again, before the first the last. A programme of no song has only the place 0.
 */
fun stepSong(now: Int, by: Int, songs: Int): Int = if (songs <= 0) 0 else (now + by).mod(songs)

/** The [song]'s tempo as the drawer says it: its rounds a minute ("35 rpm"; the page prints a whole number without a fraction). */
fun tempoLine(song: Song): String = "${song.rpm} rpm"

/**
 * The live act's switches for a show: those of the page's address ([query], `?seed=7&still`), with the stroboscope
 * as the start screen has it: [strobe] false sets `nostrobe`, true takes it away (also where the address had it).
 *
 * The line of text at the top (what the camera does, the frames, the seed) is for whoever works on the show, not for
 * visitors: it is hidden (`nohud`) unless the address asks for the controls for whoever works on the show with [DEV]
 * (`?dev`; the maintainer, 2026-11-20: "the ?dev option enables the hud or turns it off when ?dev is not present. we
 * can remove the ?hud option."). The former `?hud` names nothing now: it is handed on as a word the live act does not
 * know, and shows no line.
 *
 * [quality]: the picture's detail the machine's test chose (`quality=medium`; high, the live act's own, sets
 * nothing); the address's own `?quality=` wins ([setsQuality]), and its switch of one of the settings (`?fog=low`,
 * ...) wins over either in the live act. [oversample]: the oversampling the test chose (`oversample=2`); the
 * address's own `?oversample=` wins.
 * The look is the new one (`look=new`: the fence, the mirror floor, the haze giving way to the shadows, Sirene's eyes in
 * her shadow, the lacquer, the drifting world; the maintainer, 2026-11-20: "please make the new look the default look in
 * notstrom"), unless the address sets `?look=` itself ([setsLook]; `?look=today` gives the look as it was made). The key
 * x flips between the two while the show plays.
 * The show opens in the night (`opening=night`, [OPENING]: dark before the song, the band red in the front lamp's
 * light, turning slowly to the look's own when it begins; the maintainer, 2026-11-20: "It should also be dark in the
 * beginning"), unless the address sets
 * `?opening=` itself ([setsOpening]; `?opening=off` opens as before), and not in a frozen show (the picture test's).
 * [opens]: false for a show that follows another one at a change of song: no opening, the stage is lit as the
 * band walks to its new places.
 * [frozenAt]: the time the show is frozen at (the picture test's band, standing); the address's `?t=` is not taken,
 * as a frozen show would never start. Taken from the address: the live act's other switches (`seed`, `still`, the
 * camera's, `oversample`, `fog`, ...); decided here: `nostrobe`, `nohud`, `t`.
 */
fun showSwitches(
    query: String,
    strobe: Boolean,
    quality: PicturePreset = PicturePreset.High,
    oversample: Int = 1,
    frozenAt: Double? = null,
    opens: Boolean = true,
): Switches {
    val parts = query.removePrefix("?").split("&").filter { it.isNotEmpty() }
    val hud = parts.any { it.substringBefore("=") == DEV }
    // what the app decides is set below or not handed on; the address's others are handed on
    val others = parts.filter { it.substringBefore("=") !in APP_SWITCHES }
    // the picture's detail the test chose, unless the address sets it by hand (the address's own switch of one of its
    // settings, `?fog=low`, wins over it in the live act); the oversampling it chose, unless the address sets it
    val all = others + listOfNotNull(
        Switch.NoStrobe.key.takeUnless { strobe },
        Switch.NoHud.key.takeUnless { hud },
        "${Switch.Quality.key}=${quality.word}".takeUnless { setsQuality(query) || quality == PicturePreset.High },
        "${Switch.Oversample.key}=$oversample".takeUnless { setsOversample(query) || oversample <= 1 },
        "${Switch.Look.key}=$NEW_LOOK".takeUnless { setsLook(query) },
        "${Switch.Opening.key}=$OPENING".takeUnless { setsOpening(query) || frozenAt != null || !opens },
        frozenAt?.let { "${Switch.Time.key}=$it" },
    )

    return Switches.parse(all.joinToString("&"))
}

/**
 * The address's switches the app decides itself, whatever the address says: the stroboscope (`nostrobe`, the start
 * screen's choice), the line at the top (`nohud`, shown by the app's own `dev`), the frozen time (`t`), and the app's
 * own `notest`. None of them changes what the machine test measures.
 */
val APP_SWITCHES: Set<String> = setOf(Switch.NoStrobe.key, Switch.NoHud.key, Switch.Time.key, DEV, NO_TEST)

/**
 * Whether the machine test's result under the address of [query] may be kept for the next visit ([StoredTest]): only
 * where the address carries no live-act switch ([Switch]) but the app's own ([APP_SWITCHES]). Any other (`?nofog`,
 * `?fog=low`, `?shadows=low`, `?reflections=off`, `?nosky`, `?seed=3`, ...) may make the picture lighter or heavier
 * than the show's own, and a result measured so would later be taken as the full picture's: it is shown, not kept
 * ([NOT_KEPT] says so on the result screen). Keys that name no switch (`?fbclid=...`, `?utm_source=...`, from a shared link) cannot change
 * the picture and do not count; nor does `?look=new`, which only names the look notstrom shows anyway ([NEW_LOOK]),
 * nor `?opening=` of any value: the opening is drawn before the song only, and the test's stage stands frozen at the
 * song's start, where no show opens ([OPENING]).
 */
fun keepsTest(query: String): Boolean =
    query.removePrefix("?").split("&").filter { it.isNotEmpty() }.none { part ->
        val key = part.substringBefore("=")

        key !in APP_SWITCHES &&
            key != Switch.Opening.key &&
            part != "${Switch.Look.key}=$NEW_LOOK" &&
            Switch.entries.any { it.key == key }
    }

/** Said on the result screen where a result just measured is not kept for the next visit ([keepsTest]). */
const val NOT_KEPT = "Not kept for the next visit: the address changes the picture."

/** The address's switch that skips the machine's tests before the show (`?notest`): for repeat visitors. */
const val NO_TEST = "notest"

/**
 * The address's switch for whoever works on the show (`?dev`): the line of text at the top, which notstrom hides by
 * default, and the look's variants and the keys in the show's drawer (the maintainer, 2026-11-20: "when a ?dev param
 * is in the uri, then the drawer also shows all the graphics options and keys to switch them").
 */
const val DEV = "dev"

/** Whether the address asks for the drawer's controls for whoever works on the show ([DEV]). */
fun showsDev(query: String): Boolean = query.keys().any { it == DEV }

/** Whether the address skips the machine's tests ([NO_TEST]). */
fun skipsTests(query: String): Boolean = query.keys().any { it == NO_TEST }

/** Whether the address sets the oversampling by hand (`?oversample=2`): it wins over the test's choice. */
fun setsOversample(query: String): Boolean = query.keys().any { it == Switch.Oversample.key }

/** The look notstrom shows unless the address sets one ([Switch.Look]). */
const val NEW_LOOK = "new"

/** Whether the address sets the look itself (`?look=new`, `?look=today`): it wins over notstrom's own, the new one. */
fun setsLook(query: String): Boolean = query.keys().any { it == Switch.Look.key }

/**
 * The world notstrom's show opens in unless the address sets one ([Switch.Opening]): the night, where the live act
 * lights the walk-in with the red front lamp, the band red figures on a dark stage with their big shadows behind them
 * (the maintainer picked it from stills, 2026-11-21: "i like this mood ... the left one"). It was the ember until
 * then, the deep red furnace behind the drummer and red smoke (the maintainer, 2026-11-20, of the opening: "It should
 * also be dark in the beginning. The 'amber' mood we call it i think").
 */
const val OPENING = "night"

/** Whether the address sets the opening itself (`?opening=off`, `?opening=night`): it wins over notstrom's own. */
fun setsOpening(query: String): Boolean = query.keys().any { it == Switch.Opening.key }

/** Whether the address sets the fog by hand (`?fog=low`, or `?nofog`). */
fun setsFog(query: String): Boolean = query.keys().any { it == Switch.Fog.key || it == Switch.NoFog.key }

/** Whether the address sets the picture's quality as a whole by hand (`?quality=low`): it wins over the test's choice. */
fun setsQuality(query: String): Boolean = query.keys().any { it == Switch.Quality.key }

/**
 * Whether the address sets the picture's detail by hand: the whole (`?quality=`), or one of its settings (the fog, the
 * shadows, the reflections).
 */
fun setsPicture(query: String): Boolean = setsQuality(query) || setsFog(query) ||
    query.keys().any { it == Switch.Shadows.key || it == Switch.Reflections.key }

/**
 * The picture the show starts with, in one short line for the tests' result: the detail ([preset]) and the
 * [oversample] the test chose, or, where the address of [query] sets either, that it does.
 */
fun qualityLine(query: String, preset: PicturePreset, oversample: Int): String {
    val detail = if (setsPicture(query)) "as the address sets it" else preset.word
    val sharp = when {
        setsOversample(query) -> "oversampling as the address sets it"
        oversample > 1 -> "${oversample}x oversampling"
        else -> "no oversampling"
    }

    return "Picture quality: $detail, $sharp."
}

/**
 * Whether the machine test's medium part is worth running: where its first part, the high picture at 1x ([fpsHigh]),
 * does not hold [PictureQuality.HOLD_FPS] (a rate that is no number holds nothing), and the address of [query] does not
 * set the picture's detail itself ([setsPicture]), which wins over the test's choice.
 */
fun measuresMedium(fpsHigh: Double, query: String): Boolean = !(fpsHigh >= PictureQuality.HOLD_FPS) && !setsPicture(query)

/**
 * The picture's detail the machine test chooses ([StoredTest.QUALITY_CHOICES]): [PicturePreset.High] where its first
 * part, the high picture at 1x ([fpsHigh]), holds [PictureQuality.HOLD_FPS]; else [PicturePreset.Medium] where its
 * medium part ([fpsMedium]; null where it was not run) holds it; else [PicturePreset.Low]. A rate that is no number
 * holds nothing. The oversampling is chosen apart ([PictureQuality.oversampleFor]), by the display as well.
 */
fun presetFor(fpsHigh: Double, fpsMedium: Double?): PicturePreset = when {
    fpsHigh >= PictureQuality.HOLD_FPS -> PicturePreset.High
    fpsMedium != null && fpsMedium >= PictureQuality.HOLD_FPS -> PicturePreset.Medium
    else -> PicturePreset.Low
}

/** The switches' names in a page's address. */
private fun String.keys(): List<String> = removePrefix("?").split("&").filter { it.isNotEmpty() }.map { it.substringBefore("=") }

/** The performance's seed: the address's (`?seed=7`), else another one every time, drawn from [random]. */
fun showSeed(switches: Switches, random: Random): Int = switches[Switch.Seed]?.toIntOrNull() ?: random.nextInt(100_000)
