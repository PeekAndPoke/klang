package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import kotlin.math.*


// ═══════════════════════════════════════════════════════════════════════════════
// Distortion
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Distortion / waveshaping combinator. Processes per-sample.
 *
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
 * Includes a DC blocker for asymmetric shapes (diode, rectify).
 */
fun Exciter.distort(amount: Exciter, shape: String = "soft"): Exciter {
    val resolved = resolveDistortionShape(shape)
    val waveshaper = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock

    // DC blocker state
    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Exciters.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Exciter

        val drive = 10.0.pow(amt * 1.2)

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

/** Double convenience overload — keeps early return optimization. */
fun Exciter.distort(amount: Double, shape: String = "soft"): Exciter {
    if (amount <= 0.0) return this
    return distort(ParamExciter("amount", amount), shape)
}

/**
 * Pre-amplification stage. Boosts signal level.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 */
fun Exciter.drive(amount: Exciter, type: String = "linear"): Exciter {
    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Exciters.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Exciter

        val driveGain = when (type.lowercase()) {
            "linear" -> 10.0.pow(amt * 1.2)
            else -> 10.0.pow(amt * 1.2) // default to linear, future: tube, fet, tape
        }

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (buffer[i] * driveGain).toFloat()
        }
    }
}

/** Double convenience overload. */
fun Exciter.drive(amount: Double, type: String = "linear"): Exciter {
    if (amount <= 0.0) return this
    return drive(ParamExciter("amount", amount), type)
}

/**
 * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
 * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
 * Includes DC blocker for asymmetric shapes (diode, rectify).
 */
fun Exciter.clip(shape: String = "soft"): Exciter {
    val resolved = resolveDistortionShape(shape)
    val clipFn = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock

    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            var y = clipFn(buffer[i].toDouble()) * outputGain

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

// ResolvedShape and resolveDistortionShape() moved to audio_be/DistortionShape.kt

// ═══════════════════════════════════════════════════════════════════════════════
// BitCrush
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bit-depth reduction (bitcrush) for lo-fi digital sound. Processes per-sample.
 * Amount is read once per block (control rate). Bypasses when amount <= 0 or levels <= 1.
 */
fun Exciter.crush(amount: Exciter): Exciter {
    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Exciters.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Exciter

        val levels = 2.0.pow(amt).toInt().toDouble()
        if (levels <= 1.0) return@Exciter

        val halfLevels = levels / 2.0

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (floor(buffer[i].toDouble() * halfLevels) / halfLevels).toFloat()
        }
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.crush(amount: Double): Exciter {
    if (amount <= 0.0) return this
    val levels = 2.0.pow(amount).toInt().toDouble()
    if (levels <= 1.0) return this
    return crush(ParamExciter("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Coarse (Sample Rate Reducer)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sample-rate reducer (coarse). Holds a sample value for multiple frames. Processes per-sample.
 * Amount is read once per block (control rate). Bypasses when amount <= 1.0.
 */
fun Exciter.coarse(amount: Exciter): Exciter {
    var lastValue = 0.0f
    var counter = 0.0

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Exciters.readParam(amount, freqHz, ctx)
        if (amt <= 1.0) return@Exciter

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val idx = i - ctx.offset

            if (counter >= 1.0 || (idx == 0 && counter == 0.0)) {
                lastValue = buffer[i]
                counter -= 1.0
            }

            buffer[i] = lastValue
            counter += (1.0 / amt)
        }
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.coarse(amount: Double): Exciter {
    if (amount <= 1.0) return this
    return coarse(ParamExciter("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Phaser
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-stage all-pass cascade phaser with sine LFO modulation. Processes per-sample.
 * Rate, depth, center, and sweep are read once per block (control rate). Bypasses when depth <= 0.
 */
fun Exciter.phaser(
    rate: Exciter,
    depth: Exciter,
    center: Exciter = ParamExciter("center", 1000.0),
    sweep: Exciter = ParamExciter("sweep", 1000.0),
): Exciter {
    val stages = 4
    var lfoPhase = 0.0
    val filterState = DoubleArray(stages)
    var lastOutput = 0.0
    val feedback = 0.5

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val rateVal = Exciters.readParam(rate, freqHz, ctx)
        val depthVal = Exciters.readParam(depth, freqHz, ctx)
        val centerVal = Exciters.readParam(center, freqHz, ctx)
        val sweepVal = Exciters.readParam(sweep, freqHz, ctx)

        if (depthVal <= 0.0) return@Exciter

        val inverseSampleRate = 1.0 / ctx.sampleRate
        val lfoIncrement = rateVal * TWO_PI * inverseSampleRate

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            // LFO
            lfoPhase += lfoIncrement
            if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI
            val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

            // Modulated frequency
            var modFreq = centerVal + (lfoValue - 0.5) * sweepVal
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
            buffer[i] = (buffer[i] + signal * depthVal).toFloat()
        }
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.phaser(
    rate: Double,
    depth: Double,
    center: Double = 1000.0,
    sweep: Double = 1000.0,
): Exciter {
    if (depth <= 0.0) return this
    return phaser(
        ParamExciter("rate", rate),
        ParamExciter("depth", depth),
        ParamExciter("center", center),
        ParamExciter("sweep", sweep),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tremolo
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Amplitude modulation (tremolo) via sine LFO. Processes per-sample.
 * Rate and depth are read once per block (control rate). Bypasses when depth <= 0.
 */
fun Exciter.tremolo(
    rate: Exciter,
    depth: Exciter,
): Exciter {
    var phase = 0.0

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val rateVal = Exciters.readParam(rate, freqHz, ctx)
        val depthVal = Exciters.readParam(depth, freqHz, ctx)

        if (depthVal <= 0.0) return@Exciter

        val phaseInc = (TWO_PI * rateVal) / ctx.sampleRate

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            phase += phaseInc
            if (phase > TWO_PI) phase -= TWO_PI

            val lfoNorm = (sin(phase) + 1.0) * 0.5
            val gain = 1.0 - (depthVal * (1.0 - lfoNorm))
            buffer[i] = (buffer[i] * gain).toFloat()
        }
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Exciter.tremolo(
    rate: Double,
    depth: Double,
): Exciter {
    if (depth <= 0.0) return this
    return tremolo(ParamExciter("rate", rate), ParamExciter("depth", depth))
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
fun Exciter.dcBlock(coefficient: Double = 0.995): Exciter {
    var x1 = 0.0
    var y1 = 0.0

    return Exciter { buffer, freqHz, ctx ->
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
