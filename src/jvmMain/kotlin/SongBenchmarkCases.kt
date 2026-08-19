/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.builtinsongs.derSchmetterlingSong

/**
 * Benchmark cases for "Der Schmetterling" (frozen 2026-07-03).
 *
 * Two flavours of case:
 *  - **Isolated voices** — each layer of the `stack(...)` on its own, with section gating
 *    (`.mute(...)`, `.filterWhen(...)`) removed so the voice plays continuously and we measure a
 *    clean steady-state cost.
 *  - **Effect-strip ladders** — a heavy voice built up one effect group at a time. The delta in
 *    RTF between consecutive rungs is the marginal CPU cost of that effect group *in context*.
 *
 * The chains are transcribed verbatim from the frozen song, only removing the section gates.
 */
object SongBenchmarkCases {

    private const val HDR = "import * from \"stdlib\"\nimport * from \"sprudel\"\nlet feel = 8.0\n"

    private fun code(expr: String): String = HDR + expr

    private const val DER_RPM = 34.5

    /**
     * Build a cumulative ladder. [rungs] is (label, segmentAppended). Case N renders the source
     * plus segments 0..N. `name` = "group: label".
     */
    private fun ladder(
        group: String,
        source: String,
        rungs: List<Pair<String, String>>,
        cycles: Int = 8,
    ): List<SongBenchmark.Case> {
        var expr = source
        val out = ArrayList<SongBenchmark.Case>()
        for ((label, seg) in rungs) {
            expr = if (seg.isBlank()) expr else "$expr\n    $seg"
            out += SongBenchmark.Case(
                name = "$group: $label",
                code = code(expr),
                group = group,
                rpm = DER_RPM,
                cycles = cycles,
            )
        }
        return out
    }

    private fun voice(name: String, group: String, expr: String, cycles: Int = 8): SongBenchmark.Case =
        SongBenchmark.Case(name = name, code = code(expr), group = group, rpm = DER_RPM, cycles = cycles)

    // ────────────────────────────────────────────────────────────────────────────────────────
    // LEAD ladder — superramp, unison(5)
    // ────────────────────────────────────────────────────────────────────────────────────────

    private val leadSource = """
        n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)
          .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").sound("superramp").unison(5).spread(0.08)
          .gain(0.50).adsr("0.007:4.0:0.0:0.01")
    """.trimIndent()

    private val leadLadder = ladder(
        group = "LEAD",
        source = leadSource,
        rungs = listOf(
            "0 osc+env (superramp uni5)" to "",
            "1 +filters (hpf/lpf/lpe/lpq/lpadsr)" to
                    """.hpf(1500).lpf(1575).lpe(berlin.range(2, 2.10).fast(4)).lpq(2.3).lpadsr("0.007:1.3:0.0:0.01")""",
            "2 +distort (0.62:tube:4)+clip" to
                    """.distort("0.620:tube:4").postgain("<0.220!48 0.110!16 0.220!48 0.330!16>").clip(0.89)""",
            "3 +pitchmod (vibrato/shuffle)" to
                    """.release("<0.04!16 0.11!16>").vibrato(8).vibmod(0.01).shuffle("<1!64 0!16 1!1 4/8!14 1!33>")""",
            "4 +superimpose (transpose+2xsuper)" to
                    """.superimpose(x => x.transpose(12).spread(0.12).velocity(0.10).pan(0.15).superimpose(pan(0.85)))""",
            "5 +analog(feel)" to """.analog(feel)""",
            "6 +pipeline(pedal)" to """.pipeline("pedal")""",
            "7 +room(0.3:5:0.1)" to """.room("0.3:5:0.1")""",
        ),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // GUITAR 1 ladder — supersaw, unison(9), the most decorated voice
    // ────────────────────────────────────────────────────────────────────────────────────────

    private val guitar1Source = """
        n(`<[7 [4@4 2 -1] 2 1 [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
            [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
            [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-5 <-1 -4 -4 1>]]>/4`)
          .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>")
          .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)
          .sound("supersaw").unison(9).spread(0.08).gain(0.75).postgain(0.12).adsr("0.005:2.5:0.0:0.029")
    """.trimIndent()

    private val guitar1Ladder = ladder(
        group = "GTR1",
        source = guitar1Source,
        rungs = listOf(
            "0 osc+env (supersaw uni9)" to "",
            "1 +filters (lpadsr/hpf/lpf-mod/lpe/lpq)" to
                    """.lpadsr("0.005:1.1:0.0:0.015").hpf("<550!16 360!16 550!16 800!16>").lpf("3450".add(saw.range(1, 0).pow(1.8).mul(800)).slow(4)).lpe(0.6).lpq(2.0)""",
            "2 +distortx2 (1:tube:4 + 0.80)+clip" to
                    """.distort("1:tube:4").distort(0.80).clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2))""",
            "3 +coarse(2,os4)" to """.coarse(2).coarseos(4)""",
            "4 +superimpose#1 (pan copy)" to """.pan(0.15).superimpose(pan(0.85))""",
            "5 +superimpose#2 (hpf/lpf air)" to """.superimpose(hpf(3800).lpf(6700).postgain(0.03))""",
            "6 +pipeline(pedal)" to """.pipeline("pedal")""",
            "7 +body(wood, mix0.3)" to """.body("wood").bodyMix(0.3)""",
            "8 +room(0.10:8:0.12)" to """.room("0.10:8:0.12")""",
        ),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // GTR-FX ladder — the CURRENT guitar tone chain (parallel bandpass taps + notch + tracking
    // highpass + double lowpass), transcribed from builtinsongs/DerSchmetterling.kt. Unlike
    // ladder(), each rung swaps the RETURN EXPRESSION of the ignitor definition — the chain
    // sits above the pattern, so the pattern-segment helper cannot express it.
    // Baseline + before/after harness for the unified-equalizer work.
    // ────────────────────────────────────────────────────────────────────────────────────────

    // Ignitor definition transcribed 2026-08-19 from builtinsongs/DerSchmetterling.kt (working
    // tree). Live edits to the song do NOT propagate here — re-transcribe when the guitar tone
    // changes. RETURN_EXPR is replaced per rung; the template is pre-trimmed so the interpolated
    // multi-line return expressions cannot pollute trimIndent's common-indent scan.
    // PATTERN_EXPR is replaced per use: the GTR-FX ladder drives a melodic line (~1 concurrent
    // voice); the GTR-POLY sweep drives sustained K-note chords (K concurrent chain instances).
    private val gtrFxTemplate = """
        let snareHz = 210
        let guitar = (() => {
          let pVoices  = OscSlot.voices
          let pSpread  = OscSlot.spread
          let pAnalog  = OscSlot.analog

          let pMidsHz     = Osc.param("midsHz",      850.000, "Mids frequency")
          let pMidsQ      = Osc.param("midsQ",         0.707, "Mids Q")
          let pMids       = Osc.param("mids",          2.000, "Mids Volume")
          let pPresenceHz = Osc.param("presenceHz", 2500.000, "Presence frequency")
          let pPresenceQ  = Osc.param("presenceQ",     0.700, "Presence Q")
          let pPresence   = Osc.param("presence",      5.000, "Presence Volume")
          let pHpTrack    = Osc.param("hptrack",       1.000, "Highpass tracking")
          let pHpQ        = Osc.param("hpq",           0.707, "Highpass resonance")

          let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, spread = pSpread)
            .phasePool(on = 1, kMin = 0.50, kMax = 0.90)
            .analog(pAnalog).spreadPower(1.0).sideAtten(0.1).gainJitter(0.20).centerJitter(0.20)
            .pitchEnvelope(0.3, 0.001, 0.02)
            .plus(Osc.whitenoise().highpass(2000).adsr(0.000, 0.05, 0.0, 0.005).mul(0.14))
            .distort(0.35, "hard", 4)

          return RETURN_EXPR
        })()
        PATTERN_EXPR
    """.trimIndent()

    // The ladder pattern mirrors GTR1's real cost drivers: unison(15) and analog(13) — the song
    // wraps the whole guitar stack in .analog(feel) with feel = 13.0. Without them the
    // bare-signal denominator is far too cheap and the tone-chain share reads too high.
    private val gtrFxLadderPattern = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound(guitar).unison(15).spread(0.08)
          .analog(13.0).gain(0.5).adsr("0.005:2.5:0.0:0.029")
    """.trimIndent()

    private fun gtrFxCode(returnExpr: String, patternExpr: String = gtrFxLadderPattern): String =
        code(gtrFxTemplate.replace("RETURN_EXPR", returnExpr).replace("PATTERN_EXPR", patternExpr))

    // NOTE on rung deltas: rung 0→1 also switches `signal`'s MemoizingIgnitor from the direct
    // 1-consumer path onto the render-to-cache + copyInto-per-consumer path (2 consumers; rung 2
    // makes it 3). So the "+mids tap" delta = bandpass + mul + add + that mode switch. The
    // optimizer's R2 banks the reverse effect (3 -> 1 consumers) later.
    private val gtrFxRungs = listOf(
        "0 bare signal (supersaw+noise+distort)" to "",
        "1 +mids tap (add bandpass.mul)" to ".add(signal.bandpass(pMidsHz, pMidsQ).mul(pMids))",
        "2 +presence tap (add bandpass.mul)" to ".add(signal.bandpass(pPresenceHz, pPresenceQ).mul(pPresence))",
        "3 +notch (snare room)" to ".notch(snareHz, 2.5)",
        "4 +tracking highpass" to ".highpass(Osc.freq().mul(pHpTrack), pHpQ)",
        "5 +double lowpass (cabinet)" to ".lowpass(5300).lowpass(5300)",
    )

    // cycles = 32 (not the ladder default 8): per-rung deltas sit near the in-run noise floor
    // (~2-3.5e-4 medRTF at cycles=8 — negative deltas observed); the pattern repeats identically
    // per cycle, so longer runs are pure statistics. HEADLINE METRIC for before/after comparisons
    // is the IN-RUN chain total (rung5 - rung0), which is immune to the per-pass fixed-cost drift
    // that dominates cheap cases across runs; individual rung deltas are indicative only.
    private const val GTR_FX_CYCLES = 32

    private val gtrFxLadder: List<SongBenchmark.Case> = run {
        var expr = "signal"
        val out = ArrayList<SongBenchmark.Case>()
        // Floor: same pattern, trivial sine voice instead of the guitar. Approximates the fixed
        // renderer overhead (scheduler, orbit mix, master limiter, output conversion) shared by
        // every rung — but note it also removes one sine voice's ignitor+ADSR+pipeline cost, and
        // the rungs (not the floor) carry .analog(13.0) drift. The floor-subtracted denominator
        // is therefore "guitar voice minus a sine voice, incl. analog drift", NOT pure
        // guitar-ignitor cost — state that whenever quoting the tone chain's share.
        out += SongBenchmark.Case(
            name = "GTR-FX: F floor (sine voice, no guitar)",
            code = code(
                """
                n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("sine")
                  .gain(0.5).adsr("0.005:2.5:0.0:0.029")
                """.trimIndent(),
            ),
            group = "GTR-FX",
            rpm = DER_RPM,
            cycles = GTR_FX_CYCLES,
        )
        for ((label, seg) in gtrFxRungs) {
            if (seg.isNotBlank()) expr = "$expr\n    $seg"
            out += SongBenchmark.Case(
                name = "GTR-FX: $label",
                code = gtrFxCode(expr),
                group = "GTR-FX",
                rpm = DER_RPM,
                cycles = GTR_FX_CYCLES,
            )
        }
        out
    }

    // ────────────────────────────────────────────────────────────────────────────────────────
    // GTR-POLY sweep — cache-pressure hypothesis (maintainer, 2026-08-19): the single-voice
    // anchors keep everything L1-hot, so per-node plumbing measures ~free. What actually scales
    // with K concurrent chain instances: one MemoizingIgnitor cache (~1 KB, full-chain case only
    // — bare `signal` has 1 consumer and takes the direct path), per-voice oscillator + drift
    // state (unison-15 WaveVoiceStates + PolyAnalogDrift arrays) and ~6 SVF state pairs — about
    // 2-3 KB per voice. The scratch-buffer pool and the voice buffer are SHARED per engine
    // (VoiceScheduler allocates one of each) and stay hot across voices. So K=16 only REACHES
    // L1 capacity (~32-48 KB); the sweep must go to K=32/64 to genuinely leave L1 and press L2
    // — below that, a flat curve is a property of the probe, not a refutation. If the chain's
    // marginal cost per voice, (full(K) − bare(K)) / K, GROWS with K, cache pressure is real and
    // the fused EQ's win scales with polyphony. Sustained K-note chords (comma = parallel stack,
    // sustain 1.0, one event per cycle) hold exactly K chain instances; signal cost cancels in
    // the full − bare subtraction at each K.
    // ────────────────────────────────────────────────────────────────────────────────────────

    // Derived from gtrFxRungs so the sweep can NEVER measure a different chain than the ladder.
    private val GTR_FULL_CHAIN: String = gtrFxRungs.fold("signal") { acc, (_, seg) ->
        if (seg.isBlank()) acc else "$acc\n    $seg"
    }

    // K=1 runs ~170 ms/pass — with the default 2 warmup passes it measured ~12-18% above the
    // K≥4 linear fit (JIT/alloc warmup, not signal). More warmup + cycles gets every K to
    // steady state so the K=1→4 segment is interpretable.
    private const val GTR_POLY_CYCLES = 16
    private const val GTR_POLY_WARMUP_PASSES = 4

    private fun gtrPolyPattern(k: Int): String {
        // Degrees bounded to 0..26 (duplicates allowed above K=14): unbounded stacking pushed
        // K=32/64 voices past Nyquist (degree 58+ on e2:minor), silently changing the per-sample
        // code path (wrapPhase modulo branch, tracking-HP pinned at its clamp) — the sweep must
        // vary only the COUNT, never the workload class. Residual caveat: the pitch SET still
        // grows up to K=14 (saw flyback fraction rises with pitch, a few % oscillator cost), so
        // raw bare/full columns are cross-K comparable only from K=16 up; the headline
        // full − bare delta cancels it at every K.
        val chord = (0 until k).joinToString(",") { ((it * 2) % 28).toString() }
        return """
            n("[$chord]").orbit(1).scale("e2:minor").sound(guitar).unison(15).spread(0.08)
              .analog(13.0).gain(0.3).adsr("0.005:0.5:1.0:0.05")
        """.trimIndent()
    }

    // POSITION-IN-RUN WARNING: turbo/clock decay shifts per-voice numbers ~5-10% across a sweep
    // (measured 2026-08-19: ascending order showed a spurious +33% chain bump at K=32; the same
    // sweep descending was flat 1.24-1.29e-3 at every K, and K=64 ran 10% cheaper when first).
    // Any conclusion about K-scaling needs the sweep run in BOTH orders.
    private val gtrPolySweep: List<SongBenchmark.Case> = listOf(1, 4, 8, 16, 32, 64).flatMap { k ->
        listOf(
            SongBenchmark.Case(
                name = "GTR-POLY: K=$k bare signal",
                code = gtrFxCode("signal", gtrPolyPattern(k)),
                group = "GTR-POLY",
                rpm = DER_RPM,
                cycles = GTR_POLY_CYCLES,
                warmupPasses = GTR_POLY_WARMUP_PASSES,
            ),
            SongBenchmark.Case(
                name = "GTR-POLY: K=$k full chain",
                code = gtrFxCode(GTR_FULL_CHAIN, gtrPolyPattern(k)),
                group = "GTR-POLY",
                rpm = DER_RPM,
                cycles = GTR_POLY_CYCLES,
                warmupPasses = GTR_POLY_WARMUP_PASSES,
            ),
        )
    }

    fun gtrPoly(): List<SongBenchmark.Case> = gtrPolySweep

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Isolated voices (full chains, section gates removed)
    // ────────────────────────────────────────────────────────────────────────────────────────

    private val guitar2 = voice(
        "GTR2 (full, uni7, nested superimpose)", "voice",
        """
        n("<0 0 2 4 0 0 -2 -1>")
          .orbit(1).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
          .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)
          .sound("supersaw").unison(7).spread(0.09).gain(0.75).postgain(0.11).distort("1:tube:4").distort(0.85)
          .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2)).adsr("0.005:2.5:0.0:0.027").lpadsr("0.005:1.0:0.0:0.01")
          .hpf(120).lpf(3200).lpe(0.6).lpq(1.8)
          .coarse(2).coarseos(4).pan(0.3).superimpose(
            x => x.pan(0.7),
            x => x.postgain(0.09).hpf(240).lpf(3400).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-7 -3] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
                 .pan(0.2).superimpose(pan(0.8))
          ).superimpose(hpf(3500).lpf(6200).postgain(0.03)).pipeline("pedal").body("wood").bodyMix(0.30)
        """.trimIndent(),
    )

    private val bass = voice(
        "BASS (full, saw)", "voice",
        """
        n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.98 0.99 0.98".fast(2))
          .orbit(4).scale("e1:minor").sound("saw").gain(0.5).distort("0.05:soft:2").postgain(0.20).clip(0.65)
          .adsr("0.007:5.0:0.0:0.015").lpadsr("0.001:0.05:0.0:0.01").hpf(60).hpq(1.0).lpf(200).lpe(35).lpq(1.0)
          .pan(0.50)
        """.trimIndent(),
    )

    private val drumsKick = voice(
        "DRUMS kick (bd, sampled)", "voice",
        """
        sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>")
          .pan(0.5).orbit(5).gain(0.24).hpf(45).lpf(11500).adsr("0.002:0.10:0.5:0.2")
        """.trimIndent(),
    )

    private val hats = voice(
        "HATS (hh/oh/cr/rd, sampled)", "voice",
        """
        sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2)
          .pan(0.515).late(0.0005).orbit(5).gain(0.33).hpf(800).lpf("11500".add(perlin.mul(300))).adsr("0.005:0.15:0.8:0.2")
        """.trimIndent(),
    )

    private val pink = voice(
        "PINK (pink noise)", "voice",
        """
        sound("pink!8").orbit(6).gain(0.08).hpf(8000).pan(sine.range(0.25, 0.75).slow(3)).adsr("0.007:0.3:0.0:0.05")
        """.trimIndent(),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Targeted experiments
    // ────────────────────────────────────────────────────────────────────────────────────────

    // Distort oversample sweep on a supersaw-uni9 + filters base (isolates oversampling cost).
    private val distortBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(9).spread(0.08)
          .gain(0.75).adsr("0.005:2.5:0.0:0.029").hpf(400).lpf(3000).lpe(0.6).lpq(2.0)
    """.trimIndent()

    private fun distortCase(label: String, distort: String): SongBenchmark.Case =
        voice("DISTORT: $label", "exp-distort", "$distortBase\n    $distort")

    private val distortSweep = listOf(
        distortCase("no distort", ""),
        distortCase("os1 (1:tube:1)", """.distort("1:tube:1")"""),
        distortCase("os2 (1:tube:2)", """.distort("1:tube:2")"""),
        distortCase("os4 (1:tube:4)", """.distort("1:tube:4")"""),
        distortCase("os8 (1:tube:8)", """.distort("1:tube:8")"""),
        distortCase("os4 x2 (double, as song)", """.distort("1:tube:4").distort(0.80)"""),
    )

    // Unison sweep on the FULL guitar-1 effect chain (osc-gen scales with unison; fixed effects don't).
    private val fullChainTail =
        """.lpadsr("0.005:1.1:0.0:0.015").hpf(400).lpf(3000).lpe(0.6).lpq(2.0)""" +
                """.distort("1:tube:4").distort(0.80).clip(0.85).coarse(2).coarseos(4)""" +
                """.pan(0.15).superimpose(pan(0.85)).superimpose(hpf(3800).lpf(6700).postgain(0.03))""" +
                """.pipeline("pedal").body("wood").bodyMix(0.3)"""

    private fun unisonCase(n: Int): SongBenchmark.Case =
        voice(
            "UNISON: full chain uni$n", "exp-unison",
            """n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison($n).spread(0.08).gain(0.75).adsr("0.005:2.5:0.0:0.029")
    $fullChainTail""",
        )

    private val unisonSweep = listOf(1, 5, 9, 15).map { unisonCase(it) }

    // Body / pipeline isolation on a fixed base — clean marginal cost of each.
    private val fxBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(9).spread(0.08)
          .gain(0.75).adsr("0.005:2.5:0.0:0.029").hpf(400).lpf(3000).lpe(0.6).lpq(2.0)
          .distort("1:tube:4").clip(0.85)
    """.trimIndent()

    private val fxIsolation = listOf(
        voice("FX: base (osc+filt+dist)", "exp-fx", fxBase),
        voice("FX: base +pipeline(pedal)", "exp-fx", """$fxBase.pipeline("pedal")"""),
        voice("FX: base +body(wood)", "exp-fx", """$fxBase.body("wood").bodyMix(0.3)"""),
        voice("FX: base +body(glass)", "exp-fx", """$fxBase.body("glass").bodyMix(0.3)"""),
        voice("FX: base +vowel(a)", "exp-fx", """$fxBase.vowel("a").vowelMix(0.3)"""),
        voice("FX: base +room", "exp-fx", """$fxBase.room("0.10:8:0.12")"""),
        voice("FX: base +pipeline+body+room", "exp-fx", """$fxBase.pipeline("pedal").body("wood").bodyMix(0.3).room("0.10:8:0.12")"""),
    )

    // 2x2 interaction: does `superimpose` MULTIPLY the cost of a per-voice effect (`body`)?
    // cost(body | no super)  = [+body]           - [base]
    // cost(body | 1 super)   = [+super +body]    - [+super]
    // If the second is much larger than the first, superimpose amplifies body → multiplicative.
    private val intBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(9).spread(0.08)
          .gain(0.75).adsr("0.005:2.5:0.0:0.029").hpf(400).lpf(3000).lpe(0.6).lpq(2.0).distort("1:tube:4").clip(0.85)
    """.trimIndent()

    private val interactionSweep = listOf(
        voice("INT: base (no super, no body)", "exp-interaction", intBase),
        voice("INT: +body (no super)", "exp-interaction", """$intBase.body("wood").bodyMix(0.3)"""),
        voice("INT: +super (no body)", "exp-interaction", """$intBase.pan(0.15).superimpose(pan(0.85))"""),
        voice("INT: +super +body", "exp-interaction", """$intBase.pan(0.15).superimpose(pan(0.85)).body("wood").bodyMix(0.3)"""),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Full frozen songs
    // ────────────────────────────────────────────────────────────────────────────────────────

    fun frozenSongs(): List<SongBenchmark.Case> = listOf(
        SongBenchmark.Case(
            name = "Der Schmetterling (FULL frozen)",
            code = FrozenSongs.derSchmetterling_2026_07_03,
            group = "full-song",
            rpm = 34.5,
            cycles = 48,
            warmupPasses = 1,
            measurePasses = 3,
        ),
        SongBenchmark.Case(
            name = "Seltsamere Dinge (FULL frozen)",
            code = FrozenSongs.strangerThings_2026_07_03,
            group = "full-song",
            rpm = 34.0,
            cycles = 48,
            warmupPasses = 1,
            measurePasses = 3,
        ),
    )

    /**
     * The CURRENT (live) built-in Der Schmetterling — reads `builtinsongs/DerSchmetterling.kt` as edited,
     * next to the frozen snapshot for a same-run comparison. Use `--args=live`.
     */
    fun live(): List<SongBenchmark.Case> = listOf(
        SongBenchmark.Case(
            name = "Der Schmetterling (FROZEN 07-03)",
            code = FrozenSongs.derSchmetterling_2026_07_03,
            group = "full-song",
            rpm = 34.5,
            cycles = 48,
            warmupPasses = 1,
            measurePasses = 3,
        ),
        SongBenchmark.Case(
            name = "Der Schmetterling (LIVE, current code)",
            code = derSchmetterlingSong.code,
            group = "full-song",
            rpm = derSchmetterlingSong.rpm,
            cycles = 48,
            warmupPasses = 1,
            measurePasses = 3,
        ),
    )

    fun voices(): List<SongBenchmark.Case> = listOf(
        // isolated voices — the full lead/gtr1 chains are the top rung of their ladders, relabelled
        // into the shared "voice" group so they sit alongside the other isolated voices.
        leadLadder.last().copy(name = "LEAD (full, superramp uni5)", group = "voice"),
        guitar1Ladder.last().copy(name = "GTR1 (full, supersaw uni9)", group = "voice"),
        guitar2, bass, drumsKick, hats, pink,
    )

    fun ladders(): List<SongBenchmark.Case> = leadLadder + guitar1Ladder + gtrFxLadder

    fun experiments(): List<SongBenchmark.Case> = distortSweep + unisonSweep + fxIsolation + interactionSweep

    fun all(): List<SongBenchmark.Case> = voices() + ladders() + experiments() + frozenSongs()
}
