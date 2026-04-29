package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.DEFAULT_DC_BLOCK_COEFF
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
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
 *
 * **DC blocker is always applied** when the effect is engaged — not just for
 * asymmetric shapes (`diode`, `rectify`) that need it for correctness, but also
 * for symmetric shapes at extreme drive where any input asymmetry causes the
 * output to rail-lock toward `±1` and produce a DC bias that can damage speakers.
 *
 * **Output is bounded to ±1 by a C¹-piecewise soft cap** ([ClippingFuncs.softCap]).
 * Below the linear-region threshold the cap is identity (clean signals
 * untouched); above, the rail-edge transients from the DC blocker's 2× HF gain
 * are smoothly compressed toward ±1 with continuous value + slope at the
 * threshold (no cliff click). The cap is per-stage so chained ignitor
 * combinators (downstream filters, mix points) see a well-behaved bounded
 * input — without it, a heavy-distort branch can dominate a mix.
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
    val oversampler = if (oversampleStages > 0) Oversampler(oversampleStages) else null

    // DC blocker — always applied to guard against rail-lock at extreme drive.
    // Runs at base rate after oversampler decimation (or directly when no oversampler).
    // The block-based class keeps state in registers across the inner loop.
    // The downstream `softCap` bounds the raw-pole 2× edge transient to ±1.
    val dcBlocker = LowPassHighPassFilters.DcBlocker()

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (amt <= 0.0) {
                // Bypass: copy upstream dry through to output.
                for (i in ctx.offset until end) {
                    output[i] = work[i]
                }
                return@use
            }

            val drive = 10.0.pow(amt * 1.2)
            val os = oversampler

            if (os != null) {
                // Pass 1: oversampled waveshape into work (in-place).
                os.process(work, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                    (waveshaper(sample * drive) * outputGain)
                }
            } else {
                // Pass 1 (direct): drive + waveshape into work (in-place).
                for (i in ctx.offset until end) {
                    work[i] = waveshaper(work[i] * drive) * outputGain
                }
            }

            // Pass 2: DC-block in-place.
            dcBlocker.process(work, ctx.offset, ctx.length)

            // Pass 3: soft-cap into output.
            for (i in ctx.offset until end) {
                output[i] = ClippingFuncs.softCap(work[i])
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
    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (amt <= 0.0) {
                for (i in ctx.offset until end) {
                    output[i] = work[i]
                }
                return@use
            }

            val driveGain = when (type.lowercase()) {
                "linear" -> 10.0.pow(amt * 1.2)
                else -> 10.0.pow(amt * 1.2) // default to linear, future: tube, fet, tape
            }

            for (i in ctx.offset until end) {
                output[i] = (work[i] * driveGain)
            }
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
 *
 * **DC blocker is always applied** to guard against rail-lock when the input is
 * already heavily saturated (e.g. after `drive`). See [distort] for the rationale.
 * Output is bounded to ±1 by a C¹-piecewise soft cap ([ClippingFuncs.softCap])
 * — identity in the linear region, smooth saturation above. See [distort].
 *
 * @param shape Waveshaper function. Default: "soft" (tanh).
 *   Options: "soft" (tanh), "hard" (clip), "gentle" (soft clip, 2× gain), "cubic",
 *   "diode" (asymmetric), "fold" (wave folding), "chebyshev", "rectify", "exp".
 */
fun Ignitor.clip(shape: String = "soft", oversampleStages: Int = 0): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val clipFn = resolved.fn
    val outputGain = resolved.outputGain
    val oversampler = if (oversampleStages > 0) Oversampler(oversampleStages) else null

    // DC blocker pre-softCap. See `Ignitor.distort` for the rationale.
    val dcBlocker = LowPassHighPassFilters.DcBlocker()

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            val end = ctx.offset + ctx.length
            val os = oversampler

            if (os != null) {
                // Pass 1: oversampled clip into work (in-place).
                os.process(work, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                    (clipFn(sample) * outputGain)
                }
            } else {
                // Pass 1 (direct): clip into work (in-place).
                for (i in ctx.offset until end) {
                    work[i] = clipFn(work[i]) * outputGain
                }
            }

            // Pass 2: DC-block in-place.
            dcBlocker.process(work, ctx.offset, ctx.length)

            // Pass 3: soft-cap into output.
            for (i in ctx.offset until end) {
                output[i] = ClippingFuncs.softCap(work[i])
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
    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            // Continuous levels — no toInt() so modulating `amt` sweeps the grid smoothly.
            val levels = 2.0.pow(amt)
            // Bypass: need at least 2 levels (amt >= 1) for the quantizer to be
            // remotely bounded by the input range. Sub-1.0 amounts would inflate by
            // factor `1/halfLevels`, which can exceed 2×.
            if (levels < 2.0) {
                for (i in ctx.offset until end) {
                    output[i] = work[i]
                }
                return@use
            }

            val halfLevels = levels / 2.0

            for (i in ctx.offset until end) {
                // Midtread symmetric quantizer (round, not floor) — no DC bias.
                // Clamp output to [-1, 1] to catch non-integer halfLevels inflation.
                val q = round(work[i] * halfLevels) / halfLevels
                output[i] = q.coerceIn(-1.0, 1.0)
            }
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
    var lastValue = 0.0
    var counter = 0.0

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            val amt = Ignitors.readParam(amount, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (amt <= 1.0) {
                for (i in ctx.offset until end) {
                    output[i] = work[i]
                }
                return@use
            }

            for (i in ctx.offset until end) {
                val idx = i - ctx.offset

                if (counter >= 1.0 || (idx == 0 && counter == 0.0)) {
                    lastValue = work[i]
                    counter -= 1.0
                }

                output[i] = lastValue
                counter += (1.0 / amt)
            }
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
    blend: Ignitor,
    center: Ignitor = ParamIgnitor("center", 1000.0),
    sweep: Ignitor = ParamIgnitor("sweep", 1000.0),
): Ignitor {
    val stages = 4
    var lfoPhase = 0.0
    val filterState = DoubleArray(stages)
    var lastOutput = 0.0
    val feedback = 0.5

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val rateVal = Ignitors.readParam(rate, freqHz, ctx)
            val blendVal = Ignitors.readParam(blend, freqHz, ctx).coerceIn(0.0, 1.0)
            val centerVal = Ignitors.readParam(center, freqHz, ctx)
            val sweepVal = Ignitors.readParam(sweep, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (blendVal <= 0.0) {
                for (i in ctx.offset until end) {
                    output[i] = input[i]
                }
                return@use
            }

            val inverseSampleRate = 1.0 / ctx.sampleRate
            val lfoIncrement = rateVal * TWO_PI * inverseSampleRate

            for (i in ctx.offset until end) {
                lfoPhase += lfoIncrement
                if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI
                val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

                var modFreq = centerVal + (lfoValue - 0.5) * sweepVal
                modFreq = modFreq.coerceIn(100.0, 18000.0)

                val tanValue = tan(PI * modFreq * inverseSampleRate)
                val alpha = (tanValue - 1.0) / (tanValue + 1.0)

                val dry = input[i]
                var signal = dry + lastOutput * feedback

                for (s in 0 until stages) {
                    val stageOut = alpha * signal + filterState[s]
                    filterState[s] = flushDenormal(signal - alpha * stageOut)
                    signal = stageOut
                }

                lastOutput = flushDenormal(signal)

                // Crossfade: dry · (1 − blend) + wet · blend
                output[i] = (dry * (1.0 - blendVal) + signal * blendVal)
            }
        }
    }
}

/**
 * 4-stage all-pass cascade phaser (convenience overload with fixed values).
 *
 * @param rate LFO speed in Hz. Typical range: 0.1–5.0.
 * @param blend Crossfade: 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only). Default: 0.5.
 * @param center Center frequency in Hz. Default: 1000.0. Clamped to [100, 18000].
 * @param sweep Modulation width in Hz. Default: 1000.0. Clamped to [100, 18000].
 */
fun Ignitor.phaser(
    rate: Double,
    blend: Double = 0.5,
    center: Double = 1000.0,
    sweep: Double = 1000.0,
): Ignitor {
    if (blend <= 0.0) return this
    return phaser(
        ParamIgnitor("rate", rate),
        ParamIgnitor("blend", blend),
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

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val rateVal = Ignitors.readParam(rate, freqHz, ctx)
            val depthVal = Ignitors.readParam(depth, freqHz, ctx)
            val end = ctx.offset + ctx.length

            if (depthVal <= 0.0) {
                for (i in ctx.offset until end) {
                    output[i] = input[i]
                }
                return@use
            }

            val phaseInc = (TWO_PI * rateVal) / ctx.sampleRate

            for (i in ctx.offset until end) {
                phase += phaseInc
                if (phase > TWO_PI) phase -= TWO_PI

                val lfoNorm = (sin(phase) + 1.0) * 0.5
                val gain = 1.0 - (depthVal * (1.0 - lfoNorm))
                output[i] = (input[i] * gain)
            }
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
 * pitched rates, with a feedback loop through a tone lowpass.
 *
 * @param blend Crossfade between dry and wet. 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only).
 *   Formula: `out = dry · (1 − blend) + wet · blend`.
 * @param feedback Wet → grain-buffer feedback. 0.0 = single pass, 0.9 = long cascading tails.
 *   Hard-clamped to 0.95 for stability.
 * @param tone One-pole LPF cutoff (Hz) in the feedback path. Lower = darker. Clamped to [200, 16000].
 * @param pitches Semitone transpositions for grains. Each grain is assigned a pitch from this
 *   list in round-robin order. Default: `[0, 7, 12]` (root + fifth + octave).
 */
fun Ignitor.shimmer(
    blend: Ignitor,
    feedback: Ignitor,
    tone: Ignitor,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
): Ignitor {
    val ringSize = 96_000
    val ring = AudioBuffer(ringSize)
    var writePos = 0

    val maxGrains = 8
    val grainActive = BooleanArray(maxGrains)
    val grainReadPos = DoubleArray(maxGrains)
    val grainRate = DoubleArray(maxGrains)
    val grainElapsed = IntArray(maxGrains)
    val grainTotal = IntArray(maxGrains)

    val intervalRates = DoubleArray(pitches.size) { 2.0.pow(pitches[it] / 12.0) }
    var nextIntervalIdx = 0

    val grainsPerSecond = 12.0
    val grainSizeSec = 0.150
    var samplesUntilNextGrain = 0

    var feedbackTap = 0.0
    var lpfState = 0.0

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val blendVal = Ignitors.readParam(blend, freqHz, ctx).coerceIn(0.0, 1.0)
            val fbVal = Ignitors.readParam(feedback, freqHz, ctx).coerceIn(0.0, 0.95)
            val toneVal = Ignitors.readParam(tone, freqHz, ctx).coerceIn(200.0, 16000.0)
            val end = ctx.offset + ctx.length

            if (blendVal <= 0.0 && fbVal <= 0.0) {
                for (i in ctx.offset until end) {
                    output[i] = input[i]
                }
                return@use
            }

            val sampleRate = ctx.sampleRate
            val grainPeriodSamples = (sampleRate / grainsPerSecond).toInt().coerceAtLeast(1)
            val grainTotalSamples = (sampleRate * grainSizeSec).toInt().coerceAtLeast(1)
            val invGrainTotal = 1.0 / grainTotalSamples

            val lpfA = exp(-TWO_PI * toneVal / sampleRate)
            val lpfOneMinusA = 1.0 - lpfA

            for (i in ctx.offset until end) {
                val dry = input[i]

                var write = dry + feedbackTap * fbVal
                if (write > 2.0) write = 2.0 else if (write < -2.0) write = -2.0
                ring[writePos] = write
                writePos++
                if (writePos >= ringSize) writePos = 0

                if (samplesUntilNextGrain <= 0) {
                    samplesUntilNextGrain = grainPeriodSamples
                    val rate = intervalRates[nextIntervalIdx]
                    nextIntervalIdx = (nextIntervalIdx + 1) % intervalRates.size

                    var slot = -1
                    for (g in 0 until maxGrains) {
                        if (!grainActive[g]) {
                            slot = g; break
                        }
                    }
                    if (slot >= 0) {
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

                var wet = 0.0
                for (g in 0 until maxGrains) {
                    if (!grainActive[g]) continue

                    val pos = grainReadPos[g]
                    val idx1 = pos.toInt()
                    val frac = pos - idx1
                    val idx2 = if (idx1 + 1 >= ringSize) 0 else idx1 + 1
                    val sample = ring[idx1] + frac * (ring[idx2] - ring[idx1])

                    val phase = grainElapsed[g] * invGrainTotal
                    val win = 0.5 - 0.5 * cos(TWO_PI * phase)
                    wet += sample * win

                    var nextPos = pos + grainRate[g]
                    while (nextPos >= ringSize) nextPos -= ringSize
                    grainReadPos[g] = nextPos
                    grainElapsed[g]++
                    if (grainElapsed[g] >= grainTotal[g]) grainActive[g] = false
                }

                lpfState = flushDenormal(lpfOneMinusA * wet + lpfA * lpfState)
                feedbackTap = lpfState

                // Crossfade: dry · (1 − blend) + wet · blend
                output[i] = (dry * (1.0 - blendVal) + wet * blendVal)
            }
        }
    }
}

/**
 * Granular shimmer (convenience overload with fixed values).
 *
 * @param blend Crossfade: 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only). Default: 0.5.
 * @param feedback Cascade feedback. 0.0 = single pass, 0.9 = long tails. Default: 0.5.
 * @param tone Feedback-path LPF cutoff in Hz. Default: 4000.0.
 * @param pitches Semitone transpositions for grains. Default: `[0, 7, 12]`.
 */
fun Ignitor.shimmer(
    blend: Double = 0.5,
    feedback: Double = 0.5,
    tone: Double = 4000.0,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
): Ignitor {
    if (blend <= 0.0 && feedback <= 0.0) return this
    return shimmer(
        ParamIgnitor("blend", blend),
        ParamIgnitor("feedback", feedback),
        ParamIgnitor("tone", tone),
        pitches,
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DC Blocker
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One-pole highpass DC blocker — degenerate first-order HPF with raw pole.
 *
 * Removes DC offset accumulation from waveshaping, feedback, and asymmetric clipping.
 * Essential after distortion shapes that produce asymmetric output (diode, rectify).
 *
 * Cutoff approximation: `fc ≈ (1 − a)·fs / (2π)`. Reference points:
 *   - `coefficient = 0.995` → ~35 Hz @ 44.1k, ~38 Hz @ 48k (good for feedback paths)
 *   - `coefficient = 0.999` → ~7 Hz  @ 44.1k, ~7.6 Hz @ 48k (good for master output)
 *
 * Higher coefficient = lower cutoff = less low-frequency content removed.
 *
 * Implementation delegates to [LowPassHighPassFilters.DcBlocker]; see that class for
 * the dedup history (this used to be one of 9 inline copies before 2026-04-29).
 *
 * @param coefficient Raw IIR pole. NaN/Inf or out-of-range values fall back to 0.995. Default: 0.995.
 */
fun Ignitor.dcBlock(coefficient: Double = DEFAULT_DC_BLOCK_COEFF): Ignitor {
    val dcBlocker = LowPassHighPassFilters.DcBlocker(coefficient)

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)
            dcBlocker.process(input, output, ctx.offset, ctx.length)
        }
    }
}
