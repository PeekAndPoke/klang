package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.osci.OscFn

/**
 * Adapts a [SignalGen] (which owns its own phase) to the [OscFn] interface
 * (which receives phase/phaseInc from the caller).
 *
 * Since SignalGen manages phase internally, the bridge:
 * 1. Creates a [SignalContext] once (reused across blocks)
 * 2. On each OscFn.process() call, updates the context's per-block fields
 * 3. Delegates to SignalGen.generate()
 * 4. Returns 0.0 for phase (SignalGen owns phase internally)
 *
 * TEMPORARY: SignalGen POC bridge — remove when SignalGen replaces OscFn directly in SynthVoice.
 */
fun SignalGen.toOscFn(
    sampleRate: Int,
    voiceDurationFrames: Int,
    gateEndFrame: Int,
    releaseFrames: Int,
    voiceEndFrame: Int,
    scratchBuffers: ScratchBuffers,
): OscFn {
    val ctx = SignalContext(
        sampleRate = sampleRate,
        voiceDurationFrames = voiceDurationFrames,
        gateEndFrame = gateEndFrame,
        releaseFrames = releaseFrames,
        voiceEndFrame = voiceEndFrame,
        scratchBuffers = scratchBuffers,
    )
    var totalFrames = 0

    return OscFn { buffer, offset, length, _, phaseInc, phaseMod ->
        // Convert phaseInc back to freqHz
        val freqHz = phaseInc / TWO_PI * sampleRate

        // Update per-block context
        ctx.offset = offset
        ctx.length = length
        ctx.voiceElapsedFrames = totalFrames
        ctx.phaseMod = phaseMod

        // Generate
        this.generate(buffer, freqHz, ctx)

        totalFrames += length

        0.0 // phase is managed internally by SignalGen
    }
}
