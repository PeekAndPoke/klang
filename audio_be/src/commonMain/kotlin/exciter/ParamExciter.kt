package io.peekandpoke.klang.audio_be.exciter

/**
 * Runtime exciter that fills a buffer with a constant value.
 *
 * This is the default runtime representation of [ExciterDsl.Param].
 * When no modulator is wired into a parameter slot, the slot produces
 * a flat signal at [default] for every sample.
 *
 * @param name parameter name (carried for debugging / introspection)
 * @param default the constant value to fill
 */
class ParamExciter(
    val name: String,
    val default: Double,
) : Exciter {

    private val defaultF = default.toFloat()

    override fun generate(buffer: FloatArray, freqHz: Double, ctx: ExciteContext) {
        buffer.fill(defaultF, ctx.offset, ctx.offset + ctx.length)
    }
}
