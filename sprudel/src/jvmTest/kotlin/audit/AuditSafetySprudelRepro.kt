/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.sprudel.SprudelPattern
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/**
 * The disproof pass of the engine audit (KLANG-00420), cluster "safety", sprudel's half: what a real PATTERN puts on
 * the wire as `pan` and `freqHz` (the frontend's own conversion, `KlangPatternEvent.toVoiceData()`, which
 * `KlangPatternScheduler` calls for every event it sends), and what the engine makes of it end to end
 * (`KlangOfflineRenderer`: the KlangScript compile, the real scheduler and wire, the live dispatcher, the house stage).
 * A measurement: it prints what it found and asserts nothing. The report is
 * `docs/audio-audit/2026-11-10/audit/disproof-safety.md`; the engine-side twin is `AuditSafetyRepro` in `audio_be`.
 *
 * **Out of the normal run**: every test is disabled unless the environment holds `KLANG_PROBE=audit`. Run it under the
 * build lock:
 *
 *     KLANG_PROBE=audit console/with-build-lock.sh ./gradlew :sprudel:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.sprudel.audit.AuditSafetySprudelRepro
 *
 * The lines go to `<build dir>/probes/audit-safety-sprudel.txt` ([auditOutputFile]: the probes' `probeOutputFile` lives
 * in `audio_be`'s test sources, which this module cannot see, so the five lines that find the build directory are
 * repeated here).
 */
class AuditSafetySprudelRepro : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "audit"
    val report = ArrayList<String>()

    fun line(text: String) {
        println(text)
        report.add(text)
    }

    fun flush() {
        val target = auditOutputFile("audit-safety-sprudel.txt")

        target.parentFile?.mkdirs()
        target.writeText(report.joinToString(separator = "\n", postfix = "\n"))
        println("audit safety (sprudel) written to ${target.absolutePath}")
    }

    "N1: a pluck through lpf(16000, 200), analog 0 and 5, rendered end to end".config(enabled = requested) {
        line("== the render: KlangOfflineRenderer at 48 kHz, cps 0.5 (2 s per cycle)")

        // The rows of KLANG-00425's findings (the NaN pan and pitch, the duck) left with their fixes: their guards are
        // `WirePanSpec` here and the engine's specs named in that ticket. The N1 rows stay for their own ticket.
        val songs = listOf(
            Triple("N1: a pluck through lpf(16000, 200) with analog(0)", """note("a3").s("pluck").lpf(16000, 200).analog(0)""", 1),
            Triple("N1: a pluck through lpf(16000, 200) with analog(5)", """note("a3").s("pluck").lpf(16000, 200).analog(5)""", 1),
        )

        for ((label, code, cycles) in songs) {
            val pattern = SprudelPattern.compile(code)

            if (pattern == null) {
                line("RENDER [$label] did not compile")

                continue
            }

            val trace = Trace()
            val thrown = try {
                KlangOfflineRenderer(sampleRate = SAMPLE_RATE).render(
                    pattern = pattern,
                    cycles = cycles,
                    cyclesPerSecond = 0.5,
                    tailSec = 1.0,
                    onBlock = { out, frames -> trace.take(left = out.left, right = out.right, frames = frames) },
                )
                null
            } catch (t: Throwable) {
                "${t.javaClass.simpleName}: ${t.message?.take(100)}"
            }

            line("RENDER [$label] ${trace.text()}${if (thrown != null) "; THREW $thrown" else ""}")
            line("RENDER [$label] RMS per 0.5 s: ${trace.windowRms(0.5)}")
        }

        flush()
    }
})

private const val SAMPLE_RATE: Int = 48_000

private fun fmt(x: Double): String = String.format(Locale.ROOT, "%.3f", x)

/** `$KLANG_PROBE_OUT`'s folder or `<build dir>/probes/`, found as `probeOutputFile` (audio_be's probes) finds it. */
private fun auditOutputFile(fileName: String): File {
    System.getenv("KLANG_PROBE_OUT")?.let { return File(File(it).parentFile, fileName) }

    val classes = File(AuditSafetySprudelRepro::class.java.protectionDomain.codeSource.location.toURI())
    val buildDir = classes.parentFile?.parentFile?.parentFile?.parentFile ?: File(System.getProperty("java.io.tmpdir"))

    return File(buildDir, "probes/$fileName")
}

/** The rendered output, kept whole: exact-zero runs, non-finite samples, RMS per window. */
private class Trace {
    private val left = ArrayList<Double>()
    private val zero = ArrayList<Boolean>()
    var nonFinite = 0
    val frames: Int get() = left.size

    fun take(left: DoubleArray, right: DoubleArray, frames: Int) {
        for (i in 0 until frames) {
            val l = left[i]
            val r = right[i]

            this.left.add(l)
            zero.add(l == 0.0 && r == 0.0)

            if (!l.isFinite() || !r.isFinite()) {
                nonFinite++
            }
        }
    }

    fun text(): String {
        val from = SAMPLE_RATE / 10
        var longestStart = -1
        var longestLength = 0
        var start = -1
        var zeros = 0

        for (i in from..zero.size) {
            val z = i < zero.size && zero[i]

            if (z) {
                zeros++
            }

            if (z && start < 0) {
                start = i
            }

            if (!z && start >= 0) {
                if (i - start > longestLength) {
                    longestLength = i - start
                    longestStart = start
                }

                start = -1
            }
        }

        val run = if (longestStart < 0) {
            "none"
        } else {
            "${fmt(longestStart / SAMPLE_RATE.toDouble())} s .. ${fmt((longestStart + longestLength) / SAMPLE_RATE.toDouble())} s (${fmt(longestLength / SAMPLE_RATE.toDouble())} s)"
        }

        return "${fmt(frames / SAMPLE_RATE.toDouble())} s rendered, exact-zero frames from 0.1 s: $zeros, longest exact-zero run $run, non-finite out $nonFinite"
    }

    fun windowRms(seconds: Double): String {
        val window = (seconds * SAMPLE_RATE).toInt()
        val parts = ArrayList<String>()
        var at = 0

        while (at + window <= left.size) {
            var sum = 0.0

            for (i in at until at + window) {
                sum += left[i] * left[i]
            }

            parts.add(String.format(Locale.ROOT, "%.3f", sqrt(sum / window)))
            at += window
        }

        return parts.joinToString(" ")
    }
}
