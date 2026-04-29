package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.AudioBuffer

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Convenience for testing: runs a [BlockRenderer] on a raw buffer without full voice setup.
 * Creates a minimal [BlockContext] wrapping the buffer.
 */
fun BlockRenderer.renderInPlace(buffer: AudioBuffer, sampleRate: Int = 44100) {
    val ctx = BlockContext(
        audioBuffer = buffer,
        freqModBuffer = DoubleArray(buffer.size),
        scratchBuffers = ScratchBuffers(buffer.size),
        sampleRate = sampleRate,
        startFrame = 0,
        endFrame = buffer.size,
        gateEndFrame = buffer.size,
        freqHz = 440.0,
        signal = { _, _, _ -> },
        signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = buffer.size,
            gateEndFrame = buffer.size,
            releaseFrames = 0,
            voiceEndFrame = buffer.size,
            scratchBuffers = ScratchBuffers(buffer.size),
        ),
        cylinders = Cylinders(blockFrames = buffer.size, sampleRate = sampleRate),
    )
    ctx.offset = 0
    ctx.length = buffer.size
    ctx.blockStart = 0

    render(ctx)
}
