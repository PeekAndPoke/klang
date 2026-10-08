/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.flushState
import kotlin.random.Random

/**
 * One Karplus-Strong string: a delay line excited by a noise burst, read with linear interpolation, its feedback
 * filtered by a brightness one-pole and an optional stiffness allpass, then scaled by the decay.
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
     * Plucks the string: a noise burst over the first `baseDelay.toInt()` samples of the delay line, placed by
     * [pickPos] (0 a short burst at the start, 1 the whole length), one `rng.nextDouble() * 2 - 1` per burst sample in
     * index order and zeros elsewhere; then [writePos]. The filter state is left as it is.
     */
    fun excite(baseDelay: Double, pickPos: Double, rng: Random) {
        val delayLen = baseDelay.toInt()
        val pp = pickPos.coerceIn(0.0, 1.0)
        val burstLen = maxOf(1, (delayLen * (0.1 + 0.9 * pp)).toInt())
        val burstStart = ((delayLen - burstLen) * pp).toInt()

        for (j in 0 until delayLen) {
            delayLine[j] = if (j >= burstStart && j < burstStart + burstLen) {
                (rng.nextDouble() * 2.0 - 1.0)
            } else {
                0.0
            }
        }

        writePos = delayLen % MAX_DELAY
    }

    /**
     * Renders the string over `[from, to)`: the delay [baseDelay] divided by `phaseMod[i]` (when there is one) and by
     * the drift ramp (when [hasDrift]: it starts at [driftStart] and steps by [driftStep] per sample), clamped to the
     * line; the read, the filters, the write-back scaled by [decay]. The output is `sample * gain`, written to
     * [buffer] or, when [accumulate], added to it. [lpAlpha], [hasStiffness] and [apCoeff] come from [lpAlphaOf],
     * [hasStiffnessOf] and [apCoeffOf].
     *
     * The shape of this function is measured (tidy-up step 11, review rounds 1 and 2, recorded in
     * `docs/tasks/engine-tidy-up.md`, step 11 (b)), keep it:
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
        lpAlpha: Double,
        hasStiffness: Boolean,
        apCoeff: Double,
        decay: Double,
        gain: Double,
        accumulate: Boolean,
    ) {
        val delay = baseDelay * 1.0
        var m = driftStart * 1.0
        val dm = driftStep * 1.0
        val alpha = lpAlpha * 1.0
        val ap = apCoeff * 1.0
        val decayGain = decay * 1.0
        val g = gain * 1.0
        val line = delayLine
        val len = line.size
        var lp = lpState
        var prevIn = apPrevIn
        var prevOut = apPrevOut
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

            dl = dl.coerceIn(2.0, (len - 1.0))

            // Read with linear interpolation
            val readPosF = wp - dl
            val readPosWrapped = if (readPosF < 0) readPosF + len else readPosF
            val readIdx = readPosWrapped.toInt() % len
            val frac = readPosWrapped - readPosWrapped.toInt()
            val nextIdx = (readIdx + 1) % len
            val sample = line[readIdx] + (line[nextIdx] - line[readIdx]) * frac

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

            // Write back with decay
            line[wp] = (filtered * decayGain)

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

        /** The base delay in samples of a string at [freqHz], clamped to the line. */
        fun baseDelayOf(sampleRate: Double, freqHz: Double): Double = (sampleRate / freqHz).coerceIn(2.0, (MAX_DELAY - 1.0))

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
