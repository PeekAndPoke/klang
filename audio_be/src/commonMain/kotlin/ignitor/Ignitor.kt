package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Core signal generator interface.
 *
 * An Ignitor is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
 * Ignitors are composed via extension functions (filters, envelopes, arithmetic, pitch modulation).
 */
fun interface Ignitor {
    /**
     * @param buffer where to write output samples
     * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
     * @param ctx per-voice rendering context (timing, block params, scratch buffers)
     */
    fun generate(buffer: FloatArray, freqHz: Double, ctx: IgniteContext)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════

/** Mix two signals additively per-sample. Uses a scratch buffer for the second signal. */
operator fun Ignitor.plus(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] + tmp[i]
        }
    }
}

/** Ring-modulate two signals by per-sample multiplication. Uses a scratch buffer for the second signal. */
operator fun Ignitor.times(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * tmp[i]
        }
    }
}

/** Scale signal amplitude per-sample by an audio-rate [factor]. Delegates to [times]. */
fun Ignitor.mul(factor: Ignitor): Ignitor = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Ignitor.mul(factor: Double): Ignitor {
    if (factor == 1.0) return this
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)
        val f = factor.toFloat()
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * f
        }
    }
}

/**
 * Divide signal amplitude per-sample by an audio-rate [divisor].
 *
 * Zero divisors are substituted with a tiny epsilon (`1e-30`) so the engine
 * never produces `NaN`. The master limiter handles the resulting spike.
 */
fun Ignitor.div(divisor: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        divisor.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val d = tmp[i]
            buffer[i] = buffer[i] / if (d == 0f) DIV_EPSILON else d
        }
    }
}

/** Divide signal amplitude by a constant [divisor]. Delegates to [mul] with the reciprocal. */
fun Ignitor.div(divisor: Double): Ignitor = mul(1.0 / divisor)

/** Subtract another signal from this one (per-sample). Uses a scratch buffer for the second signal. */
fun Ignitor.minus(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] - tmp[i]
        }
    }
}

/** Negate this signal (flip polarity, per-sample). */
fun Ignitor.neg(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = -buffer[i]
    }
}

/** Absolute value of this signal (per-sample, full-wave rectification). */
fun Ignitor.abs(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = if (v < 0f) -v else v
    }
}

/**
 * Raise this signal to the power of [exp] (per-sample).
 * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
 */
fun Ignitor.pow(exp: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        exp.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val b = buffer[i].toDouble()
            val e = tmp[i].toDouble()
            buffer[i] = if (b >= 0.0) b.pow(e).toFloat() else -((-b).pow(e)).toFloat()
        }
    }
}

/** Per-sample minimum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.min(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val a = buffer[i]
            val b = tmp[i]
            buffer[i] = if (a < b) a else b
        }
    }
}

/** Per-sample maximum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.max(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val a = buffer[i]
            val b = tmp[i]
            buffer[i] = if (a > b) a else b
        }
    }
}

/** Bound this signal to `[lo, hi]` per sample. Uses two scratch buffers. */
fun Ignitor.clamp(lo: Ignitor, hi: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { loBuf ->
        lo.generate(loBuf, freqHz, ctx)
        ctx.scratchBuffers.use { hiBuf ->
            hi.generate(hiBuf, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val v = buffer[i]
                val l = loBuf[i]
                val h = hiBuf[i]
                buffer[i] = when {
                    v < l -> l
                    v > h -> h
                    else -> v
                }
            }
        }
    }
}

/** `e^x` per sample. */
fun Ignitor.exp(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = exp(buffer[i].toDouble()).toFloat()
    }
}

/**
 * Natural logarithm per sample.
 *
 * Signed-magnitude: `x > 0` → `ln(x)`, `x < 0` → `−ln(−x)`, `x == 0` → `0`.
 * Avoids `-Inf` / `NaN` poisoning the audio path.
 */
fun Ignitor.log(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = when {
            v > 0f -> ln(v.toDouble()).toFloat()
            v < 0f -> -ln((-v).toDouble()).toFloat()
            else -> 0f
        }
    }
}

/** Square root per sample. Signed-magnitude: `x < 0` → `−√(−x)`. */
fun Ignitor.sqrt(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = if (v >= 0f) sqrt(v.toDouble()).toFloat() else -sqrt((-v).toDouble()).toFloat()
    }
}

/** Sign of the inner signal: `-1`, `0`, or `+1`. */
fun Ignitor.sign(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = when {
            v > 0f -> 1f
            v < 0f -> -1f
            else -> 0f
        }
    }
}

/** `tanh(x)` per sample. */
fun Ignitor.tanh(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = tanh(buffer[i].toDouble()).toFloat()
    }
}

/** Linear interpolation: `this·(1−t) + other·t`. Two scratch buffers. */
fun Ignitor.lerp(other: Ignitor, t: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { otherBuf ->
        other.generate(otherBuf, freqHz, ctx)
        ctx.scratchBuffers.use { tBuf ->
            t.generate(tBuf, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val tv = tBuf[i]
                buffer[i] = buffer[i] * (1f - tv) + otherBuf[i] * tv
            }
        }
    }
}

/** Maps `[-1, 1]` → `[lo, hi]` per sample: `lo + (x + 1)·0.5·(hi − lo)`. */
fun Ignitor.range(lo: Ignitor, hi: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { loBuf ->
        lo.generate(loBuf, freqHz, ctx)
        ctx.scratchBuffers.use { hiBuf ->
            hi.generate(hiBuf, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val l = loBuf[i]
                val h = hiBuf[i]
                buffer[i] = l + (buffer[i] + 1f) * 0.5f * (h - l)
            }
        }
    }
}

/** Maps `[0, 1]` → `[-1, 1]` per sample: `x·2 − 1`. */
fun Ignitor.bipolar(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = buffer[i] * 2f - 1f
    }
}

/** Maps `[-1, 1]` → `[0, 1]` per sample: `(x + 1)·0.5`. */
fun Ignitor.unipolar(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = (buffer[i] + 1f) * 0.5f
    }
}

/** Per-sample floor. */
fun Ignitor.floor(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = floor(buffer[i].toDouble()).toFloat()
    }
}

/** Per-sample ceiling. */
fun Ignitor.ceil(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = ceil(buffer[i].toDouble()).toFloat()
    }
}

/** Per-sample round to nearest integer. */
fun Ignitor.round(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = round(buffer[i].toDouble()).toFloat()
    }
}

/** Per-sample fractional part: `x − floor(x)`. */
fun Ignitor.frac(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i].toDouble()
        buffer[i] = (v - floor(v)).toFloat()
    }
}

/**
 * Per-sample modulo. Zero divisor → epsilon to keep the engine `NaN`-free.
 * Uses one scratch buffer for the divisor.
 */
fun Ignitor.mod(other: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tmp ->
        other.generate(tmp, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val d = tmp[i]
            val safeD = if (d == 0f) DIV_EPSILON else d
            buffer[i] = buffer[i] % safeD
        }
    }
}

/** Per-sample reciprocal `1/x`. Zero inputs → epsilon to avoid `Inf`/`NaN`. */
fun Ignitor.recip(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = 1f / if (v == 0f) DIV_EPSILON else v
    }
}

/** Per-sample square: `x · x`. */
fun Ignitor.sq(): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        val v = buffer[i]
        buffer[i] = v * v
    }
}

/**
 * Per-sample conditional: when this signal `> 0`, use [whenTrue]; else [whenFalse].
 *
 * Both branches are evaluated at audio rate — no short-circuit — so any stateful
 * sources advance regardless of which branch is selected.
 */
fun Ignitor.select(whenTrue: Ignitor, whenFalse: Ignitor): Ignitor = Ignitor { buffer, freqHz, ctx ->
    this.generate(buffer, freqHz, ctx)
    ctx.scratchBuffers.use { tBuf ->
        whenTrue.generate(tBuf, freqHz, ctx)
        ctx.scratchBuffers.use { fBuf ->
            whenFalse.generate(fBuf, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = if (buffer[i] > 0f) tBuf[i] else fBuf[i]
            }
        }
    }
}

/**
 * Epsilon used to substitute a zero divisor in [div], [mod], and [recip].
 *
 * Picked small enough that `1 / EPSILON` lands well above the audio range
 * (the master limiter clamps the resulting spike) and large enough to avoid
 * subnormal-float performance penalties.
 */
private const val DIV_EPSILON: Float = 1e-30f

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Ignitor.detune(semitones: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        ctx.scratchBuffers.use { semiBuf ->
            semitones.generate(semiBuf, freqHz, ctx)
            val s = semiBuf[ctx.offset].toDouble()
            val ratio = 2.0.pow(s / 12.0)
            this.generate(buffer, freqHz * ratio, ctx)
        }
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Ignitor.detune(semitones: Double): Ignitor {
    if (semitones == 0.0) return this
    val ratio = 2.0.pow(semitones / 12.0)
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz * ratio, ctx)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Gain from Ignitor (param slot)
// ═══════════════════════════════════════════════════════════════════════════════

/** Apply audio-rate gain from another exciter. Short-circuits when [gain] is a [ParamIgnitor] with default 1.0. */
fun Ignitor.withGain(gain: Ignitor): Ignitor {
    if (gain is ParamIgnitor && gain.default == 1.0) return this
    return this * gain
}

/** Shift frequency up one octave. */
fun Ignitor.octaveUp(): Ignitor = detune(12.0)

/** Shift frequency down one octave. */
fun Ignitor.octaveDown(): Ignitor = detune(-12.0)
