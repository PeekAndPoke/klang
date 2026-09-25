/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.builtinsongs.derSchmetterlingSong

/**
 * Benchmark cases for "Der Schmetterling".
 *
 * Two flavours of case:
 *  - **Isolated voices** — each layer of the `stack(...)` on its own, with section gating
 *    (`.mute(...)`, `.filterWhen(...)`) removed so the voice plays continuously and we measure a
 *    clean steady-state cost.
 *  - **Effect-strip ladders** — a heavy voice built up one effect group at a time. The delta in
 *    RTF between consecutive rungs is the marginal CPU cost of that effect group *in context*.
 *
 * The chains are transcribed verbatim from the 2026-07-03 frozen snapshot, only removing the section
 * gates. That snapshot was replaced on 2026-09-25 by a fresh one ([FrozenSongs]); the transcriptions
 * stay as they are, a fixed workload of their own, minus the `pipeline("pedal")` calls, which went
 * with the preset (`docs/tasks/builtin-instruments.md` D4).
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
          .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").sound("superramp").unison(voices = 5, spread = 0.08)
          .gain(0.50).adsr(0.007, 4.0, 0.0, 0.01)
    """.trimIndent()

    private val leadLadder = ladder(
        group = "LEAD",
        source = leadSource,
        rungs = listOf(
            "0 osc+env (superramp uni5)" to "",
            "1 +filters (hpf/lpf/lpe/lpq/lpadsr)" to
                    """.hpf(1500).lpf(freq = 1575, env = berlin.range(19.0, 19.6).fast(4), q = 2.3, attack = 0.007, decay = 1.3, sustain = 0.0, release = 0.01)""",
            "2 +distort (0.62:tube:4)+clip" to
                    """.distort(0.620, "tube", 4).gain("<0.220!48 0.110!16 0.220!48 0.330!16>".mul(0.50)).clip(0.89)""",
            "3 +pitchmod (vibrato/shuffle)" to
                    """.adsr(release = "<0.04!16 0.11!16>").vibrato(rate = 8, depth = 0.01).shuffle("<1!64 0!16 1!1 4/8!14 1!33>")""",
            "4 +superimpose (transpose+2xsuper)" to
                    """.superimpose(x => x.transpose(12).unison(spread = 0.12).velocity(0.10).pan(0.15).superimpose(pan(0.85)))""",
            "5 +analog(feel)" to """.analog(feel)""",
            // Rung 6, `.pipeline("pedal")`, went with the preset (2026-09-25). The later rungs keep
            // their numbers so benchmark files still line up by name (rungs 7 and 8 are cumulative, so
            // their workload differs only by that reorder, which measured roughly free).
            "7 +room(0.3:5:0.1)" to """.reverb(0.3, 1)""",
            // The ladder had NO delay coverage before the master round needed to price a
            // per-sample guard on DelayLine's ring store (2026-08-31). Feedback is deliberately
            // real (0.35) so the recirculating path — the one that carries the store cost — is
            // exercised, not just a single tap.
            "8 +delay(0.3:1/8:0.35)" to """.delay(wet = 0.3, time = pure(1/8).div(cps), feedback = 0.35)""",
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
          .sound("supersaw").unison(voices = 9, spread = 0.08).gain(0.75 * 0.12).adsr(0.005, 2.5, 0.0, 0.029)
    """.trimIndent()

    private val guitar1Ladder = ladder(
        group = "GTR1",
        source = guitar1Source,
        rungs = listOf(
            "0 osc+env (supersaw uni9)" to "",
            "1 +filters (lpadsr/hpf/lpf-mod/lpe/lpq)" to
                    """.lpf(attack = 0.005, decay = 1.1, sustain = 0.0, release = 0.015).hpf("<550!16 360!16 550!16 800!16>").lpf(freq = "3450".add(saw.range(1, 0).pow(1.8).mul(800)).slow(4), env = 8.1, q = 2.0)""",
            "2 +distortx2 (1:tube:4 + 0.80)+clip" to
                    """.distort(1, "tube", 4).distort(0.80).clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2))""",
            "3 +coarse(2,os4)" to """.coarse(amount = 2, oversample = 4)""",
            "4 +superimpose#1 (pan copy)" to """.pan(0.15).superimpose(pan(0.85))""",
            "5 +superimpose#2 (hpf/lpf air)" to """.superimpose(hpf(3800).lpf(6700).gain(0.75 * 0.03))""",
            // Rung 6, `.pipeline("pedal")`, went with the preset (2026-09-25); see the LEAD ladder.
            "7 +body(wood, mix0.3)" to """.body(material = "wood", wet = 0.3)""",
            "8 +room(0.10:8:0.12)" to """.reverb(0.10, 1.2)""",
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

          let signal = Osc.supersaw(x => x.voices(pVoices).spread(pSpread)
            .phasePool(on = 1, kMin = 0.50, kMax = 0.90)
            .analog(pAnalog).spreadPower(1.0).sideAtten(0.1).gainJitter(0.20).centerJitter(0.20))
            .pitchEnvelope(0.3, x => x.adsr(0.001, 0.02, 0, 0))
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
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound(guitar).unison(voices = 15, spread = 0.08)
          .analog(13.0).gain(0.5).adsr(0.005, 2.5, 0.0, 0.029)
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
                  .gain(0.5).adsr(0.005, 2.5, 0.0, 0.029)
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
    // state (unison-15 WaveVoiceStates + the stack's DriftLanes) and ~6 SVF state pairs — about
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
            n("[$chord]").orbit(1).scale("e2:minor").sound(guitar).unison(voices = 15, spread = 0.08)
              .analog(13.0).gain(0.3).adsr(0.005, 0.5, 1.0, 0.05)
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
          .sound("supersaw").unison(voices = 7, spread = 0.09).gain(0.75 * 0.11).distort(1, "tube", 4).distort(0.85)
          .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2)).adsr(0.005, 2.5, 0.0, 0.027).lpf(attack = 0.005, decay = 1.0, sustain = 0.0, release = 0.01)
          .hpf(120).lpf(freq = 3200, env = 8.1, q = 1.8)
          .coarse(amount = 2, oversample = 4).pan(0.3).superimpose(
            x => x.pan(0.7),
            x => x.gain(0.75 * 0.09).hpf(240).lpf(3400).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-7 -3] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
                 .pan(0.2).superimpose(pan(0.8))
          ).superimpose(hpf(3500).lpf(6200).gain(0.75 * 0.03)).body(material = "wood", wet = 0.30)
        """.trimIndent(),
    )

    private val bass = voice(
        "BASS (full, saw)", "voice",
        """
        n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.98 0.99 0.98".fast(2))
          .orbit(4).scale("e1:minor").sound("saw").gain(0.5 * 0.20).distort(0.05, "soft", 2).clip(0.65)
          .adsr(0.007, 5.0, 0.0, 0.015).lpf(attack = 0.001, decay = 0.05, sustain = 0.0, release = 0.01).hpf(freq = 60, q = 1.0).lpf(freq = 200, env = 62, q = 1.0)
          .pan(0.50)
        """.trimIndent(),
    )

    private val drumsKick = voice(
        "DRUMS kick (bd, sampled)", "voice",
        """
        sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>")
          .pan(0.5).orbit(5).gain(0.24).hpf(45).lpf(11500).adsr(0.002, 0.10, 0.5, 0.2)
        """.trimIndent(),
    )

    private val hats = voice(
        "HATS (hh/oh/cr/rd, sampled)", "voice",
        """
        sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2)
          .pan(0.515).late(0.0005).orbit(5).gain(0.33).hpf(800).lpf("11500".add(perlin.mul(300))).adsr(0.005, 0.15, 0.8, 0.2)
        """.trimIndent(),
    )

    private val pink = voice(
        "PINK (pink noise)", "voice",
        """
        sound("pink!8").orbit(6).gain(0.08).hpf(8000).pan(sine.range(0.25, 0.75).slow(3)).adsr(0.007, 0.3, 0.0, 0.05)
        """.trimIndent(),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Targeted experiments
    // ────────────────────────────────────────────────────────────────────────────────────────

    // Distort oversample sweep on a supersaw-uni9 + filters base (isolates oversampling cost).
    private val distortBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(voices = 9, spread = 0.08)
          .gain(0.75).adsr(0.005, 2.5, 0.0, 0.029).hpf(400).lpf(freq = 3000, env = 8.1, q = 2.0)
    """.trimIndent()

    private fun distortCase(label: String, distort: String): SongBenchmark.Case =
        voice("DISTORT: $label", "exp-distort", "$distortBase\n    $distort")

    private val distortSweep = listOf(
        distortCase("no distort", ""),
        distortCase("os1 (1:tube:1)", """.distort(1, "tube", 1)"""),
        distortCase("os2 (1:tube:2)", """.distort(1, "tube", 2)"""),
        distortCase("os4 (1:tube:4)", """.distort(1, "tube", 4)"""),
        distortCase("os8 (1:tube:8)", """.distort(1, "tube", 8)"""),
        distortCase("os4 x2 (double, as song)", """.distort(1, "tube", 4).distort(0.80)"""),
    )

    // Unison sweep on the FULL guitar-1 effect chain (osc-gen scales with unison; fixed effects don't).
    private val fullChainTail =
        """.lpf(attack = 0.005, decay = 1.1, sustain = 0.0, release = 0.015).hpf(400).lpf(freq = 3000, env = 8.1, q = 2.0)""" +
                """.distort(1, "tube", 4).distort(0.80).clip(0.85).coarse(amount = 2, oversample = 4)""" +
                """.pan(0.15).superimpose(pan(0.85)).superimpose(hpf(3800).lpf(6700).gain(0.75 * 0.03))""" +
                """.body(material = "wood", wet = 0.3)"""

    private fun unisonCase(n: Int): SongBenchmark.Case =
        voice(
            "UNISON: full chain uni$n", "exp-unison",
            """n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(voices = $n, spread = 0.08).gain(0.75).adsr(0.005, 2.5, 0.0, 0.029)
    $fullChainTail""",
        )

    private val unisonSweep = listOf(1, 5, 9, 15).map { unisonCase(it) }

    // Body / room isolation on a fixed base: the clean marginal cost of each.
    private val fxBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(voices = 9, spread = 0.08)
          .gain(0.75).adsr(0.005, 2.5, 0.0, 0.029).hpf(400).lpf(freq = 3000, env = 8.1, q = 2.0)
          .distort(1, "tube", 4).clip(0.85)
    """.trimIndent()

    private val fxIsolation = listOf(
        voice("FX: base (osc+filt+dist)", "exp-fx", fxBase),
        voice("FX: base +body(wood)", "exp-fx", """$fxBase.body(material = "wood", wet = 0.3)"""),
        voice("FX: base +body(glass)", "exp-fx", """$fxBase.body(material = "glass", wet = 0.3)"""),
        voice("FX: base +vowel(a)", "exp-fx", """$fxBase.vowel(vowel = "a", wet = 0.3)"""),
        voice("FX: base +room", "exp-fx", """$fxBase.reverb(0.10, 1.2)"""),
        voice("FX: base +body+room", "exp-fx", """$fxBase.body(material = "wood", wet = 0.3).reverb(0.10, 1.2)"""),
    )

    // 2x2 interaction: does `superimpose` MULTIPLY the cost of a per-voice effect (`body`)?
    // cost(body | no super)  = [+body]           - [base]
    // cost(body | 1 super)   = [+super +body]    - [+super]
    // If the second is much larger than the first, superimpose amplifies body → multiplicative.
    private val intBase = """
        n("0 2 4 5").fast(2).orbit(1).scale("e3:minor").sound("supersaw").unison(voices = 9, spread = 0.08)
          .gain(0.75).adsr(0.005, 2.5, 0.0, 0.029).hpf(400).lpf(freq = 3000, env = 8.1, q = 2.0).distort(1, "tube", 4).clip(0.85)
    """.trimIndent()

    private val interactionSweep = listOf(
        voice("INT: base (no super, no body)", "exp-interaction", intBase),
        voice("INT: +body (no super)", "exp-interaction", """$intBase.body(material = "wood", wet = 0.3)"""),
        voice("INT: +super (no body)", "exp-interaction", """$intBase.pan(0.15).superimpose(pan(0.85))"""),
        voice("INT: +super +body", "exp-interaction", """$intBase.pan(0.15).superimpose(pan(0.85)).body(material = "wood", wet = 0.3)"""),
    )

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Full frozen songs
    // ────────────────────────────────────────────────────────────────────────────────────────

    fun frozenSongs(): List<SongBenchmark.Case> = listOf(
        SongBenchmark.Case(
            name = "Der Schmetterling (FULL frozen 09-25)",
            code = FrozenSongs.derSchmetterling_2026_09_25,
            group = "full-song",
            rpm = 32.5,
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
            name = "Der Schmetterling (FROZEN 09-25)",
            code = FrozenSongs.derSchmetterling_2026_09_25,
            group = "full-song",
            rpm = 32.5,
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

    /** The audio-rate modulator paths the song does not use: one FM voice, one LFO-modulated voice. */
    private val fmBell = voice(
        "FM BELL (sine, fm 200:1.4)", "voice",
        """
        n("<0 2 4 5 7 5 4 2>").fast(2).orbit(6).scale("e4:minor").sound("sine").fm(200, 1.4, 0.01, 0.6, 0.2)
          .adsr(0.005, 0.8, 0.3, 0.3).gain(0.4).pan(0.5)
        """,
    )

    private val lfoPad = voice(
        "LFO PAD (saw, vibrato + tremolo)", "voice",
        """
        n("<[0,4,7] [-3,2,5]>").orbit(6).scale("e3:minor").sound("saw").vibrato(5.5, 0.3).tremolo(0.5, 4)
          .adsr(0.05, 0.5, 0.7, 0.4).gain(0.4).pan(0.5)
        """,
    )

    fun voices(): List<SongBenchmark.Case> = listOf(
        // isolated voices — the full lead/gtr1 chains are the top rung of their ladders, relabelled
        // into the shared "voice" group so they sit alongside the other isolated voices.
        leadLadder.last().copy(name = "LEAD (full, superramp uni5)", group = "voice"),
        guitar1Ladder.last().copy(name = "GTR1 (full, supersaw uni9)", group = "voice"),
        guitar2, bass, drumsKick, hats, pink, fmBell, lfoPad,
    )

    fun ladders(): List<SongBenchmark.Case> = leadLadder + guitar1Ladder + gtrFxLadder

    fun experiments(): List<SongBenchmark.Case> = distortSweep + unisonSweep + fxIsolation + interactionSweep

    // ────────────────────────────────────────────────────────────────────────────────────────
    // Live rig ablation (2026-09-15): where the cycles go in the CURRENT Der Schmetterling
    // ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A song text with every section gate (`.mute("<...>")`) removed, so each part plays continuously,
     * and its shuffle seed pinned: the song seeds it from the wall clock, so every pass and every arm
     * of an A/B would otherwise render a different realisation.
     */
    private fun ungated(code: String): String =
        swap("seed(timeOfDay.mul(60*60*60*24))", "seed(0.5)")(
            Regex("""\.mute\("<[^"]*>"\)""").replace(code, ""),
        )

    /** The live song, ungated. */
    private val liveUngated: String by lazy { ungated(derSchmetterlingSong.code) }

    /**
     * The song axis of the optimization series (`docs/plans/blog-optimization-series.md`, P16): every
     * snapshot of Der Schmetterling the caller drops into `KLANG_SNAPSHOT_DIR` as `NN__<rpm>__<label>.klang`
     * (the song text as it stood at a tag, extracted from `builtinsongs/DerSchmetterling.kt` at that tag),
     * rendered ungated with the seed pinned, on ONE engine, eight cycles each, so the census columns show
     * the song's work growing while the engine stays put. A snapshot that no longer parses fails the run
     * loudly rather than silently dropping out. Use `--args=snapshots`.
     */
    fun snapshots(): List<SongBenchmark.Case> {
        val dir = System.getenv("KLANG_SNAPSHOT_DIR")
            ?: error("snapshots: set KLANG_SNAPSHOT_DIR to a directory of NN__<rpm>__<label>.klang files")
        val files = java.io.File(dir).listFiles { f -> f.name.endsWith(".klang") }?.sortedBy { it.name }
            ?: error("snapshots: $dir is not a directory")

        // Older texts have no wall-clock seed to pin, so the seed swap is lenient here; the gates go always.
        fun ungatedLenient(code: String): String =
            Regex("""seed\(timeOfDay[^)]*\)\)""").replace(Regex("""\.mute\("<[^"]*>"\)""").replace(code, ""), "seed(0.5)")

        // The frozen row used to open the axis as its July anchor; since the 2026-09-25 re-snapshot it is
        // the same text as the HEAD file `console/song-snapshots.sh` appends last, so it is not repeated
        // here: the axis is the tag files, oldest first.
        return files.map { file ->
            val parts = file.name.removeSuffix(".klang").split("__", limit = 3)

            require(parts.size == 3) { "snapshots: ${file.name} is not NN__<rpm>__<label>.klang" }

            SongBenchmark.Case(
                name = parts[2],
                group = "snapshots",
                rpm = parts[1].toDouble(),
                cycles = 8,
                code = ungatedLenient(file.readText()),
            )
        }
    }

    /** A case that renders [expr] on top of the ungated live song, after [edit] has rewritten the song text. */
    private fun liveCase(name: String, group: String, expr: String, edit: (String) -> String = { it }): SongBenchmark.Case =
        SongBenchmark.Case(
            name = name,
            group = group,
            rpm = derSchmetterlingSong.rpm,
            cycles = 8,
            code = edit(liveUngated) + "\n\n" + expr + "\n",
        )

    /** Text swap that fails loudly when the song no longer contains the anchor: the suite tracks the live song. */
    private fun swap(from: String, to: String): (String) -> String = { src ->
        require(src.contains(from)) { "rig suite anchor not found in the live song: $from" }
        src.replace(from, to)
    }

    /** Every match of [from] goes, and there must be exactly [expected] of them: a re-authored song fails loudly. */
    private fun swapAll(from: Regex, to: String, expected: Int): (String) -> String = { src ->
        val matches = from.findAll(src).count()

        require(matches == expected) { "rig suite anchor matched $matches times in the live song, expected $expected: ${from.pattern}" }

        src.replace(from, to)
    }

    /**
     * The song's arrangement without its two-cycle count-in: the band's gate (`late(2).filterWhen(t >= 2)`)
     * goes, and the count-in's own gate (`filterWhen(t < 2)`) becomes never, so the band plays from cycle 0
     * and the count-in's orbit config (no compressor, its own room) never claims the hats' orbit.
     */
    private val ungateSong: (String) -> String = { src ->
        swap("filterWhen(t => t < 2)", "filterWhen(t => t < 0)")(swap("x => x.late(2).filterWhen(t => t >= 2)", "x => x")(src))
    }

    /** Like [swap] with a pattern, for anchors whose VALUE is tuned by ear (a wet amount, a level). */
    private fun swap(from: Regex, to: String): (String) -> String = { src ->
        val matches = from.findAll(src).count()

        require(matches == 1) { "rig suite anchor matched $matches times in the live song, needs exactly one: ${from.pattern}" }

        src.replace(from, to)
    }

    private const val RHYTHM_RIG =
        "let guitar       = makeGuitar(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, cab4x12)"

    private fun rhythmRig(pickup: String, pedal: String, preamp: String, power: String, cab: String): String =
        "let guitar       = makeGuitar($pickup, $pedal, $preamp, $power, $cab)"

    private const val BAND = ".analog(feel).transpose(transposition)"
    private const val RHYTHM = "stack(guitar2.apply(guitar2_arrange), guitar3.apply(guitar3_arrange))$BAND"
    private const val LEAD = "lead.apply(lead_arrange)$BAND"
    private const val TROMMEL = "trommel.apply(trommel_arrange)$BAND"

    fun rig(): List<SongBenchmark.Case> = listOf(
        // each part solo
        liveCase("guitar1 (melody rig, uni 15)", "part", "guitar1.apply(guitar1_arrange)$BAND"),
        liveCase("guitar2+3 (rhythm rig, uni 13+11)", "part", RHYTHM),
        liveCase("lead (marimba)", "part", LEAD),
        liveCase("trommel", "part", TROMMEL),
        liveCase("bass", "part", "bass.apply(bass_arrange)$BAND"),
        liveCase(
            "drums (samples)", "part",
            "stack(kick.apply(kick_arrange), snare.apply(snare_arrange), hats.apply(hats_arrange), clap.apply(clap_arrange), shaker.apply(shaker_arrange)).analog(feel / 2)",
        ),
        // the rhythm rig, one stage at a time back to stock
        liveCase("rhythm: full rig", "rig", RHYTHM),
        liveCase("rhythm: pickup stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupStock", "pedalScreamer", "preampHighGain", "powerPushPull", "cab4x12"))),
        liveCase("rhythm: pedal stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupHumbucker", "pedalStock", "preampHighGain", "powerPushPull", "cab4x12"))),
        liveCase("rhythm: preamp stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupHumbucker", "pedalScreamer", "preampStock", "powerPushPull", "cab4x12"))),
        liveCase("rhythm: power stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupHumbucker", "pedalScreamer", "preampHighGain", "powerStock", "cab4x12"))),
        liveCase("rhythm: cab stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupHumbucker", "pedalScreamer", "preampHighGain", "powerPushPull", "cabStock"))),
        liveCase("rhythm: all stock", "rig", RHYTHM, swap(RHYTHM_RIG, rhythmRig("pickupStock", "pedalStock", "preampStock", "powerStock", "cabStock"))),

        // the string side of the same guitars: what the unison count, the analog drift and the
        // string extras (pitch envelope, crackle burst) cost, the rig untouched
        liveCase("rhythm: uni 7+7", "string", RHYTHM) {
            swap("unison(voices = 13, spread = 0.05)", "unison(voices = 7, spread = 0.05)")(
                swap("unison(voices = 11, spread = 0.05)", "unison(voices = 7, spread = 0.05)")(it),
            )
        },
        liveCase("rhythm: no analog", "string", "stack(guitar2.apply(guitar2_arrange), guitar3.apply(guitar3_arrange)).analog(0).transpose(transposition)"),
        liveCase("rhythm: no string extras", "string", RHYTHM) {
            swap("    .pitchEnvelope(0.5, x => x.adsr(0.001, 0.02, 0, 0))\n", "")(
                swap("    .plus(Osc.crackle(1.25).highpass(1000).adsr(0.005, 0.1, 0.0, 0.05).mul(1.0))\n", "")(it),
            )
        },
        // the marimba, one component at a time
        liveCase("marimba: full", "marimba", LEAD),
        liveCase("marimba: no analog", "marimba", LEAD, swap("let pAnalog = OscSlot.analog\n  let ring = Osc.constant(400)", "let pAnalog = 0\n  let ring = Osc.constant(400)")),
        liveCase("marimba: no body", "marimba", LEAD, swap(Regex("""\.body\(material = "wood", wet = [0-9.]+\)"""), "")),
        // the drum, one component at a time
        liveCase("trommel: full", "trommel", TROMMEL),
        liveCase("trommel: no harmonic bank", "trommel", TROMMEL, swap(".plus(harms)", "")),
        liveCase("trommel: no distort", "trommel", TROMMEL, swap(Regex("""(\.plus\(beater\)\s*)\.distort\([0-9.]+, "tube", 2\)"""), "$1")),
        liveCase("trommel: no body", "trommel", TROMMEL, swap(Regex("""\.body\(material = "membrane", wet = [0-9.]+\)"""), "")),
        liveCase("trommel: no analog", "trommel", TROMMEL, swap("let pAnalog = OscSlot.analog\n  let ring = Osc.constant(150)", "let pAnalog = 0\n  let ring = Osc.constant(150)")),

        // the whole song, and the whole song without its orbit compressors (three calls, one
        // compressor per orbit they cover, nine instances; the master limiter stays): what the
        // compressor's per-sample ln and exp cost across the mix. The song's arrangement holds two
        // count-in cycles before the band; both cases drop that gate and the count-in itself, so
        // all eight rendered cycles play the band and nothing else.
        liveCase("song: full", "song", "song", ungateSong),
        liveCase("song: no compressors", "song", "song") {
            swapAll(Regex("""\.compressor\([^)]*\)"""), "", expected = 3)(ungateSong(it))
        },
    )

    /**
     * The ledger suite (`--args=ledger`): the instrument pieces of Der Schmetterling, each solo and
     * ungated, on the FROZEN song text of `FrozenPieces` (so the engine is the only thing that can
     * move a piece's numbers between runs) and on the LIVE text (so the same run shows how far the
     * song has moved since the snapshot). Rows are appended to `docs/benchmarks/ledger.md`.
     */
    fun ledger(): List<SongBenchmark.Case> {
        val pieces = listOf(
            "guitar melody (rig)" to "guitar1.apply(guitar1_arrange)$BAND",
            "guitars rhythm (rig)" to RHYTHM,
            "marimba" to LEAD,
            "trommel" to TROMMEL,
            "bass" to "bass.apply(bass_arrange)$BAND",
            "drums (samples)" to "stack(kick.apply(kick_arrange), snare.apply(snare_arrange), hats.apply(hats_arrange), clap.apply(clap_arrange), shaker.apply(shaker_arrange)).analog(feel / 2)",
        )
        val frozen = ungated(FrozenPieces.derSchmetterling_2026_09_16)

        return pieces.map { (name, expr) ->
            SongBenchmark.Case(
                name = "$name @ frozen 2026-09-16",
                group = "ledger",
                rpm = FrozenPieces.derSchmetterlingRpm_2026_09_16,
                cycles = 8,
                code = frozen + "\n\n" + expr + "\n",
            )
        } + pieces.map { (name, expr) ->
            SongBenchmark.Case(
                name = "$name @ live",
                group = "ledger-live",
                rpm = derSchmetterlingSong.rpm,
                cycles = 8,
                code = liveUngated + "\n\n" + expr + "\n",
            )
        }
    }

    fun all(): List<SongBenchmark.Case> = voices() + ladders() + experiments() + frozenSongs()
}
