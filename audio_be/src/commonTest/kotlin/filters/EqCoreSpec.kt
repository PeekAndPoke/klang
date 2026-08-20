/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.FreqIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.highpass
import io.peekandpoke.klang.audio_be.ignitor.lowpass
import io.peekandpoke.klang.audio_be.ignitor.notch
import io.peekandpoke.klang.audio_be.ignitor.bandpass
import io.peekandpoke.klang.audio_be.ignitor.plus
import io.peekandpoke.klang.audio_be.ignitor.times
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * ULP-0 bit-parity guards for [EqCore] against chained per-voice SVF nodes (unified-eq plan,
 * D2a/D2b): the fused core must equal the chained `SvfIgnitor`s bit-for-bit — that is the graph
 * optimizer's replacement contract.
 *
 * ORACLE CHOICE: the Ignitor-form chain (`Ignitor.lowpass(...)` etc.) driven by a deterministic
 * [BufferSourceIgnitor] — NOT the class-form `SvfLPF` — because the Ignitor form is literally
 * what the optimizer replaces (the plan's oracle-drift risk item is moot this way).
 *
 * The LOOP SHAPE bake-off closed 2026-08-19 (locals-state section-major won — see the class
 * KDoc's decision record); the suite pins the single surviving implementation. Sub-block
 * windows run as the FIRST call on fresh state (a full-block first call would mask
 * window-arithmetic bugs), CONTINUE with a full block on the same core (the production
 * voice-onset sequence), and finish with a DOUBLE-length call that proves the input-copy
 * capacity GROWS (the onset round-up makes the full block fit without growing); the
 * denormal-tail case gives `flushDenormal` a discriminating input (tiny impulse decaying
 * through the flush threshold).
 *
 * RAW_TAP (D2b) parity oracle: `Plus(chainSoFar, Times(Bandpass(input), Constant(gain)))` —
 * the exact wire-path graph of `signal.add(signal.bandpass(f, q).mul(g))`. Times has NO unity
 * short-circuit, so gain 1.0 (what the optimizer synthesizes for a bare `.add(bandpass())`)
 * is a real parity row; the position-pinned tap definition is exercised by the mid-chain and
 * full-guitar-tail rows.
 *
 * BELL (D2c) has NO legacy oracle (new math): its VALUE is pinned by response rows against
 * ground truth (peak = 10^(db/20), cut/boost reciprocity, the clamped-k +120 dB trap) and
 * its loop MECHANICS bit-exactly by the bell relation `x + m1·bp(x, q·A)` (chainOracle's
 * BELL arm) plus the 0 dB transparency and A=1 state-continuity rows.
 */
class EqCoreSpec : StringSpec({

    // The production quantum, NOT a free knob: the input-copy capacity pins below assert
    // round-up to exactly this grain (precedent: SampleVoiceOnsetSpec).
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES
    val blocks = 4
    val sr = 44100

    // Deterministic input: seeded noise + a slow ramp (both polarities, zero crossings,
    // amplitude spread). kotlin.random.Random(seed) is platform-deterministic.
    val input = DoubleArray(blockFrames * (blocks + 1)).also { d ->
        val rng = Random(42)
        for (i in d.indices) {
            d[i] = rng.nextDouble(-1.0, 1.0) * 0.7 + (i % 97) / 97.0 * 0.2 - 0.1
        }
    }

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = sr,
        voiceDurationFrames = blockFrames * 16,
        gateEndFrame = blockFrames * 16,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 16,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /** Section spec: type + freq + q (+ RAW_TAP gain, BELL db), applied to core and oracle. */
    data class Section(
        val type: Int,
        val freq: Double,
        val q: Double,
        val gain: Double = 1.0,
        val db: Double = 0.0,
    )

    fun buildCore(sections: List<Section>): EqCore =
        EqCore(sections.size).also { core ->
            sections.forEachIndexed { i, s ->
                core.configureSection(i, s.type, s.freq, s.q, s.db, s.gain, sr.toDouble())
            }
        }

    /**
     * The legacy-graph oracle: serial types fold onto the running chain; a RAW_TAP becomes
     * `Plus(chainSoFar, Times(Bandpass(input), Constant(gain)))` — the WIRE-path node (`.mul`
     * on the wire lowers to `IgnitorDsl.Times`; `TimesIgnitor` with a constant operand runs
     * `safeOut(v * k)` per sample and has NO unity short-circuit, unlike the Kotlin-API
     * `Ignitor.mul(Double)` front door, which skips safeOut at exactly 1.0 and is therefore
     * NOT the node the tap is bit-parity with). Each tap reads the ORIGINAL input through its
     * OWN BufferSourceIgnitor over the same data (BufferSourceIgnitor is stateful, and
     * independent instances model exactly what the real graph's memoized `signal` replay
     * delivers).
     */
    fun chainOracle(data: DoubleArray, sections: List<Section>, startAt: Int = 0): Ignitor {
        var acc: Ignitor = BufferSourceIgnitor(data, startAt)
        sections.forEachIndexed { idx, s ->
            acc = when (s.type) {
                EqCore.LOWPASS -> acc.lowpass(s.freq, s.q)
                EqCore.HIGHPASS -> acc.highpass(s.freq, s.q)
                EqCore.BANDPASS -> acc.bandpass(s.freq, s.q)
                EqCore.NOTCH -> acc.notch(s.freq, s.q)
                EqCore.RAW_TAP ->
                    acc + BufferSourceIgnitor(data, startAt).bandpass(s.freq, s.q) *
                        ConstantIgnitor(s.gain)
                EqCore.BELL -> {
                    // Bell RELATION oracle: bell(x) = x + m1·bp(x; freq, q·A) — the same
                    // recurrence, different tap, bit-exact wherever safeOut is the identity
                    // on the product (the response rows pin m1's VALUE against ground truth;
                    // this pins the arm's loop mechanics — together they close the m1
                    // tautology of reusing computeSvfBellCoeffs here). Position 0 only: a
                    // mid-chain bell would need a memoized chain-so-far as its source.
                    check(idx == 0) { "bell oracle only valid as the first section" }
                    check(s.db != 0.0) { "bell oracle needs db != 0 (0 dB is its own row)" }
                    val c = SvfCoeffs()
                    computeSvfBellCoeffs(s.freq, s.q, s.db, sr.toDouble(), c)
                    acc + BufferSourceIgnitor(data, startAt)
                        .bandpass(s.freq, s.q * 10.0.pow(s.db / 40.0)) *
                        ConstantIgnitor(c.m1)
                }
                else -> error("EqCoreSpec oracle has no node for type ${s.type}")
            }
        }
        return acc
    }

    /**
     * Steady-state amplitude ratio of a sine at [freqHz] through [core]: renders
     * [warmBlocks] to settle the filter (high effective Q needs ~Q/(π·fc) seconds), then
     * measures peak(out)/peak(in) over [measureBlocks]. Response rows use this to pin the
     * bell's dB math against GROUND TRUTH (10^(db/20)) — tolerances, not bits: bells are
     * new math with no legacy oracle.
     */
    fun sineGainThrough(core: EqCore, freqHz: Double, warmBlocks: Int, measureBlocks: Int): Double {
        val w = 2.0 * PI * freqHz / sr
        val buf = AudioBuffer(blockFrames)
        var peak = 0.0
        var n = 0
        repeat(warmBlocks + measureBlocks) { blk ->
            for (i in 0 until blockFrames) {
                buf[i] = 0.5 * sin(w * n)
                n++
            }
            core.process(buf, 0, blockFrames)
            if (blk >= warmBlocks) {
                for (i in 0 until blockFrames) {
                    val a = abs(buf[i])
                    if (a > peak) {
                        peak = a
                    }
                }
            }
        }
        return peak / 0.5
    }

    /**
     * Renders [blocks] full blocks through the fused core and the chained oracle from the same
     * deterministic [data] and asserts per-sample bit equality — EXCEPT NaN, which compares as
     * NaN with any payload: the JVM guarantees only "a NaN" (a fresh x86 invalid-op NaN is
     * 0xFFF8..., a propagated input NaN keeps its bits, and JIT operand commutation of
     * commutative ops may select either operand's NaN), so payload bits are outside every
     * layer's contract. A NaN state never returns to finite in an SVF, so the exception can
     * never mask a finite divergence.
     */
    fun assertChainParity(sections: List<Section>, data: DoubleArray = input) {
        val core = buildCore(sections)
        val oracle = chainOracle(data, sections)
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            data.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, 220.0, c)
            for (i in 0 until blockFrames) {
                if (!(bufCore[i].isNaN() && bufOracle[i].isNaN())) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                }
            }
            c.voiceElapsedFrames += blockFrames
        }
    }

    val singleSections = listOf(
        "lowpass" to Section(EqCore.LOWPASS, 1234.0, 1.7),
        "highpass" to Section(EqCore.HIGHPASS, 440.0, 0.707),
        "bandpass" to Section(EqCore.BANDPASS, 850.0, 0.9),
        "notch" to Section(EqCore.NOTCH, 210.0, 2.5),
    )

    // RAW_TAP joins every per-type parameterization (sub-block, denormal, pathological):
    // its arm is its own hand-written loop in the section-major body (checklist item 4).
    val tapSection = Section(EqCore.RAW_TAP, 850.0, 0.9, gain = 2.0)
    val allSections = singleSections + ("rawtap" to tapSection)

    // BELL joins the sub-block + denormal parameterizations via its relation oracle, but
    // NOT the pathological row: the bell tap is a BARE v0 + m1·v1 (m1 pre-clamped at
    // configure) while the relation oracle's MulConst applies safeOut per sample — identical
    // on sane data, divergent by design on NaN/Inf products. The bell's own pathology pin is
    // the 0 dB transparency row; the gained arm propagates NaN like every bare tap.
    val bellSection = Section(EqCore.BELL, 1234.0, 1.7, db = 6.0)
    val windowSections = allSections + ("bell" to bellSection)

    for ((secName, section) in singleSections) {
        "single $secName section is bit-equal to the chained node" {
            assertChainParity(listOf(section))
        }
    }

    "guitar-tail 4-section chain is bit-equal to the chained nodes" {
        assertChainParity(
            listOf(
                Section(EqCore.NOTCH, 210.0, 2.5),
                Section(EqCore.HIGHPASS, 440.0, 0.707),
                Section(EqCore.LOWPASS, 5300.0, 0.707),
                Section(EqCore.LOWPASS, 5300.0, 0.707),
            ),
        )
    }

    "tap-only Eq is bit-equal to the legacy parallel-boost graph" {
        assertChainParity(listOf(tapSection))
    }

    "full guitar tail (2 taps + 4 serial) is bit-equal to the legacy graph" {
        // The real chain's topology: signal.add(bp.mul()).add(bp.mul()).notch()
        // .highpass().lowpass().lowpass() — taps at positions 0 and 1 read the INPUT,
        // the serial tail runs after them (position-pinned tap definition).
        assertChainParity(
            listOf(
                Section(EqCore.RAW_TAP, 1000.0, 0.8, gain = 2.0),
                Section(EqCore.RAW_TAP, 4000.0, 0.85, gain = 5.5),
                Section(EqCore.NOTCH, 210.0, 2.5),
                Section(EqCore.HIGHPASS, 440.0, 0.707),
                Section(EqCore.LOWPASS, 5300.0, 0.707),
                Section(EqCore.LOWPASS, 5300.0, 0.707),
            ),
        )
    }

    "mid-chain tap at UNITY gain contributes at its list position" {
        // Serial work BEFORE and AFTER the tap: kills any implementation that applies
        // taps first or last instead of at the pinned list position — that is THIS row's
        // discriminator. Gain 1.0 on purpose: it is what the optimizer synthesizes for a
        // bare `.add(bandpass())`, testable only because the oracle builds the Times node
        // (the Kotlin-API `.mul(1.0)` front door short-circuits — see chainOracle). NOTE:
        // on this bounded data safeOut is the identity at unity — the unity SAFEOUT-SKIP
        // kill lives in the pathological row's "rawtap-unity" variant (NaN/Inf data); do
        // not prune that one as "covered here".
        assertChainParity(
            listOf(
                Section(EqCore.NOTCH, 210.0, 2.5),
                Section(EqCore.RAW_TAP, 850.0, 0.9, gain = 1.0),
                Section(EqCore.LOWPASS, 5300.0, 0.707),
            ),
        )
    }

    "tap gain 0 is NOT skipped" {
        // Legacy adds safeOut(v1 * 0) = SIGNED zero; on a -0.0 input sample where the
        // tap's band is positive, -0.0 + 0.0 flips to +0.0 — a gain-0 skip would keep
        // -0.0 and break bit-parity. Data: an impulse ringing the band over a bed of
        // NEGATIVE zeros, so many samples exercise the flip.
        val negZeros = DoubleArray(blockFrames * (blocks + 1)) { -0.0 }.also { it[0] = 1.0 }
        assertChainParity(
            listOf(Section(EqCore.RAW_TAP, 850.0, 0.9, gain = 0.0)),
            data = negZeros,
        )
    }

    "huge tap gain engages safeOut bit-identically" {
        // gain 1e300 pushes the per-sample product far past SAFE_MAX on ordinary input:
        // both sides must clamp identically — a core that drops the tap's safeOut lands
        // near 1e299 instead of SAFE_MAX and reddens.
        assertChainParity(listOf(Section(EqCore.RAW_TAP, 850.0, 0.9, gain = 1e300)))
    }

    // ── BELL (D2c) ──

    "BELL at 0 dB is bit-transparent, incl. -0.0 / Inf / NaN samples and sub-block windows" {
        // The EXPLICIT passthrough branch: the algebraic v0 + 0·v1 flips -0.0 to +0.0 and
        // goes NaN on Inf/NaN input (0·Inf = NaN). Transparent = raw-bit-equal for
        // EVERYTHING, payloads included (the output is a copy of the input sample).
        val data = DoubleArray(blockFrames * (blocks + 1)) { i -> input[i] }.also {
            it[7] = -0.0
            it[40] = Double.POSITIVE_INFINITY
            it[100] = Double.NaN
        }
        val core = buildCore(listOf(Section(EqCore.BELL, 850.0, 0.9, db = 0.0)))
        val buf = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            data.copyInto(buf, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(buf, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].toRawBits() shouldBe data[blk * blockFrames + i].toRawBits()
            }
        }

        // The 0 dB branch is its own hand-written windowed loop (checklist item 4): a
        // mid-block first call must stay inside its window.
        val sentinel = 123.456
        val fresh = buildCore(listOf(Section(EqCore.BELL, 850.0, 0.9, db = 0.0)))
        val winBuf = AudioBuffer(blockFrames).apply { fill(sentinel) }
        input.copyInto(winBuf, 37, 37, 37 + 64)
        fresh.process(winBuf, 37, 64)
        for (i in 0 until blockFrames) {
            if (i in 37 until 37 + 64) {
                winBuf[i].toRawBits() shouldBe input[i].toRawBits()
            } else {
                winBuf[i] shouldBe sentinel
            }
        }

        // The window's STATE half — the identity write-back makes the output
        // bounds-INSENSITIVE (an offset-blind 0 dB loop rewrites every sample with itself
        // and stays green above), so the pin is state: over the same mid-block window a
        // 0 dB bell must advance state exactly like a BANDPASS at the same freq/q (A=1
        // coefficients are identical), observed through the reconfigure channel. An
        // offset-blind loop ingests the sentinel frames outside the window and diverges.
        val bp = buildCore(listOf(Section(EqCore.BANDPASS, 850.0, 0.9)))
        val bpBuf = AudioBuffer(blockFrames).apply { fill(sentinel) }
        input.copyInto(bpBuf, 37, 37, 37 + 64)
        bp.process(bpBuf, 37, 64)

        fresh.configureSection(0, EqCore.BELL, 850.0, 0.9, 6.0, 1.0, sr.toDouble())
        bp.configureSection(0, EqCore.BELL, 850.0, 0.9, 6.0, 1.0, sr.toDouble())
        val nextA = AudioBuffer(blockFrames)
        val nextB = AudioBuffer(blockFrames)
        input.copyInto(nextA, 0, blockFrames, 2 * blockFrames)
        input.copyInto(nextB, 0, blockFrames, 2 * blockFrames)
        fresh.process(nextA, 0, blockFrames)
        bp.process(nextB, 0, blockFrames)
        for (i in 0 until blockFrames) {
            nextA[i].toRawBits() shouldBe nextB[i].toRawBits()
        }
    }

    "BELL with non-finite db is transparent (NaN-guard)" {
        // db resolves from evaluated expressions at the DSL layer — one Inf control tick
        // must not become a +300 dB blast (unguarded: A = Inf -> Butterworth fallback ->
        // m1 = SAFE_MAX) or a full null (-Inf -> m1 = -10). NaN alone cannot kill a
        // dropped-guard mutant (safeOut scrubs NaN back to 0) — the Inf legs are the teeth.
        for (db in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val core = buildCore(listOf(Section(EqCore.BELL, 850.0, 0.9, db = db)))
            val buf = AudioBuffer(blockFrames)
            input.copyInto(buf, 0, 0, blockFrames)
            core.process(buf, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].toRawBits() shouldBe input[i].toRawBits()
            }
        }
    }

    "BELL boost propagates Inf/NaN bare (no output clamp — legacy parity)" {
        // The gained tap is v0 + m1·v1 BARE (m1 clamped at configure; the per-sample path
        // adds nothing): an Inf input sample blows the output non-finite and the NaN'd
        // state stays non-finite — a well-meant safeOut/NaN scrub on the bell output
        // produces finite samples here and reddens. (The bell is excluded from the
        // pathological PARITY row because the relation oracle's MulConst DOES scrub — this
        // row is the bell's own pathology pin.)
        val data = DoubleArray(blockFrames * 2) { i -> input[i] }.also {
            it[40] = Double.POSITIVE_INFINITY
        }
        val core = buildCore(listOf(Section(EqCore.BELL, 850.0, 0.9, db = 12.0)))
        val buf = AudioBuffer(blockFrames)
        data.copyInto(buf, 0, 0, blockFrames)
        core.process(buf, 0, blockFrames)
        buf[40].isFinite() shouldBe false

        // Block 2: state is NaN — every sample must come out non-finite.
        data.copyInto(buf, 0, blockFrames, 2 * blockFrames)
        core.process(buf, 0, blockFrames)
        for (i in 0 until blockFrames) {
            buf[i].isFinite() shouldBe false
        }
    }

    "plain computeSvfCoeffs zeroes a stale bell m1 (shared-holder guard)" {
        // EqCore reuses ONE SvfCoeffs holder across configure calls; without the zero-write
        // a bell's m1 would leak into the next configured section — output-invisible today
        // (only the BELL arm reads m1, and the bell helper always rewrites it), but the
        // trap the SvfCoeffs KDoc names for future shelf sections. Pinned directly, like
        // the other output-invisible invariants (hasRawTap, inputCopyCapacity).
        val c = SvfCoeffs()
        computeSvfBellCoeffs(1000.0, 1.0, 12.0, sr.toDouble(), c)
        (c.m1 != 0.0) shouldBe true
        computeSvfCoeffs(1000.0, 1.0, sr.toDouble(), c)
        c.m1 shouldBe 0.0
    }

    "BELL boost and cut are bit-equal to the bell relation x + m1·bp(x, q·A)" {
        // Pins the gained arm's loop mechanics bit-exactly (see chainOracle's BELL arm; the
        // response rows below pin m1's VALUE against ground truth). This row ALSO carries
        // the q·A pin alone: peak = A² holds for ANY effective k (the response rows are
        // k-blind), so a swapped q/A or bare q dies only here, via the oracle's independent
        // q·A restatement.
        assertChainParity(listOf(Section(EqCore.BELL, 850.0, 0.9, db = 12.0)))
        assertChainParity(listOf(Section(EqCore.BELL, 850.0, 0.9, db = -9.0)))
    }

    "BELL peak gain at fc is 10^(db/20)" {
        for (db in listOf(12.0, -12.0)) {
            val core = buildCore(listOf(Section(EqCore.BELL, 1000.0, 1.0, db = db)))
            val ratio = sineGainThrough(core, 1000.0, warmBlocks = 40, measureBlocks = 10)
            val expected = 10.0.pow(db / 20.0)
            ratio shouldBe (expected plusOrMinus expected * 0.02)
        }
    }

    "BELL cut/boost cancel at fc (reciprocal within the unclamped region)" {
        val core = buildCore(
            listOf(
                Section(EqCore.BELL, 1000.0, 1.0, db = 9.0),
                Section(EqCore.BELL, 1000.0, 1.0, db = -9.0),
            ),
        )
        val ratio = sineGainThrough(core, 1000.0, warmBlocks = 40, measureBlocks = 10)
        ratio shouldBe (1.0 plusOrMinus 0.02)
    }

    "BELL +120 dB at q=10 peaks at exactly 1e6 — m1 must come from the CLAMPED k" {
        // q·A = 10·10^3 clamps to 200. m1 from the CLAMPED k keeps peak = A² = 1e6 exact; a
        // recomputed m1 = (A²−1)/(q·A) lands ~34 dB off (factor ~50) and reddens. Effective
        // Q=200 settles in ~Q/(π·fc) ≈ 64 ms — hence the long warmup.
        val core = buildCore(listOf(Section(EqCore.BELL, 1000.0, 10.0, db = 120.0)))
        val ratio = sineGainThrough(core, 1000.0, warmBlocks = 80, measureBlocks = 10)
        ratio shouldBe (1e6 plusOrMinus 5e4)
    }

    "BELL at db=5000 stays finite and capped (safeOut on m1)" {
        // A is finite (10^125) but an unbounded m1 = k·(A²−1) ≈ 5e247 (k = 0.005 at the
        // q·A clamp) — the configure-time cap holds m1 at SAFE_MAX (1e15), bounding output
        // near |v0| + SAFE_MAX·|v1|. Uncapped, samples reach ~1e248; capped they stay far
        // below 1e17 on this input. isFinite() alone would NOT catch a dropped cap (the
        // uncapped m1 is still finite) — the bound is what discriminates.
        val core = buildCore(listOf(Section(EqCore.BELL, 1000.0, 1.0, db = 5000.0)))
        val buf = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            input.copyInto(buf, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(buf, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].isFinite() shouldBe true
                (abs(buf[i]) < 1e17) shouldBe true
            }
        }
    }

    "BELL at 0 dB advances state at the A=1 coefficients (click-free zero crossing)" {
        // A db crossing zero mid-note must not freeze state: at A=1 the bell's recurrence
        // equals a BANDPASS at the same (freq, q) bit-exactly (identical coefficients,
        // different tap). So a bell that spent block 0 at 0 dB must continue at db=6
        // EXACTLY like a bandpass that processed the same block — a skip-freeze mutant
        // (0 dB branch emitting v0 without running the recurrence) diverges here.
        val x = buildCore(listOf(Section(EqCore.BELL, 1234.0, 1.7, db = 0.0)))
        val y = buildCore(listOf(Section(EqCore.BANDPASS, 1234.0, 1.7)))
        val bufX = AudioBuffer(blockFrames)
        val bufY = AudioBuffer(blockFrames)
        input.copyInto(bufX, 0, 0, blockFrames)
        input.copyInto(bufY, 0, 0, blockFrames)
        x.process(bufX, 0, blockFrames)
        y.process(bufY, 0, blockFrames)

        x.configureSection(0, EqCore.BELL, 1234.0, 1.7, 6.0, 1.0, sr.toDouble())
        y.configureSection(0, EqCore.BELL, 1234.0, 1.7, 6.0, 1.0, sr.toDouble())
        input.copyInto(bufX, 0, blockFrames, 2 * blockFrames)
        input.copyInto(bufY, 0, blockFrames, 2 * blockFrames)
        x.process(bufX, 0, blockFrames)
        y.process(bufY, 0, blockFrames)
        for (i in 0 until blockFrames) {
            bufX[i].toRawBits() shouldBe bufY[i].toRawBits()
        }
    }

    "sub-block FIRST call windows correctly, then continues and GROWS the tap copy" {
        // Parameterized over EVERY section type: each type has its own hand-written
        // windowed loop (checklist item 4). For RAW_TAP the first call is also the
        // input-copy sizing case — an ABSOLUTE-indexed read of the copy returns the WRONG
        // samples on a mid-block first call (shifted by offset, zero-fill past length),
        // SILENTLY on both platforms (the capacity round-up keeps it in bounds); the value
        // comparison below is what catches it.
        for ((_, single) in windowSections) {
            val sections = listOf(single)
            val offset = 37
            val length = 64
            val sentinel = 123.456

            val core = buildCore(sections)
            val bufCore = AudioBuffer(blockFrames).apply { fill(sentinel) }
            input.copyInto(bufCore, offset, offset, offset + length)
            core.process(bufCore, offset, length)

            val oracle = chainOracle(input, sections, startAt = offset)
            val bufOracle = AudioBuffer(blockFrames).apply { fill(sentinel) }
            val c = ctx().apply {
                this.offset = offset
                this.length = length
                voiceElapsedFrames = -offset // production mid-block-onset shape
            }
            oracle.generate(bufOracle, 220.0, c)

            for (i in 0 until blockFrames) {
                if (i in offset until offset + length) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                } else {
                    bufCore[i] shouldBe sentinel
                    bufOracle[i] shouldBe sentinel
                }
            }

            // The onset round-up must have allocated a FULL quantum for the short window
            // (one alloc per onset, not two — output-invisible, so pinned directly, same
            // rationale as the hasRawTap lifecycle row).
            if (single.type == EqCore.RAW_TAP) {
                core.inputCopyCapacity shouldBe blockFrames
            }

            // SECOND call on the SAME core: the full block that follows a mid-block onset
            // in production (Voice.render hands a short window first, then full blocks) —
            // cursor/state continuity across differing window sizes. NOT the grow branch:
            // thanks to the onset round-up the full block already fits (that is the point);
            // only the THIRD call below can prove grow-vs-latch.
            val bufCore2 = AudioBuffer(blockFrames)
            val bufOracle2 = AudioBuffer(blockFrames)
            input.copyInto(bufCore2, 0, offset + length, offset + length + blockFrames)
            core.process(bufCore2, 0, blockFrames)
            c.offset = 0
            c.length = blockFrames
            c.voiceElapsedFrames = length
            oracle.generate(bufOracle2, 220.0, c)
            c.voiceElapsedFrames += blockFrames

            for (i in 0 until blockFrames) {
                bufCore2[i].toRawBits() shouldBe bufOracle2[i].toRawBits()
            }

            // THIRD call at DOUBLE length: the capacity-GROW branch. The onset capacity is
            // rounded up to the quantum grain (so full-block production never grows twice) —
            // which also means only a LARGER-than-capacity window can prove the guard grows
            // instead of latching (a first-call-latch mutant dies here: the oversized
            // copyInto THROWS on both platforms). The oracle runs the same span as two full
            // blocks — bit-identical: serial state is continuous, and the tap reads the same
            // input values in the same order either way.
            val bufCore3 = AudioBuffer(2 * blockFrames)
            input.copyInto(
                bufCore3,
                0,
                offset + length + blockFrames,
                offset + length + 3 * blockFrames,
            )
            core.process(bufCore3, 0, 2 * blockFrames)

            val bufOracle3 = AudioBuffer(blockFrames)
            for (half in 0 until 2) {
                c.offset = 0
                c.length = blockFrames
                oracle.generate(bufOracle3, 220.0, c)
                for (i in 0 until blockFrames) {
                    bufCore3[half * blockFrames + i].toRawBits() shouldBe
                        bufOracle3[i].toRawBits()
                }
                c.voiceElapsedFrames += blockFrames
            }

            if (single.type == EqCore.RAW_TAP) {
                core.inputCopyCapacity shouldBe 2 * blockFrames
            }
        }
    }

    "per-block cutoff changes stay bit-equal and stable" {
        // Expression-backed cutoffs recompute per block on the Ignitor side (its cache
        // predicate is Param-only); the core mirrors that by reconfiguring per block.
        // Oracle cutoff = the voice frequency itself (FreqIgnitor), swept per block.
        val freqs = listOf(500.0, 1500.0, 500.0, 3000.0)
        val core = EqCore(1)
        val oracle = BufferSourceIgnitor(input).lowpass(FreqIgnitor)
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        freqs.forEachIndexed { blk, f ->
            core.configureSection(0, EqCore.LOWPASS, f, 0.707, 0.0, 1.0, sr.toDouble())
            input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, f, c)
            for (i in 0 until blockFrames) {
                bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
            }
            c.voiceElapsedFrames += blockFrames
        }
    }

    "denormal tail flushes bit-identically" {
        // Tiny impulse then silence: state decays through the flush threshold within the
        // first block — the discriminating input for flushDenormal. Parameterized over
        // EVERY type: each type's loop duplicates the state update, and the recurrence
        // (where the flush lives) is tap-independent.
        val tiny = DoubleArray(blockFrames * (blocks + 1)).also { it[0] = 1e-14 }
        for ((_, single) in windowSections) {
            assertChainParity(
                listOf(Section(single.type, 1234.0, 1.7, gain = 2.0, db = single.db)),
                data = tiny,
            )
        }
    }

    "extreme freq/q inherit the shared clamps bit-identically" {
        // The core must never add or remove a clamp vs the Ignitor path — pinned at the
        // values where a well-meaning guard would diverge (NaN -> 1000 Hz fallback,
        // 1e9 -> Nyquist-1, q NaN -> Butterworth, q 1e9 -> 200, q 0 -> 0.1).
        val extremes = listOf(
            Section(EqCore.LOWPASS, Double.NaN, 1.0),
            Section(EqCore.LOWPASS, 1e9, 1.0),
            Section(EqCore.HIGHPASS, 440.0, Double.NaN),
            Section(EqCore.BANDPASS, 850.0, 1e9),
            Section(EqCore.NOTCH, 210.0, 0.0),
        )
        for (section in extremes) {
            assertChainParity(listOf(section))
        }
    }

    "unknown section type is PASSTHROUGH" {
        // Garbage types — POSITIVE and NEGATIVE (-42 is arbitrary garbage, the UNCONFIGURED
        // sentinel has its own dedicated case) — must neither invent gain nor notch: output
        // bit-equal to input. The state half is its own case below. History: every reserved
        // ordinal that graduated (RAW_TAP in D2b, BELL in D2c) reddened this row on arrival,
        // the designed loud failure. Ordinal 6 is the NEXT append-only slot — implementing
        // it reddens this row and moves the tripwire to 7.
        for (unknownType in listOf(6, 99, -42)) {
            val core = EqCore(1).also {
                it.configureSection(0, unknownType, 1000.0, 1.0, 6.0, 1.5, sr.toDouble())
            }
            val buf = AudioBuffer(blockFrames)
            input.copyInto(buf, 0, 0, blockFrames)
            core.process(buf, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].toRawBits() shouldBe input[i].toRawBits()
            }
        }
    }

    "non-finite and huge input samples propagate bit-identically" {
        // The signal path is CLAMP-FREE (raw engine — the class KDoc contract): huge
        // finite values, Inf, and NaN must flow through the fused core exactly as through
        // the chained nodes — EXCEPT a RAW_TAP's contribution, which inherits Times's
        // safeOut (clamp + NaN scrub) on BOTH sides: the tap arm is the one clamped arm in
        // the core, and its clamp is parity, not protection. A well-meant NaN scrub or
        // output coerce anywhere else reddens here (a scrubbed sample is finite where the
        // oracle's is NaN — the NaN-payload exception in assertChainParity cannot mask it).
        // Each pathology gets its OWN data variant hitting CLEAN state mid-array: SVF state
        // never recovers from Inf/NaN, so a single escalating array masks every pathology
        // after the first (proven live — a NaN-scrub mutation survived because the NaN
        // arrived after an Inf had already NaN'd the state). The bake-off-era escalating
        // array also caught the NaN-payload issue (one loop shape propagated the input's
        // 0x7FF8... NaN where another produced x86's fresh 0xFFF8... — both NaN, both
        // correct, JIT-dependent which); the split variants no longer mix payload families,
        // so assertChainParity's NaN exception is a FORWARD guard against platform/JIT
        // variation, not currently exercised — do not "clean it up".
        // The extra UNITY-gain tap arms the gain==1.0 safeOut-skip mutant: on NaN data the
        // legacy Times scrubs (safeOut(NaN * 1.0) = 0.0) while a skipping core adds bare NaN
        // — normal-amplitude data cannot tell the two apart at unity.
        val pathologySections = allSections + ("rawtap-unity" to Section(EqCore.RAW_TAP, 850.0, 0.9, gain = 1.0))
        for (pathology in listOf(1e12, Double.POSITIVE_INFINITY, Double.NaN)) {
            val wild = DoubleArray(blockFrames * (blocks + 1)) { i -> input[i] }.also {
                it[40] = pathology
            }
            for ((_, single) in pathologySections) {
                assertChainParity(listOf(single), data = wild)
            }
        }
    }

    "disableSection retires a section to PASSTHROUGH and zeroes its state" {
        // The sanctioned retire call for pooled cores: after disableSection the slot must
        // pass block 2 through bit-untouched (type back to UNCONFIGURED).
        val core = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
        val buf = AudioBuffer(blockFrames)
        input.copyInto(buf, 0, 0, blockFrames)
        core.process(buf, 0, blockFrames)

        core.disableSection(0)
        input.copyInto(buf, 0, blockFrames, 2 * blockFrames)
        core.process(buf, 0, blockFrames)
        for (i in 0 until blockFrames) {
            buf[i].toRawBits() shouldBe input[blockFrames + i].toRawBits()
        }

        // Re-enable: state was ZEROED on disable (KDoc — releasing stale pre-disable
        // integrator energy here would thump), so block 3 must equal a FRESH core's
        // first block bit-for-bit.
        core.configureSection(0, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
        val fresh = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
        val bufFresh = AudioBuffer(blockFrames)
        input.copyInto(buf, 0, 2 * blockFrames, 3 * blockFrames)
        input.copyInto(bufFresh, 0, 2 * blockFrames, 3 * blockFrames)
        core.process(buf, 0, blockFrames)
        fresh.process(bufFresh, 0, blockFrames)
        for (i in 0 until blockFrames) {
            buf[i].toRawBits() shouldBe bufFresh[i].toRawBits()
        }
    }

    "hasRawTap tracks the tap lifecycle" {
        // The flag gates the input copy. Stuck TRUE it is output-invisible (the copy is
        // redundant, not wrong) but silently taxes every serial-only core — the benchmark
        // numbers the loop-shape decision was made on assume serial cores pay no copy. So
        // the LIFECYCLE is pinned directly (internal visibility), not via output parity.
        val core = EqCore(2).also {
            it.configureSection(0, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
        }
        core.hasRawTap shouldBe false

        core.configureSection(1, EqCore.RAW_TAP, 850.0, 0.9, 0.0, 2.0, sr.toDouble())
        core.hasRawTap shouldBe true

        core.disableSection(1)
        core.hasRawTap shouldBe false

        core.configureSection(1, EqCore.RAW_TAP, 850.0, 0.9, 0.0, 2.0, sr.toDouble())
        core.configureSection(1, EqCore.NOTCH, 210.0, 2.5, 0.0, 1.0, sr.toDouble())
        core.hasRawTap shouldBe false // reconfiguring AWAY from a tap also clears the flag

        // TWO taps, one disabled: the flag must stay TRUE — a wrongly-CLEARED flag skips
        // captureInput and the surviving tap silently filters a one-block-stale copy (no
        // crash, wrong audio). This is the direction a blanket `hasRawTap = false` in
        // disableSection breaks.
        core.configureSection(0, EqCore.RAW_TAP, 850.0, 0.9, 0.0, 2.0, sr.toDouble())
        core.configureSection(1, EqCore.RAW_TAP, 1000.0, 0.8, 0.0, 2.0, sr.toDouble())
        core.disableSection(1)
        core.hasRawTap shouldBe true

        // ...and the MIRROR: disable the LOWER-indexed tap with the survivor above it — a
        // recompute scan that stops one slot short goes stuck-false only in this direction.
        core.configureSection(0, EqCore.RAW_TAP, 850.0, 0.9, 0.0, 2.0, sr.toDouble())
        core.configureSection(1, EqCore.RAW_TAP, 1000.0, 0.8, 0.0, 2.0, sr.toDouble())
        core.disableSection(0)
        core.hasRawTap shouldBe true
    }

    "insane process windows are ignored" {
        // Same house fall-through as the index guards: a broken surface handing a window
        // past the buffer (or negative) must not throw — on the JS worklet an escaped
        // exception kills the whole processor, not one voice. "Ignored" has three legs, all
        // pinned: buffer bit-untouched, NOTHING allocated, and state untouched.
        val core = buildCore(listOf(tapSection))
        val buf = AudioBuffer(blockFrames)
        input.copyInto(buf, 0, 0, blockFrames)

        core.process(buf, 64, blockFrames) // reaches past the end
        core.process(buf, -1, 32) // negative offset
        core.process(buf, 0, -5) // negative length
        core.process(buf, Int.MAX_VALUE, 1) // naive `offset + length` wraps NEGATIVE here

        for (i in 0 until blockFrames) {
            buf[i].toRawBits() shouldBe input[i].toRawBits()
        }

        // Rejected windows allocate nothing (the guard sits BEFORE captureInput).
        core.inputCopyCapacity shouldBe 0

        // ...and leave STATE untouched: a warm core hit by insane calls must continue
        // exactly like a reference that never saw them — kills a well-meant
        // reset-on-insane "improvement" (which would turn a surface bug into an audible
        // discontinuity on the NEXT good block).
        val ref = buildCore(listOf(tapSection))
        val bufCore = AudioBuffer(blockFrames)
        val bufRef = AudioBuffer(blockFrames)
        input.copyInto(bufCore, 0, 0, blockFrames)
        input.copyInto(bufRef, 0, 0, blockFrames)
        core.process(bufCore, 0, blockFrames)
        ref.process(bufRef, 0, blockFrames)

        core.process(bufCore, 64, blockFrames) // insane, between the two valid blocks
        core.process(bufCore, 0, -5)

        input.copyInto(bufCore, 0, blockFrames, 2 * blockFrames)
        input.copyInto(bufRef, 0, blockFrames, 2 * blockFrames)
        core.process(bufCore, 0, blockFrames)
        ref.process(bufRef, 0, blockFrames)
        for (i in 0 until blockFrames) {
            bufCore[i].toRawBits() shouldBe bufRef[i].toRawBits()
        }
    }

    "out-of-range section indices are ignored" {
        // configureSection/disableSection run at control rate on the AUDIO thread — they
        // must never throw (house fall-through; JS typed arrays silently drop OOB writes,
        // the guard makes JVM behave the same). The core must act as if the calls never
        // happened.
        val core = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
        core.configureSection(1, EqCore.HIGHPASS, 440.0, 0.707, 0.0, 1.0, sr.toDouble())
        core.configureSection(-1, EqCore.HIGHPASS, 440.0, 0.707, 0.0, 1.0, sr.toDouble())
        core.disableSection(1)
        core.disableSection(-1)

        val oracle = chainOracle(input, listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        input.copyInto(bufCore, 0, 0, blockFrames)
        core.process(bufCore, 0, blockFrames)
        oracle.generate(bufOracle, 220.0, c)
        for (i in 0 until blockFrames) {
            bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
        }
    }

    "unconfigured sections are PASSTHROUGH" {
        // The surface contract says configure EVERY section before process(); the BREACH
        // degradation is pinned here: types default to UNCONFIGURED (-1), which passes
        // through. A zero-filled type default would render all-zero-coefficient LOWPASS
        // SILENCE on the unconfigured slot and kill the whole chain. The hole sits
        // BEFORE the configured section: "skip this section" and "abort the chain" are
        // only distinguishable when configured work follows the hole.
        val core = EqCore(2).also {
            // section 0 deliberately NOT configured
            it.configureSection(1, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
        }
        val oracle = chainOracle(input, listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, 220.0, c)
            for (i in 0 until blockFrames) {
                bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
            }
            c.voiceElapsedFrames += blockFrames
        }
    }

    "unknown section mid-chain is transparent to the sections after it" {
        // With a single section, "skip THIS section" and "skip the REST" are
        // indistinguishable — only a chain with sections AFTER the unknown one can tell
        // them apart: the trailing highpass must still run.
        val lp = Section(EqCore.LOWPASS, 1234.0, 1.7)
        val hp = Section(EqCore.HIGHPASS, 440.0, 0.707)
        val core = EqCore(3).also {
            it.configureSection(0, lp.type, lp.freq, lp.q, 0.0, 1.0, sr.toDouble())
            it.configureSection(1, 99, 1000.0, 1.0, 6.0, 1.5, sr.toDouble())
            it.configureSection(2, hp.type, hp.freq, hp.q, 0.0, 1.0, sr.toDouble())
        }
        val oracle = chainOracle(input, listOf(lp, hp))
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, 220.0, c)
            for (i in 0 until blockFrames) {
                bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
            }
            c.voiceElapsedFrames += blockFrames
        }
    }

    "unknown section leaves its state untouched" {
        // Output parity alone can't see state: the else arm passes through, so a mutation
        // that runs the recurrence but still emits the input stays green above.
        // Reconfiguring the slot to LOWPASS is the observation channel — configureSection
        // keeps state (snap semantics), so a core whose unknown section secretly advanced
        // ic1/ic2 diverges from a fresh core on the very next block. Parameterized over
        // the next append-only ordinal (6), garbage-positive (99) and garbage-negative
        // (-42): configureSection stores real coefficients regardless of type, so a
        // mis-dispatched unknown type advances state invisibly.
        for (unknownType in listOf(6, 99, -42)) {
            val core = EqCore(1).also {
                it.configureSection(0, unknownType, 1234.0, 1.7, 6.0, 1.5, sr.toDouble())
            }
            val warm = AudioBuffer(blockFrames)
            input.copyInto(warm, 0, 0, blockFrames)
            core.process(warm, 0, blockFrames)

            core.configureSection(0, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
            val fresh = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)))
            val bufCore = AudioBuffer(blockFrames)
            val bufFresh = AudioBuffer(blockFrames)
            input.copyInto(bufCore, 0, blockFrames, 2 * blockFrames)
            input.copyInto(bufFresh, 0, blockFrames, 2 * blockFrames)
            core.process(bufCore, 0, blockFrames)
            fresh.process(bufFresh, 0, blockFrames)
            for (i in 0 until blockFrames) {
                bufCore[i].toRawBits() shouldBe bufFresh[i].toRawBits()
            }
        }
    }

    "reset zeroes state but keeps the curve" {
        // Four sections with distinct types: a per-slot reset shortcut (zeroing only
        // slot 0) leaves slots 1..3 ringing and reddens here — the pooling scenario
        // reset() exists for is multi-section.
        val sections = listOf(
            Section(EqCore.NOTCH, 210.0, 2.5),
            Section(EqCore.HIGHPASS, 440.0, 0.707),
            Section(EqCore.BANDPASS, 850.0, 0.9),
            Section(EqCore.LOWPASS, 1234.0, 1.7),
        )
        val fresh = buildCore(sections)
        val recycled = buildCore(sections)

        // Dirty the recycled core's state, then reset — it must match a fresh core
        // bit-for-bit WITHOUT reconfiguration (coefficients persist).
        val warm = AudioBuffer(blockFrames)
        input.copyInto(warm, 0, 0, blockFrames)
        recycled.process(warm, 0, blockFrames)
        recycled.reset()

        val bufFresh = AudioBuffer(blockFrames)
        val bufRecycled = AudioBuffer(blockFrames)
        input.copyInto(bufFresh, 0, 0, blockFrames)
        input.copyInto(bufRecycled, 0, 0, blockFrames)
        fresh.process(bufFresh, 0, blockFrames)
        recycled.process(bufRecycled, 0, blockFrames)
        for (i in 0 until blockFrames) {
            bufRecycled[i].toRawBits() shouldBe bufFresh[i].toRawBits()
        }
    }
})

/**
 * Test helper: plays a fixed array window-by-window — the deterministic input source for the
 * Ignitor-form oracle chains. Each [generate] call copies the next `length` samples into
 * `[offset, offset+length)` and advances the cursor (matching how a real upstream advances
 * per block). [startAt] seats the cursor for sub-block first-call cases.
 */
internal class BufferSourceIgnitor(
    private val data: DoubleArray,
    startAt: Int = 0,
) : Ignitor {
    private var pos = startAt

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = data[pos++]
        }
    }
}
