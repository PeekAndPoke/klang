package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan


// ═══════════════════════════════════════════════════════════════════════════════
// Distortion
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Distortion / waveshaping combinator. Processes per-sample.
 *
 * Drives the signal into a nonlinear transfer function, generating new harmonics.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 * Includes a DC blocker for asymmetric shapes (diode, rectify).
 *
 * @param amount Drive intensity. 0.0 = bypass, 0.3 = warm saturation, 1.0 = heavy distortion,
 *   2.0+ = extreme. Internally: gain = 10^(amount × 1.2). Default: 0.0 (bypass).
 * @param shape Waveshaper function. Default: "soft" (tanh).
 *   Options: "soft" (tanh), "hard" (clip), "gentle" (soft clip, 2× gain), "cubic",
 *   "diode" (asymmetric), "fold" (wave folding), "chebyshev", "rectify", "exp".
 */
fun Ignitor.distort(amount: Ignitor, shape: String = "soft", oversampleStages: Int = 0): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val waveshaper = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock
    val oversampler = if (oversampleStages > 0) Oversampler(oversampleStages) else null

    // DC blocker state
    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)
        if (amt <= 0.0) return@Ignitor

        val drive = 10.0.pow(amt * 1.2)
        val os = oversampler

        if (os != null) {
            os.process(buffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                (waveshaper(sample.toDouble() * drive) * outputGain).toFloat()
            }
            // DC blocker at original rate after decimation
            if (needsDcBlock) {
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val y = buffer[i].toDouble()
                    val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
                    dcBlockX1 = y
                    dcBlockY1 = flushDenormal(dcOut)
                    buffer[i] = dcOut.toFloat()
                }
            }
        } else {
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
}

/**
 * Distortion / waveshaping combinator (convenience overload with fixed amount).
 *
 * @param amount Drive intensity. 0.0 = bypass, 0.3 = warm, 1.0 = heavy, 2.0+ = extreme. Default: 0.0.
 * @param shape Waveshaper function. Default: "soft". See [distort] for all options.
 * @param oversampleStages Number of 2x oversampling stages. 0 = off, 1 = 2x, 2 = 4x, etc.
 */
fun Ignitor.distort(amount: Double, shape: String = "soft", oversampleStages: Int = 0): Ignitor {
    if (amount <= 0.0) return this
    return distort(ParamIgnitor("amount", amount), shape, oversampleStages)
}

/**
 * Pre-amplification stage. Boosts signal level without waveshaping.
 *
 * Use before a clip() or distort() to control how hard the signal hits the shaper.
 * Amount is read once per block (control rate). Bypasses when amount <= 0.
 *
 * @param amount Gain boost intensity. 0.0 = bypass, 0.5 = moderate boost, 1.0 = loud,
 *   2.0+ = extreme. Internally: gain = 10^(amount × 1.2). Default: 0.0 (bypass).
 * @param type Drive type. Default: "linear". Future: "tube", "fet", "tape".
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

/**
 * Pre-amplification stage (convenience overload with fixed amount).
 *
 * @param amount Gain boost intensity. 0.0 = bypass, 1.0 = loud. Default: 0.0.
 * @param type Drive type. Default: "linear".
 */
fun Ignitor.drive(amount: Double, type: String = "linear"): Ignitor {
    if (amount <= 0.0) return this
    return drive(ParamIgnitor("amount", amount), type)
}

/**
 * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
 *
 * Unlike [distort], this does not boost the signal before shaping — it only clips
 * whatever amplitude is already there. Use [drive] before clip() for a two-stage chain.
 * Includes DC blocker for asymmetric shapes (diode, rectify).
 *
 * @param shape Waveshaper function. Default: "soft" (tanh).
 *   Options: "soft" (tanh), "hard" (clip), "gentle" (soft clip, 2× gain), "cubic",
 *   "diode" (asymmetric), "fold" (wave folding), "chebyshev", "rectify", "exp".
 */
fun Ignitor.clip(shape: String = "soft", oversampleStages: Int = 0): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val clipFn = resolved.fn
    val outputGain = resolved.outputGain
    val needsDcBlock = resolved.needsDcBlock
    val oversampler = if (oversampleStages > 0) Oversampler(oversampleStages) else null

    val dcBlockCoeff = 0.995
    var dcBlockX1 = 0.0
    var dcBlockY1 = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val os = oversampler

        if (os != null) {
            os.process(buffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                (clipFn(sample.toDouble()) * outputGain).toFloat()
            }
            if (needsDcBlock) {
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val y = buffer[i].toDouble()
                    val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
                    dcBlockX1 = y
                    dcBlockY1 = flushDenormal(dcOut)
                    buffer[i] = dcOut.toFloat()
                }
            }
        } else {
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
}

// ResolvedShape and resolveDistortionShape() moved to audio_be/DistortionShape.kt

// ═══════════════════════════════════════════════════════════════════════════════
// BitCrush
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bit-depth reduction (bitcrush) for lo-fi digital sound. Processes per-sample.
 *
 * Symmetric midtread quantizer: `round(x * halfLevels) / halfLevels`, output
 * clamped to `[-1, 1]`. No DC bias, no amplitude inflation. The clamp catches
 * the non-integer `halfLevels` case where a unit input would otherwise map to
 * a grid point outside the input range (e.g. `amount = 1.5` → `hl ≈ 1.414` →
 * raw output `2/1.414 ≈ 1.414`, clamped to `1.0`).
 *
 * Amount is read once per block (control rate). **Bypasses when amount < 1.0** —
 * fewer than 2 levels means the grid step exceeds the input range entirely.
 *
 * @param amount Bit depth. Below 1.0 = bypass. 1.0 = 2 levels (extreme lo-fi),
 *   4.0 = 16 levels, 8.0 = 256 levels, 16.0 = 65536 levels (subtle).
 *   Internally: `levels = 2^amount`. Typical range: 2.0–8.0.
 */
fun Ignitor.crush(amount: Ignitor): Ignitor {
    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val amt = Ignitors.readParam(amount, freqHz, ctx)

        // Continuous levels — no toInt() so modulating `amt` sweeps the grid smoothly.
        val levels = 2.0.pow(amt)
        // Bypass: need at least 2 levels (amt >= 1) for the quantizer to be
        // remotely bounded by the input range. Sub-1.0 amounts would inflate by
        // factor `1/halfLevels`, which can exceed 2×.
        if (levels < 2.0) return@Ignitor

        val halfLevels = levels / 2.0

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            // Midtread symmetric quantizer (round, not floor) — no DC bias.
            // Clamp output to [-1, 1] to catch non-integer halfLevels inflation.
            val q = round(buffer[i].toDouble() * halfLevels) / halfLevels
            buffer[i] = q.coerceIn(-1.0, 1.0).toFloat()
        }
    }
}

/**
 * Bit-depth reduction (convenience overload with fixed amount).
 *
 * @param amount Bit depth. Below 1.0 = bypass. 4.0 = 16 levels (lo-fi),
 *   8.0 = 256 levels. Default: 0.0.
 */
fun Ignitor.crush(amount: Double): Ignitor {
    if (amount < 1.0) return this
    return crush(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Coarse (Sample Rate Reducer)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sample-rate reducer (coarse). Holds a sample value for multiple frames. Processes per-sample.
 *
 * Creates aliased, metallic artifacts by reducing the effective sample rate.
 * Amount is read once per block (control rate). Bypasses when amount <= 1.0.
 *
 * @param amount Sample-hold factor. Values <= 1.0 are inactive (bypass).
 *   2.0 = every 2nd sample held, 4.0 = every 4th (strong aliasing), 10.0+ = extreme lo-fi.
 *   Typical range: 2.0–8.0. Default: 0.0 (inactive).
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

/**
 * Sample-rate reducer (convenience overload with fixed amount).
 *
 * @param amount Sample-hold factor. Values <= 1.0 = inactive. 4.0 = strong aliasing. Default: 0.0.
 */
fun Ignitor.coarse(amount: Double): Ignitor {
    if (amount <= 1.0) return this
    return coarse(ParamIgnitor("amount", amount))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Phaser
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-stage all-pass cascade phaser with sine LFO modulation. Processes per-sample.
 *
 * Sweeps a series of notch filters through the spectrum, creating the classic
 * "whooshing" or "jet" effect. All params read once per block (control rate).
 * Bypasses when depth <= 0.
 *
 * @param rate LFO speed in Hz. 0.0 = static, 0.5 = slow sweep, 2.0 = moderate,
 *   5.0+ = fast. Typical range: 0.1–5.0. Default: no default (required).
 * @param depth Wet/dry mix amount. 0.0 = bypass, 0.5 = subtle, 1.0 = full effect.
 *   Typical range: 0.3–1.0. Default: no default (required).
 * @param center Center frequency of the notch sweep in Hz. Default: 1000.0.
 *   Clamped to [100, 18000]. Typical range: 500–4000.
 * @param sweep Modulation width in Hz — how far the notch sweeps from center.
 *   Default: 1000.0. Clamped to [100, 18000]. Typical range: 500–3000.
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

/**
 * 4-stage all-pass cascade phaser (convenience overload with fixed values).
 *
 * @param rate LFO speed in Hz. Typical range: 0.1–5.0.
 * @param depth Wet/dry mix. 0.0 = bypass, 1.0 = full effect.
 * @param center Center frequency in Hz. Default: 1000.0. Clamped to [100, 18000].
 * @param sweep Modulation width in Hz. Default: 1000.0. Clamped to [100, 18000].
 */
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
 *
 * Modulates the volume up and down rhythmically, creating a pulsing effect.
 * Rate and depth are read once per block (control rate). Bypasses when depth <= 0.
 *
 * @param rate LFO speed in Hz. 0.0 = static, 2.0 = gentle pulse, 5.0 = moderate,
 *   10.0+ = fast chopping. Typical range: 1.0–8.0. Default: no default (required).
 * @param depth Modulation intensity. 0.0 = bypass (no tremolo), 0.5 = subtle,
 *   1.0 = full depth (volume drops to zero). Typical range: 0.2–0.8.
 *   Default: no default (required).
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

/**
 * Amplitude modulation / tremolo (convenience overload with fixed values).
 *
 * @param rate LFO speed in Hz. Typical range: 1.0–8.0.
 * @param depth Modulation intensity. 0.0 = bypass, 1.0 = full depth.
 */
fun Ignitor.tremolo(
    rate: Double,
    depth: Double,
): Ignitor {
    if (depth <= 0.0) return this
    return tremolo(ParamIgnitor("rate", rate), ParamIgnitor("depth", depth))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shimmer — granular pitch-shift cloud with feedback (Aetherizer-style)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Granular shimmer effect — short overlapping grains read back from a ring buffer at
 * pitched-up rates (+7 and +12 semitones, alternating), with a feedback loop through a
 * tone lowpass. Produces the classic rising-octaves shimmer cloud.
 *
 * All params read once per block (control rate). Bypasses when mix <= 0 AND feedback <= 0.
 *
 * @param mix Wet amount added to dry. 0.0 = dry only, 1.0 = full wet added on top. Clamped to [0, 1].
 * @param feedback Wet → grain-buffer feedback. 0.0 = single pass, 0.9 = long cascading tails.
 *   Hard-clamped to 0.95 for stability.
 * @param tone One-pole LPF cutoff (Hz) in the feedback path. Lower = darker. Clamped to [200, 16000].
 */
fun Ignitor.shimmer(
    mix: Ignitor,
    feedback: Ignitor,
    tone: Ignitor,
): Ignitor {
    // Ring buffer — 1 second at 48kHz is enough headroom for 150ms grains at +12 semitones (rate 2.0).
    // Sized for the highest sample rate we expect (96kHz safe) so no lazy allocation in hot path.
    val ringSize = 96_000
    val ring = FloatArray(ringSize)
    var writePos = 0

    // Hann-windowed grains. Pool of fixed slots to avoid allocation.
    val maxGrains = 8
    val grainActive = BooleanArray(maxGrains)
    val grainReadPos = DoubleArray(maxGrains)
    val grainRate = DoubleArray(maxGrains)
    val grainElapsed = IntArray(maxGrains)
    val grainTotal = IntArray(maxGrains)

    // Fixed intervals for the MVP shimmer preset: perfect fifth and octave.
    val intervalRates = doubleArrayOf(
        1.0, // Same tone
        2.0.pow(7.0 / 12.0),   // +7 semitones  ≈ 1.498
        2.0.pow(12.0 / 12.0),  // +12 semitones = 2.000
    )
    var nextIntervalIdx = 0

    // Grain scheduler — density fixed at ~12 grains/sec (overlap factor ~1.8 at 150ms grains).
    val grainsPerSecond = 12.0
    val grainSizeSec = 0.150
    var samplesUntilNextGrain = 0

    // Feedback tap + one-pole LPF state for the tone filter in the feedback path.
    var feedbackTap = 0.0
    var lpfState = 0.0

    return Ignitor { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val mixVal = Ignitors.readParam(mix, freqHz, ctx).coerceIn(0.0, 1.0)
        val fbVal = Ignitors.readParam(feedback, freqHz, ctx).coerceIn(0.0, 0.95)
        val toneVal = Ignitors.readParam(tone, freqHz, ctx).coerceIn(200.0, 16000.0)

        if (mixVal <= 0.0 && fbVal <= 0.0) return@Ignitor

        val sampleRate = ctx.sampleRate
        val grainPeriodSamples = (sampleRate / grainsPerSecond).toInt().coerceAtLeast(1)
        val grainTotalSamples = (sampleRate * grainSizeSec).toInt().coerceAtLeast(1)
        val invGrainTotal = 1.0 / grainTotalSamples

        // One-pole LPF coefficient. y[n] = (1-a) * x + a * y[n-1] where a = exp(-2π * fc / fs).
        val lpfA = exp(-TWO_PI * toneVal / sampleRate)
        val lpfOneMinusA = 1.0 - lpfA

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val dry = buffer[i].toDouble()

            // ── Write input + feedback tap into the ring buffer ──
            var write = dry + feedbackTap * fbVal
            if (write > 2.0) write = 2.0 else if (write < -2.0) write = -2.0
            ring[writePos] = write.toFloat()
            writePos++
            if (writePos >= ringSize) writePos = 0

            // ── Maybe spawn a new grain ──
            if (samplesUntilNextGrain <= 0) {
                samplesUntilNextGrain = grainPeriodSamples
                val rate = intervalRates[nextIntervalIdx]
                nextIntervalIdx = (nextIntervalIdx + 1) % intervalRates.size

                // Find a free slot.
                var slot = -1
                for (g in 0 until maxGrains) {
                    if (!grainActive[g]) {
                        slot = g
                        break
                    }
                }
                if (slot >= 0) {
                    // Start reading so that the grain finishes approximately at writePos.
                    // Read distance = rate * grainTotalSamples; start that far behind writePos.
                    val lookback = rate * grainTotalSamples
                    var start = writePos - lookback
                    while (start < 0.0) start += ringSize
                    grainReadPos[slot] = start
                    grainRate[slot] = rate
                    grainElapsed[slot] = 0
                    grainTotal[slot] = grainTotalSamples
                    grainActive[slot] = true
                }
            }
            samplesUntilNextGrain--

            // ── Read & mix all active grains ──
            var wet = 0.0
            for (g in 0 until maxGrains) {
                if (!grainActive[g]) continue

                val pos = grainReadPos[g]
                val idx1 = pos.toInt()
                val frac = pos - idx1
                val idx2 = if (idx1 + 1 >= ringSize) 0 else idx1 + 1
                val s1 = ring[idx1].toDouble()
                val s2 = ring[idx2].toDouble()
                val sample = s1 + frac * (s2 - s1)

                // Hann window over the grain lifetime.
                val phase = grainElapsed[g] * invGrainTotal
                val win = 0.5 - 0.5 * cos(TWO_PI * phase)

                wet += sample * win

                // Advance.
                var nextPos = pos + grainRate[g]
                while (nextPos >= ringSize) nextPos -= ringSize
                grainReadPos[g] = nextPos
                grainElapsed[g]++
                if (grainElapsed[g] >= grainTotal[g]) {
                    grainActive[g] = false
                }
            }

            // ── Feedback tap through tone LPF ──
            lpfState = flushDenormal(lpfOneMinusA * wet + lpfA * lpfState)
            feedbackTap = lpfState

            // ── Output: dry + wet * mix ──
            buffer[i] = (dry + wet * mixVal).toFloat()
        }
    }
}

/**
 * Granular shimmer (convenience overload with fixed values).
 *
 * @param mix Wet amount. 0.0 = dry only, 1.0 = full wet. Default: 0.5.
 * @param feedback Cascade feedback. 0.0 = single pass, 0.9 = long tails. Default: 0.5.
 * @param tone Feedback-path LPF cutoff in Hz. Default: 4000.0.
 */
fun Ignitor.shimmer(
    mix: Double = 0.5,
    feedback: Double = 0.5,
    tone: Double = 4000.0,
): Ignitor {
    if (mix <= 0.0 && feedback <= 0.0) return this
    return shimmer(
        ParamIgnitor("mix", mix),
        ParamIgnitor("feedback", feedback),
        ParamIgnitor("tone", tone),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DC Blocker
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One-pole highpass DC blocker.
 *
 * Removes DC offset accumulation from waveshaping, feedback, and asymmetric clipping.
 * Essential after distortion shapes that produce asymmetric output (diode, rectify).
 *
 * @param coefficient Filter coefficient controlling the cutoff frequency.
 *   0.995 = ~20 Hz cutoff (good for feedback paths). 0.999 = ~5 Hz cutoff (master output).
 *   Higher = lower cutoff, less signal removed. Default: 0.995.
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
