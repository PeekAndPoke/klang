/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.EqCore
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.IgnitorDsl.EqSection
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.maxReleaseSec
import io.peekandpoke.klang.audio_bridge.notch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import kotlin.random.Random

/**
 * End-to-end ULP-0 parity for the D3b adapter path: `IgnitorDsl.Eq → EqIgnitor → EqCore`
 * against the CHAINED DSL built through the very same runtime (`toExciter()` on both sides —
 * this is the graph the optimizer will replace, wired for real). EqCore's own bit-identity is
 * proven at the core level (EqCoreSpec); this spec pins the ADAPTER: param resolution through
 * [Ignitors.readParam] (incl. the tracking-HP `Freq`-backed cutoff across voice frequencies),
 * oscParams overrides reaching section params, the per-section static/dynamic coefficient
 * cache classification, the adapter-owned 0 dB bell skip, and the production sub-block shape.
 *
 * NaN compares as NaN (payload bits are outside every layer's contract — see EqCoreSpec).
 */
class EqIgnitorSpec : StringSpec({

    val blockFrames = 128
    val blocks = 4
    val sr = 44100

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

    /**
     * Largest absolute sample difference between two DSL trees across EVERY rendered block
     * (scanned inside the render loop — a first-block-only divergence must not hide behind
     * a later block), advancing voiceElapsedFrames like [assertDslParity] so envelope-bearing
     * trees see real time.
     */
    fun maxAbsDiff(a: IgnitorDsl, b: IgnitorDsl): Double {
        val ea = a.toExciter(null)
        val eb = b.toExciter(null)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)
        val ca = ctx()
        val cb = ctx()
        var worst = 0.0
        repeat(blocks) {
            ea.generate(bufA, 220.0, ca)
            eb.generate(bufB, 220.0, cb)
            for (i in 0 until blockFrames) {
                val d = abs(bufA[i] - bufB[i])
                if (d > worst) {
                    worst = d
                }
            }
            ca.voiceElapsedFrames += blockFrames
            cb.voiceElapsedFrames += blockFrames
        }
        return worst
    }

    /**
     * Builds BOTH trees through toExciter and renders [blocks] blocks at each voice
     * frequency (fresh exciters per frequency — per-voice state), asserting raw-bit
     * equality sample by sample.
     */
    fun assertDslParity(
        chained: IgnitorDsl,
        fused: IgnitorDsl,
        oscParams: Map<String, Double>? = null,
        freqs: List<Double> = listOf(220.0),
    ) {
        for (f in freqs) {
            val a = chained.toExciter(oscParams)
            val b = fused.toExciter(oscParams)
            val bufA = AudioBuffer(blockFrames)
            val bufB = AudioBuffer(blockFrames)
            val ca = ctx()
            val cb = ctx()
            repeat(blocks) {
                a.generate(bufA, f, ca)
                b.generate(bufB, f, cb)
                for (i in 0 until blockFrames) {
                    if (!(bufA[i].isNaN() && bufB[i].isNaN())) {
                        bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
                    }
                }
                ca.voiceElapsedFrames += blockFrames
                cb.voiceElapsedFrames += blockFrames
            }
        }
    }

    val c = { v: Double -> IgnitorDsl.Constant(v) }

    "single bandpass section is bit-equal to the chained node" {
        // The one variant no other row renders — a swapped Bandpass mapping arm in the
        // runtime (e.g. -> NOTCH) would ship a spectrally INVERTED filter with every other
        // row green.
        val chained = IgnitorDsl.Bandpass(IgnitorDsl.Sawtooth(), c(1200.0), c(3.0))
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Bandpass(c(1200.0), c(3.0))),
        )
        assertDslParity(chained, fused)
    }

    "DYNAMIC section param (oscillator-backed cutoff) reconfigures per block bit-equally" {
        // The scratch-render path: an oscillator-backed cutoff makes readParam RENDER the
        // param subtree per block (stateful LFO phase advances, cutoff sweeps, the section
        // reconfigures every block). Deterministic ON PURPOSE (analog = 0 everywhere):
        // cross-TREE bit parity cannot involve the shared global Random at all — two
        // independently built exciters draw from one stream in sequence, so any
        // value-bearing draw (active drift, noise) diverges the trees REGARDLESS of adapter
        // ordering. The ordering property itself is pinned white-box in the next row.
        fun lfoCutoff() = IgnitorDsl.Plus(
            c(2000.0),
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(2.0)), c(500.0)),
        )
        val chained = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sawtooth(),
            cutoffHz = lfoCutoff(),
            q = c(0.9),
        )
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Lowpass(lfoCutoff(), c(0.9))),
        )
        assertDslParity(chained, fused)
    }

    "chained vs fused stays bit-equal WITH ACTIVE DRIFT (same-seeded voice streams)" {
        // The BLACK-BOX upgrade of the RNG-order probe below, possible only since
        // seeded-voice-rng (D3c): each tree gets its OWN equal-seeded stream, so parity now
        // REQUIRES the adapter to make its draws in the chained order (upstream's drift
        // seeds before the LFO cutoff's) — a reorder hands the saw the LFO's numbers and
        // diverges, no probes needed. (Relaxes dossier item 10 for same-seed setups.)
        fun lfoCutoff() = IgnitorDsl.Plus(
            c(2000.0),
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(2.0), analog = c(0.3)), c(500.0)),
        )
        val chained = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sawtooth(analog = c(0.7)),
            cutoffHz = lfoCutoff(),
            q = c(0.9),
        )
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(analog = c(0.7)),
            sections = listOf(EqSection.Lowpass(lfoCutoff(), c(0.9))),
        )

        val ra = Random(11)
        val rb = Random(11)
        // (The LFO Sine inside lfoCutoff carries analog too — see lfoCutoff — so its draws
        // are load-bearing by construction, not by SineIgnitor's incidental unconditional
        // drift init.)
        val a = chained.toExciter(random = ra)
        val b = fused.toExciter(random = rb)

        fun mk(r: Random) = IgniteContext(
            sampleRate = sr,
            voiceDurationFrames = blockFrames * 16,
            gateEndFrame = blockFrames * 16,
            releaseFrames = 0,
            voiceEndFrame = blockFrames * 16,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = r,
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }

        val ca = mk(ra)
        val cb = mk(rb)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)
        repeat(blocks) {
            a.generate(bufA, 220.0, ca)
            b.generate(bufB, 220.0, cb)
            for (i in 0 until blockFrames) {
                bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
            }
            ca.voiceElapsedFrames += blockFrames
            cb.voiceElapsedFrames += blockFrames
        }
    }

    "upstream renders BEFORE the first section param resolves (RNG draw order)" {
        // The property behind the chained path's sound: on block 0 the SOURCE seeds its
        // drift/unison/noise draws from the voice's stream BEFORE any section param's
        // scratch render draws (SvfIgnitor renders upstream first, then reads params). No
        // cross-tree parity row can observe this (see the previous row's note), so the call
        // ORDER is pinned directly with probe ignitors.
        val order = mutableListOf<String>()
        val upstreamProbe = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                order.add("upstream")
                buffer.fill(0.0, ctx.offset, ctx.offset + ctx.length)
            }
        }
        val paramProbe = object : Ignitor {
            // controlRateValueOrNull stays null -> readParam takes the scratch-render path.
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                order.add("param")
                buffer.fill(1000.0, ctx.offset, ctx.offset + ctx.length)
            }
        }
        val eq = EqIgnitor(
            upstream = upstreamProbe,
            sections = listOf(EqIgnitor.Section(EqCore.LOWPASS, paramProbe, ConstantIgnitor(1.0))),
        )
        eq.generate(AudioBuffer(blockFrames), 220.0, ctx())
        order shouldBe listOf("upstream", "param")
    }

    "pitch mod over an Eq reaches the source; section params stay mod-free" {
        // Both withMod halves in one row (every other row builds the Eq at the tree ROOT,
        // where accumulatedMod is null and withMod == noMod): (a) the vibrato must reach the
        // fused source through Eq's inner (a noMod inner goes dead flat); (b) the
        // oscillator-backed cutoff must NOT receive the mod (the chained arm noMods its
        // params — a withMod section param vibratos the LFO and diverges).
        fun lfoCutoff() = IgnitorDsl.Plus(
            c(2000.0),
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(2.0)), c(500.0)),
        )
        val chained = IgnitorDsl.Vibrato(
            inner = IgnitorDsl.Lowpass(
                inner = IgnitorDsl.Sawtooth(),
                cutoffHz = lfoCutoff(),
                q = c(0.9),
            ),
            rate = c(5.0),
            depth = c(0.3),
        )
        val fused = IgnitorDsl.Vibrato(
            inner = IgnitorDsl.Eq(
                inner = IgnitorDsl.Sawtooth(),
                sections = listOf(EqSection.Lowpass(lfoCutoff(), c(0.9))),
            ),
            rate = c(5.0),
            depth = c(0.3),
        )
        assertDslParity(chained, fused)
    }

    "static skip machinery is LIVE across blocks (white-box counters)" {
        // Both perf invariants are output-invisible (the core's explicit branch is equally
        // transparent; reconfiguring a static section re-derives identical coefficients),
        // so they are pinned directly, per the hasRawTap/inputCopyCapacity precedent — over
        // TWO blocks, because one block cannot distinguish "configures once per voice" from
        // "configures every block". Direct construction: toExciter wraps in
        // MemoizingIgnitor, which hides the adapter.
        val eq = EqIgnitor(
            upstream = ConstantIgnitor(0.0),
            sections = listOf(
                EqIgnitor.Section(EqCore.LOWPASS, ConstantIgnitor(2000.0), ConstantIgnitor(1.0)),
                EqIgnitor.Section(
                    EqCore.BELL, ConstantIgnitor(850.0), ConstantIgnitor(0.9),
                    db = ParamIgnitor("d", 0.0),
                ),
            ),
        )
        val buf = AudioBuffer(blockFrames)
        val c1 = ctx()
        eq.generate(buf, 220.0, c1)
        c1.voiceElapsedFrames += blockFrames
        eq.generate(buf, 220.0, c1)

        // Block 0: both sections configure (bell -> disabled, counted once). Block 1: both
        // are static+configured and skip. A dropped `configured[i] = true` re-runs the
        // branch every block: bell count 2, configure-skips 0.
        eq.staticZeroDbSkips shouldBe 1
        eq.staticConfigureSkips shouldBe 2
    }

    "parity holds at 48 kHz (the deployment worklet's rate)" {
        // Every other row runs at 44.1k — a sample-rate literal accidentally hardcoded in
        // the adapter (instead of ctx.sampleRate) would mistune every fused section on the
        // 48k worklet with the whole suite green. One chained-vs-fused row at 48k pins the
        // plumbing against the SvfIgnitor oracle.
        val chained = IgnitorDsl.Lowpass(IgnitorDsl.Sawtooth(), c(2000.0), c(0.9))
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Lowpass(c(2000.0), c(0.9))),
        )
        val a = chained.toExciter()
        val b = fused.toExciter()
        val mk = {
            IgniteContext(
                sampleRate = 48000,
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
        }
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)
        val ca = mk()
        val cb = mk()
        repeat(blocks) {
            a.generate(bufA, 220.0, ca)
            b.generate(bufB, 220.0, cb)
            for (i in 0 until blockFrames) {
                bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
            }
            ca.voiceElapsedFrames += blockFrames
            cb.voiceElapsedFrames += blockFrames
        }
    }

    "guitar serial tail: chained DSL is bit-equal to the fused Eq DSL" {
        val chained = IgnitorDsl.Sawtooth()
            .notch(210.0, 2.5)
            .highpass(440.0, 0.707)
            .lowpass(5300.0, 0.707)
            .lowpass(5300.0, 0.707)
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.Notch(c(210.0), c(2.5)),
                EqSection.Highpass(c(440.0), c(0.707)),
                EqSection.Lowpass(c(5300.0), c(0.707)),
                EqSection.Lowpass(c(5300.0), c(0.707)),
            ),
        )
        assertDslParity(chained, fused)
    }

    "tracking highpass: Freq-backed cutoff stays bit-equal ACROSS voice frequencies" {
        // The reason EqIgnitor exists as the ignitor-surface adapter: a `Freq`-backed param
        // must re-resolve per voice frequency (and per block) — the static-cache predicate
        // deliberately excludes FreqIgnitor. A mis-classified section freezes the cutoff at
        // the first frequency and reddens the 110/440 runs.
        fun trackingCutoff() = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Param("track", 4.0))
        val chained = IgnitorDsl.Highpass(
            inner = IgnitorDsl.Sawtooth(),
            cutoffHz = trackingCutoff(),
            q = c(0.9),
        )
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Highpass(trackingCutoff(), c(0.9))),
        )
        assertDslParity(chained, fused, freqs = listOf(110.0, 220.0, 440.0))
    }

    "tracking highpass retracks when the voice frequency changes MID-VOICE" {
        // Fresh-exciter-per-frequency rows cannot catch a frozen cache (each build
        // reconfigures once, correctly). This row renders ONE exciter pair across a
        // frequency glide, with BOTH tracking forms: a cutoff that is DIRECTLY `Freq` (the
        // only shape that reaches the FreqIgnitor-exclusion branch of the static predicate
        // — expressions arrive MemoizingIgnitor-wrapped and are dynamic regardless) and a
        // composite `Freq`-based expression. A section wrongly classified static freezes
        // its cutoff at 220 Hz and diverges from block 1 on.
        fun trackingCutoff() = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Param("track", 4.0))
        val a = IgnitorDsl.Highpass(
            inner = IgnitorDsl.Highpass(
                inner = IgnitorDsl.Sawtooth(),
                cutoffHz = IgnitorDsl.Freq,
                q = c(0.9),
            ),
            cutoffHz = trackingCutoff(),
            q = c(1.3),
        ).toExciter()
        val b = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.Highpass(IgnitorDsl.Freq, c(0.9)),
                EqSection.Highpass(trackingCutoff(), c(1.3)),
            ),
        ).toExciter()

        val glide = listOf(220.0, 262.0, 330.0, 440.0)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)
        val ca = ctx()
        val cb = ctx()
        for (f in glide) {
            a.generate(bufA, f, ca)
            b.generate(bufB, f, cb)
            for (i in 0 until blockFrames) {
                bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
            }
            ca.voiceElapsedFrames += blockFrames
            cb.voiceElapsedFrames += blockFrames
        }
    }

    "a DYNAMIC bell at a transient 0 dB is NOT disabled (state keeps running)" {
        // The skip guard's isStatic half: an expression-backed db that happens to pass
        // through 0 must keep its state running (the core's explicit branch), NOT be retired
        // — a disable zeroes state, and when db moves off zero the bell resumes from wrong
        // state. Reference: EqCore driven directly with the same per-block scalars (the
        // semantic the adapter must reproduce). db = (freq - 220)/100: 0 at 220 Hz, 1.1 at
        // 330 Hz.
        fun dbExpr() = IgnitorDsl.Times(
            IgnitorDsl.Minus(IgnitorDsl.Freq, c(220.0)),
            c(0.01),
        )
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Bell(c(850.0), c(0.9), dbExpr())),
        ).toExciter()
        val srcOnly = IgnitorDsl.Sawtooth().toExciter()
        val core = EqCore(1)

        val glide = listOf(220.0, 330.0, 330.0)
        val bufFused = AudioBuffer(blockFrames)
        val bufRef = AudioBuffer(blockFrames)
        val cf = ctx()
        val cr = ctx()
        for (f in glide) {
            fused.generate(bufFused, f, cf)

            core.configureSection(
                0, EqCore.BELL, 850.0, 0.9, (f - 220.0) * 0.01, 0.0, sr.toDouble(),
            )
            srcOnly.generate(bufRef, f, cr)
            core.process(bufRef, 0, blockFrames)

            for (i in 0 until blockFrames) {
                bufFused[i].toRawBits() shouldBe bufRef[i].toRawBits()
            }
            cf.voiceElapsedFrames += blockFrames
            cr.voiceElapsedFrames += blockFrames
        }
    }

    "full guitar tail (2 taps + 4 serial): the legacy shared-source graph is bit-equal" {
        // The D4d replacement target, wired end to end: the legacy tree shares ONE source
        // node (identity-shared -> MemoizingIgnitor replay), the fused tree hands the same
        // node to Eq as inner — taps read the Eq input by definition.
        val sharedLegacy = IgnitorDsl.Sawtooth()
        val legacy = IgnitorDsl.Plus(
            IgnitorDsl.Plus(
                sharedLegacy,
                IgnitorDsl.Times(
                    IgnitorDsl.Bandpass(sharedLegacy, c(1000.0), c(0.8)),
                    c(2.0),
                ),
            ),
            IgnitorDsl.Times(
                IgnitorDsl.Bandpass(sharedLegacy, c(4000.0), c(0.85)),
                c(5.5),
            ),
        )
            .notch(210.0, 2.5)
            .highpass(440.0, 0.707)
            .lowpass(5300.0, 0.707)
            .lowpass(5300.0, 0.707)

        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.RawTap(c(1000.0), c(0.8), c(2.0)),
                EqSection.RawTap(c(4000.0), c(0.85), c(5.5)),
                EqSection.Notch(c(210.0), c(2.5)),
                EqSection.Highpass(c(440.0), c(0.707)),
                EqSection.Lowpass(c(5300.0), c(0.707)),
                EqSection.Lowpass(c(5300.0), c(0.707)),
            ),
        )
        assertDslParity(legacy, fused)
    }

    "the .eq().tap() SURFACE reproduces the legacy parallel chain bit-exactly" {
        // The row above proves a hand-built Eq tree matches the legacy graph. This one proves
        // the authoring SURFACE builds the same thing: .tap() must produce a RawTap with the
        // params in the right slots, or a wrong order / a Bell-instead-of-RawTap would leave
        // every parity row above green. NOTE this is not the song's literal shape — the serial
        // tail is appended as SECTIONS here, whereas a song chains .notch()/.lowpass() as
        // separate nodes after the Eq (that shape is pinned by StdLibOscTest instead).
        val sharedLegacy = IgnitorDsl.Sawtooth()
        val legacy = IgnitorDsl.Plus(
            IgnitorDsl.Plus(
                sharedLegacy,
                IgnitorDsl.Times(IgnitorDsl.Bandpass(sharedLegacy, c(1000.0), c(0.8)), c(2.0)),
            ),
            IgnitorDsl.Times(IgnitorDsl.Bandpass(sharedLegacy, c(4000.0), c(0.85)), c(5.5)),
        )
            .notch(210.0, 2.5)
            .lowpass(5300.0, 0.707)

        val authored = IgnitorDsl.Sawtooth()
            .eq()
            .tap(1000.0, 0.8, 2.0)
            .tap(4000.0, 0.85, 5.5)
            .let {
                // No section methods for the serial filters yet (they stay chained nodes
                // until the optimizer folds them), so the tail is appended directly.
                it.copy(
                    sections = it.sections +
                            EqSection.Notch(c(210.0), c(2.5)) +
                            EqSection.Lowpass(c(5300.0), c(0.707)),
                )
            }

        assertDslParity(legacy, authored)
    }

    "ONE tap equals its exactly-converted bell" {
        // The conversion the plan's D7 table documents: A² = 1 + gain·Q, q_bell = Q/A.
        // For a SINGLE section this is a real identity (near-bit; one pow/tan rounding),
        // and pinning it is what makes the next row's INEQUALITY meaningful.
        val q = 0.707
        val gain = 1.7
        val aSq = 1.0 + gain * q
        val qBell = q / sqrt(aSq)
        val db = 20.0 * log10(aSq)

        val oneTap = IgnitorDsl.Sawtooth().eq().tap(850.0, q, gain)
        val oneBell = IgnitorDsl.Sawtooth().eq().band(850.0, qBell, db)

        maxAbsDiff(oneTap, oneBell) shouldBeLessThan 1e-9
    }

    "TWO taps do NOT equal two exactly-converted bells (the cross term)" {
        // Taps SUM onto the dry signal, bells MULTIPLY, so the identity above does not
        // compose: the leftover gain₁·gain₂·H₁·H₂ term measured +4.5 dB around 1200 Hz on
        // the real song. Each band below is converted EXACTLY (same formula as the row
        // above), so the difference is purely topological, not a mis-conversion.
        //
        // This row is THE tripwire for EqCore's RAW_TAP reading `inputCopy` and not `buffer`:
        // a tap on the running buffer makes two taps equal `1 + g₁H₁ + g₂H₂(1 + g₁H₁)`, which
        // is the serial-bell product exactly, collapsing the gap to ~1e-15. The bound sits far
        // above rounding but well below the real gap (order 0.3-1.0 here), so it is a topology
        // tripwire with ~10x margin. It does NOT by itself establish audibility — the +4.5 dB
        // figure does; a max-abs-diff bound cannot express that.
        val bands = listOf(850.0 to (0.707 to 1.7), 2500.0 to (0.7 to 5.0))

        var taps = IgnitorDsl.Sawtooth().eq()
        var bells = IgnitorDsl.Sawtooth().eq()
        for ((freq, spec) in bands) {
            val (q, gain) = spec
            val aSq = 1.0 + gain * q
            taps = taps.tap(freq, q, gain)
            bells = bells.band(freq, q / sqrt(aSq), 20.0 * log10(aSq))
        }

        // Far above any rounding: this is a real, audible gap.
        maxAbsDiff(taps, bells) shouldBeGreaterThan 0.05
    }

    "static 0 dB bell is skipped: bit-equal to the chain WITHOUT the bell" {
        // The adapter-owned skip (EqCore KDoc): Param-backed db is per-voice constant, so a
        // 0 dB bell can never move off zero and its slot is retired to passthrough.
        val without = IgnitorDsl.Sawtooth().lowpass(2000.0, 1.0)
        val with = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.Bell(c(850.0), c(0.9), IgnitorDsl.Param("belldb", 0.0)),
                EqSection.Lowpass(c(2000.0), c(1.0)),
            ),
        )
        assertDslParity(without, with)
    }

    "EXPRESSION-backed 0 dB bell stays transparent through the running-state path" {
        // Not static (Times is neither Param nor Constant) -> the core's explicit 0 dB
        // branch runs state every block and emits v0 — still bit-transparent.
        val without = IgnitorDsl.Sawtooth().lowpass(2000.0, 1.0)
        val with = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.Bell(c(850.0), c(0.9), IgnitorDsl.Times(c(0.0), IgnitorDsl.Freq)),
                EqSection.Lowpass(c(2000.0), c(1.0)),
            ),
        )
        assertDslParity(without, with)
    }

    "oscParams override reaches section params" {
        // A Param-backed bell db overridden at play time must land exactly like a Constant
        // of the same value — the oscParams plumbing goes through the same buildIgnitor leaf
        // path as every other param.
        val overridden = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Bell(c(850.0), c(0.9), IgnitorDsl.Param("belldb", 0.0))),
        )
        val explicit = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(EqSection.Bell(c(850.0), c(0.9), c(6.0))),
        )
        assertDslParity(explicit, overridden, oscParams = mapOf("belldb" to 6.0))
    }

    "an Eq with zero sections is bit-transparent" {
        // `.eq()` alone is the NORMAL intermediate state while live-coding (type .eq(), then
        // add bands; or delete the last .band(...) from a line), so the empty section list is
        // user-reachable, not a degenerate test case. What this pins precisely: construction
        // and the process() entry guards neither throw nor mutate the buffer at sectionCount
        // 0 (a later `require(sectionCount > 0)` is the realistic regression). It does NOT
        // pin loop-index arithmetic — the 1-section rows carry that.
        val bare = IgnitorDsl.Sawtooth()
        val emptyEq = IgnitorDsl.Eq(inner = IgnitorDsl.Sawtooth(), sections = emptyList())
        assertDslParity(bare, emptyEq)
    }

    "production sub-block onset renders bit-equal (offset != 0, partial length, first call)" {
        val chained = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0, 0.707)
        val fused = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sawtooth(),
            sections = listOf(
                EqSection.Notch(c(210.0), c(2.5)),
                EqSection.Lowpass(c(5300.0), c(0.707)),
            ),
        )
        val a = chained.toExciter()
        val b = fused.toExciter()
        val offset = 37
        val length = 64
        val sentinel = 123.456
        val bufA = AudioBuffer(blockFrames).apply { fill(sentinel) }
        val bufB = AudioBuffer(blockFrames).apply { fill(sentinel) }
        val ca = ctx().apply { this.offset = offset; this.length = length; voiceElapsedFrames = -offset }
        val cb = ctx().apply { this.offset = offset; this.length = length; voiceElapsedFrames = -offset }
        a.generate(bufA, 220.0, ca)
        b.generate(bufB, 220.0, cb)
        for (i in 0 until blockFrames) {
            if (i in offset until offset + length) {
                bufA[i].toRawBits() shouldBe bufB[i].toRawBits()
            } else {
                bufA[i] shouldBe sentinel
                bufB[i] shouldBe sentinel
            }
        }
    }

    "static/dynamic classification is node-type-based per section" {
        // The predicate the class KDoc pins: Param/Constant = static; Freq is DELIBERATELY
        // dynamic (block-constant but NOT voice-constant); expressions arrive wrapped
        // (MemoizingIgnitor) and are dynamic. Output-visible in the tracking-HP row for the
        // dangerous direction (frozen tracking); pinned directly here for both directions.
        EqIgnitor.Section(EqCore.LOWPASS, ParamIgnitor("f", 1000.0), ConstantIgnitor(1.0))
            .isStatic shouldBe true
        EqIgnitor.Section(EqCore.HIGHPASS, FreqIgnitor, ConstantIgnitor(1.0))
            .isStatic shouldBe false
        EqIgnitor.Section(
            EqCore.BELL,
            ConstantIgnitor(850.0),
            ConstantIgnitor(0.9),
            db = MemoizingIgnitor(ConstantIgnitor(0.0)),
        ).isStatic shouldBe false
    }

    "Eq maxReleaseSec delegates to inner" {
        val dsl = IgnitorDsl.Eq(
            inner = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), releaseSec = c(0.5)),
            sections = listOf(EqSection.Lowpass(c(1000.0), c(1.0))),
        )
        dsl.maxReleaseSec() shouldBe 0.5
    }

    "Eq collectParams walks inner first, then sections in list order" {
        val dsl = IgnitorDsl.Eq(
            inner = IgnitorDsl.Sine(freq = IgnitorDsl.Param("innerFreq", 440.0)),
            sections = listOf(
                EqSection.Highpass(IgnitorDsl.Param("hpFreq", 440.0), IgnitorDsl.Param("hpQ", 0.7)),
                EqSection.Bell(c(850.0), c(0.9), IgnitorDsl.Param("bellDb", 6.0)),
                EqSection.RawTap(c(1000.0), c(0.8), IgnitorDsl.Param("tapGain", 2.0)),
            ),
        )
        val out = mutableListOf<IgnitorDsl.Param>()
        dsl.collectParams(out)
        // "analog" is Sine's own default drift param — the inner subtree contributes it
        // BEFORE any section param, which is exactly the inner-first contract under test.
        out.map { it.name } shouldBe listOf(
            "innerFreq", "analog", "hpFreq", "hpQ", "bellDb", "tapGain",
        )
    }
})
