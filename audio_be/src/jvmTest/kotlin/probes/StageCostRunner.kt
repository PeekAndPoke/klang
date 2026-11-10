/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.Locale

/**
 * The stage cost table's RUNNER (KLANG-00420, probe 7): every row of [stageCostCases] timed by [measureCosts], the
 * table sorted by net cost, printed and written to a file. A measurement, not a gate: it asserts nothing about the
 * numbers, which belong to the machine and the moment they were taken on.
 *
 * **Out of the normal run.** Its one test is disabled unless the environment holds `KLANG_PROBE=cost`, so a normal
 * `jvmTest` reports it as skipped and measures nothing (the stage probe's pattern, [StageProbeRunner]). Run it:
 *
 *     KLANG_PROBE=cost console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.probes.StageCostRunner
 *
 * The table goes to `$KLANG_PROBE_OUT` when set (a file path), else to `stage-cost.txt` in `<build dir>/probes/`
 * ([probeOutputFile]); the same rows go beside it as JSON (`stage-cost.json`, [formatStageCostJson]). Compare two
 * runs before reading a difference between rows: the spread between runs on one machine is the floor.
 */
class StageCostRunner : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "cost"

    "stage cost: every source and stage alone, ns per sample (KLANG_PROBE=cost)".config(enabled = requested) {
        val started = nanoNow()
        val config = CostConfig()
        val cases = stageCostCases()
        val results = measureCosts(stages = cases.map { it.probe }, config = config)
        val rows = sortedByCost(deriveCostRows(cases = cases, results = results, sampleRate = config.sampleRate))
        val seconds = (nanoNow() - started) / 1e9
        val runtime = ManagementFactory.getRuntimeMXBean()
        val allocCounted = !allocatedBytesNow().isNaN()
        val report = buildString {
            appendLine("Stage cost table (KLANG-00420, probe 7), ${Instant.now()}")
            appendLine("JVM ${runtime.vmName} ${runtime.vmVersion}, ${Runtime.getRuntime().availableProcessors()} processors, max heap ${String.format(Locale.ROOT, "%.0f", Runtime.getRuntime().maxMemory().toDouble() / (1 shl 20))} MB")
            appendLine(
                "sample rate ${config.sampleRate} Hz (one sample = ${String.format(Locale.ROOT, "%.1f", 1e9 / config.sampleRate)} ns), " +
                    "block ${config.blockFrames} frames, every case rendered ${config.blocksLong} blocks and ${config.blocksShort} block per visit, " +
                    "ns per sample = (long - short) / the frames between",
            )
            appendLine(
                "${config.warmupRounds} warm-up rounds, then ${config.rounds} timed rounds; every round visits every case once, " +
                    "in its own shuffled order (seed ${config.seed}); a fresh stage instance per render",
            )
            appendLine(
                "input: full-scale white noise, seeded, held (an Ignitor gate never closes inside the render); sources and voices " +
                    "play a held middle C ($COST_NOTE_HZ Hz); the catalogue's Ignitor stages play the stage probe's 220 Hz",
            )
            appendLine(
                "allocation: " + if (allocCounted) {
                    "HotSpot's per-thread allocated bytes around each render, (long - short) / the blocks between"
                } else {
                    "not counted (this JVM has no per-thread counter)"
                },
            )
            appendLine()
            appendLine("Columns:")
            STAGE_COST_LEGEND.forEach { appendLine("  $it") }
            appendLine()
            append(formatStageCostTable(rows))
            appendLine()
            appendLine("${rows.size} rows, ${String.format(Locale.ROOT, "%.1f", seconds)} s")
        }

        println(report)

        val target = probeOutputFile("stage-cost.txt")

        writeProbeFile(target = target, text = report, what = "stage cost table")
        writeProbeFile(target = jsonTwin(target), text = formatStageCostJson(rows), what = "stage cost JSON")
    }
})
