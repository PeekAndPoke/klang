package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.flushDenormal
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tan


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
 * The SVF topology computes lowpass, highpass, bandpass, and notch simultaneously;
 * [mode] selects which output is used. Each instance creates per-voice filter state
 * in its closure. Optional [env] modulates cutoff at control rate (once per block).
 *
 * @param mode Filter type: [SvfMode.LOWPASS], [SvfMode.HIGHPASS], [SvfMode.BANDPASS], or [SvfMode.NOTCH].
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Typical: 200–8000 for LP, 100–2000 for HP, 300–5000 for BP/Notch.
 * @param q Resonance / Q factor. 0.707 = flat (Butterworth), higher = sharper peak.
 *   Clamped to [0.1, 50.0]. Default: 0.707. Typical range: 0.5–10.0.
 * @param env Optional ADSR envelope to modulate cutoff over time. Default: none.
 */
fun Ignitor.svf(
    mode: SvfMode,
    cutoffHz: Ignitor,
    q: Ignitor = ParamIgnitor("q", 0.707),
    env: FilterEnvelope = FilterEnvelope.NONE,
): Ignitor {
    var ic1eq = 0.0
    var ic2eq = 0.0
    var a1 = 0.0
    var a2 = 0.0
    var a3 = 0.0
    var k = 0.0
    var initialized = false
    val hasEnv = env.depth != 0.0

    return Ignitor { output, freqHz, ctx ->
        // Read upstream into a scratch buffer so the caller's input is never mutated.
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            // Read cutoff and Q per block (control rate)
            val baseCutoff = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val qVal = Ignitors.readParam(q, freqHz, ctx)

            val effectiveCutoff = if (hasEnv) {
                val envValue = computeFilterEnvelope(ctx, env.attackSec, env.decaySec, env.sustainLevel, env.releaseSec)
                baseCutoff * (1.0 + env.depth * envValue)
            } else {
                baseCutoff
            }

            if (!initialized || hasEnv || cutoffHz !is ParamIgnitor || q !is ParamIgnitor) {
                val nyquist = 0.5 * ctx.sampleRate
                val fc = effectiveCutoff.coerceIn(5.0, nyquist - 1.0)
                val Q = qVal.coerceIn(0.1, 50.0)
                val g = tan(PI * fc / ctx.sampleRate)
                k = 1.0 / Q
                a1 = 1.0 / (1.0 + g * (g + k))
                a2 = g * a1
                a3 = g * a2
                initialized = true
            }

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val v0 = input[i].toDouble()
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)

                output[i] = when (mode) {
                    SvfMode.LOWPASS -> v2
                    SvfMode.HIGHPASS -> v0 - k * v1 - v2
                    SvfMode.BANDPASS -> v1
                    SvfMode.NOTCH -> v0 - k * v1
                }.toFloat()
            }
        }
    }
}

/**
 * SVF filter with constant cutoff and Q (convenience overload).
 *
 * @param mode Filter type: LOWPASS, HIGHPASS, BANDPASS, or NOTCH.
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 50.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.svf(
    mode: SvfMode,
    cutoffHz: Double,
    q: Double = 0.707,
    env: FilterEnvelope = FilterEnvelope.NONE,
): Ignitor = svf(mode, ParamIgnitor("cutoffHz", cutoffHz), ParamIgnitor("q", q), env)

// ═══════════════════════════════════════════════════════════════════════════════
// Convenience wrappers — delegates to svf() with the appropriate mode
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Lowpass filter — lets low frequencies through, dulls the highs.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 200–8000.
 * @param q Resonance. 0.707 = flat (Butterworth), higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.lowpass(cutoffHz: Ignitor, q: Ignitor = ParamIgnitor("q", 0.707), env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.LOWPASS, cutoffHz, q, env)

/**
 * Lowpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 200–8000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 50.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.lowpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.LOWPASS, cutoffHz, q, env)

/**
 * Highpass filter — lets high frequencies through, removes the bottom.
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 80–2000.
 * @param q Resonance. 0.707 = flat, higher = peak at cutoff. Default: 0.707.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.highpass(cutoffHz: Ignitor, q: Ignitor = ParamIgnitor("q", 0.707), env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.HIGHPASS, cutoffHz, q, env)

/**
 * Highpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 80–2000.
 * @param q Resonance. Default: 0.707 (Butterworth). Clamped to [0.1, 50.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.highpass(cutoffHz: Double, q: Double = 0.707, env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.HIGHPASS, cutoffHz, q, env)

/**
 * Bandpass filter — keeps only a frequency band, removes everything above and below.
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the pass band. 1.0 = moderate, higher = narrower band. Default: 1.0.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.bandpass(cutoffHz: Ignitor, q: Ignitor = ParamIgnitor("q", 1.0), env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.BANDPASS, cutoffHz, q, env)

/**
 * Bandpass filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the pass band. Default: 1.0. Clamped to [0.1, 50.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.bandpass(cutoffHz: Double, q: Double = 1.0, env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.BANDPASS, cutoffHz, q, env)

/**
 * Notch (band-reject) filter — removes one frequency band, keeps everything else.
 *
 * @param cutoffHz Center frequency of the notch in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the notch. 1.0 = moderate, higher = narrower cut. Default: 1.0.
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.notch(cutoffHz: Ignitor, q: Ignitor = ParamIgnitor("q", 1.0), env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.NOTCH, cutoffHz, q, env)

/**
 * Notch (band-reject) filter (convenience overload with fixed values).
 *
 * @param cutoffHz Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–5000.
 * @param q Width of the notch. Default: 1.0. Clamped to [0.1, 50.0].
 * @param env Optional ADSR envelope for cutoff modulation. Default: none.
 */
fun Ignitor.notch(cutoffHz: Double, q: Double = 1.0, env: FilterEnvelope = FilterEnvelope.NONE): Ignitor =
    svf(SvfMode.NOTCH, cutoffHz, q, env)

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Lowpass (for warmth / simple smoothing)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple one-pole lowpass filter for warmth or smoothing. Processes per-sample.
 *
 * Gentler slope than the SVF (6 dB/oct vs 12 dB/oct). Good for subtle tone shaping.
 * Cutoff is read once per block (control rate).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Lower = darker/warmer, higher = more transparent. Typical: 1000–8000.
 */
fun Ignitor.onePoleLowpass(cutoffHz: Ignitor): Ignitor {
    var y = 0.0

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val fc = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val nyquist = 0.5 * ctx.sampleRate
            val alpha = 1.0 - exp(-2.0 * PI * fc.coerceIn(5.0, nyquist - 1.0) / ctx.sampleRate)

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                y += alpha * (input[i].toDouble() - y)
                y = flushDenormal(y)
                output[i] = y.toFloat()
            }
        }
    }
}

/**
 * One-pole lowpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 1000–8000.
 */
fun Ignitor.onePoleLowpass(cutoffHz: Double): Ignitor = onePoleLowpass(ParamIgnitor("cutoffHz", cutoffHz))

// ═══════════════════════════════════════════════════════════════════════════════
// One-Pole Highpass
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple one-pole highpass filter. Processes per-sample.
 *
 * Gentler slope than the SVF (6 dB/oct). Good for removing low-end rumble.
 * Cutoff is read once per block (control rate).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1].
 *   Frequencies below this are attenuated. Typical: 30–500.
 */
fun Ignitor.onePoleHighpass(cutoffHz: Ignitor): Ignitor {
    var y = 0.0
    var xPrev = 0.0

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val fc = Ignitors.readParam(cutoffHz, freqHz, ctx)
            val nyquist = 0.5 * ctx.sampleRate
            val a = exp(-2.0 * PI * fc.coerceIn(5.0, nyquist - 1.0) / ctx.sampleRate)

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val x = input[i].toDouble()
                y = a * (y + x - xPrev)
                y = flushDenormal(y)
                xPrev = x
                output[i] = y.toFloat()
            }
        }
    }
}

/**
 * One-pole highpass with constant cutoff (convenience overload).
 *
 * @param cutoffHz Cutoff frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 30–500.
 */
fun Ignitor.onePoleHighpass(cutoffHz: Double): Ignitor = onePoleHighpass(ParamIgnitor("cutoffHz", cutoffHz))

// ═══════════════════════════════════════════════════════════════════════════════
// Formant Filter (parallel bandpass bank)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Formant filter — parallel bandpass filter bank for vowel synthesis.
 *
 * Sums multiple SVF bandpass filters, each at a different frequency with its own Q and gain.
 * Use to create vowel sounds ("ah", "ee", "oo") or instrument body resonances.
 *
 * @param bands List of [FormantBand] specifications, each with freq (Hz), q, and db (gain).
 *   Typical vowel: 3–5 bands between 300–3500 Hz with Q of 5–15.
 */
fun Ignitor.formant(bands: List<FormantBand>): Ignitor {
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

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                output[i] = 0.0f
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
                    val v0 = input[i].toDouble()
                    val v3 = v0 - band.ic2eq
                    val v1 = band.a1 * band.ic1eq + band.a2 * v3
                    val v2 = band.ic2eq + band.a2 * band.ic1eq + band.a3 * v3
                    band.ic1eq = flushDenormal(2.0 * v1 - band.ic1eq)
                    band.ic2eq = flushDenormal(2.0 * v2 - band.ic2eq)
                    output[i] = (output[i] + v1 * band.linearGain).toFloat()
                }
            }
        }
    }
}

/**
 * A single formant band specification.
 *
 * @property freq Center frequency in Hz. Clamped to [5, Nyquist-1]. Typical: 300–3500.
 * @property q Bandwidth (resonance). Higher = narrower peak. Typical: 5–15.
 * @property db Gain in decibels. 0 = unity, negative = attenuated, positive = boosted.
 */
data class FormantBand(
    val freq: Double,
    val q: Double,
    val db: Double,
)

// ═══════════════════════════════════════════════════════════════════════════════
// Warmth (one-pole low-pass based on alpha factor)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Wraps a [Ignitor] with a one-pole low-pass filter controlled by warmth factor.
 * One-pole LPF: `smoothed = raw + alpha * (lastSample - raw)`.
 *
 * @param warmthFactor Amount of filtering (0.0 = none/bypass, up to 0.99 = very muffled).
 */
fun Ignitor.withWarmth(warmthFactor: Double): Ignitor {
    if (warmthFactor <= 0.0) return this

    var lastSample = 0.0
    val alpha = warmthFactor.coerceIn(0.0, 0.99)

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val raw = input[i].toDouble()
                val smoothed = raw + alpha * (lastSample - raw)
                output[i] = smoothed.toFloat()
                lastSample = flushDenormal(smoothed)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Internal: Filter envelope computation (control rate)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Computes the ADSR envelope level at a given position (attack/decay/sustain — no release).
 *
 * Used to determine the actual level at any point during the gate-on phase,
 * including at gate-end for correct release-start calculation.
 */
private fun envelopeLevelAtPosition(
    absPos: Int,
    attackFrames: Int,
    decayFrames: Int,
    sustainLevel: Double,
): Double = when {
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

/**
 * Computes a simple ADSR envelope value at the current block position.
 * Called once per block (control rate), not per sample.
 *
 * Release phase decays from the **actual level at gate-end**, not from sustainLevel.
 * This prevents discontinuous jumps (clicks) when gate-off occurs during attack or decay.
 */
internal fun computeFilterEnvelope(
    ctx: IgniteContext,
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
): Double {
    val attackFrames = (attackSec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val decayFrames = (decaySec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val releaseFrames = (releaseSec.coerceAtLeast(0.0) * ctx.sampleRate).toInt()
    val clampedSustain = sustainLevel.coerceIn(0.0, 1.0)

    val absPos = ctx.voiceElapsedFrames
    val gateEndPos = ctx.gateEndFrame

    val envValue = if (absPos >= gateEndPos) {
        val levelAtGateEnd = envelopeLevelAtPosition(gateEndPos, attackFrames, decayFrames, clampedSustain)
        val relPos = absPos - gateEndPos
        val relRate = if (releaseFrames > 0) levelAtGateEnd / releaseFrames else 1.0
        levelAtGateEnd - (relPos * relRate)
    } else {
        envelopeLevelAtPosition(absPos, attackFrames, decayFrames, clampedSustain)
    }

    return envValue.coerceIn(0.0, 1.0)
}
