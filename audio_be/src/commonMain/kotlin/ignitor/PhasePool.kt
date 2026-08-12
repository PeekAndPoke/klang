/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Per-playback registry of unison start-phase pools (docs/tasks/unison-phase-pool.md §3.3–§3.6).
 *
 * One pool per (orbit, unisonCount, sideAtten, band): the same instrument on the same orbit reuses
 * one vocabulary of accepted phase configurations — superimpose copies pull different entries from
 * the same pool ("double-tracked, same instrument"), while two orbits develop different characters
 * over time (bounded by construction: every pool lives inside the same quality band).
 *
 * "Born warm" collapses into AMORTIZED first-use warming: `voices` is a pattern-level param
 * unknown at registration time, and an eager full fill measurably blows the render budget
 * (3.8–20 ms cold at shipped defaults vs the 2.67 ms block — measured on V8), so a pool seeds a
 * WORK-budgeted prefix at construction and tops up a few entries per served note (µs each,
 * closing the vocabulary in ~[PhasePool.TOP_UP_DIVISOR] notes). The K distribution is complete
 * from the first entry — vocabulary size governs repetition audibility, not quality — so the
 * sound guarantee is identical from note one. Once full, pool operations allocate nothing:
 * refresh redraws in place and `next()` returns a stored reference for the engine to copy from;
 * during growth each top-up allocates its one entry (lazy — an eager poolSize-array constructor
 * was the stall being avoided).
 *
 * The [rng] is the pool's own stream, separate from the per-voice rng: live playback passes a
 * freshly-created source ("takes vary" — see `VoiceScheduler`), the offline renderer passes a
 * fixed seed so pool vocabularies reproduce (`AudioBackendContext.phasePoolSeed`).
 */
class PhasePools(private val rng: Random) {

    companion object {
        /** Pools retained per playback. Live-edit sweeps of any key component (a by-ear `.kMin()`
         *  session) mint a pool per value; at the cap the LEAST-RECENTLY-SERVED pool is evicted,
         *  so the key being auditioned NOW is always the pooled one (a hard refusal would make
         *  the user A/B pooled-vs-stateless at the cap boundary instead of knob-vs-knob).
         *  Trade-off: eviction is the EXPENSIVE branch — with more than [MAX_POOLS] keys
         *  simultaneously live (not swept), every note-on reconstructs a prefix (~7× a stateless
         *  note at the deepest default, up to ~35× at a warmup-32 config — work-budget-bound
         *  vs warmup-bound) plus a fresh poolSize-slot backing array, and no vocabulary
         *  ever accumulates. Bounded (≤ ~4k trig-loop iterations per note), pathological, and
         *  preferable to punishing the common sweep case. */
        const val MAX_POOLS = 64
    }

    private data class Key(
        val orbit: Int,
        val voices: Int,
        val sideAtten: Double,
        val kMin: Double,
        val kMax: Double,
        val drawTries: Int,
        val poolSize: Int,
        val refreshEvery: Int,
        val warmup: Int,
    )

    private class Slot(val pool: PhasePool, var tick: Int)

    private val pools = mutableMapOf<Key, Slot>()
    private var useTick = 0

    /** Number of live pools (diagnostics/specs). */
    val size: Int get() = pools.size

    /**
     * The pool for this configuration, warming it on first use. The band is part of the key —
     * stored configurations are only valid for the (gain profile, band) they were accepted under.
     * The maintenance knobs are part of the key too: two sounds sharing every OTHER key component
     * but differing in e.g. [drawTries] must not race for one pool on note-arrival order (a knob
     * whose effect depends on which note lands first is the parameter-parity bug class). Sounds
     * with identical settings — the common case, and every superimpose copy — still share. Keys
     * are built from COERCED knob values, so settings beyond the clamps (e.g. `.drawTries(80)`
     * vs `.drawTries(200)`) share one pool instead of burning cap slots on identical configs.
     */
    fun pool(
        orbit: Int,
        voices: Int,
        sideAtten: Double,
        kMin: Double,
        kMax: Double,
        drawTries: Double,
        poolSize: Double,
        refreshEvery: Double,
        // Default is a spec convenience only — the engine always passes the DSL value explicitly.
        warmup: Double = 16.0,
    ): PhasePool {
        val lo = kMin.coerceIn(0.0, 1.0)
        val hi = kMax.coerceIn(lo, 1.0)
        val tries = drawTries.toInt().coerceIn(1, 64)
        val size = poolSize.toInt().coerceIn(1, PhasePool.MAX_POOL_SIZE)
        val refresh = refreshEvery.toInt().coerceAtLeast(0)
        val seed = warmup.toInt().coerceIn(0, size)
        val key = Key(
            orbit = orbit,
            voices = voices,
            sideAtten = sideAtten,
            kMin = lo,
            kMax = hi,
            drawTries = tries,
            poolSize = size,
            refreshEvery = refresh,
            warmup = seed
        )

        useTick++

        pools[key]?.let { slot ->
            slot.tick = useTick
            return slot.pool
        }

        if (pools.size >= MAX_POOLS) {
            var oldestKey: Key? = null
            var oldestTick = Int.MAX_VALUE
            for ((k, slot) in pools) {
                if (slot.tick < oldestTick) {
                    oldestTick = slot.tick
                    oldestKey = k
                }
            }

            pools.remove(oldestKey)
        }

        return PhasePool(
            voices = voices,
            sideAtten = sideAtten,
            kMin = lo,
            kMax = hi,
            drawTries = tries.toDouble(),
            poolSize = size.toDouble(),
            refreshEvery = refresh.toDouble(),
            warmup = seed.toDouble(),
            rng = rng,
        ).also { pools[key] = Slot(pool = it, tick = useTick) }
    }
}

/**
 * A bounded vocabulary of accepted start-phase configurations for one (unisonCount, gain-profile,
 * band) — the stateful half of the phase-pool design. Entries are drawn with the same banded
 * best-of-M acceptance as the stateless P0 path, but scored against the BASE gain profile
 * (`superSawVoiceGains(v, sideAtten)`): per-note gain jitter does not exist at fill time, and it
 * perturbs the effective K only second-order (doc §3.6, deliberate deviation from the stateless
 * path's exact-jittered-gain scoring).
 *
 * Evolution: every [refreshEvery]-th served note triggers one fresh banded draw that replaces a
 * RANDOM entry — never the worst (evict-worst would homogenize the pool toward the band center;
 * random eviction keeps it a fair rolling sample of the accept distribution, doc §3.4).
 * `refreshEvery = 0` freezes the pool (reproducible vocabulary).
 */
class PhasePool(
    private val voices: Int,
    sideAtten: Double,
    kMin: Double,
    kMax: Double,
    drawTries: Double,
    poolSize: Double,
    refreshEvery: Double,
    warmup: Double,
    private val rng: Random,
) {
    companion object {
        /** Upper bound on entries per pool: memory guard (scales with the note's unison count —
         *  entries allocate lazily, one `DoubleArray(voices)` per top-up), not a sound cap. */
        const val MAX_POOL_SIZE = 1024

        /** Constructor work budget in tries×voices units (≈ trig-loop iterations): bounds the
         *  eager warmup prefix to well under a block even at the knob caps — a user `warmup`
         *  beyond it is silently work-capped (a compute bound, not a sound clamp). */
        const val PREFIX_WORK_BUDGET = 2048

        /** Growth divisor: top up ~poolSize/[TOP_UP_DIVISOR] entries per served note (min 1, and
         *  work-capped like the prefix): the default 256-entry pool closes in ~224 notes, the
         *  1024 cap in ~250 (without this, a large pool at one-per-note never leaves the growing
         *  phase inside a real song). */
        const val TOP_UP_DIVISOR = 256
    }

    private val lo = kMin.coerceIn(0.0, 1.0)
    private val hi = kMax.coerceIn(lo, 1.0)
    private val tries = drawTries.toInt().coerceIn(1, 64)
    private val refreshN = refreshEvery.toInt().coerceAtLeast(0)

    // Base (jitter-free) gain profile the acceptance scoring runs against.
    private val gains: DoubleArray = Ignitors.superSawVoiceGains(voices, sideAtten)
    private val gsum: Double = gains.sum()

    // Entries allocate LAZILY as the vocabulary grows — an eager Array(poolSize){DoubleArray(v)}
    // would put poolSize allocations on the first note-on's render callback.
    private val entries: Array<DoubleArray?> =
        arrayOfNulls(poolSize.toInt().coerceIn(1, MAX_POOL_SIZE))

    // Both growth terms are bounded: by poolSize (close in ~TOP_UP_DIVISOR..2x notes) AND by
    // draw cost (a deep-tries × many-voices config tops up fewer entries per note — the same
    // work budget the constructor prefix uses, so no knob combination stalls a block).
    private val topUpPerNote = minOf(
        (entries.size / TOP_UP_DIVISOR).coerceAtLeast(1),
        (PREFIX_WORK_BUDGET / (tries * voices)).coerceAtLeast(1),
    )

    /** Best-so-far scratch for the banded draw — preallocated, reused by every refresh. */
    private val scratch = DoubleArray(voices)

    private var rr = 0
    private var served = 0

    /** Entries drawn so far — the vocabulary grows per served note until full. */
    var filled: Int = 0
        private set

    init {
        // Amortized warm-up: seed `warmup` entries now (user knob, default 16), WORK-capped so a
        // deep tries × many-voices config cannot stall the first note (an eager full fill
        // measurably drops render blocks). `warmup 0` = fully lazy: the first served note tops up.
        val prefix = warmup.toInt()
            .coerceIn(0, PREFIX_WORK_BUDGET / (tries * voices))
            .coerceAtMost(entries.size)
        repeat(prefix) {
            topUpOne()
        }
    }

    private fun topUpOne() {
        entries[filled] = DoubleArray(voices).also { drawBandedInto(it) }
        filled++
    }

    /**
     * Serve one entry for a note-on. `selection`: 0 = roundRobin (cycle the array — settled
     * §9.2), anything > 0.5 = random. The returned array is pool-owned — COPY from it, never
     * mutate or retain it.
     */
    fun next(selection: Double): DoubleArray {
        if (filled < entries.size) {
            // Growing phase: a few ~µs top-ups per note stand in for refresh (the vocabulary is
            // already churning by construction) and close the pool in ~TOP_UP_DIVISOR notes.
            var k = 0
            while (k < topUpPerNote && filled < entries.size) {
                topUpOne()
                k++
            }
        } else if (refreshN > 0) {
            served++
            if (served >= refreshN) {
                served = 0
                drawBandedInto(entries[rng.nextInt(filled)]!!) // random eviction, never worst
            }
        }

        if (selection > 0.5) {
            return entries[rng.nextInt(filled)]!!
        }

        if (rr >= filled) {
            rr = 0
        }

        val e = entries[rr]!!
        rr++

        return e
    }

    /** Spec-only read access to a stored entry (no serving side effects). */
    internal fun peek(index: Int): DoubleArray = entries[index]!!

    /**
     * One banded best-of-[tries] draw into [target] — same acceptance logic as the engine's
     * stateless `selectBandedPhases`, scored against the base profile. Early exit on the first
     * in-band candidate is accept-reject sampling, unbiased within the band.
     */
    private fun drawBandedInto(target: DoubleArray) {
        var bestDist = Double.MAX_VALUE

        for (t in 0 until tries) {
            var re = 0.0
            var im = 0.0

            for (n in 0 until voices) {
                val p = rng.nextDouble()
                target[n] = p
                val a = p * TWO_PI
                re += gains[n] * cos(a)
                im += gains[n] * sin(a)
            }

            val k = if (gsum != 0.0) sqrt(re * re + im * im) / abs(gsum) else 1.0
            val dist = if (k < lo) lo - k else if (k > hi) k - hi else 0.0

            if (dist == 0.0) {
                return // target already holds the accepted candidate; scratch not needed
            }

            if (dist < bestDist) {
                bestDist = dist
                target.copyInto(scratch)
            }
        }

        scratch.copyInto(target)
    }
}
