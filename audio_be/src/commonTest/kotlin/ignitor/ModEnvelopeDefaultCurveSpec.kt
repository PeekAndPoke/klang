/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.DoorAdsr
import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.DoorFm
import io.peekandpoke.klang.audio_be.voices.DoorPenv
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.renderPitchRatios
import io.peekandpoke.klang.audio_be.voices.withClassicSlots
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * **Every modulation envelope defaults to the house EXPONENTIAL curve** (decision D3 (b), 2026-09-25): an
 * envelope whose author writes no curve bends every stage by `g(x) = (e^(3x) - 1) / (e^3 - 1)`, the curve
 * of the chain `adsr`, on every host that has one. This spec holds the pitch envelope (the Ignitor node, alone
 * and as `classic()`'s stage, which sprudel's `penv` fills) and `classic()`'s FM stage, which sprudel's `fm` fills. The Ignitor FM index envelope and the four
 * Ignitor filter envelopes are pinned elsewhere (2026-09-27): their unwritten curve against a written-out
 * Exponential oracle, stage by stage, in `EnvelopeLawSpec` (the FM and filter host rows), and the filter nodes'
 * constructor defaults in `EnvelopeCurveKnobSpec`.
 *
 * The oracles are written out HERE, never read from `MOD_ENV_CURVE` or from the curve code: `g` below is
 * plain arithmetic, and the DSL rows compare against the curve NAMED as the enum literal. A default that
 * moved back to linear, or a host that stopped reading the default, is red in its own row.
 *
 * The level is compared frame by frame: the pitch node's ratio is `2^level` at 12 semitones, the classic FM stage's
 * deviation is read off the ramp-sample probe against a flat reference, and the classic pitch envelope stage's ratio is
 * `2^level` at 12 semitones, read off the same probe.
 */
class ModEnvelopeDefaultCurveSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    // Stage lengths in frames (whole, so the oracle needs no fractional-frame reading): attack, decay,
    // sustain level, release, the gate.
    val a = 480.0
    val d = 960.0
    val s = 0.25
    val r = 480.0
    val gate = 2400
    val total = 3200

    /** The house Exponential curve at the engine's bend of 3, written out. */
    fun g(x: Double): Double = (exp(3.0 * x) - 1.0) / (exp(3.0) - 1.0)

    /** The envelope level at [pos] with every stage exponential: the law as its KDoc states it. */
    fun oracleLevel(pos: Int): Double {
        fun ads(p: Int): Double = when {
            p < a -> g(p / a)
            p < a + d -> s + (1.0 - s) * g(1.0 - (p - a) / d)
            else -> s
        }

        // The release runs floor(r) frames and lands on 0 on the last one (the N - 1 base).
        return if (pos >= gate) ads(gate) * g(1.0 - minOf((pos - gate) / (r - 1.0), 1.0)) else ads(pos)
    }

    fun sec(frames: Double): Double = frames / sampleRate

    fun renderRuntime(ig: Ignitor, freqHz: Double): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val out = DoubleArray(total)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0

        while (pos < total) {
            val n = minOf(blockFrames, total - pos)

            ctx.updateOffsetAndLength(offset = 0, length = n)
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, freqHz, ctx)

            for (i in 0 until n) {
                out[pos + i] = tmp[i]
            }

            pos += n
        }

        return out
    }

    fun renderDsl(dsl: IgnitorDsl): List<Long> {
        val ig = dsl.buildExciter(ignitorParams = null, random = Random(3), freqHz = 220.0).ignitor

        return renderRuntime(ig, 220.0).map { it.toRawBits() }
    }

    val expKnob = AdsrCurves.knob(AdsrCurve.Exponential)
    val linKnob = AdsrCurves.knob(AdsrCurve.Linear)
    val saw = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)

    "the Ignitor pitch envelope: an unwritten curve sweeps on the exponential curve" {
        // 12 semitones: the ratio is 2^level, so log2 of it is the level. 1e-9: the engine bends through
        // `fastExp` and raises through `fastExp2`.
        val out = renderRuntime(
            pitchEnvelopeModIgnitor(
                attack = ParamIgnitor("a", sec(a)),
                decay = ParamIgnitor("d", sec(d)),
                release = ParamIgnitor("r", sec(r)),
                semitones = ParamIgnitor("st", 12.0),
                sustain = ParamIgnitor("s", s),
            ),
            freqHz = 220.0,
        )

        for (pos in 0 until total) {
            withClue("frame $pos") { ln(out[pos]) / ln(2.0) shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
        }

        // The node's own field defaults, through the whole build: the same as the curve named Exponential.
        val bare = IgnitorDsl.PitchEnvelope(
            inner = saw, semitones = IgnitorDsl.Constant(12.0), attack = IgnitorDsl.Constant(sec(a)), decay = IgnitorDsl.Constant(sec(d)),
            sustain = IgnitorDsl.Constant(s), release = IgnitorDsl.Constant(sec(r)),
        )

        renderDsl(bare) shouldBe renderDsl(bare.copy(attackCurve = expKnob, decayCurve = expKnob, releaseCurve = expKnob))
        withClue("anti-vacuous: linear sounds different here") {
            renderDsl(bare) shouldNotBe renderDsl(bare.copy(attackCurve = linKnob, decayCurve = linKnob, releaseCurve = linKnob))
        }
    }

    "classic()'s FM stage (sprudel's fm): an unwritten curve (no door names one) is the exponential curve" {
        // Through the real VoiceFactory and the `fm.*` slots (pitch pipeline step 4). The ratio the voice applies is
        // `1 + sin(phase) * depth * level / freq`, read off the ramp-sample probe; against a reference voice with no
        // envelope (every stage at its default: the depth is full on every frame, the release tail included) whose
        // modulator runs the same phases, the ratio of the two deviations is the level itself, PER FRAME (the strip
        // held it per block until step 4, ledger E11), gate and release included.
        fun ratios(attack: Double?, decay: Double?, sustain: Double?, release: Double?): DoubleArray = renderPitchRatios(
            doors = DoorFields(
                fm = DoorFm(depth = 100.0, ratio = 1.0, attack = attack, decay = decay, sustain = sustain, release = release),
                adsr = DoorAdsr(release = 1.0),
            ),
            frames = total,
            gateFrames = gate,
        )

        val shaped = ratios(attack = sec(a), decay = sec(d), sustain = s, release = sec(r))
        val flat = ratios(attack = null, decay = null, sustain = null, release = null)
        var compared = 0

        for (pos in 0 until total) {
            val reference = flat[pos] - 1.0

            // Frames where the modulator crosses zero carry no level; skip them (the probe reads the ratio to about
            // 1e-11, so a reference above 0.05 keeps the quotient's error under 1e-9).
            if (abs(reference) > 0.05) {
                compared++
                withClue("frame $pos") { (shaped[pos] - 1.0) / reference shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
            }
        }

        withClue("anti-vacuous: the modulator moved the ratio on most frames") { (compared > total / 2) shouldBe true }
    }

    "classic()'s pitch envelope stage (sprudel's penv): an unwritten curve slot sweeps on the exponential curve" {
        // Through the real VoiceFactory and the `penv.*` slots (pitch pipeline step 1): 12 semitones, so log2 of the
        // frequency ratio the voice applies is the level, frame by frame, gate and release included. The ratio is
        // read off the ramp-sample probe (`renderPitchRatios`), whose rounding is about 1e-11.
        fun ratios(attack: AdsrCurve?, decay: AdsrCurve?, release: AdsrCurve?): DoubleArray = renderPitchRatios(
            doors = DoorFields(
                penv = DoorPenv(
                    semitones = 12.0, attack = sec(a), decay = sec(d), sustain = s, release = sec(r),
                    attackCurve = attack, decayCurve = decay, releaseCurve = release,
                ),
                adsr = DoorAdsr(release = 1.0),
            ),
            frames = total,
            gateFrames = gate,
        )

        fun ratios(named: AdsrCurve?): DoubleArray = ratios(attack = named, decay = named, release = named)

        val unwritten = ratios(null)

        for (pos in 0 until total) {
            withClue("frame $pos") { ln(unwritten[pos]) / ln(2.0) shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
        }

        withClue("a curve named in the slot reaches the stage: linear sounds different here") {
            ratios(AdsrCurve.Linear).toList() shouldNotBe unwritten.toList()
        }
        withClue("and naming the default is the unwritten sweep, bit for bit") {
            ratios(AdsrCurve.Exponential).map { it.toRawBits() } shouldBe unwritten.map { it.toRawBits() }
        }

        // Each curve slot shapes ITS stage and no other: one stage named linear moves frames of that stage only
        // (the gate sits after the sweep, so the release starts from the sustain whatever the first two curves).
        val stages = listOf(
            Triple("attack", ratios(attack = AdsrCurve.Linear, decay = null, release = null), 0 until a.toInt()),
            Triple("decay", ratios(attack = null, decay = AdsrCurve.Linear, release = null), a.toInt() until (a + d).toInt()),
            Triple("release", ratios(attack = null, decay = null, release = AdsrCurve.Linear), gate until gate + r.toInt()),
        )

        for ((stage, named, frames) in stages) {
            // Above the probe's rounding: a different sweep moves the playhead, so later ratios differ in the last bits.
            val moved = (0 until total).filter { abs(named[it] - unwritten[it]) > 1e-9 }

            withClue("$stage: the named curve moves frames") { moved.isNotEmpty() shouldBe true }
            withClue("$stage: only frames of its own stage") { moved.all { it in frames } shouldBe true }
        }
    }
})
