/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The `selection` modes (user decisions, 2026-08-24): STRING-valued, value-colon compound
 * `"name[:width[:blend]]"` — `"normal"` (NEW DEFAULT: median-centered normal serving over
 * the vocabulary's RANK order — rank space so an unreachable band cannot collapse serving
 * onto one extreme entry, the review's HIGH finding; blend mixes in plain-random serves),
 * `"random"` (as before), `"roundrobin"` (the OLD default, now opt-in: its cycling period
 * gargles audibly at short vocabularies).
 */
class PhasePoolSelectionModesSpec : StringSpec({

    val v = 11

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

    // Wide band + single-try draws: entry K values SCATTER, so serving policies differ.
    fun widePool(rng: Random = Random(11), refreshEvery: Double = 0.0) = PhasePool(
        voices = v, sideAtten = 0.1, kMin = 0.05, kMax = 0.95,
        drawTries = 1.0, poolSize = 16.0, refreshEvery = refreshEvery, warmup = 16.0, rng = rng,
    )

    // ── parser ───────────────────────────────────────────────────────────────

    "parser: names, aliases and the two positional coefficients" {
        parsePhasePoolSelection("normal").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("distribution").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("dist").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("gauss").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("random").mode shouldBe PhasePoolSelection.Random
        parsePhasePoolSelection("rnd").mode shouldBe PhasePoolSelection.Random
        parsePhasePoolSelection("roundrobin").mode shouldBe PhasePoolSelection.RoundRobin
        parsePhasePoolSelection("roundrobbin").mode shouldBe PhasePoolSelection.RoundRobin
        parsePhasePoolSelection("rr").mode shouldBe PhasePoolSelection.RoundRobin

        parsePhasePoolSelection(" Normal:0.1 ").let {
            it.mode shouldBe PhasePoolSelection.Normal
            it.width shouldBe 0.1
            it.blend shouldBe PHASE_POOL_DEFAULT_BLEND
        }
        parsePhasePoolSelection("normal:0.1:0.5").let {
            it.width shouldBe 0.1
            it.blend shouldBe 0.5
        }
        // empty width slot: "almost fully random with a slight center edge"
        parsePhasePoolSelection("normal::0.9").let {
            it.width shouldBe PHASE_POOL_DEFAULT_WIDTH
            it.blend shouldBe 0.9
        }
        parsePhasePoolSelection("normal").width shouldBe PHASE_POOL_DEFAULT_WIDTH
    }

    "parser: coercion, never a throw — unknown names and bad coefficients fall to defaults" {
        parsePhasePoolSelection("gargle").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("").mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection(null).mode shouldBe PhasePoolSelection.Normal
        parsePhasePoolSelection("normal:x").width shouldBe PHASE_POOL_DEFAULT_WIDTH
        parsePhasePoolSelection("normal:-3").width shouldBe PHASE_POOL_DEFAULT_WIDTH
        parsePhasePoolSelection("normal:0").width shouldBe 0.0 // "no spread — always the median take"
        parsePhasePoolSelection("normal:0.5:7").blend shouldBe 1.0   // clamped
        parsePhasePoolSelection("normal:0.5:-1").blend shouldBe 0.0  // clamped
        parsePhasePoolSelection("normal:0.5:x").blend shouldBe PHASE_POOL_DEFAULT_BLEND
    }

    // ── normal serving (rank space) ──────────────────────────────────────────

    "normal: a tiny width always serves the MEDIAN-rank entry of the vocabulary" {
        val p = widePool()
        // width 0 = "no spread": the target IS the median rank, deterministically.
        val served = (1..20).map { p.next(PhasePoolSelection.Normal, width = 0.0) }
        val first = served.first()
        served.forEach { (it === first) shouldBe true }

        // the served entry sits at the median RANK: for even filled the target (filled-1)/2
        // = 7.5 rounds to rank 8 (half-up and half-to-even agree here), so exactly 8 entries have lower K
        val servedK = kOf(first)
        val lower = (0 until 16).count { kOf(p.peek(it)) < servedK }
        lower shouldBe 16 / 2
    }

    "normal: blend = 1 serves uniformly even at tiny width (the almost-random mix)" {
        val p = widePool()
        val served = (1..20).map { p.next(PhasePoolSelection.Normal, width = 0.0, blend = 1.0) }
        val first = served.first()
        // with pure-random mixing the serve stream is NOT constant (kills a dropped blend)
        served.any { it !== first } shouldBe true
    }

    "normal vs random: at default width the serves concentrate toward the population middle" {
        fun meanCenterDist(mode: PhasePoolSelection): Double {
            val p = widePool(Random(11)) // identical vocabulary for both modes
            val ks = (0 until 16).map { kOf(p.peek(it)) }.sorted()
            val median = ks[8]
            var sum = 0.0
            repeat(400) {
                sum += abs(kOf(p.next(mode, width = PHASE_POOL_DEFAULT_WIDTH)) - median)
            }
            return sum / 400
        }
        val dist = meanCenterDist(PhasePoolSelection.Normal)
        val rand = meanCenterDist(PhasePoolSelection.Random)
        (dist < rand) shouldBe true
    }

    "roundrobin still cycles the vocabulary in order (the opt-in legacy behaviour)" {
        val p = widePool()
        val firstPass = (0 until 16).map { p.next(PhasePoolSelection.RoundRobin) }
        firstPass.forEachIndexed { i, e -> (e === p.peek(i)) shouldBe true }
        (p.next(PhasePoolSelection.RoundRobin) === p.peek(0)) shouldBe true
    }

    // ── kOf bookkeeping (review findings 5 + 6) ──────────────────────────────

    "kOf stays consistent through refresh eviction AND the out-of-band fallback path" {
        // refreshEvery = 1: every full-pool serve redraws one entry; kOf[j] must follow.
        val p = widePool(refreshEvery = 1.0)
        repeat(64) { p.next(PhasePoolSelection.RoundRobin) }
        for (i in 0 until 16) {
            p.peekK(i) shouldBe (kOf(p.peek(i)) plusOrMinus 1e-12)
        }

        // Narrow near-unreachable band, tries 1: every draw exits through the best-of
        // FALLBACK (scratch copy + `return bestK`) — a `return 0.0` mutant breaks this.
        val narrow = PhasePool(
            voices = v, sideAtten = 0.1, kMin = 0.85, kMax = 0.95,
            drawTries = 1.0, poolSize = 8.0, refreshEvery = 1.0, warmup = 8.0, rng = Random(3),
        )
        repeat(32) { narrow.next(PhasePoolSelection.RoundRobin) }
        for (i in 0 until 8) {
            narrow.peekK(i) shouldBe (kOf(narrow.peek(i)) plusOrMinus 1e-12)
        }
    }

    // ── the engine seam (review finding 4) ───────────────────────────────────

    "the Ignitor seam forwards the parsed mode — different selections render differently" {
        // A mutant that hardcodes the mode (or drops the parsed width/blend forwarding)
        // makes these two configurations render identically.
        fun renderNotes(selection: String): DoubleArray {
            val pools = PhasePools(Random(5))
            val node = IgnitorDsl.SuperSaw(
                freq = IgnitorDsl.Constant(110.0),
                phasePool = 1.0,
                kMin = 0.05, kMax = 0.95, drawTries = 1.0,
                poolSize = 8.0, refreshEvery = 0.0, warmup = 8.0,
                selection = selection,
            )
            val out = DoubleArray(4 * 128)
            for (note in 0 until 4) {
                val chain = node.toExciter(phasePools = pools, random = Random(9)) // fresh voice
                val ctx = IgniteContext(
                    sampleRate = 48000,
                    voiceDurationFrames = 128,
                    gateEndFrame = 128,
                    releaseFrames = 0,
                    voiceEndFrame = 128,
                    scratchBuffers = ScratchBuffers(blockFrames = 128),
                )
                ctx.offset = 0
                ctx.length = 128
                val buf = AudioBuffer(128)
                chain.generate(buf, 110.0, ctx)
                buf.copyInto(out, note * 128, 0, 128)
            }
            return out
        }
        val rr = renderNotes("roundrobin")
        val rnd = renderNotes("random")
        var differs = false
        for (i in rr.indices) {
            if (rr[i] != rnd[i]) {
                differs = true
                break
            }
        }
        differs shouldBe true

        // and the COEFFICIENTS are forwarded too: an always-median serve stream must differ
        // from an always-uniform one (a mutant dropping the width/blend arguments makes
        // both fall back to the engine defaults and render identically)
        val tight = renderNotes("normal:0")
        val mixed = renderNotes("normal::1.0")
        var coeffDiffers = false
        for (i in tight.indices) {
            if (tight[i] != mixed[i]) {
                coeffDiffers = true
                break
            }
        }
        coeffDiffers shouldBe true
    }
})
