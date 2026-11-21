/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

/**
 * How the show's opening fades in the app (the maintainer, 2026-11-20: "when the user clicks 'Start the show'... let
 * us fade out the start screen and then slowly fade in the stage"), in two steps, each from its own beginning:
 * - [Step.Click]: the click that starts the show. The screen it came from (the start screen, or the tests' result)
 *   fades out to black in [SCREEN_OUT]; the show gets ready on the black. Where that takes longer than [LINE_AFTER],
 *   the readying screen's words come up on the black, at the screen's pace, so a slow connection does not look stuck.
 * - [Step.Standing]: the band stands and the walk-in begins. The black over the stage goes in a moment ([UNVEIL]):
 *   the stage is pitch black itself then, and its own light comes up as the band walks in (the live act's opening,
 *   `opening=night`, [io.peekandpoke.klang.viz.liveact.lights.LightsUp]; the maintainer, 2026-11-20: "I would only do
 *   the lights and start from pitch black"). Where the address turns the opening off, the stage is there at once.
 *
 * Each layer's share ([Layers]) is a function of the seconds since its step began, eased at both ends. Those seconds
 * are counted frame by frame ([advance]), each frame at most [LONGEST_FRAME]: a frame that holds the page (the
 * stage's first frames compile its shaders, a second or more on a slow machine) does not skip the fade. The machine
 * test's phases have no fade: its stage and what it measures stay as they are. The timings are first guesses for the
 * maintainer's eye.
 */
object OpeningFade {

    /** The two steps the opening fades from. */
    enum class Step {
        /** The click that starts the show: the screen goes, the show gets ready on the black. */
        Click,

        /** The band stands and walks in: the stage comes up from the black. */
        Standing,
    }

    /**
     * How much is seen of each layer, from 0 (nothing) to 1 (all of it): [screen] the screen the click came from,
     * [line] the readying screen's words on the black, [black] the black over the stage.
     */
    data class Layers(val screen: Double, val line: Double, val black: Double)

    /** How long the screen the click came from takes to fade out to black, in seconds. */
    const val SCREEN_OUT = 0.8

    /**
     * How long the show may take to get ready after the click before the readying screen's words come up on the
     * black, in seconds (they come at the screen's pace, [SCREEN_OUT]).
     */
    const val LINE_AFTER = 1.0

    /**
     * How long the black over the stage takes to go once the band stands, in seconds: a moment, as the stage itself is
     * pitch black then and comes up by its own light.
     */
    const val UNVEIL = 0.3

    /**
     * The most one frame counts for the fade, in seconds, as the stage's own fades count it: a longer frame (a hitch,
     * a page that was hidden) counts as this long.
     */
    const val LONGEST_FRAME = 0.25

    /**
     * The seconds since a step began after one more frame of [frame] seconds, counted from [since]: at most
     * [LONGEST_FRAME], and a frame below 0 or of no number counts nothing.
     */
    fun advance(since: Double, frame: Double): Double {
        // NaN-guard: a frame of no number counts nothing
        val counted = if (frame != frame) 0.0 else frame.coerceIn(0.0, LONGEST_FRAME)

        return since + counted
    }

    /** Each layer's share [since] seconds after [step] began; a time below 0, or no number, is its beginning. */
    fun layers(step: Step, since: Double): Layers = when (step) {
        Step.Click -> Layers(
            screen = 1.0 - eased(since / SCREEN_OUT),
            line = eased((since - LINE_AFTER) / SCREEN_OUT),
            black = 1.0,
        )

        Step.Standing -> Layers(screen = 0.0, line = 0.0, black = 1.0 - eased(since / UNVEIL))
    }

    /** From how many seconds after [step] began its layers no longer change. */
    fun settles(step: Step): Double = when (step) {
        Step.Click -> LINE_AFTER + SCREEN_OUT
        Step.Standing -> UNVEIL
    }

    /** How far a fade is along its way, [x] from 0 to 1, eased at both ends (smoothstep); outside them held there. */
    private fun eased(x: Double): Double {
        // NaN-guard: no time is the fade's beginning
        val along = if (x != x) 0.0 else x.coerceIn(0.0, 1.0)

        return along * along * (3 - 2 * along)
    }
}
