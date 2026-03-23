package io.peekandpoke.klang.audio_be.exciter

import kotlin.math.*

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
private const val DENORMAL_THRESHOLD = 1e-15

/** Flushes a value to zero if it is below the denormal threshold. */
@Suppress("NOTHING_TO_INLINE")
private inline fun flushDenormal(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v

// ═══════════════════════════════════════════════════════════════════════════════
// SVF Mode enum — determines which output tap is used
// ═══════════════════════════════════════════════════════════════════════════════

/** SVF filter mode — selects the output tap from the state variable filter topology. */
enum class SvfMode {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
    NOTCH,
}

// ═══════════════════════════════════════════════════════════════════════════════
// Filter envelope parameters (optional, used by all filter combinators)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Optional ADSR-style envelope that modulates filter cutoff at control rate (once per block).
 *
 * When applied, the effective cutoff becomes: `baseCutoff * (1.0 + depth * envValue)`
 * where `envValue` is 0.0..1.0 from the envelope shape.
 */
data class FilterEnvelope(
    val depth: Double = 0.0,
    val attackSec: Double = 0.0,
    val decaySec: Double = 0.0,
    val sustainLevel: Double = 1.0,
    val releaseSec: Double = 0.0,
) {
    companion object {
        val NONE = FilterEnvelope()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SVF Filter — unified implementation for all modes
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * State Variable Filter (SVF) combinator.
 *
 * Ported from: LowPassHighPassFilters.kt (BaseSvf, SvfLPF, SvfHPF, SvfBPF, SvfNotch)
 *
 * The SVF topology computes all four outputs simultaneously; [mode] selects which one is used.
 * Each instance creates per-voice filter state (ic1eq, ic2eq) in its closure.
 *
 * Optional [env] modulates cutoff at control rate (once per block).
 */
fun Exciter.svf(
    mode: SvfMode,
    cutoffHz: Double,
    q: Double = 0.707,
    env: FilterEnvelope = FilterEnvelope.NONE,
): Exciter {
    var ic1eq = 0.0
    var ic2eq = 0.0
    var a1 = 0.0
    var a2 = 0.0
    var a3 = 0.0
    var k = 0.0
    var initialized = false
    val hasEnv = env.depth != 0.0

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val effectiveCutoff = if (hasEnv) {
            val envValue = computeFilterEnvelope(ctx, env.attackSec, env.decaySec, env.sustainLevel, env.releaseSec)
            cutoffHz * (1.0 + env.depth * envValue)
        } else {
            cutoffHz
        }

        if (!initialized || hasEnv) {
            val nyquist = 0.5 * ctx.sampleRate
            val fc = effectiveCutoff.coerceIn(5.0, nyquist - 1.0)
            val Q = q.coerceIn(0.1, 50.0)
            val g = tan(PI * fc / ctx.sampleRate)
            k = 1.0 / Q
            a1 = 1.0 / (1.0 + g * (g + k))
            a2 = g * a1
            a3 = g * a2
            initialized = true
        }

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v0 = buffer[i].toDouble()
            val v3 = v0 - ic2eq
            val v1 = a1 * ic1eq + a2 * v3
            val v2 = ic2eq + a2 * ic1eq + a3 * v3
            ic1eq = flushDenormal(2.0 * v1 - ic1eq)
            ic2eq = flushDenormal(2.0 * v2 - ic2eq)

            buffer[i] = when (mode) {
                SvfMode.LOWPASS -> v2
                SvfMode.HIGHPASS -> v0 - k * v1 - v2
                SvfMode.BANDPASS -> v1
                SvfMode.NOTCH -> v0 - k * v1
            }.toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Convenience wrappers — delegates to svf() with the appropriate mode
// ═══════════════════════════════════════════════════════════════════════════════

fun Exciter.lowpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvelope = FilterEnvelope.NONE): Exciter =
    svf(SvfMode.LOWPASS, cutoffHz, q, env)

fun Exciter.highpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvelope = FilterEnvelope.NONE): Exciter =
    svf(SvfMode.HIGHPASS, cutoffHz, q, env)

fun Exciter.bandpass(cutoffHz: Double, q: Double = 1.0, env: FilterEnvelope = FilterEnvelope.NONE): Exciter =
    svf(SvfMode.BANDPASS, cutoffHz, q, env)

fun Exciter.notch(cutoffHz: Double, q: Double = 1.0, env: FilterEnvelope = FilterEnvelope.NONE): Exciter =
    svf(SvfMode.NOTCH, cutoffHz, q, env)

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Lowpass (for warmth / simple smoothing)
// ═══════════════════════════════════════════════════════════════════════════════

fun Exciter.onePoleLowpass(cutoffHz: Double): Exciter {
    var y = 0.0
    var alpha = 0.0
    var initialized = false

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        if (!initialized) {
            val nyquist = 0.5 * ctx.sampleRate
            val fc = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            alpha = 1.0 - exp(-2.0 * PI * fc / ctx.sampleRate)
            initialized = true
        }

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            y += alpha * (buffer[i].toDouble() - y)
            y = flushDenormal(y)
            buffer[i] = y.toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Highpass
// ═══════════════════════════════════════════════════════════════════════════════

fun Exciter.onePoleHighpass(cutoffHz: Double): Exciter {
    var y = 0.0
    var xPrev = 0.0
    var a = 0.0
    var initialized = false

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        if (!initialized) {
            val nyquist = 0.5 * ctx.sampleRate
            val fc = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            a = exp(-2.0 * PI * fc / ctx.sampleRate)
            initialized = true
        }

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val x = buffer[i].toDouble()
            y = a * (y + x - xPrev)
            y = flushDenormal(y)
            xPrev = x
            buffer[i] = y.toFloat()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Formant Filter (parallel bandpass bank)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Formant filter: parallel SVF bandpass filters summed together.
 * Each band has a center frequency, Q, and gain in dB.
 *
 * Ported from: FormantFilter.kt
 */
fun Exciter.formant(bands: List<FormantBand>): Exciter {
    class BandState(val freq: Double, val q: Double, val linearGain: Double) {
        var ic1eq = 0.0
        var ic2eq = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var a3 = 0.0
        var initialized = false
    }

    val bandStates = bands.map { band ->
        BandState(band.freq, band.q, 10.0.pow(band.db / 20.0))
    }

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length

        ctx.scratchBuffers.use { inputCopy ->
            for (i in ctx.offset until end) {
                inputCopy[i] = buffer[i]
            }
            for (i in ctx.offset until end) {
                buffer[i] = 0.0f
            }

            for (band in bandStates) {
                if (!band.initialized) {
                    val nyquist = 0.5 * ctx.sampleRate
                    val fc = band.freq.coerceIn(5.0, nyquist - 1.0)
                    val Q = band.q.coerceIn(0.1, 50.0)
                    val g = tan(PI * fc / ctx.sampleRate)
                    band.a1 = 1.0 / (1.0 + g * (g + 1.0 / Q))
                    band.a2 = g * band.a1
                    band.a3 = g * band.a2
                    band.initialized = true
                }

                for (i in ctx.offset until end) {
                    val v0 = inputCopy[i].toDouble()
                    val v3 = v0 - band.ic2eq
                    val v1 = band.a1 * band.ic1eq + band.a2 * v3
                    val v2 = band.ic2eq + band.a2 * band.ic1eq + band.a3 * v3
                    band.ic1eq = flushDenormal(2.0 * v1 - band.ic1eq)
                    band.ic2eq = flushDenormal(2.0 * v2 - band.ic2eq)
                    buffer[i] = (buffer[i] + v1 * band.linearGain).toFloat()
                }
            }
        }
    }
}

/** A single formant band specification. */
data class FormantBand(
    val freq: Double,
    val q: Double,
    val db: Double,
)

// ═══════════════════════════════════════════════════════════════════════════════
// Warmth (one-pole low-pass based on alpha factor)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Wraps a [Exciter] with a one-pole low-pass filter controlled by warmth factor.
 * One-pole LPF: `smoothed = raw + alpha * (lastSample - raw)`.
 *
 * @param warmthFactor Amount of filtering (0.0 = none/bypass, up to 0.99 = very muffled).
 */
fun Exciter.withWarmth(warmthFactor: Double): Exciter {
    if (warmthFactor <= 0.0) return this

    var lastSample = 0.0
    val alpha = warmthFactor.coerceIn(0.0, 0.99)

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val raw = buffer[i].toDouble()
            val smoothed = raw + alpha * (lastSample - raw)
            buffer[i] = smoothed.toFloat()
            lastSample = flushDenormal(smoothed)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Internal: Filter envelope computation (control rate)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Computes a simple ADSR envelope value at the current block position.
 * Called once per block (control rate), not per sample.
 *
 * Ported from: calculateFilterEnvelope() in voices/common.kt
 */
internal fun computeFilterEnvelope(
    ctx: ExciteContext,
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
): Double {
    val attackFrames = (attackSec * ctx.sampleRate).toInt()
    val decayFrames = (decaySec * ctx.sampleRate).toInt()
    val releaseFrames = (releaseSec * ctx.sampleRate).toInt()

    val absPos = ctx.voiceElapsedFrames
    val gateEndPos = ctx.gateEndFrame

    val envValue = if (absPos >= gateEndPos) {
        val relPos = absPos - gateEndPos
        val relRate = if (releaseFrames > 0) sustainLevel / releaseFrames else 1.0
        sustainLevel - (relPos * relRate)
    } else {
        when {
            absPos < attackFrames -> {
                val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
                absPos * attRate
            }

            absPos < attackFrames + decayFrames -> {
                val decPos = absPos - attackFrames
                val decRate = if (decayFrames > 0) (1.0 - sustainLevel) / decayFrames else 0.0
                1.0 - (decPos * decRate)
            }

            else -> sustainLevel
        }
    }

    return envValue.coerceIn(0.0, 1.0)
}
