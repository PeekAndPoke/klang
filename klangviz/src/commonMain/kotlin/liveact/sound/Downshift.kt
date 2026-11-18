/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

/**
 * When a picture drawn with oversampling takes a step down to fewer samples, while a show plays: after [PATIENCE]
 * stretches of frames in a row below [SLOW] frames a second (the live act counts its frames in stretches of half a
 * second, so three seconds). A single slow stretch (a stutter) is not enough. It only says when; the live act takes
 * the step (as the key o does, one step down), and never steps up again by itself. A choice by hand holds: after it,
 * no step is asked for.
 *
 * It works beside [Sharpness], not against it: Sharpness judges only a picture drawn without oversampling, so once the
 * oversampling is down to none, Sharpness takes over and may lower the pixels further.
 *
 * Tell it the frame rate of each stretch of frames with [judge], only of stretches that were seen.
 */
class Downshift {

    /** How many stretches in a row were too slow. */
    private var slow = 0

    /**
     * Takes the frame rate of the last stretch ([fps], a second); true where it is time for a step down. [byHand]: the
     * oversampling was chosen by hand (the key o, a control): that choice holds, and no step is ever asked for.
     */
    fun judge(fps: Double, byHand: Boolean = false): Boolean {
        // NaN-guard: a rate that is no number says nothing, and does not count as slow
        if (byHand || fps != fps || fps >= SLOW) {
            slow = 0

            return false
        }

        slow++

        if (slow < PATIENCE) {
            return false
        }

        slow = 0

        return true
    }

    companion object {
        /** Below this many frames a second a stretch is slow: under [Sharpness.ENOUGH], so a 50 Hz screen is not. */
        const val SLOW = 45.0

        /** So many slow stretches in a row (half a second each) before a step down. */
        const val PATIENCE = 6
    }
}
