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

    "L1-01 L6-02 L1-02 the key question: what a sprudel pattern puts on the wire as pan and freqHz".config(enabled = requested) {
        line("== the wire: SprudelPattern.compile(code), cycle 0 queried at cps 0.5, each event through toVoiceData()")

        val cases = listOf(
            // pan
            """note("c3").pan("NaN")""",
            """note("c3").pan("Infinity")""",
            """note("c3").pan("nan")""",
            """note("c3").pan(Math.sqrt(-1))""",
            """note("c3").pan((-1) ** 0.5)""",
            """note("c3").pan("0").pan(div(0))""",
            """note("c3").pan("0 0.5".div(0))""",
            """note("c3").pan("0").pan(pow(-1)).pan(mul(0))""",
            """note("c3").pan("-1").pan(pow(0.5))""",
            """note("c3").pan("-1").pan(log2())""",
            """note("c3").pan(sine.range(-1, 0).pow(0.5))""",
            """note("c3").pan(0/0)""",
            // freq
            """s("bd").freq("NaN")""",
            """s("bd").freq(0)""",
            """s("bd").freq(Math.sqrt(-1))""",
            """note("NaN")""",
            """note("-Infinity")""",
            """note("Infinity")""",
            """note(-2000)""",
            """note("c3").freq(mul(0))""",
            """note("c3").freq(div(0))""",
            """n("NaN").scale("c3:major")""",
        )

        for (code in cases) {
            val pattern = try {
                SprudelPattern.compile(code)
            } catch (t: Throwable) {
                line("WIRE [$code] did not compile: ${t.javaClass.simpleName}: ${t.message?.take(80)}")

                continue
            }

            if (pattern == null) {
                line("WIRE [$code] compiled to no pattern")

                continue
            }

            val wire = pattern.queryEvents(fromCycles = 0.0, toCycles = 1.0, cps = 0.5).map { it.toVoiceData() }

            line("WIRE [$code] ${wire.size} events: pan ${wire.map { it.pan }.distinct()}, freqHz ${wire.map { it.freqHz }.distinct()}, sound ${wire.map { it.sound }.distinct()}")
        }

        flush()
    }

    "L1-01 L6-02 L6-01 L7b-01: the NaN pan rendered end to end, alone and on a duck's source cylinder".config(enabled = requested) {
        line("== the render: KlangOfflineRenderer at 48 kHz, cps 0.5 (2 s per cycle)")

        val songs = listOf(
            Triple("A: NaN pan alone", """note("c3").s("sine").pan("NaN")""", 1),
            Triple("A0: control, pan 0.5", """note("c3").s("sine").pan("0.5")""", 1),
            Triple(
                "C: one NaN kick (3.0 s) on the duck's source, the bass plays on to 8 s",
                """stack(note("c4*4").s("sine").cylinder(1).pan("<0.5 [0.5 0.5 NaN 0.5] 0.5 0.5>"), note("c2*8").s("saw").gain(0.5).duck(1, 0.8, 0.2))""",
                4,
            ),
            Triple(
                "C0: control, pan 0.5",
                """stack(note("c4*4").s("sine").cylinder(1).pan("0.5"), note("c2*8").s("saw").gain(0.5).duck(1, 0.8, 0.2))""",
                4,
            ),
            Triple(
                "E: the same, the bass stops at 4 s",
                """stack(note("c4*4").s("sine").cylinder(1).pan("<0.5 [0.5 0.5 NaN 0.5] 0.5 0.5>"), note("<[c2*8] [c2*8] ~ ~>").s("saw").gain(0.5).duck(1, 0.8, 0.2))""",
                4,
            ),
            Triple(
                "F: the same, the duck's depth falls to 0 at 4 s",
                """stack(note("c4*4").s("sine").cylinder(1).pan("<0.5 [0.5 0.5 NaN 0.5] 0.5 0.5>"), note("c2*8").s("saw").gain(0.5).duck(1, "<0.8 0.8 0 0>", 0.2))""",
                4,
            ),
            Triple("N1: a pluck through lpf(16000, 200) with analog(0)", """note("a3").s("pluck").lpf(16000, 200).analog(0)""", 1),
            Triple("N1: a pluck through lpf(16000, 200) with analog(5)", """note("a3").s("pluck").lpf(16000, 200).analog(5)""", 1),
            Triple("L1-02: a pluck at note NaN", """note("NaN").s("pluck")""", 1),
            Triple(
                "G: no NaN literal: pan 0, pow(-1), mul(0) on the kick at 3.0 s",
                """stack(note("c4*4").s("sine").cylinder(1).pan("<0.5 [0.5 0.5 0 0.5] 0.5 0.5>").pan(pow(-1)).pan(mul(0)), note("c2*8").s("saw").gain(0.5).duck(1, 0.8, 0.2))""",
                4,
            ),
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

    "L2-04 L8-03 L9-01: a gate before the onset from sprudel, rendered end to end".config(enabled = requested) {
        line("== the throw hunt from patterns: KlangOfflineRenderer, 2 cycles at cps 0.5")

        val songs = listOf(
            """note("c3*8").s("sine").legato(-0.001).adsr(release = 0)""",
            """note("c3*8").s("sine").freq(30000).legato(-0.001).adsr(release = 0)""",
            """note("NaN*8").s("sine").legato(-0.001).adsr(release = 0)""",
            """note("c3*8").s("supersaw").legato(-0.001).adsr(release = 0)""",
            """note("c3*8").sound(Ignitor.sine().mul(Ignitor.progress())).legato(-0.205)""",
            """note("c3*8").sound(Ignitor.sine().mul(Ignitor.progress())).legato(-0.001)""",
            """note("c3*8").s("sine").legato(-0.205)""",
            """note("c3*8").s("silence").legato(-0.001).adsr(release = 0)""",
            """note("c3*8").s("saw").pregain(0).legato(-0.001).adsr(release = 0)""",
            """note("c3*8").s("saw").pregain(0).legato(-0.001)""",
            """stack(note("c3*8").s("silence").legato(-0.001).adsr(release = 0), note("c4").s("sine"))""",
        )

        for (code in songs) {
            val pattern = SprudelPattern.compile(code)

            if (pattern == null) {
                line("HUNT [$code] did not compile")

                continue
            }

            val trace = Trace()
            val thrown = try {
                KlangOfflineRenderer(sampleRate = SAMPLE_RATE).render(
                    pattern = pattern,
                    cycles = 2,
                    cyclesPerSecond = 0.5,
                    tailSec = 0.5,
                    onBlock = { out, frames -> trace.take(left = out.left, right = out.right, frames = frames) },
                )
                null
            } catch (t: Throwable) {
                val site = t.stackTrace.firstOrNull { it.className.startsWith("io.peekandpoke.klang") && !it.className.contains(".audit.") }

                "${t.javaClass.simpleName}(${t.message?.take(100)}) at ${site?.className?.substringAfterLast('.')}.${site?.methodName}:${site?.lineNumber}"
            }

            line("HUNT [$code] rendered ${fmt(trace.frames / SAMPLE_RATE.toDouble())} s; ${thrown?.let { "THREW $it" } ?: "no throw"}")
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
