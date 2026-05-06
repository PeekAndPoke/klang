package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/** Runtime ignitor that fills buffer with the voice frequency. Runtime representation of [io.peekandpoke.klang.audio_bridge.IgnitorDsl.Freq]. */
object FreqIgnitor : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(freqHz, ctx.offset, ctx.offset + ctx.length)
    }
}
