/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.viz.liveact.band.Player
import kotlin.math.abs

// The page "Meet the band" (MeetTheBandPage): its words, and what it decides in plain numbers.

/** The start screen's quiet button to the page, and the page's name. */
const val MEET_THE_BAND = "Meet the band"

/** What the start screen says where the band's figures did not load for the page. */
const val MEET_FAILED = "The band could not be met (their figures did not load). Please reload the page."

/** The word over the wildness marks: 0 is elegant, 10 an animal (the maintainer, 2026-11-18). */
const val WILDNESS = "Wildness"

/**
 * What [player] plays in the band, in the page's words: what the show's cast says ([SHOW_SCORE]), not the member's
 * sheet, as who plays what is the cast's to say. Nothing where the cast gives them no seat.
 */
fun playsIn(player: Player): String = SHOW_SCORE.cast.playsOf(player).orEmpty()

/** How far a finger must travel across the screen for a swipe, in CSS pixels. */
const val SWIPE_LEAST = 40.0

/** How steep a swipe may be: the way up or down at most this share of the way across. */
const val SWIPE_FLAT = 0.6

/**
 * Which way a drag of [dx] across and [dy] down (CSS pixels, from where the finger touched to where it left) turns the
 * carousel: 1 to the next member (a swipe to the left, as a page is turned), -1 to the one before (to the right), 0
 * where it is no swipe: shorter than [SWIPE_LEAST], or steeper than [SWIPE_FLAT] (a scroll, a tap). No number is no
 * swipe.
 */
fun swipeStep(dx: Double, dy: Double): Int {
    // NaN-guard: a way of no number fails every comparison below and is no swipe
    if (!(abs(dx) >= SWIPE_LEAST) || !(abs(dy) <= SWIPE_FLAT * abs(dx))) {
        return 0
    }

    return if (dx < 0) 1 else -1
}
