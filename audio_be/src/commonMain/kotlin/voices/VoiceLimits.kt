/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

/**
 * A voice's time limits, in absolute backend frames (Double, see `RenderClock.cursorFrame`): its onset, its gate
 * end and its end (the gate end plus the release tail).
 *
 * The ONE home of these values. The [Voice] owns the instance and is its only writer in production
 * (`Voice.releaseGate` moves the gate and the end on a realtime note-off); every stage reads it through
 * `BlockContext.limits` on every call and never keeps a copy, so a moved limit reaches every reader at once (the
 * "amendment A1" bug class, a reader of a copy the writer forgot, cannot come back). The ignite stage derives the
 * voice-relative gate the ignitors read (`IgniteContext.gateEndFrame`) from it once per block.
 *
 * Not a limit and deliberately not here: the `accelerate` glide base (`IgniteContext.voiceDurationFrames`, the
 * scheduled gate length, which the accelerate node glides over and then holds), which a note-off must not move; and a cut's fade window, which only the
 * voice reads and which lives with the state that uses it, `Voice.State.Fading` (lifecycle step 5b).
 */
class VoiceLimits(
    /** The onset. Fixed for the voice's life. */
    val startFrame: Double,
    gateEndFrame: Double,
    endFrame: Double,
) {
    /** Where the gate ends and the release begins. Moves earlier on a realtime note-off. */
    var gateEndFrame: Double = gateEndFrame
        internal set

    /**
     * The death frame (gate end plus the release tail). Moved by a realtime note-off together with
     * [gateEndFrame]; earlier in every real case, see `Voice.endFrame`.
     */
    var endFrame: Double = endFrame
        internal set
}
