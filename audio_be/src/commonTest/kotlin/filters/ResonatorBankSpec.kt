/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The resonator bank behind `body(...)` and `vowel(...)`, and the two rules that turn a catalogue row into a band.
 *
 * The bank rows compare RAW BITS against the plain law written HERE, per band and per sample: the TPT SVF recurrence
 * with the integrators flushed, the tap `k * v1` times the band's gain summed into `0.0` in band order, and the blend
 * `dry * dryGain + wet * wetGain` of the shared wet/dry law (p = 2), a mix at or below 0 leaving the buffer untouched
 * and the bands unrun. The coefficients come from [computeSvfCoeffs] after the SVF's own guards, the one formula the
 * engine has. A reassociated product or sum, a band order turned round, a bypass skipped: each is a different double
 * somewhere on the hostile source and goes red. A NaN compares by being NaN (its payload is no stable observable on
 * the JVM).
 */
class ResonatorBankSpec : StringSpec({

    val sampleRate = 48000.0

    /** The bits, or one marker for every NaN. */
    fun bits(x: Double): Long = if (x != x) 0x7ff8000000000000L else x.toRawBits() // NaN-guard

    /** A sweep plus a deterministic rattle, so every band rings, with the hostile values woven in. */
    fun hostile(length: Int): AudioBuffer = AudioBuffer(length) { i ->
        when (i % 397) {
            11 -> Double.NaN
            53 -> Double.POSITIVE_INFINITY
            97 -> Double.NEGATIVE_INFINITY
            151 -> 1e300
            199 -> 4.9e-324
            257 -> -0.0
            311 -> -1e300
            else -> {
                val t = i / sampleRate

                0.5 * sin(2.0 * PI * (80.0 + 20000.0 * t) * t) + 0.3 * sin(i * 0.731) * sin(i * 0.0137)
            }
        }
    }

    /** The block lengths a run walks through, ragged on purpose; a run starts each block at the next offset. */
    val ragged = listOf(128, 37, 128, 1, 90, 128, 64, 128, 0, 128, 77, 128)

    /** Runs [process] over [signal] in the ragged blocks, in place in a copy, and returns it. */
    fun run(signal: AudioBuffer, process: (buffer: AudioBuffer, offset: Int, length: Int) -> Unit): AudioBuffer {
        val buf = signal.copyOf()
        var offset = 0
        var b = 0

        while (offset < buf.size) {
            val length = minOf(ragged[b % ragged.size], buf.size - offset)

            process(buf, offset, length)
            offset += length
            b++
        }

        return buf
    }

    /**
     * THE LAW, written out: [rows] are (freq, q, gain) triples; each block runs every band from its own state over
     * the dry block, sums the taps into `0.0` in band order, and blends.
     */
    class Law(rows: List<Triple<Double, Double, Double>>, mix: Double, floor: Double, sampleRate: Double) {
        val a1 = DoubleArray(rows.size)
        val a2 = DoubleArray(rows.size)
        val a3 = DoubleArray(rows.size)
        val k = DoubleArray(rows.size)
        val gain = DoubleArray(rows.size) { rows[it].third }
        val ic1 = DoubleArray(rows.size)
        val ic2 = DoubleArray(rows.size)
        val w = if (mix.isFinite()) mix.coerceIn(0.0, 1.0) else 0.0
        val dryGain = WetDryMix.dryCoeff(w = w, floor = floor, p = 2)
        val wetGain = WetDryMix.wetCoeff(w, p = 2)

        init {
            val c = SvfCoeffs()

            for (b in rows.indices) {
                computeSvfCoeffs(
                    cutoffHz = clampSvfCutoff(cutoffHz = rows[b].first, sampleRate = sampleRate),
                    q = clampSvfQ(rows[b].second),
                    sampleRate = sampleRate,
                    out = c,
                )
                a1[b] = c.a1
                a2[b] = c.a2
                a3[b] = c.a3
                k[b] = c.k
            }
        }

        fun block(buffer: AudioBuffer, offset: Int, length: Int) {
            if (w <= 0.0) {
                return
            }

            val wet = DoubleArray(length) { 0.0 }

            for (b in a1.indices) {
                for (i in 0 until length) {
                    val v0 = buffer[offset + i]
                    val v3 = v0 - ic2[b]
                    val v1 = a1[b] * ic1[b] + a2[b] * v3
                    val v2 = ic2[b] + a2[b] * ic1[b] + a3[b] * v3

                    ic1[b] = (2.0 * v1 - ic1[b]).flushState()
                    ic2[b] = (2.0 * v2 - ic2[b]).flushState()
                    wet[i] = wet[i] + (k[b] * v1) * gain[b]
                }
            }

            for (i in 0 until length) {
                buffer[offset + i] = buffer[offset + i] * dryGain + wet[i] * wetGain
            }
        }
    }

    fun table(rows: List<Triple<Double, Double, Double>>): ResonatorTable =
        ResonatorTable.ofBody(rows.map { FilterDef.Body.Mode(freq = it.first, db = 20.0 * log10(it.third), q = it.second) })

    /** Bands whose gains are far apart, so the order of the sum is observable, and one with a hostile q and freq. */
    val rows = listOf(
        Triple(300.0, 12.0, 900.0),
        Triple(1900.0, 60.0, 0.35),
        Triple(5200.0, 4.0, 0.0007),
        Triple(30000.0, 500.0, 1.0),
    )

    /** How many samples of two renders differ by their bits. */
    fun differing(a: AudioBuffer, b: AudioBuffer): Int = a.indices.count { bits(a[it]) != bits(b[it]) }

    "the bank is the law, bit for bit, on a hostile source, ragged blocks, every mix and floor" {
        val signal = hostile(4000)
        // The table's gains, as the bank will read them (the dB round trip of `table` is the table's, not the law's).
        val t = table(rows)
        val tableRows = (0 until t.count).map { Triple(t.freq[it], t.q[it], t.gain[it]) }

        val cases = listOf(
            0.5 to 0.4,
            1.0 to 0.0,
            2.5 to 0.05, // the mix clamps to 1
            0.3 to Double.NaN, // a non-finite floor reads 0
            Double.NaN to 0.2, // a non-finite mix reads 0: the bypass
            0.0 to 0.4, // the bypass
            -0.5 to 0.4, // below 0: the bypass
            1e-300 to 0.4, // a mix that is not 0 runs the bands
        )

        for ((mix, floor) in cases) {
            val bank = ResonatorBank(capacity = t.count, sampleRate = sampleRate)
            val law = Law(rows = tableRows, mix = mix, floor = floor, sampleRate = sampleRate)

            bank.install(ResonatorConfig(table = t, mix = mix, floor = floor))

            val actual = run(signal) { buf, offset, length -> bank.process(buffer = buf, offset = offset, length = length) }
            val expected = run(signal) { buf, offset, length -> law.block(buffer = buf, offset = offset, length = length) }

            withClue("mix $mix, floor $floor") {
                differing(actual, expected) shouldBe 0
            }
        }
    }

    "the law can see the band order: summed back to front, the same bands give other doubles" {
        // The row above binds the order only where it shows on its source; pinned here so it cannot go quiet.
        val signal = hostile(4000)
        val t = table(rows)
        val tableRows = (0 until t.count).map { Triple(t.freq[it], t.q[it], t.gain[it]) }

        val lawForward = Law(rows = tableRows, mix = 0.7, floor = 0.4, sampleRate = sampleRate)
        val lawReversed = Law(rows = tableRows.reversed(), mix = 0.7, floor = 0.4, sampleRate = sampleRate)
        val f = run(signal) { buf, o, l -> lawForward.block(buffer = buf, offset = o, length = l) }
        val r = run(signal) { buf, o, l -> lawReversed.block(buffer = buf, offset = o, length = l) }

        differing(f, r) shouldBeGreaterThan 0
    }

    "an install makes a bank FRESH: a reconfigured bank renders as one built new on the same table" {
        // The pool's whole premise (engine tidy-up step 12 (a)): the stage reconfigures a bank nobody hears instead of
        // building one, and that is bit-identical only because install zeroes every band's state.
        val signal = hostile(3000)
        val first = table(listOf(Triple(200.0, 30.0, 4.0), Triple(800.0, 20.0, 2.0), Triple(2400.0, 10.0, 1.0)))
        val second = table(listOf(Triple(450.0, 40.0, 3.0), Triple(1300.0, 25.0, 1.5)))

        val reused = ResonatorBank(capacity = 3, sampleRate = sampleRate)

        reused.install(ResonatorConfig(table = first, mix = 0.8, floor = 0.3))
        run(signal) { buf, o, l -> reused.process(buffer = buf, offset = o, length = l) } // rings on the first table
        reused.install(ResonatorConfig(table = second, mix = 0.6, floor = 0.2))

        val fresh = ResonatorBank(capacity = 2, sampleRate = sampleRate)

        fresh.install(ResonatorConfig(table = second, mix = 0.6, floor = 0.2))

        val got = run(signal) { buf, o, l -> reused.process(buffer = buf, offset = o, length = l) }
        val want = run(signal) { buf, o, l -> fresh.process(buffer = buf, offset = o, length = l) }

        reused.count shouldBe 2
        differing(got, want) shouldBe 0
    }

    "a table larger than the capacity grows the bank once and renders as a fresh one" {
        val signal = hostile(2000)
        val big = table((0 until 12).map { Triple(150.0 * (it + 1), 8.0 + it, 1.0 / (it + 1)) })

        val small = ResonatorBank(capacity = 2, sampleRate = sampleRate)

        small.install(ResonatorConfig(table = table(listOf(Triple(500.0, 10.0, 1.0))), mix = 1.0, floor = 0.0))
        run(signal) { buf, o, l -> small.process(buffer = buf, offset = o, length = l) }
        small.install(ResonatorConfig(table = big, mix = 0.7, floor = 0.4))

        val fresh = ResonatorBank(capacity = 12, sampleRate = sampleRate)

        fresh.install(ResonatorConfig(table = big, mix = 0.7, floor = 0.4))

        small.capacity shouldBe 12
        small.count shouldBe 12
        differing(
            run(signal) { buf, o, l -> small.process(buffer = buf, offset = o, length = l) },
            run(signal) { buf, o, l -> fresh.process(buffer = buf, offset = o, length = l) },
        ) shouldBe 0
    }

    "a bank before its first install, and one with no bands, change nothing or blend over silence" {
        val signal = hostile(1000)
        val never = ResonatorBank(capacity = 4, sampleRate = sampleRate)

        differing(run(signal) { buf, o, l -> never.process(buffer = buf, offset = o, length = l) }, signal) shouldBe 0

        val empty = ResonatorBank(capacity = 4, sampleRate = sampleRate)
        val law = Law(rows = emptyList(), mix = 0.5, floor = 0.2, sampleRate = sampleRate)

        empty.install(ResonatorConfig(table = table(emptyList()), mix = 0.5, floor = 0.2))

        differing(
            run(signal) { buf, o, l -> empty.process(buffer = buf, offset = o, length = l) },
            run(signal) { buf, o, l -> law.block(buffer = buf, offset = o, length = l) },
        ) shouldBe 0
    }

    "the body rule: the gain is the plain dB factor, a non-finite dB is 0 dB, freq and q pass raw" {
        val plain = ResonatorTable.ofBody(listOf(FilterDef.Body.Mode(freq = 230.0, db = -3.0, q = 10.0)))

        plain.gain[0] shouldBe 10.0.pow(-3.0 / 20.0)
        plain.freq[0] shouldBe 230.0
        plain.q[0] shouldBe 10.0

        ResonatorTable.ofBody(listOf(FilterDef.Body.Mode(freq = 230.0, db = Double.NaN, q = 10.0))).gain[0] shouldBe 1.0
        ResonatorTable.ofBody(listOf(FilterDef.Body.Mode(freq = 230.0, db = Double.NEGATIVE_INFINITY, q = 10.0))).gain[0] shouldBe 1.0

        // The SVF clamps q itself; the body folds nothing, so an out-of-range q reaches it unchanged.
        ResonatorTable.ofBody(listOf(FilterDef.Body.Mode(freq = 230.0, db = 0.0, q = 500.0))).q[0] shouldBe 500.0
    }

    "the vowel rule: the dB factor times the clamped q times 0.05, in that order; the SVF gets the raw q" {
        fun vowel(freq: Double, db: Double, q: Double) = ResonatorTable.ofVowel(listOf(FilterDef.Formant.Band(freq = freq, db = db, q = q)))

        // (-3 dB, q 110) is a pair where the three associations of the product are three different
        // doubles, so a regrouped fold is a different number here, not just a different spelling.
        val band = vowel(freq = 730.0, db = -3.0, q = 110.0)

        band.gain[0] shouldBe 10.0.pow(-3.0 / 20.0) * 110.0 * 0.05
        band.gain[0] shouldNotBe 10.0.pow(-3.0 / 20.0) * (110.0 * 0.05)
        band.freq[0] shouldBe 730.0
        band.q[0] shouldBe 110.0

        // The fold uses the SVF's own clamp, [0.1, 200], and its own fallback for a non-finite q,
        // while the band hands the SVF the raw value.
        val high = vowel(freq = 730.0, db = 0.0, q = 500.0)

        high.gain[0] shouldBe 1.0 * 200.0 * 0.05
        high.q[0] shouldBe 500.0

        vowel(freq = 730.0, db = 0.0, q = 0.01).gain[0] shouldBe 1.0 * 0.1 * 0.05
        vowel(freq = 730.0, db = 0.0, q = Double.NaN).gain[0] shouldBe 1.0 * 0.7071067811865475 * 0.05

        // A non-finite dB is 0 dB.
        vowel(freq = 730.0, db = Double.NaN, q = 80.0).gain[0] shouldBe 1.0 * 80.0 * 0.05
    }

    "a band peaks at unity at its centre whatever its q, and passes little far from it" {
        // The SvfBPF rows this class absorbed (engine tidy-up step 12 (a)), on a one-band bank at full mix and no
        // floor, where the dry coefficient is cos(pi/2)^2, about 4e-33: the output is the band.
        fun peakThrough(centre: Double, q: Double, probe: Double): Double {
            val bank = ResonatorBank(capacity = 1, sampleRate = sampleRate)

            bank.install(ResonatorConfig(table = table(listOf(Triple(centre, q, 1.0))), mix = 1.0, floor = 0.0))

            val buf = AudioBuffer(128)
            var peak = 0.0

            for (b in 0 until 80) {
                for (i in 0 until 128) {
                    buf[i] = sin(2.0 * PI * probe * (b * 128 + i) / sampleRate)
                }

                bank.process(buffer = buf, offset = 0, length = 128)

                if (b >= 60) {
                    peak = maxOf(peak, buf.maxOf { abs(it) })
                }
            }

            return peak
        }

        (abs(peakThrough(centre = 1000.0, q = 4.0, probe = 1000.0) - 1.0) < 0.02) shouldBe true
        (abs(peakThrough(centre = 1000.0, q = 0.5, probe = 1000.0) - 1.0) < 0.02) shouldBe true
        (peakThrough(centre = 5000.0, q = 2.0, probe = 100.0) < 0.1) shouldBe true
        (peakThrough(centre = 500.0, q = 2.0, probe = 15000.0) < 0.1) shouldBe true
        // The cutoff guards: 5 Hz holds a 1 kHz sine down, a NaN cutoff is 1 kHz, a cutoff past Nyquist is clamped.
        (peakThrough(centre = 5.0, q = 1.0, probe = 1000.0) < 0.1) shouldBe true
        (abs(peakThrough(centre = Double.NaN, q = 4.0, probe = 1000.0) - 1.0) < 0.02) shouldBe true
        peakThrough(centre = 1e9, q = 1.0, probe = 1000.0).isFinite() shouldBe true
    }

    "the body bank boosts on a mode AND keeps the rest at the floor (it never thins)" {
        // From the parallel-mix wrapper's spec, which this class absorbed (engine tidy-up step 12 (a)); the blend law
        // itself is the law row above and `WetDryMixSpec`'s. db = 0 on the 430 mode: the wet is about unity there.
        val sr = 44100.0
        val frames = 4096
        val modes = listOf(
            FilterDef.Body.Mode(freq = 110.0, db = 2.0, q = 12.0),
            FilterDef.Body.Mode(freq = 230.0, db = 1.0, q = 10.0),
            FilterDef.Body.Mode(freq = 430.0, db = 0.0, q = 9.0),
            FilterDef.Body.Mode(freq = 820.0, db = -2.0, q = 7.0),
            FilterDef.Body.Mode(freq = 1500.0, db = -4.0, q = 5.0),
        )

        fun rms(buf: AudioBuffer): Double = sqrt(buf.fold(0.0) { acc, v -> acc + v * v } / buf.size)

        fun through(freq: Double): Pair<Double, Double> {
            val buf = AudioBuffer(frames) { i -> sin(2.0 * PI * freq * i / sr) }
            val before = rms(buf)
            val bank = ResonatorBank(capacity = modes.size, sampleRate = sr, blockFrames = frames)

            bank.install(ResonatorConfig(table = ResonatorTable.ofBody(modes), mix = 1.0, floor = 0.6))
            bank.process(buffer = buf, offset = 0, length = frames)

            return before to rms(buf)
        }

        val (inOn, outOn) = through(430.0)
        val (inOff, outOff) = through(12000.0)

        // On a mode: floor * dry + resonance, boosted above the input.
        (outOn > inOn * 1.2) shouldBe true
        // Off every mode: the wet is about 0, so out is about floor * dry. Attenuated to the floor, never below it.
        (outOff > inOff * 0.5) shouldBe true
        (outOff < inOff * 0.75) shouldBe true
    }

    "a cutoff at or past Nyquist is clamped to Nyquist - 1 Hz: finite, and the clamp's own band bit for bit" {
        // From the `SvfBPF` rows this class absorbed (engine tidy-up step 12 (a)).
        val nyquist = sampleRate / 2.0
        val signal = AudioBuffer(4096) { sin(2.0 * PI * 1000.0 * it / sampleRate) }

        fun render(cutoff: Double): AudioBuffer {
            val bank = ResonatorBank(capacity = 1, sampleRate = sampleRate)

            bank.install(ResonatorConfig(table = table(listOf(Triple(cutoff, 1.0, 1.0))), mix = 1.0, floor = 0.0))

            return run(signal) { buf, o, l -> bank.process(buffer = buf, offset = o, length = l) }
        }

        val clamped = render(nyquist - 1.0)

        for (cutoff in listOf(nyquist, sampleRate, 1e9)) {
            val got = render(cutoff)

            withClue("cutoff $cutoff") {
                got.all { it.isFinite() } shouldBe true
                differing(got, clamped) shouldBe 0
            }
        }
    }
})
