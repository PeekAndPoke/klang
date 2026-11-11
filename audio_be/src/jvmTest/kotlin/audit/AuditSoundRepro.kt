/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.audit.AuditSoundKit.f
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_be.probes.FRAMING_NONE
import io.peekandpoke.klang.audio_be.probes.FramingNote
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.heldSilence
import io.peekandpoke.klang.audio_be.probes.measureAliasing
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_be.probes.sampleVoiceProbeStage
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * The disproof pass, cluster "sound", the SOURCES (KLANG-00420, `docs/audio-audit/2026-11-10/audit/disproof-sound.md`):
 * what the engine's sources really play, measured in the engine. A measurement, not a gate: nothing here asserts an
 * engine value; each test prints what it measured and writes it to `<build dir>/probes/audit-sound.txt`. The fixed
 * findings' rows left with their fixes (L3-01 and L3-06 in KLANG-00426; L3-02, L3-04, L3-05's transpositions and L3-14
 * in KLANG-00430, each now an ordinary spec); what stays is open: L3-03 (accepted) and the rate conversion of L3-05 and
 * L9-07 (KLANG-00430, "Decide").
 *
 * **Out of the normal run**: enabled only with `KLANG_PROBE=audit`.
 *
 *     KLANG_PROBE=audit console/with-build-lock.sh ./gradlew :audio_be:jvmTest \
 *         --tests io.peekandpoke.klang.audio_be.audit.AuditSoundRepro
 */
class AuditSoundRepro : StringSpec({

    val requested = AuditSoundKit.requested
    val report = AuditSoundKit.Report(fileName = "audit-sound.txt", title = "Disproof pass, cluster sound, sources (KLANG-00420)")
    val sr = PROBE_SAMPLE_RATE

    // ── L3-03: the impulse ──────────────────────────────────────────────────────────────────────────────

    /**
     * An impulse train at [hz] in test code: [kernel] 0 is the engine's rule (1.0 on the first sample after the wrap),
     * 1 splits the unit spike over the two samples around the true crossing by its fraction (a triangle kernel).
     */
    fun testImpulse(hz: Double, frames: Int, kernel: Int): DoubleArray {
        val dt = hz / sr
        val out = DoubleArray(frames)
        var p = 0.0

        for (n in 1 until frames) {
            p += dt

            if (p >= 1.0) {
                p -= 1.0

                // The crossing lies `p / dt` samples before frame n.
                val past = p / dt

                if (kernel == 0) {
                    out[n] += 1.0
                } else {
                    out[n] += 1.0 - past
                    out[n - 1] += past
                }
            }
        }

        return out
    }

    "L3-03: the impulse's aliasing against a triangle kernel (KLANG_PROBE=audit)".config(enabled = requested) {
        val sources = builtInSources()
        val frames = wholeProbeBlocks(sr / 2 + 65536)

        report.line()
        report.line("== L3-03 the impulse: non-harmonic energy re ALL harmonics (the meter's nonh_reH), dB ==")

        for (pitch in listOf(110.0, 1244.5, 4186.0, 7044.4)) {
            val engine = AuditSoundKit.renderTree(tree = sources.getValue("impulse"), freqHz = pitch, params = mapOf("analog" to 0.0), frames = frames)
            val replica = testImpulse(pitch, frames, kernel = 0)
            val triangle = testImpulse(pitch, frames, kernel = 1)

            report.line(
                "pitch ${f(pitch, 1).padStart(7)}  engine ${f(AuditSoundKit.bandSplit(engine, pitch).nonHarmonicReHarmonicsDb, 1)}  " +
                    "test replica of the engine rule ${f(AuditSoundKit.bandSplit(replica, pitch).nonHarmonicReHarmonicsDb, 1)}  " +
                    "test triangle kernel ${f(AuditSoundKit.bandSplit(triangle, pitch).nonHarmonicReHarmonicsDb, 1)}  " +
                    "(triangle kernel below 20 kHz ${f(AuditSoundKit.bandSplit(triangle, pitch).below20kDb, 1)} re f0)",
            )
        }

    }

    // ── L3-05: sample playback ────────────────────────────────────────────────────────────────────────────────────

    /** [pcm] read at [rate] by a four-point kernel (test code): [hermite] the Catmull-Rom cubic, else the cubic Lagrange. */
    fun resample4(pcm: DoubleArray, rate: Double, frames: Int, hermite: Boolean): DoubleArray {
        var ph = 0.0

        return DoubleArray(frames) {
            val base = floor(ph).toInt()
            val t = ph - base
            val xm1 = if (base - 1 >= 0) pcm[base - 1] else 0.0
            val x0 = pcm[base]
            val x1 = if (base + 1 < pcm.size) pcm[base + 1] else 0.0
            val x2 = if (base + 2 < pcm.size) pcm[base + 2] else 0.0
            val v = if (hermite) {
                val c1 = 0.5 * (x1 - xm1)
                val c2 = xm1 - 2.5 * x0 + 2.0 * x1 - 0.5 * x2
                val c3 = 0.5 * (x2 - xm1) + 1.5 * (x0 - x1)

                ((c3 * t + c2) * t + c1) * t + x0
            } else {
                xm1 * (-t * (t - 1.0) * (t - 2.0) / 6.0) + x0 * ((t + 1.0) * (t - 1.0) * (t - 2.0) / 2.0) +
                    x1 * (-(t + 1.0) * t * (t - 2.0) / 2.0) + x2 * ((t + 1.0) * t * (t - 1.0) / 6.0)
            }

            ph += rate

            v
        }
    }

    "L3-05, L9-07: a file off the engine's rate through the playhead's read (KLANG_PROBE=audit)".config(enabled = requested) {
        val frames = wholeProbeBlocks(sr / 2 + 65536)

        report.line()
        report.line("== L3-05 sample playback: non-harmonic energy re the tone, dB (findPitch off for the rate-conversion rows) and the tone's level ==")

        fun row(label: String, x: DoubleArray, nominal: Double, findPitch: Boolean) {
            val s = AuditSoundKit.bandSplit(samples = x, nominalHz = nominal, findPitch = findPitch)
            val level = measureAliasing(samples = x, nominalHz = nominal, findPitch = findPitch).fundamentalDbfs

            report.line("${label.padEnd(58)} total ${f(s.totalDb, 1).padStart(6)}  <20k ${f(s.below20kDb, 1).padStart(6)}  <5k ${f(s.below5kDb, 1).padStart(6)}   tone ${f(level, 2)} dBFS (source -6.02)")
        }

        for ((fileRate, tones) in listOf(44100 to listOf(5000.0, 10000.0, 15000.0), 22050 to listOf(2000.0, 5000.0, 9000.0))) {
            val rate = fileRate.toDouble() / sr

            for (tone in tones) {
                val pcm = DoubleArray(frames * 2) { 0.5 * sin(2.0 * PI * tone * it / fileRate) }
                val engine = sampleVoiceProbeStage(tree = IgnitorDsl.Sample, pcm = pcm, rate = rate, freqHz = 220.0).render(heldSilence(frames)).left

                row("$fileRate Hz file, ${f(tone, 0)} Hz tone, pitch ratio 1: engine", engine, tone, findPitch = false)
                row("$fileRate Hz file, ${f(tone, 0)} Hz tone, pitch ratio 1: test Hermite", resample4(pcm, rate, frames, hermite = true), tone, findPitch = false)
            }
        }

        // The production path folds the file's own rate into the playhead's (VoiceFactory): the same 10 kHz row through it.
        val pcm44 = DoubleArray(frames * 2) { 0.5 * sin(2.0 * PI * 10000.0 * it / 44100) }
        val voice = renderFramedVoice(
            note = FramingNote(onsetFrame = 0, gateFrames = frames, cutFrame = FRAMING_NONE, renderFrames = frames),
            data = VoiceData.empty.copy(sound = "auditsample44k", freqHz = 220.0, ignitorParams = mapOf("analog" to 0.0, "adsr.on" to 0.0).toKlangMap()),
            getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = 220.0, sample = MonoSamplePcm(sampleRate = 44100, pcm = pcm44)) },
        ).left

        row("44100 Hz file, 10000 Hz tone, through VoiceFactory (sample voice)", voice, 10000.0, findPitch = false)
    }
})
