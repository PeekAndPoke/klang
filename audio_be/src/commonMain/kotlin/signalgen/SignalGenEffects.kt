package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.*

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
private const val DENORMAL_THRESHOLD = 1e-15

@Suppress("NOTHING_TO_INLINE")
private inline fun flushDenormal(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v

// ═══════════════════════════════════════════════════════════════════════════════
// Distortion
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Distortion / waveshaping combinator.
 *
 * Ported from: DistortionFilter.kt
 *
 * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp"
 * Includes DC blocker for asymmetric shapes (diode, rectify).
 */
fun SignalGen.distort(amount: Double, shape: String = "soft"): SignalGen {
    if (amount <= 0.0) return this

    val drive = 10.0.pow(amount * 1.2)
    val resolved = resolveDistortionShape(shape)
    val waveshaper = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock

    // DC blocker state
    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val x = buffer[i].toDouble() * drive
            var y = waveshaper(x) * outputGain

            if (needsDcBlock) {
                val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
                dcBlockX1 = y
                dcBlockY1 = flushDenormal(dcOut)
                y = dcOut
            }

            buffer[i] = y.toFloat()
        }
    }
}

private data class ResolvedShape(
    val fn: (Double) -> Double,
    val outputGain: Double = 1.0,
    val needsDcBlock: Boolean = false,
)

private fun resolveDistortionShape(shape: String): ResolvedShape = when (shape.lowercase()) {
    "hard" -> ResolvedShape(fn = ClippingFuncs::hardClip)
    "gentle" -> ResolvedShape(fn = ClippingFuncs::softClip, outputGain = 2.0)
    "cubic" -> ResolvedShape(fn = ClippingFuncs::cubicClip)
    "diode" -> ResolvedShape(fn = ClippingFuncs::diodeClip, needsDcBlock = true)
    "fold" -> ResolvedShape(fn = ClippingFuncs::sineFold)
    "chebyshev" -> ResolvedShape(fn = ClippingFuncs::chebyshevT3)
    "rectify" -> ResolvedShape(fn = ClippingFuncs::rectify, needsDcBlock = true)
    "exp" -> ResolvedShape(fn = ClippingFuncs::expClip)
    else -> ResolvedShape(fn = ClippingFuncs::fastTanh) // "soft" & fallback
}

// ═══════════════════════════════════════════════════════════════════════════════
// BitCrush
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bit depth reduction combinator.
 *
 * Ported from: BitCrushFilter.kt
 */
fun SignalGen.crush(amount: Double): SignalGen {
    if (amount <= 0.0) return this

    val levels = 2.0.pow(amount).toInt().toDouble()
    if (levels <= 1.0) return this

    val halfLevels = levels / 2.0

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (floor(buffer[i].toDouble() * halfLevels) / halfLevels).toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Coarse (Sample Rate Reducer)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sample-and-hold downsampling combinator.
 *
 * Ported from: SampleRateReducerFilter.kt
 */
fun SignalGen.coarse(amount: Double): SignalGen {
    if (amount <= 1.0) return this

    var lastValue = 0.0f
    var counter = 0.0

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val idx = i - ctx.offset

            if (counter >= 1.0 || (idx == 0 && counter == 0.0)) {
                lastValue = buffer[i]
                counter -= 1.0
            }

            buffer[i] = lastValue
            counter += (1.0 / amount)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Phaser
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-stage all-pass cascade phaser with LFO.
 *
 * Ported from: PhaserFilter.kt
 */
fun SignalGen.phaser(
    rate: Double,
    depth: Double,
    center: Double = 1000.0,
    sweep: Double = 1000.0,
): SignalGen {
    if (depth <= 0.0) return this

    val stages = 4
    var lfoPhase = 0.0
    val filterState = DoubleArray(stages)
    var lastOutput = 0.0
    val feedback = 0.5

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val inverseSampleRate = 1.0 / ctx.sampleRate
        val lfoIncrement = rate * TWO_PI * inverseSampleRate

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            // LFO
            lfoPhase += lfoIncrement
            if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI
            val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

            // Modulated frequency
            var modFreq = center + (lfoValue - 0.5) * sweep
            modFreq = modFreq.coerceIn(100.0, 18000.0)

            // All-pass coefficient
            val tanValue = tan(PI * modFreq * inverseSampleRate)
            val alpha = (tanValue - 1.0) / (tanValue + 1.0)

            // All-pass cascade with feedback
            var signal = buffer[i].toDouble() + lastOutput * feedback

            for (s in 0 until stages) {
                val output = alpha * signal + filterState[s]
                filterState[s] = flushDenormal(signal - alpha * output)
                signal = output
            }

            lastOutput = flushDenormal(signal)

            // Mix wet with dry
            buffer[i] = (buffer[i] + signal * depth).toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tremolo
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Amplitude modulation (tremolo) combinator with sine LFO.
 *
 * Ported from: TremoloFilter.kt
 */
fun SignalGen.tremolo(
    rate: Double,
    depth: Double,
): SignalGen {
    if (depth <= 0.0) return this

    var phase = 0.0

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val phaseInc = (TWO_PI * rate) / ctx.sampleRate

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            phase += phaseInc
            if (phase > TWO_PI) phase -= TWO_PI

            val lfoNorm = (sin(phase) + 1.0) * 0.5
            val gain = 1.0 - (depth * (1.0 - lfoNorm))
            buffer[i] = (buffer[i] * gain).toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DC Blocker
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One-pole highpass DC blocker.
 *
 * Removes DC offset accumulation from waveshaping, feedback, and asymmetric clipping.
 * Default cutoff ~20Hz (coefficient 0.995) for feedback paths.
 * Use coefficient ~0.999 (~5Hz) for master output.
 */
fun SignalGen.dcBlock(coefficient: Double = 0.995): SignalGen {
    var x1 = 0.0
    var y1 = 0.0

    return SignalGen { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val x = buffer[i].toDouble()
            val y = x - x1 + coefficient * y1
            x1 = x
            y1 = flushDenormal(y)
            buffer[i] = y.toFloat()
        }
    }
}
