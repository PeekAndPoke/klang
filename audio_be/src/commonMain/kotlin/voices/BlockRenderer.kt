package io.peekandpoke.klang.audio_be.voices

/**
 * A single processing stage in the voice pipeline.
 *
 * The voice signal flows through a chain of BlockRenderers:
 * **Pitch → Excite → Filter**
 *
 * - **Pitch** renderers write to [BlockContext.freqModBuffer] (frequency multipliers)
 * - **Excite** renderers write to [BlockContext.audioBuffer] (raw waveform)
 * - **Filter** renderers read/write [BlockContext.audioBuffer] (sculpt the waveform)
 *
 * All stages share the same interface so they can be freely composed and reordered.
 */
fun interface BlockRenderer {
    fun render(ctx: BlockContext)
}
