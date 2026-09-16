/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Song-level CPU benchmark runner.
 *
 * Run with:  `./gradlew runSongBenchmark`            (all suites)
 *            `./gradlew runSongBenchmark --args=ladders`   (one suite)
 *
 * Suites: `voices`, `ladders`, `experiments`, `gtrpoly`, `songs`, `live`, `rig`, `snapshots` (the song
 * texts in `KLANG_SNAPSHOT_DIR`, one engine), `ledger` (appends to
 * `docs/benchmarks/ledger.md`), `all` (default).
 *
 * Prints a table per suite (with ladder deltas) and saves a markdown report to docs/benchmarks/.
 */
fun main(args: Array<String>) {
    val suite = args.firstOrNull()?.lowercase() ?: "all"

    println("=== Klang Song CPU Benchmark (JVM) ===")
    println("Runtime: ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
    println("Suite: $suite")
    println()

    // Samples are needed for the drum/percussion voices to actually render. Best-effort — the
    // synth voices (the CPU-heavy ones) render fine without them.
    val samples: Samples? = try {
        runBlocking { Samples.create(catalogue = SampleCatalogue.default) }
    } catch (e: Throwable) {
        println("[warn] could not load samples (${e.message}); sampled voices will be silent.")
        null
    }

    val bench = SongBenchmark(samples = samples)

    val cases = when (suite) {
        "voices" -> SongBenchmarkCases.voices()
        "ladders" -> SongBenchmarkCases.ladders()
        "experiments", "exp" -> SongBenchmarkCases.experiments()
        "gtrpoly" -> SongBenchmarkCases.gtrPoly()
        "songs", "full" -> SongBenchmarkCases.frozenSongs()
        "live" -> SongBenchmarkCases.live()
        "rig" -> SongBenchmarkCases.rig()
        "ledger" -> SongBenchmarkCases.ledger()
        "snapshots" -> SongBenchmarkCases.snapshots()
        else -> SongBenchmarkCases.all()
    }

    println("Running ${cases.size} cases...")
    println()

    val results = bench.run(cases)

    val md = StringBuilder()
    md.appendLine("# Song CPU Benchmark — JVM")
    md.appendLine()
    md.appendLine("- Runtime: `${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}`")
    // Read off the instance, not from constants: the report IS the artifact, so it has to record
    // what this run actually measured. A hardcoded line here is how the previous report ended up
    // claiming 512 frames after the engine moved to 128.
    md.appendLine("- Sample rate: ${bench.sampleRate} Hz, block: ${bench.blockFrames} frames")
    md.appendLine("- `medianRtf` = render/audio time (steady-state avg). `peakBlockRtf` = busiest single block (≈ worst render-cycle CPU).")
    md.appendLine("- Lower is better. On JVM values are small; the browser worklet is ~10-30x slower, so multiply to relate to the 50% figure.")
    md.appendLine()

    // Group results by their case group, preserving case order within a group.
    val byGroup = results.groupBy { it.group }
    for ((group, rows) in byGroup) {
        printGroup(group, rows, md, bench.sampleRate)
    }

    // Save markdown
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
    val outDir = File("docs/benchmarks").apply { mkdirs() }
    val outFile = File(outDir, "${stamp}_song_jvm.md")
    outFile.writeText(md.toString())
    println()
    println("Saved markdown: ${outFile.path}")

    if (suite == "ledger") {
        appendLedger(results, bench, stamp, outDir)
    }
}

/**
 * The ledger: one row per piece per run, APPENDED to `docs/benchmarks/ledger.md`, so every
 * optimization round adds its rows next to the previous ones and a piece's history reads down
 * one column. The engine version is `git describe`; the piece names its frozen snapshot, so two
 * rows are comparable exactly when the piece and the machine match.
 */
private fun appendLedger(results: List<SongBenchmark.Result>, bench: SongBenchmark, stamp: String, outDir: File) {
    val ledger = File(outDir, "ledger.md")
    val describe = runCatching {
        val process = ProcessBuilder("git", "describe", "--tags", "--always", "--dirty").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()

        if (process.waitFor() == 0) text else "unknown"
    }.getOrDefault("unknown")
    val cpu = runCatching {
        File("/proc/cpuinfo").useLines { lines -> lines.firstOrNull { it.startsWith("model name") }?.substringAfter(":")?.trim() }
    }.getOrNull() ?: System.getProperty("os.arch")

    if (!ledger.exists()) {
        ledger.writeText(
            """
            |# The ledger
            |
            |One row per piece per run of `./gradlew runSongBenchmark --args=ledger`, appended, never edited by hand.
            |A piece is a FROZEN instrument text (`FrozenPieces.kt`), so within one piece and one machine the engine is
            |the only thing that moves between rows. Columns: `voices` rendering voices per block (median); `work` passes
            |over the block per block (median, summed over the voices, from `GraphCensus`); `traffic` block-buffer reads
            |and writes per sample (median); `KiB` state the voices hold (busiest block); `ns/smp/voice` render time per
            |sample per rendering voice; `ns/smp/pass` render time per sample per pass, the engine's cost per unit of work.
            |`voices` and the two `ns/smp/*` columns are comparable among harness rows only.
            |The census counts the voice's IGNITOR graph; the strip's own stages (a pipeline filter, the orbit effects, the
            |master) are not in it, and a sample voice counts as one pass, while the render time in the numerator of both
            |`ns/smp/*` columns covers all of it: those two columns compare an engine against itself on ONE piece, never one
            |piece against another. JVM, ${bench.sampleRate} Hz, ${bench.blockFrames}-frame blocks.
            |The detail file of each run sits next to this one.
            |
            || date | engine | machine | piece | onsets | culled | voices | work | traffic | KiB | medRTF | peakRTF | ns/smp/voice | ns/smp/pass |
            ||------|--------|---------|-------|-------:|-------:|-------:|-----:|--------:|----:|-------:|--------:|-------------:|------------:|
            |""".trimMargin(),
        )
    }

    val sb = StringBuilder()

    for (r in results) {
        if (r.error != null) {
            continue
        }

        sb.appendLine(
            "| %s | %s | %s | %s | %d | %d | %.1f | %.0f | %.0f | %.0f | %.5f | %.5f | %.1f | %.1f |".format(
                Locale.ROOT,
                stamp.substring(0, 10), describe, cpu, r.name, r.onsets, r.culled,
                r.medianVoices, r.medianWork, r.medianTraffic, r.peakBytes / 1024.0, r.medianRtf, r.peakBlockRtf,
                r.nsPerSamplePerVoice(bench.sampleRate), r.nsPerSamplePerPass(bench.sampleRate),
            ),
        )
    }

    ledger.appendText(sb.toString())
    println("Appended ${results.count { it.error == null }} rows to ${ledger.path}")
}

private fun printGroup(group: String, rows: List<SongBenchmark.Result>, md: StringBuilder, sampleRate: Int) {
    val title = when (group) {
        "voice" -> "Isolated voices"
        "full-song" -> "Full frozen songs"
        else -> group
    }
    val isLadder = group == "LEAD" || group == "GTR1" || group == "GTR-FX"

    println("── $title " + "─".repeat(maxOf(1, 60 - title.length)))
    md.appendLine("## $title")
    md.appendLine()

    val header = if (isLadder) {
        "%-42s %7s %7s %10s %10s %12s %10s".format("case", "onsets", "culled", "medRTF", "peakRTF", "us/cycle", "Δ medRTF")
    } else {
        "%-42s %7s %7s %7s %6s %10s %10s %12s %9s".format("case", "onsets", "culled", "voices", "work", "medRTF", "peakRTF", "us/cycle", "ns/s/pass")
    }
    println(header)
    println("-".repeat(header.length))

    if (isLadder) {
        md.appendLine("| case | onsets | culled | medRTF | peakRTF | µs/cycle | Δ medRTF |")
        md.appendLine("|------|-------:|-------:|-------:|--------:|---------:|---------:|")
    } else {
        md.appendLine("| case | onsets | culled | voices | work | traffic | KiB | medRTF | peakRTF | µs/cycle | ns/smp/voice | ns/smp/pass |")
        md.appendLine("|------|-------:|-------:|-------:|-----:|--------:|----:|-------:|--------:|---------:|-------------:|------------:|")
    }

    var prev = 0.0
    for ((i, r) in rows.withIndex()) {
        if (r.error != null) {
            println("%-42s  ERROR: %s".format(r.name.take(42), r.error))
            val blanks = if (isLadder) "| — | — | — | — | — " else "| — | — | — | — | — | — | — | — | — | — "
            md.appendLine("| ${r.name} $blanks| ERROR: ${r.error} |")
            continue
        }
        val shortName = r.name.substringAfter(": ").ifBlank { r.name }.take(42)
        if (isLadder) {
            val delta = if (i == 0) 0.0 else r.medianRtf - prev
            println(
                "%-42s %7d %7d %10.5f %10.5f %12.1f %+10.5f".format(
                    shortName, r.onsets, r.culled, r.medianRtf, r.peakBlockRtf, r.renderUsPerCycle, delta
                )
            )
            md.appendLine(
                "| $shortName | ${r.onsets} | ${r.culled} | ${"%.5f".format(r.medianRtf)} | ${"%.5f".format(r.peakBlockRtf)} | ${"%.1f".format(r.renderUsPerCycle)} | ${
                    "%+.5f".format(
                        delta
                    )
                } |"
            )
            prev = r.medianRtf
        } else {
            println(
                "%-42s %7d %7d %7.1f %6.0f %10.5f %10.5f %12.1f %9.1f".format(
                    shortName, r.onsets, r.culled, r.medianVoices, r.medianWork, r.medianRtf, r.peakBlockRtf, r.renderUsPerCycle,
                    r.nsPerSamplePerPass(sampleRate),
                )
            )
            md.appendLine(
                "| $shortName | ${r.onsets} | ${r.culled} | ${"%.1f".format(r.medianVoices)} | ${"%.0f".format(r.medianWork)} | ${"%.0f".format(r.medianTraffic)} | ${"%.0f".format(r.peakBytes / 1024.0)} | ${"%.5f".format(r.medianRtf)} | ${"%.5f".format(r.peakBlockRtf)} | ${"%.1f".format(r.renderUsPerCycle)} | ${"%.1f".format(r.nsPerSamplePerVoice(sampleRate))} | ${"%.1f".format(r.nsPerSamplePerPass(sampleRate))} |"
            )
        }
    }
    println()
    md.appendLine()
}
