/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_be.utils.nanGuard
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * One Karplus-Strong string: a delay line excited by a noise burst, read with a four-point (cubic Lagrange)
 * interpolation, its feedback filtered by a brightness one-pole and an optional stiffness allpass, then scaled by the
 * feedback coefficient.
 *
 * **The read** (KLANG-00430, audit finding L3-07): the fractional delay sits inside the loop, so its gain is paid on
 * every trip. A linear read's gain is 1 at a fraction of 0 and `cos(w / 2)` at 0.5 (0.71 at 12 kHz), so two notes 2 Hz
 * apart rang very differently at brightness 1: one kept its top for seconds, the other lost it in tens of
 * milliseconds. The cubic Lagrange read ([lagrange4]) is 0.88 at 12 kHz at its worst fraction and 0.99 at 6 kHz; the
 * note dependence shrinks but stays (a lossless allpass read is the other option, see the ticket). Its delay at the
 * note is in [loopDelayOf], so the tuning is untouched.
 *
 * **In tune** (KLANG-00426, audit finding L3-01): the loop's period is the line's read delay PLUS the phase delay of the
 * two loop filters (and of the read's interpolation). The nodes take that delay at the note ([loopDelayOf]) out of the
 * read delay, so the loop's pole sits at the note at every brightness, stiffness and feedback. Before, the line alone was one period
 * and the default pluck sounded 16 cents flat at A4.
 *
 * The string core of both pluck nodes: `Ignitors.karplusStrong` holds one string, `Ignitors.superKarplusStrong` one
 * per unison voice (engine tidy-up step 11). The nodes keep what differs: their param reads (order, count and
 * cadence), their drift (one [AnalogDrift], or [DriftLanes]), their draw order, the unison detune of the base delay
 * and the voice gain.
 *
 * [excite] writes the burst and [writePos] and nothing else: a superpluck string that comes back after a shrink is
 * plucked again, and its filter state ([lpState], [apPrevIn], [apPrevOut]) carries over. [excited] belongs to the
 * node, which sets it before it plucks and clears it to pluck again.
 */
internal class KarplusString {
    /** The delay line, [MAX_DELAY] samples. */
    val delayLine: AudioBuffer = AudioBuffer(MAX_DELAY)

    /** Where the next sample is written. */
    var writePos: Int = 0

    /** Whether the string has been plucked; the node sets it, and clears it for a re-pluck. */
    var excited: Boolean = false

    /** The brightness one-pole's state. */
    var lpState: Double = 0.0

    /** The stiffness allpass's last input. */
    var apPrevIn: Double = 0.0

    /** The stiffness allpass's last output. */
    var apPrevOut: Double = 0.0

    /**
     * Plucks the string: a noise burst over the first `lineDelay.toInt()` samples of the delay line (the read delay,
     * [lineDelay] clamped to the line as [render] clamps it), placed by [pickPos] (0 a short burst at the start, 1 the
     * whole length), one `rng.nextDouble() * 2 - 1` per burst sample in index order and zeros elsewhere; then the mean
     * of those samples is taken out of each of them, so the excitation carries no DC into the loop (audit finding L3-06:
     * the loop passes DC at the feedback gain alone, so a burst's random mean outlived its tone); then [writePos]. The
     * draws are the same in count and order as without the mean's removal. The filter state is left as it is.
     */
    fun excite(lineDelay: Double, pickPos: Double, rng: Random) {
        val delayLen = lineDelay.coerceIn(2.0, (MAX_DELAY - 1.0)).toInt()
        val pp = pickPos.coerceIn(0.0, 1.0)
        val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
        val burstStart = ((delayLen - burstLen) * pp).toInt()
        var sum = 0.0

        for (j in 0 until delayLen) {
            val v = if (j >= burstStart && j < burstStart + burstLen) {
                (rng.nextDouble() * 2.0 - 1.0)
            } else {
                0.0
            }

            delayLine[j] = v
            sum += v
        }

        // A NaN line delay leaves delayLen at 0: nothing was written, and nothing is to be taken out.
        if (delayLen > 0) {
            val mean = sum / delayLen

            for (j in 0 until delayLen) {
                delayLine[j] = delayLine[j] - mean
            }
        }

        writePos = delayLen % MAX_DELAY
    }

    /**
     * Renders the string over `[from, to)`: the delay [baseDelay] divided by `phaseMod[i]` (when there is one) and by
     * the drift ramp (when [hasDrift]: it starts at [driftStart] and steps by [driftStep] per sample), less [loopDelay]
     * (the loop's delay besides the line at the note, [loopDelayOf]), clamped to the line; the read, the filters, the
     * write-back scaled by [feedback]. The loop delay is taken off after the pitch modulation divides the delay, so a
     * modulated note keeps the filters' delay (which hardly moves with the pitch) instead of scaling it. The output is `sample * gain`, written to
     * [buffer] or, when [accumulate], added to it. [lpAlpha], [hasStiffness] and [apCoeff] come from [lpAlphaOf],
     * [hasStiffnessOf] and [apCoeffOf].
     *
     * The shape of this function is measured (tidy-up step 11, review rounds 1 and 2, recorded in
     * KLANG-00286 (engine tidy up), step 11 (b)), keep it:
     * - Every double value passes through `* 1.0` once, before the loop (exact for every value but a NaN's payload).
     *   On V8, a double that comes from a call result or a field and seeds a loop-carried variable (the delay, the
     *   drift ramp) stays a tagged value unless it passes through an arithmetic operation first, and then every
     *   update allocates a heap number. Without the `* 1.0` the superpluck with drift went back to 424 to 633
     *   scavenges per run and 40 to 76 percent more time; the pluck allocated too. No test can pin this (the sound is
     *   bit-identical either way): this text is the guard. See the V8 rule in `audio/ref/performance.md`.
     * - `inline`: each node gets its own copy of the loop, as before the string was shared. As one shared method the
     *   loop ran 3 to 18 percent slower on V8 (9 to 18 on five of the six Karplus rows measured); on the JVM it makes
     *   no difference once the wrap is the one below.
     * - The wrap is by the line's length read at run time, not by the constant [MAX_DELAY]. With the constant, C2's
     *   modulo made the JVM 15 to 20 percent slower here (the write position is a loop-carried `% 2500`).
     * - The state lives in locals during the loop and is written back after it (9 to 16 percent faster on V8 than
     *   the fields; the measured exception to the "no snapshot into locals" rule). The write-back of the allpass
     *   state is pinned by `BlockFramingInvarianceSpec` (I2, the stiff rows) and `BuiltInVoiceMatrixSpec`.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun render(
        buffer: AudioBuffer,
        from: Int,
        to: Int,
        baseDelay: Double,
        phaseMod: DoubleArray?,
        hasDrift: Boolean,
        driftStart: Double,
        driftStep: Double,
        loopDelay: Double,
        lpAlpha: Double,
        hasStiffness: Boolean,
        apCoeff: Double,
        feedback: Double,
        gain: Double,
        accumulate: Boolean,
    ) {
        val delay = baseDelay * 1.0
        val filterDelay = loopDelay * 1.0
        var m = driftStart * 1.0
        val dm = driftStep * 1.0
        val alpha = lpAlpha * 1.0
        val ap = apCoeff * 1.0
        val feedbackGain = feedback * 1.0
        val g = gain * 1.0
        val line = delayLine
        val len = line.size
        // `* 1.0`: a seed from a field must pass an arithmetic op on V8, or the loop can box it (audio/ref/performance.md).
        var lp = lpState * 1.0
        var prevIn = apPrevIn * 1.0
        var prevOut = apPrevOut * 1.0
        var wp = writePos

        for (i in from until to) {
            var dl = delay

            if (phaseMod != null) {
                dl /= phaseMod[i]
            }

            if (hasDrift) {
                dl /= m
                m += dm
            }

            dl = (dl - filterDelay).coerceIn(2.0, (len - 1.0))

            // The cubic read at `wp - dl`: the frames `i0 - 1` to `i0 + 2` round the line. The newest of them is at least
            // one sample old (`dl` >= 2), except at a whole `dl` of 2, where the frame at `wp` is read with weight 0.
            val readPosF = wp - dl
            val readPosWrapped = if (readPosF < 0) readPosF + len else readPosF
            val whole = readPosWrapped.toInt()
            // A position a hair under 0 wraps to `len` itself in floating point: that is frame 0.
            val i0 = if (whole >= len) whole - len else whole
            val frac = readPosWrapped - whole
            val im1 = if (i0 == 0) len - 1 else i0 - 1
            var i1 = i0 + 1

            if (i1 >= len) {
                i1 -= len
            }

            var i2 = i1 + 1

            if (i2 >= len) {
                i2 -= len
            }

            val sample = lagrange4(t = frac, xm1 = line[im1], x0 = line[i0], x1 = line[i1], x2 = line[i2])

            // One-pole lowpass (brightness)
            lp = (lp + alpha * (sample - lp)).flushState()

            var filtered = lp

            // Allpass stiffness
            if (hasStiffness) {
                val allpass = ap * (filtered - prevOut) + prevIn

                prevIn = filtered.flushState()
                prevOut = allpass.flushState()
                filtered = allpass
            }

            // Write back with feedback
            line[wp] = (filtered * feedbackGain)

            val out = (sample * g)

            if (accumulate) {
                buffer[i] = buffer[i] + out
            } else {
                buffer[i] = out
            }

            wp = (wp + 1) % len
        }

        lpState = lp
        apPrevIn = prevIn
        apPrevOut = prevOut
        writePos = wp
    }

    companion object {
        /** The delay line's length: down to about 20 Hz at 48 kHz (2400 samples). */
        const val MAX_DELAY: Int = 2500

        /** The base delay in samples of a string at [freqHz], clamped to the line: one period of the note. */
        fun baseDelayOf(sampleRate: Double, freqHz: Double): Double = (sampleRate / freqHz).coerceIn(2.0, (MAX_DELAY - 1.0))

        /**
         * How much the nodes take off the line's read delay so the string sounds at the frequency whose period is
         * [period] samples (a base delay, [baseDelayOf]): the delay the loop adds besides the line, so that the loop as
         * a whole has its pole at the note (audit finding L3-01). The loop is the line `z^-M`, its cubic Lagrange read
         * `h0 z + h1 + h2 z^-1 + h3 z^-2`, the Lagrange weights of the delay `1 + d` over taps 0 to 3 (`M + d` the read
         * delay, [render]'s four frames; KLANG-00430, audit finding L3-07), the brightness one-pole
         * `alpha / (1 - (1 - alpha) z^-1)`, when [hasStiffness] the allpass `(a + z^-1) / (1 + a z^-1)` with `a` =
         * [apCoeff], and the gain [feedback].
         *
         * The phase delay at the note on the unit circle (the one-pole's lag `atan2((1 - alpha) sin w, 1 - (1 - alpha)
         * cos w) / w`, the allpass's) is most of it: the one-pole's falls with frequency (at `alpha` 0.01 it is 99
         * samples at DC and about 24 at A4), so the DC delay `(1 - alpha) / alpha` would be far off. But a damped loop's
         * pole lies inside the circle, at the radius `r` where the loop gain is 1 (`r^M = |feedback H(z)|`), and the
         * filters' phase there differs: on the unit circle a dark pluck stays 1.4 cents flat at A4 (brightness 0.1) and
         * 2 cents at C6 (brightness 0.2). So the phase is taken at `z = r e^(jw)`, the radius from the loop gain at the
         * current estimate, in [LOOP_DELAY_ITERATIONS] fixed-point steps from the unit circle (the first step is the
         * unit-circle phase delay). Modelled against the exact pole: within 0.1 cent from A1 to C7 at every brightness
         * and stiffness, also where the tone dies in a few periods.
         *
         * Per note and again when an input moves, never per sample (a few dozen transcendental calls). The read delay is
         * clamped to the line as [render] clamps it. A NaN (a NaN brightness, period or feedback) reads as 0: the read
         * delay then behaves as it did before this compensation existed, and a NaN knob does not become a NaN delay.
         */
        fun loopDelayOf(period: Double, lpAlpha: Double, hasStiffness: Boolean, apCoeff: Double, feedback: Double): Double {
            val w = 2.0 * PI / period
            val cosW = cos(w)
            val sinW = sin(w)
            val pole = 1.0 - lpAlpha
            val loopGainSq = feedback * feedback * lpAlpha * lpAlpha
            // z^-1 = rInv e^(-jw): rInv is 1 / r, the unit circle at the start.
            var rInv = 1.0
            var readDelay = period

            for (i in 0 until LOOP_DELAY_ITERATIONS) {
                readDelay = readDelay.coerceIn(2.0, (MAX_DELAY - 1.0))

                val whole = floor(readDelay)
                val frac = readDelay - whole
                val zr = rInv * cosW
                val zi = -rInv * sinW

                // The one-pole: 1 / (1 - pole z^-1), its gain alpha in loopGainSq.
                val lpRe = 1.0 - pole * zr
                val lpIm = -pole * zi
                var phase = -atan2(lpIm, lpRe)
                var gainSq = loopGainSq / (lpRe * lpRe + lpIm * lpIm)

                if (hasStiffness) {
                    val numRe = apCoeff + zr
                    val denRe = 1.0 + apCoeff * zr
                    val denIm = apCoeff * zi

                    phase += atan2(zi, numRe) - atan2(denIm, denRe)
                    gainSq *= (numRe * numRe + zi * zi) / (denRe * denRe + denIm * denIm)
                }

                // The cubic Lagrange read: h0 z + h1 + h2 z^-1 + h3 z^-2, the weights of the delay 1 + frac over taps 0 to 3.
                // z = (1 / rInv) e^(jw), z^-2 = (z^-1)^2.
                val fm1 = frac - 1.0
                val fm2 = frac - 2.0
                val fp1 = frac + 1.0
                val h0 = -frac * fm1 * fm2 / 6.0
                val h1 = fp1 * fm1 * fm2 / 2.0
                val h2 = -fp1 * frac * fm2 / 2.0
                val h3 = fp1 * frac * fm1 / 6.0
                val zpr = cosW / rInv
                val zpi = sinW / rInv
                val z2r = zr * zr - zi * zi
                val z2i = 2.0 * zr * zi
                val inRe = h0 * zpr + h1 + h2 * zr + h3 * z2r
                val inIm = h0 * zpi + h2 * zi + h3 * z2i

                phase += atan2(inIm, inRe)
                gainSq *= inRe * inRe + inIm * inIm

                // The pole's angle is the note's when the whole loop's phase delay, the line's `whole` samples plus the
                // rest, is one period; its radius is where the loop gain is 1.
                readDelay = period - (-phase / w - frac)
                rInv = gainSq.pow(-0.5 / whole)
            }

            return (period - readDelay.coerceIn(2.0, (MAX_DELAY - 1.0))).nanGuard()
        }

        /** The fixed-point steps of [loopDelayOf]: modelled, 4 reach 0.4 cent at the darkest top notes, 5 reach 0.08. */
        const val LOOP_DELAY_ITERATIONS: Int = 5

        // The filter coefficients, from the block's brightness and stiffness. Three laws, one place; three functions
        // because a single one could hand back three values only through an allocation or a holder.

        /** The brightness one-pole's coefficient. */
        fun lpAlphaOf(brightness: Double): Double = brightness.coerceIn(0.01, 1.0)

        /** Whether the stiffness allpass runs: any stiffness above 0 (a NaN does not). */
        fun hasStiffnessOf(stiffness: Double): Boolean = stiffness > 0.0

        /** The stiffness allpass's coefficient. */
        fun apCoeffOf(stiffness: Double): Double = stiffness.coerceIn(0.0, 0.99) * 0.5
    }
}
