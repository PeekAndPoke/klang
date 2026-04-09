package io.peekandpoke.klang.audio_be.ignitor

/**
 * FM (Frequency Modulation) synthesis combinator.
 *
 * The modulator Ignitor's output modulates the carrier's frequency via phaseMod.
 *
 * Ported from: FM section in Voice.render()
 *
 * @param modulator Ignitor that produces the modulation signal
 * @param ratio frequency ratio between modulator and carrier (e.g. 1.0, 2.0, 3.14)
 * @param depth modulation depth in Hz
 * @param envAttackSec envelope attack time controlling modulation depth over time
 * @param envDecaySec envelope decay time
 * @param envSustainLevel envelope sustain level (0.0 = FM fades out, 1.0 = constant FM)
 * @param envReleaseSec envelope release time
 */
fun Ignitor.fm(
    modulator: Ignitor,
    ratio: Ignitor,
    depth: Ignitor,
    envAttackSec: Ignitor = ParamIgnitor("envAttackSec", 0.0),
    envDecaySec: Ignitor = ParamIgnitor("envDecaySec", 0.0),
    envSustainLevel: Ignitor = ParamIgnitor("envSustainLevel", 1.0),
    envReleaseSec: Ignitor = ParamIgnitor("envReleaseSec", 0.0),
): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        if (freqHz <= 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Ignitor
        }

        val ratioVal = Ignitors.readParam(ratio, freqHz, ctx)
        val depthVal = Ignitors.readParam(depth, freqHz, ctx)

        if (depthVal == 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@Ignitor
        }

        val envAttackSecVal = Ignitors.readParam(envAttackSec, freqHz, ctx)
        val envDecaySecVal = Ignitors.readParam(envDecaySec, freqHz, ctx)
        val envSustainLevelVal = Ignitors.readParam(envSustainLevel, freqHz, ctx)
        val envReleaseSecVal = Ignitors.readParam(envReleaseSec, freqHz, ctx)

        // Compute FM envelope level (control rate — once per block)
        val envLevel = if (envAttackSecVal > 0.0 || envDecaySecVal > 0.0 || envSustainLevelVal < 1.0) {
            computeFilterEnvelope(ctx, envAttackSecVal, envDecaySecVal, envSustainLevelVal, envReleaseSecVal)
        } else {
            1.0
        }
        val effectiveDepth = depthVal * envLevel

        ctx.scratchBuffers.use { mb ->
            // Generate modulator signal into FloatArray
            val modFreq = freqHz * ratioVal
            modulator.generate(mb, modFreq, ctx)

            ctx.scratchBuffers.useDouble { pmb ->
                // Convert modulator output to frequency multipliers
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    pmb[i] = 1.0 + (mb[i].toDouble() * effectiveDepth / freqHz)
                }

                // Chain with existing phaseMod
                val existing = ctx.phaseMod
                if (existing != null) {
                    for (i in ctx.offset until end) {
                        pmb[i] *= existing[i]
                    }
                }

                val savedMod = ctx.phaseMod
                ctx.phaseMod = pmb
                this.generate(buffer, freqHz, ctx)
                ctx.phaseMod = savedMod
            }
        }
    }
}

/** Double convenience overload — delegates to the Ignitor-param version. */
fun Ignitor.fm(
    modulator: Ignitor,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
): Ignitor {
    if (depth == 0.0) return this

    return fm(
        modulator = modulator,
        ratio = ParamIgnitor("ratio", ratio),
        depth = ParamIgnitor("depth", depth),
        envAttackSec = ParamIgnitor("envAttackSec", envAttackSec),
        envDecaySec = ParamIgnitor("envDecaySec", envDecaySec),
        envSustainLevel = ParamIgnitor("envSustainLevel", envSustainLevel),
        envReleaseSec = ParamIgnitor("envReleaseSec", envReleaseSec),
    )
}
