package io.peekandpoke.klang.audio_be.ignitor

/** Runtime ignitor that fills buffer with the voice frequency. Runtime representation of [io.peekandpoke.klang.audio_bridge.IgnitorDsl.Freq]. */
object FreqIgnitor : Ignitor {
    override fun generate(buffer: FloatArray, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(freqHz.toFloat(), ctx.offset, ctx.offset + ctx.length)
    }
}
