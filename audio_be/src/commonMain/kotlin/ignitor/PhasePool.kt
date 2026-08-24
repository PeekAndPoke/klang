/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** How [PhasePool.next] serves entries. The user surface is a STRING (`selection`), value-colon
 * compound form `"name[:width[:blend]]"` — parsed by [parsePhasePoolSelection] at voice build. */
enum class PhasePoolSelection {
    /** Normal-distribution serving over the vocabulary's RANK ORDER (median-centered) — the
     *  "guitar-like" default. Rank space makes it robust: even when the accept band is
     *  unreachable and every stored K sits off-band, serving still spans the vocabulary
     *  instead of collapsing onto one extreme entry. */
    Normal,

    /** Uniform random pick from the vocabulary. */
    Random,

    /** Cycle the vocabulary in order. OPT-IN: at short vocabularies the cycling period is
     *  audible as a gargling pattern — that is why it is no longer the default. */
    RoundRobin,
}

/** Parsed `selection`: the mode plus the Normal mode's two coefficients. */
class PhasePoolSelectionParsed(val mode: PhasePoolSelection, val width: Double, val blend: Double)

/** Normal-mode width when no coeff is given: σ = width · filled/2 in RANK space, so the
 *  default 0.5 keeps ~95% of targets within the vocabulary span (out-of-range targets are
 *  REFLECTED back, not clamped - no edge piling). SMALLER =
 *  tighter around the median entry: `"normal:0.1"` serves almost only the most typical
 *  takes; larger loosens toward uniform. */
const val PHASE_POOL_DEFAULT_WIDTH: Double = 0.5

/** Normal-mode random blend when no second coeff is given: 0 = pure normal serving. */
const val PHASE_POOL_DEFAULT_BLEND: Double = 0.0

/**
 * Parses the `selection` string: `"name[:width[:blend]]"` (the sanctioned VALUE-colon form,
 * like `bd:2`). Names (aliases in parens): `"normal"` (`distribution`, `dist`, `gauss`,
 * `gaussian`) — the default; `"random"` (`rnd`); `"roundrobin"` (`roundrobbin`, `rr`).
 *
 * Normal-mode coefficients (positional, either may be left empty — `"normal::0.9"`):
 *  - width: center tightness in rank space (σ = width · vocabulary/2). 0.1 = tight,
 *    0.5 = default, larger = looser.
 *  - blend: fraction of serves that instead pick a uniformly random VOCABULARY entry
 *    (0..1, default 0) — still a band-accepted take, not the un-pooled legacy randomness.
 *    `0` = pure bell, `1` = same as `"random"`; "almost fully random with a slight edge
 *    in the center" = `"normal::0.9"` (≡ `"normal:0.5:0.9"` — 0.5 IS the default width).
 *
 * An unrecognized name or a bad coefficient COERCES to its default (never throws); modes
 * without coefficients ignore them silently.
 */
fun parsePhasePoolSelection(raw: String?): PhasePoolSelectionParsed {
    val parts = (raw ?: "").trim().lowercase().split(':')
    val mode = when (parts.getOrNull(0)?.trim()) {
        "random", "rnd" -> PhasePoolSelection.Random
        "roundrobin", "roundrobbin", "rr" -> PhasePoolSelection.RoundRobin
        else -> PhasePoolSelection.Normal // incl. "normal"/aliases, "" and typos: coerce
    }
    val c1 = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
    // width 0 is meaningful ("no spread - always the median take"), so only NEGATIVE and
    // non-finite values coerce to the default.
    val width = if (c1 != null && c1.isFinite() && c1 >= 0.0) {
        c1
    } else {
        PHASE_POOL_DEFAULT_WIDTH
    }
    val c2 = parts.getOrNull(2)?.trim()?.toDoubleOrNull()
    val blend = if (c2 != null && c2.isFinite()) {
        c2.coerceIn(0.0, 1.0)
    } else {
        PHASE_POOL_DEFAULT_BLEND
    }
    return PhasePoolSelectionParsed(mode = mode, width = width, blend = blend)
}

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
        /** Pools retained per playback. Live-edit sweeps of any key component (a by-ear `.phasePool(kMin = …)`
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
     * are built from COERCED knob values, so settings beyond the clamps (e.g. `.phasePool(drawTries = 80)`
     * vs `= 200`) share one pool instead of burning cap slots on identical configs.
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
        warmup: Double,
    ): PhasePool {
        val lo = kMin.coerceIn(0.0, 1.0)
        val hi = kMax.coerceIn(lo, 1.0)
        val tries = drawTries.toInt().coerceIn(1, 64)
        val size = poolSize.toInt().coerceIn(1, PhasePool.MAX_POOL_SIZE)
        val refresh = refreshEvery.toInt().coerceAtLeast(0)
        // Work-cap BEFORE the key: every warmup above the cap behaves identically, so it must
        // share one pool (the coerced-keys invariant) instead of burning cap slots per value.
        val seed = warmup.toInt().coerceIn(0, minOf(size, PhasePool.PREFIX_WORK_BUDGET / (tries * voices)))
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
         *  work-capped like the prefix): the default 256-entry pool closes in ~240 notes, the
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

    /** Fundamental-coherence K of each stored entry (parallel to [entries]) — what the
     *  Normal mode's rank order sorts by. One eager Double per slot (≤ 8 KB at the cap). */
    private val kOf = DoubleArray(entries.size)

    /** Entry indices sorted by [kOf] ascending over `[0, filled)` — the Normal mode's rank
     *  space. Preallocated; re-sorted lazily (insertion sort — the array is nearly sorted
     *  after a single top-up or refresh redraw, so the pass is ~O(filled)). */
    private val rankIdx = IntArray(entries.size)
    private var ranksDirty = true

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
        // Sort the warmup prefix now: the first Normal serve would otherwise pay a full
        // O(prefix^2) insertion sort on the audio callback (the warmup budget exists
        // precisely to keep the first note inside a block).
        ensureRanks()
    }

    private fun topUpOne() {
        val e = DoubleArray(voices)
        kOf[filled] = drawBandedInto(e)
        entries[filled] = e
        rankIdx[filled] = filled
        filled++
        ranksDirty = true
    }

    /**
     * Serve one entry for a note-on ([mode]/[width]/[blend] from [parsePhasePoolSelection]).
     * The returned array is pool-owned — COPY from it, never mutate or retain it.
     *
     * [PhasePoolSelection.Normal] (the default) draws a target RANK from a normal centered on
     * the vocabulary's median entry (entries sorted by K; σ = width·filled/2) and serves that
     * rank — center-heavy, extremes rare, no cycling period, and robust to unreachable bands
     * (rank space always spans the vocabulary; a K-space target would collapse onto one
     * extreme entry whenever the stored Ks don't straddle the band center — review finding,
     * 2026-08-24). [blend] mixes in plain uniform serves: `blend = 0.9` is "almost fully
     * random with a slight edge in the center". [PhasePoolSelection.RoundRobin] (the
     * pre-2026-08-24 default, §9.2) is opt-in now: its cycling gargles audibly at short
     * vocabularies.
     */
    fun next(
        mode: PhasePoolSelection,
        width: Double = PHASE_POOL_DEFAULT_WIDTH,
        blend: Double = PHASE_POOL_DEFAULT_BLEND,
    ): DoubleArray {
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
                val j = rng.nextInt(filled) // random eviction, never worst
                kOf[j] = drawBandedInto(entries[j]!!)
                ranksDirty = true
            }
        }

        return when (mode) {
            PhasePoolSelection.Random -> entries[rng.nextInt(filled)]!!

            PhasePoolSelection.RoundRobin -> {
                if (rr >= filled) {
                    rr = 0
                }
                val e = entries[rr]!!
                rr++
                e
            }

            PhasePoolSelection.Normal -> {
                if (blend > 0.0 && rng.nextDouble() < blend) {
                    return entries[rng.nextInt(filled)]!!
                }
                ensureRanks()
                val median = (filled - 1) / 2.0
                val sigma = width * filled / 2.0
                var t = median + gaussian() * sigma
                // REFLECT out-of-range targets instead of clamping: a clamp piles both
                // gaussian tails onto the two extreme entries (~4x over-serving the least
                // and most coherent takes at shipped defaults - review finding). One
                // reflection covers everything up to 3x the vocabulary; coerce catches the rest.
                val top = (filled - 1).toDouble()
                if (t < 0.0) {
                    t = -t
                }
                if (t > top) {
                    t = 2.0 * top - t
                }
                val rank = round(t).toInt().coerceIn(0, filled - 1)
                entries[rankIdx[rank]]!!
            }
        }
    }

    /** Re-sorts [rankIdx] by [kOf] when stale — insertion sort, ~O(filled) when nearly sorted. */
    private fun ensureRanks() {
        if (!ranksDirty) {
            return
        }
        for (i in 1 until filled) {
            val idx = rankIdx[i]
            val k = kOf[idx]
            var j = i - 1
            while (j >= 0 && kOf[rankIdx[j]] > k) {
                rankIdx[j + 1] = rankIdx[j]
                j--
            }
            rankIdx[j + 1] = idx
        }
        ranksDirty = false
    }

    /** Spec-only read access to a stored entry's coherence K (no serving side effects). */
    internal fun peekK(index: Int): Double = kOf[index]

    /** One standard-normal draw (Box–Muller). `1.0 - nextDouble()` keeps the log argument in
     *  (0, 1] — never ln(0). Allocation-free. */
    private fun gaussian(): Double =
        sqrt(-2.0 * ln(1.0 - rng.nextDouble())) * cos(TWO_PI * rng.nextDouble())

    /** Spec-only read access to a stored entry (no serving side effects). */
    internal fun peek(index: Int): DoubleArray = entries[index]!!

    /**
     * One banded best-of-[tries] draw into [target] — same acceptance logic as the engine's
     * stateless `selectBandedPhases`, scored against the base profile. Early exit on the first
     * in-band candidate is accept-reject sampling, unbiased within the band. Returns the
     * accepted candidate's K (stored in [kOf] — the Normal mode's rank order sorts by it).
     */
    private fun drawBandedInto(target: DoubleArray): Double {
        var bestDist = Double.MAX_VALUE
        var bestK = 0.0

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
                return k // target already holds the accepted candidate; scratch not needed
            }

            if (dist < bestDist) {
                bestDist = dist
                bestK = k
                target.copyInto(scratch)
            }
        }

        scratch.copyInto(target)
        return bestK
    }
}
