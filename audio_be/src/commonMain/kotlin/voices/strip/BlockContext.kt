/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Shared context for all [BlockRenderer] stages in the voice pipeline.
 *
 * Created once per voice at construction time. Mutable fields are updated per block
 * before the pipeline runs. No allocations in the hot path.
 *
 * Stages read/write the shared buffers:
 * - **Pitch** stages write to [freqModBuffer] (frequency multipliers)
 * - **Ignite** stages write to [audioBuffer] (raw waveform)
 * - **Filter** stages read/write [audioBuffer] (sculpt the waveform)
 * - **Send** stage reads [audioBuffer] and routes to cylinder mixer
 *
 * **Threading assumption:** Voices render sequentially within a block.
 * The shared buffers ([audioBuffer], [freqModBuffer]) are not thread-safe.
 */
class BlockContext(
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared buffers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main audio signal buffer (Ignite writes, Filter reads/writes). Updated per block. */
    var audioBuffer: AudioBuffer,
    /** Pitch modulation multipliers (Pitch writes, Excite reads via IgniteContext.phaseMod) */
    val freqModBuffer: DoubleArray,
    /** Shared scratch buffer pool for Ignitor composition operators */
    val scratchBuffers: ScratchBuffers,

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration (static per voice)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Audio sample rate in Hz */
    val sampleRate: Int,
    // These three are ABSOLUTE backend frames, so Double — see RenderClock.cursorFrame.
    // (Contrast IgniteContext.gateEndFrame, which is voice-RELATIVE and stays Int.)
    /** Voice start frame (absolute) */
    val startFrame: Double,
    /**
     * Voice end frame including release (absolute).
     * `var`: a realtime note-off ([Voice.releaseGate]) moves it together with [gateEndFrame].
     * THE single source of truth — renderers must read it per render call, never bake copies.
     */
    var endFrame: Double,
    /**
     * Frame when gate ends / release begins (absolute).
     * `var`: a realtime note-off ([Voice.releaseGate]) moves the gate earlier. Single source of
     * truth for the strip — see [endFrame].
     */
    var gateEndFrame: Double,
    /** Base frequency in Hz */
    val freqHz: Double,

    // ═══════════════════════════════════════════════════════════════════════════
    // Ignite stage
    // ═══════════════════════════════════════════════════════════════════════════

    /** Ignitor for waveform generation */
    val signal: Ignitor,
    /** Per-voice IgniteContext (mutable per block) */
    val signalCtx: IgniteContext,

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing
    // ═══════════════════════════════════════════════════════════════════════════

    /** Cylinder management for routing and effects */
    val cylinders: Cylinders,
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // Mutable per block (updated before pipeline runs)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Start index in buffer for this block. Moved via [updateOffsetAndLength] / [updateOffset]. */
    var offset: Int = 0
        private set

    /** Number of samples to process. Moved via [updateOffsetAndLength] / [updateLength]. */
    var length: Int = 0
        private set

    /**
     * One past the last buffer index this block touches, i.e. `offset + length`.
     *
     * All three window fields are `private set` so this one CANNOT go stale: the only way in is
     * the update functions below, which recompute it once. That matters more than the arithmetic
     * it saves — a wrong render window is the block-framing bug class
     * (`docs/plans/block-framing-invariance.md`), and a hand-maintained copy would invite it back.
     *
     * The ignitor side does the same — see [IgniteContext.windowEnd].
     */
    var windowEnd: Int = 0
        private set

    /**
     * Moves the whole render window — the normal per-block update.
     *
     * [windowEnd] is recomputed ONCE here. Assigning the two fields separately would compute it
     * twice and, in between, leave the context describing a window that never existed.
     */
    fun updateOffsetAndLength(offset: Int, length: Int) {
        this.offset = offset
        this.length = length
        this.windowEnd = offset + length
    }

    /** Moves the window start, keeping [length]. */
    fun updateOffset(offset: Int) {
        this.offset = offset
        this.windowEnd = offset + length
    }

    /** Resizes the window, keeping [offset]. */
    fun updateLength(length: Int) {
        this.length = length
        this.windowEnd = offset + length
    }

    /** Current block start frame (absolute) */
    // Absolute backend frame — Double, see RenderClock.cursorFrame. Per-sample offsets stay Int.
    var blockStart: Double = 0.0

    /** Voice render context — set per block by Voice, read by SendRenderer for cylinder routing */
    lateinit var renderContext: Voice.RenderContext

    /** Pre-computed sample rate as Double */
    val sampleRateD: Double = sampleRate.toDouble()

    /** Whether any Pitch renderer has written to [freqModBuffer] this block. Reset per block. */
    var freqModBufferWritten: Boolean = false
}
