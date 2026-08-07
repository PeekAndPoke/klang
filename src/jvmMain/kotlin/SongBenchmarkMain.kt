/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Song-level CPU benchmark runner.
 *
 * Run with:  `./gradlew runSongBenchmark`            (all suites)
 *            `./gradlew runSongBenchmark --args=ladders`   (one suite)
 *
 * Suites: `voices`, `ladders`, `experiments`, `songs`, `all` (default).
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
        "songs", "full" -> SongBenchmarkCases.frozenSongs()
        "live" -> SongBenchmarkCases.live()
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
        printGroup(group, rows, md)
    }

    // Save markdown
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
    val outDir = File("docs/benchmarks").apply { mkdirs() }
    val outFile = File(outDir, "${stamp}_song_jvm.md")
    outFile.writeText(md.toString())
    println()
    println("Saved markdown: ${outFile.path}")
}

private fun printGroup(group: String, rows: List<SongBenchmark.Result>, md: StringBuilder) {
    val title = when (group) {
        "voice" -> "Isolated voices"
        "full-song" -> "Full frozen songs"
        else -> group
    }
    val isLadder = group == "LEAD" || group == "GTR1"

    println("── $title " + "─".repeat(maxOf(1, 60 - title.length)))
    md.appendLine("## $title")
    md.appendLine()

    val header = if (isLadder) {
        "%-42s %7s %10s %10s %12s %10s".format("case", "onsets", "medRTF", "peakRTF", "us/cycle", "Δ medRTF")
    } else {
        "%-42s %7s %10s %10s %12s".format("case", "onsets", "medRTF", "peakRTF", "us/cycle")
    }
    println(header)
    println("-".repeat(header.length))

    if (isLadder) {
        md.appendLine("| case | onsets | medRTF | peakRTF | µs/cycle | Δ medRTF |")
        md.appendLine("|------|-------:|-------:|--------:|---------:|---------:|")
    } else {
        md.appendLine("| case | onsets | medRTF | peakRTF | µs/cycle |")
        md.appendLine("|------|-------:|-------:|--------:|---------:|")
    }

    var prev = 0.0
    for ((i, r) in rows.withIndex()) {
        if (r.error != null) {
            println("%-42s  ERROR: %s".format(r.name.take(42), r.error))
            md.appendLine("| ${r.name} | — | — | — | — | ERROR: ${r.error} |")
            continue
        }
        val shortName = r.name.substringAfter(": ").ifBlank { r.name }.take(42)
        if (isLadder) {
            val delta = if (i == 0) 0.0 else r.medianRtf - prev
            println(
                "%-42s %7d %10.5f %10.5f %12.1f %+10.5f".format(
                    shortName, r.onsets, r.medianRtf, r.peakBlockRtf, r.renderUsPerCycle, delta
                )
            )
            md.appendLine(
                "| $shortName | ${r.onsets} | ${"%.5f".format(r.medianRtf)} | ${"%.5f".format(r.peakBlockRtf)} | ${"%.1f".format(r.renderUsPerCycle)} | ${
                    "%+.5f".format(
                        delta
                    )
                } |"
            )
            prev = r.medianRtf
        } else {
            println(
                "%-42s %7d %10.5f %10.5f %12.1f".format(
                    shortName, r.onsets, r.medianRtf, r.peakBlockRtf, r.renderUsPerCycle
                )
            )
            md.appendLine(
                "| $shortName | ${r.onsets} | ${"%.5f".format(r.medianRtf)} | ${"%.5f".format(r.peakBlockRtf)} | ${"%.1f".format(r.renderUsPerCycle)} |"
            )
        }
    }
    println()
    md.appendLine()
}
