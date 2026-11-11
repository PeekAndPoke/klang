/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * The aliasing meter's MEASURING functions ([measureAliasing], [fftInPlace], [windowPeakSidelobeDb]) on synthetic
 * signals whose answers are known by hand: a pure sine (the floor), a sine with a harmonic and an inharmonic component
 * at known levels, a naive unbandlimited saw and a full-wave rectified sine whose aliases are the folded Fourier series,
 * a DC offset, a fundamental away from the pitch asked for, and the FFT against a direct DFT. Every oracle is written from its signal's definition,
 * never from the meter. Fast, and asserts nothing about an engine stage.
 */
class AliasingMeterSpec : StringSpec({

    val config = AliasingConfig()
    val sr = config.sampleRate.toDouble()
    val frames = config.framesNeeded

    fun tone(vararg parts: Pair<Double, Double>, dc: Double = 0.0): DoubleArray = DoubleArray(frames) { n ->
        var x = dc

        for ((hz, amplitude) in parts) {
            x += amplitude * sin(2.0 * PI * hz * n / sr)
        }

        x
    }

    "the FFT equals a direct DFT on a random signal of 64 points" {
        val rng = Random(42)
        val x = DoubleArray(64) { rng.nextDouble() * 2.0 - 1.0 }
        val re = x.copyOf()
        val im = DoubleArray(64)

        fftInPlace(re, im)

        for (k in 0 until 64) {
            var dr = 0.0
            var di = 0.0

            for (n in 0 until 64) {
                dr += x[n] * cos(2.0 * PI * k * n / 64)
                di -= x[n] * sin(2.0 * PI * k * n / 64)
            }

            withClue("fft bin $k against the direct DFT") {
                re[k] shouldBe (dr plusOrMinus 1e-12)
                im[k] shouldBe (di plusOrMinus 1e-12)
            }
        }
    }

    "the window: its highest sidelobe past the main lobe sits below -170 dB" {
        withClue("window sidelobe") {
            windowPeakSidelobeDb() shouldBeLessThan -170.0
        }
    }

    "a pure sine reads its own level, its own pitch, and the meter's floor everywhere else" {
        val r = measureAliasing(samples = tone(1244.5 to 0.5), nominalHz = 1244.5, config = config)

        withClue("pure sine level") { r.fundamentalDbfs shouldBe ((20.0 * log10(0.5)) plusOrMinus 0.001) }
        withClue("pure sine pitch") { r.fundamentalHz shouldBe (1244.5 plusOrMinus 0.001) }
        withClue("pure sine harmonics") { r.harmonicDb shouldBeLessThan -150.0 }
        withClue("pure sine floor") { r.nonHarmonicDb shouldBeLessThan -150.0 }
        withClue("pure sine harmonic count") { r.harmonicCount shouldBe 19 }
    }

    "a sine with a harmonic at -20 dB and an inharmonic part at -60 dB reads both, each in its own sum" {
        val r = measureAliasing(samples = tone(1244.5 to 0.5, 2489.0 to 0.05, 3517.7 to 0.0005), nominalHz = 1244.5, config = config)

        withClue("known harmonic") { r.harmonicDb shouldBe (-20.0 plusOrMinus 0.001) }
        withClue("known inharmonic total") { r.nonHarmonicDb shouldBe (-60.0 plusOrMinus 0.001) }
        withClue("known inharmonic absolute") { r.nonHarmonicDbfs shouldBe ((20.0 * log10(0.0005)) plusOrMinus 0.001) }
        withClue("known inharmonic strongest frequency") { r.strongestHz shouldBe (3517.7 plusOrMinus 0.01) }
        withClue("known inharmonic strongest level") { r.strongestDb shouldBe (-60.0 plusOrMinus 0.001) }
    }

    /** A periodic signal's spectrum on the meter's terms, from its Fourier series: what lands in the bands, what folds outside. */
    class Folded(val harmonicPower: Double, val aliasPower: Double, val topPower: Double, val topHz: Double)

    /**
     * Folds the Fourier series of a signal at [f] whose component m (at m * f) has the peak [amplitude] (m >= 1) into
     * 0 .. Nyquist: a component below Nyquist is a harmonic; one above lands where it folds to, and counts as harmonic
     * only when it lands within the meter's band of a harmonic below Nyquist (or of DC). Written from sampling theory,
     * not from the meter.
     */
    fun folded(f: Double, amplitude: (Int) -> Double): Folded {
        val bandHz = config.bandBins * config.binHz
        val nyquist = sr / 2.0
        var harmonicPower = 0.0
        var aliasPower = 0.0
        var topPower = 0.0
        var topHz = 0.0

        for (m in 1..2_000_000) {
            val a = amplitude(m)
            val power = a * a / 2.0
            val hz = m * f

            if (hz < nyquist) {
                harmonicPower += power

                continue
            }

            var at = hz % sr

            if (at > nyquist) {
                at = sr - at
            }

            val nearest = round(at / f)
            val nearestHz = nearest * f
            val inBand = if (nearest == 0.0) at <= bandHz else (nearestHz < nyquist && abs(at - nearestHz) <= bandHz)

            if (inBand) {
                continue
            }

            aliasPower += power

            if (power > topPower) {
                topPower = power
                topHz = at
            }
        }

        return Folded(harmonicPower = harmonicPower, aliasPower = aliasPower, topPower = topPower, topHz = topHz)
    }

    "a naive saw: its non-harmonic total and its strongest alias are the folded Fourier series" {
        val f = 1244.5
        // x[n] = 2 frac(n f / sr) - 1 = -(2 / pi) sum_m sin(2 pi m f t) / m: harmonic m has peak 2 / (pi m).
        val saw = DoubleArray(frames) { n ->
            val p = n * f / sr

            2.0 * (p - floor(p)) - 1.0
        }
        val oracle = folded(f) { m -> 2.0 / (PI * m) }
        val fundamentalPower = (2.0 / PI) * (2.0 / PI) / 2.0
        val r = measureAliasing(samples = saw, nominalHz = f, config = config)

        withClue("naive saw alias total") { r.nonHarmonicDb shouldBe ((10.0 * log10(oracle.aliasPower / fundamentalPower)) plusOrMinus 0.01) }
        withClue("naive saw strongest alias frequency") { r.strongestHz shouldBe (oracle.topHz plusOrMinus 0.01) }
        withClue("naive saw strongest alias level") { r.strongestDb shouldBe ((10.0 * log10(oracle.topPower / fundamentalPower)) plusOrMinus 0.01) }
    }

    "a full-wave rectified sine, its grid on the given pitch: even harmonics only, read re all harmonics" {
        val f = 1244.5
        // |A sin(w t)| = 2A / pi - (4A / pi) sum_k cos(2 k w t) / (4 k^2 - 1): component m = 2k has peak 4A / (pi (m^2 - 1)).
        val rectified = DoubleArray(frames) { n -> abs(0.5 * sin(2.0 * PI * f * n / sr)) }
        val oracle = folded(f) { m -> if (m % 2 == 0) 4.0 * 0.5 / (PI * (m.toDouble() * m - 1.0)) else 0.0 }
        val r = measureAliasing(samples = rectified, nominalHz = f, config = config, findPitch = false)

        withClue("rectifier grid") { r.fundamentalHz shouldBe f }
        withClue("rectifier fundamental absent (only leakage of its neighbours)") { r.fundamentalDbfs shouldBeLessThan -120.0 }
        withClue("rectifier harmonics") { r.harmonicsDbfs shouldBe ((10.0 * log10(2.0 * oracle.harmonicPower)) plusOrMinus 0.01) }
        withClue("rectifier aliases re harmonics") {
            r.nonHarmonicReHarmonicsDb shouldBe ((10.0 * log10(oracle.aliasPower / oracle.harmonicPower)) plusOrMinus 0.01)
        }
        withClue("rectifier dc") { r.dcDbfs shouldBe ((20.0 * log10(2.0 * 0.5 / PI)) plusOrMinus 0.001) }
    }

    "DC is its own band: an offset reads as DC and adds nothing to the non-harmonic sum" {
        val r = measureAliasing(samples = tone(1244.5 to 0.5, dc = 0.1), nominalHz = 1244.5, config = config)

        withClue("dc level") { r.dcDbfs shouldBe (-20.0 plusOrMinus 0.001) }
        withClue("dc not in the non-harmonic sum") { r.nonHarmonicDb shouldBeLessThan -150.0 }
    }

    "the fundamental is found where it is, not where it was asked for, and its harmonics' bands follow it" {
        val r = measureAliasing(samples = tone(1244.5 to 0.5, 2489.0 to 0.05), nominalHz = 1240.0, config = config)

        withClue("found fundamental") { r.fundamentalHz shouldBe (1244.5 plusOrMinus 0.001) }
        withClue("found fundamental offset") { r.offsetCents shouldBe ((1200.0 * log2(1244.5 / 1240.0)) plusOrMinus 0.002) }
        withClue("found fundamental harmonic band") { r.harmonicDb shouldBe (-20.0 plusOrMinus 0.001) }
        withClue("found fundamental floor") { r.nonHarmonicDb shouldBeLessThan -150.0 }
    }

    "the sweep's pitches: about 100 Hz to 14.2 kHz, ascending, each a whole number plus 0.382 into the rate" {
        val pitches = aliasingSweepPitches()

        withClue("ascending") { pitches shouldBe pitches.sorted() }
        for (f in pitches) {
            val n = sr / f

            withClue("$f Hz: fs / f = $n") { (n - floor(n)) shouldBe (0.382 plusOrMinus 1e-9) }
        }

        withClue("bottom") { pitches.first() shouldBe (100.0 plusOrMinus 10.0) }
        withClue("top") { pitches.last() shouldBe (sr / 3.382 plusOrMinus 1e-9) }
    }

    "the healthy limit: the last pitch before the aliasing below 5 kHz reaches -60 dB re the harmonics; above 5 kHz does not count" {
        // At each pitch f: a sine at f (0.5), an inharmonic part at f / 2 whose level re the sine is -80 dB + 40 log10(f / 1 kHz)
        // (it crosses -60 dB at 3162 Hz), and a part at 20 kHz at -20 dB that the 5 kHz band must not count. And a pure sine.
        fun case(name: String, f: Double, render: () -> DoubleArray) =
            AliasingCase(family = "test", stage = name, knobs = "", pitchHz = f, kind = AliasingKind.MEASURED, note = "", findPitch = false, render = render)

        val pitches = aliasingSweepPitches()
        val rows = measureAliasingLimits(pitches = pitches, config = config) { f ->
            val alias = 0.5 * Math.pow(10.0, (-80.0 + 40.0 * log10(f / 1000.0)) / 20.0)

            listOf(
                case("rising alias", f) { tone(f to 0.5, f / 2.0 to alias, 20000.0 to 0.05) },
                case("pure sine", f) { tone(f to 0.5) },
            )
        }
        val rising = rows.single { it.stage == "rising alias" }
        val pure = rows.single { it.stage == "pure sine" }
        val crossing = 1000.0 * Math.pow(10.0, 20.0 / 40.0)

        withClue("rising alias: the limit is the last swept pitch under the crossing") {
            rising.healthyToHz shouldBe pitches.last { it < crossing }
            rising.firstUnhealthyHz shouldBe pitches.first { it >= crossing }
            rising.clean shouldBe false
        }
        withClue("rising alias: the level at the limit is its formula (the 20 kHz part not counted)") {
            val at = rising.levels.single { it.first == rising.healthyToHz }

            at.second shouldBe ((-80.0 + 40.0 * log10(at.first / 1000.0)) plusOrMinus 0.1)
        }
        withClue("pure sine: clean to the top") {
            pure.clean shouldBe true
            pure.healthyToHz shouldBe pitches.last()
        }
    }
})
