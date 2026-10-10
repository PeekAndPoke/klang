/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * Shared context for all [BlockRenderer] stages in the voice pipeline.
 *
 * Created once per voice at construction time. Mutable fields are updated per block
 * before the pipeline runs. No allocations in the hot path.
 *
 * Stages read/write the shared buffer:
 * - the **Ignite** stage writes [audioBuffer] (the Ignitor tree's output: the whole instrument, pitch stages included)
 * - the **teardown fade** reads/writes [audioBuffer]
 * - the **Send** stage reads [audioBuffer] and routes it to the cylinder mixer
 *
 * **Threading assumption:** Voices render sequentially within a block.
 * The shared buffer ([audioBuffer]) is not thread-safe.
 */
class BlockContext(
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared buffers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main audio signal buffer (Ignite writes, the teardown fade reads/writes, Send reads). Updated per block. */
    var audioBuffer: AudioBuffer,
    /** Shared scratch buffer pool for Ignitor composition operators */
    val scratchBuffers: ScratchBuffers,

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration (static per voice)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Audio sample rate in Hz */
    val sampleRate: Int,
    /**
     * The voice's onset, gate end and end ([VoiceLimits], absolute frames), by REFERENCE: the voice's own
     * instance, which a realtime note-off moves. Stages read it on every render call and never keep a copy.
     */
    val limits: VoiceLimits,
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
     * The ignitor side does the same, see `IgniteContext.windowEnd`.
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

    /**
     * Set per block by `Voice.render` BEFORE the stages run: true only when the block's output
     * peak will be read for silence culling (a cullable voice; `noCull()` voices pay nothing per
     * sample). The Send stage measures only then.
     */
    var measurePeak: Boolean = false

    /**
     * Peak `|sample|` of the voice's own output this block: post-VCA, times `gain`, BEFORE the
     * solo/mute multiplier. Zeroed per block by `Voice.render`,
     * written by the Send stage when [measurePeak] is set, read back for silence culling.
     */
    var voiceOutputPeak: Double = 0.0
}
