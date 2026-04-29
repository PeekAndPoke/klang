package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Runtime exciter that fills a buffer with a constant value.
 *
 * This is the default runtime representation of [IgnitorDsl.Param].
 * When no modulator is wired into a parameter slot, the slot produces
 * a flat signal at [default] for every sample.
 *
 * @param name parameter name (carried for debugging / introspection)
 * @param default the constant value to fill
 */
class ParamIgnitor(
    val name: String,
    val default: Double,
) : Ignitor {

    private val defaultF = default

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(defaultF, ctx.offset, ctx.offset + ctx.length)
    }
}
