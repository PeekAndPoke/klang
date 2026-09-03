/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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

    /**
     * The current render cursor (advanced by the main loop each block).
     *
     * **`Double`, not `Int`, and deliberately so.** This counter grows for the whole life of the
     * backend — it advances every block whether or not anything is playing. As an `Int` it overflows
     * after **12.4 h at 48 kHz** (13.5 h at 44.1), and the failure is silent: audio simply stops,
     * with no crash and no error. Confirmed by test — see `docs/tasks/audio-backend-audit.md`.
     *
     * `Double` holds integers **exactly** up to 2^53, i.e. **~5,950 years** at 48 kHz, with zero
     * drift: we only ever add, subtract and compare, and those are exact below 2^53 (verified
     * bit-exact over 10 M accumulations). `Long` would also work but is emulated and allocates on
     * Kotlin/JS, which is why the house rule bans it in audio paths — and on Kotlin/JS an `Int` is
     * *already* a JS number carrying a truncation on every operation, so `Double` is if anything
     * cheaper than what it replaces.
     *
     * ⚠️ **Absolute frames are `Double`; per-sample offsets stay `Int`.** The conversion happens once
     * per block per voice (e.g. `EnvelopeRenderer`: `(ctx.blockStart + ctx.offset) - startFrame`),
     * and everything inside the sample loop is `Int`. Do not widen the loop variables.
     *
     * **Between renders, this is the NEXT block to be rendered** (block-framing B1, 2026-09-03).
     * `renderBlock` sets it to the block it is rendering on entry and advances it by one block on
     * exit, so a command handled between two renders — a scheduled voice, a realtime note-on —
     * sees "now" as the first frame that can still be rendered, never one that has already gone
     * by. Before B1 the clock stayed on the block just rendered, the timeline path anchored a new
     * playback's epoch to it, and every playback's first note was therefore exactly one block
     * late — silently admitted by the old 5-block tolerance window with its first block of attack
     * skipped. Every test rig had always advanced the clock after rendering; production now agrees.
     */
    val cursorFrame: Double

    /** Seconds at an absolute [frame]. */
    fun secAt(frame: Double): Double = startTimeSec + frame / sampleRate

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
    override var cursorFrame: Double = 0.0
}
