/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIVE_PER_ANALOG
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Guards for [AnalogDrift] / [DriftLanes] stability and the `analog` → cents budget.
 *
 * Two perceptual properties:
 *  1. **In-tune attack** — a note must START centred (multiplier ≈ 1.0). The slow layer is
 *     seeded at centre precisely so short / melodic notes don't inherit a per-note random
 *     detune. Only the tiny fast layer is present at attack.
 *  2. **No runaway** — over millions of steps the multiplier stays centred on 1.0 with a
 *     bounded excursion. Both layers are stable AR(1); neither is a pure random walk, so it
 *     cannot drift away from centre.
 *
 * Plus a budget guard so the `analog` → cents mapping can't be silently cranked back up.
 */
class AnalogDriftSpec : StringSpec({

    val sr = 48_000

    // Multiplier (centred on 1.0) → cents away from centre.
    fun cents(mult: Double): Double = 1200.0 * log2(mult)

    "attack is in tune - slow layer seeded at centre (mono)" {
        // First multiplier across many seeds: centred, and only the fast layer is present
        // (~±0.2·analog cents). If the slow layer were seeded at steady-state (the old
        // behaviour) the spread would be ~4× larger and notes would start audibly detuned.
        val analog = 8.0
        val n = 20_000
        var sum = 0.0
        var maxAbs = 0.0
        for (i in 0 until n) {
            val first = cents(AnalogDrift(analog, sr, Random(i + 1)).nextMultiplier())
            sum += first
            val a = abs(first); if (a > maxAbs) maxAbs = a
        }
        (sum / n) shouldBe (0.0 plusOrMinus 0.1)        // centred at attack
        // fast-only peak ≈ 0.2·8 = 1.6 cents; allow tail headroom but stay well under the
        // fast+slow peak (~8 cents) the old steady-state seeding would have produced.
        maxAbs shouldBeLessThan 3.5
    }

    "attack is in tune - slow layer seeded at centre (every lane of a unison stack)" {
        val lanes = DriftLanes(analog = 8.0, stepRate = sr, rng = Random(1))
        val voices = 16

        lanes.ensureLanes(voices)

        // The multiplier a lane's first block STARTS at, the way an adopter's ramp reads it.
        fun start(lane: Int): Double {
            lanes.advanceLane(lane)

            return lanes.startOf(lane)
        }

        // The shared lane first: at spread 0 it is the only walk the whole stack hears.
        lanes.prepareBlock(0.0)
        abs(cents(start(0))) shouldBeLessThan 3.5

        // Then every own lane, which is what spread 1 (the default) hands each voice.
        lanes.prepareBlock(1.0)

        var sum = 0.0

        for (n in 0 until voices) {
            val c = cents(start(n))

            abs(c) shouldBeLessThan 3.5
            sum += c
        }

        (sum / voices) shouldBe (0.0 plusOrMinus 0.6)
    }

    "the statistics at the block rate are the statistics at the sample rate: same depth in cents" {
        // The lanes step at the block rate since 2026-09-15; the coefficients follow the rate, so
        // the realised depth must match the design's budget at both rates: 3 sigma = analog cents
        // (fast 0.2 + slow 0.8 per unit analog, independent layers). Long runs at both rates, the
        // RMS in cents against sqrt(0.2² + 0.8²) · analog / 3.
        val analog = 8.0
        val budgetRms = sqrt(ANALOG_FAST_PEAK_CENTS * ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS * ANALOG_SLOW_PEAK_CENTS) * analog / ANALOG_PEAK_SIGMAS

        for ((rate, seconds) in listOf(375 to 3600, 48_000 to 600)) {
            val d = AnalogDrift(analog = analog, stepRate = rate, rng = Random(11))
            val n = rate * seconds
            // let the slow layer settle from its centre seed before measuring (a few time constants)
            repeat(rate * 60) { d.nextMultiplier() }

            var sumSq = 0.0

            repeat(n) {
                val c = cents(d.nextMultiplier())

                sumSq += c * c
            }

            val rms = sqrt(sumSq / n)

            // The slow layer's 10 s time constant leaves the RMS of a 3600 s run within a few percent
            // of its expectation; the 600 s run at 48 kHz has fewer slow-layer periods, so a wider band.
            val tolerance = if (rate == 375) 0.08 else 0.15

            withClue("rate $rate: rms $rms cents vs budget $budgetRms") { rms shouldBe (budgetRms plusOrMinus budgetRms * tolerance) }
        }
    }

    "the coefficients' steady-state sigma is the recurrence's, at the block rate and the sample rate" {
        // The output scales normalise each layer by its steady-state sigma, so the depth in cents
        // is only right if that sigma IS the recurrence's. The recurrences from the class KDoc,
        // driven by uniform [-1, 1] noise, against `AnalogDriftCoeffs`: the exact AR(1) forms
        // (2026-09-15; the small-alpha approximations were 1.4 percent off at the block rate).
        // The slow layer at the sample rate has a 3.2e5-step correlation length and is left out.
        fun realisedSigma(steps: Int, correlationSteps: Int, next: (Double) -> Double): Double {
            var y = 0.0

            repeat(correlationSteps * 20) { y = next(y) }

            var sumSq = 0.0

            repeat(steps) {
                y = next(y)
                sumSq += y * y
            }

            return sqrt(sumSq / steps)
        }

        for (rate in listOf(375, 48_000)) {
            val c = AnalogDriftCoeffs(8.0, rate)
            val rng = Random(5)
            val fast = realisedSigma(20_000_000, (1.0 / c.alphaFast).toInt()) { y ->
                y + c.alphaFast * ((rng.nextDouble() * 2.0 - 1.0) - y)
            }

            withClue("rate $rate: fast layer sigma $fast vs ${c.sigmaYFast}") { fast shouldBe (c.sigmaYFast plusOrMinus c.sigmaYFast * 0.03) }
        }

        val c = AnalogDriftCoeffs(8.0, 375)
        val rng = Random(6)
        val slow = realisedSigma(20_000_000, (1.0 / (c.alphaSlow + c.betaSlow)).toInt()) { y ->
            y + c.alphaSlow * ((rng.nextDouble() * 2.0 - 1.0) - y) - c.betaSlow * y
        }

        withClue("slow layer sigma $slow vs ${c.sigmaYSlow}") { slow shouldBe (c.sigmaYSlow plusOrMinus c.sigmaYSlow * 0.03) }
    }

    "does not run away - centred and bounded over millions of steps" {
        val d = AnalogDrift(analog = 8.0, stepRate = sr, rng = Random(42))
        val n = 2_000_000
        var sum = 0.0
        var maxAbs = 0.0
        for (i in 0 until n) {
            val c = cents(d.nextMultiplier())
            sum += c
            val a = abs(c); if (a > maxAbs) maxAbs = a
        }
        // A pure random walk would reach hundreds/thousands of cents; a stable
        // mean-reverting process stays small. Loose bounds catch divergence robustly.
        (sum / n) shouldBe (0.0 plusOrMinus 2.0)
        maxAbs shouldBeLessThan 25.0
    }

    "analog cents budget stays tamed at analog=3 (Der Schmetterling)" {
        val analog = 3.0
        val oscPeak = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * analog
        // Read through StageDsl.Filter(), NOT the bare constants. The constants are the DEFAULTS;
        // what the engine consumes is the stage field (VoiceFactory.kt:371,430). Reading the
        // constant directly is how this guard went blind in the first place (audit F2) — it would
        // stay green if someone replaced a DSL default with a literal.
        val stage = StageDsl.Filter()
        val filterOffsetPeak = cents(1.0 + stage.cutoffOffsetPerAnalog * analog)
        val filterDriftPeak = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * analog * stage.driftRelToOsc

        // Post-tuning ceilings, deliberately close to the shipped values.
        //
        // Retightened 2026-08-11 when the filter constants moved to audio_bridge: they had been
        // lowered (0.001→0.0002, 2.5→0.25) without the bounds following, leaving 6x and 12x of
        // slack — a ceiling that far above the value guards nothing.
        //
        // These are a TUNING RECORD, not a regression alarm. A deliberate retune is expected to
        // trip them: raising `driftRelToOsc` past 0.5 (the ratio-inversion experiment in
        // docs/tasks/audio-bridge-constants.md §6) breaks `filterDriftPeak` by design. That is the
        // intended workflow — move the bound WITH the value and say why, don't widen it in advance.
        // drivePerAnalog has no cents budget (it scales filter damping, not pitch), so it gets a
        // flat pin instead.
        //
        // It needs one because the incidental coverage is lopsided: IgnitorCombinatorsSpec's
        // compression guard is ONE-SIDED (raising the drive makes it greener), and of the specs
        // that ride the value indirectly only LowPassHighPassFiltersSpec:718's DC-purity bound
        // reacts upward — and not until drive ≈ 1.5. So a modest crank, 0.25 -> 0.5, was caught
        // by nothing at all. This is the constant whose divergence caused the whole
        // de-duplication, and it is the live-tuning target.
        // BOTH paths, because they are separately reachable. `stage.drivePerAnalog` is what the
        // pipeline filter consumes; `FILTER_DRIVE_PER_ANALOG` is what IgnitorFilters.kt:119 reads
        // directly — it never sees a StageDsl.Filter. Asserting only the stage would pass while a
        // literal in PipelineDsl.Filter plus a retuned constant silently reinstated the very
        // ignitor-vs-pipeline split this de-duplication was opened to fix (§1.2).
        stage.drivePerAnalog shouldBe 0.25
        FILTER_DRIVE_PER_ANALOG shouldBe 0.25

        oscPeak shouldBeLessThan 3.5             // ±3 cents pitch (unchanged since 06-17)
        filterOffsetPeak shouldBeLessThan 2.0    // ±~1 cent  (was ±5 at 0.001, ±15 at 0.003)
        filterDriftPeak shouldBeLessThan 1.5     // ±0.75 cents (was ±7.5 at 2.5, ±15 at 5.0)

        // Eyeball table across the range people actually use (analog 1–8).
        for (a in listOf(1.0, 2.0, 3.0, 5.0, 8.0)) {
            val osc = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * a
            val off = cents(1.0 + stage.cutoffOffsetPerAnalog * a)
            val drf = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * a * stage.driftRelToOsc
            println("analog=$a  oscPitch=±${osc}c  filterOffset=±${off}c  filterDrift=±${drf}c")
        }
    }
})