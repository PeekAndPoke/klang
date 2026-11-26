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
 * The fog, the shadows and the reflections are chosen with the oversampling, as the picture's quality as a whole, by
 * the app (notstrom's presets, KLANG-00492): the 1x part draws its medium picture, the 2x part its high one, and below
 * [HOLD_FPS] at 1x it takes its low one. Until 2026-11-26 a fog part chose the fog's sheets here by how the frame time
 * grew with them. The test never chooses 0.5x or 4x oversampling: those are the visitor's choices, and they hold.
 */
object PictureQuality {
    /** The frames a second a part of the picture test must hold: the 1x part for the medium picture, the 2x part for 2x. */
    const val HOLD_FPS = 55.0

    /** The share of the 1x part's frame rate the 2x part must keep. */
    const val KEEP_SHARE = 0.9

    /** A 1x frame rate above this (on a display fast enough to show it) leaves room for 2x. */
    const val FAST_FPS = 120.0

    /** From this pixel density on, a display is dense enough without oversampling. */
    const val DENSE = 2.0

    /** Whether the 2x part of the picture test is worth running: not on a dense display, nor where 1x is too slow. */
    fun measuresTwice(fps1x: Double, density: Double): Boolean = density < DENSE && fps1x >= HOLD_FPS

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
