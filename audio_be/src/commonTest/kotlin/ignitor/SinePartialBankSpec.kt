/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The sine partial bank (`docs/plans/sine-partial-banks.md`): `IgnitorDsl.Sine` with `harmonics`,
 * `octaves`, `suboctaves`, `fundamental` and `analogSpread`, rendered by `Ignitors.sinePartials`.
 *
 * The contract pinned here: the bank renders the SAME partials as the hand-rolled
 * `Osc.sine().add(Osc.sine(freq * k).mul(1/k))` stacks (the golden tests build those trees from
 * the DSL and compare per sample), the literal defaults still build the plain sine, partials at
 * Nyquist are silent, knobs are read per block, and the drift lanes follow the `analogSpread`
 * blend against a test-side reference model.
 */
class SinePartialBankSpec : StringSpec({
    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 8

    fun ctx(random: Random = Random, frames: Int = blockFrames): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(frames),
        random = random,
    ).apply {
        updateOffsetAndLength(0, frames)
        voiceElapsedFrames = 0
    }

    /** Renders [blocks] consecutive blocks into one long buffer. */
    fun render(sig: Ignitor, freqHz: Double, random: Random = Random, count: Int = blocks): DoubleArray {
        val c = ctx(random)
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

    fun assertClose(a: DoubleArray, b: DoubleArray, tol: Double) {
        a.size shouldBe b.size
        var worst = 0.0
        for (i in a.indices) {
            worst = maxOf(worst, abs(a[i] - b[i]))
        }
        worst shouldBe (0.0 plusOrMinus tol)
    }

    fun assertBits(a: DoubleArray, b: DoubleArray) {
        a.size shouldBe b.size
        for (i in a.indices) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    fun const(v: Double): Ignitor = ConstantIgnitor(v)

    /** A plain engine sine at a fixed multiple of the voice frequency, scaled: one hand-rolled partial. */
    fun partial(multiple: Double, gain: Double, freqHz: Double): Ignitor =
        Ignitors.sine(freq = const(multiple * freqHz)).withGain(ParamIgnitor("gain", gain))

    fun sum(vararg parts: Ignitor): Ignitor = parts.reduce { acc, p -> acc + p }

    /** `Osc.sine(freq = Osc.freq().mul(k)).mul(g)` as the song writes it, on the DSL. */
    fun dslPartial(k: Double, g: Double): IgnitorDsl =
        IgnitorDsl.Sine(freq = IgnitorDsl.Freq.mul(IgnitorDsl.Constant(k))).mul(IgnitorDsl.Constant(g))

    fun c(v: Double) = IgnitorDsl.Constant(v)

    // ── the plain sine is untouched ──────────────────────────────────────────────

    "a bank of one partial renders bit-identically to the plain sine" {
        assertBits(render(Ignitors.sinePartials(), 440.0), render(Ignitors.sine(), 440.0))
    }

    "IgnitorDsl.Sine with literal defaults still renders bit-identically to Ignitors.sine" {
        assertBits(render(IgnitorDsl.Sine().toExciter(), 440.0), render(Ignitors.sine(), 440.0))
    }

    "isPlainSine: literal defaults yes, any bank knob set (a Param at 0 included) no" {
        IgnitorDsl.Sine().isPlainSine() shouldBe true
        IgnitorDsl.Sine(freq = c(5.0), analog = c(3.0)).isPlainSine() shouldBe true
        IgnitorDsl.Sine(harmonics = c(1.0)).isPlainSine() shouldBe false
        IgnitorDsl.Sine(octaves = c(1.0)).isPlainSine() shouldBe false
        IgnitorDsl.Sine(suboctaves = c(1.0)).isPlainSine() shouldBe false
        IgnitorDsl.Sine(fundamental = c(0.5)).isPlainSine() shouldBe false
        IgnitorDsl.Sine(harmonics = IgnitorDsl.Param("h", 0.0)).isPlainSine() shouldBe false
    }

    // ── the banks are the hand-rolled stacks ─────────────────────────────────────

    "harmonics 2: f + 2f/2 + 3f/3, the sum of three plain sines" {
        val bank = Ignitors.sinePartials(harmonics = const(2.0))
        val ref = sum(partial(1.0, 1.0, 440.0), partial(2.0, 0.5, 440.0), partial(3.0, 1.0 / 3.0, 440.0))
        assertClose(render(bank, 440.0), render(ref, 440.0), 1e-12)
    }

    "GOLDEN: Sine(harmonics = 7) is the Der Schmetterling sub + seven-sine stack, per sample" {
        val bank = IgnitorDsl.Sine(harmonics = c(7.0)).toExciter()
        val tree = (2..8).fold(IgnitorDsl.Sine() as IgnitorDsl) { acc, k -> acc + dslPartial(k.toDouble(), 1.0 / k) }
            .toExciter()

        for (freqHz in listOf(41.2, 220.0, 440.0)) {
            assertClose(render(bank, freqHz), render(tree, freqHz), 1e-12)
        }
    }

    "fundamental 0 leaves the overtones only: the golden tree without the sub" {
        val bank = IgnitorDsl.Sine(harmonics = c(7.0), fundamental = c(0.0)).toExciter()
        val tree = (3..8).fold(dslPartial(2.0, 0.5)) { acc, k -> acc + dslPartial(k.toDouble(), 1.0 / k) }.toExciter()
        assertClose(render(bank, 41.2), render(tree, 41.2), 1e-12)
    }

    "octaves 5 on a 2f sine scaled by 1/2 is the original six-sine grind stack" {
        val bank = IgnitorDsl.Sine(freq = IgnitorDsl.Freq.mul(c(2.0)), octaves = c(5.0)).mul(c(0.5)).toExciter()
        val tree = (2..6).fold(dslPartial(2.0, 0.5)) { acc, i ->
            val k = 1 shl i
            acc + dslPartial(k.toDouble(), 1.0 / k)
        }.toExciter()
        assertClose(render(bank, 41.2), render(tree, 41.2), 1e-12)
    }

    "suboctaves 1: f + (f/2)/2; rolloff 0 puts the sub at gain 1" {
        val half = Ignitors.sinePartials(suboctaves = const(1.0))
        assertClose(render(half, 440.0), render(sum(partial(1.0, 1.0, 440.0), partial(0.5, 0.5, 440.0)), 440.0), 1e-12)

        val flat = Ignitors.sinePartials(suboctaves = const(1.0), suboctavesRolloff = const(0.0))
        assertClose(render(flat, 440.0), render(sum(partial(1.0, 1.0, 440.0), partial(0.5, 1.0, 440.0)), 440.0), 1e-12)
    }

    "rolloff 2 is the 1/m² law: the 2f partial at 1/4" {
        val bank = Ignitors.sinePartials(fundamental = const(0.0), harmonics = const(1.0), harmonicsRolloff = const(2.0))
        assertClose(render(bank, 440.0), render(partial(2.0, 0.25, 440.0), 440.0), 1e-12)
    }

    "rolloff 0 is flat: three partials at gain 1" {
        val bank = Ignitors.sinePartials(harmonics = const(2.0), harmonicsRolloff = const(0.0))
        val ref = sum(partial(1.0, 1.0, 440.0), partial(2.0, 1.0, 440.0), partial(3.0, 1.0, 440.0))
        assertClose(render(bank, 440.0), render(ref, 440.0), 1e-12)
    }

    "banks sum without deduplication: harmonics 1 + octaves 1 doubles the 2f partial" {
        val bank = Ignitors.sinePartials(fundamental = const(0.0), harmonics = const(1.0), octaves = const(1.0))
        assertClose(render(bank, 440.0), render(partial(2.0, 1.0, 440.0), 440.0), 1e-12)
    }

    "each partial sits at its multiple: zero crossings of a lone 2f partial" {
        val bank = Ignitors.sinePartials(fundamental = const(0.0), harmonics = const(1.0))
        val out = render(bank, 440.0, count = 35) // 4480 frames, a hair over 100 ms
        var crossings = 0
        for (i in 1 until out.size) {
            if ((out[i - 1] >= 0.0 && out[i] < 0.0) || (out[i - 1] < 0.0 && out[i] >= 0.0)) crossings++
        }
        crossings shouldBeInRange 176..182 // 880 Hz over 101.6 ms = 178.7 crossings
    }

    // ── band limit ───────────────────────────────────────────────────────────────

    "a partial at or above Nyquist is silent, one just below is not" {
        val above = Ignitors.sinePartials(fundamental = const(0.0), octaves = const(1.0))
        // A fully silent bank must still WRITE zeros: the buffer arrives with stale content.
        val stale = AudioBuffer(blockFrames).apply { fill(123.0) }
        above.generate(stale, 11025.0, ctx()) // 2f = 22050 = Nyquist
        stale.all { it == 0.0 } shouldBe true
        render(above, 11025.0).all { it == 0.0 } shouldBe true
        val below = Ignitors.sinePartials(fundamental = const(0.0), octaves = const(1.0))
        render(below, 11000.0).any { it != 0.0 } shouldBe true // 2f = 22000
    }

    // ── knobs are signals, read per block ────────────────────────────────────────

    "a per-block count signal adds and removes partials at block boundaries, keeping surviving phases" {
        val count = object : Ignitor {
            var value = 0.0
            override fun controlRateValueOrNull(freqHz: Double): Double = value
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = buffer.fill(value)
        }
        val bank = Ignitors.sinePartials(harmonics = count)
        val plain = Ignitors.sine()
        val c1 = ctx()
        val c2 = ctx()
        val buf = AudioBuffer(blockFrames)
        val ref = AudioBuffer(blockFrames)

        fun block() {
            bank.generate(buf, 440.0, c1)
            plain.generate(ref, 440.0, c2)
            c1.voiceElapsedFrames += blockFrames
            c2.voiceElapsedFrames += blockFrames
        }

        // block 0: plain sine
        block()
        assertBits(buf.copyOf(), ref.copyOf())

        // block 1: a 2f partial appears at the block start
        count.value = 1.0
        block()
        var diff = 0.0
        for (i in 0 until blockFrames) diff = maxOf(diff, abs(buf[i] - ref[i]))
        (diff > 0.1) shouldBe true

        // block 2: gone again, and the fundamental's phase was never disturbed
        count.value = 0.0
        block()
        assertBits(buf.copyOf(), ref.copyOf())
    }

    /** A count signal the test drives per block. */
    class CountSignal(var value: Double) : Ignitor {
        override fun controlRateValueOrNull(freqHz: Double): Double = value
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = buffer.fill(value)
    }

    "a count change in the harmonics bank leaves the sub-octave partial's phase alone (per-bank regions)" {
        // The harmonics are made silent by an enormous rolloff (their count still changes), so the
        // output is the sub alone and must stay a continuous f/2 sine across the count step.
        val h = CountSignal(2.0)
        val bank = Ignitors.sinePartials(
            fundamental = const(0.0), harmonics = h, harmonicsRolloff = const(1000.0),
            suboctaves = const(1.0), suboctavesRolloff = const(0.0),
        )
        val ref = partial(0.5, 1.0, 440.0)
        val c1 = ctx()
        val c2 = ctx()
        val buf = AudioBuffer(blockFrames)
        val exp = AudioBuffer(blockFrames)
        repeat(4) { b ->
            if (b == 2) h.value = 3.0
            bank.generate(buf, 440.0, c1)
            ref.generate(exp, 440.0, c2)
            assertClose(buf.copyOf(), exp.copyOf(), 1e-12)
            c1.voiceElapsedFrames += blockFrames
            c2.voiceElapsedFrames += blockFrames
        }
    }

    "a surviving partial keeps its phase when its own bank grows" {
        val h = CountSignal(1.0)
        val bank = Ignitors.sinePartials(fundamental = const(0.0), harmonics = h)
        val ref = partial(2.0, 0.5, 440.0) // the 2f partial, alone
        val c1 = ctx()
        val c2 = ctx()
        val buf = AudioBuffer(blockFrames)
        val exp = AudioBuffer(blockFrames)
        // block 0: 2f alone. block 1: 3f joins; 2f must continue, so (bank - fresh 3f) == 2f continued.
        bank.generate(buf, 440.0, c1); ref.generate(exp, 440.0, c2)
        assertClose(buf.copyOf(), exp.copyOf(), 1e-12)
        c1.voiceElapsedFrames += blockFrames; c2.voiceElapsedFrames += blockFrames
        h.value = 2.0
        bank.generate(buf, 440.0, c1); ref.generate(exp, 440.0, c2)
        val fresh3f = AudioBuffer(blockFrames)
        partial(3.0, 1.0 / 3.0, 440.0).generate(fresh3f, 440.0, ctx())
        val twoF = DoubleArray(blockFrames) { buf[it] - fresh3f[it] }
        assertClose(twoF, exp.copyOf(), 1e-12)
    }

    "a re-activated partial starts at phase 0 (a zero crossing), not at its stale phase" {
        val h = CountSignal(1.0)
        val bank = Ignitors.sinePartials(fundamental = const(0.0), harmonics = h)
        val c1 = ctx()
        val buf = AudioBuffer(blockFrames)
        bank.generate(buf, 440.0, c1); c1.voiceElapsedFrames += blockFrames   // on
        h.value = 0.0
        bank.generate(buf, 440.0, c1); c1.voiceElapsedFrames += blockFrames   // off
        h.value = 1.0
        bank.generate(buf, 440.0, c1)                                          // on again: fresh start
        val fresh = AudioBuffer(blockFrames)
        partial(2.0, 0.5, 440.0).generate(fresh, 440.0, ctx())
        assertClose(buf.copyOf(), fresh.copyOf(), 1e-12)
    }

    "a mid-block window renders only its frames, like the plain sines it equals" {
        val offset = 37
        val length = 64
        val sentinel = 123.456
        fun windowCtx() = ctx().apply { updateOffsetAndLength(offset, length); voiceElapsedFrames = -offset }
        val bank = Ignitors.sinePartials(harmonics = const(1.0))
        val ref = sum(partial(1.0, 1.0, 440.0), partial(2.0, 0.5, 440.0))
        val a = AudioBuffer(blockFrames).apply { fill(sentinel) }
        val b = AudioBuffer(blockFrames).apply { fill(sentinel) }
        bank.generate(a, 440.0, windowCtx())
        ref.generate(b, 440.0, windowCtx())
        for (i in 0 until blockFrames) {
            if (i in offset until offset + length) {
                a[i] shouldBe (b[i] plusOrMinus 1e-12)
            } else {
                a[i] shouldBe sentinel
            }
        }
    }

    "a non-finite fundamental gain reads as 0: the overtones play, no NaN reaches the buffer" {
        val bank = Ignitors.sinePartials(fundamental = const(Double.NaN), harmonics = const(1.0))
        assertClose(render(bank, 440.0), render(partial(2.0, 0.5, 440.0), 440.0), 1e-12)
        val inf = Ignitors.sinePartials(fundamental = const(Double.POSITIVE_INFINITY))
        render(inf, 440.0).all { it == 0.0 } shouldBe true
    }

    "counts are coerced: negative and NaN read as none, a runaway count clamps to 64 without throwing" {
        val negative = Ignitors.sinePartials(harmonics = const(-3.0), octaves = const(Double.NaN))
        assertBits(render(negative, 440.0), render(Ignitors.sine(), 440.0))

        val runaway = Ignitors.sinePartials(harmonics = const(1e9), harmonicsRolloff = const(0.0))
        val capped = Ignitors.sinePartials(harmonics = const(64.0), harmonicsRolloff = const(0.0))
        assertClose(render(runaway, 100.0), render(capped, 100.0), 1e-12)
    }

    "a NaN analog read on the first block latches as inactive and a later value cannot resurrect drift" {
        val analog = CountSignal(Double.NaN)
        val bank = Ignitors.sinePartials(analog = analog, harmonics = const(2.0))
        val still = Ignitors.sinePartials(harmonics = const(2.0))
        val c1 = ctx()
        val c2 = ctx()
        val buf = AudioBuffer(blockFrames)
        val exp = AudioBuffer(blockFrames)
        repeat(3) { b ->
            if (b == 1) analog.value = 3.0
            bank.generate(buf, 440.0, c1) // must not throw
            still.generate(exp, 440.0, c2)
            assertBits(buf.copyOf(), exp.copyOf())
            c1.voiceElapsedFrames += blockFrames
            c2.voiceElapsedFrames += blockFrames
        }
    }

    "the pitch envelope moves every partial: bank under PitchEnvelope == tree under PitchEnvelope" {
        fun env(inner: IgnitorDsl) = IgnitorDsl.PitchEnvelope(inner, semitones = c(12.0), attackSec = c(0.001), decaySec = c(0.02))
        val bank = env(IgnitorDsl.Sine(harmonics = c(2.0))).toExciter()
        val tree = env(IgnitorDsl.Sine() + dslPartial(2.0, 0.5) + dslPartial(3.0, 1.0 / 3.0)).toExciter()
        val a = render(bank, 110.0)
        val b = render(tree, 110.0)
        assertClose(a, b, 1e-9)
        // and the envelope did something (otherwise the test proves nothing)
        var moved = 0.0
        val still = render(IgnitorDsl.Sine(harmonics = c(2.0)).toExciter(), 110.0)
        for (i in a.indices) moved = maxOf(moved, abs(a[i] - still[i]))
        (moved > 0.1) shouldBe true
    }

    // ── drift lanes ──────────────────────────────────────────────────────────────

    /**
     * Test-side model of the bank's drift: the bank creates the SHARED lane first (first block),
     * then one lane per partial in index order, all from `ctx.random`, so the same seed reproduces
     * the exact multiplier sequences here. Radian phases, the sine's own accumulate-and-wrap.
     */
    fun driftReference(seed: Int, analog: Double, spread: Double, multiples: DoubleArray, gains: DoubleArray, freqHz: Double): DoubleArray {
        val r = Random(seed)
        val shared = AnalogDrift(analog, sampleRate, r)
        val lanes = Array(multiples.size) { AnalogDrift(analog, sampleRate, r) }
        val ph = DoubleArray(multiples.size)
        val inc = DoubleArray(multiples.size) { TWO_PI * multiples[it] * freqHz / sampleRate.toDouble() }
        val out = DoubleArray(blocks * blockFrames)

        // Constant-power weights: the drift depth stays `analog` at every spread.
        val wShared = sqrt(1.0 - spread)
        val wOwn = sqrt(spread)

        for (i in out.indices) {
            val sharedDev = if (spread < 1.0) shared.nextMultiplier() - 1.0 else 0.0
            var acc = 0.0

            for (p in multiples.indices) {
                acc += gains[p] * sin(ph[p])
                val ownDev = if (spread > 0.0) lanes[p].nextMultiplier() - 1.0 else 0.0
                val mul = 1.0 + wShared * sharedDev + wOwn * ownDev
                ph[p] = (ph[p] + inc[p] * mul).wrapPhase(TWO_PI)
            }

            out[i] = acc
        }

        return out
    }

    val driftMultiples = doubleArrayOf(1.0, 2.0, 3.0)
    val driftGains = doubleArrayOf(1.0, 0.5, 1.0 / 3.0)

    fun driftBank(spread: Double): Ignitor =
        Ignitors.sinePartials(analog = const(20.0), harmonics = const(2.0), analogSpread = const(spread))

    "analogSpread 0: every partial follows the one shared walk" {
        assertClose(
            render(driftBank(0.0), 220.0, random = Random(11)),
            driftReference(11, 20.0, 0.0, driftMultiples, driftGains, 220.0),
            1e-9,
        )
    }

    "analogSpread 1: every partial walks on its own lane" {
        assertClose(
            render(driftBank(1.0), 220.0, random = Random(11)),
            driftReference(11, 20.0, 1.0, driftMultiples, driftGains, 220.0),
            1e-9,
        )
    }

    "analogSpread 0.25: mostly shared, some own (unequal weights pin the blend direction), and the three settings differ" {
        val mid = render(driftBank(0.25), 220.0, random = Random(11))
        assertClose(mid, driftReference(11, 20.0, 0.25, driftMultiples, driftGains, 220.0), 1e-9)

        val locked = render(driftBank(0.0), 220.0, random = Random(11))
        val free = render(driftBank(1.0), 220.0, random = Random(11))
        fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
            var d = 0.0
            for (i in a.indices) d = maxOf(d, abs(a[i] - b[i]))
            return d
        }
        (maxDiff(locked, free) > 1e-6) shouldBe true
        (maxDiff(locked, mid) > 1e-6) shouldBe true
        (maxDiff(mid, free) > 1e-6) shouldBe true
    }

    "same seed, same render: the bank with drift is reproducible" {
        val a = render(driftBank(1.0), 220.0, random = Random(3))
        val b = render(driftBank(1.0), 220.0, random = Random(3))
        assertBits(a, b)
        render(driftBank(1.0), 220.0, random = Random(4)).contentEquals(a) shouldNotBe true
    }
})
