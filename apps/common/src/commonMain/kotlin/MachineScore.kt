/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How well a machine can play the show, from its two tests: the [sound] (the engine's dynamometer: the voices it
 * renders safely, the studio's PS) and the [picture] (the frames a second the stage is drawn at), each 0 to 100, and
 * the [combined] score: their geometric mean, rounded, so one weak half pulls it down as it would pull down the show.
 */
data class MachineScore(val sound: Double, val picture: Double, val combined: Int) {

    /** One plain sentence for the [combined] score. */
    val verdict: String
        get() = when {
            combined >= READY -> "This machine is ready for the show."
            combined >= RUNS -> "The show should run; it may stutter now and then."
            else -> "This machine will struggle with the show. You can start it anyway."
        }

    companion object {
        /** The safe voices that give the full sound score: the studio's "Excellent" tier. */
        const val FULL_VOICES = 250

        /** The frames a second that give the full picture score: most displays show no more. */
        const val FULL_FPS = 60.0

        /** From this combined score on, the machine is ready for the show. */
        const val READY = 80

        /** From this combined score on, the show should run, with a stutter now and then. */
        const val RUNS = 50

        /** The score of a machine that renders [safeVoices] safely and draws the stage at [fps]. */
        fun of(safeVoices: Int, fps: Double): MachineScore {
            val sound = 100.0 * min(1.0, safeVoices.coerceAtLeast(0).toDouble() / FULL_VOICES)
            // NaN-guard: an unreadable frame rate counts as none
            val frames = if (fps.isFinite()) fps.coerceAtLeast(0.0) else 0.0
            val picture = 100.0 * min(1.0, frames / FULL_FPS)

            return MachineScore(sound = sound, picture = picture, combined = sqrt(sound * picture).roundToInt())
        }
    }
}

/**
 * The oversampling the show starts with, by the picture test: 2 where this machine holds its frame rate drawing the
 * stage at 2x oversampling, else 1 (the live act steps it down again where the frames stay slow while the show plays).
 *
 * A display caps the frames at its refresh rate, so how much room is left above it cannot be read from the frame rate
 * at 1x: the stage is drawn at 2x as well ([fps2x]; null where that part was not run). 2x is chosen where the 2x part
 * holds at least [HOLD_FPS] and at least [KEEP_SHARE] of the 1x part ([fps1x]); or, on a display fast enough to tell,
 * where the 1x part is above [FAST_FPS] and the 2x part holds [HOLD_FPS].
 *
 * A dense display ([density], the device's pixels for each of the page's, each way; at least [DENSE]) already has the
 * pixels oversampling would add (the live act draws at the display's density): it gets 1 whatever the rates, and the
 * 2x part need not be run there ([measuresTwice]).
 *
 * A known limit: the test draws in the window as it is, and the show may play in fullscreen, with more pixels to
 * draw. Where that is too much, the live act's own step down while the show plays is the safety net.
 *
 * The fog on the floor ([fogFor]): the test's first part draws it with its most sheets (high). Where that part holds
 * [HOLD_FPS], the fog stays so and the 2x part decides the oversampling as above. Where it does not, a fog part draws
 * the fewest sheets (low) at 1x instead of the 2x part, and the fog's sheets are chosen by how the frame time grows
 * with them. The test never chooses no fog or 0.5x oversampling: those are the visitor's choices, and they hold.
 */
object PictureQuality {
    /** The frames a second the 2x part must hold. */
    const val HOLD_FPS = 55.0

    /** The share of the 1x part's frame rate the 2x part must keep. */
    const val KEEP_SHARE = 0.9

    /** A 1x frame rate above this (on a display fast enough to show it) leaves room for 2x. */
    const val FAST_FPS = 120.0

    /** From this pixel density on, a display is dense enough without oversampling. */
    const val DENSE = 2.0

    /** The fog part must be this many times as fast as the first part to count as clearly faster (5 percent). */
    const val FOG_GAIN = 1.05

    /** Whether the 2x part of the picture test is worth running: not on a dense display, nor where 1x is too slow. */
    fun measuresTwice(fps1x: Double, density: Double): Boolean = density < DENSE && fps1x >= HOLD_FPS

    /**
     * Whether the fog part of the picture test is worth running: where the first part ([fpsFull], the fog at its
     * most sheets) does not hold [HOLD_FPS]. A rate that is no number holds nothing.
     */
    fun measuresFog(fpsFull: Double): Boolean = !(fpsFull >= HOLD_FPS)

    /**
     * The fog's sheets to start the show with: one of [choices], the counts of sheets the test may choose from,
     * fewest first (notstrom's: low and high). The first part ([fpsFull]) was drawn with the last of them, the fog
     * part ([fpsFew]; null where it was not run) with the first.
     *
     * Where the first part holds [HOLD_FPS]: the most. Else the frame time is taken to grow in a straight line with
     * the sheets, `t(n) = a + b n` through the two parts (`t = 1000 / fps`, in milliseconds), and the most sheets
     * whose foreseen rate holds [HOLD_FPS] are chosen; where none does, the fewest. A part at or above [HOLD_FPS]
     * may have been capped by the display (its true rate may be higher): it is taken at the rate measured, so the
     * line foresees too slow a rate rather than too fast a one.
     *
     * Where the fog part is not clearly faster than the first (less than [FOG_GAIN] times its rate, or slower): fewer
     * sheets buy nothing measurable, so the fog is not what holds the frames back (a display of 50 pictures a second
     * or less caps both parts alike), and the look is not given up for no frames: the most.
     *
     * The plain answer, the fewest, where a part says nothing: no fog part, or a rate that is no number, not finite or
     * not above 0.
     *
     * Not here: stepping the fog down by itself while the show plays. Where the frames get slow there, the live act's
     * `Sharpness` (fewer pixels at 1x and 0.5x) and the oversampling's step down are the safety net.
     */
    fun fogFor(fpsFull: Double, fpsFew: Double?, choices: List<Int>): Int {
        require(choices.size >= 2 && choices.zipWithNext().all { (a, b) -> a < b }) { "the fog's choices, fewest first: $choices" }

        val fewest = choices.first()
        val most = choices.last()

        if (fpsFull >= HOLD_FPS) {
            return most
        }

        val fullMs = frameMs(fpsFull)
        val fewMs = fpsFew?.let { frameMs(it) }

        if (fpsFew == null || fullMs == null || fewMs == null) {
            return fewest
        }

        // fewer sheets were not clearly faster: the fog is not what holds the frames back
        if (fpsFew < FOG_GAIN * fpsFull) {
            return most
        }

        return min(foreseenFog(fullMs = fullMs, fewMs = fewMs, choices = choices), most)
    }

    /**
     * The most of [choices] the straight line through the two parts' frame times ([fullMs] with the most sheets,
     * [fewMs] with the fewest, the fewer clearly faster) foresees at [HOLD_FPS]; the fewest where none.
     */
    private fun foreseenFog(fullMs: Double, fewMs: Double, choices: List<Int>): Int {
        val few = choices.first()
        val full = choices.last()
        val slope = (fullMs - fewMs) / (full - few)
        val base = fewMs - slope * few
        val budget = 1000.0 / HOLD_FPS

        return choices.lastOrNull { n -> base + slope * n <= budget } ?: few
    }

    /** The time of a frame at [fps], in milliseconds; null for a rate that is no number, not finite or not above 0. */
    private fun frameMs(fps: Double): Double? = if (fps.isFinite() && fps > 0.0) 1000.0 / fps else null

    /** The oversampling to start the show with: 1 or 2 (see the object). */
    fun oversampleFor(fps1x: Double, fps2x: Double?, density: Double): Int = when {
        density >= DENSE -> 1
        // NaN-guard: a 2x rate that is no number holds nothing
        fps2x == null || !(fps2x >= HOLD_FPS) -> 1
        fps1x > FAST_FPS -> 2
        fps2x >= KEEP_SHARE * fps1x -> 2
        else -> 1
    }
}
