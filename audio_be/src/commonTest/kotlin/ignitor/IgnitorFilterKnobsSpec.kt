/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.butterworthQLadder
import io.peekandpoke.klang.audio_bridge.FilterEnvDef as WireFilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.FILTER_CUTOFF_OFFSET_PER_ANALOG
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import kotlin.random.Random

/**
 * Phase 3 step 3a: the CUTOFF ENVELOPE knobs and the per-voice HUMANIZE lane on the four filter
 * nodes (`docs/tasks/builtin-instruments.md` section 4, first two rows).
 *
 * Every row here answers one of three questions, and a knob that cannot answer all three has no
 * business on the node:
 *
 *  - **the default reproduces today's tree, bit for bit.** Raw bits, not a tolerance: the claim
 *    is that nothing moved, and nothing moved is exact.
 *  - **an explicit value changes what it should change**, so the identity rows cannot be passing
 *    because the knob is dead.
 *  - **the RNG STREAM.** `humanize` is the only knob in the DSL that DRAWS at build, and the draw
 *    order has to reproduce `VoiceFactory`'s per filter or every later noise source and every
 *    supersaw jitter on the voice shifts, silently. Pinned against a literal replay, because a
 *    count is exactly what a second reader of the stream would get wrong.
 *
 * The GATE (step 2) is the fourth thing a row watches: a filter gates on an UNSET CUTOFF and on
 * nothing else, so neither the envelope knobs nor `humanize` may change that verdict, in either
 * direction, and a gated-off filter must not draw.
 */
class IgnitorFilterKnobsSpec : StringSpec({

    val blockFrames = 128
    val blocks = 8
    val freqHz = 220.0
    val sampleRate = 48000

    fun seed() = Random(7)

    fun ctx(rng: Random): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * blocks / 2,
        gateEndFrame = blockFrames * blocks / 2,
        releaseFrames = blockFrames,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    fun build(dsl: IgnitorDsl, params: Map<String, Double>? = null, rng: Random = seed()): Ignitor =
        dsl.buildExciter(
            oscParams = params, random = rng, freqHz = freqHz,
            sampleRate = sampleRate, blockFrames = blockFrames,
        ).ignitor

    /**
     * [blocks] blocks of [dsl], rendered end to end with ONE `Random` shared by the build and the
     * render context, which is what a voice gets. The gate end sits mid-render so the envelope's
     * release segment is reached.
     */
    fun render(dsl: IgnitorDsl, params: Map<String, Double>? = null): List<Long> {
        val rng = seed()
        val ignitor = build(dsl, params, rng)
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out.map { it.toRawBits() }
    }

    /** The node right under the root memo: what the build did or did not add. */
    fun shapeOf(ignitor: Ignitor): Any = (ignitor as MemoizingIgnitor).inner::class

    val saw: IgnitorDsl = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    /**
     * [blocks] blocks of a RAW runtime Ignitor built by [wrap] around the bare saw, for the rows
     * whose oracle is the runtime door itself rather than a DSL node.
     */
    fun renderRaw(wrap: (Ignitor) -> Ignitor): List<Long> {
        val rng = seed()
        val source = saw.buildExciter(random = rng, freqHz = freqHz).ignitor
        val ignitor = wrap(source)
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out.map { it.toRawBits() }
    }


    /**
     * How many and WHICH draws a build took off the voice's stream: build with a shared `Random`,
     * then ask it for one more number and see which replay of the same seed agrees. `nextInt` and
     * `nextDouble` consume different amounts of the generator, so this pins the KINDS too, not
     * just the count.
     */
    fun drawsTakenBy(dsl: IgnitorDsl, params: Map<String, Double>? = null): Int {
        val rng = seed()
        build(dsl, params, rng)
        val after = rng.nextInt()

        // The replay: 0 draws, then the strip's own sequence for one filter (1 x nextDouble for
        // the cutoff tolerance, then AnalogDrift's 2 x nextDouble + 1 x nextInt).
        val replay = seed()

        if (replay.nextInt() == after) return 0

        for (taken in 1..12) {
            val probe = seed()

            repeat(taken) { i ->
                // The strip's kinds, in the strip's order: draws 1..3 are doubles, draw 4 is an int.
                if (i == 3) probe.nextInt() else probe.nextDouble()
            }

            if (probe.nextInt() == after) return taken
        }

        return -1
    }

    val bare = render(saw)
    val bareShape = shapeOf(build(saw))

    // ── The envelope knobs ───────────────────────────────────────────────────────────────────

    "the node's envelope defaults ARE the shared constants, in one home" {
        val lp = IgnitorDsl.Lowpass(inner = saw, freq = IgnitorDsl.Constant(800.0))

        lp.env shouldBe IgnitorDsl.Constant(0.0)
        lp.attackSec shouldBe IgnitorDsl.Constant(FILTER_ENV_ATTACK_SEC)
        lp.decaySec shouldBe IgnitorDsl.Constant(FILTER_ENV_DECAY_SEC)
        lp.sustainLevel shouldBe IgnitorDsl.Constant(FILTER_ENV_SUSTAIN_LEVEL)
        lp.releaseSec shouldBe IgnitorDsl.Constant(FILTER_ENV_RELEASE_SEC)
        lp.humanize shouldBe false

        // The strip resolves the same five from the same constants (`FilterEnvDef.resolve`),
        // which is what "defaults live in ONE place" buys. The depth is the one deliberate
        // difference and it is in the KDoc: on a node, `env = 0` IS "no envelope".
        WireFilterEnvDef().resolve().attack shouldBe FILTER_ENV_ATTACK_SEC
        WireFilterEnvDef().resolve().decay shouldBe FILTER_ENV_DECAY_SEC
        WireFilterEnvDef().resolve().sustain shouldBe FILTER_ENV_SUSTAIN_LEVEL
        WireFilterEnvDef().resolve().release shouldBe FILTER_ENV_RELEASE_SEC
    }

    "a filter with the default envelope renders bit for bit what a filter without the knobs rendered" {
        // The oracle is the RUNTIME door, reached without the DSL node: `Ignitor.lowpass` with
        // `FilterEnvDef.NONE`, which is literally the code path that existed before this step.
        val rng = seed()
        val oracleSource = saw.buildExciter(random = rng, freqHz = freqHz).ignitor
        val oracle = oracleSource.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707))
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            oracle.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        render(saw.lowpass(800.0)) shouldBe out.map { it.toRawBits() }
    }

    "naming a stage knob and NOT env sweeps, because the door fills the depth" {
        // The compound fill, heard rather than read off the node (`/dsl-design` section 4 and
        // `fillFilterEnvelope`). `lpf(800, decay = 0.3, sustain = 0.2)` is an audible pluck on
        // sprudel, so `lowpass(800, decaySec = 0.3, sustainLevel = 0.2)` has to be one here.
        val plain = saw.lowpass(800.0)

        render(saw.lowpass(800.0, decaySec = 0.3, sustainLevel = 0.2)) shouldNotBe render(plain)
        render(saw.lowpass(800.0, attackSec = 0.4)) shouldNotBe render(plain)
        render(saw.lowpass(800.0, sustainLevel = 0.1)) shouldNotBe render(plain)
        render(saw.lowpass(800.0, releaseSec = 0.9)) shouldNotBe render(plain)

        withClue("a call that names NONE of the five is the untouched filter, cascade included") {
            // The ORACLE is the runtime door with `FilterEnvDef.NONE`, which is the code path
            // that existed before any of this. Comparing the door against a second call of
            // itself would assert determinism and nothing else, and would stay green under a
            // fill that names the stage unconditionally.
            //
            // The single-stage half of this DUPLICATES the "renders bit for bit what a filter
            // without the knobs rendered" row above, deliberately: that row is the identity claim
            // and this one is the fill's negative case, and they would be deleted for different
            // reasons. Do not fold them together without checking both clues.
            renderRaw { it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707)) } shouldBe render(plain)

            // ...and through a passes cascade, which the single-stage oracle does not reach.
            //
            // Honest about what this oracle IS: it calls the same `butterworthQLadder` the
            // runtime calls, so a wrong ladder would agree with itself here and this row would
            // not see it. That is fine for the clue it carries, which is envelope ABSENCE through
            // a cascade, not the ladder's values; the ladder is pinned against literals in
            // `PassesCascadeSpec`.
            val ladder = butterworthQLadder(2, 1.0)

            renderRaw {
                it
                    .lowpass(ConstantIgnitor(800.0), ConstantIgnitor(1.2 * ladder[0]))
                    .lowpass(ConstantIgnitor(800.0), ConstantIgnitor(1.2 * ladder[1]))
            } shouldBe render(saw.lowpass(800.0, q = 1.2, passes = 2))
        }
    }

    "the fill and the envelope reach the other three filter kinds, not just the lowpass" {
        // Four nodes, four build arms, four door pairs: a row that only ever renders a lowpass
        // cannot see an arm that forgot to pass the envelope through.
        val plainHp = saw.highpass(400.0)
        val plainBp = saw.bandpass(1200.0)
        val plainNt = saw.notch(1200.0)

        withClue("highpass") {
            render(saw.highpass(400.0, decaySec = 0.3, sustainLevel = 0.2)) shouldNotBe render(plainHp)
            render(saw.highpass(400.0, env = 0.0, decaySec = 0.3, sustainLevel = 0.2)) shouldBe render(plainHp)
        }

        withClue("bandpass") {
            render(saw.bandpass(1200.0, decaySec = 0.3, sustainLevel = 0.2)) shouldNotBe render(plainBp)
            render(saw.bandpass(1200.0, env = 0.0, decaySec = 0.3, sustainLevel = 0.2)) shouldBe render(plainBp)
        }

        withClue("notch") {
            render(saw.notch(1200.0, decaySec = 0.3, sustainLevel = 0.2)) shouldNotBe render(plainNt)
            render(saw.notch(1200.0, env = 0.0, decaySec = 0.3, sustainLevel = 0.2)) shouldBe render(plainNt)
        }

        withClue("humanize draws on every kind, and only when analog is above 0") {
            drawsTakenBy(saw.highpass(400.0, analog = 4.0, humanize = true)) shouldBe 4
            drawsTakenBy(saw.bandpass(1200.0, analog = 4.0, humanize = true)) shouldBe 4
            drawsTakenBy(saw.notch(1200.0, analog = 4.0, humanize = true)) shouldBe 4
            drawsTakenBy(saw.highpass(400.0, analog = 0.0, humanize = true)) shouldBe 0
            drawsTakenBy(saw.bandpass(1200.0, analog = 0.0, humanize = true)) shouldBe 0
            drawsTakenBy(saw.notch(1200.0, analog = 0.0, humanize = true)) shouldBe 0
        }

        withClue("the tolerance reaches a BANDPASS too, where analog's saturation does not") {
            // The node KDoc's claim that `analog` is not inert on the taps without saturation.
            val tolerance = FilterHumanization(cutoffOffsetMul = 1.25, drift = null)

            renderRaw { it.bandpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707), humanize = tolerance) } shouldBe
                renderRaw { it.bandpass(ConstantIgnitor(1000.0), ConstantIgnitor(0.707)) }
        }
    }

    "env = 0 written EXPLICITLY is the node's switch: the four stage knobs are then inert" {
        val plain = saw.lowpass(800.0)
        val offWithOddStages =
            saw.lowpass(800.0, env = 0.0, attackSec = 0.9, decaySec = 0.7, sustainLevel = 0.2, releaseSec = 1.3)

        withClue("a written zero depth is bit for bit the filter with no envelope at all") {
            render(offWithOddStages) shouldBe render(plain)
        }

        withClue("the same stage knobs at a depth DO change it (the control)") {
            val swept = saw.lowpass(
                800.0, env = 24.0,
                attackSec = 0.9, decaySec = 0.7, sustainLevel = 0.2, releaseSec = 1.3,
            )

            render(swept) shouldNotBe render(plain)
            // ...and a different depth is a different sweep, so `env` is not merely a flag.
            render(saw.lowpass(800.0, env = 12.0)) shouldNotBe render(saw.lowpass(800.0, env = 24.0))
        }
    }

    "each stage knob moves the sweep on its own" {
        val base = saw.lowpass(800.0, env = 24.0)

        withClue("attack") { render(saw.lowpass(800.0, env = 24.0, attackSec = 0.5)) shouldNotBe render(base) }
        withClue("decay, which needs a sustain BELOW 1 or it has nothing to fall to") {
            // At the default sustain of 1.0 the decay segment runs from the peak to the peak, so
            // `decaySec` alone is inaudible and a row that compared it against `base` would pass
            // for the wrong reason (review checklist item 10).
            render(saw.lowpass(800.0, env = 24.0, sustainLevel = 0.2, decaySec = 0.5)) shouldNotBe
                render(saw.lowpass(800.0, env = 24.0, sustainLevel = 0.2))
        }
        withClue("sustain") { render(saw.lowpass(800.0, env = 24.0, sustainLevel = 0.1)) shouldNotBe render(base) }
        withClue("release") { render(saw.lowpass(800.0, env = 24.0, releaseSec = 0.9)) shouldNotBe render(base) }
    }

    "a SLOT drives the depth, and an unwritten slot is the same as no envelope" {
        val slotted = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Constant(800.0),
            env = IgnitorDsl.Param("lpenv", SLOT_UNSET),
        )

        withClue("unset slot: no envelope, bit for bit the plain filter") {
            render(slotted) shouldBe render(saw.lowpass(800.0))
        }

        withClue("written slot: the sweep engages") {
            render(slotted, mapOf("lpenv" to 24.0)) shouldNotBe render(slotted)
        }
    }

    "a non-leaf STAGE knob is unreadable at build and takes its constant" {
        // Deliberate and documented (`filterEnvDef`): the envelope is a per-voice constant on the
        // strip too. What matters here is that it does not become NaN or silence.
        val modulated = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Constant(800.0),
            env = IgnitorDsl.Constant(24.0),
            attackSec = IgnitorDsl.PerlinNoise(rate = IgnitorDsl.Constant(2.0)),
        )

        render(modulated) shouldBe render(saw.lowpass(800.0, env = 24.0))
    }

    "a non-leaf DEPTH is unreadable too, and there it switches the whole envelope OFF" {
        // The sharp edge of the same rule, pinned so it is a known shape and not a surprise: a
        // stage knob falls back to a usable time, the DEPTH falls back to the off value.
        val expressionDepth = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Constant(800.0),
            env = IgnitorDsl.Max(IgnitorDsl.Param("lpenv", 24.0), IgnitorDsl.Constant(36.0)),
            decaySec = IgnitorDsl.Constant(0.3),
            sustainLevel = IgnitorDsl.Constant(0.2),
        )

        render(expressionDepth) shouldBe render(saw.lowpass(800.0))
    }

    "a non-leaf ANALOG leaves the humanize lane unbuilt, and draws nothing" {
        val expressionAnalog = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Constant(800.0),
            analog = IgnitorDsl.Max(IgnitorDsl.Param("analog", 4.0), IgnitorDsl.Constant(8.0)),
            humanize = true,
        )

        drawsTakenBy(expressionAnalog) shouldBe 0
    }

    // ── The gate is untouched by the new knobs ───────────────────────────────────────────────

    "the gate still fires on an UNSET cutoff and on nothing else, envelope or not" {
        val gatedPlain = IgnitorDsl.Lowpass(inner = saw, freq = IgnitorDsl.Param("lpf", SLOT_UNSET))
        val gatedSwept = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Param("lpf", SLOT_UNSET),
            env = IgnitorDsl.Constant(24.0),
            humanize = true,
            analog = IgnitorDsl.Constant(4.0),
        )

        withClue("an unset cutoff gates, with an envelope and a humanize lane on the node") {
            shapeOf(build(gatedSwept)) shouldBe bareShape
            render(gatedSwept) shouldBe bare
        }

        withClue("...and a written cutoff still builds") {
            shapeOf(build(gatedSwept, mapOf("lpf" to 800.0))) shouldNotBe bareShape
        }

        withClue("an envelope alone never keeps a gated filter alive") {
            render(gatedSwept) shouldBe render(gatedPlain)
        }
    }

    "a gated-off filter takes its humanize DRAWS with it, the way it takes its knobs" {
        val gated = IgnitorDsl.Lowpass(
            inner = saw,
            freq = IgnitorDsl.Param("lpf", SLOT_UNSET),
            analog = IgnitorDsl.Constant(4.0),
            humanize = true,
        )

        withClue("gated off: no draws at all") { drawsTakenBy(gated) shouldBe 0 }
        withClue("cutoff written: the four draws are taken") {
            drawsTakenBy(gated, mapOf("lpf" to 800.0)) shouldBe 4
        }
    }

    // ── The humanize lane and its DRAW ORDER ────────────────────────────────────────────────

    "humanize draws exactly what VoiceFactory draws per filter, in that order" {
        val humanized = saw.lowpass(800.0, analog = 4.0, humanize = true)

        withClue("one nextDouble for the tolerance, then AnalogDrift's two doubles and one int") {
            drawsTakenBy(humanized) shouldBe 4
        }

        withClue("the tolerance is the SAME number the strip would draw") {
            // Same function, same stream position: `perVoiceCutoffOffsetMul` has one home and
            // both callers reach it. A second copy of the expression is what this row forbids.
            val replay = seed()
            val expected = perVoiceCutoffOffsetMul(4.0, FILTER_CUTOFF_OFFSET_PER_ANALOG, replay)

            buildFilterHumanization(4.0, sampleRate, blockFrames, seed())!!
                .cutoffOffsetMul shouldBe expected
        }
    }

    "humanize at analog 0, at a non-finite analog, and switched off draws NOTHING" {
        withClue("analog 0") { drawsTakenBy(saw.lowpass(800.0, analog = 0.0, humanize = true)) shouldBe 0 }
        withClue("analog unset") {
            val unsetAnalog = IgnitorDsl.Lowpass(
                inner = saw,
                freq = IgnitorDsl.Constant(800.0),
                analog = IgnitorDsl.Param("analog", SLOT_UNSET),
                humanize = true,
            )

            drawsTakenBy(unsetAnalog) shouldBe 0
        }
        withClue("humanize off at analog 4") { drawsTakenBy(saw.lowpass(800.0, analog = 4.0)) shouldBe 0 }
    }

    "a passes cascade draws ONCE, not once per stage" {
        // The strip's cascade is ONE AudioFilter with N stages: one tolerance, one drift lane,
        // one multiplier per block. Three stages drawing three tolerances would be a different
        // filter AND a shifted stream.
        drawsTakenBy(saw.lowpass(800.0, passes = 3, analog = 4.0, humanize = true)) shouldBe 4
    }

    "humanize changes the sound at analog > 0 and nothing at all at analog 0" {
        withClue("engaged") {
            render(saw.lowpass(800.0, analog = 4.0, humanize = true)) shouldNotBe
                render(saw.lowpass(800.0, analog = 4.0))
        }

        withClue("at analog 0 it is exactly the same filter, bit for bit") {
            render(saw.lowpass(800.0, analog = 0.0, humanize = true)) shouldBe
                render(saw.lowpass(800.0, analog = 0.0))
        }
    }

    "the fixed cutoff tolerance reaches the COEFFICIENTS, not just the object" {
        // A lane-free humanization isolates the tolerance from the drift. Without this row a
        // tolerance that never multiplied the cutoff would still pass the engagement row above,
        // because the drift alone moves the sound.
        val tolerance = FilterHumanization(cutoffOffsetMul = 1.25, drift = null)

        // 800 * 1.25 is exactly 1000 in binary, so the claim is an exact bit equality.
        renderRaw { it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707), humanize = tolerance) } shouldBe
            renderRaw { it.lowpass(ConstantIgnitor(1000.0), ConstantIgnitor(0.707)) }

        withClue("...and it is NOT the same as the untouched 800 Hz filter") {
            renderRaw { it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707), humanize = tolerance) } shouldNotBe
                renderRaw { it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707)) }
        }

        withClue("the ENVELOPE path carries the tolerance too") {
            // Not an exact-equality claim here, deliberately: with a sweep the tolerance
            // multiplies AFTER `base * 2^(depth/12 * env)`, so `(800 * 2^x) * 1.25` and
            // `1000 * 2^x` round differently. What has to hold is that the tolerance is ON
            // that path at all.
            val swept = FilterEnvDef(depth = 24.0, attackSec = 0.01, decaySec = 0.2, sustainLevel = 0.3, releaseSec = 0.1)

            renderRaw {
                it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707), swept, humanize = tolerance)
            } shouldNotBe renderRaw { it.lowpass(ConstantIgnitor(800.0), ConstantIgnitor(0.707), swept) }
        }
    }

    "the drift lane is stepped once per block, not once per cascade stage" {
        // Two stages sharing one lane must see ONE multiplier per block. If each stage stepped
        // it, the second stage would filter at a different cutoff than the first and the render
        // would differ from a lane that is stepped once and held.
        val lane = buildFilterHumanization(4.0, sampleRate, blockFrames, seed())!!
        val context = ctx(seed())

        val first = lane.blockDriftMultiplier(context)
        val again = lane.blockDriftMultiplier(context)

        withClue("same block, same multiplier") { again shouldBe first }

        context.voiceElapsedFrames += blockFrames

        withClue("next block, it moved") { lane.blockDriftMultiplier(context) shouldNotBe first }
    }
})
