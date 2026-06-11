package io.peekandpoke.klang.sprudel.golden

/**
 * Deterministic corpus for the mutable-[io.peekandpoke.klang.sprudel.SprudelVoiceData] differential
 * golden test ([MutableVoiceDataGoldenSpec]).
 *
 * The golden test guards the single-owner / no-aliasing invariant that makes in-place mutation of
 * `SprudelVoiceData` safe. Aliasing bugs are silent (one event's data bleeding into another), so the
 * corpus must heavily exercise the constructs where a shared `data` reference can leak:
 *
 *  - **leaf emitters** — `AtomicPattern`, `AtomicInfinitePattern` (the `ply` family)
 *  - **fan-out** — `stack`, `superimpose`, `jux`
 *  - **time fan-out** — `ply`, `echo`
 *
 * Everything here is deterministic: no wall-clock, and every random construct runs under a pinned seed.
 */
object GoldenCorpus {

    data class Entry(
        val name: String,
        val code: String,
        /** How many cycles to capture for this entry. */
        val cycles: Int,
    )

    /**
     * Frozen snapshot of the built-in song "Der Schmetterling" as of 2026-06-05.
     *
     * Kept as a *copy* on purpose — the live song in `builtinsongs/DerSchmetterling.kt` will keep
     * changing, but the golden needs a stable fixture at a good complexity level. The only change from
     * the live version is the seed: the live song uses `seed(timeOfDay.mul(...))` (wall-clock,
     * non-deterministic); here it is pinned to a constant so the random `|`-choices and noise signals
     * (`berlin`, `saw`) are fully reproducible.
     */
    private val derSchmetterlingPinned = """
import * from "stdlib"
import * from "sprudel"

let feel = 2.5

stack(
  // Lead
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)
    .orbit(1).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(11).detune(0.03).analog(feel)
    .hpf(1350).lpf(1625).lpe(berlin.range(3.0, 3.0)).lpq(1.2).lpadsr("0.005:3.0:0.5:0.05")
    .gain(1.00).distort("0.300:tube:4").postgain("<0.800!48 0.325!16>")
    .adsr("0.01:3.0:0.0:0.05").clip(0.8)
    .release("<0.25!16 0.15!16>")
    .phaser(1/8).phaserdepth(0.15).phasersweep(1000).phasercenter(1800)
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>").coarse(2).coarseos(8)
    .superimpose(x => x.transpose(12).detune(0.10).velocity("<0!32 0.15!32>").pan(0.3).late(0.001),
                 x => x.transpose(12).detune(0.15).velocity("<0!32 0.15!32>").pan(0.7).late(0.0015))
    .mute("<1!32 0!256>").engine("pedal"),
  // Guitar 1
  n(`<[7 4 2 <-1 4 1 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [4 7@3] [4 6@3]>]!4
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [-1 -3] 0 <[4 6] [0, -3]>] [<7 [[7 4 6 4]!4]> [-5 -6] -7 [-2 <3 -1>]]>/4`)
    .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>")
    .velocity("1.00 0.95!3 0.98 0.95!3".fast(2)).analog(feel)
    .sound("supersaw").unison(17).detune(0.08)
    .distort("1.0:tube:4").distort(1.0)
    .adsr("0.005:3.0:0.0:0.005").adsrCurves("square:exp:cube").lpadsr("0.007:1.0:0.0:0.005")
    .clip("<0.96!31 0.9 0.96!31 0.825>".fast(2))
    .gain(0.6).postgain(0.365).hpf("<400!48 700!16 400!32 300!32>").lpq(1.5).lpf(saw.range(1,0).pow(1.5).mul(1000).add(1200).slow(4)).lpe(1.0).lpq(2.0)
    .pan(0.2).superimpose(pan(0.8))
    .orbit(1).engine("pedal")
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")
    .scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .analog(feel).sound("supersaw").unison(11).detune(0.08)
    .hpf(180).hpq(1.0).lpf(1400).lpe(1.0).lpq(2.50)
    .adsr("0.009:3.0:0.0:0.005").adsrCurves("square:exp:cube").lpadsr("0.007:1.0:0.0:0.005").velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
    .clip("<0.96!31 0.9 0.96!31 0.825>".fast(2)).gain(0.8).distort("1:tube:4").distort(1.0)
    .coarse(2).coarseos(2)
    .pan(0.35).postgain(0.275).superimpose(
      x => x.pan(0.65),
      x => x.postgain(0.25).hpf(180).lpf(1900)
            .scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.3).superimpose(pan(0.7))
    ).orbit(2).mute("<0!128 1!16 0!16>").engine("pedal")
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!32 [x@3 x]!32 [x!4]!64>").fast(2).velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!88 e3:minor!8>").sound("saw").gain(0.5).distort("0.2:tube:1").coarse(2).postgain(0.6).clip(1.0)
    .adsr("0.015:0.3:0.0:0.15").hpf(80).hpq(1).lpf(100).lpe(2).lpadsr("0.01:0.1:0.0:0.22")
    .mute("<0!128 1!32>")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd ~  bd ~ ]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>")
    .early(0.002).orbit(5).gain(0.85).hpf(80).lpf(5000).adsr("0.005:0.15:0.1:0.1"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd ~  sd]!24 [~  sd ~  sd]!24>").mute("<0!128 1!32>")
    .early(0.002).orbit(5).gain(0.95).hpf(180).lpf(7000).adsr("0.006:0.25:0.1:0.1").superimpose(bandf(220).bandq(4).gain(0.4)),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")
    .late(0.004).orbit(5).gain(1.00).hpf(3000).lpf(6000).lpq(1.2).adsr("0.01:0.2:0.5:0.2")
).room("0.02:5").compressor("-6:3:10:0.02:0.25").seed(42)
    """.trimIndent()

    val entries: List<Entry> = listOf(
        // Real-world complexity (stack + superimpose + struct + scale + seeded choices/noise).
        Entry("der-schmetterling", derSchmetterlingPinned, cycles = 24),

        // --- Targeted patterns for constructs the song does not cover -------------------------------
        // ply: duplicates each event via AtomicInfinitePattern (leaf #2) — the shared-data hot spot.
        Entry("ply", """note("c3 e3 g3").gain(0.7).ply(3).pan("0.2 0.8")""", cycles = 8),
        // echo: time-shifted copies that decay — re-emits leaf data offset in time.
        Entry("echo", """note("c3 e3").sound("supersaw").gain(0.9).echo(3, 0.125, 0.6)""", cycles = 8),
        // jux: stereo fan-out, left/right transformed independently from one source.
        Entry("jux", """note("c3 e3 g3 b3").sound("saw").jux(x => x.rev().gain(0.5))""", cycles = 8),
        // superimpose stacked with ply — fan-out of a fan-out.
        Entry(
            "superimpose-ply",
            """note("c3 e3 g3").gain(0.8).superimpose(x => x.transpose(12).gain(0.4)).ply("2 1 3")""",
            cycles = 8,
        ),
    )
}
