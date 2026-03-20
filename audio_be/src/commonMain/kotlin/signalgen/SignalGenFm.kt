package io.peekandpoke.klang.audio_be.signalgen

/**
 * FM (Frequency Modulation) synthesis combinator.
 *
 * The modulator SignalGen's output modulates the carrier's frequency via phaseMod.
 *
 * Ported from: FM section in AbstractVoice.render() (lines ~133-157)
 *
 * @param modulator SignalGen that produces the modulation signal
 * @param ratio frequency ratio between modulator and carrier (e.g. 1.0, 2.0, 3.14)
 * @param depth modulation depth in Hz
 * @param envAttackSec envelope attack time controlling modulation depth over time
 * @param envDecaySec envelope decay time
 * @param envSustainLevel envelope sustain level (0.0 = FM fades out, 1.0 = constant FM)
 * @param envReleaseSec envelope release time
 */
fun SignalGen.fm(
    modulator: SignalGen,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
): SignalGen {
    if (depth == 0.0) return this

    var modBuf: DoubleArray? = null

    return SignalGen { buffer, freqHz, ctx ->
        if (freqHz <= 0.0) {
            this.generate(buffer, freqHz, ctx)
            return@SignalGen
        }

        val bufSize = ctx.offset + ctx.length
        val existing0 = modBuf
        if (existing0 == null || existing0.size < bufSize) {
            modBuf = DoubleArray(bufSize)
        }
        val mb = modBuf ?: error("unreachable")

        // Compute FM envelope level (control rate — once per block)
        val envLevel = if (envAttackSec > 0.0 || envDecaySec > 0.0 || envSustainLevel < 1.0) {
            computeFilterEnvelope(ctx, envAttackSec, envDecaySec, envSustainLevel, envReleaseSec)
        } else {
            1.0
        }
        val effectiveDepth = depth * envLevel

        // Generate modulator signal
        val modFreq = freqHz * ratio
        modulator.generate(mb, modFreq, ctx)

        // Convert modulator output to frequency multipliers
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            mb[i] = 1.0 + (mb[i] * effectiveDepth / freqHz)
        }

        // Chain with existing phaseMod
        val existing = ctx.phaseMod
        if (existing != null) {
            for (i in ctx.offset until end) {
                mb[i] *= existing[i]
            }
        }

        val savedMod = ctx.phaseMod
        ctx.phaseMod = mb
        this.generate(buffer, freqHz, ctx)
        ctx.phaseMod = savedMod
    }
}
