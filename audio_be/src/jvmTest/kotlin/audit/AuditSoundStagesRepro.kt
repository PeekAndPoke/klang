/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.audit.AuditSoundKit.db
import io.peekandpoke.klang.audio_be.audit.AuditSoundKit.f
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.filters.filterEnvCutoff
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.probes.CLICK_EVENT_FRAME
import io.peekandpoke.klang.audio_be.probes.CLICK_INSTANTS
import io.peekandpoke.klang.audio_be.probes.CLICK_VOICE_START
import io.peekandpoke.klang.audio_be.probes.ClickConfig
import io.peekandpoke.klang.audio_be.probes.ClickRender
import io.peekandpoke.klang.audio_be.probes.FRAMING_NONE
import io.peekandpoke.klang.audio_be.probes.FramingNote
import io.peekandpoke.klang.audio_be.probes.PROBE_BLOCK_FRAMES
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.ProbeSignal
import io.peekandpoke.klang.audio_be.probes.blackmanHarris7
import io.peekandpoke.klang.audio_be.probes.clickTone
import io.peekandpoke.klang.audio_be.probes.fftInPlace
import io.peekandpoke.klang.audio_be.probes.ignitorProbeStage
import io.peekandpoke.klang.audio_be.probes.katalystOwnerChangeStage
import io.peekandpoke.klang.audio_be.probes.katalystProbeStage
import io.peekandpoke.klang.audio_be.probes.measureClick
import io.peekandpoke.klang.audio_be.probes.measureClickStereo
import io.peekandpoke.klang.audio_be.probes.measureStage
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_be.probes.windowedPowerSpectrum
import io.peekandpoke.klang.audio_be.utils.flushState
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.optimizer
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The disproof pass, cluster "sound", the STAGES (KLANG-00420, `docs/audio-audit/2026-11-10/audit/disproof-sound.md`):
 * the filter envelope's block law, the voice's last frame, the de-click's subnormals, the chain swaps, the vowel, the
 * reverb's damping, the compressor's detector, the phaser at a low rate and the optimizer's filter gate. A
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
    val block = PROBE_BLOCK_FRAMES

    /** 20 log10 of a ratio, as a number. */
    fun dbOf(ratio: Double): Double = if (ratio > 0.0) 20.0 * log10(ratio) else Double.NEGATIVE_INFINITY

    // ── L4-02: the filter envelope at a block's two ends ─────────────────────────────────────────────────────────

    /**
     * A bare tree rendered as `Voice.render` drives it with the onset at frame [offset] of the first block: the first
     * window is `[offset, 128)` at voice frame 0, every later one a whole block. The output in NOTE frames.
     */
    fun renderTreeAtOffset(tree: IgnitorDsl, freqHz: Double, offset: Int, noteFrames: Int, gateFrames: Int): DoubleArray {
        val random = Random(7)
        val built = tree.buildExciter(ignitorParams = null, random = random, freqHz = freqHz, sampleRate = sr, blockFrames = block)
        val ctx = IgniteContext(sampleRate = sr, voiceDurationFrames = gateFrames, gateEndFrame = gateFrames, scratchBuffers = ScratchBuffers(block), random = random)
        val buffer = AudioBuffer(block)
        val out = DoubleArray(noteFrames + block)
        var elapsed = 0
        var first = true

        while (elapsed < noteFrames) {
            val off = if (first) offset else 0
            val len = block - off

            ctx.voiceElapsedFrames = elapsed
            ctx.updateOffsetAndLength(offset = off, length = len)
            built.ignitor.generate(buffer, freqHz, ctx)

            for (i in 0 until len) {
                out[elapsed + i] = buffer[off + i]
            }

            elapsed += len
            first = false
        }

        return out.copyOf(noteFrames)
    }

    /**
     * The linear TPT lowpass of `SvfIgnitor` (its LOWPASS arm, `analog` 0) in TEST code, with the cutoff given at the
     * [knots] (note frames, ascending, the first 0, the last past the input's end) and the coefficients stepped linearly between two knots exactly as
     * `SvfCoeffSweep` and the loop step them (start set, `(end - start) * (1 / frames)`, an add after every sample).
     * Knots at every block border of an onset at offset k are today's law; a knot at every frame is the exact
     * per-sample evaluation; knots on a grid anchored to the note are the audit's proposed fix.
     */
    fun sweptLowpass(input: DoubleArray, knots: IntArray, cutoffAt: (Int) -> Double, q: Double): DoubleArray {
        val out = DoubleArray(input.size)
        val start = SvfCoeffs()
        val end = SvfCoeffs()
        var ic1eq = 0.0
        var ic2eq = 0.0

        for (j in 0 until knots.size - 1) {
            val from = knots[j]
            val to = knots[j + 1]

            if (from >= input.size) {
                break
            }

            val frames = to - from
            val c0 = cutoffAt(from)
            val c1 = cutoffAt(to)

            computeSvfCoeffs(cutoffHz = c0, q = q, sampleRate = sr.toDouble(), out = start)

            var a1 = start.a1
            var a2 = start.a2
            var a3 = start.a3
            var a1Step = 0.0
            var a2Step = 0.0
            var a3Step = 0.0

            if (frames > 0 && c1 != c0) {
                computeSvfCoeffs(cutoffHz = c1, q = q, sampleRate = sr.toDouble(), out = end)

                val inv = 1.0 / frames

                a1Step = (end.a1 - start.a1) * inv
                a2Step = (end.a2 - start.a2) * inv
                a3Step = (end.a3 - start.a3) * inv
            }

            for (i in from until min(to, input.size)) {
                val v0 = input[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3

                ic1eq = (2.0 * v1 - ic1eq).flushState()
                ic2eq = (2.0 * v2 - ic2eq).flushState()
                out[i] = v2
                a1 += a1Step
                a2 += a2Step
                a3 += a3Step
            }
        }

        return out
    }

    /** Max |a - b| re [peak] and RMS(a - b) re [rmsRef], in dB, over `[from, to)`. */
    fun diffDb(a: DoubleArray, b: DoubleArray, from: Int, to: Int, peak: Double, rmsRef: Double): Pair<Double, Double> {
        var m = 0.0
        var s = 0.0

        for (i in from until to) {
            val d = abs(a[i] - b[i])

            m = max(m, d)
            s += d * d
        }

        return dbOf(m / peak) to dbOf(sqrt(s / (to - from)) / rmsRef)
    }

    "L4-02 (b): two onsets one frame apart under a filter envelope, the classic voice (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L4-02 (b) the filter envelope: the same note at onsets k and k + 1 inside the block (k = 0..126), aligned by the onset ==")
        report.line("max = largest |difference| re the note's peak; rms = RMS of the difference re the note's RMS; 'attack' = the first 20 ms only")

        class Case(val label: String, val sound: String, val freq: Double, val params: Map<String, Double>, val gate: Int, val span: Int)

        val cases = listOf(
            Case(
                label = "saw 110 Hz, lpf 300 Hz, env 24 st, default times (a 0.01 d 0.1 s 1 r 0.1), default adsr, gate 0.3 s",
                sound = "saw", freq = 110.0,
                params = mapOf("analog" to 0.0, "lpf.freq" to 300.0, "lpf.env" to 24.0),
                gate = 3 * sr / 10, span = 3 * sr / 10 + 2400,
            ),
            Case(
                label = "IrishLamentTechno bass shape on a saw at 55 Hz: lpf 700, env 15.9, q 1.5, a .005 d .08 s .7 r .05; adsr .01 .2 .4 .05; gate .138 s",
                sound = "saw", freq = 55.0,
                params = mapOf(
                    "analog" to 0.0, "lpf.freq" to 700.0, "lpf.env" to 15.9, "lpf.q" to 1.5, "lpf.attack" to 0.005, "lpf.decay" to 0.08,
                    "lpf.sustain" to 0.7, "lpf.release" to 0.05, "adsr.attack" to 0.01, "adsr.decay" to 0.2, "adsr.sustain" to 0.4, "adsr.release" to 0.05,
                ),
                gate = (0.138 * sr).toInt(), span = (0.138 * sr).toInt() + 2400,
            ),
            Case(
                label = "Tetris sub shape on a tri at 82.4 Hz: lpf 200, env 56.4, q 1, a .01 d .1 s 0 r .09; adsr .01 .2 .4 .09; gate .3 s (the song adds analog 2.5)",
                sound = "tri", freq = 82.41,
                params = mapOf(
                    "analog" to 0.0, "lpf.freq" to 200.0, "lpf.env" to 56.4, "lpf.q" to 1.0, "lpf.attack" to 0.01, "lpf.decay" to 0.1,
                    "lpf.sustain" to 0.0, "lpf.release" to 0.09, "adsr.attack" to 0.01, "adsr.decay" to 0.2, "adsr.sustain" to 0.4, "adsr.release" to 0.09,
                ),
                gate = 3 * sr / 10, span = 3 * sr / 10 + 4800,
            ),
            Case(
                label = "TetrisRemix sub shape on a tri at 82.4 Hz: lpf 300, env 48, a .005 d 1 s 0 r .2; default adsr; gate .3 s",
                sound = "tri", freq = 82.41,
                params = mapOf(
                    "analog" to 0.0, "lpf.freq" to 300.0, "lpf.env" to 48.0, "lpf.attack" to 0.005, "lpf.decay" to 1.0,
                    "lpf.sustain" to 0.0, "lpf.release" to 0.2,
                ),
                gate = 3 * sr / 10, span = 3 * sr / 10 + 2400,
            ),
        )

        for (case in cases) {
            val renders = (0 until block).map { k ->
                val out = AuditSoundKit.renderVoice(sound = case.sound, freqHz = case.freq, params = case.params, frames = case.span + 2 * block, gateFrames = case.gate, onsetFrame = k).left

                DoubleArray(case.span) { out[k + it] }
            }
            val peak = AuditSoundKit.peak(renders[0])
            val rmsRef = AuditSoundKit.rms(renders[0])
            val attack = sr / 50
            val attackRms = AuditSoundKit.rms(x = renders[0], from = 0, to = attack)
            val adjacentMax = DoubleArray(block - 1)
            val adjacentRms = DoubleArray(block - 1)
            val adjacentAttack = DoubleArray(block - 1)
            val toZeroMax = DoubleArray(block - 1)
            val toZeroRms = DoubleArray(block - 1)

            for (k in 0 until block - 1) {
                val (m, r) = diffDb(a = renders[k], b = renders[k + 1], from = 0, to = case.span, peak = peak, rmsRef = rmsRef)
                val (_, ra) = diffDb(a = renders[k], b = renders[k + 1], from = 0, to = attack, peak = peak, rmsRef = attackRms)
                val (m0, r0) = diffDb(a = renders[0], b = renders[k + 1], from = 0, to = case.span, peak = peak, rmsRef = rmsRef)

                adjacentMax[k] = m
                adjacentRms[k] = r
                adjacentAttack[k] = ra
                toZeroMax[k] = m0
                toZeroRms[k] = r0
            }

            fun stats(x: DoubleArray): String {
                val sorted = x.sorted()

                return "worst ${f(sorted.last(), 1)}  median ${f(sorted[sorted.size / 2], 1)}  best ${f(sorted.first(), 1)}"
            }

            report.line(case.label)
            report.line("  adjacent onsets, max re peak:       ${stats(adjacentMax)}")
            report.line("  adjacent onsets, rms re note rms:   ${stats(adjacentRms)}")
            report.line("  adjacent onsets, first 20 ms rms:   ${stats(adjacentAttack)}")
            report.line("  onset k against onset 0, max re peak (the framing probe's measure): ${stats(toZeroMax)}")
            report.line("  onset k against onset 0, rms re note rms:                         ${stats(toZeroRms)}")

            // A control: the same voice without the envelope must be identical at every offset.
            val staticParams = case.params - "lpf.env" - "lpf.attack" - "lpf.decay" - "lpf.sustain" - "lpf.release"
            val s0 = AuditSoundKit.renderVoice(sound = case.sound, freqHz = case.freq, params = staticParams, frames = case.span + 2 * block, gateFrames = case.gate, onsetFrame = 0).left
            val s37 = AuditSoundKit.renderVoice(sound = case.sound, freqHz = case.freq, params = staticParams, frames = case.span + 2 * block, gateFrames = case.gate, onsetFrame = 37).left
            var staticDiff = 0.0

            for (i in 0 until case.span) {
                staticDiff = max(staticDiff, abs(s0[i] - s37[37 + i]))
            }

            report.line("  control, the same voice without an envelope, onset 0 against onset 37: max |difference| $staticDiff")
        }
    }

    "L4-02 (c): today's block law, the exact per-sample cutoff and a note-anchored grid, in test code checked against the engine (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L4-02 (c) the proposed fix shape: knots on a grid anchored to the note (every N frames plus the breakpoints), coefficients stepped between them ==")

        val freq = 110.0
        val baseCutoff = 300.0
        val depth = 24.0
        val q = 0.707
        val gate = 3 * sr / 10
        val span = gate + 2400 + 2400
        val saw = IgnitorDsl.Saw(analog = IgnitorDsl.Constant(0.0))
        val tree = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Constant(baseCutoff),
            q = IgnitorDsl.Constant(q),
            analog = IgnitorDsl.Constant(0.0),
            env = IgnitorDsl.Constant(depth),
        )
        val source = renderTreeAtOffset(tree = saw, freqHz = freq, offset = 0, noteFrames = span, gateFrames = gate)
        val source37 = renderTreeAtOffset(tree = saw, freqHz = freq, offset = 37, noteFrames = span, gateFrames = gate)
        var sourceDiff = 0.0

        for (i in 0 until span) {
            sourceDiff = max(sourceDiff, abs(source[i] - source37[i]))
        }

        val core = EnvelopeCore()
        val attackFrames = 0.01 * sr
        val decayFrames = 0.1 * sr
        val releaseFrames = 0.1 * sr

        core.prepare(
            attackFrames = attackFrames,
            decayFrames = decayFrames,
            sustainLevel = 1.0,
            releaseFrames = releaseFrames,
            gateEndPos = gate,
            attackCurve = AdsrCurve.Exponential,
            decayCurve = AdsrCurve.Exponential,
            releaseCurve = AdsrCurve.Exponential,
        )

        val cutoffAt: (Int) -> Double = { pos -> core.filterEnvCutoff(pos = pos, baseCutoff = baseCutoff, depthSemitones = depth) * 1.0 * 1.0 }

        fun todayKnots(k: Int): IntArray {
            val first = if (k == 0) block else block - k

            return (listOf(0) + generateSequence(first) { it + block }.takeWhile { it < span + block }.toList()).toIntArray()
        }

        val breakpoints = listOf(attackFrames.toInt(), (attackFrames + decayFrames).toInt(), gate, gate + floor(releaseFrames).toInt())

        fun gridKnots(n: Int): IntArray = (generateSequence(0) { it + n }.takeWhile { it < span + n }.toList() + breakpoints).distinct().sorted().toIntArray()

        val exact = sweptLowpass(input = source, knots = IntArray(span + 1) { it }, cutoffAt = cutoffAt, q = q)
        val peak = AuditSoundKit.peak(exact)
        val rmsRef = AuditSoundKit.rms(exact)
        var emuMismatch = 0.0
        val todayErr = DoubleArray(block)
        val todayErrRms = DoubleArray(block)
        val todays = ArrayList<DoubleArray>()

        for (k in 0 until block) {
            val engine = renderTreeAtOffset(tree = tree, freqHz = freq, offset = k, noteFrames = span, gateFrames = gate)
            val emu = sweptLowpass(input = source, knots = todayKnots(k), cutoffAt = cutoffAt, q = q)

            for (i in 0 until span) {
                emuMismatch = max(emuMismatch, abs(engine[i] - emu[i]))
            }

            val (m, r) = diffDb(a = engine, b = exact, from = 0, to = span, peak = peak, rmsRef = rmsRef)

            todayErr[k] = m
            todayErrRms[k] = r
            todays.add(engine)
        }

        report.line(
            "saw 110 Hz into lowpass 300 Hz, env 24 st, a 0.01 d 0.1 s 1 r 0.1 (exponential), q 0.707, gate 0.3 s; compared over ${f(span.toDouble() / sr, 3)} s " +
                "(the note, the release and 50 ms after); source identical at offsets 0 and 37: max diff $sourceDiff",
        )
        report.line("test emulation of today's law against the ENGINE at all 128 onsets: largest |difference| $emuMismatch (0 = the emulation is the engine, bit for bit)")

        val sortedToday = todayErr.sorted()

        report.line(
            "today against the exact per-sample cutoff: max re peak worst ${f(sortedToday.last(), 1)} dB, median ${f(sortedToday[block / 2], 1)}, best ${f(sortedToday.first(), 1)}; " +
                "rms re rms worst ${f(todayErrRms.maxOrNull() ?: 0.0, 1)}",
        )

        for (n in listOf(128, 64, 32)) {
            val grid = sweptLowpass(input = source, knots = gridKnots(n), cutoffAt = cutoffAt, q = q)
            val (gm, gr) = diffDb(a = grid, b = exact, from = 0, to = span, peak = peak, rmsRef = rmsRef)
            val changeMax = DoubleArray(block)
            val changeRms = DoubleArray(block)

            for (k in 0 until block) {
                val (cm, cr) = diffDb(a = grid, b = todays[k], from = 0, to = span, peak = peak, rmsRef = rmsRef)

                changeMax[k] = cm
                changeRms[k] = cr
            }

            val sortedMax = changeMax.sorted()
            val sortedRms = changeRms.sorted()
            val sets = gridKnots(n).count { it < span }

            report.line(
                "grid N=$n (+4 breakpoints, $sets coefficient sets per note): against exact max ${f(gm, 1)} rms ${f(gr, 1)} dB; " +
                    "the CHANGE against today, over the 128 onsets: max re peak median ${f(sortedMax[block / 2], 1)} worst ${f(sortedMax.last(), 1)}, " +
                    "rms re rms median ${f(sortedRms[block / 2], 1)} worst ${f(sortedRms.last(), 1)}",
            )
        }

        // Today's own spread: what one onset moved by k frames already changes.
        val spread = DoubleArray(block - 1) { diffDb(a = todays[0], b = todays[it + 1], from = 0, to = span, peak = peak, rmsRef = rmsRef).second }.sorted()

        report.line("today's own spread, onset 0 against onset k, rms re rms: median ${f(spread[spread.size / 2], 1)} worst ${f(spread.last(), 1)}")

        // Cost: the coefficient sets per note, today at onset 0 (two per moving block, one per still block) against the grid.
        var todaySets = 0

        for (knot in todayKnots(0).filter { it < span }) {
            val next = knot + block

            todaySets += if (cutoffAt(knot) != cutoffAt(next)) 2 else 1
        }

        report.line("coefficient sets (each a tan and a divide) over this note: today $todaySets; grid N=128 ${gridKnots(128).count { it < span }}; N=32 ${gridKnots(32).count { it < span }}")
    }

    // ── L2-01: a voice that ends in its own envelope ────────────────────────────────────────────────────────────

    "L2-01: the gain left on a voice's last frame, by release and curve, and the sine's zero-crossing (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L2-01 the last rendered frame of a voice that ends in classic()'s envelope ==")

        // A DC source through classic(): the output IS the applied gain (times the channel), so no phase hides it.
        val dcName = "auditdcvoice"
        val registry = { IgnitorRegistry().apply { registerDefaults(); register(dcName, IgnitorDsl.Constant(1.0).classic()) } }
        val gate = sr / 2
        val linear = AdsrCurves.indexOf(AdsrCurve.Linear)
        val exponential = AdsrCurves.indexOf(AdsrCurve.Exponential)

        for ((curveName, curve) in listOf("exponential (default)" to exponential, "linear" to linear)) {
            for (release in listOf(0.2, 0.05, 0.02, 0.01, 0.005, 0.002)) {
                val out = renderFramedVoice(
                    note = FramingNote(onsetFrame = 0, gateFrames = gate, cutFrame = FRAMING_NONE, renderFrames = gate + sr / 2),
                    data = VoiceData.empty.copy(
                        sound = dcName,
                        freqHz = 110.0,
                        ignitorParams = mapOf("adsr.release" to release, "adsrCurves.release" to curve).toKlangMap(),
                    ),
                    makeRegistry = registry,
                ).left
                val level = out[gate - 1]
                val last = (out.indices).last { out[it] != 0.0 }
                val expectedLast = gate + floor(release * sr).toInt() - 1

                report.line(
                    "DC voice, release ${f(release, 3)} s, $curveName: last nonzero frame ${last - gate} after the gate (expected ${expectedLast - gate}); " +
                        "gain left there ${f(out[last] / level, 5)} (${db(out[last] / level)} dB re the note), the frame after it 0",
                )
            }
        }

        // The probe's sine: 110 Hz, onset at CLICK_VOICE_START, gate 2 s, the default release; then the gate a quarter period later.
        report.line()

        for (release in listOf(0.05, 0.01, 0.005)) {
            for ((label, shift) in listOf("the probe's gate" to 0, "the gate a quarter period later (+109 frames)" to 109)) {
                val noteGate = 2 * sr + shift
                val out = renderFramedVoice(
                    note = FramingNote(onsetFrame = CLICK_VOICE_START, gateFrames = noteGate, cutFrame = FRAMING_NONE, renderFrames = CLICK_VOICE_START + noteGate + 2 * sr),
                    data = VoiceData.empty.copy(sound = "sine", freqHz = 110.0, ignitorParams = mapOf("analog" to 0.0, "adsr.release" to release).toKlangMap()),
                ).left
                val notePeak = AuditSoundKit.peak(x = out, from = CLICK_VOICE_START + sr / 2, to = CLICK_VOICE_START + sr)
                val last = (out.indices).last { out[it] != 0.0 }
                val reading = measureClick(y = out, eventFrame = CLICK_VOICE_START + noteGate, config = ClickConfig())

                report.line(
                    "sine 110 Hz, release ${f(release, 3)}, $label: last sample ${f(out[last] / notePeak, 5)} of the peak (${db(abs(out[last]) / notePeak)} dB), " +
                        "the step to 0 after it; click detector at the gate: kink ${f(reading.kinkRatio, 1)} at +${reading.kinkOffsetFrames}, burst ${f(reading.burstAbsDb, 1)} dB",
                )
            }
        }
    }

    // ── L4-03: sustain 0 and the de-click smoother ──────────────────────────────────────────────────────────────

    "L4-03: subnormals from classic()'s de-click at sustain 0, the stage alone and through the voice into the cylinder (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L4-03 adsr(0.01, 0.1, 0, 0.05), held 3 s ==")

        val held = 3 * sr
        val total = wholeProbeBlocks(held + sr / 2)
        val step = ProbeSignal(name = "step", left = DoubleArray(total) { if (it < held) 1.0 else 0.0 }, right = DoubleArray(total) { if (it < held) 1.0 else 0.0 }, inputEndFrame = held)
        val params = mapOf("adsr.attack" to 0.01, "adsr.decay" to 0.1, "adsr.sustain" to 0.0, "adsr.release" to 0.05)
        val stage = ignitorProbeStage(tree = IgnitorDsl.Sample.classic(), params = params)
        val facts = measureStage(stage, step).left
        val gain = stage.render(step).left

        report.line(
            "stage probe (Sample.classic() on a 1.0 step): subnormal samples ${facts.subnormalCount}, the first at frame ${facts.firstSubnormalFrame} " +
                "(${f(facts.firstSubnormalFrame.toDouble() / sr, 3)} s; the decay ends at 0.110 s)",
        )
        report.line(
            "the gain at 0.2 / 0.5 / 0.8 / 1.0 / 2.0 / 2.99 s: ${listOf(0.2, 0.5, 0.8, 1.0, 2.0, 2.99).joinToString(" / ") { gain[(it * sr).toInt()].toString() }}",
        )

        val voice = AuditSoundKit.renderVoice(sound = "saw", freqHz = 110.0, params = mapOf("analog" to 0.0) + params, frames = total, gateFrames = held)
        var subnormal = 0
        var firstSub = -1
        var zeros = 0

        for (channel in listOf(voice.left, voice.right ?: DoubleArray(0))) {
            for (i in channel.indices) {
                val a = abs(channel[i])

                if (a > 0.0 && a < 2.2250738585072014E-308) {
                    subnormal++

                    if (firstSub < 0 || i < firstSub) {
                        firstSub = i
                    }
                }

                if (i in sr / 2 until held && channel[i] == 0.0) {
                    zeros++
                }
            }
        }

        report.line(
            "the saw voice's cylinder mix (both channels): subnormal samples $subnormal, the first at frame $firstSub (${f(firstSub.toDouble() / sr, 3)} s); " +
                "exact zeros between 0.5 s and the gate $zeros",
        )

        // What it costs on this JVM: the same 20 s held saw voice at sustain 0 (subnormal for 19.2 s of it) against
        // sustain 1e-9 (a normal number all along, the same work otherwise), warmed up, then 9 interleaved rounds.
        val long = 20 * sr
        val longParams = mapOf("analog" to 0.0, "adsr.attack" to 0.01, "adsr.decay" to 0.1, "adsr.release" to 0.05)

        fun timed(sustain: Double): Long {
            val started = System.nanoTime()

            AuditSoundKit.renderVoice(sound = "saw", freqHz = 110.0, params = longParams + ("adsr.sustain" to sustain), frames = long, gateFrames = long)

            return System.nanoTime() - started
        }

        repeat(2) {
            timed(0.0)
            timed(1e-9)
        }

        val zeroTimes = ArrayList<Long>()
        val tinyTimes = ArrayList<Long>()

        repeat(9) {
            zeroTimes.add(timed(0.0))
            tinyTimes.add(timed(1e-9))
        }

        val zeroMedian = zeroTimes.sorted()[4] / 1e6
        val tinyMedian = tinyTimes.sorted()[4] / 1e6

        report.line(
            "cost on this JVM, a 20 s held saw voice (render plus the registry build), median of 9 interleaved rounds: sustain 0 ${f(zeroMedian, 1)} ms, " +
                "sustain 1e-9 ${f(tinyMedian, 1)} ms, ratio ${f(zeroMedian / tinyMedian, 3)}",
        )
    }

    // ── L7a-01 and L7a-02: delay and reverb switched on, and a chain swap ──────────────────────────────────────

    "L7a-01 and L7a-02: a delay switched on, a swap into a room and into a delay, and the room's first return (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L7a-01 / L7a-02 the click detector on a 110 Hz tone, worst of 4 instants (the click table's method) ==")

        val config = ClickConfig()

        fun eventAt(instant: Int): Int = CLICK_EVENT_FRAME + instant * block

        fun tone(instant: Int): DoubleArray = clickTone(frames = wholeProbeBlocks(eventAt(instant) + config.tailFrames + block), peakFrame = CLICK_EVENT_FRAME)

        fun line(label: String, renders: List<ClickRender>) {
            val readings = renders.map { measureClickStereo(it, config) }
            val worst = readings.maxBy { it.kinkRatio }

            report.line(
                "${label.padEnd(54)} kink ${f(worst.kinkRatio, 1).padStart(6)} at ${f(worst.kinkOffsetFrames * 1000.0 / sr, 1)} ms, " +
                    "jump ${f(worst.jumpRatio, 1)}, burst ${f(worst.burstAbsDb, 1)} dB",
            )
        }

        val delayOn = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.3)

        line(
            "classic chain, owner change {} -> delay wet .5 time .25",
            (0 until CLICK_INSTANTS).map { i ->
                val t = tone(i)
                val out = katalystOwnerChangeStage(dsl = KatalystDsl.classic, before = emptyMap<String, Double>().toKlangMap(), after = delayOn.toKlangMap(), eventFrame = eventAt(i))
                    .render(ProbeSignal(name = "tone", left = t, right = t.copyOf(), inputEndFrame = t.size))

                ClickRender(left = out.left, right = out.right, eventFrame = eventAt(i))
            },
        )

        fun swap(name: String, dsl: KatalystDsl): List<ClickRender> = (0 until CLICK_INSTANTS).map { i ->
            val e = eventAt(i)
            val t = tone(i)
            val rig = CylinderSwapRig(sampleRate = sr)
            val left = DoubleArray(t.size)
            val right = DoubleArray(t.size)

            rig.registry.register(name, dsl)

            var at = 0

            while (at < t.size) {
                if (at == e) {
                    rig.cylinder.requestChain(name)
                }

                val from = at
                val out = rig.block(level = 0.0) {
                    t.copyInto(destination = rig.cylinder.mixBuffer.left, destinationOffset = 0, startIndex = from, endIndex = from + block)
                    t.copyInto(destination = rig.cylinder.mixBuffer.right, destinationOffset = 0, startIndex = from, endIndex = from + block)
                }

                out.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = block)
                rig.cylinder.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = block)
                at += block
            }

            ClickRender(left = left, right = right, eventFrame = e)
        }

        line("swap classic -> room (reverb wet .5 size 3)", swap("auditroom", KatalystDsl.of(KatalystStageDsl.Reverb(wet = 0.5.kat, size = 3.0.kat))))
        line(
            "swap classic -> delay (wet .5 time .25 feedback .3)",
            swap("auditdelay", KatalystDsl.of(KatalystStageDsl.Delay(wet = 0.5.kat, time = 0.25.kat, feedback = 0.3.kat))),
        )
        line("swap classic -> gain 0.5 (no tail; the control)", swap("auditgain", KatalystDsl.of(KatalystStageDsl.Gain(gain = 0.5.kat))))

        // The room's first return: an impulse through the room alone, the dry path subtracted.
        val frames = wholeProbeBlocks(sr / 5)
        val impulse = ProbeSignal(name = "impulse", left = DoubleArray(frames).also { it[0] = 1.0 }, right = DoubleArray(frames).also { it[0] = 1.0 }, inputEndFrame = 1)
        val room = katalystProbeStage(KatalystDsl.of(KatalystStageDsl.Reverb(wet = 0.5.kat, size = 3.0.kat))).render(impulse)
        val wet = DoubleArray(frames) { room.left[it] - impulse.left[it] }
        val first = wet.indices.first { abs(wet[it]) > 1e-12 }
        val strongest = (0 until frames).maxBy { abs(wet[it]) }

        report.line(
            "room (reverb wet .5 size 3) on an impulse: first return at frame $first (${f(first * 1000.0 / sr, 1)} ms), strongest in the first 200 ms at " +
                "${f(strongest * 1000.0 / sr, 1)} ms; the swap's ramp is 60 ms",
        )
    }

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

    // ── L7b-03: the reverb's lowpass ─────────────────────────────────────────────────────────────────────────────

    "L7b-03: the room's decay time by band at lowpass 0, 1000, 20000 and unset (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L7b-03 reverb(wet 1, size 5, lowpass X) on an impulse: T60 by octave band (from the -5 to -35 dB slope of 4096-frame STFT frames) ==")

        val frames = wholeProbeBlocks(4 * sr)
        val impulse = ProbeSignal(name = "impulse", left = DoubleArray(frames).also { it[0] = 1.0 }, right = DoubleArray(frames).also { it[0] = 1.0 }, inputEndFrame = 1)
        val hop = 4096
        val window = blackmanHarris7(hop)
        val centres = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 12000.0)

        for (lowpass in listOf(0.0, 1000.0, 5000.0, 20000.0, Double.NaN)) {
            val stage = KatalystStageDsl.Reverb(wet = 1.0.kat, size = 5.0.kat, lowpass = if (lowpass.isNaN()) null else lowpass.kat)
            val out = katalystProbeStage(KatalystDsl.of(stage)).render(impulse).left
            val wet = DoubleArray(frames) { out[it] - impulse.left[it] }
            val starts = generateSequence(0) { it + hop }.takeWhile { it + hop <= frames }.toList()
            val bandLevels = centres.map { c ->
                starts.map { s ->
                    val p = windowedPowerSpectrum(samples = wet, from = s, window = window)
                    val lo = (c / sqrt(2.0) / (sr.toDouble() / hop)).toInt()
                    val hi = (c * sqrt(2.0) / (sr.toDouble() / hop)).toInt()
                    var sum = 1e-300

                    for (k in lo..hi) {
                        sum += p[k]
                    }

                    10.0 * log10(sum)
                }
            }
            val t60s = bandLevels.map { levels ->
                val top = levels.maxOrNull() ?: 0.0
                val idx = levels.indices.filter { levels[it] <= top - 5.0 && levels[it] >= top - 35.0 && it >= levels.indexOf(top) }

                if (idx.size < 3) {
                    Double.NaN
                } else {
                    val xs = idx.map { it.toDouble() * hop / sr }
                    val ys = idx.map { levels[it] }
                    val mx = xs.average()
                    val my = ys.average()
                    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.indices.sumOf { (xs[it] - mx) * (xs[it] - mx) }

                    -60.0 / slope
                }
            }

            report.line(
                "lowpass ${if (lowpass.isNaN()) "unset" else f(lowpass, 0)}: T60 s by band " +
                    centres.indices.joinToString("  ") { "${f(centres[it], 0)}:${f(t60s[it], 2)}" },
            )
        }
    }

    // ── L7b-04: the compressor's detector ────────────────────────────────────────────────────────────────────────

    "L7b-04: the compressor's dead time after silence and its release by what follows (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L7b-04 the cylinder compressor (no lookahead): a DC step at 1.0 (0 dBFS) after 0.2 s of silence; gain = out / in ==")

        val onset = sr / 5
        val loudEnd = onset + sr / 2
        val total = wholeProbeBlocks(loudEnd + sr / 2)

        for ((label, dsl) in listOf("compressor defaults (-20 dB, 4:1, knee 6, attack 3 ms, release 0.1 s)" to KatalystBuilder().compressor().node, "limiter preset (-1 dB, 20:1, knee 2, attack 1 ms)" to KatalystBuilder().limiter().node)) {
            for ((afterLabel, afterLevel) in listOf("then -100 dBFS (reads as silence)" to 1e-5, "then -30 dBFS" to 0.0316227766)) {
                val x = DoubleArray(total) { if (it < onset) 0.0 else if (it < loudEnd) 1.0 else afterLevel }
                val out = katalystProbeStage(dsl).render(ProbeSignal(name = "step", left = x, right = x.copyOf(), inputEndFrame = total)).left
                val gain = DoubleArray(total) { if (x[it] != 0.0) out[it] / x[it] else 1.0 }
                val dead = (onset until loudEnd).firstOrNull { gain[it] < 1.0 }?.minus(onset) ?: -1
                val toMinus1 = (onset until loudEnd).firstOrNull { gain[it] < 0.891 }?.minus(onset) ?: -1
                val steady = gain[loudEnd - 1]
                val recover1 = (loudEnd until total).firstOrNull { gain[it] > 0.891 }?.minus(loudEnd) ?: -1
                val recover01 = (loudEnd until total).firstOrNull { gain[it] > 0.98855 }?.minus(loudEnd) ?: -1

                report.line(
                    "$label, $afterLabel: gain exactly 1.0 for $dead frames (${f(dead * 1000.0 / sr, 2)} ms) after the onset; -1 dB after $toMinus1 frames; " +
                        "steady ${db(steady)} dB; after the step down: within 1 dB after ${f(recover1 * 1000.0 / sr, 1)} ms, within 0.1 dB after ${f(recover01 * 1000.0 / sr, 1)} ms",
                )
            }
        }
    }

    // ── L7b-05: the phaser at a low engine rate ─────────────────────────────────────────────────────────────────

    "L7b-05: the phaser's breakpoint clamp at a 32 kHz and a 48 kHz engine (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L7b-05 phaser(rate .5, wet .5, center 15000, sweep 6000) on 1 s of noise and 1 s of silence ==")

        for (rate in listOf(32000, 44100, 48000)) {
            val total = wholeProbeBlocks(2 * rate)
            val rng = Random(0x5EED)
            val noise = DoubleArray(total) { if (it < rate) rng.nextDouble() * 2.0 - 1.0 else 0.0 }
            val dsl = KatalystDsl.of(KatalystStageDsl.Phaser(rate = 0.5.kat, wet = 0.5.kat, center = 15000.0.kat, sweep = 6000.0.kat))
            val out = katalystProbeStage(dsl = dsl, sampleRate = rate).render(ProbeSignal(name = "noise", left = noise, right = noise.copyOf(), inputEndFrame = rate)).left
            val nonFinite = out.count { !it.isFinite() }
            val finitePeak = out.filter { it.isFinite() }.maxOfOrNull { abs(it) } ?: 0.0
            val loud = out.count { abs(it) > 10.0 }

            report.line("engine at $rate Hz: peak |out| $finitePeak, samples above 10: $loud, non-finite samples $nonFinite")
        }
    }

    // ── L5-01: the optimizer and the filter's gate ──────────────────────────────────────────────────────────────

    "L5-01: a filter on an unset slot, unoptimized against optimized, and through the registry (KLANG_PROBE=audit)".config(enabled = requested) {
        report.line()
        report.line("== L5-01 a plain filter door on an unset slot, no slot written; unoptimized (buildExciter alone) against optimize() ==")

        val frames = wholeProbeBlocks(sr / 2)
        val sine = IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.0))
        val saw = IgnitorDsl.Saw(analog = IgnitorDsl.Constant(0.0))
        val trees = listOf(
            "Sine().lowpass(Slots.lpf.freq)" to sine.lowpass(IgnitorDsl.Slots.lpf.freq),
            "Sine().highpass(Slots.hpf.freq)" to sine.highpass(IgnitorDsl.Slots.hpf.freq),
            "Saw().lowpass(Slots.lpf.freq)" to saw.lowpass(IgnitorDsl.Slots.lpf.freq),
        )

        for ((label, tree) in trees) {
            val optimized = tree.optimize()

            for (freq in listOf(220.0, 880.0)) {
                for (params in listOf(emptyMap<String, Double>(), mapOf("lpf.freq" to 500.0, "hpf.freq" to 500.0))) {
                    val a = AuditSoundKit.renderTree(tree = tree, freqHz = freq, params = params, frames = frames)
                    val b = AuditSoundKit.renderTree(tree = optimized, freqHz = freq, params = params, frames = frames)
                    var maxDiff = 0.0

                    for (i in a.indices) {
                        maxDiff = max(maxDiff, abs(a[i] - b[i]))
                    }

                    report.line(
                        "${label.padEnd(32)} ${f(freq, 0)} Hz ${if (params.isEmpty()) "no slot written  " else "slot written 500 "} optimized root ${optimized::class.simpleName}: " +
                            "level optimized re unoptimized ${db(AuditSoundKit.rms(b) / AuditSoundKit.rms(a))} dB, max |difference| ${f(maxDiff, 4)}",
                    )
                }
            }
        }

        // The production path: the registry optimizes at register(); `.optimizer(0)` is the by-ear A/B.
        val lp = sine.lowpass(IgnitorDsl.Slots.lpf.freq)
        val hp = sine.highpass(IgnitorDsl.Slots.hpf.freq)
        val registry = {
            IgnitorRegistry().apply {
                registerDefaults()
                register("auditlp", lp)
                register("auditlpoff", lp.optimizer(0))
                register("audithp", hp)
                register("audithpoff", hp.optimizer(0))
            }
        }

        for ((on, off) in listOf("auditlp" to "auditlpoff", "audithp" to "audithpoff")) {
            for (freq in listOf(220.0, 880.0)) {
                fun play(sound: String): DoubleArray = renderFramedVoice(
                    note = FramingNote(onsetFrame = 0, gateFrames = sr / 2, cutFrame = FRAMING_NONE, renderFrames = sr / 2),
                    data = VoiceData.empty.copy(sound = sound, freqHz = freq),
                    makeRegistry = registry,
                ).left

                val a = play(off)
                val b = play(on)

                report.line(
                    "registered '$on' against '$off' (.optimizer(0)) at ${f(freq, 0)} Hz, no slot written, the voice path: " +
                        "level ${db(AuditSoundKit.rms(x = b, from = sr / 10, to = sr / 2 - sr / 10) / AuditSoundKit.rms(x = a, from = sr / 10, to = sr / 2 - sr / 10))} dB",
                )
            }
        }
    }
})
