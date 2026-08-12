/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Guards for the stateful phase pool (P1 — docs/tasks/unison-phase-pool.md §3.3–§3.6):
 * the amortized warm fill lands in-band and grows correctly, roundRobin cycles the vocabulary,
 * refresh evicts randomly, a seeded registry reproduces, and the ENGINE path serves entries from
 * the pool (superimpose copies on the same orbit share one vocabulary; different orbits get
 * different pools; OFF never touches the registry).
 */
class PhasePoolStateSpec : StringSpec({

    val v = 11
    val lo = 0.30
    val hi = 0.55

    /** K of a phase set against the base gain profile — the pool's own acceptance measure. */
    fun kOf(phases: DoubleArray, sideAtten: Double = 0.1): Double {
        val gains = Ignitors.superSawVoiceGains(v, sideAtten)
        var re = 0.0
        var im = 0.0
        for (n in phases.indices) {
            val a = phases[n] * TWO_PI
            re += gains[n] * cos(a)
            im += gains[n] * sin(a)
        }
        return sqrt(re * re + im * im) / abs(gains.sum())
    }

    fun pool(
        rng: Random = Random(7),
        size: Double = 32.0,
        refreshEvery: Double = 0.0,
        warmup: Double = 32.0, // legacy-prefix arithmetic for the growth cases below
    ) = PhasePool(
        voices = v, sideAtten = 0.1, kMin = lo, kMax = hi,
        drawTries = 5.0, poolSize = size, refreshEvery = refreshEvery, warmup = warmup, rng = rng,
    )

    "amortized warm - every served entry is overwhelmingly in-band, from note one" {
        val p = pool(size = 200.0)
        val inBand = (1..200).count {
            val k = kOf(p.next(selection = 0.0))
            k >= lo - 1e-9 && k <= hi + 1e-9
        }
        // Same acceptance statistics as the stateless path: ~88 % in-band at 5 tries; the rest
        // sit at the closest edge.
        inBand shouldBeGreaterThanOrEqual 160
    }

    "roundRobin - cycles the whole vocabulary before repeating" {
        val p = pool(size = 8.0)
        val first = (1..8).map { p.next(selection = 0.0) }
        // 8 distinct entry objects, then the cycle wraps to the same objects in the same order.
        first.toSet().size shouldBe 8
        val second = (1..8).map { p.next(selection = 0.0) }
        second shouldBe first
    }

    "refresh - every Nth note redraws EXACTLY ONE entry, not always the same one; frozen never changes" {
        val frozen = pool(size = 8.0, refreshEvery = 0.0)
        val before = (1..8).map { frozen.next(0.0).copyOf() }
        repeat(64) { frozen.next(0.0) }
        (1..8).map { frozen.next(0.0).copyOf() } shouldBe before

        val evolving = pool(size = 8.0, refreshEvery = 4.0)
        val changedIndices = mutableSetOf<Int>()
        var inBandRefreshes = 0
        repeat(12) {
            val snapshot = (0 until 8).map { evolving.peek(it).copyOf() }
            repeat(4) { evolving.next(0.0) } // exactly one refresh cycle
            val changed = (0 until 8).filter { evolving.peek(it).toList() != snapshot[it].toList() }
            changed.size shouldBe 1 // ONE entry redrawn — never zero, never all
            changedIndices.add(changed.single())
            val k = kOf(evolving.peek(changed.single()))
            if (k >= lo - 1e-9 && k <= hi + 1e-9) {
                inBandRefreshes++
            }
        }
        // Random eviction, never a pinned slot: 12 refreshes must not all hit one index.
        (changedIndices.size > 1).shouldBeTrue()
        // Refresh draws pass the same banded acceptance: ~87 % of banded draws land in-band vs
        // ~34 % for an unbanded first-candidate draw — ≥ 8/12 discriminates the two.
        inBandRefreshes shouldBeGreaterThanOrEqual 8
    }

    "growing phase - work-budgeted prefix, per-note top-up, roundRobin never outruns filled" {
        // tries 5 × v 11 → helper warmup 32 fits the work budget; size 64 → topUpPerNote = 1.
        val p = pool(size = 64.0)
        p.filled shouldBe 32
        val servedRefs = ArrayList<DoubleArray>()
        repeat(32) { n ->
            servedRefs.add(p.next(0.0))
            p.filled shouldBe 33 + n // exactly one top-up per served note
        }
        p.filled shouldBe 64
        repeat(32) { servedRefs.add(p.next(0.0)) }
        // 64 serves over a 64-entry pool: roundRobin walks 0,1,2,…,63 IN ORDER — the order pin
        // (not just distinctness) is what catches a serve-the-just-drawn-entry or frozen-rr
        // mutant, which also produces 64 distinct entries but out of sequence.
        repeat(64) { n ->
            (servedRefs[n] === p.peek(n)).shouldBeTrue()
        }
    }

    "growing phase - large pools top up several entries per note and clamp at the boundary" {
        // size 1000 → topUpPerNote = 1000/256 = 3 (well under the work cap 2048/(5×11) = 37),
        // and (1000 − 32) is NOT divisible by 3, so the final batch must clamp mid-stride.
        val p = pool(size = 1000.0)
        p.filled shouldBe 32
        repeat(10) { n ->
            p.next(0.0)
            p.filled shouldBe 32 + 3 * (n + 1)
        }
        repeat(320) { p.next(0.0) }
        p.filled shouldBe 1000 // clamped exactly at poolSize — no overshoot past the last slot
    }

    "growing phase - prefix is WORK-budgeted, not a flat entry count" {
        // tries 64 × v 11 = 704 work units → prefix = 2048/704 = 2 (not the requested warmup 32).
        val p = PhasePool(
            voices = v, sideAtten = 0.1, kMin = lo, kMax = hi,
            drawTries = 64.0, poolSize = 32.0, refreshEvery = 0.0, warmup = 32.0, rng = Random(8),
        )
        p.filled shouldBe 2
    }

    "warmup knob - seeds exactly warmup entries; 0 = fully lazy, first note still serves" {
        pool(size = 64.0, warmup = 16.0).filled shouldBe 16
        val cold = pool(size = 64.0, warmup = 0.0)
        cold.filled shouldBe 0
        // First serve tops up before serving — no unfilled slot is ever reachable.
        cold.next(0.0)
        cold.filled shouldBe 1
    }

    "seeded registry - identical seeds produce identical vocabularies (offline reproducibility)" {
        val a = PhasePools(Random(42)).pool(2, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        val b = PhasePools(Random(42)).pool(2, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        (1..16).map { a.next(0.0).toList() } shouldBe (1..16).map { b.next(0.0).toList() }
    }

    "registry - caps at MAX_POOLS via least-recently-served eviction; the active key stays pooled" {
        val pools = PhasePools(Random(2))
        val first = pools.pool(0, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0)
        val second = pools.pool(1, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0)
        val last = pools.pool(2, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0)
        for (orbit in 3 until PhasePools.MAX_POOLS) {
            pools.pool(orbit, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0)
        }
        pools.size shouldBe PhasePools.MAX_POOLS
        // Touch orbits 0 and 2 so orbit 1 becomes the least-recently-served...
        (pools.pool(0, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0) === first).shouldBeTrue()
        (pools.pool(2, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0) === last).shouldBeTrue()
        // ...then a fresh key evicts EXACTLY orbit 1 — not the touched keys, not the newcomer,
        // not whatever the map happens to iterate first or last.
        pools.pool(999, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0)
        pools.size shouldBe PhasePools.MAX_POOLS
        (pools.pool(0, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0) === first).shouldBeTrue()
        (pools.pool(2, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0) === last).shouldBeTrue()
        // Re-requesting the evicted key mints a FRESH pool (identity differs from the original).
        (pools.pool(1, v, 0.1, lo, hi, 5.0, 4.0, 0.0, warmup = 16.0) === second) shouldBe false
    }

    "registry - keys are coerced: settings beyond the clamps share one pool" {
        val pools = PhasePools(Random(6))
        val a = pools.pool(0, v, 0.1, lo, hi, 80.0, 5000.0, 0.0, warmup = 16.0)
        val b = pools.pool(0, v, 0.1, lo, hi, 200.0, 9000.0, 0.0, warmup = 16.0) // both coerce to (64, 1024)
        (a === b).shouldBeTrue()
        pools.size shouldBe 1
    }

    "registry - identical settings share ONE pool; every key component mints its own" {
        val pools = PhasePools(Random(1))
        val g2a = pools.pool(2, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        val g2b = pools.pool(2, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0) // superimpose copy, same orbit
        (g2a === g2b).shouldBeTrue()
        pools.size shouldBe 1
        // Each key component isolates: orbit, voices, gain profile, band edges, and the
        // maintenance knobs (a knob whose effect depends on note-arrival order would be the
        // parameter-parity bug class).
        pools.pool(3, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v + 2, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.5, lo, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.1, 0.10, hi, 5.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.1, lo, 0.90, 5.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.1, lo, hi, 40.0, 16.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.1, lo, hi, 5.0, 24.0, 0.0, warmup = 16.0)
        pools.pool(2, v, 0.1, lo, hi, 5.0, 16.0, 10.0, warmup = 16.0)
        pools.pool(2, v, 0.1, lo, hi, 5.0, 16.0, 0.0, warmup = 4.0)
        pools.size shouldBe 10
    }

    // ── Engine integration: the pooled path actually serves pool entries ─────────────────────────

    fun renderFundamental(sig: Ignitor): Double {
        val sampleRate = 48000
        val n = 512
        val buffer = AudioBuffer(n)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = n,
            voiceEndFrame = sampleRate + n,
            scratchBuffers = ScratchBuffers(n),
        ).apply { offset = 0; length = n; voiceElapsedFrames = 0 }
        sig.generate(buffer, 375.0, ctx)
        var re = 0.0
        var im = 0.0
        val w = TWO_PI * 375.0 / sampleRate
        for (i in 0 until n) {
            re += buffer[i] * cos(w * i)
            im += buffer[i] * sin(w * i)
        }
        return 2.0 * sqrt(re * re + im * im) / n
    }

    "engine - phasePool OFF never touches an available registry" {
        val pools = PhasePools(Random(4))
        renderFundamental(
            Ignitors.superSine(
                voices = ParamIgnitor("voices", v.toDouble()),
                detune = ParamIgnitor("spread", 0.0),
                analog = ParamIgnitor("analog", 0.0),
                rng = Random(1),
                phasePool = 0.0,
                phasePools = pools, orbit = 0,
            )
        )
        // A refactor that hoists the pool lookup out of the `banded` gate would warm a pool for
        // every super-osc voice with the feature OFF — this pins the gate.
        pools.size shouldBe 0
    }

    "engine - pooled roundRobin repeats the vocabulary; the stateless fallback never does" {
        // Frozen 2-entry pool + roundRobin: notes 1/3 and 2/4 must render IDENTICAL fundamentals
        // (same phase configuration re-served). The stateless fallback draws fresh per note.
        fun note(pools: PhasePools?, seed: Int): Double = renderFundamental(
            Ignitors.superSine(
                voices = ParamIgnitor("voices", v.toDouble()),
                detune = ParamIgnitor("spread", 0.0),
                analog = ParamIgnitor("analog", 0.0),
                rng = Random(seed),
                sideAtten = 0.1, gainJitter = 0.0, // jitter off → the phase entry alone sets K
                phasePool = 1.0, drawTries = 5.0, kMin = lo, kMax = hi,
                poolSize = 2.0, refreshEvery = 0.0, selection = 0.0,
                phasePools = pools, orbit = 2,
            )
        )

        val pools = PhasePools(Random(9))
        val pooled = (1..4).map { note(pools, seed = it) }
        pooled[2] shouldBe pooled[0]
        pooled[3] shouldBe pooled[1]

        val stateless = (1..4).map { note(null, seed = it) }
        (stateless.toSet().size > 2).shouldBeTrue()
    }

    "engine - the pooled path lands in-band (entries reach the voices)" {
        val pools = PhasePools(Random(5))
        val k = renderFundamental(
            Ignitors.superSine(
                voices = ParamIgnitor("voices", v.toDouble()),
                detune = ParamIgnitor("spread", 0.0),
                analog = ParamIgnitor("analog", 0.0),
                rng = Random(1),
                sideAtten = 0.1, gainJitter = 0.0,
                phasePool = 1.0, drawTries = 64.0, kMin = 0.85, kMax = 0.95,
                poolSize = 8.0, refreshEvery = 0.0, selection = 0.0,
                phasePools = pools, orbit = 0,
            )
        )
        // With jitter off and spread 0 the fundamental IS the entry's K. A best-of-64 draw toward
        // the (unreachable) [0.85, 0.95] band lands ~0.53-0.63 — but the stateless fallback
        // produces the same statistics, so the DISCRIMINATING assertion is the registry: only the
        // pooled path mints a pool.
        k shouldBeGreaterThan 0.45
        pools.size shouldBe 1
    }
})
