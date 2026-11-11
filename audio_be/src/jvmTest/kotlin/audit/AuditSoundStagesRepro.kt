/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.audit.AuditSoundKit.db
import io.peekandpoke.klang.audio_be.audit.AuditSoundKit.f
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.ProbeSignal
import io.peekandpoke.klang.audio_be.probes.fftInPlace
import io.peekandpoke.klang.audio_be.probes.katalystOwnerChangeStage
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The disproof pass, cluster "sound", the STAGES (KLANG-00420, `docs/audio-audit/2026-11-10/audit/disproof-sound.md`):
 * what is left open of it, the vowel (L7b-02). The rest was fixed and its reproductions deleted, their guards ordinary
 * specs now: the chain swaps, the phaser at a low rate, the reverb's lowpass and the compressor's detector by
 * KLANG-00428, the optimizer's filter gate by KLANG-00429, the filter envelope's block law (L4-02) by KLANG-00432. A
 * measurement, not a gate: nothing here asserts an engine value; each test prints what it measured and writes it to
 * `<build dir>/probes/audit-sound-stages.txt`.
 *
 * **Out of the normal run**: enabled only with `KLANG_PROBE=audit`.
 *
 *     KLANG_PROBE=audit console/with-build-lock.sh ./gradlew :audio_be:jvmTest \
 *         --tests io.peekandpoke.klang.audio_be.audit.AuditSoundStagesRepro
 */
class AuditSoundStagesRepro : StringSpec({

    val requested = AuditSoundKit.requested
    val report = AuditSoundKit.Report(fileName = "audit-sound-stages.txt", title = "Disproof pass, cluster sound, stages (KLANG-00420)")
    val sr = PROBE_SAMPLE_RATE

    /** 20 log10 of a ratio, as a number. */
    fun dbOf(ratio: Double): Double = if (ratio > 0.0) 20.0 * log10(ratio) else Double.NEGATIVE_INFINITY

    // ── L7b-02: the vowel ────────────────────────────────────────────────────────────────────────────────────────

    "L7b-02: the vowel's formant widths from its impulse response, and a saw's level by note (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L7b-02 the vowel stage (Katalyst classic chain, vowel slots), wet 1, floor 0 unless said ==")

        val settle = sr / 2
        val irFrames = 65536
        val total = wholeProbeBlocks(settle + irFrames)

        fun vowelMap(name: String, wet: Double, floor: Double): Map<String, Double> =
            mapOf("vowel.vowel" to VowelBands.indexOf(name), "vowel.wet" to wet, "vowel.floor" to floor)

        fun runVowel(signal: DoubleArray, map: Map<String, Double>): DoubleArray =
            katalystOwnerChangeStage(dsl = KatalystDsl.classic, before = map.toKlangMap(), after = map.toKlangMap(), eventFrame = 0)
                .render(ProbeSignal(name = "in", left = signal, right = signal.copyOf(), inputEndFrame = signal.size)).left

        for (name in listOf("bass:a", "soprano:a")) {
            val input = DoubleArray(total).also { it[settle] = 1.0 }
            val ir = runVowel(input, vowelMap(name, wet = 1.0, floor = 0.0))
            val padded = 4 * irFrames
            val re = DoubleArray(padded)
            val im = DoubleArray(padded)

            for (i in 0 until irFrames) {
                re[i] = ir[settle + i]
            }

            fftInPlace(re, im)

            val binHz = sr.toDouble() / padded

            fun mag(k: Int): Double = sqrt(re[k] * re[k] + im[k] * im[k])

            val bands = VowelBands.bandsFor(name) ?: emptyList()

            report.line("$name: freq, dB, q in the table; measured peak, its gain (dB), its -3 dB width; freq / q")

            for (b in bands) {
                val lo = ((b.freq * 0.97) / binHz).toInt()
                val hi = ((b.freq * 1.03) / binHz).toInt()
                val k = (lo..hi).maxBy { mag(it) }
                val half = mag(k) / sqrt(2.0)
                var left = k
                var right = k

                while (left > 1 && mag(left) > half) {
                    left--
                }

                while (right < padded / 2 - 1 && mag(right) > half) {
                    right++
                }

                // Linear interpolation of the two crossings.
                val l = left + (half - mag(left)) / (mag(left + 1) - mag(left))
                val r = right - 1 + (mag(right - 1) - half) / (mag(right - 1) - mag(right))

                report.line(
                    "  ${f(b.freq, 0).padStart(5)} Hz ${f(b.db, 0).padStart(4)} dB q ${f(b.q, 0).padStart(4)}:  peak at ${f(k * binHz, 1)} Hz, gain ${db(mag(k))} dB, " +
                        "-3 dB width ${f((r - l) * binHz, 1)} Hz;  freq / q ${f(b.freq / b.q, 1)} Hz",
                )
            }
        }

        // A band-limited saw (test code, additive, harmonics to 20 kHz, peak about 0.5) through bass:a, by note.
        val sawFrames = wholeProbeBlocks(sr)

        fun saw(hz: Double): DoubleArray = DoubleArray(sawFrames) { n ->
            var v = 0.0
            var h = 1

            while (h * hz < 20000.0) {
                v += sin(2.0 * PI * h * hz * n / sr) / h
                h++
            }

            0.3 * v
        }

        val notes = listOf(110.0, 116.54, 123.47, 130.81, 138.59, 146.83, 150.0, 155.56, 164.81, 174.61, 185.0, 196.0, 207.65, 220.0)

        for ((label, map) in listOf("wet 1 floor 0" to vowelMap("bass:a", 1.0, 0.0), "default wet .5 floor .2" to vowelMap("bass:a", 0.5, 0.2))) {
            val levels = notes.map { hz ->
                val x = saw(hz)
                val y = runVowel(x, map)

                dbOf(AuditSoundKit.rms(x = y, from = sr / 2, to = sawFrames) / AuditSoundKit.rms(x = x, from = sr / 2, to = sawFrames))
            }

            report.line(
                "bass:a, $label, a saw's level out re in by note (A2 to A3): " +
                    notes.indices.joinToString("  ") { "${f(notes[it], 1)}:${f(levels[it], 1)}" } +
                    "   swing ${f(levels.max() - levels.min(), 1)} dB (110 vs 150: ${f(levels[6] - levels[0], 1)} dB)",
            )
        }
    }
})
