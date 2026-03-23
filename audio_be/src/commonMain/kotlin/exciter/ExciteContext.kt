package io.peekandpoke.klang.audio_be.exciter

/**
 * Context for Exciter rendering. Created ONCE per voice, mutated per block.
 * No reinstantiation in the hot path.
 *
 * RULE: No Long anywhere in audio hot paths. Long is boxed in Kotlin/JS = severe perf degradation.
 * Int frames: max ~2.1B frames = ~12.4 hours at 48kHz — more than enough for any voice.
 */
class ExciteContext(
    // ── Static per voice (set at creation, never changes) ──────────────────────
    /** Audio sample rate in Hz */
    val sampleRate: Int,
    /** Total gate duration in frames (scheduled, before release) */
    val voiceDurationFrames: Int,
    /** Frame (relative to voice start) when gate ends and release begins */
    val gateEndFrame: Int,
    /** Release duration in frames */
    val releaseFrames: Int,
    /** Frame (relative to voice start) when voice should be terminated */
    val voiceEndFrame: Int,
    /** Shared scratch buffer pool for binary composition operators */
    val scratchBuffers: ScratchBuffers,

    // ── Mutable per block (updated by caller before each generate() call) ──────
    /** Start index in buffer for this block */
    var offset: Int = 0,
    /** Number of samples to generate */
    var length: Int = 0,
    /** Frames since voice start (monotonic, updated once per block) */
    var voiceElapsedFrames: Int = 0,
    /** Per-sample phase-increment multipliers (1.0 = no change), or null.
     *  MUST be at least (offset + length) elements long when non-null. */
    var phaseMod: DoubleArray? = null,
) {
    // ── Computed properties (derived from above, no storage) ───────────────────

    /** Pre-computed Double to avoid repeated Int→Double conversion in hot loops */
    val sampleRateD: Double = sampleRate.toDouble()

    /** Seconds since voice start */
    val voiceElapsedSecs: Double get() = voiceElapsedFrames.toDouble() / sampleRate

    /** Total gate duration in seconds */
    val voiceDurationSecs: Double get() = voiceDurationFrames.toDouble() / sampleRate

    /** Voice progress 0.0 → 1.0 relative to gate duration. Can exceed 1.0 during release. */
    val voiceProgress: Double get() = voiceElapsedFrames.toDouble() / voiceDurationFrames

    /** True when past gate end (in ADSR release phase) */
    val isInRelease: Boolean get() = voiceElapsedFrames >= gateEndFrame

    /** Release progress 0.0 → 1.0 (only meaningful when isInRelease is true) */
    val releaseProgress: Double
        get() {
            if (!isInRelease) return 0.0
            if (releaseFrames <= 0) return 1.0
            return ((voiceElapsedFrames - gateEndFrame).toDouble() / releaseFrames).coerceIn(0.0, 1.0)
        }
}
