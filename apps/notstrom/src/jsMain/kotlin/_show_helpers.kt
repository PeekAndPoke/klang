/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.random.Random

/**
 * The live act's switches for a show: those of the page's address ([query], `?seed=7&still`), with the stroboscope
 * as the start screen has it: [strobe] false sets `nostrobe`, true takes it away (also where the address had it).
 *
 * The line of text at the top (what the camera does, the frames, the seed) is for whoever works on the show, not for
 * visitors: it is hidden (`nohud`) unless the address asks for it with [HUD] (`?hud`).
 *
 * [oversample]: the oversampling the machine's test chose (`oversample=2`); the address's own `?oversample=` wins.
 * [frozenAt]: the time the show is frozen at (the picture test's band, standing); the address's `?t=` is not taken,
 * as a frozen show would never start. Taken from the address: the live act's other switches (`seed`, `still`, the
 * camera's, `oversample`, ...); decided here: `nostrobe`, `nohud`, `t`.
 */
fun showSwitches(query: String, strobe: Boolean, oversample: Int = 1, frozenAt: Double? = null): Switches {
    val parts = query.removePrefix("?").split("&").filter { it.isNotEmpty() }
    val hud = parts.any { it.substringBefore("=") == HUD }
    // what the app decides (the stroboscope, the line at the top, the frozen time) and its own `hud` and `notest` are
    // set below or not handed on; the address's others are handed on
    val decided = setOf(Switch.NoStrobe.key, Switch.NoHud.key, Switch.Time.key, HUD, NO_TEST)
    val others = parts.filter { it.substringBefore("=") !in decided }
    // the oversampling the test chose, unless the address sets it by hand
    val byHand = others.any { it.substringBefore("=") == Switch.Oversample.key }
    val all = others + listOfNotNull(
        Switch.NoStrobe.key.takeUnless { strobe },
        Switch.NoHud.key.takeUnless { hud },
        "${Switch.Oversample.key}=$oversample".takeUnless { byHand || oversample <= 1 },
        frozenAt?.let { "${Switch.Time.key}=$it" },
    )

    return Switches.parse(all.joinToString("&"))
}

/** The address's switch that shows the line of text at the top, which notstrom hides by default (`?hud`). */
const val HUD = "hud"

/** The address's switch that skips the machine's tests before the show (`?notest`): for repeat visitors. */
const val NO_TEST = "notest"

/** Whether the address skips the machine's tests ([NO_TEST]). */
fun skipsTests(query: String): Boolean = query.keys().any { it == NO_TEST }

/** Whether the address sets the oversampling by hand (`?oversample=2`): it wins over the test's choice. */
fun setsOversample(query: String): Boolean = query.keys().any { it == Switch.Oversample.key }

/** The switches' names in a page's address. */
private fun String.keys(): List<String> = removePrefix("?").split("&").filter { it.isNotEmpty() }.map { it.substringBefore("=") }

/** The performance's seed: the address's (`?seed=7`), else another one every time, drawn from [random]. */
fun showSeed(switches: Switches, random: Random): Int = switches[Switch.Seed]?.toIntOrNull() ?: random.nextInt(100_000)
