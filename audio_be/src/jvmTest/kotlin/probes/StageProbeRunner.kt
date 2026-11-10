/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import java.time.Instant
import java.util.Locale

/**
 * The stage probe's RUNNER (KLANG-00420, probe 1): every stage of [stageProbeCases] under every signal of
 * [standardProbeSignals], one table row each, printed and written to a file. A measurement, not a gate: it asserts
 * nothing about the numbers.
 *
 * **Out of the normal run.** Its one test is disabled unless the environment holds `KLANG_PROBE=stage`, so a normal
 * `jvmTest` reports it as skipped and measures nothing (no Gradle task of its own, decided 2026-11-10). Run it:
 *
 *     KLANG_PROBE=stage console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.probes.StageProbeRunner
 *
 * The table goes to `$KLANG_PROBE_OUT` when set (a file path), else to `stage-probe.txt` in the build directory the
 * test classes were compiled into (`<build dir>/probes/`, [probeOutputFile]); the same rows go beside it as JSON
 * (`stage-probe.json`, [formatStageProbeJson]). The register is `audio/ref/verification.md`, "The probes".
 */
class StageProbeRunner : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "stage"

    "stage probe: every stage alone under the standard signals (KLANG_PROBE=stage)".config(enabled = requested) {
        val started = System.nanoTime()
        val signals = standardProbeSignals()
        val cases = stageProbeCases()
        val rows = ArrayList<StageProbeRow>()

        for (case in cases) {
            for (signal in signals) {
                rows.add(StageProbeRow(case = case, signal = signal, facts = measureStage(stage = case.probe, signal = signal)))
            }
        }

        val seconds = (System.nanoTime() - started) / 1e9
        val report = buildString {
            appendLine("Stage probe (KLANG-00420, probe 1), ${Instant.now()}")
            appendLine("sample rate $PROBE_SAMPLE_RATE Hz, block $PROBE_BLOCK_FRAMES frames, $PROBE_TAIL_SECONDS s of silence after every input")
            appendLine(
                "signals: impulse (1.0 at frame 0), step (DC 1.0 for 1 s), sweep (exp. sine 20 Hz..20 kHz, 2 s, full scale), " +
                    "silence, noise (white, uniform -1..1, 1 s, seeded per channel), nan / +inf / 1e30 (0.5 sine 220 Hz for 0.5 s, " +
                    "one bad sample on the LEFT at frame $PROBE_POISON_FRAME)",
            )
            appendLine(
                "katalyst: stereo, the production chain builder, applyParams(null) before every block (the output host's way), " +
                    "a duck's sidechain is the same signal",
            )
            appendLine(
                "ignitor: mono, the signal is the voice's sample playhead under classic() (one stage switched on by its slots, " +
                    "adsr.on=0 unless the adsr is probed) or under a bare node; gate closes at the input's end",
            )
            appendLine()
            appendLine("Columns:")
            STAGE_PROBE_LEGEND.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Stages and knob sets (${cases.size}):")
            cases.groupBy { "${it.family} ${it.stage}" }.forEach { (stage, group) ->
                appendLine("  $stage: ${group.joinToString(" | ") { it.knobs }}")
            }
            appendLine()
            append(formatStageProbeTable(rows))
            appendLine()
            appendLine("${rows.size} rows, ${String.format(Locale.ROOT, "%.1f", seconds)} s")
        }

        println(report)

        val target = probeOutputFile("stage-probe.txt")

        writeProbeFile(target = target, text = report, what = "stage probe table")
        writeProbeFile(target = jsonTwin(target), text = formatStageProbeJson(rows), what = "stage probe JSON")
    }
})
