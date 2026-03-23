package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.signalgen.ScratchBuffers
import io.peekandpoke.klang.audio_be.signalgen.SignalContext
import io.peekandpoke.klang.audio_be.signalgen.SignalGen

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
 */
class BlockContext(
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared buffers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main audio signal buffer (Excite writes, Filter reads/writes) */
    val audioBuffer: FloatArray,
    /** Pitch modulation multipliers (Pitch writes, Excite reads via SignalContext.phaseMod) */
    val freqModBuffer: DoubleArray,
    /** Shared scratch buffer pool for SignalGen composition operators */
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

    /** SignalGen for waveform generation */
    val signal: SignalGen,
    /** Per-voice SignalContext (mutable per block) */
    val signalCtx: SignalContext,

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

    /** Pre-computed sample rate as Double */
    val sampleRateD: Double = sampleRate.toDouble()

    /** Whether any Pitch renderer has written to [freqModBuffer] this block */
    var hasFreqMod: Boolean = false
}
