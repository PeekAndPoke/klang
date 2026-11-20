/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import kotlin.random.Random

/**
 * The live act's switches for a show: those of the page's address ([query], `?seed=7&still`), with the stroboscope
 * as the start screen has it: [strobe] false sets `nostrobe`, true takes it away (also where the address had it).
 *
 * The line of text at the top (what the camera does, the frames, the seed) is for whoever works on the show, not for
 * visitors: it is hidden (`nohud`) unless the address asks for it with [HUD] (`?hud`).
 *
 * [oversample]: the oversampling the machine's test chose (`oversample=2`); the address's own `?oversample=` wins.
 * [fog]: the fog the machine's test chose (`fog=low`; high, the live act's own, sets nothing); the address's own
 * `?fog=` or `?nofog` wins ([setsFog]).
 * The look is the new one (`look=new`: the fence, the mirror floor, the haze giving way to the shadows, Sirene's eyes in
 * her shadow, the lacquer, the drifting world; the maintainer, 2026-11-20: "please make the new look the default look in
 * notstrom"), unless the address sets `?look=` itself ([setsLook]; `?look=today` gives the look as it was made). The key
 * x flips between the two while the show plays.
 * [frozenAt]: the time the show is frozen at (the picture test's band, standing); the address's `?t=` is not taken,
 * as a frozen show would never start. Taken from the address: the live act's other switches (`seed`, `still`, the
 * camera's, `oversample`, `fog`, ...); decided here: `nostrobe`, `nohud`, `t`.
 */
fun showSwitches(
    query: String,
    strobe: Boolean,
    oversample: Int = 1,
    fog: FogQuality = FogQuality.High,
    frozenAt: Double? = null,
): Switches {
    val parts = query.removePrefix("?").split("&").filter { it.isNotEmpty() }
    val hud = parts.any { it.substringBefore("=") == HUD }
    // what the app decides is set below or not handed on; the address's others are handed on
    val others = parts.filter { it.substringBefore("=") !in APP_SWITCHES }
    // the oversampling the test chose, unless the address sets it by hand
    val byHand = others.any { it.substringBefore("=") == Switch.Oversample.key }
    val all = others + listOfNotNull(
        Switch.NoStrobe.key.takeUnless { strobe },
        Switch.NoHud.key.takeUnless { hud },
        "${Switch.Oversample.key}=$oversample".takeUnless { byHand || oversample <= 1 },
        "${Switch.Fog.key}=${fog.word}".takeUnless { setsFog(query) || fog == FogQuality.High },
        "${Switch.Look.key}=$NEW_LOOK".takeUnless { setsLook(query) },
        frozenAt?.let { "${Switch.Time.key}=$it" },
    )

    return Switches.parse(all.joinToString("&"))
}

/**
 * The address's switches the app decides itself, whatever the address says: the stroboscope (`nostrobe`, the start
 * screen's choice), the line at the top (`nohud`, and the app's own `hud`), the frozen time (`t`), and the app's own
 * `notest`. None of them changes what the machine test measures.
 */
val APP_SWITCHES: Set<String> = setOf(Switch.NoStrobe.key, Switch.NoHud.key, Switch.Time.key, HUD, NO_TEST)

/**
 * Whether the machine test's result under the address of [query] may be kept for the next visit ([StoredTest]): only
 * where the address carries no live-act switch ([Switch]) but the app's own ([APP_SWITCHES]). Any other (`?nofog`,
 * `?fog=low`, `?shadows=low`, `?nosky`, `?seed=3`, ...) may make the picture lighter or heavier than the show's own,
 * and a result measured so would later be taken as the full picture's: it is shown, not kept. Keys that name no
 * switch (`?fbclid=...`, `?utm_source=...`, from a shared link) cannot change the picture and do not count.
 */
fun keepsTest(query: String): Boolean =
    query.keys().none { key -> key !in APP_SWITCHES && Switch.entries.any { it.key == key } }

/** The address's switch that shows the line of text at the top, which notstrom hides by default (`?hud`). */
const val HUD = "hud"

/** The address's switch that skips the machine's tests before the show (`?notest`): for repeat visitors. */
const val NO_TEST = "notest"

/** Whether the address skips the machine's tests ([NO_TEST]). */
fun skipsTests(query: String): Boolean = query.keys().any { it == NO_TEST }

/** Whether the address sets the oversampling by hand (`?oversample=2`): it wins over the test's choice. */
fun setsOversample(query: String): Boolean = query.keys().any { it == Switch.Oversample.key }

/** The look notstrom shows unless the address sets one ([Switch.Look]). */
const val NEW_LOOK = "new"

/** Whether the address sets the look itself (`?look=new`, `?look=today`): it wins over notstrom's own, the new one. */
fun setsLook(query: String): Boolean = query.keys().any { it == Switch.Look.key }

/** Whether the address sets the fog by hand (`?fog=low`, or `?nofog`): it wins over the test's choice. */
fun setsFog(query: String): Boolean = query.keys().any { it == Switch.Fog.key || it == Switch.NoFog.key }

/**
 * The picture's quality the show starts with, in one short line for the tests' result: the [oversample] and the
 * [fog] the test chose, or, where the address of [query] sets either, that it does.
 */
fun qualityLine(query: String, oversample: Int, fog: FogQuality): String {
    val oversampling = setsOversample(query)
    val fogged = setsFog(query)
    val picture = when {
        oversample > 1 -> "high (${oversample}x oversampling)"
        else -> "standard"
    }

    return when {
        oversampling && fogged -> "Picture quality: as the address sets it (oversampling and fog)."
        oversampling -> "Picture quality: oversampling as the address sets it, fog ${fog.word}."
        fogged -> "Picture quality: $picture, fog as the address sets it."
        else -> "Picture quality: $picture, fog ${fog.word}."
    }
}

/** The switches' names in a page's address. */
private fun String.keys(): List<String> = removePrefix("?").split("&").filter { it.isNotEmpty() }.map { it.substringBefore("=") }

/** The performance's seed: the address's (`?seed=7`), else another one every time, drawn from [random]. */
fun showSeed(switches: Switches, random: Random): Int = switches[Switch.Seed]?.toIntOrNull() ?: random.nextInt(100_000)
