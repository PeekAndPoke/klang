/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The band MORPH of [ResonatorBank] (Katalyst step 5c-10, 2026-09-20): a material change travels
 * the bank in service instead of crossfading two banks.
 *
 * The rules under test were DECIDED with the maintainer (`docs/tasks/katalyst-dsl.md`, "Morph
 * rules of the bank"), so every row states one of them and the oracle is that rule applied to the
 * bare DSP: [MorphOracle] drives plain [LowPassHighPassFilters.SvfBPF] instances BY HAND with the
 * law's values and sums them itself. No number here is read off a run of the bank.
 */
class ResonatorBankMorphSpec : StringSpec({

    val sampleRate = 48000.0
    val n = 128
    val len = (sampleRate * KNOB_GLIDE_SECONDS).toInt()

    fun band(freq: Double, q: Double, gain: Double) = ResonatorBank.Band(freq, q, gain)

    // A sweep plus a rattle, so every band rings and the state carries across blocks.
    fun input(blocks: Int): AudioBuffer = AudioBuffer(n * blocks) { i ->
        val t = i / sampleRate

        0.5 * sin(2.0 * PI * (80.0 + 12000.0 * t) * t) + 0.3 * sin(i * 0.731) * sin(i * 0.0137)
    }

    val wood = listOf(band(300.0, 8.0, 1.0), band(900.0, 6.0, 0.6), band(1900.0, 5.0, 0.35))
    val glass = listOf(band(700.0, 40.0, 1.4), band(2100.0, 60.0, 0.5), band(4000.0, 35.0, 0.2))

    "a morph travels every band by position, and the whole trajectory is the decided law" {
        val signal = input(blocks = 30)
        val bank = ResonatorBank(wood, sampleRate)
        val oracle = MorphOracle(wood, sampleRate, len)
        val actual = signal.copyOf()
        val expected = signal.copyOf()

        for (b in 0 until 30) {
            if (b == 4) {
                bank.morphTo(glass)
                oracle.morphTo(glass)
            }

            bank.process(actual, b * n, n)
            oracle.process(expected, b * n, n)
        }

        withClue("every sample is the law's") {
            actual.indices.maxOf { abs(actual[it] - expected[it]) } shouldBeLessThan 1e-12
        }

        // Teeth: a bank that JUMPED to glass would be a different signal, so the row can fail.
        val jumped = signal.copyOf()
        val hard = ResonatorBank(wood, sampleRate)

        for (b in 0 until 30) {
            if (b == 4) {
                hard.morphTo(glass)
                hard.process(jumped, b * n, n) // the first morphed block, then force the landing
                repeat(len / n + 2) { hard.process(AudioBuffer(n), 0, n) }

                continue
            }

            hard.process(jumped, b * n, n)
        }

        expected.indices.maxOf { abs(expected[it] - jumped[it]) } shouldBeGreaterThan 0.01
    }

    "frequency and Q travel in LOG space, the gain LINEARLY" {
        val bank = ResonatorBank(wood, sampleRate)
        val buf = input(blocks = 40)

        bank.process(buf, 0, n) // spend the snap: the first morph would otherwise be instant
        bank.morphTo(glass)

        val half = len / 2
        var done = 0

        while (done < half) {
            bank.process(buf, n + done, n)
            done += n
        }

        val t = done.toDouble() / len
        val here = bank.bandNow(0)

        withClue("frequency on the log axis at t = $t") {
            here.freq shouldBe (exp(ln(300.0) + (ln(700.0) - ln(300.0)) * t) plusOrMinus 1e-9)
        }

        withClue("q on the log axis") {
            here.q shouldBe (exp(ln(8.0) + (ln(40.0) - ln(8.0)) * t) plusOrMinus 1e-9)
        }

        withClue("gain on the linear axis") {
            here.gain shouldBe (1.0 + (1.4 - 1.0) * t plusOrMinus 1e-9)
        }

        // Teeth: the linear-frequency law and the log-gain law are far away from these numbers.
        withClue("a LINEAR frequency law would be here instead") {
            abs(here.freq - (300.0 + (700.0 - 300.0) * t)) shouldBeGreaterThan 20.0
        }

        withClue("a LOG gain law would be here instead") {
            abs(here.gain - exp(ln(1.0) + (ln(1.4) - ln(1.0)) * t)) shouldBeGreaterThan 1e-3
        }
    }

    "bands pair BY POSITION, never by nearest frequency" {
        // The target is this bank's own bands in reverse: paired by position every band crosses
        // the bank, paired by nearest frequency nothing would move at all.
        val bank = ResonatorBank(wood, sampleRate)
        val buf = input(blocks = 40)

        bank.process(buf, 0, n)
        bank.morphTo(wood.reversed())

        var done = 0

        while (done < len / 2) {
            bank.process(buf, n + done, n)
            done += n
        }

        val t = done.toDouble() / len

        withClue("band 0 is on its way from 300 Hz to 1900 Hz") {
            bank.bandNow(0).freq shouldBe (exp(ln(300.0) + (ln(1900.0) - ln(300.0)) * t) plusOrMinus 1e-9)
        }

        withClue("band 2 is on its way down to 300 Hz") {
            bank.bandNow(2).freq shouldBe (exp(ln(1900.0) + (ln(300.0) - ln(1900.0)) * t) plusOrMinus 1e-9)
        }

        withClue("nearest-frequency pairing would have left band 0 at 300 Hz") {
            abs(bank.bandNow(0).freq - 300.0) shouldBeGreaterThan 100.0
        }
    }

    "a band the target does not have keeps its frequency and fades its gain to 0 in place" {
        val bank = ResonatorBank(wood, sampleRate)
        val buf = input(blocks = 40)

        bank.process(buf, 0, n)
        bank.morphTo(wood.take(2))

        var done = 0

        while (done < len) {
            withClue("band 2 never moves while it fades, at $done samples") {
                bank.bandNow(2).freq shouldBe (1900.0 plusOrMinus 1e-9)
                bank.bandNow(2).q shouldBe (5.0 plusOrMinus 1e-12)
            }

            withClue("its gain falls linearly, at $done samples") {
                bank.bandNow(2).gain shouldBe (0.35 * (1.0 - done.toDouble() / len) plusOrMinus 1e-12)
            }

            withClue("it still runs while it fades") { bank.activeBands shouldBe 3 }

            bank.process(buf, n + done, n)
            done += n
        }

        withClue("landed: the band is gone from the loop") {
            bank.morphing shouldBe false
            bank.activeBands shouldBe 2
            bank.bandNow(2).gain shouldBe 0.0
        }
    }

    "a band the bank does not have arrives at its own frequency, from gain 0, and starts COLD" {
        // The slot has to have held something first, or "cold" would be vacuous: three bands, down
        // to two (band 2 fades out and its SVF keeps ringing), then back up to three.
        val buf = input(blocks = 4 + 2 * (len / n + 2))
        val bank = ResonatorBank(wood, sampleRate)
        var at = 0

        bank.process(buf, at, n)
        at += n
        bank.morphTo(wood.take(2))

        repeat(len / n + 1) {
            bank.process(buf, at, n)
            at += n
        }

        bank.activeBands shouldBe 2

        val arriving = wood.take(2) + band(1200.0, 30.0, 0.9)

        bank.morphTo(arriving)

        withClue("in place at its own frequency from the first sample, at gain 0") {
            bank.bandNow(2).freq shouldBe (1200.0 plusOrMinus 1e-9)
            bank.bandNow(2).q shouldBe (30.0 plusOrMinus 1e-12)
            bank.bandNow(2).gain shouldBe 0.0
        }

        // The oracle's band 2 is a FRESH SvfBPF at 1200 Hz: only a cold start can match it.
        val oracle = MorphOracle(wood, sampleRate, len)
        val expected = buf.copyOf()
        var oat = 0

        oracle.process(expected, oat, n)
        oat += n
        oracle.morphTo(wood.take(2))

        repeat(len / n + 1) {
            oracle.process(expected, oat, n)
            oat += n
        }

        oracle.morphTo(arriving)

        val actual = buf.copyOf()

        // Re-run the bank's history on its own copy so both walk the same input.
        run {
            val replay = ResonatorBank(wood, sampleRate)
            var a = 0

            replay.process(actual, a, n)
            a += n
            replay.morphTo(wood.take(2))

            repeat(len / n + 1) {
                replay.process(actual, a, n)
                a += n
            }

            replay.morphTo(arriving)

            repeat(len / n + 2) {
                replay.process(actual, a, n)
                oracle.process(expected, oat, n)
                a += n
                oat += n
            }
        }

        withClue("the arriving band's own state is a fresh one's") {
            actual.indices.maxOf { abs(actual[it] - expected[it]) } shouldBeLessThan 1e-12
        }
    }

    "the landing hands the FILTER the target itself, not its logarithm" {
        // The row below reads the bank's bookkeeping, which `morphTo` wrote; this one reads what
        // `process` handed the SVF, which is where the exact landing actually lives. Two oracles
        // that differ ONLY in the landing block are the instrument: one retunes from the un-logged
        // target (the decided law), the other from `exp(lerp)` at t = 1, an ulp away.
        //
        // The target has to be chosen, not assumed: `retune` turns the target into an INCREMENT,
        // `(target - current) / ramp`, and a one-ulp difference survives that division only about
        // a quarter of the time at a ramp of 128 (measured, Katalyst 5c-10). So probe candidates
        // until one lands observably, and fail loudly if none does rather than pass vacuously.
        val blocks = 4 + len / n + 12
        val signal = input(blocks)

        fun run(target: List<ResonatorBank.Band>, landExact: Boolean): DoubleArray {
            val out = signal.copyOf()
            val law = MorphOracle(wood, sampleRate, len, landExact = landExact)

            for (b in 0 until blocks) {
                if (b == 2) {
                    law.morphTo(target)
                }

                law.process(out, b * n, n)
            }

            return out
        }

        fun observable(target: List<ResonatorBank.Band>): Boolean {
            val a = run(target, landExact = true)
            val b = run(target, landExact = false)

            return a.indices.any { a[it] != b[it] }
        }

        val candidates = (1..60).map {
            listOf(band(200.0 + it * 37.0, 7.0 + it * 1.7, 1.2), band(1100.0 + it * 53.0, 40.0 + it * 0.9, 0.5))
        }
        val bent = candidates.firstOrNull { observable(it) }

        withClue("no candidate landing is observable: the row would be vacuous") {
            (bent != null) shouldBe true
        }

        val target = bent!!
        val exact = run(target, landExact = true)
        val logged = run(target, landExact = false)
        val got = signal.copyOf()
        val bank = ResonatorBank(wood, sampleRate)

        for (b in 0 until blocks) {
            if (b == 2) {
                bank.morphTo(target)
            }

            bank.process(got, b * n, n)
        }

        withClue("the bank lands the way the law says") {
            got.indices.maxOf { abs(got[it] - exact[it]) } shouldBeLessThan 1e-12
        }

        withClue("and the bank is NOT the one that landed through the logarithm") {
            got.indices.maxOf { abs(got[it] - logged[it]) } shouldBeGreaterThan 0.0
        }
    }

    "the landing is exact: the target's own numbers, and the morph stops costing anything" {
        val bank = ResonatorBank(wood, sampleRate)
        val buf = input(blocks = 4 + len / n + 4)

        bank.process(buf, 0, n)
        bank.morphTo(glass)

        var at = n
        var done = 0

        while (done < len) {
            bank.process(buf, at, n)
            at += n
            done += n
        }

        withClue("no block later than it must be") { bank.morphing shouldBe false }

        glass.indices.forEach { b ->
            withClue("band $b lands on the target itself") {
                bank.bandNow(b).freq.toRawBits() shouldBe glass[b].freq.toRawBits()
                bank.bandNow(b).q.toRawBits() shouldBe glass[b].q.toRawBits()
                bank.bandNow(b).gain.toRawBits() shouldBe glass[b].gain.toRawBits()
            }
        }
    }

    "a retarget mid-morph starts from where the bands STAND" {
        val bank = ResonatorBank(wood, sampleRate)
        val buf = input(blocks = 40)

        bank.process(buf, 0, n)
        bank.morphTo(glass)

        var at = n

        repeat(6) {
            bank.process(buf, at, n)
            at += n
        }

        val before = (0..2).map { bank.bandNow(it) }

        bank.morphTo(wood)

        val after = (0..2).map { bank.bandNow(it) }

        withClue("the retarget itself moves nothing") {
            // Not `shouldBe`: the values travel through `ln` and `exp`, whose last bit is the
            // platform's, and `commonTest` runs on JS as well as on the JVM.
            after.indices.forEach { b ->
                after[b].freq shouldBe (before[b].freq plusOrMinus 1e-9)
                after[b].q shouldBe (before[b].q plusOrMinus 1e-9)
                after[b].gain shouldBe (before[b].gain plusOrMinus 1e-12)
            }
        }

        // And it travels on from there: halfway to wood from where it stood, not from glass.
        var done = 0

        while (done < len / 2) {
            bank.process(buf, at, n)
            at += n
            done += n
        }

        val t = done.toDouble() / len

        withClue("band 0 travels from where it stood to 300 Hz") {
            bank.bandNow(0).freq shouldBe
                (exp(ln(before[0].freq) + (ln(300.0) - ln(before[0].freq)) * t) plusOrMinus 1e-9)
        }
    }

    "the first morph is instant, and only the first" {
        val fresh = ResonatorBank(wood, sampleRate)

        fresh.morphTo(glass) shouldBe true

        withClue("nothing has sounded, so there is nothing to be continuous with") {
            fresh.morphing shouldBe false
            fresh.bandNow(0).freq shouldBe 700.0
            fresh.bandNow(0).gain shouldBe 1.4
        }

        val buf = input(blocks = 4)

        fresh.process(buf, 0, n)
        fresh.morphTo(wood)

        withClue("after a block it glides, from where it stands") {
            fresh.morphing shouldBe true
            fresh.bandNow(0).freq shouldBe (700.0 plusOrMinus 1e-9)
        }
    }

    "a capacity below the band count is RAISED, never believed" {
        // `capacity` is a public constructor parameter: a caller that asks for less than the bands
        // it hands over must not make `process` read past the arrays on the audio thread. No
        // `require`, the Motor stays raw; the bank just takes the larger of the two.
        val tight = ResonatorBank(wood, sampleRate, capacity = 1)
        val loose = ResonatorBank(wood, sampleRate)

        tight.capacityBands shouldBe wood.size
        loose.capacityBands shouldBe ResonatorBank.MORPH_CAPACITY

        val signal = input(blocks = 12)
        val a = signal.copyOf()
        val b = signal.copyOf()

        for (block in 0 until 12) {
            tight.process(a, block * n, n)
            loose.process(b, block * n, n)
        }

        withClue("and it is the same bank either way") { a.toList() shouldBe b.toList() }

        withClue("what it cannot hold, it refuses") {
            tight.morphTo(wood + band(2500.0, 9.0, 0.2)) shouldBe false
            tight.morphTo(wood) shouldBe true
        }
    }

    "a target that does not fit the capacity is refused, and changes nothing" {
        val bank = ResonatorBank(wood, sampleRate, capacity = ResonatorBank.MORPH_CAPACITY)
        val buf = input(blocks = 4)

        bank.process(buf, 0, n)

        val eight = List(ResonatorBank.MORPH_CAPACITY) { band(200.0 + 100.0 * it, 7.0, 0.5) }

        withClue("exactly the capacity fits") { bank.morphTo(eight) shouldBe true }

        val nine = eight + band(1500.0, 7.0, 0.5)
        val standing = (0 until ResonatorBank.MORPH_CAPACITY).map { bank.bandNow(it) }
        val activeBefore = bank.activeBands

        withClue("one more does not") { bank.morphTo(nine) shouldBe false }

        withClue("and the refusal left the bank alone") {
            (0 until ResonatorBank.MORPH_CAPACITY).map { bank.bandNow(it) } shouldBe standing
            bank.activeBands shouldBe activeBefore
        }
    }

    "every shipped material and vowel fits the morph capacity" {
        BodyMaterials.names.forEachIndexed { i, name ->
            val modes = BodyMaterials.modesAt(i.toDouble())

            withClue("body $name has ${modes?.size} bands") {
                ((modes?.size ?: 0) <= ResonatorBank.MORPH_CAPACITY) shouldBe true
            }
        }

        VowelBands.names.forEachIndexed { i, name ->
            val bands = VowelBands.bandsAt(i.toDouble())

            withClue("vowel $name has ${bands?.size} bands") {
                ((bands?.size ?: 0) <= ResonatorBank.MORPH_CAPACITY) shouldBe true
            }
        }

        // Teeth: the tables are not all empty, and one of them really reaches the capacity.
        val widest = BodyMaterials.names.indices.maxOf { BodyMaterials.modesAt(it.toDouble())?.size ?: 0 }

        widest shouldBe ResonatorBank.MORPH_CAPACITY
    }

    "a non-finite band travels on the SVF's own clamps, never on a NaN" {
        val odd = listOf(band(Double.NaN, Double.NaN, 1.0), band(-5.0, 1e9, 0.5))
        val bank = ResonatorBank(odd, sampleRate)
        val buf = input(blocks = 40)

        bank.process(buf, 0, n)
        bank.morphTo(wood)

        withClue("the non-finite start reads as the SVF's substitute, not as NaN") {
            bank.bandNow(0).freq shouldBe (1000.0 plusOrMinus 1e-9)
            bank.bandNow(0).q shouldBe (sqrt(0.5) plusOrMinus 1e-12)
            bank.bandNow(1).freq shouldBe (5.0 plusOrMinus 1e-12)
            bank.bandNow(1).q shouldBe (200.0 plusOrMinus 1e-9)
        }

        var at = n

        repeat(len / n + 2) {
            bank.process(buf, at, n)
            at += n
        }

        withClue("and the whole trip stays finite, landing exactly on the target") {
            buf.all { it.isFinite() } shouldBe true
            bank.bandNow(0).freq.toRawBits() shouldBe 300.0.toRawBits()
        }
    }

    // ── The Q setter the morph needed (LowPassHighPassFilters.BaseSvf.retune) ─────────────────────
    //
    // `retune` and `resetState` exist for this morph and have no other caller, so they are pinned
    // here. The oracle is a filter CONSTRUCTED at the numbers, which is the only definition of
    // "arrived" that does not ask the setter about itself.

    "a snapped retune IS a filter built at those numbers, q included" {
        val signal = input(blocks = 12)
        val moved = LowPassHighPassFilters.SvfBPF(400.0, 5.0, sampleRate)

        // Give it a life at the old numbers first, so a setter that forgets q keeps something.
        moved.process(signal.copyOf(), 0, n * 4)
        moved.resetState()
        moved.retune(1200.0, 30.0, 0)

        val built = LowPassHighPassFilters.SvfBPF(1200.0, 30.0, sampleRate)
        val a = signal.copyOf()
        val b = signal.copyOf()

        moved.process(a, 0, a.size)
        built.process(b, 0, b.size)

        a.toList() shouldBe b.toList()

        // Teeth: the old q really is a different filter.
        val stale = LowPassHighPassFilters.SvfBPF(1200.0, 5.0, sampleRate)
        val c = signal.copyOf()

        stale.process(c, 0, c.size)
        a.indices.maxOf { abs(a[it] - c[it]) } shouldBeGreaterThan 0.01
    }

    "a ramped retune ARRIVES at those numbers once its ramp is spent" {
        val signal = input(blocks = 12)
        val moved = LowPassHighPassFilters.SvfBPF(400.0, 5.0, sampleRate)

        moved.retune(1200.0, 30.0, n)
        moved.process(signal.copyOf(), 0, n) // spend the ramp
        moved.resetState()

        val built = LowPassHighPassFilters.SvfBPF(1200.0, 30.0, sampleRate)
        val a = signal.copyOf()
        val b = signal.copyOf()

        moved.process(a, 0, a.size)
        built.process(b, 0, b.size)

        withClue("the ramp's last step lands on the built filter, bar its own rounding") {
            a.indices.maxOf { abs(a[it] - b[it]) } shouldBeLessThan 1e-9
        }

        // Teeth: an eighth of the way is measurably somewhere else.
        val half = LowPassHighPassFilters.SvfBPF(400.0, 5.0, sampleRate)

        half.retune(1200.0, 30.0, n * 8)
        half.process(signal.copyOf(), 0, n)
        half.resetState()

        val c = signal.copyOf()

        half.process(c, 0, c.size)
        a.indices.maxOf { abs(a[it] - c[it]) } shouldBeGreaterThan 0.01
    }

    "resetState zeroes the integrators and nothing else" {
        val signal = input(blocks = 12)
        val used = LowPassHighPassFilters.SvfBPF(900.0, 12.0, sampleRate)

        used.process(signal.copyOf(), 0, n * 5)
        used.resetState()

        val fresh = LowPassHighPassFilters.SvfBPF(900.0, 12.0, sampleRate)
        val a = signal.copyOf()
        val b = signal.copyOf()

        used.process(a, 0, a.size)
        fresh.process(b, 0, b.size)

        a.toList() shouldBe b.toList()

        // Teeth: without the reset the two are far apart.
        val ringing = LowPassHighPassFilters.SvfBPF(900.0, 12.0, sampleRate)

        ringing.process(signal.copyOf(), 0, n * 5)

        val c = signal.copyOf()

        ringing.process(c, 0, c.size)
        a.indices.maxOf { abs(a[it] - c[it]) } shouldBeGreaterThan 0.001
    }
})

/**
 * Test convenience: the [ResonatorBank.morphTo] door as a list of bands. Production passes three
 * preallocated arrays, because a material change may not allocate on the audio thread; a spec may.
 */
internal fun ResonatorBank.morphTo(target: List<ResonatorBank.Band>): Boolean = morphTo(
    DoubleArray(target.size) { target[it].freq },
    DoubleArray(target.size) { target[it].q },
    DoubleArray(target.size) { target[it].gain },
    target.size,
)

/**
 * The decided morph law, written from scratch: bare [LowPassHighPassFilters.SvfBPF] bands driven
 * BY HAND with the law's values and summed here. It is the ORACLE, so it copies the RULES
 * (pairing by position, log frequency and Q, linear gain per sample, a block-long coefficient
 * ramp, an exact landing, a cold arrival) and nothing of the bank's shape.
 */
private class MorphOracle(
    start: List<ResonatorBank.Band>,
    private val sr: Double,
    private val len: Int,
    /**
     * The decided law is `true`: the landing block retunes from the UN-LOGGED target, so the bank
     * settles on the material exactly. `false` is the same law landing through `exp(ln(x))`, one
     * ulp away, which the landing row uses to show that the difference is observable at all.
     */
    private val landExact: Boolean = true,
) {

    companion object {
        const val CAPACITY = 8

        fun freqOf(bands: List<ResonatorBank.Band>, i: Int, sr: Double): Double {
            val f = bands.getOrNull(i)?.freq ?: 1000.0

            return if (f.isFinite()) f.coerceIn(5.0, 0.5 * sr - 1.0) else 1000.0
        }

        fun qOf(bands: List<ResonatorBank.Band>, i: Int): Double {
            val q = bands.getOrNull(i)?.q ?: 0.7071067811865475

            return if (q.isFinite()) q.coerceIn(0.1, 200.0) else 0.7071067811865475
        }
    }

    private val svf = Array(CAPACITY) {
        LowPassHighPassFilters.SvfBPF(freqOf(start, it, sr), qOf(start, it), sr)
    }

    private val fFrom = DoubleArray(CAPACITY) { freqOf(start, it, sr) }
    private val fTo = DoubleArray(CAPACITY) { fFrom[it] }
    private val qFrom = DoubleArray(CAPACITY) { qOf(start, it) }
    private val qTo = DoubleArray(CAPACITY) { qFrom[it] }
    private val gFrom = DoubleArray(CAPACITY) { start.getOrNull(it)?.gain ?: 0.0 }
    private val gTo = DoubleArray(CAPACITY) { gFrom[it] }
    private val gNow = DoubleArray(CAPACITY) { gFrom[it] }

    private var active = start.size
    private var landing = start.size
    private var pos = len
    private var fresh = true

    private val inv = 1.0 / len

    private fun logAt(from: Double, to: Double, t: Double): Double = exp(ln(from) + (ln(to) - ln(from)) * t)

    private fun freqNow(b: Int): Double = if (pos < len) logAt(fFrom[b], fTo[b], pos * inv) else fTo[b]

    private fun qNow(b: Int): Double = if (pos < len) logAt(qFrom[b], qTo[b], pos * inv) else qTo[b]

    fun morphTo(target: List<ResonatorBank.Band>) {
        val union = maxOf(active, target.size)

        for (b in 0 until union) {
            val has = b < active
            val next = b < target.size
            val curF = if (has) freqNow(b) else freqOf(target, b, sr)
            val curQ = if (has) qNow(b) else qOf(target, b)
            val curG = if (has) gNow[b] else 0.0

            fTo[b] = if (next) freqOf(target, b, sr) else curF
            qTo[b] = if (next) qOf(target, b) else curQ
            gTo[b] = if (next) target[b].gain else 0.0

            if (!has) {
                svf[b].resetState()
            }

            if (fresh) {
                fFrom[b] = fTo[b]
                qFrom[b] = qTo[b]
                gFrom[b] = gTo[b]
                gNow[b] = gTo[b]
                svf[b].retune(fTo[b], qTo[b], 0)
            } else {
                fFrom[b] = curF
                qFrom[b] = curQ
                gFrom[b] = curG
                gNow[b] = curG

                if (!has) {
                    svf[b].retune(curF, curQ, 0)
                }
            }
        }

        active = if (fresh) target.size else union
        landing = target.size
        pos = if (fresh) len else 0
    }

    fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        fresh = false

        val dry = DoubleArray(length) { buffer[offset + it] }
        val sum = DoubleArray(length)
        val ramp = if (pos < len) minOf(length, len - pos) else 0
        val lands = pos + ramp >= len

        for (b in 0 until active) {
            if (ramp > 0) {
                val t = (pos + ramp) * inv

                if (lands && landExact) {
                    svf[b].retune(fTo[b], qTo[b], ramp)
                } else {
                    svf[b].retune(logAt(fFrom[b], fTo[b], t), logAt(qFrom[b], qTo[b], t), ramp)
                }
            }

            val band = dry.copyOf()

            svf[b].process(band, 0, length)

            for (i in 0 until length) {
                val g = if (ramp > 0) {
                    val left = maxOf(len - pos - i - 1, 0)

                    gTo[b] - (gTo[b] - gFrom[b]) * (left * inv)
                } else {
                    gNow[b]
                }

                sum[i] += band[i] * g
            }

            if (ramp > 0) {
                val left = maxOf(len - pos - length, 0)

                gNow[b] = gTo[b] - (gTo[b] - gFrom[b]) * (left * inv)
            }
        }

        for (i in 0 until length) {
            buffer[offset + i] = sum[i]
        }

        if (ramp > 0) {
            pos += ramp

            if (pos >= len) {
                for (b in 0 until active) {
                    fFrom[b] = fTo[b]
                    qFrom[b] = qTo[b]
                    gFrom[b] = gTo[b]
                }

                active = landing
            }
        }
    }
}
