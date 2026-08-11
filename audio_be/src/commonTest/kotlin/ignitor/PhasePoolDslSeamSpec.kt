/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * End-to-end seam guard for the phase-pool knobs: DSL node → `IgnitorDslRuntime.buildRaw`
 * forwarding → `Ignitors.*` factory → shared engine. This is the ONE path real users take
 * (`Osc.supersine().phasePool(1)`), and dropping a `phasePool = phasePool` forwarding in a
 * `buildRaw` branch compiles cleanly (the factory default fills in) while turning the knob into a
 * permanent silent no-op — no other spec would notice (`PhasePoolSpec` calls the factories
 * directly; the KlangScript specs stop at DSL object equality; the codec spec stops at the wire).
 *
 * The runtime does not thread an rng (production uses `Random.Default`), so this is statistical:
 * with the pool ON at a high band ([0.85, 0.95], 64 tries) the mean fundamental across notes sits
 * ≈ 0.63, versus ≈ 0.27 for honest random draws — an ~8σ separation at 60 notes per side, immune
 * to seed luck for any practical purpose. Three probes per node, each killing a different dropped
 * forwarding: on-vs-off (phasePool), deep-vs-shallow at the same band (drawTries — both sides
 * collapse to the family default when un-threaded), and high-band-vs-low-band at the same depth
 * (kMin/kMax). Measured minimum ratios over 20k simulated runs: 1.76 / 1.84 / 7.76 against
 * thresholds 1.5 / 1.5 / 3.0.
 */
class PhasePoolDslSeamSpec : StringSpec({

    val sampleRate = 48000
    val n = 512 // exactly 4 periods of 375 Hz at 48 kHz — leakage-free fundamental bin
    val freqHz = 375.0
    val notes = 60

    fun fundamentalAmp(dsl: IgnitorDsl): Double {
        val buffer = AudioBuffer(n)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = n,
            voiceEndFrame = sampleRate + n,
            scratchBuffers = ScratchBuffers(n),
        ).apply { offset = 0; length = n; voiceElapsedFrames = 0 }
        dsl.toExciter().generate(buffer, freqHz, ctx)
        var re = 0.0
        var im = 0.0
        val w = TWO_PI * freqHz / sampleRate
        for (i in 0 until n) {
            re += buffer[i] * cos(w * i)
            im += buffer[i] * sin(w * i)
        }
        return 2.0 * sqrt(re * re + im * im) / n
    }

    val voices = IgnitorDsl.Constant(11.0)
    val spread = IgnitorDsl.Constant(0.0)
    val analog = IgnitorDsl.Constant(0.0)

    // (phasePool, kMin, kMax, drawTries) -> node, per super type.
    val nodes = listOf<Pair<String, (Double, Double, Double, Double) -> IgnitorDsl>>(
        "SuperSaw" to { pool, lo, hi, tries ->
            IgnitorDsl.SuperSaw(
                voices = voices, spread = spread, analog = analog,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
            )
        },
        "SuperRamp" to { pool, lo, hi, tries ->
            IgnitorDsl.SuperRamp(
                voices = voices, spread = spread, analog = analog,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
            )
        },
        "SuperSquare" to { pool, lo, hi, tries ->
            IgnitorDsl.SuperSquare(
                voices = voices, spread = spread, analog = analog,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
            )
        },
        "SuperTri" to { pool, lo, hi, tries ->
            IgnitorDsl.SuperTri(
                voices = voices, spread = spread, analog = analog,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
            )
        },
        "SuperSine" to { pool, lo, hi, tries ->
            IgnitorDsl.SuperSine(
                voices = voices, spread = spread, analog = analog,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
            )
        },
    )

    for ((name, make) in nodes) {
        "$name - phasePool/drawTries/kMin/kMax reach the engine through the DSL runtime" {
            fun mean(pool: Double, lo: Double, hi: Double, tries: Double): Double =
                (1..notes).sumOf { fundamentalAmp(make(pool, lo, hi, tries)) } / notes

            val deep = mean(1.0, 0.85, 0.95, 64.0)
            val off = mean(0.0, 0.85, 0.95, 64.0)
            val shallow = mean(1.0, 0.85, 0.95, 1.0)
            val lowBand = mean(1.0, 0.02, 0.10, 64.0)
            deep shouldBeGreaterThan off * 1.5      // dropped phasePool → ratio ~1
            deep shouldBeGreaterThan shallow * 1.5  // dropped drawTries → both sides = family default
            deep shouldBeGreaterThan lowBand * 3.0  // dropped kMin/kMax → both sides = default band
        }
    }
})
