/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIVE_PER_ANALOG
import kotlin.math.abs
import kotlin.math.log2
import kotlin.random.Random

/**
 * Guards for [AnalogDrift] / [PolyAnalogDrift] stability and the `analog` → cents budget.
 *
 * Two perceptual properties:
 *  1. **In-tune attack** — a note must START centred (multiplier ≈ 1.0). The slow layer is
 *     seeded at centre precisely so short / melodic notes don't inherit a per-note random
 *     detune. Only the tiny fast layer is present at attack.
 *  2. **No runaway** — over millions of samples the multiplier stays centred on 1.0 with a
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

    "attack is in tune - slow layer seeded at centre (poly / unison)" {
        val drift = PolyAnalogDrift(analog = 8.0, voiceCount = 16, sampleRate = sr, rng = Random(1))
        drift.advanceAll()
        var sum = 0.0
        for (m in drift.multipliers) {
            val c = cents(m)
            abs(c) shouldBeLessThan 3.5
            sum += c
        }
        (sum / drift.multipliers.size) shouldBe (0.0 plusOrMinus 0.6)
    }

    "does not run away - centred and bounded over millions of samples" {
        val d = AnalogDrift(analog = 8.0, sampleRate = sr, rng = Random(42))
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