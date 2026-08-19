/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Behavioral guards for the banded best-of-M start-phase selection
 * (docs/tasks/unison-phase-pool.md §3.1–§3.2): with the pool on, the "fundamental lottery" tail
 * (notes born with a cancelled fundamental) disappears, while notes remain genuinely random.
 *
 * Measurement trick: a supersine stack with `spread = 0` and `analog = 0` sums N equal-frequency
 * sines whose normalized gains total 1, so the output is a single sine of amplitude exactly
 * K = |Σ gₙ·e^{i2πφₙ}| — the scored coherence. At 375 Hz / 48 kHz one period is exactly 128
 * samples, so the RMS over 512 samples is K/√2 with no window leakage: K = RMS·√2.
 */
class PhasePoolSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 4
    val freqHz = 375.0

    fun renderK(
        seed: Int,
        phasePool: Double,
        kMin: Double = 0.30,
        kMax: Double = 0.55,
        drawTries: Double = 5.0,
        sideAtten: Double = 0.1,
        gainJitter: Double = 0.15,
        centerJitterScale: Double = 0.4,
    ): Double {
        // The gain profile defaults to pinned literals: the DISTRIBUTION thresholds below depend
        // on it, and the SUPER*_* engine constants are retuned by ear — a tuning pass must not
        // flip those assertions. The reachability cases override with each family's own profile
        // on purpose (they validate the shipped combination).
        val sig = Ignitors.superSine(
            voices = ParamIgnitor("voices", 11.0),
            detune = ParamIgnitor("spread", 0.0),
            analog = ParamIgnitor("analog", 0.0),
            rng = Random(seed),
            sideAtten = sideAtten, gainJitter = gainJitter, centerJitterScale = centerJitterScale,
            phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
        )
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = blockFrames,
            voiceEndFrame = sampleRate + blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        var sumSq = 0.0
        for (b in 0 until blocks) {
            ctx.apply { offset = 0; length = blockFrames; voiceElapsedFrames = b * blockFrames }
            sig.generate(buffer, freqHz, ctx)
            for (i in 0 until blockFrames) {
                sumSq += buffer[i] * buffer[i]
            }
        }
        return sqrt(sumSq / (blocks * blockFrames)) * sqrt(2.0)
    }

    val seeds = 1..300

    "phasePool off - the fundamental lottery is real (holes + deep p10)" {
        val ks = seeds.map { renderK(it, phasePool = 0.0) }.sorted()
        val holes = ks.count { it < 0.15 }
        // Rayleigh with N_eff ≈ 10.6 predicts ~21 % holes; demand at least half that.
        holes shouldBeGreaterThanOrEqual 30
        ks[(ks.size * 0.10).toInt()] shouldBeLessThan 0.20 // p10 floor is deep
    }

    "phasePool on - holes are gone, p10 floor lifts into the band's reach" {
        val ks = seeds.map { renderK(it, phasePool = 1.0) }.sorted()
        // Best-of-5 predicts ~1 hole in 2400 notes; allow 2 in 300 for slack.
        ks.count { it < 0.15 } shouldBeLessThanOrEqual 2
        ks[(ks.size * 0.10).toInt()] shouldBeGreaterThan 0.25
    }

    "phasePool on - selection lands inside the band when candidates allow it" {
        // With 5 tries at N_eff ≈ 10.6 most notes find the band; the rest sit at its closest edge.
        val ks = seeds.map { renderK(it, phasePool = 1.0) }
        val inBand = ks.count { it >= 0.30 - 1e-9 && it <= 0.55 + 1e-9 }
        inBand shouldBeGreaterThanOrEqual (ks.size / 2)
        // Never above the band by more than a whisker: over-coherent (thin/buzzy) draws are rejected.
        ks.count { it > 0.60 } shouldBeLessThanOrEqual 2
    }

    "phasePool on - notes remain genuinely random (selection, not alignment)" {
        val a = renderK(1, phasePool = 1.0)
        val b = renderK(2, phasePool = 1.0)
        (abs(a - b) > 1e-6).shouldBeTrue()
    }

    "phasePool on - user-facing knobs coerce instead of throwing (inverted band, zero tries)" {
        // kMin > kMax collapses to [kMin, kMin]; drawTries 0 coerces to 1. Must render sane audio.
        val k = renderK(7, phasePool = 1.0, kMin = 0.9, kMax = 0.1, drawTries = 0.0)
        (k == k).shouldBeTrue() // NaN-guard
        k shouldBeGreaterThan 0.0
    }

    // ── Per-waveform knob threading ──────────────────────────────────────────────────────────────
    // The four knobs travel positionally through factory → subclass → shared engine for every
    // waveform, but only the supersine path is exercised by the K-statistics tests above. This
    // drives a high band vs a low band through EACH factory and asserts the fundamental follows —
    // proving phasePool/drawTries/kMin/kMax reach the shared engine per waveform.

    fun fundamentalAmp(sig: Ignitor): Double {
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = blockFrames,
            voiceEndFrame = sampleRate + blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        var re = 0.0
        var im = 0.0
        var idx = 0
        val w = TWO_PI * freqHz / sampleRate
        for (b in 0 until blocks) {
            ctx.apply { offset = 0; length = blockFrames; voiceElapsedFrames = b * blockFrames }
            sig.generate(buffer, freqHz, ctx)
            for (i in 0 until blockFrames) {
                re += buffer[i] * cos(w * idx)
                im += buffer[i] * sin(w * idx)
                idx++
            }
        }
        return 2.0 * sqrt(re * re + im * im) / idx
    }

    // (voices, spread, analog, rng, kMin, kMax, drawTries) -> pool-on ignitor, per factory.
    val factories = listOf<Pair<String, (Ignitor, Ignitor, Ignitor, Random, Double, Double, Double) -> Ignitor>>(
        "superSaw" to { v, d, a, r, lo, hi, t ->
            Ignitors.superSaw(voices = v, detune = d, analog = a, rng = r, phasePool = 1.0, drawTries = t, kMin = lo, kMax = hi)
        },
        "superRamp" to { v, d, a, r, lo, hi, t ->
            Ignitors.superRamp(voices = v, detune = d, analog = a, rng = r, phasePool = 1.0, drawTries = t, kMin = lo, kMax = hi)
        },
        "superSquare" to { v, d, a, r, lo, hi, t ->
            Ignitors.superSquare(voices = v, detune = d, analog = a, rng = r, phasePool = 1.0, drawTries = t, kMin = lo, kMax = hi)
        },
        "superTri" to { v, d, a, r, lo, hi, t ->
            Ignitors.superTri(voices = v, detune = d, analog = a, rng = r, phasePool = 1.0, drawTries = t, kMin = lo, kMax = hi)
        },
        "superSine" to { v, d, a, r, lo, hi, t ->
            Ignitors.superSine(voices = v, detune = d, analog = a, rng = r, phasePool = 1.0, drawTries = t, kMin = lo, kMax = hi)
        },
    )

    for ((name, make) in factories) {
        fun amp(lo: Double, hi: Double, tries: Double, seed: Int): Double = fundamentalAmp(
            make(
                ParamIgnitor("voices", 11.0), ParamIgnitor("spread", 0.0), ParamIgnitor("analog", 0.0),
                Random(seed), lo, hi, tries,
            )
        )

        "$name - phase pool knobs are threaded: high band beats low band on the fundamental" {
            // Averaged over a few notes so a lucky low-band draw can't flip the comparison. The
            // correct engine's ratio is ~10.6×; 6× also kills a dropped-kMax mutant (~3.2×) for
            // every family, and a dropped-drawTries mutant for the 5-try saw family (~4.3×). For
            // supertri/supersine (defaults 16/40) a dropped drawTries barely moves this ratio —
            // the shallow-vs-deep case below is the depth guard for ALL families.
            val high = (1..5).sumOf { amp(0.85, 0.95, 64.0, it) }
            val low = (1..5).sumOf { amp(0.02, 0.10, 64.0, it) }
            high shouldBeGreaterThan low * 6.0
        }

        "$name - drawTries is threaded: deep search beats a single draw at a rare band" {
            // Same band both sides — only the search depth differs. A factory (or runtime) that
            // stops threading drawTries makes both sides fall back to the family constant, and
            // the ratio collapses to ~1 for EVERY family regardless of its default depth.
            val deep = (1..5).sumOf { amp(0.85, 0.95, 64.0, it) }
            val shallow = (1..5).sumOf { amp(0.85, 0.95, 1.0, it) }
            deep shouldBeGreaterThan shallow * 1.7
        }
    }

    // ── Default-band reachability ────────────────────────────────────────────────────────────────
    // A band that candidates rarely hit silently degrades "closest to the band" into best-of-M
    // K-MAXIMIZATION — the thin/buzzy Goodhart failure §3.1 forbids. Each family's SHIPPED
    // (band, tries, gain profile) combination must land in-band for the vast majority of notes —
    // measured through the sine engine (K statistics depend only on the gain profile), with each
    // family's OWN profile constants so a per-family by-ear retune re-validates its band here.
    // NOTE: calibrated at unison 11 — in-band probability falls with voice count (N_eff ≈ v);
    // the shipped bands are calibrated for v ≈ 7–11 (see OscillatorTuning).

    class BandRow(
        val label: String,
        val kMin: Double, val kMax: Double, val tries: Double,
        val sideAtten: Double, val gainJitter: Double, val centerJitterScale: Double,
    )

    val bandRows = listOf(
        BandRow(
            "supersaw", SUPERSAW_K_MIN, SUPERSAW_K_MAX, SUPERSAW_DRAW_TRIES,
            SUPERSAW_SIDE_ATTEN, SUPERSAW_GAIN_JITTER, SUPERSAW_CENTER_JITTER_SCALE,
        ),
        BandRow(
            "superramp", SUPERRAMP_K_MIN, SUPERRAMP_K_MAX, SUPERRAMP_DRAW_TRIES,
            SUPERRAMP_SIDE_ATTEN, SUPERRAMP_GAIN_JITTER, SUPERRAMP_CENTER_JITTER_SCALE,
        ),
        BandRow(
            "supersquare", SUPERSQUARE_K_MIN, SUPERSQUARE_K_MAX, SUPERSQUARE_DRAW_TRIES,
            SUPERSQUARE_SIDE_ATTEN, SUPERSQUARE_GAIN_JITTER, SUPERSQUARE_CENTER_JITTER_SCALE,
        ),
        BandRow(
            "supertri", SUPERTRI_K_MIN, SUPERTRI_K_MAX, SUPERTRI_DRAW_TRIES,
            SUPERTRI_SIDE_ATTEN, SUPERTRI_GAIN_JITTER, SUPERTRI_CENTER_JITTER_SCALE,
        ),
        BandRow(
            "supersine", SUPERSINE_K_MIN, SUPERSINE_K_MAX, SUPERSINE_DRAW_TRIES,
            SUPERSINE_SIDE_ATTEN, SUPERSINE_GAIN_JITTER, SUPERSINE_CENTER_JITTER_SCALE,
        ),
    )

    for (row in bandRows) {
        "${row.label} - shipped band defaults are reachable: most notes land in-band, not at the fallback" {
            val inBand = (1..200).map {
                renderK(
                    it, phasePool = 1.0,
                    kMin = row.kMin, kMax = row.kMax, drawTries = row.tries,
                    sideAtten = row.sideAtten, gainJitter = row.gainJitter,
                    centerJitterScale = row.centerJitterScale,
                )
            }.count { it >= row.kMin - 1e-9 && it <= row.kMax + 1e-9 }
            inBand shouldBeGreaterThanOrEqual 160
        }
    }

    // ── Bypass rng-stream position ───────────────────────────────────────────────────────────────
    // The JVM golden fixtures (PhasePoolBypassGoldenSpec) cannot see TRAILING rng consumption: an
    // extra draw after voice init changes no sample of THIS note, but reorders every later note in
    // a session (all super-oscillators share Random.Default in production). This pins the stream
    // position on the OFF path: note-on at v voices must consume exactly v phase draws + v jitter
    // draws, nothing more. Exact equality is safe — the rng is integer-based (xorwow), bit-exact
    // on JVM and JS alike, which is why this lives in commonTest.
    "phasePool off - note-on consumes exactly the legacy rng stream (v phases + v jitters)" {
        val expected = Random(42).let { r ->
            repeat(22) { r.nextDouble() }
            r.nextDouble()
        }
        val offFactories = listOf<Pair<String, (Ignitor, Ignitor, Random) -> Ignitor>>(
            "superSaw" to { v, a, r -> Ignitors.superSaw(voices = v, analog = a, rng = r) },
            "superRamp" to { v, a, r -> Ignitors.superRamp(voices = v, analog = a, rng = r) },
            "superSquare" to { v, a, r -> Ignitors.superSquare(voices = v, analog = a, rng = r) },
            "superTri" to { v, a, r -> Ignitors.superTri(voices = v, analog = a, rng = r) },
            "superSine" to { v, a, r -> Ignitors.superSine(voices = v, analog = a, rng = r) },
        )
        for ((name, make) in offFactories) {
            val rng = Random(42)
            val sig = make(ParamIgnitor("voices", 11.0), ParamIgnitor("analog", 0.0), rng)
            val buffer = AudioBuffer(blockFrames)
            val ctx = IgniteContext(
                sampleRate = sampleRate,
                voiceDurationFrames = sampleRate,
                gateEndFrame = sampleRate,
                releaseFrames = blockFrames,
                voiceEndFrame = sampleRate + blockFrames,
                scratchBuffers = ScratchBuffers(blockFrames),
            ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }
            sig.generate(buffer, freqHz, ctx)
            withClue(name) { rng.nextDouble() shouldBe expected }
        }
    }

    // ── Note-on-only guard ───────────────────────────────────────────────────────────────────────
    // Selection must never touch already-ringing voices: a mid-note `voices` change re-enters the
    // voice-count branch with old states present, and re-drawing their phases would be a hard
    // discontinuity (click). The added voices get plain random phases (doc §6).

    "phasePool on - mid-note voice-count change keeps ringing voices' phases (no re-selection click)" {
        class VoicesParam(var value: Double) : Ignitor {
            override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double = value
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                buffer.fill(value, ctx.offset, ctx.offset + ctx.length)
            }
        }

        fun midNoteMaxDelta(seed: Int): Double {
            val voicesParam = VoicesParam(11.0)
            // Every knob pinned to literals: the 0.30 threshold's headroom scales with the note's
            // amplitude (≈ the band), so a by-ear retune of the supersine defaults must not be
            // able to flip this guard.
            val sig = Ignitors.superSine(
                voices = voicesParam,
                detune = ParamIgnitor("spread", 0.0),
                analog = ParamIgnitor("analog", 0.0),
                rng = Random(seed),
                sideAtten = 0.1, gainJitter = 0.15, centerJitterScale = 0.4,
                phasePool = 1.0, drawTries = 5.0, kMin = 0.30, kMax = 0.55,
            )
            val buffer = AudioBuffer(blockFrames)
            val ctx = IgniteContext(
                sampleRate = sampleRate,
                voiceDurationFrames = sampleRate,
                gateEndFrame = sampleRate,
                releaseFrames = blockFrames,
                voiceEndFrame = sampleRate + blockFrames,
                scratchBuffers = ScratchBuffers(blockFrames),
            )
            var maxDelta = 0.0
            var prev = 0.0
            var idx = 0
            for (b in 0 until blocks) {
                if (b == 2) {
                    voicesParam.value = 13.0
                }
                ctx.apply { offset = 0; length = blockFrames; voiceElapsedFrames = b * blockFrames }
                sig.generate(buffer, freqHz, ctx)
                for (i in 0 until blockFrames) {
                    if (idx > 0) {
                        val d = abs(buffer[i] - prev)
                        if (d > maxDelta) {
                            maxDelta = d
                        }
                    }
                    prev = buffer[i]
                    idx++
                }
            }
            return maxDelta
        }

        // Legit engine: per-sample slope ≤ 2π·375/48000 ≈ 0.05 plus a bounded renormalization step
        // at the voice-count change (measured ceiling ~0.22 over 200 seeds). A phase re-draw of the
        // ringing voices jumps far above this — but only on ~half of single seeds, so the guard
        // takes the max over 8 (kill rate > 99.5 %).
        val worst = (1..8).maxOf { midNoteMaxDelta(it) }
        worst shouldBeLessThan 0.30
    }
})
