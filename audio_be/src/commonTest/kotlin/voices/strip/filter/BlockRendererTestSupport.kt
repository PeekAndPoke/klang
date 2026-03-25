package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.exciter.ExciteContext
import io.peekandpoke.klang.audio_be.exciter.Exciter
import io.peekandpoke.klang.audio_be.exciter.ScratchBuffers
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Convenience for testing: runs a [BlockRenderer] on a raw buffer without full voice setup.
 * Creates a minimal [BlockContext] wrapping the buffer.
 */
fun BlockRenderer.renderInPlace(buffer: FloatArray, sampleRate: Int = 44100) {
    val ctx = BlockContext(
        audioBuffer = buffer,
        freqModBuffer = DoubleArray(buffer.size),
        scratchBuffers = ScratchBuffers(buffer.size),
        sampleRate = sampleRate,
        startFrame = 0L,
        endFrame = buffer.size.toLong(),
        gateEndFrame = buffer.size.toLong(),
        freqHz = 440.0,
        signal = Exciter { _, _, _ -> },
        signalCtx = ExciteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = buffer.size,
            gateEndFrame = buffer.size,
            releaseFrames = 0,
            voiceEndFrame = buffer.size,
            scratchBuffers = ScratchBuffers(buffer.size),
        ),
        orbits = Orbits(blockFrames = buffer.size, sampleRate = sampleRate),
    )
    ctx.offset = 0
    ctx.length = buffer.size
    ctx.blockStart = 0L

    render(ctx)
}
