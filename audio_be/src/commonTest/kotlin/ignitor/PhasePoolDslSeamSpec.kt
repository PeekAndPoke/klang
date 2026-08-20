/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * End-to-end seam guard for the phase-pool knobs: DSL node → `IgnitorDslRuntime.buildRaw`
 * forwarding → `Ignitors.*` factory → shared engine. This is the ONE path real users take
 * (`Osc.supersine().phasePool(1)`), and dropping a `phasePool = phasePool` forwarding in a
 * `buildRaw` branch compiles cleanly (the factory default fills in) while turning the knob into a
 * permanent silent no-op — no other spec would notice (`PhasePoolSpec` calls the factories
 * directly; the KlangScript specs stop at DSL object equality; the codec spec stops at the wire).
 *
 * Production now threads the voice's stream (seeded-voice-rng), but this spec predates it and stays statistical by design:
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

    fun fundamentalAmp(dsl: IgnitorDsl, pools: PhasePools? = null, orbit: Int = 0): Double {
        val buffer = AudioBuffer(n)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = n,
            voiceEndFrame = sampleRate + n,
            scratchBuffers = ScratchBuffers(n),
        ).apply { offset = 0; length = n; voiceElapsedFrames = 0 }
        dsl.toExciter(phasePools = pools, orbit = orbit).generate(buffer, freqHz, ctx)
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

    // (phasePool, kMin, kMax, drawTries, poolSize, refreshEvery, warmup) -> node. gainJitter is pinned
    // to 0 so a re-served pool entry renders an IDENTICAL fundamental (the repeat signature).
    val nodes = listOf<Pair<String, (Double, Double, Double, Double, Double, Double, Double) -> IgnitorDsl>>(
        "SuperSaw" to { pool, lo, hi, tries, poolSize, refreshEvery, warmup ->
            IgnitorDsl.SuperSaw(
                voices = voices, spread = spread, analog = analog, gainJitter = 0.0,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
                poolSize = poolSize, refreshEvery = refreshEvery, warmup = warmup,
            )
        },
        "SuperRamp" to { pool, lo, hi, tries, poolSize, refreshEvery, warmup ->
            IgnitorDsl.SuperRamp(
                voices = voices, spread = spread, analog = analog, gainJitter = 0.0,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
                poolSize = poolSize, refreshEvery = refreshEvery, warmup = warmup,
            )
        },
        "SuperSquare" to { pool, lo, hi, tries, poolSize, refreshEvery, warmup ->
            IgnitorDsl.SuperSquare(
                voices = voices, spread = spread, analog = analog, gainJitter = 0.0,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
                poolSize = poolSize, refreshEvery = refreshEvery, warmup = warmup,
            )
        },
        "SuperTri" to { pool, lo, hi, tries, poolSize, refreshEvery, warmup ->
            IgnitorDsl.SuperTri(
                voices = voices, spread = spread, analog = analog, gainJitter = 0.0,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
                poolSize = poolSize, refreshEvery = refreshEvery, warmup = warmup,
            )
        },
        "SuperSine" to { pool, lo, hi, tries, poolSize, refreshEvery, warmup ->
            IgnitorDsl.SuperSine(
                voices = voices, spread = spread, analog = analog, gainJitter = 0.0,
                phasePool = pool, drawTries = tries, kMin = lo, kMax = hi,
                poolSize = poolSize, refreshEvery = refreshEvery, warmup = warmup,
            )
        },
    )

    for ((name, make) in nodes) {
        "$name - phasePool/drawTries/kMin/kMax reach the engine through the DSL runtime" {
            fun mean(pool: Double, lo: Double, hi: Double, tries: Double): Double =
                (1..notes).sumOf { fundamentalAmp(make(pool, lo, hi, tries, 1000.0, 10.0, 16.0)) } / notes

            val deep = mean(1.0, 0.85, 0.95, 64.0)
            val off = mean(0.0, 0.85, 0.95, 64.0)
            val shallow = mean(1.0, 0.85, 0.95, 1.0)
            val lowBand = mean(1.0, 0.02, 0.10, 64.0)
            deep shouldBeGreaterThan off * 1.5      // dropped phasePool → ratio ~1
            deep shouldBeGreaterThan shallow * 1.5  // dropped drawTries → both sides = family default
            deep shouldBeGreaterThan lowBand * 3.0  // dropped kMin/kMax → both sides = default band
        }

        "$name - pooled serving + orbit key reach the engine through the DSL runtime" {
            // Frozen 2-entry pool, roundRobin, jitter off: notes 1/3 and 2/4 re-serve the same
            // entries → IDENTICAL fundamentals. A dropped `phasePools = cache.phasePools`
            // forwarding falls back to stateless per-note draws (never repeats); a dropped
            // `orbit = cache.orbit` collapses the registry to one pool (size stays 1).
            val pools = PhasePools(Random(3))
            fun note(orbit: Int): Double =
                fundamentalAmp(make(1.0, 0.30, 0.55, 5.0, 2.0, 0.0, 4.0), pools, orbit)
            val a = note(2)
            val b = note(2)
            note(2) shouldBe a
            note(2) shouldBe b
            pools.size shouldBe 1
            note(3)
            pools.size shouldBe 2
            // warmup is a Key component: probing the registry with the SAME coerced settings must
            // resolve the pool the runtime created — a dropped `warmup = warmup` forwarding
            // anywhere in the chain would have keyed the family default 16 and the probe would
            // mint an extra pool. (poolSize 32 on purpose: at the tiny 2-entry pool above, warmup
            // 4 and 16 BOTH coerce to 2 and the probe would be blind.)
            fundamentalAmp(make(1.0, 0.30, 0.55, 5.0, 32.0, 0.0, 4.0), pools, 5)
            pools.size shouldBe 3
            pools.pool(5, 11, 0.1, 0.30, 0.55, 5.0, 32.0, 0.0, warmup = 4.0)
            pools.size shouldBe 3
        }
    }
})
