package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan


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
fun Ignitor.distort(amount: Ignitor, shape: String = "soft"): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val waveshaper = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock

    // DC blocker state
    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Ignitor

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
fun Ignitor.distort(amount: Double, shape: String = "soft"): Ignitor {
    if (amount <= 0.0) return this
    return distort(ParamIgnitor("amount", amount), shape)
}

/**
 * Pre-amplification stage. Boosts signal level.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 */
fun Ignitor.drive(amount: Ignitor, type: String = "linear"): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Ignitor

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
fun Ignitor.drive(amount: Double, type: String = "linear"): Ignitor {
    if (amount <= 0.0) return this
    return drive(ParamIgnitor("amount", amount), type)
}

/**
 * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
 * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
 * Includes DC blocker for asymmetric shapes (diode, rectify).
 */
fun Ignitor.clip(shape: String = "soft"): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val clipFn = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock

    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Ignitor { buffer, freqHz, ctx ->
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
fun Ignitor.crush(amount: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Ignitor

        val levels = 2.0.pow(amt).toInt().toDouble()
        if (levels <= 1.0) return@Ignitor

        val halfLevels = levels / 2.0

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (floor(buffer[i].toDouble() * halfLevels) / halfLevels).toFloat()
        }
    }
}

/** Double convenience overload — keeps early return optimization. */
fun Ignitor.crush(amount: Double): Ignitor {
    if (amount <= 0.0) return this
    val levels = 2.0.pow(amount).toInt().toDouble()
    if (levels <= 1.0) return this
    return crush(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Coarse (Sample Rate Reducer)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sample-rate reducer (coarse). Holds a sample value for multiple frames. Processes per-sample.
 * Amount is read once per block (control rate). Bypasses when amount <= 1.0.
 */
fun Ignitor.coarse(amount: Ignitor): Ignitor {
    var lastValue = 0.0f
    var counter = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)
        if (amt <= 1.0) return@Ignitor

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
fun Ignitor.coarse(amount: Double): Ignitor {
    if (amount <= 1.0) return this
    return coarse(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Phaser
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-stage all-pass cascade phaser with sine LFO modulation. Processes per-sample.
 * Rate, depth, center, and sweep are read once per block (control rate). Bypasses when depth <= 0.
 */
fun Ignitor.phaser(
    rate: Ignitor,
    depth: Ignitor,
    center: Ignitor = ParamIgnitor("center", 1000.0),
    sweep: Ignitor = ParamIgnitor("sweep", 1000.0),
): Ignitor {
    val stages = 4
    var lfoPhase = 0.0
    val filterState = DoubleArray(stages)
    var lastOutput = 0.0
    val feedback = 0.5

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val rateVal = Ignitors.readParam(rate, freqHz, ctx)
        val depthVal = Ignitors.readParam(depth, freqHz, ctx)
        val centerVal = Ignitors.readParam(center, freqHz, ctx)
        val sweepVal = Ignitors.readParam(sweep, freqHz, ctx)

        if (depthVal <= 0.0) return@Ignitor

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
fun Ignitor.phaser(
    rate: Double,
    depth: Double,
    center: Double = 1000.0,
    sweep: Double = 1000.0,
): Ignitor {
    if (depth <= 0.0) return this
    return phaser(
        ParamIgnitor("rate", rate),
        ParamIgnitor("depth", depth),
        ParamIgnitor("center", center),
        ParamIgnitor("sweep", sweep),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tremolo
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Amplitude modulation (tremolo) via sine LFO. Processes per-sample.
 * Rate and depth are read once per block (control rate). Bypasses when depth <= 0.
 */
fun Ignitor.tremolo(
    rate: Ignitor,
    depth: Ignitor,
): Ignitor {
    var phase = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val rateVal = Ignitors.readParam(rate, freqHz, ctx)
        val depthVal = Ignitors.readParam(depth, freqHz, ctx)

        if (depthVal <= 0.0) return@Ignitor

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
fun Ignitor.tremolo(
    rate: Double,
    depth: Double,
): Ignitor {
    if (depth <= 0.0) return this
    return tremolo(ParamIgnitor("rate", rate), ParamIgnitor("depth", depth))
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
fun Ignitor.dcBlock(coefficient: Double = 0.995): Ignitor {
    var x1 = 0.0
    var y1 = 0.0

    return Ignitor { buffer, freqHz, ctx ->
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
