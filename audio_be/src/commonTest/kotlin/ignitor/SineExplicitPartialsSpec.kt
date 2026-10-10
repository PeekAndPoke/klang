/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.range
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The explicit partials on the sine (`docs/tasks/in-progress/sine-inharmonic-partials.md`, Q26):
 * `IgnitorDsl.Sine.partials`, rendered by `Ignitors.sinePartials` as the fourth bank.
 *
 * The oracle is written here, from the definition: a partial `(r, g, p)` on a sine at `f` is
 * `g * sin(2 pi (r f t + p))`, summed raw with the fundamental and the banks. The engine accumulates each phase
 * sample by sample (`fastSin`, error bound 1e-10), so the closed form agrees within 1e-8 over these renders; the
 * identities that hold bit for bit (one partial at ratio 1 is the sine, a wrapped phase is the same phase, a
 * non-finite knob reads as its silent or default value) are asserted bit for bit. The drift rows live in
 * `SinePartialBankSpec`, next to the reference drift model they share with the banks.
 */
class SineExplicitPartialsSpec : StringSpec({
    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 8

    fun ctx(random: Random = Random(7)): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = random,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
        voiceElapsedFrames = 0
    }

    /** Renders [count] consecutive blocks into one long buffer. */
    fun render(sig: Ignitor, freqHz: Double, count: Int = blocks): DoubleArray {
        val c = ctx()
        val out = DoubleArray(count * blockFrames)
        val buf = AudioBuffer(blockFrames)

        repeat(count) { b ->
            sig.generate(buf, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        a.size shouldBe b.size
        var worst = 0.0

        for (i in a.indices) {
            worst = maxOf(worst, abs(a[i] - b[i]))
        }

        return worst
    }

    fun assertClose(a: DoubleArray, b: DoubleArray, tol: Double) {
        maxDiff(a = a, b = b) shouldBe (0.0 plusOrMinus tol)
    }

    fun assertBits(a: DoubleArray, b: DoubleArray) {
        a.size shouldBe b.size

        for (i in a.indices) {
            withClue("sample $i") {
                a[i].toRawBits() shouldBe b[i].toRawBits()
            }
        }
    }

    fun peak(a: DoubleArray): Double = a.maxOf { abs(it) }

    fun const(v: Double): Ignitor = ConstantIgnitor(v)

    fun sp(ratio: Double, gain: Double = 1.0, phase: Double = 0.0): Ignitors.SinePartial =
        Ignitors.SinePartial(ratio = const(ratio), gain = const(gain), phase = const(phase))

    fun cluster(vararg partials: Ignitors.SinePartial, fundamental: Double = 0.0): Ignitor =
        Ignitors.sinePartials(fundamental = const(fundamental), partials = partials.toList())

    /** A partial of the oracle: ratio, gain, phase in cycles. */
    class P(val ratio: Double, val gain: Double, val phase: Double = 0.0)

    /** The oracle: the sum of `g sin(2 pi (r f n / sr + p))`, the phase taken modulo one cycle from the closed form. */
    fun oracle(freqHz: Double, vararg partials: P, frames: Int = blocks * blockFrames): DoubleArray = DoubleArray(frames) { n ->
        var acc = 0.0

        for (p in partials) {
            val cycles = p.ratio * freqHz * n / sampleRate + p.phase

            acc += p.gain * sin(2.0 * PI * (cycles - floor(cycles)))
        }

        acc
    }

    /** A knob the test drives per block: block-constant, its value changed between blocks. */
    class Knob(var value: Double) : Ignitor {
        override val isBlockConstant: Boolean = true

        override fun controlRateValueOrNull(freqHz: Double): Double = value

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = buffer.fill(value)
    }

    // Der Schmetterling's thud (`metalSnare`): 13 ratios, the gains' magnitudes, the signs as start phases.
    val thudRatios = doubleArrayOf(0.6571, 0.7571, 0.8714, 0.9476, 1.0857, 1.2524, 1.4333, 1.6524, 1.8952, 2.1952, 2.5333, 2.9381, 3.3905)
    val thudGains = doubleArrayOf(0.520, 0.676, -0.652, 0.826, 0.938, -1.000, 0.839, 0.746, -0.692, -0.591, 0.494, 0.474, -0.358)

    // ── ratio 1 and the fundamental ─────────────────────────────────────────────────

    "one partial at ratio 1 under fundamental(0) is the plain sine, bit for bit, on both paths" {
        assertBits(a = render(cluster(sp(1.0)), 440.0), b = render(Ignitors.sine(), 440.0))

        val dsl = IgnitorDsl.Sine(fundamental = IgnitorDsl.Constant(0.0), partials = listOf(IgnitorDsl.Sine.Partial(ratio = IgnitorDsl.Constant(1.0))))
        assertBits(a = render(dsl.toExciter(random = Random(1)), 440.0), b = render(IgnitorDsl.Sine().toExciter(random = Random(1)), 440.0))
    }

    "a ratio-1 partial sums with the fundamental: twice the sine" {
        val doubled = render(cluster(sp(1.0), fundamental = 1.0), 440.0)
        val plain = render(Ignitors.sine(), 440.0)

        assertBits(a = doubled, b = DoubleArray(plain.size) { plain[it] + plain[it] })
    }

    // ── a cluster ───────────────────────────────────────────────────────────────────

    "the thud's cluster, no ratio-1 partial, under fundamental(0) is the sum of its 13 sines" {
        val engine = cluster(*Array(13) { sp(ratio = thudRatios[it], gain = abs(thudGains[it]), phase = if (thudGains[it] < 0.0) 0.5 else 0.0) })
        val expected = oracle(205.0, *Array(13) { P(ratio = thudRatios[it], gain = thudGains[it]) })
        val got = render(engine, 205.0)

        assertClose(a = got, b = expected, tol = 1e-8)
        peak(got) shouldBeGreaterThan 1.0
    }

    // ── gains and phases ────────────────────────────────────────────────────────────

    "a negative gain is the same partial at phase 0.5, and a gain is linear" {
        val negative = render(cluster(sp(ratio = 1.37, gain = -0.6)), 330.0)
        val half = render(cluster(sp(ratio = 1.37, gain = 0.6, phase = 0.5)), 330.0)

        assertClose(a = negative, b = half, tol = 1e-9)
        assertClose(a = negative, b = oracle(330.0, P(ratio = 1.37, gain = -0.6)), tol = 1e-8)
        assertClose(a = render(cluster(sp(ratio = 1.37, gain = -0.6, phase = 0.25)), 330.0), b = render(cluster(sp(ratio = 1.37, gain = 0.6, phase = 0.75)), 330.0), tol = 1e-9)
        peak(negative) shouldBe (0.6 plusOrMinus 1e-3)
    }

    "phases are fractions of one cycle: 0.25 starts on the peak, and they wrap (1.25 is 0.25, -0.25 is 0.75)" {
        val quarter = render(cluster(sp(ratio = 0.8, gain = 1.0, phase = 0.25)), 220.0)

        quarter[0] shouldBe (1.0 plusOrMinus 1e-9)
        assertClose(a = quarter, b = oracle(220.0, P(ratio = 0.8, gain = 1.0, phase = 0.25)), tol = 1e-8)
        assertClose(a = render(cluster(sp(ratio = 2.2, gain = 0.5, phase = 0.75)), 220.0), b = oracle(220.0, P(ratio = 2.2, gain = 0.5, phase = 0.75)), tol = 1e-8)
        assertBits(a = render(cluster(sp(ratio = 0.8, phase = 1.25)), 220.0), b = quarter)
        assertBits(a = render(cluster(sp(ratio = 0.8, phase = -0.25)), 220.0), b = render(cluster(sp(ratio = 0.8, phase = 0.75)), 220.0))
    }

    "the sine's own phase moves every partial by the same fraction of its own cycle, on top of the partial's phase" {
        val shifted = Ignitors.sinePartials(fundamental = const(0.0), phase = const(0.25), partials = listOf(sp(ratio = 1.5, gain = 1.0, phase = 0.25)))

        assertClose(a = render(shifted, 220.0), b = oracle(220.0, P(ratio = 1.5, gain = 1.0, phase = 0.5)), tol = 1e-8)
    }

    // ── with the other banks ────────────────────────────────────────────────────────

    "with harmonics: summed raw, a partial both list is there twice" {
        // harmonics(1) adds 2f at 1/2; the explicit partial adds 2f at 1/2 again, and the fundamental plays too.
        val both = Ignitors.sinePartials(harmonics = const(1.0), partials = listOf(sp(ratio = 2.0, gain = 0.5)))

        assertClose(a = render(both, 440.0), b = oracle(440.0, P(ratio = 1.0, gain = 1.0), P(ratio = 2.0, gain = 1.0)), tol = 1e-8)
    }

    // ── band limit ──────────────────────────────────────────────────────────────────

    "a partial at or past Nyquist is silent and writes zeros; just below it plays; a negative ratio is judged by its magnitude" {
        // Started on the peak (phase 0.25): a sine sampled AT Nyquist from phase 0 is all zeros, gated or not.
        val atNyquist = cluster(sp(ratio = 2.0, phase = 0.25))
        val stale = AudioBuffer(blockFrames).apply { fill(123.0) }

        atNyquist.generate(stale, 11025.0, ctx()) // 2 x 11025 = 22050 = Nyquist
        stale.all { it == 0.0 } shouldBe true

        for (ratio in doubleArrayOf(2.0, -2.0, 2.3, -2.3)) {
            withClue("ratio $ratio") {
                render(cluster(sp(ratio = ratio, phase = 0.25)), 11025.0).all { it == 0.0 } shouldBe true
            }
        }

        peak(render(cluster(sp(ratio = 1.99)), 11025.0)) shouldBeGreaterThan 0.5

        // below Nyquist a negative ratio is the partial at the magnitude, inverted: sin(-x) = -sin(x)
        val negative = render(cluster(sp(ratio = -1.5)), 440.0)
        val positive = render(cluster(sp(ratio = 1.5)), 440.0)

        assertClose(a = negative, b = DoubleArray(positive.size) { -positive[it] }, tol = 1e-9)
    }

    "a moving ratio that crosses Nyquist drops the partial for that block and resumes it where its phase stopped" {
        val ratio = Knob(2.0)
        val engine = cluster(Ignitors.SinePartial(ratio = ratio, gain = const(1.0), phase = const(0.0)))
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(3 * blockFrames)

        for (b in 0 until 3) {
            ratio.value = if (b == 1) 2.3 else 2.0 // at 10 kHz: 20 kHz plays, 23 kHz is past Nyquist
            engine.generate(buf, 10000.0, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        // the oracle: block 1 silent, the phase frozen across it, so block 2 continues block 0
        val inc = 2.0 * PI * 20000.0 / sampleRate
        val expected = DoubleArray(out.size) { n ->
            when {
                n < blockFrames -> sin(inc * n)
                n < 2 * blockFrames -> 0.0
                else -> sin(inc * (n - blockFrames))
            }
        }

        assertClose(a = out, b = expected, tol = 1e-8)
    }

    "one Nyquist law for the whole node: under a negative base frequency the fundamental and a bank are judged by magnitude" {
        // harmonics(40, rolloff 0) on +1000 Hz plays f .. 22 f (23 partials below Nyquist); on -1000 Hz the same
        // partials, each inverted (sin(-x) = -sin(x)), and the 18 past Nyquist silent, not aliased back
        val up = render(Ignitors.sinePartials(freq = const(1000.0), harmonics = const(40.0), harmonicsRolloff = const(0.0)), 1000.0)
        val down = render(Ignitors.sinePartials(freq = const(-1000.0), harmonics = const(40.0), harmonicsRolloff = const(0.0)), -1000.0)

        assertClose(a = down, b = DoubleArray(up.size) { -up[it] }, tol = 1e-8)
        peak(up) shouldBeGreaterThan 5.0

        // a partial at ratio 1 is the fundamental at a negative base too
        assertClose(
            a = render(Ignitors.sinePartials(freq = const(-30000.0), fundamental = const(0.0), partials = listOf(sp(1.0))), 1.0),
            b = render(Ignitors.sinePartials(freq = const(-30000.0)), 1.0),
            tol = 0.0,
        )
    }

    // ── the resource cap ────────────────────────────────────────────────────────────

    "a sine plays its first 256 partials: a 257th is not built, and the 256th still plays" {
        fun partial(i: Int) = IgnitorDsl.Sine.Partial(ratio = IgnitorDsl.Constant(0.5 + 0.01 * i), gain = IgnitorDsl.Constant(0.01))

        fun sine(n: Int, extra: IgnitorDsl.Sine.Partial? = null) =
            IgnitorDsl.Sine(fundamental = IgnitorDsl.Constant(0.0), partials = List(n) { partial(it) } + listOfNotNull(extra))
                .toExciter(random = Random(1))

        val capped = render(sine(256), 110.0)
        val loud257th = IgnitorDsl.Sine.Partial(ratio = IgnitorDsl.Constant(3.0), gain = IgnitorDsl.Constant(1.0))

        assertBits(a = render(sine(256, extra = loud257th), 110.0), b = capped)
        maxDiff(a = render(sine(255), 110.0), b = capped) shouldBeGreaterThan 1e-3
    }

    // ── knobs as signals, read once per block ───────────────────────────────────────

    "a ratio and a gain driven per block step at block boundaries, the phase continuous" {
        val ratio = Knob(1.5)
        val gain = Knob(0.8)
        val engine = cluster(Ignitors.SinePartial(ratio = ratio, gain = gain, phase = const(0.0)))
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(4 * blockFrames)
        val ratios = doubleArrayOf(1.5, 2.0, 2.0, 0.75)
        val gains = doubleArrayOf(0.8, 0.8, -0.3, 0.5)

        for (b in 0 until 4) {
            ratio.value = ratios[b]
            gain.value = gains[b]
            engine.generate(buf, 300.0, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        // the oracle accumulates the phase sample by sample at each block's ratio, from the definition
        val expected = DoubleArray(out.size)
        var ph = 0.0

        for (n in expected.indices) {
            val b = n / blockFrames

            expected[n] = gains[b] * sin(ph)
            ph += 2.0 * PI * ratios[b] * 300.0 / sampleRate
        }

        assertClose(a = out, b = expected, tol = 1e-8)
    }

    "a gain signal is read per sample: an LFO that starts at 0 plays from the first block, smooth" {
        // a 3 Hz sine as the gain, 0 at the first sample: the partial is not skipped, nothing waits for a block edge
        val engine = cluster(Ignitors.SinePartial(ratio = const(1.25), gain = Ignitors.sine(freq = const(3.0)), phase = const(0.0)))
        val inc = 2.0 * PI * 1.25 * 440.0 / sampleRate
        val expected = DoubleArray(blocks * blockFrames) { n -> sin(2.0 * PI * 3.0 * n / sampleRate) * sin(inc * n) }

        assertClose(a = render(engine, 440.0), b = expected, tol = 1e-8)
    }

    /** One explicit partial at 1.25 with [gain] and [phase], under fundamental(0), on the DSL path. */
    fun dslPartial(gain: IgnitorDsl, phase: Double): IgnitorDsl = IgnitorDsl.Sine(
        fundamental = IgnitorDsl.Constant(0.0),
        partials = listOf(IgnitorDsl.Sine.Partial(ratio = IgnitorDsl.Constant(1.25), gain = gain, phase = IgnitorDsl.Constant(phase))),
    )

    /** The same partial as its own sine: `Ign.sine(Ign.freq().mul(1.25), x => x.phase(p))`. */
    fun ownSine(phase: Double): IgnitorDsl = IgnitorDsl.Sine(freq = IgnitorDsl.Freq.mul(IgnitorDsl.Constant(1.25)), phase = IgnitorDsl.Constant(phase))

    fun firstSound(a: DoubleArray): Int = a.indexOfFirst { it != 0.0 }

    "an envelope on a partial's gain is the partial as its own sine under the same adsr: 0.5 ms and 2 ms attacks, from the peak" {
        for ((attack, decay) in listOf(0.0005 to 0.050, 0.002 to 0.300)) {
            withClue("attack $attack, decay $decay") {
                val env = IgnitorDsl.Constant(1.0).adsr(attack = attack, decay = decay, sustain = 0.0, release = 0.02)
                val partial = render(dslPartial(gain = env, phase = 0.25).toExciter(random = Random(1)), 210.0, count = 24)
                val own = render(ownSine(0.25).adsr(attack = attack, decay = decay, sustain = 0.0, release = 0.02).toExciter(random = Random(1)), 210.0, count = 24)

                firstSound(partial) shouldBe firstSound(own)
                firstSound(own) shouldBe 1 // the envelope is 0 at the onset, the sine from its peak just after
                assertBits(a = partial, b = own)
                peak(own) shouldBeGreaterThan 0.5
            }
        }
    }

    "a 12 Hz tremolo on a partial's gain is the partial as its own sine times the same tremolo" {
        val tremolo = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(12.0)).range(from = 0.0, to = 1.0)
        val partial = render(dslPartial(gain = tremolo, phase = 0.0).toExciter(random = Random(1)), 210.0, count = 40)
        val own = render(ownSine(0.0).mul(tremolo).toExciter(random = Random(1)), 210.0, count = 40)

        assertBits(a = partial, b = own)
        peak(own) shouldBeGreaterThan 0.5
    }

    "a phase driven per block glides to its new value across the block, the short way round the cycle" {
        val phase = Knob(0.0)
        val engine = cluster(Ignitors.SinePartial(ratio = const(1.0), gain = const(1.0), phase = phase))
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        // the first block's phase is the start phase (a jump); then 0.25, 0.1, 0.95 (the short way: -0.15) and 0.05 (+0.1)
        val phases = doubleArrayOf(0.3, 0.25, 0.1, 0.95, 0.05)
        val out = DoubleArray(phases.size * blockFrames)

        for (b in phases.indices) {
            phase.value = phases[b]
            engine.generate(buf, 500.0, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        // the oracle: within block b >= 1 the offset runs linearly from the last block's phase by the folded change
        val inc = 2.0 * PI * 500.0 / sampleRate
        val expected = DoubleArray(out.size) { n ->
            val b = n / blockFrames
            val j = n % blockFrames
            var offset = phases[0]

            for (k in 1..b) {
                var d = phases[k] - phases[k - 1]

                if (d > 0.5) {
                    d -= 1.0
                }

                if (d <= -0.5) {
                    d += 1.0
                }

                offset += if (k < b) d else d * j / blockFrames
            }

            sin(inc * n + 2.0 * PI * offset)
        }

        assertClose(a = out, b = expected, tol = 1e-8)

        // and no block edge jumps: the largest sample-to-sample step stays the sine's own
        var jump = 0.0
        for (n in 1 until out.size) jump = maxOf(jump, abs(out[n] - out[n - 1]))
        jump shouldBe (0.0 plusOrMinus (inc * 1.3))
    }

    // ── the signal-gain loop under drift, pitch modulation and a moving phase (review round 2) ──

    /** A signal (not block-constant) that holds [v]: it takes the per-sample gain loop. */
    class Held(private val v: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = v
            }
        }
    }

    /** Renders [count] blocks with a seeded stream and, when [pm] is set, that phase modulation on every block. */
    fun renderWith(sig: Ignitor, freqHz: Double, seed: Int, pm: DoubleArray?, count: Int = blocks): DoubleArray {
        val c = ctx(random = Random(seed))
        val out = DoubleArray(count * blockFrames)
        val buf = AudioBuffer(blockFrames)

        repeat(count) { b ->
            c.phaseMod = pm
            sig.generate(buf, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        return out
    }

    "a signal gain holding 0.7 renders bit for bit the constant gain 0.7: under drift, under a phase modulation, under a moving sine phase" {
        fun bank(gain: Ignitor, analog: Double, phase: Ignitor?) = Ignitors.sinePartials(
            analog = const(analog), analogSpread = const(1.0), fundamental = const(0.0), phase = phase,
            partials = listOf(sp(ratio = 0.8), Ignitors.SinePartial(ratio = const(1.37), gain = gain, phase = const(0.25))),
        )

        val pm = DoubleArray(blockFrames) { 1.0 + 0.02 * sin(2.0 * PI * it / blockFrames) }

        withClue("analog 20, spread 1") {
            val held = renderWith(bank(gain = Held(0.7), analog = 20.0, phase = null), 330.0, seed = 5, pm = null)

            assertBits(a = held, b = renderWith(bank(gain = const(0.7), analog = 20.0, phase = null), 330.0, seed = 5, pm = null))
            maxDiff(a = held, b = renderWith(bank(gain = const(0.7), analog = 0.0, phase = null), 330.0, seed = 5, pm = null)) shouldBeGreaterThan 1e-6
        }

        withClue("phase modulation") {
            val held = renderWith(bank(gain = Held(0.7), analog = 0.0, phase = null), 330.0, seed = 5, pm = pm)

            assertBits(a = held, b = renderWith(bank(gain = const(0.7), analog = 0.0, phase = null), 330.0, seed = 5, pm = pm))
            maxDiff(a = held, b = renderWith(bank(gain = const(0.7), analog = 0.0, phase = null), 330.0, seed = 5, pm = null)) shouldBeGreaterThan 1e-3
        }

        withClue("a moving sine phase") {
            val held = renderWith(bank(gain = Held(0.7), analog = 0.0, phase = Ignitors.sine(freq = const(2.0)) * 0.1), 330.0, seed = 5, pm = null)

            assertBits(a = held, b = renderWith(bank(gain = const(0.7), analog = 0.0, phase = Ignitors.sine(freq = const(2.0)) * 0.1), 330.0, seed = 5, pm = null))
            maxDiff(a = held, b = renderWith(bank(gain = const(0.7), analog = 0.0, phase = null), 330.0, seed = 5, pm = null)) shouldBeGreaterThan 1e-3
        }
    }

    "a signal gain keeps running while its partial sits past Nyquist for a block: an adsr, and a stateful LFO" {
        // The adsr reads the voice clock, so it would land right even if it were skipped; the LFO keeps its own phase,
        // so it shows whether the gain really rendered during the silent block.
        val gains = listOf<Pair<String, () -> Ignitor>>(
            "adsr" to { IgnitorDsl.Constant(1.0).adsr(attack = 0.002, decay = 0.3, sustain = 0.0, release = 0.02).toExciter(random = Random(1)) },
            "LFO" to { Ignitors.sine(freq = const(30.0)) * 0.5 + const(0.5) },
        )

        for ((name, gain) in gains) {
            withClue(name) {
                val ratio = Knob(2.0)
                val engine = cluster(Ignitors.SinePartial(ratio = ratio, gain = gain(), phase = const(0.0)))
                val envelope = render(gain(), 10000.0, count = 3)
                val c = ctx()
                val buf = AudioBuffer(blockFrames)
                val out = DoubleArray(3 * blockFrames)

                for (b in 0 until 3) {
                    ratio.value = if (b == 1) 2.3 else 2.0 // at 10 kHz: 20 kHz plays, 23 kHz is past Nyquist
                    engine.generate(buf, 10000.0, c)

                    for (i in 0 until blockFrames) {
                        out[b * blockFrames + i] = buf[i]
                    }

                    c.voiceElapsedFrames += blockFrames
                }

                // the partial's phase is held for the silent block; the gain runs on through it
                val inc = 2.0 * PI * 20000.0 / sampleRate
                val expected = DoubleArray(out.size) { n ->
                    when {
                        n < blockFrames -> envelope[n] * sin(inc * n)
                        n < 2 * blockFrames -> 0.0
                        else -> envelope[n] * sin(inc * (n - blockFrames))
                    }
                }

                assertClose(a = out, b = expected, tol = 1e-8)
                peak(out) shouldBeGreaterThan 0.5
            }
        }
    }

    "a gain of 0 for one block while the phase changes: the partial comes back at the new phase" {
        val gain = Knob(1.0)
        val phase = Knob(0.0)
        val engine = cluster(Ignitors.SinePartial(ratio = const(1.0), gain = gain, phase = phase))
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(3 * blockFrames)

        for (b in 0 until 3) {
            gain.value = if (b == 1) 0.0 else 1.0
            phase.value = if (b == 0) 0.0 else 0.25
            engine.generate(buf, 500.0, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        // silent in block 1 (phase held), the change applied as a jump there, so block 2 starts a quarter cycle on
        val inc = 2.0 * PI * 500.0 / sampleRate
        val expected = DoubleArray(out.size) { n ->
            when {
                n < blockFrames -> sin(inc * n)
                n < 2 * blockFrames -> 0.0
                else -> sin(inc * (n - blockFrames) + 0.5 * PI)
            }
        }

        assertClose(a = out, b = expected, tol = 1e-8)
    }

    "a phase change in a zero-length window is kept: it lands as a jump, and the next block plays at the new phase" {
        // a window of 0 frames is reachable (a gate clipped to 0 frames still runs the voice's pipeline)
        val phase = Knob(0.0)
        val engine = cluster(Ignitors.SinePartial(ratio = const(1.0), gain = const(1.0), phase = phase))
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(2 * blockFrames)

        engine.generate(buf, 500.0, c)
        buf.copyInto(destination = out, destinationOffset = 0)
        c.voiceElapsedFrames += blockFrames

        phase.value = 0.25
        c.updateOffsetAndLength(offset = 0, length = 0)
        engine.generate(buf, 500.0, c)

        c.updateOffsetAndLength(offset = 0, length = blockFrames)
        engine.generate(buf, 500.0, c)
        buf.copyInto(destination = out, destinationOffset = blockFrames)

        val inc = 2.0 * PI * 500.0 / sampleRate
        val expected = DoubleArray(out.size) { n -> sin(inc * n + if (n < blockFrames) 0.0 else 0.5 * PI) }

        assertClose(a = out, b = expected, tol = 1e-8)
    }

    // ── hostile values ──────────────────────────────────────────────────────────────

    val hostile = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

    "a non-finite ratio silences that partial and only that one" {
        val alone = render(cluster(sp(ratio = 1.5, gain = 0.7)), 440.0)

        for (v in hostile) {
            withClue("ratio $v") {
                // started on its peak, so a partial that rendered even one sample before its phase was scrubbed would show
                assertBits(a = render(cluster(sp(ratio = v, gain = 1.0, phase = 0.25), sp(ratio = 1.5, gain = 0.7)), 440.0), b = alone)
            }
        }
    }

    "a non-finite gain reads as 0: that partial is silent, the others play" {
        val alone = render(cluster(sp(ratio = 1.5, gain = 0.7)), 440.0)

        for (v in hostile) {
            withClue("gain $v") {
                assertBits(a = render(cluster(sp(ratio = 0.9, gain = v), sp(ratio = 1.5, gain = 0.7)), 440.0), b = alone)
            }
        }
    }

    "a gain signal's non-finite samples read as 0: that partial is silent, the others play" {
        val alone = render(cluster(sp(ratio = 1.5, gain = 0.7)), 440.0)

        for (v in hostile) {
            withClue("gain signal $v") {
                val signal = object : Ignitor {
                    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = buffer.fill(v)
                }

                // exact up to the sign of a zero: the silent partial adds +0.0 to the other's -0.0 at the onset
                assertClose(a = render(cluster(sp(ratio = 1.5, gain = 0.7), Ignitors.SinePartial(ratio = const(0.9), gain = signal, phase = const(0.25))), 440.0), b = alone, tol = 0.0)
            }
        }
    }

    "a non-finite phase reads as 0: the partial plays from the upward zero crossing" {
        val zero = render(cluster(sp(ratio = 0.9, gain = 0.7, phase = 0.0)), 440.0)

        for (v in hostile) {
            withClue("phase $v") {
                assertBits(a = render(cluster(sp(ratio = 0.9, gain = 0.7, phase = v)), 440.0), b = zero)
            }
        }

        peak(zero) shouldBeGreaterThan 0.5
    }
})
