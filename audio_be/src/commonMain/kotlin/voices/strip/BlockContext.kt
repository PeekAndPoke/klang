package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.exciter.ExciteContext
import io.peekandpoke.klang.audio_be.exciter.Exciter
import io.peekandpoke.klang.audio_be.exciter.ScratchBuffers
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Shared context for all [BlockRenderer] stages in the voice pipeline.
 *
 * Created once per voice at construction time. Mutable fields are updated per block
 * before the pipeline runs. No allocations in the hot path.
 *
 * Stages read/write the shared buffers:
 * - **Pitch** stages write to [freqModBuffer] (frequency multipliers)
 * - **Excite** stages write to [audioBuffer] (raw waveform)
 * - **Filter** stages read/write [audioBuffer] (sculpt the waveform)
 * - **Send** stage reads [audioBuffer] and routes to orbit mixer
 *
 * **Threading assumption:** Voices render sequentially within a block.
 * The shared buffers ([audioBuffer], [freqModBuffer]) are not thread-safe.
 */
class BlockContext(
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared buffers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main audio signal buffer (Excite writes, Filter reads/writes). Updated per block. */
    var audioBuffer: FloatArray,
    /** Pitch modulation multipliers (Pitch writes, Excite reads via ExciteContext.phaseMod) */
    val freqModBuffer: DoubleArray,
    /** Shared scratch buffer pool for Exciter composition operators */
    val scratchBuffers: ScratchBuffers,

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration (static per voice)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Audio sample rate in Hz */
    val sampleRate: Int,
    /** Voice start frame (absolute) */
    val startFrame: Long,
    /** Voice end frame including release (absolute) */
    val endFrame: Long,
    /** Frame when gate ends / release begins (absolute) */
    val gateEndFrame: Long,
    /** Base frequency in Hz */
    val freqHz: Double,

    // ═══════════════════════════════════════════════════════════════════════════
    // Excite stage
    // ═══════════════════════════════════════════════════════════════════════════

    /** Exciter for waveform generation */
    val signal: Exciter,
    /** Per-voice ExciteContext (mutable per block) */
    val signalCtx: ExciteContext,

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing
    // ═══════════════════════════════════════════════════════════════════════════

    /** Orbit management for routing and effects */
    val orbits: Orbits,
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // Mutable per block (updated before pipeline runs)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Start index in buffer for this block */
    var offset: Int = 0

    /** Number of samples to process */
    var length: Int = 0

    /** Current block start frame (absolute) */
    var blockStart: Long = 0

    /** Voice render context — set per block by Voice, read by SendRenderer for orbit routing */
    lateinit var renderContext: Voice.RenderContext

    /** Pre-computed sample rate as Double */
    val sampleRateD: Double = sampleRate.toDouble()

    /** Whether any Pitch renderer has written to [freqModBuffer] this block. Reset per block. */
    var freqModBufferWritten: Boolean = false
}
