/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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

    fun ladders(): List<SongBenchmark.Case> = leadLadder + guitar1Ladder

    fun experiments(): List<SongBenchmark.Case> = distortSweep + unisonSweep + fxIsolation + interactionSweep

    fun all(): List<SongBenchmark.Case> = voices() + ladders() + experiments() + frozenSongs()
}
