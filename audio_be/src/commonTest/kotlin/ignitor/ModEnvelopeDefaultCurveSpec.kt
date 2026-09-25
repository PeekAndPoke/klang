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
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * **Every modulation envelope defaults to the house EXPONENTIAL curve** (decision D3 (b), 2026-09-25): an
 * envelope whose author writes no curve bends every stage by `g(x) = (e^(3x) - 1) / (e^3 - 1)`, the curve
 * of the chain `adsr` and the strip VCA, on every host that has one: the Ignitor pitch, FM and filter
 * envelopes and the voice strip's FM, filter and pitch envelopes (the strip pitch envelope, sprudel's
 * `penv`, since phase 3 step 5b (c1)).
 *
 * The oracles are written out HERE, never read from `MOD_ENV_CURVE` or from the curve code: `g` below is
 * plain arithmetic, and the DSL rows compare against the curve NAMED as the enum literal. A default that
 * moved back to linear, or a host that stopped reading the default, is red in its own row.
 *
 * Where the level is observable it is compared frame by frame (the pitch node's ratio is `2^level` at 12
 * semitones, the FM node's is `1 + level` with a constant modulator and `depth == freq`, the strip FM's
 * multiplier is read off the voice's frequency-modulation buffer against a flat reference, and the strip
 * pitch envelope's ratio is `2^level` at 12 semitones, read off the same buffer). The filters'
 * level is not observable on their output, so their rows pin the unwritten curve against the same node
 * with the curve named Exponential; the exponential law itself through the filter is pinned against a
 * written-out oracle in `EnvelopeLawSpec` (the filter host row), and the strip's filter against the node
 * in `ClassicStripParitySpec`.
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
            releaseFrames = total - gate, scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = DoubleArray(total)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0

        while (pos < total) {
            val n = minOf(blockFrames, total - pos)

            ctx.updateOffsetAndLength(0, n)
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
        val ig = dsl.buildExciter(oscParams = null, random = Random(3), freqHz = 220.0).ignitor

        return renderRuntime(ig, 220.0).map { it.toRawBits() }
    }

    val expKnob = AdsrCurves.knob(AdsrCurve.Exponential)
    val linKnob = AdsrCurves.knob(AdsrCurve.Linear)
    val saw = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    "the Ignitor pitch envelope: an unwritten curve sweeps on the exponential curve" {
        // 12 semitones: the ratio is 2^level, so log2 of it is the level. 1e-9: the engine bends through
        // `fastExp` and raises through `fastExp2`.
        val out = renderRuntime(
            pitchEnvelopeModIgnitor(
                attackSec = ParamIgnitor("a", sec(a)),
                decaySec = ParamIgnitor("d", sec(d)),
                releaseSec = ParamIgnitor("r", sec(r)),
                semitones = ParamIgnitor("st", 12.0),
                sustainLevel = ParamIgnitor("s", s),
            ),
            freqHz = 220.0,
        )

        for (pos in 0 until total) {
            withClue("frame $pos") { ln(out[pos]) / ln(2.0) shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
        }

        // The node's own field defaults, through the whole build: the same as the curve named Exponential.
        val bare = IgnitorDsl.PitchEnvelope(
            saw, IgnitorDsl.Constant(12.0), IgnitorDsl.Constant(sec(a)), IgnitorDsl.Constant(sec(d)),
            IgnitorDsl.Constant(s), IgnitorDsl.Constant(sec(r)),
        )

        renderDsl(bare) shouldBe renderDsl(bare.copy(attackCurve = expKnob, decayCurve = expKnob, releaseCurve = expKnob))
        withClue("anti-vacuous: linear sounds different here") {
            renderDsl(bare) shouldNotBe renderDsl(bare.copy(attackCurve = linKnob, decayCurve = linKnob, releaseCurve = linKnob))
        }
    }

    "the Ignitor FM index envelope (no curve knob at all): every stage on the exponential curve" {
        // A constant modulator of 1.0 with depth == freq makes the ratio exactly 1 + level.
        val out = renderRuntime(
            fmModIgnitor(
                modulator = ParamIgnitor("m", 1.0),
                ratio = ParamIgnitor("ratio", 1.0),
                depth = ParamIgnitor("depth", 100.0),
                envAttackSec = ParamIgnitor("a", sec(a)),
                envDecaySec = ParamIgnitor("d", sec(d)),
                envSustainLevel = ParamIgnitor("s", s),
                envReleaseSec = ParamIgnitor("r", sec(r)),
                freq = FreqIgnitor,
            ),
            freqHz = 100.0,
        )

        for (pos in 0 until total) {
            withClue("frame $pos") { out[pos] - 1.0 shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
        }
    }

    "the Ignitor filter envelopes: an unwritten curve renders the curve named Exponential, on all four" {
        val cutoff = IgnitorDsl.Constant(400.0)
        val sweep = IgnitorDsl.Constant(24.0)
        val att = IgnitorDsl.Constant(sec(a))
        val dec = IgnitorDsl.Constant(sec(d))
        val sus = IgnitorDsl.Constant(s)
        val rel = IgnitorDsl.Constant(sec(r))

        // Each: the bare node, and the node with the three curves named.
        val kinds: List<Pair<String, (IgnitorDsl?) -> IgnitorDsl>> = listOf(
            "lowpass" to { k ->
                val n = IgnitorDsl.Lowpass(saw, cutoff, env = sweep, attackSec = att, decaySec = dec, sustainLevel = sus, releaseSec = rel)
                if (k == null) n else n.copy(attackCurve = k, decayCurve = k, releaseCurve = k)
            },
            "highpass" to { k ->
                val n = IgnitorDsl.Highpass(saw, cutoff, env = sweep, attackSec = att, decaySec = dec, sustainLevel = sus, releaseSec = rel)
                if (k == null) n else n.copy(attackCurve = k, decayCurve = k, releaseCurve = k)
            },
            "bandpass" to { k ->
                val n = IgnitorDsl.Bandpass(saw, cutoff, env = sweep, attackSec = att, decaySec = dec, sustainLevel = sus, releaseSec = rel)
                if (k == null) n else n.copy(attackCurve = k, decayCurve = k, releaseCurve = k)
            },
            "notch" to { k ->
                val n = IgnitorDsl.Notch(saw, cutoff, env = sweep, attackSec = att, decaySec = dec, sustainLevel = sus, releaseSec = rel)
                if (k == null) n else n.copy(attackCurve = k, decayCurve = k, releaseCurve = k)
            },
        )

        for ((name, node) in kinds) {
            val unwritten = renderDsl(node(null))

            withClue("$name: the unwritten curve is Exponential") { unwritten shouldBe renderDsl(node(expKnob)) }
            withClue("$name: anti-vacuous, linear sounds different here") { unwritten shouldNotBe renderDsl(node(linKnob)) }
        }

        // The resolved struct the runtime reads: a shape that names no curve runs the node's default.
        val resolved = FilterEnvDef(depth = 24.0, attackSec = sec(a), decaySec = sec(d), sustainLevel = s, releaseSec = sec(r))
        val named = resolved.copy(
            attackCurve = AdsrCurve.Exponential, decayCurve = AdsrCurve.Exponential, releaseCurve = AdsrCurve.Exponential,
        )

        fun raw(env: FilterEnvDef): List<Long> =
            renderRuntime(Ignitors.sawtooth().lowpass(ParamIgnitor("f", 400.0), ParamIgnitor("q", 0.707), env), 220.0).map { it.toRawBits() }

        withClue("FilterEnvDef: an unnamed curve is Exponential") { raw(resolved) shouldBe raw(named) }
    }

    "the strip's FM envelope: an unwritten curve (the wire has none) is the exponential curve" {
        // The multiplier FmRenderer writes is `1 + sin(phase) * depth * level / freq`, held per block at the
        // level of the block's first frame. Against a reference voice whose envelope is flat at 1 (attack 0,
        // decay 0, sustain 1) and whose modulator runs the same phases, the ratio of the two deviations is
        // the level itself. The strip FM has no release (the wire carries none); the gate is past the render.
        fun multipliers(attack: Double, decay: Double, sustain: Double): List<DoubleArray> {
            val registry = IgnitorRegistry().apply { registerDefaults() }
            val voiceBuffer = DoubleArray(blockFrames)
            val freqModBuffer = DoubleArray(blockFrames)
            val factory = VoiceFactory(
                sampleRate = sampleRate,
                sampleRateDouble = sampleRate.toDouble(),
                blockFrames = blockFrames,
                ignitorRegistry = registry,
                pipelineRegistry = PipelineRegistry(),
                cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
                voiceBuffer = voiceBuffer,
                freqModBuffer = freqModBuffer,
                scratchBuffers = ScratchBuffers(blockFrames),
            )
            val voice = factory.makeVoice(
                scheduled = ScheduledVoice(
                    playbackId = "fm",
                    data = VoiceData.empty.copy(
                        freqHz = 220.0, sound = "sine", adsr = AdsrDef.Std(on = false),
                        fmh = 1.0, fmEnv = 100.0, fmAttack = attack, fmDecay = decay, fmSustain = sustain,
                    ),
                    startTime = 0.0,
                    gateEndTime = 1.0,
                    playbackStartTime = 0.0,
                ),
                backendStartTimeSec = 0.0,
                playbackCtx = PlaybackCtx(playbackId = "fm", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
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

            return (0 until gate / blockFrames).map { b ->
                voiceBuffer.fill(0.0)
                rc.blockStart = (b * blockFrames).toDouble()
                voice.render(rc)
                freqModBuffer.copyOf()
            }
        }

        val shaped = multipliers(sec(a), sec(d), s)
        val flat = multipliers(0.0, 0.0, 1.0)
        var compared = 0

        for (b in shaped.indices) {
            for (i in 0 until blockFrames) {
                val reference = flat[b][i] - 1.0

                // Frames where the modulator crosses zero carry no level; skip them.
                if (abs(reference) > 1e-3) {
                    compared++
                    withClue("block $b frame $i") {
                        (shaped[b][i] - 1.0) / reference shouldBe (oracleLevel(b * blockFrames) plusOrMinus 1e-9)
                    }
                }
            }
        }

        withClue("anti-vacuous: the modulator moved the buffer") { (compared > gate / 2) shouldBe true }
    }

    "the strip's pitch envelope (sprudel's penv): an unwritten wire curve sweeps on the exponential curve" {
        // Through the real VoiceFactory: 12 semitones, so log2 of the ratio the renderer writes into the
        // frequency-modulation buffer is the level, frame by frame, gate and release included.
        fun ratios(named: AdsrCurve?): DoubleArray {
            val registry = IgnitorRegistry().apply { registerDefaults() }
            val voiceBuffer = DoubleArray(blockFrames)
            val freqModBuffer = DoubleArray(blockFrames)
            val factory = VoiceFactory(
                sampleRate = sampleRate,
                sampleRateDouble = sampleRate.toDouble(),
                blockFrames = blockFrames,
                ignitorRegistry = registry,
                pipelineRegistry = PipelineRegistry(),
                cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
                voiceBuffer = voiceBuffer,
                freqModBuffer = freqModBuffer,
                scratchBuffers = ScratchBuffers(blockFrames),
            )
            val voice = factory.makeVoice(
                scheduled = ScheduledVoice(
                    playbackId = "penv",
                    data = VoiceData.empty.copy(
                        freqHz = 220.0, sound = "sine", adsr = AdsrDef.Std(on = false),
                        pEnv = 12.0, pAttack = sec(a), pDecay = sec(d), pSustain = s, pRelease = sec(r),
                        pAttackCurve = named, pDecayCurve = named, pReleaseCurve = named,
                    ),
                    startTime = 0.0,
                    gateEndTime = sec(gate.toDouble()),
                    playbackStartTime = 0.0,
                ),
                backendStartTimeSec = 0.0,
                playbackCtx = PlaybackCtx(playbackId = "penv", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
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
            val out = DoubleArray(total)

            for (b in 0 until total / blockFrames) {
                rc.blockStart = (b * blockFrames).toDouble()
                voice.render(rc)
                freqModBuffer.copyInto(out, b * blockFrames)
            }

            return out
        }

        val unwritten = ratios(null)

        for (pos in 0 until total) {
            withClue("frame $pos") { ln(unwritten[pos]) / ln(2.0) shouldBe (oracleLevel(pos) plusOrMinus 1e-9) }
        }

        withClue("a curve named on the wire reaches the strip: linear sounds different here") {
            ratios(AdsrCurve.Linear).toList() shouldNotBe unwritten.toList()
        }
        withClue("and naming the default is the unwritten sweep, bit for bit") {
            ratios(AdsrCurve.Exponential).map { it.toRawBits() } shouldBe unwritten.map { it.toRawBits() }
        }
    }
})
