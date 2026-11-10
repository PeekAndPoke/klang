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
import io.peekandpoke.klang.audio_be.probes.PROBE_BLOCK_FRAMES
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.heldSilence
import io.peekandpoke.klang.audio_be.probes.measureAliasing
import io.peekandpoke.klang.audio_be.probes.probeSine
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_be.probes.sampleVoiceProbeStage
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * The disproof pass, cluster "sound", the SOURCES (KLANG-00420, `docs/audio-audit/2026-11-10/audit/disproof-sound.md`):
 * what the engine's sources really play, measured in the engine. A measurement, not a gate: nothing here asserts an
 * engine value; each test prints what it measured and writes it to `<build dir>/probes/audit-sound.txt`.
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

    // ── L3-02: the saw and the square ─────────────────────────────────────────────────────────────────────────────

    /** The two-point PolyBLEP residual for a unit step at phase 0 (test code, the textbook form). */
    fun polyBlep(t: Double, dt: Double): Double = when {
        t < dt -> {
            val x = t / dt

            x + x - x * x - 1.0
        }

        t > 1.0 - dt -> {
            val x = (t - 1.0) / dt

            x * x + x + x + 1.0
        }

        else -> 0.0
    }

    /** A two-point PolyBLEP saw (rising, falling edge at the wrap), test code. */
    fun polyBlepSaw(hz: Double, frames: Int): DoubleArray {
        val dt = hz / sr
        var t = 0.0

        return DoubleArray(frames) {
            val v = 2.0 * t - 1.0 - polyBlep(t = t, dt = dt)

            t += dt

            if (t >= 1.0) {
                t -= 1.0
            }

            v
        }
    }

    /** A two-point PolyBLEP square, duty 0.5, test code. */
    fun polyBlepSquare(hz: Double, frames: Int): DoubleArray {
        val dt = hz / sr
        var t = 0.0

        return DoubleArray(frames) {
            var u = t + 0.5

            if (u >= 1.0) {
                u -= 1.0
            }

            val v = (if (t < 0.5) 1.0 else -1.0) + polyBlep(t = t, dt = dt) - polyBlep(t = u, dt = dt)

            t += dt

            if (t >= 1.0) {
                t -= 1.0
            }

            v
        }
    }

    fun splitRow(label: String, pitch: Double, s: AuditSoundKit.BandSplit): String =
        "${label.padEnd(34)} ${f(pitch, 1).padStart(7)}  total ${f(s.totalDb, 1).padStart(6)}  <20k ${f(s.below20kDb, 1).padStart(6)}  " +
            "<15k ${f(s.below15kDb, 1).padStart(6)}  <5k ${f(s.below5kDb, 1).padStart(6)}  <f0 ${f(s.belowFundamentalDb, 1).padStart(6)}  " +
            ">20k ${f(s.above20kDb, 1).padStart(6)}"

    "L3-02: saw and square, non-harmonic energy by band against a two-point PolyBLEP, and the flank under upward pitch modulation (KLANG_PROBE=audit)".config(enabled = requested) {
        val sources = builtInSources()
        val frames = wholeProbeBlocks(sr / 2 + 65536)
        val analog0 = mapOf("analog" to 0.0)

        report.line()
        report.line("== L3-02 saw and square: non-harmonic energy re the fundamental, dB, by where it lands (the aliasing meter's method and window) ==")
        report.line("engine rows: the bare built-in source, analog 0; PolyBLEP rows: test code, phase from 0; 'meter' = measureAliasing's own total, the split's check")

        for (pitch in listOf(110.0, 1244.5, 2637.0, 4186.0, 7044.4)) {
            val saw = AuditSoundKit.renderTree(tree = sources.getValue("saw"), freqHz = pitch, params = analog0, frames = frames)
            val meter = measureAliasing(samples = saw, nominalHz = pitch)

            report.line(splitRow("engine saw (1-sample flank)", pitch, AuditSoundKit.bandSplit(saw, pitch)) + "   meter ${f(meter.nonHarmonicDb, 1)}")

            val zaw = AuditSoundKit.renderTree(tree = sources.getValue("zawtooth"), freqHz = pitch, params = analog0, frames = frames)

            report.line(splitRow("engine zawtooth (naive)", pitch, AuditSoundKit.bandSplit(zaw, pitch)))
            report.line(splitRow("test PolyBLEP saw (2-point)", pitch, AuditSoundKit.bandSplit(polyBlepSaw(pitch, frames), pitch)))

            val square = AuditSoundKit.renderTree(tree = sources.getValue("square"), freqHz = pitch, params = analog0, frames = frames)

            report.line(splitRow("engine square (2-sample flank)", pitch, AuditSoundKit.bandSplit(square, pitch)))
            report.line(splitRow("test PolyBLEP square (2-point)", pitch, AuditSoundKit.bandSplit(polyBlepSquare(pitch, frames), pitch)))
        }

        report.line()
        report.line("-- the flank is sized from the UNMODULATED increment: a saw pitched up by a pitch stage against the same saw played at that pitch --")

        val bareSaw = IgnitorDsl.Saw(analog = IgnitorDsl.Constant(0.0))

        for ((out, semis) in listOf(1244.5 to 12.0, 2637.0 to 12.0, 4186.0 to 12.0, 4186.0 to 24.0, 7044.4 to 24.0)) {
            val base = out / 2.0.pow(semis / 12.0)
            val direct = AuditSoundKit.renderTree(tree = bareSaw, freqHz = out, params = emptyMap(), frames = frames)
            val modded = AuditSoundKit.renderTree(
                tree = IgnitorDsl.PitchModSemitones(inner = bareSaw, mod = IgnitorDsl.Constant(semis)),
                freqHz = base,
                params = emptyMap(),
                frames = frames,
            )
            val accelerated = AuditSoundKit.renderTree(
                tree = IgnitorDsl.Accelerate(inner = bareSaw, semitones = IgnitorDsl.Constant(semis)),
                freqHz = base,
                params = emptyMap(),
                frames = frames,
                gateFrames = sr / 10,
            )

            report.line(splitRow("saw played at the pitch", out, AuditSoundKit.bandSplit(direct, out)))
            report.line(splitRow("saw +${semis.toInt()} st by pitchModSemitones", out, AuditSoundKit.bandSplit(modded, out)))
            report.line(splitRow("saw +${semis.toInt()} st by accelerate (held)", out, AuditSoundKit.bandSplit(accelerated, out)))
        }
    }

    // ── L3-03 and L3-04: the impulse ──────────────────────────────────────────────────────────────────────────────

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

    "L3-03 and L3-04: the impulse's aliasing against a triangle kernel, and a backward increment (KLANG_PROBE=audit)".config(enabled = requested) {
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

        report.line()
        report.line("== L3-04 the impulse under a backward increment: samples at 1.0 in 0.1 s (a forward impulse at 220 Hz spikes 22 times) ==")

        val tenth = sr / 10
        val bareImpulse = IgnitorDsl.Impulse(analog = IgnitorDsl.Constant(0.0))

        fun ones(x: DoubleArray, from: Int, to: Int): Int = (from until to).count { x[it] != 0.0 }

        for (mod in listOf(0.5, -0.5, -2.0)) {
            val out = AuditSoundKit.renderTree(tree = IgnitorDsl.PitchMod(inner = bareImpulse, mod = IgnitorDsl.Constant(mod)), freqHz = 220.0, params = emptyMap(), frames = tenth)

            report.line("pitchMod(mod = $mod, ratio ${1.0 + mod}): ${ones(x = out, from = 0, to = tenth)} nonzero samples of $tenth")
        }

        val fm = AuditSoundKit.renderVoice(
            sound = "impulse",
            freqHz = 220.0,
            params = mapOf("analog" to 0.0, "adsr.on" to 0.0, "fm.ratio" to 1.0, "fm.depth" to 440.0),
            frames = sr,
            gateFrames = sr,
        ).left
        val fmPlain = AuditSoundKit.renderVoice(
            sound = "impulse",
            freqHz = 220.0,
            params = mapOf("analog" to 0.0, "adsr.on" to 0.0, "fm.ratio" to 1.0, "fm.depth" to 110.0),
            frames = sr,
            gateFrames = sr,
        ).left

        report.line(
            "voice 'impulse' at 220 Hz, fm ratio 1: depth 440 Hz (ratio 1 + 2 sin, negative a third of the time) ${ones(x = fm, from = tenth, to = 2 * tenth)} " +
                "nonzero samples in 0.1 s; depth 110 Hz (never negative) ${ones(x = fmPlain, from = tenth, to = 2 * tenth)}",
        )
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

    "L3-05: sample playback, the engine's two-point linear read against four-point kernels, and a file off the engine's rate (KLANG_PROBE=audit)".config(enabled = requested) {
        val frames = wholeProbeBlocks(sr / 2 + 65536)

        report.line()
        report.line("== L3-05 sample playback: non-harmonic energy re the tone, dB (findPitch off for the rate-conversion rows) and the tone's level ==")

        val semitone = 2.0.pow(1.0 / 12.0)

        fun row(label: String, x: DoubleArray, nominal: Double, findPitch: Boolean) {
            val s = AuditSoundKit.bandSplit(samples = x, nominalHz = nominal, findPitch = findPitch)
            val level = measureAliasing(samples = x, nominalHz = nominal, findPitch = findPitch).fundamentalDbfs

            report.line("${label.padEnd(58)} total ${f(s.totalDb, 1).padStart(6)}  <20k ${f(s.below20kDb, 1).padStart(6)}  <5k ${f(s.below5kDb, 1).padStart(6)}   tone ${f(level, 2)} dBFS (source -6.02)")
        }

        for (pitch in listOf(1244.5, 4186.0, 7044.4)) {
            val pcm = probeSine(hz = pitch / semitone, amplitude = 0.5, frames = frames * 2)
            val engine = sampleVoiceProbeStage(tree = IgnitorDsl.Sample, pcm = pcm, rate = semitone, freqHz = pitch).render(heldSilence(frames)).left

            row("${f(pitch, 1)} Hz, a semitone up: engine (linear)", engine, pitch, findPitch = true)
            row("${f(pitch, 1)} Hz, a semitone up: test 4-point Hermite", resample4(pcm, semitone, frames, hermite = true), pitch, findPitch = true)
            row("${f(pitch, 1)} Hz, a semitone up: test 4-point Lagrange", resample4(pcm, semitone, frames, hermite = false), pitch, findPitch = true)
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

    // ── L3-14: the sample's last frame, and a loop over the whole sample ─────────────────────────────────────────

    "L3-14: the playhead's last frame and a whole-sample loop through the sample voice (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L3-14 the sample voice through VoiceFactory, adsr off, rate 1: an 8-frame PCM ==")

        fun play(pcm: DoubleArray, loop: Boolean): DoubleArray = renderFramedVoice(
            note = FramingNote(onsetFrame = 0, gateFrames = 4 * PROBE_BLOCK_FRAMES, cutFrame = FRAMING_NONE, renderFrames = 4 * PROBE_BLOCK_FRAMES),
            data = VoiceData.empty.copy(
                sound = "auditloopsample",
                freqHz = 220.0,
                ignitorParams = (mapOf("analog" to 0.0, "adsr.on" to 0.0) + (if (loop) mapOf("loop" to 1.0) else emptyMap())).toKlangMap(),
            ),
            getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = 220.0, sample = MonoSamplePcm(sampleRate = sr, pcm = pcm)) },
        ).left

        val ramp = DoubleArray(8) { 0.1 * (it + 1) }
        val oneShot = play(ramp, loop = false)
        val scale = oneShot[0] / ramp[0]

        report.line("one shot, PCM 0.1 .. 0.8: frames 0..9 played (re the PCM scale) ${(0 until 10).joinToString(" ") { f(oneShot[it] / scale, 3) }}")

        val flat = DoubleArray(8) { 0.5 }
        val looped = play(flat, loop = true)
        val zeros = (0 until 64).filter { looped[it] == 0.0 }

        report.line("loop(1), constant PCM 0.5: zero samples among the first 64 frames at ${zeros.joinToString(" ")} (${zeros.size} of 64)")
    }
})
