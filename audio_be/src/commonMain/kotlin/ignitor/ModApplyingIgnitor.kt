package io.peekandpoke.klang.audio_be.ignitor

/**
 * Wraps a source [Ignitor] with a pre-computed pitch modulation signal.
 *
 * At runtime, generates the [mod] Ignitor (which outputs ratio-space values, 1.0 = no change),
 * combines with any existing strip-level `ctx.phaseMod`, and calls [inner] with the combined
 * modulation set on the context.
 *
 * This class is designed to sit **inside** a [MemoizingIgnitor] boundary. On cache hit, neither
 * `mod.generate` nor the `ctx.phaseMod` mutation occurs — the memoised output is returned directly.
 * This eliminates the phase-mod–memoisation conflict: each fork of a shared source gets its own
 * `Memoized(ModApplyingIgnitor(...))`, so different mods on the same source don't interfere.
 *
 * @param inner the source oscillator (reads `ctx.phaseMod` per sample as today)
 * @param mod ratio-space modulation signal (1.0 = no change, >1.0 = higher pitch, <1.0 = lower)
 */
internal class ModApplyingIgnitor(val inner: Ignitor, val mod: Ignitor) : Ignitor {

    override fun generate(buffer: FloatArray, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { modBuf ->
            mod.generate(modBuf, freqHz, ctx)

            ctx.scratchBuffers.useDouble { ratioArray ->
                val existing = ctx.phaseMod
                val end = ctx.offset + ctx.length

                if (existing != null) {
                    for (i in ctx.offset until end) {
                        ratioArray[i] = modBuf[i].toDouble() * existing[i]
                    }
                } else {
                    for (i in ctx.offset until end) {
                        ratioArray[i] = modBuf[i].toDouble()
                    }
                }

                val savedMod = ctx.phaseMod
                ctx.phaseMod = ratioArray
                inner.generate(buffer, freqHz, ctx)
                ctx.phaseMod = savedMod
            }
        }
    }
}
