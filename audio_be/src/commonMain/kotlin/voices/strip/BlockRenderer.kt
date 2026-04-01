package io.peekandpoke.klang.audio_be.voices.strip

/**
 * A single processing stage in the voice pipeline.
 *
 * The voice signal flows through a chain of BlockRenderers:
 * **Pitch → Ignite → Filter → Send**
 *
 * - **Pitch** renderers write to [BlockContext.freqModBuffer] (frequency multipliers)
 * - **Ignite** renderers write to [BlockContext.audioBuffer] (raw waveform)
 * - **Filter** renderers read/write [BlockContext.audioBuffer] (sculpt the waveform)
 * - **Send** renderer reads [BlockContext.audioBuffer] and routes to cylinder mixer
 *
 * All stages share the same interface so they can be freely composed and reordered.
 */
fun interface BlockRenderer {
    fun render(ctx: BlockContext)
}
