package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.AudioSample

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
 *
 * **NOT a `fun interface`** by design — see `audio/ref/performance.md`. SAM-constructor
 * usage (`Ignitor { … }`) silently turns mutable closure-captured `var`s into Kotlin/JS
 * `ObjectRef` wrappers, with per-sample property-load cost. Stateful implementations
 * must be regular classes with explicit fields; stateless ones can use anonymous
 * `object : Ignitor { override fun generate(…) { … } }` if a class is overkill.
 */
interface Ignitor {
    /**
     * @param buffer where to write output samples
     * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
     * @param ctx per-voice rendering context (timing, block params, scratch buffers)
     */
    fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════
//
// All combinators implement `Ignitor` as dedicated `private class` types, not SAM
// lambdas. See `audio/ref/performance.md` for the rationale (Rule 1).

/** Mix two signals additively per-sample. Uses a scratch buffer for the second signal. */
operator fun Ignitor.plus(other: Ignitor): Ignitor = PlusIgnitor(this, other)

private class PlusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] + tmp[i]
            }
        }
    }
}

/**
 * Ring-modulate two signals by per-sample multiplication. Uses a scratch buffer for the second signal.
 *
 * Output magnitude is clamped to `±SAFE_MAX` and `NaN` is scrubbed to `0` per sample.
 * See `audio/ref/numerical-safety.md` for the safety contract.
 */
operator fun Ignitor.times(other: Ignitor): Ignitor = TimesIgnitor(this, other)

private class TimesIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = safeOut(buffer[i] * tmp[i])
            }
        }
    }
}

/** Scale signal amplitude per-sample by an audio-rate [factor]. Delegates to [times]. */
fun Ignitor.mul(factor: Ignitor): Ignitor = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Ignitor.mul(factor: Double): Ignitor {
    if (factor == 1.0) return this
    return MulConstIgnitor(this, factor)
}

private class MulConstIgnitor(private val upstream: Ignitor, private val factor: Double) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(buffer[i] * factor)
        }
    }
}

/**
 * Divide signal amplitude per-sample by an audio-rate [divisor].
 *
 * Divisor magnitudes below `SAFE_MIN` are clamped (sign preserved) so the engine
 * never produces `NaN`/`Inf`. The output is also clamped to `±SAFE_MAX`.
 * See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.div(divisor: Ignitor): Ignitor = DivIgnitor(this, divisor)

private class DivIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = safeOut(buffer[i] / safeDiv(tmp[i]))
            }
        }
    }
}

/**
 * Divide signal amplitude by a constant [divisor].
 * Zero divisor is substituted with `±SAFE_MIN`; output clamped to `±SAFE_MAX`.
 */
fun Ignitor.div(divisor: Double): Ignitor {
    val safeFactor = 1.0 / safeDiv(divisor)
    return mul(safeFactor)
}

/** Subtract another signal from this one (per-sample). Uses a scratch buffer for the second signal. */
fun Ignitor.minus(other: Ignitor): Ignitor = MinusIgnitor(this, other)

private class MinusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] - tmp[i]
            }
        }
    }
}

/** Negate this signal (flip polarity, per-sample). */
fun Ignitor.neg(): Ignitor = NegIgnitor(this)

private class NegIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = -buffer[i]
        }
    }
}

/** Absolute value of this signal (per-sample, full-wave rectification). */
fun Ignitor.abs(): Ignitor = AbsIgnitor(this)

private class AbsIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = if (v < 0.0) -v else v
        }
    }
}

/**
 * Raise this signal to the power of [exp] (per-sample).
 *
 * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
 * `0^negative = +Inf` is caught by the output clamp (`±SAFE_MAX`).
 */
fun Ignitor.pow(exp: Ignitor): Ignitor = PowIgnitor(this, exp)

private class PowIgnitor(private val base: Ignitor, private val exp: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        base.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            exp.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val b = buffer[i]
                val e = tmp[i]
                val raw = if (b >= 0.0) b.pow(e) else -((-b).pow(e))
                buffer[i] = safeOut(raw)
            }
        }
    }
}

/** Per-sample minimum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.min(other: Ignitor): Ignitor = MinIgnitor(this, other)

private class MinIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val x = buffer[i]
                val y = tmp[i]
                buffer[i] = if (x < y) x else y
            }
        }
    }
}

/** Per-sample maximum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.max(other: Ignitor): Ignitor = MaxIgnitor(this, other)

private class MaxIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val x = buffer[i]
                val y = tmp[i]
                buffer[i] = if (x > y) x else y
            }
        }
    }
}

/** Bound this signal to `[lo, hi]` per sample. Uses two scratch buffers. */
fun Ignitor.clamp(lo: Ignitor, hi: Ignitor): Ignitor = ClampIgnitor(this, lo, hi)

private class ClampIgnitor(
    private val upstream: Ignitor,
    private val lo: Ignitor,
    private val hi: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
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
}

/** `e^x` per sample. Output clamped to `±SAFE_MAX` (caught for large `x`, e.g. `exp(40) ≈ 2.4e17`). */
fun Ignitor.exp(): Ignitor = ExpIgnitor(this)

private class ExpIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(exp(buffer[i]))
        }
    }
}

/**
 * Natural logarithm per sample.
 *
 * Signed-magnitude: `x > 0` → `ln(x)`, `x < 0` → `−ln(−x)`, `x == 0` → `0`.
 * Avoids `-Inf` / `NaN` poisoning the audio path.
 */
fun Ignitor.log(): Ignitor = LogIgnitor(this)

private class LogIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = when {
                v > 0.0 -> ln(v)
                v < 0.0 -> -ln((-v))
                else -> 0.0
            }
        }
    }
}

/** Square root per sample. Signed-magnitude: `x < 0` → `−√(−x)`. */
fun Ignitor.sqrt(): Ignitor = SqrtIgnitor(this)

private class SqrtIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = if (v >= 0.0) sqrt(v) else -sqrt((-v))
        }
    }
}

/** Sign of the inner signal: `-1`, `0`, or `+1`. */
fun Ignitor.sign(): Ignitor = SignIgnitor(this)

private class SignIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = when {
                v > 0.0 -> 1.0
                v < 0.0 -> -1.0
                else -> 0.0
            }
        }
    }
}

/** `tanh(x)` per sample. */
fun Ignitor.tanh(): Ignitor = TanhIgnitor(this)

private class TanhIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = tanh(buffer[i])
        }
    }
}

/** Linear interpolation: `this·(1−t) + other·t`. Two scratch buffers. */
fun Ignitor.lerp(other: Ignitor, t: Ignitor): Ignitor = LerpIgnitor(this, other, t)

private class LerpIgnitor(
    private val a: Ignitor,
    private val b: Ignitor,
    private val t: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { otherBuf ->
            b.generate(otherBuf, freqHz, ctx)
            ctx.scratchBuffers.use { tBuf ->
                t.generate(tBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val tv = tBuf[i]
                    buffer[i] = buffer[i] * (1.0 - tv) + otherBuf[i] * tv
                }
            }
        }
    }
}

/** Maps `[-1, 1]` → `[lo, hi]` per sample: `lo + (x + 1)·0.5·(hi − lo)`. */
fun Ignitor.range(lo: Ignitor, hi: Ignitor): Ignitor = RangeIgnitor(this, lo, hi)

private class RangeIgnitor(
    private val upstream: Ignitor,
    private val lo: Ignitor,
    private val hi: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { loBuf ->
            lo.generate(loBuf, freqHz, ctx)
            ctx.scratchBuffers.use { hiBuf ->
                hi.generate(hiBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val l = loBuf[i]
                    val h = hiBuf[i]
                    buffer[i] = l + (buffer[i] + 1.0) * 0.5 * (h - l)
                }
            }
        }
    }
}

/** Maps `[0, 1]` → `[-1, 1]` per sample: `x·2 − 1`. */
fun Ignitor.bipolar(): Ignitor = BipolarIgnitor(this)

private class BipolarIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * 2.0 - 1.0
        }
    }
}

/** Maps `[-1, 1]` → `[0, 1]` per sample: `(x + 1)·0.5`. */
fun Ignitor.unipolar(): Ignitor = UnipolarIgnitor(this)

private class UnipolarIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (buffer[i] + 1.0) * 0.5
        }
    }
}

/** Per-sample floor. */
fun Ignitor.floor(): Ignitor = FloorIgnitor(this)

private class FloorIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = floor(buffer[i])
        }
    }
}

/** Per-sample ceiling. */
fun Ignitor.ceil(): Ignitor = CeilIgnitor(this)

private class CeilIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = ceil(buffer[i])
        }
    }
}

/** Per-sample round to nearest integer. */
fun Ignitor.round(): Ignitor = RoundIgnitor(this)

private class RoundIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = round(buffer[i])
        }
    }
}

/** Per-sample fractional part: `x − floor(x)`. */
fun Ignitor.frac(): Ignitor = FracIgnitor(this)

private class FracIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = (v - floor(v))
        }
    }
}

/**
 * Per-sample modulo (Kotlin `rem` semantics — sign follows the dividend).
 *
 * Divisor magnitudes below `SAFE_MIN` are clamped (sign preserved). Uses one scratch buffer.
 * See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.mod(other: Ignitor): Ignitor = ModIgnitor(this, other)

private class ModIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] % safeDiv(tmp[i])
            }
        }
    }
}

/**
 * Per-sample reciprocal `1/x`.
 *
 * Input magnitudes below `SAFE_MIN` are clamped (sign preserved) so the output
 * stays at `±SAFE_MAX` rather than overflowing to `±Inf`.
 */
fun Ignitor.recip(): Ignitor = RecipIgnitor(this)

private class RecipIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(1.0 / safeDiv(buffer[i]))
        }
    }
}

/** Per-sample square: `x · x`. Output clamped to `±SAFE_MAX` (caught for `|x| > ~3.16e7`). */
fun Ignitor.sq(): Ignitor = SqIgnitor(this)

private class SqIgnitor(private val upstream: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = safeOut(v * v)
        }
    }
}

/**
 * Per-sample conditional: when this signal `> 0`, use [whenTrue]; else [whenFalse].
 *
 * Both branches are evaluated at audio rate — no short-circuit — so any stateful
 * sources advance regardless of which branch is selected.
 */
fun Ignitor.select(whenTrue: Ignitor, whenFalse: Ignitor): Ignitor =
    SelectIgnitor(this, whenTrue, whenFalse)

private class SelectIgnitor(
    private val cond: Ignitor,
    private val whenTrue: Ignitor,
    private val whenFalse: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        cond.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tBuf ->
            whenTrue.generate(tBuf, freqHz, ctx)
            ctx.scratchBuffers.use { fBuf ->
                whenFalse.generate(fBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = if (buffer[i] > 0.0) tBuf[i] else fBuf[i]
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Numerical Safety Bounds
// ═══════════════════════════════════════════════════════════════════════════════
//
// Klang's safety contract — see `audio/ref/numerical-safety.md` for the full story.
//
// Every arithmetic operator that can produce `NaN`/`Inf` clamps either its inputs
// (divisor-class ops: Div, Mod, Recip) or its output (output-clamp ops: Times,
// Pow, Exp, Sq, Mul-by-constant). Naturally bounded ops (Plus, Minus, Lerp,
// Range, Clamp, Min, Max, Abs, Neg, Sign, Floor, Ceil, Round, Frac, Tanh, Sqrt,
// Log) need no extra guard.
//
// Values match the SuperCollider / ChucK / STK convention (`zapgremlins`,
// `CK_DDN_*`): ±300 dBFS, well below any audible signal, well above subnormal.
// `1 / SAFE_MIN = SAFE_MAX` ensures a reciprocal of the smallest allowed
// divisor lands exactly at the largest allowed output — round-trip safe.

/**
 * Smallest allowed magnitude for a divisor (or reciprocal input) in audio arithmetic.
 *
 * Values closer to zero are clamped to `±SAFE_MIN` (sign preserved) to prevent
 * `1/x` from overflowing. ≈ -300 dBFS — well below any audible signal.
 */
const val SAFE_MIN: AudioSample = 1e-15

/**
 * Largest allowed output magnitude for ops that can grow values (`Times`, `Pow`,
 * `Exp`, `Sq`, `Mul-by-constant`).
 *
 * Outputs above this are clamped to `±SAFE_MAX`. ≈ +300 dBFS — vastly above any
 * musical signal, well below `Double.MAX_VALUE` (`≈ 1.8e308`). Squaring two
 * `SAFE_MAX` values gives `1e30`, still finite Double.
 */
const val SAFE_MAX: AudioSample = 1e15

/**
 * Clamp a divisor's magnitude to `≥ SAFE_MIN`, preserving sign.
 *
 * Substitutes `0.0` and `NaN` with `+SAFE_MIN`. `±Inf` passes through unchanged
 * (since `±Inf` is already a valid divisor — `a / ±Inf = ±0`); the resulting
 * `0` or any `NaN` from `Inf - Inf` patterns is scrubbed downstream by [safeOut].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeDiv(d: AudioSample): AudioSample = when {
    d.isNaN() -> SAFE_MIN
    d > SAFE_MIN -> d
    d < -SAFE_MIN -> d
    d < 0.0 -> -SAFE_MIN
    else -> SAFE_MIN
}

/** Clamp an output value to `[-SAFE_MAX, +SAFE_MAX]`. Scrubs `NaN` to `0`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeOut(v: AudioSample): AudioSample = when {
    v.isNaN() -> 0.0
    v > SAFE_MAX -> SAFE_MAX
    v < -SAFE_MAX -> -SAFE_MAX
    else -> v
}

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Ignitor.detune(semitones: Ignitor): Ignitor = DetuneIgnitor(this, semitones)

private class DetuneIgnitor(
    private val upstream: Ignitor,
    private val semitones: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { semiBuf ->
            semitones.generate(semiBuf, freqHz, ctx)
            val s = semiBuf[ctx.offset]
            val ratio = 2.0.pow(s / 12.0)
            upstream.generate(buffer, freqHz * ratio, ctx)
        }
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Ignitor.detune(semitones: Double): Ignitor {
    if (semitones == 0.0) return this
    return DetuneConstIgnitor(this, 2.0.pow(semitones / 12.0))
}

private class DetuneConstIgnitor(
    private val upstream: Ignitor,
    private val ratio: Double,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz * ratio, ctx)
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
