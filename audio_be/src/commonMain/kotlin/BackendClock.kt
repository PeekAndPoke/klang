/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * Read-only view of the backend's single audio timeline. Engines and schedulers consume this to
 * convert frames ↔ seconds and to decide what is "now"; they must **not** mutate it.
 *
 * The whole first-note-loss class of bug comes from a scheduler tracking its *own* notion of the
 * current frame: a freshly-created per-playback engine would start at frame 0 and compute its epoch
 * against the backend *start* time instead of *now*. There is exactly one timeline per backend, so
 * the cursor lives here and is written only by the main loop ([BackendClock]).
 */
interface RenderClock {
    val sampleRate: Int

    /** Backend epoch — wall-relative seconds at frame 0 (set once at startup). */
    val startTimeSec: Double

    /** The current render cursor (advanced by the main loop each block). */
    val cursorFrame: Int

    /** Seconds at an absolute [frame]. */
    fun secAt(frame: Int): Double = startTimeSec + frame.toDouble() / sampleRate

    /** Seconds at the current cursor — i.e. "now". */
    fun nowSec(): Double = secAt(cursorFrame)
}

/**
 * The mutable backend clock. Owned and written **only by the main loop** (the dispatcher in the live
 * path, [KlangAudioRenderer] offline): `cursorFrame` advances per block, `startTimeSec` is set once.
 * It is handed to engines as a read-only [RenderClock].
 */
class BackendClock(override val sampleRate: Int) : RenderClock {
    override var startTimeSec: Double = 0.0
    override var cursorFrame: Int = 0
}
