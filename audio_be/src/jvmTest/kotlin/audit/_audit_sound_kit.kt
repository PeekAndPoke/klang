/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.probes.FRAMING_NONE
import io.peekandpoke.klang.audio_be.probes.FramedOutput
import io.peekandpoke.klang.audio_be.probes.FramingNote
import io.peekandpoke.klang.audio_be.probes.PROBE_BLOCK_FRAMES
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.blackmanHarris7
import io.peekandpoke.klang.audio_be.probes.probeOutputFile
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_be.probes.windowedPowerSpectrum
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.toKlangMap
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// The measuring kit of the disproof pass's "sound" cluster (KLANG-00420, docs/audio-audit/2026-11-10/audit/
// DISPROOF-BRIEF.md). Test code only: a measurement, never a gate. Everything that can be reused from the probes is
// imported from them (the FFT, the window, the voice renderer, the output file); what is here is what the probes do not
// have: the aliasing meter's sum split by band. The pitch estimate for a decaying tone by two independent methods moved to
// the normal test sources when the pluck was tuned (KLANG-00426): `PitchMeter` in `audio_be/src/jvmTest/kotlin/ignitor/`.

internal object AuditSoundKit {

    /** True when the environment asks for the audit reproductions (`KLANG_PROBE=audit`). */
    val requested: Boolean = System.getenv("KLANG_PROBE") == "audit"

    /** Two decimals (or [digits]), dot as the separator. */
    fun f(x: Double, digits: Int = 2): String = String.format(Locale.ROOT, "%.${digits}f", x)

    /** A level ratio in dB, `20 log10`, with -Inf for 0. */
    fun db(ratio: Double): String = if (ratio > 0.0) f(20.0 * log10(ratio), 1) else "-inf"

    /** A power ratio in dB, `10 log10`, with -Inf for 0. */
    fun dbPower(ratio: Double): String = if (ratio > 0.0) f(10.0 * log10(ratio), 1) else "-inf"

    /**
     * One report file in `<build dir>/probes/`: created (and truncated) on the first line written, so a disabled spec
     * writes nothing. Every line is printed too.
     */
    class Report(private val fileName: String, private val title: String) {
        private val file: File by lazy {
            probeOutputFile(fileName).also {
                it.parentFile?.mkdirs()
                it.writeText("$title\n")
                println("audit report: ${it.absolutePath}")
            }
        }

        fun line(text: String = "") {
            println(text)
            file.appendText(text + "\n")
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * One voice of the registered sound [sound] at [freqHz] through the production path (the probes'
     * [renderFramedVoice]: a fresh registry with the built-ins, the real `VoiceFactory` and `Voice.render`), with the
     * note slots [params], its onset at [onsetFrame], its gate [gateFrames] long, at least [frames] rendered. The
     * cylinder mix as the voice left it (both channels).
     */
    fun renderVoice(
        sound: String,
        freqHz: Double,
        params: Map<String, Double>,
        frames: Int,
        gateFrames: Int,
        onsetFrame: Int = 0,
    ): FramedOutput = renderFramedVoice(
        note = FramingNote(onsetFrame = onsetFrame, gateFrames = gateFrames, cutFrame = FRAMING_NONE, renderFrames = frames),
        data = VoiceData.empty.copy(sound = sound, freqHz = freqHz, ignitorParams = params.toKlangMap()),
    )

    /**
     * A bare Ignitor tree through the production build ([buildExciter]) at [freqHz] with the note slots [params] and a
     * voice stream seeded [seed], rendered in whole blocks from frame 0 with the gate at [gateFrames] (the probes'
     * `ignitorProbeStage`, with a seed of the caller's choice and silence as the only input).
     */
    fun renderTree(
        tree: IgnitorDsl,
        freqHz: Double,
        params: Map<String, Double>,
        frames: Int,
        gateFrames: Int = frames,
        seed: Int = 7,
        sampleRate: Int = PROBE_SAMPLE_RATE,
    ): DoubleArray {
        val blockFrames = PROBE_BLOCK_FRAMES
        val random = Random(seed)
        val built = tree.buildExciter(
            ignitorParams = params.toKlangMap(),
            random = random,
            freqHz = freqHz,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = random,
        )
        val total = wholeProbeBlocks(frames)
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        while (at < total) {
            ctx.voiceElapsedFrames = at
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            built.ignitor.generate(buffer, freqHz, ctx)
            buffer.copyInto(destination = out, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            at += blockFrames
        }

        return out
    }

    // ── The aliasing meter's sum, split by where the energy lands ──────────────────────────────────────────────

    /** Non-harmonic energy re the fundamental, by band, in dB (power). [totalDb] is the meter's own `nonHarmonicDb`. */
    class BandSplit(
        val fundamentalHz: Double,
        val totalDb: Double,
        val below20kDb: Double,
        val below15kDb: Double,
        val below5kDb: Double,
        val belowFundamentalDb: Double,
        val above20kDb: Double,
        val nonHarmonicReHarmonicsDb: Double,
    )

    /**
     * The aliasing meter's reading of [samples] (`measureAliasing`'s method exactly: one 65536-frame 7-term
     * Blackman-Harris window after [settleFrames], bands of 8 bins round every harmonic below Nyquist and round DC,
     * the fundamental searched within 2 percent when [findPitch], parabola-refined), with the non-harmonic power split
     * by frequency. A spec checks [BandSplit.totalDb] against the meter's own number, so the split cannot drift from it.
     */
    fun bandSplit(
        samples: DoubleArray,
        nominalHz: Double,
        findPitch: Boolean = true,
        settleFrames: Int = PROBE_SAMPLE_RATE / 2,
        fftSize: Int = 65536,
        sampleRate: Int = PROBE_SAMPLE_RATE,
    ): BandSplit {
        val n = fftSize
        val window = blackmanHarris7(n)
        var sumSquares = 0.0

        for (w in window) {
            sumSquares += w * w
        }

        val power = windowedPowerSpectrum(samples = samples, from = settleFrames, window = window)
        val half = n / 2
        val norm = n * sumSquares
        val meanSquare = DoubleArray(half + 1) { k -> power[k] * (if (k == 0 || k == half) 1.0 else 2.0) / norm }
        val binHz = sampleRate.toDouble() / n
        val nyquist = sampleRate / 2.0
        val band = 8
        val nominalBin = nominalHz / binHz
        val reach = max(3.0, 0.02 * nominalBin)
        val lo = max(1, ceil(nominalBin - reach).toInt())
        val hi = min(half - 1, floor(nominalBin + reach).toInt())
        var peakBin = lo

        for (k in lo..hi) {
            if (power[k] > power[peakBin]) {
                peakBin = k
            }
        }

        val fundamentalHz = if (findPitch) {
            val a = ln(power[peakBin - 1])
            val b = ln(power[peakBin])
            val c = ln(power[peakBin + 1])
            val den = a - 2.0 * b + c

            (if (den < 0.0) peakBin + 0.5 * (a - c) / den else peakBin.toDouble()) * binHz
        } else {
            nominalHz
        }

        // 0 other, 1 DC, 2 fundamental, 3 harmonic
        val kind = IntArray(half + 1)

        for (k in 0..min(band, half)) {
            kind[k] = 1
        }

        var h = 1

        while (h * fundamentalHz < nyquist) {
            val centre = h * fundamentalHz / binHz
            val from = max(0, ceil(centre - band).toInt())
            val to = min(half, floor(centre + band).toInt())

            for (k in from..to) {
                kind[k] = if (h == 1) 2 else 3
            }

            h++
        }

        var fundamental = 0.0
        var harmonic = 0.0
        var total = 0.0
        var below20k = 0.0
        var below15k = 0.0
        var below5k = 0.0
        var belowF = 0.0
        var above20k = 0.0

        for (k in 0..half) {
            val hz = k * binHz

            when (kind[k]) {
                2 -> fundamental += meanSquare[k]
                3 -> harmonic += meanSquare[k]
                1 -> {}
                else -> {
                    total += meanSquare[k]

                    if (hz < 20000.0) {
                        below20k += meanSquare[k]
                    } else {
                        above20k += meanSquare[k]
                    }

                    if (hz < 15000.0) {
                        below15k += meanSquare[k]
                    }

                    if (hz < 5000.0) {
                        below5k += meanSquare[k]
                    }

                    if (hz < fundamentalHz) {
                        belowF += meanSquare[k]
                    }
                }
            }
        }

        fun rel(p: Double): Double = 10.0 * log10(p / fundamental)

        return BandSplit(
            fundamentalHz = fundamentalHz,
            totalDb = rel(total),
            below20kDb = rel(below20k),
            below15kDb = rel(below15k),
            below5kDb = rel(below5k),
            belowFundamentalDb = rel(belowF),
            above20kDb = rel(above20k),
            nonHarmonicReHarmonicsDb = 10.0 * log10(total / (fundamental + harmonic)),
        )
    }

    // ── Small helpers ────────────────────────────────────────────────────────────────────────────────────────────

    /** RMS of [x] over `[from, to)`. */
    fun rms(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var sum = 0.0

        for (i in from until to) {
            sum += x[i] * x[i]
        }

        return if (to > from) sqrt(sum / (to - from)) else 0.0
    }

    /** Mean of [x] over `[from, to)`. */
    fun mean(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var sum = 0.0

        for (i in from until to) {
            sum += x[i]
        }

        return if (to > from) sum / (to - from) else 0.0
    }

    /** Largest |x| over `[from, to)`. */
    fun peak(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var p = 0.0

        for (i in from until to) {
            p = max(p, abs(x[i]))
        }

        return p
    }
}
