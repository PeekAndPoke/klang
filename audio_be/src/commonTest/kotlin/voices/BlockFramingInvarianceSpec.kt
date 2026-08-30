/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.FilterEnvDef
import io.peekandpoke.klang.audio_be.ignitor.lowpass
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.fm
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * P0 of `docs/plans/block-framing-invariance.md`: a voice's output must depend only on when the
 * note starts and what it is made of, never on how the renderer chops time into blocks.
 *
 * Two drivers: the REAL production framing (`VoiceFactory` -> `Voice.render`, so the first-block
 * `ctx.offset` path is the genuine one), and a raw `IgniteContext` loop for ragged block sequences
 * and runtime-only constructs. Durations are deliberately non-round (no multiple of 128, 64 or 37),
 * so the gate end and the release end land mid-block at every swept size, and the alignment sweep
 * moves the interior envelope breakpoints across every offset (ledger rule E9: an onset-only sweep
 * sees nothing).
 *
 * Class 1 nodes are asserted BIT-IDENTICAL. Recurrence-anchored nodes would need the E7 relative
 * tolerance instead; none of the P0 picks does.
 *
 * The `KNOWN DEFECT` case at the bottom PINS ledger defect E3 in its current wrong shape, so this
 * suite stays green while the defect is open, and goes RED the moment someone fixes it, forcing the
 * flip to the correct assertion written above the pin. (E1, the FM depth envelope, was pinned the
 * same way and was fixed 2026-08-28: "fm with envelope" now sits in the green node list.)
 */
class BlockFramingInvarianceSpec : StringSpec({

    val sampleRate = 44100

    // Non-round on purpose, see the class KDoc.
    val gateFrames = 4813
    val relFrames = 2411
    val relSec = (relFrames + 0.25) / sampleRate

    // ── Driver A: the real production framing ─────────────────────────────────

    /**
     * Renders one voice of the given instrument through the real [VoiceFactory] and [Voice.render],
     * and returns exactly the NOTE-RELATIVE `gateFrames + relFrames` samples. The VCA is switched
     * off so the exciter itself is what gets compared. Fresh registry + [PlaybackCtx] per call with
     * a fixed pid, so every RNG-consuming node draws the identical stream on every call.
     */
    fun renderVoice(dsl: IgnitorDsl, startFrame: Int, blockFrames: Int, pid: String = "framing"): DoubleArray {
        val registry = IgnitorRegistry().apply { registerDefaults(); register("probe", dsl) }
        val voiceBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        // +0.25 frames keeps every floor() stable against 1-ulp time wobble without moving a frame.
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = pid,
                data = VoiceData.empty.copy(
                    freqHz = 220.0,
                    sound = "probe",
                    adsr = AdsrDef.Std(release = relSec, on = false),
                ),
                startTime = (startFrame + 0.25) / sampleRate,
                gateEndTime = (startFrame + gateFrames + 0.25) / sampleRate,
                playbackStartTime = 0.0,
            ),
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = pid, ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = ArrayList<Double>(startFrame + gateFrames + relFrames + blockFrames)
        var b = 0.0
        while (b < sampleRate * 4) {
            voiceBuffer.fill(0.0)
            rc.blockStart = b
            if (!voice.render(rc)) break
            for (v in voiceBuffer) out.add(v)
            b += blockFrames
        }
        val total = gateFrames + relFrames
        check(out.size >= startFrame + total) { "rendered ${out.size}, need ${startFrame + total}" }
        return DoubleArray(total) { out[startFrame + it] }
    }

    // ── Driver B: raw IgniteContext, for ragged sequences and runtime-only chains ──

    /** Renders [makeIg]'s ignitor over a (cycled) ragged block-length sequence. */
    fun renderRagged(lengths: List<Int>, freqHz: Double = 220.0, makeIg: () -> Ignitor): DoubleArray {
        val total = gateFrames + relFrames
        val ig = makeIg()
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            releaseFrames = relFrames,
            scratchBuffers = ScratchBuffers(256),
        )
        val out = DoubleArray(total)
        val tmp = AudioBuffer(256)
        var pos = 0
        var li = 0
        while (pos < total) {
            val n = minOf(lengths[li % lengths.size], total - pos)
            li++
            ctx.offset = 0
            ctx.length = n
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, freqHz, ctx)
            for (i in 0 until n) out[pos + i] = tmp[i]
            pos += n
        }
        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) m = maxOf(m, abs(a[i] - b[i]))
        return m
    }

    fun peak(a: DoubleArray): Double {
        var m = 0.0
        for (v in a) m = maxOf(m, abs(v))
        return m
    }

    // ── The P0 node picks: one envelope, one oscillator, one noise, one delay line ──

    val nodes = listOf<Pair<String, IgnitorDsl>>(
        // Times chosen so attack end (309), decay end (1676) and the adsr's own release all land
        // mid-block at every swept size and alignment.
        "adsr on DC" to IgnitorDsl.Adsr(
            inner = IgnitorDsl.Constant(1.0),
            attackSec = IgnitorDsl.Constant(0.007),
            decaySec = IgnitorDsl.Constant(0.031),
            sustainLevel = IgnitorDsl.Constant(0.6),
            releaseSec = IgnitorDsl.Constant(0.033),
        ),
        "sine" to IgnitorDsl.Sine(),
        // Ledger E1, fixed 2026-08-28: the depth envelope is per-sample now. Attack 220 frames and
        // decay both land mid-block everywhere, so this red-flags any regression to a block hold.
        "fm with envelope" to IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.005,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        ),
        "white noise" to IgnitorDsl.WhiteNoise(),
        "pluck" to IgnitorDsl.Pluck(),
        // Graduated after the W-batch (ledger W1/W2): with constant params both are per-sample
        // loops over contiguous state and belong on the bit-identical list. A MODULATED tremolo
        // depth stays off it — the gap's bulk phase advance reassociates the float sum
        // (~4e-15 class, see ModulationClockSpec).
        "coarse" to IgnitorDsl.Sine().coarse(4.0),
        "tremolo" to IgnitorDsl.Sine().tremolo(rate = 5.0, depth = 0.5),
    )

    nodes.forEach { (name, dsl) ->

        "I1+I3+I4 $name: note-relative output is bit-identical at every onset alignment" {
            val ref = renderVoice(dsl, startFrame = 0, blockFrames = 128)
            check(peak(ref) > 1e-3) { "$name reference is silent — vacuous comparison" }
            for (start in listOf(1, 37, 76, 127)) {
                withClue("$name, startFrame=$start") {
                    maxDiff(renderVoice(dsl, start, 128), ref) shouldBe 0.0
                }
            }
        }

        "I2 $name: bit-identical at block sizes 64 and 37" {
            val ref = renderVoice(dsl, startFrame = 0, blockFrames = 128)
            for (bf in listOf(64, 37)) {
                withClue("$name, blockFrames=$bf") {
                    maxDiff(renderVoice(dsl, 0, bf), ref) shouldBe 0.0
                }
            }
        }
    }

    "I2 ragged: pluck and noise survive a ragged block sequence bit-identically" {
        for ((name, dsl) in listOf(
            "pluck" to IgnitorDsl.Pluck(),
            "white noise" to IgnitorDsl.WhiteNoise(),
        )) {
            val ref = renderRagged(listOf(128)) { dsl.toExciter(random = Random(7)) }
            check(peak(ref) > 1e-3) { "$name ragged reference is silent" }
            val ragged = renderRagged(listOf(37, 128, 64, 91, 13, 111)) { dsl.toExciter(random = Random(7)) }
            withClue(name) { maxDiff(ragged, ref) shouldBe 0.0 }
        }
    }

    // ── Pinned defects (findings ledger). These go RED when the defect is fixed. ──

    "E3 KNOWN DEFECT: the SVF cutoff-envelope chord erases a sub-block attack (ledger E3)" {
        // CORRECT assertion, flip to this when fixing (or formally accepting) E3:
        //   rmsEarly(coarse) >= 0.8 * rmsEarly(fine)
        // A 22-frame envelope attack should open the filter within ~half a millisecond. With
        // 128-frame chords the knee is straightened, so the sweep arrives ~a block late and the
        // early window stays dark. lengths=[1] makes every chord one sample long: the analytic
        // reference.
        val env = FilterEnvDef(depth = 60.0, attackSec = 0.0005, decaySec = 0.05, sustainLevel = 0.0, releaseSec = 0.05)
        fun chain(): Ignitor = IgnitorDsl.Sine().toExciter().lowpass(150.0, 0.707, env = env)

        fun rmsEarly(x: DoubleArray): Double {
            var acc = 0.0
            for (i in 0 until 60) acc += x[i] * x[i]
            return sqrt(acc / 60)
        }

        val fine = renderRagged(listOf(1), freqHz = 2000.0) { chain() }
        val coarse = renderRagged(listOf(128), freqHz = 2000.0) { chain() }
        check(rmsEarly(fine) > 1e-4) { "E3 reference is silent — probe mis-tuned" }
        withClue("E3 pin: expected the DEFECT's erasure; red here means E3 changed — flip this case to the >= 0.8 form above") {
            (rmsEarly(coarse) < 0.5 * rmsEarly(fine)) shouldBe true
        }
    }
})
