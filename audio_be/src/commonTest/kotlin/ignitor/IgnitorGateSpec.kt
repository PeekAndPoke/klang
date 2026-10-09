/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.constants.ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_SEMITONES
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.pregain
import io.peekandpoke.klang.audio_bridge.shape
import kotlin.math.sin
import kotlin.random.Random

/**
 * Which of the ADSR's four time and level knobs survive a NON-FINITE value, and by WHAT.
 *
 * Two different mechanisms, and the difference is the point of the row that uses this:
 *
 *  - `sustain` survives BY GUARD, added 2026-09-20 in `AdsrIgnitor`'s `finiteOr`. Without it
 *    `coerceIn(0.0, 1.0)` is the identity on a NaN and the level multiplies every sample, so it
 *    puts NaN into the orbit mix. That mattered the moment the unity-`mul` fold landed: before it,
 *    a `pregain` at 1.0 after an envelope kept `TimesIgnitor`'s scrub and the voice went silent
 *    instead. (`expK` was the second guarded knob until step 3c removed it.)
 *  - `attack`, `decay` and `release` survive BY CONVERSION, and by accident: a time
 *    becomes a frame count through `(seconds * sampleRate).toInt()`, and `Double.toInt()` of a NaN
 *    is 0 on both platforms, so a NaN-timed stage simply has no frames. `declick` is the
 *    same shape (`> 0.0` fails for a NaN). Nobody should rely on it: `release` was NOT safe in
 *    its other half, the voice's release TAIL, which is its own row below.
 *
 * So the set is all four, and the row asserts that with the reason attached rather than a number.
 */
private val NAN_SAFE_ADSR_KNOBS: Set<String> = setOf(
    "attack", "decay", "sustain", "release",
)

/**
 * THE GATE: a stage whose gating knob is a build-time constant at its OFF value is not built.
 *
 * The rule, why it exists and why it is restricted to the `Param` and `Constant` leaves are all in
 * `IgnitorDslRuntime`'s `gatedOff` KDoc; the off VALUES are one table, in
 * `audio/ref/off-values.md`. This spec is what holds both to their word.
 *
 * Every stage row is the same shape and says the same two things, because an optimisation that
 * only ever fires is as broken as one that never does:
 *
 *  - **OFF is a fold, not a change.** The gated tree renders bit for bit like the source ALONE.
 *    Raw bits, not a tolerance: the claim is that a stage is absent, and absent is exact.
 *  - **ON still builds.** The same tree with a written value renders DIFFERENTLY, so the off row
 *    cannot be passing because nothing renders.
 *
 * The rest of the rows are about something other than one stage:
 *
 *  - the NaN GUARDRAIL: a slot whose default IS the unset sentinel must not reach the DSP, and
 *    for most of these stages the gate is the only thing between the two;
 *  - the RNG STREAM: the query may not build a knob subtree in order to ask it, because the
 *    build-time draws of a `crackle` or a `perlin` are positional, and a gated-off stage takes its
 *    sibling knobs with it;
 *  - DIFFERENT GRAPHS: one instrument, two bags, two structurally different graphs. Today's build
 *    cache is created fresh per build and a build is per note-on, so nothing can reuse the wrong
 *    one; the row is here so a future cross-voice cache cannot reintroduce the hazard silently;
 *  - the RELEASE TAIL, which the gate's `mul` row made reachable: a non-finite release must not
 *    swallow a sibling's or an inner envelope's real one;
 *  - the ungated ENVELOPE's own knobs under a non-finite value, and the `mul`-slot-default rule.
 */
class IgnitorGateSpec : StringSpec({

    val blockFrames = 128
    val blocks = 4
    val freqHz = 220.0

    /** The seed every build shares, so two trees draw the same numbers at construction. */
    fun seed() = Random(7)

    fun ctx(rng: Random): IgniteContext = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = blockFrames * blocks,
        gateEndFrame = blockFrames * blocks,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
        voiceElapsedFrames = 0
    }

    fun build(
        dsl: IgnitorDsl,
        params: Map<String, Double>? = null,
        rng: Random = seed(),
        sampleSource: Ignitor? = null,
    ): Ignitor = dsl.buildExciter(ignitorParams = params, random = rng, freqHz = freqHz, sampleSource = sampleSource).ignitor

    /**
     * [blocks] blocks of [dsl], built with [params] and rendered end to end.
     *
     * ONE `Random` for the build and the context, which is what a voice gets: the build's
     * construction draws and the render's per-sample draws come off the same stream, so a row that
     * is about draw ORDER is about the stream a voice really has.
     */
    fun render(
        dsl: IgnitorDsl,
        params: Map<String, Double>? = null,
        stripPhaseMod: DoubleArray? = null,
        sample: ((Random) -> Ignitor)? = null,
    ): DoubleArray {
        val rng = seed()
        // The playhead is built BEFORE the tree, off the same stream, as `VoiceFactory` builds it.
        val sampleSource = sample?.invoke(rng)
        val ignitor = build(dsl, params, rng, sampleSource)
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            // The voice strip's pitch ratios, handed to the whole tree at the root as `IgniteRenderer` does.
            context.phaseMod = stripPhaseMod
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    /** The node right under the root memo: what the gate adds or does not add. */
    fun shapeOf(ignitor: Ignitor): Any = (ignitor as MemoizingIgnitor).inner::class

    val saw: IgnitorDsl = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)

    /** The build's release-tail finding for [this], which is what voice lifetime is ranked on. */
    fun IgnitorDsl.tail(): Double? = buildExciter(random = seed(), freqHz = freqHz).releaseTailSec

    val unsetRelease: IgnitorDsl = IgnitorDsl.Adsr(inner = saw, release = IgnitorDsl.Constant(SLOT_UNSET))
    val longRelease: IgnitorDsl = IgnitorDsl.Adsr(inner = saw, release = IgnitorDsl.Constant(2.0))

    /** The oracle for every OFF row: the source with no stage on it at all. */
    val bare = render(saw).bits()
    val bareShape = shapeOf(build(saw))

    /**
     * One stage row: [off] values must fold away, [on] must not. [stage] hangs the stage on [saw]
     * with the knob value it is handed.
     *
     * Both the SAMPLES and the GRAPH, because for three of these stages the runtime ALSO bypasses
     * at some of the off values, so samples alone would pass with no gate at all (coarse at 1.0 is
     * an exact copy at render time, crush below 1.0 likewise). The graph is where "not built"
     * is a fact rather than a coincidence; the bits are where "fold, not change" is.
     */
    fun stageRow(name: String, off: List<Double>, on: Double, stage: (IgnitorDsl.Constant) -> IgnitorDsl) {
        for (value in off) {
            val tree = stage(IgnitorDsl.Constant(value))

            withClue("$name at $value must not be built") {
                shapeOf(build(tree)) shouldBe bareShape
                render(tree).bits() shouldBe bare
            }
        }

        val onTree = stage(IgnitorDsl.Constant(on))

        withClue("$name at $on must still build") {
            shapeOf(build(onTree)) shouldNotBe bareShape
            render(onTree).bits() shouldNotBe bare
        }
    }

    // ── The stage rows, one per row of the off-value table ───────────────────────────────────

    "coarse: at or below 1.0 is not built, above it is" {
        stageRow("coarse", listOf(1.0, 0.0, -4.0, SLOT_UNSET), on = 4.0) {
            IgnitorDsl.Coarse(inner = saw, amount = it)
        }
    }

    "crush: BELOW 1.0 is not built, at or above it is" {
        // Not 0: the renderer bypasses below two levels, and the Double door returns the inner
        // below 1.0. 1.0 itself is ON, one bit of the table that is easy to get wrong.
        stageRow("crush", listOf(0.999, 0.0, -4.0, SLOT_UNSET), on = 8.0) {
            IgnitorDsl.Crush(inner = saw, amount = it)
        }

        withClue("crush at exactly 1.0 is ON, and audibly so") {
            // `CrushCore.halfLevels` engages at `amount >= 1.0`, so amount 1 is exactly TWO levels:
            // the quantizer RUNS with `halfLevels = 1.0`, and under the floor law (D1, step 4) a saw
            // comes out a two-level pulse, -1 on its negative half and 0 on its positive half. The
            // boundary is `< 1.0`, not `<= 1.0`, and both halves say so.
            val atOne = IgnitorDsl.Crush(inner = saw, amount = IgnitorDsl.Constant(1.0))

            shapeOf(build(atOne)) shouldNotBe bareShape
            render(atOne).bits() shouldNotBe bare
        }
    }

    "distort: at or below 0.0 is not built, above it is" {
        // The fused node (classic()'s distort stage, the strip's law since step 4): neither authoring
        // door builds it (both spell `distort` as `Shape(Drive(...))`, which `drive` below guards),
        // and `classic()` relies on this gate to switch its distort stage off.
        stageRow("distort", listOf(0.0, -1.0, SLOT_UNSET), on = 0.5) {
            IgnitorDsl.Distort(inner = saw, amount = it)
        }
    }

    "drive: at or below 0.0 is not built, above it is" {
        // THE row both authoring doors reach. At or below 0 it is a true fold (`DriveIgnitor`
        // already copies its input through unchanged); at a non-finite amount it closes a hole,
        // which the guardrail row below is about.
        stageRow("drive", listOf(0.0, -1.0, SLOT_UNSET), on = 0.5) {
            IgnitorDsl.Drive(inner = saw, amount = it)
        }
    }

    "shape is NOT gated: it has no amount knob to read an off value from" {
        // Recorded, not a defect: `Shape` carries a transfer function and nothing else, so there
        // is no off value. Which node `classic()`'s distort stage becomes is decision D2's.
        val shaped = saw.shape("soft")

        shapeOf(build(shaped)) shouldNotBe bareShape
        render(shaped).bits() shouldNotBe bare
    }

    "tremolo: a depth at or below 0.0 is not built, above it is" {
        stageRow("tremolo", listOf(0.0, -1.0, SLOT_UNSET), on = 0.5) {
            IgnitorDsl.Tremolo(inner = saw, rate = IgnitorDsl.Constant(5.0), depth = it)
        }

        withClue("the RATE is not a gating knob: a tremolo at rate 0 is a static gain, not an absence") {
            val atRateZero = IgnitorDsl.Tremolo(
                inner = saw,
                rate = IgnitorDsl.Constant(0.0),
                depth = IgnitorDsl.Constant(1.0),
            )

            shapeOf(build(atRateZero)) shouldNotBe bareShape
            render(atRateZero).bits() shouldNotBe bare
        }
    }

    "onepole: a cutoff at or below 0.0 is not built, above it is" {
        stageRow("onepole", listOf(0.0, -1.0, SLOT_UNSET), on = 2000.0) {
            IgnitorDsl.OnePoleLowpass(inner = saw, freq = it)
        }
    }

    // ── The four pitch arms (pitch pipeline step 0, 2026-10-07) ──────────────────────────────
    //
    // A built pitch arm at its off value writes exactly 1.0 and every reader multiplies by it, so
    // the gate is a FOLD: the "ungated" rows below build the same arm with a NON-LEAF knob at the
    // off value (no build-time answer, so never gated) and must render the bits of the bare source.

    /** A knob at [value] the gate cannot read: an expression, not a leaf, so the stage is built. */
    fun ungated(value: Double): IgnitorDsl = IgnitorDsl.Constant(4.0).mul(IgnitorDsl.Constant(value / 4.0))

    fun vibrato(inner: IgnitorDsl, depth: IgnitorDsl, rate: IgnitorDsl = IgnitorDsl.Constant(5.0)) =
        IgnitorDsl.Vibrato(inner = inner, rate = rate, semitones = depth)

    fun accelerate(inner: IgnitorDsl, amount: IgnitorDsl) = IgnitorDsl.Accelerate(inner = inner, semitones = amount)

    fun pitchEnvelope(inner: IgnitorDsl, amount: IgnitorDsl) = IgnitorDsl.PitchEnvelope(inner = inner, semitones = amount)

    fun fm(inner: IgnitorDsl, depth: IgnitorDsl, modulator: IgnitorDsl = IgnitorDsl.Sine()) =
        IgnitorDsl.Fm(carrier = inner, modulator = modulator, depth = depth)

    /** The four arms, each hung on an inner with its switch knob. */
    val pitchArms: Map<String, (IgnitorDsl, IgnitorDsl) -> IgnitorDsl> = mapOf(
        "vibrato" to { inner, knob -> vibrato(inner = inner, depth = knob) },
        "accelerate" to { inner, knob -> accelerate(inner = inner, amount = knob) },
        "pitch envelope" to { inner, knob -> pitchEnvelope(inner = inner, amount = knob) },
        "fm" to { inner, knob -> fm(inner = inner, depth = knob) },
    )

    "vibrato: a FINITE depth at or below 0.0 is not built, above it is" {
        stageRow("vibrato", listOf(0.0, -0.0, -0.5), on = 0.5) { vibrato(inner = saw, depth = it) }

        withClue("the RATE is not a gating knob: a vibrato at rate 0 is still built") {
            // At rate 0 the LFO sits at phase 0 and its law writes 2^0, so the BITS are the bare saw's;
            // the graph is where "not a gating knob" shows.
            shapeOf(build(vibrato(inner = saw, depth = IgnitorDsl.Constant(1.0), rate = IgnitorDsl.Constant(0.0)))) shouldNotBe bareShape
        }
    }

    "vibrato: a NON-FINITE depth is built and renders the default depth, it is not off" {
        // The one pitch arm whose unset is not off: the runtime reads a non-finite depth as the node's
        // DEFAULT, VIBRATO_SEMITONES (`finiteOr`), so gating it would change the sound, not fold a stage.
        val atDefault = render(vibrato(inner = saw, depth = IgnitorDsl.Constant(VIBRATO_SEMITONES))).bits()

        atDefault shouldNotBe bare

        val nonFinite = listOf(
            IgnitorDsl.Constant(SLOT_UNSET),
            IgnitorDsl.Constant(Double.POSITIVE_INFINITY),
            IgnitorDsl.Constant(Double.NEGATIVE_INFINITY),
            IgnitorDsl.Param(name = "vib", default = SLOT_UNSET),
        )

        for (depth in nonFinite) {
            withClue("depth $depth") {
                shapeOf(build(vibrato(inner = saw, depth = depth))) shouldNotBe bareShape
                render(vibrato(inner = saw, depth = depth)).bits() shouldBe atDefault
            }
        }
    }

    "accelerate: exactly 0.0 is not built, any other amount is" {
        stageRow("accelerate", listOf(0.0, -0.0, SLOT_UNSET, Double.POSITIVE_INFINITY), on = 12.0) {
            accelerate(inner = saw, amount = it)
        }

        withClue("a NEGATIVE amount glides down and is built") {
            shapeOf(build(accelerate(inner = saw, amount = IgnitorDsl.Constant(-12.0)))) shouldNotBe bareShape
            render(accelerate(inner = saw, amount = IgnitorDsl.Constant(-12.0))).bits() shouldNotBe bare
        }
    }

    "pitch envelope: an amount of exactly 0.0 is not built, any other amount is" {
        stageRow("pitch envelope", listOf(0.0, -0.0, SLOT_UNSET, Double.NEGATIVE_INFINITY), on = 12.0) {
            pitchEnvelope(inner = saw, amount = it)
        }

        withClue("a NEGATIVE amount sweeps from below and is built") {
            shapeOf(build(pitchEnvelope(inner = saw, amount = IgnitorDsl.Constant(-12.0)))) shouldNotBe bareShape
            render(pitchEnvelope(inner = saw, amount = IgnitorDsl.Constant(-12.0))).bits() shouldNotBe bare
        }
    }

    "fm: a depth of exactly 0.0 is not built, any other depth is" {
        stageRow("fm", listOf(0.0, -0.0, SLOT_UNSET), on = 200.0) { fm(inner = saw, depth = it) }

        withClue("a NEGATIVE depth renders on both hosts and is built") {
            shapeOf(build(fm(inner = saw, depth = IgnitorDsl.Constant(-200.0)))) shouldNotBe bareShape
            render(fm(inner = saw, depth = IgnitorDsl.Constant(-200.0))).bits() shouldNotBe bare
        }
    }

    "the four pitch arms UNGATED at their off value render the bare source: the gate is a fold" {
        for ((name, arm) in pitchArms) {
            val built = arm(saw, ungated(0.0))

            withClue("$name, ungated at 0") {
                shapeOf(build(built)) shouldNotBe bareShape
                render(built).bits() shouldBe bare
            }
        }

        withClue("vibrato ungated at a NEGATIVE depth, its other off value") {
            render(vibrato(inner = saw, depth = ungated(-0.5))).bits() shouldBe bare
        }
    }

    "the fold holds for every pitched source family, the sample playhead included" {
        // Each reader multiplies by the ratio: the oscillators' `phaseInc * phaseMod[i]`, the sample's
        // `rate * phaseMod[i]`, the Karplus loop's `dl / phaseMod[i]`. At exactly 1.0 that is the identity.
        val ramp = DoubleArray(4096) { (it % 512) / 256.0 - 1.0 }
        val playhead: (Random) -> Ignitor = { rng ->
            SampleIgnitor(
                pcm = ramp,
                rate = 0.73,
                playhead = 0.0,
                loopStart = 0.0,
                loopEnd = 0.0,
                isLooping = false,
                stopFrame = ramp.size.toDouble(),
                sampleRate = 48000,
                rng = rng,
            )
        }

        val sources: Map<String, IgnitorDsl> = mapOf(
            "sine" to IgnitorDsl.Sine(),
            "sine, analog 0.5" to IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.5)),
            "square" to IgnitorDsl.Square(),
            "tri" to IgnitorDsl.Tri(),
            "supersaw" to IgnitorDsl.SuperSaw(),
            "pluck" to IgnitorDsl.Pluck(),
            "sample" to IgnitorDsl.Sample,
        )

        /** The sources that draw off the voice's stream at RENDER, after the fm modulator's first block. */
        val drawsAtRender = setOf("sine, analog 0.5", "supersaw", "pluck")

        for ((sourceName, source) in sources) {
            val sample = if (source == IgnitorDsl.Sample) playhead else null
            val alone = render(source, sample = sample).bits()

            for ((armName, arm) in pitchArms) {
                withClue("$armName over $sourceName") {
                    render(arm(source, IgnitorDsl.Constant(0.0)), sample = sample).bits() shouldBe alone

                    if (armName == "fm" && sourceName in drawsAtRender) {
                        // The fm seed consequence (its own row below): the ungated modulator seeds its drift
                        // lane first, so a source that draws at render starts from other numbers.
                        render(arm(source, ungated(0.0)), sample = sample).bits() shouldNotBe alone
                    } else {
                        render(arm(source, ungated(0.0)), sample = sample).bits() shouldBe alone
                    }
                }
            }
        }
    }

    "the fold holds under the strip's own ratios, which the tree's mods multiply into" {
        // `ModApplyingIgnitor` writes `treeMod * phaseMod`: at a tree mod of 1.0, the strip's ratio itself.
        val strip = DoubleArray(blockFrames) { 1.0 + 0.01 * sin(it * 0.05) }
        val bareUnderStrip = render(saw, stripPhaseMod = strip).bits()

        withClue("engagement: the strip's ratios move the saw") {
            bareUnderStrip shouldNotBe bare
        }

        for ((name, arm) in pitchArms) {
            withClue("$name under the strip") {
                render(arm(saw, ungated(0.0)), stripPhaseMod = strip).bits() shouldBe bareUnderStrip
                render(arm(saw, IgnitorDsl.Constant(0.0)), stripPhaseMod = strip).bits() shouldBe bareUnderStrip
            }
        }
    }

    "a gated OUTER pitch arm leaves a built inner one alone: the product folds" {
        // `combineMods` multiplies the outer mod into the inner's: at an outer 1.0 the inner's ratios exactly.
        val inner = vibrato(inner = saw, depth = IgnitorDsl.Constant(0.5))
        val innerAlone = render(inner).bits()

        innerAlone shouldNotBe bare

        for ((name, arm) in pitchArms) {
            withClue("$name around a built vibrato") {
                render(arm(inner, IgnitorDsl.Constant(0.0))).bits() shouldBe innerAlone
                render(arm(inner, ungated(0.0))).bits() shouldBe innerAlone
            }
        }
    }

    // ── The pitch arms' named consequences: the gated tree is the tree WITHOUT the node ──────

    "a gated pitch arm's knobs are not built, so a drawing knob there takes no draws" {
        // The sibling row above, for the pitch arms: a `perlin` vibrato rate, and a `crackle` FM
        // modulator, stop drawing, and the crackle after them renders as in a tree without the stage.
        val crackle = IgnitorDsl.Crackle()
        val never = IgnitorDsl.Plus(left = IgnitorDsl.Silence, right = crackle)

        fun withStage(stage: IgnitorDsl) = IgnitorDsl.Plus(left = stage, right = crackle)

        val drawingRate = vibrato(inner = IgnitorDsl.Silence, depth = IgnitorDsl.Constant(0.0), rate = IgnitorDsl.PerlinNoise())
        val drawingModulator = fm(inner = IgnitorDsl.Silence, depth = IgnitorDsl.Constant(0.0), modulator = IgnitorDsl.Crackle())

        render(withStage(drawingRate)).bits() shouldBe render(never).bits()
        render(withStage(drawingModulator)).bits() shouldBe render(never).bits()

        withClue("engagement: ungated, each one draws and the crackle moves") {
            val ungatedRate = vibrato(inner = IgnitorDsl.Silence, depth = ungated(0.0), rate = IgnitorDsl.PerlinNoise())
            val ungatedModulator = fm(inner = IgnitorDsl.Silence, depth = ungated(0.0), modulator = IgnitorDsl.Crackle())

            render(withStage(ungatedRate)).bits() shouldNotBe render(never).bits()
            render(withStage(ungatedModulator)).bits() shouldNotBe render(never).bits()
        }
    }

    "a gated fm no longer counts its modulator's release tail" {
        // The FM arm counts the modulator's tail (`maxTail(carrier, modulator)`); gated, there is no modulator.
        val longModulator = IgnitorDsl.Adsr(inner = IgnitorDsl.Sine(), release = IgnitorDsl.Constant(2.0))

        fm(inner = saw, depth = IgnitorDsl.Constant(0.0), modulator = longModulator).tail() shouldBe null

        withClue("engagement: built, at a written depth or an ungated 0, the tail counts") {
            fm(inner = saw, depth = IgnitorDsl.Constant(200.0), modulator = longModulator).tail() shouldBe 2.0
            fm(inner = saw, depth = ungated(0.0), modulator = longModulator).tail() shouldBe 2.0
        }
    }

    "a gated fm's modulator takes no drift seed, so a later draw of the voice stays in place" {
        // The modulator `Sine` seeds its drift lane off the voice's stream on its first block, at analog 0
        // too. Gated, it never renders, and a noise layer after it draws as in a tree without the stage.
        val noise = IgnitorDsl.WhiteNoise()
        val never = IgnitorDsl.Plus(left = saw, right = noise)

        render(IgnitorDsl.Plus(left = fm(inner = saw, depth = IgnitorDsl.Constant(0.0)), right = noise)).bits() shouldBe
                render(never).bits()

        withClue("engagement: ungated at depth 0 the modulator renders, draws, and the noise moves") {
            render(IgnitorDsl.Plus(left = fm(inner = saw, depth = ungated(0.0)), right = noise)).bits() shouldNotBe
                    render(never).bits()
        }
    }

    "a gated pitch arm's inner SHARES the build of the same node elsewhere, as `s + s` does" {
        // A built pitch arm builds its inner under a new mod, so the inner is its own instance. Gated, the
        // walk descends with the unchanged mod and the inner hits the cache entry of the same node outside
        // it: one instance, read twice (D13: one `let` is one signal). A supersaw is the strong case: each
        // instance draws its own per-voice dice, so two instances are two different sounds and one doubled is
        // not (+4.6 dB through the engine, audio review of step 0). An analog saw would show it in the bits only.
        val s = IgnitorDsl.SuperSaw()
        val twice = render(IgnitorDsl.Plus(left = s, right = s)).bits()

        for ((name, arm) in pitchArms) {
            withClue("$name gated: the tree without the node") {
                render(IgnitorDsl.Plus(left = s, right = arm(s, IgnitorDsl.Constant(0.0)))).bits() shouldBe twice
            }

            withClue("$name ungated: two instances, which is what the gate changes") {
                render(IgnitorDsl.Plus(left = s, right = arm(s, ungated(0.0)))).bits() shouldNotBe twice
            }
        }
    }

    "a gated pitch arm INSIDE a built one descends with the outer mod: the outer still bends the source" {
        // The shape `classic()` builds from step 1 on (vibrato outermost, fm innermost): an unwritten inner stage
        // must fold away WITHOUT dropping the mod of the written stages around it.
        val outerAlone = render(vibrato(inner = saw, depth = IgnitorDsl.Constant(0.5))).bits()

        outerAlone shouldNotBe bare

        for ((name, arm) in pitchArms) {
            withClue("$name gated at 0 inside a built vibrato") {
                val nested = vibrato(inner = arm(saw, IgnitorDsl.Constant(0.0)), depth = IgnitorDsl.Constant(0.5))

                render(nested).bits() shouldBe outerAlone
            }
        }
    }

    "a gated outer pitch arm leaves the freq key off the mods inside it" {
        // The fifth named consequence. `combineMods` keeps a mod's freq key when its knobs read `Freq` (fm's
        // `freq` and its default modulator do; so does a vibrato rate written over `Freq`) and hands that key to
        // every pitch mod inside it. A detuned layer under the inner mod then renders it a SECOND time per block
        // (residue 2 of the shared-modulator record), and its LFO runs double. Gated, the outer node is absent,
        // the inner memo is freq-invariant and renders once: the tree without the node.
        val layered = IgnitorDsl.Plus(left = saw, right = IgnitorDsl.Detune(inner = saw, semitones = IgnitorDsl.Constant(7.0)))
        val inner = vibrato(inner = layered, depth = IgnitorDsl.Constant(0.5))
        val without = render(inner).bits()
        val freqRate = IgnitorDsl.Times(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(0.01))

        withClue("fm gated: the tree without the node") {
            render(fm(inner = inner, depth = IgnitorDsl.Constant(0.0))).bits() shouldBe without
        }

        withClue("fm ungated at 0: the inner vibrato renders twice per block under the detune") {
            render(fm(inner = inner, depth = ungated(0.0))).bits() shouldNotBe without
        }

        withClue("a vibrato whose rate reads the note, gated and ungated") {
            render(vibrato(inner = inner, depth = IgnitorDsl.Constant(0.0), rate = freqRate)).bits() shouldBe without
            render(vibrato(inner = inner, depth = ungated(0.0), rate = freqRate)).bits() shouldNotBe without
        }
    }

    "mul: EXACTLY 1.0 is not built, any other factor is" {
        stageRow("mul", listOf(1.0), on = 0.5) { saw.mul(it) }

        withClue("the left side is asked too") {
            val leftUnity = IgnitorDsl.Times(left = IgnitorDsl.Constant(1.0), right = saw)

            shapeOf(build(leftUnity)) shouldBe bareShape
            render(leftUnity).bits() shouldBe bare
        }

        // The one deliberate asymmetry of the table: unset is NOT off for a multiply, because
        // `TimesIgnitor` sanitises a non-finite factor to an exact zero itself and because the
        // node also sits in parameter positions where that zero is the point.
        withClue("an unset factor is NOT folded away, it silences") {
            val silenced = render(saw.mul(IgnitorDsl.Constant(SLOT_UNSET)))

            silenced.bits() shouldNotBe bare
            silenced.all { it == 0.0 } shouldBe true
        }
    }

    "mul: the optimizer's Affine form of a bare multiply folds at unity too" {
        // Every registered tree renders OPTIMIZED, and the pass rewrites `x.mul(k)` into an
        // Affine with an absent pre-add and an absent add. Without this arm the `mul` row could
        // not fire on the shipping path at all.
        fun affine(mul: Double) = IgnitorDsl.Affine(
            inner = saw,
            pre = IgnitorDsl.Constant(-0.0),
            mul = IgnitorDsl.Constant(mul),
            add = IgnitorDsl.Constant(-0.0),
        )

        shapeOf(build(affine(1.0))) shouldBe bareShape
        render(affine(1.0)).bits() shouldBe bare
        shapeOf(build(affine(0.5))) shouldNotBe bareShape
        render(affine(0.5)).bits() shouldNotBe bare

        withClue("an Affine that also carries an add is NOT a bare multiply and stays") {
            val withAdd = IgnitorDsl.Affine(
                inner = saw,
                pre = IgnitorDsl.Constant(-0.0),
                mul = IgnitorDsl.Constant(1.0),
                add = IgnitorDsl.Constant(0.25),
            )

            shapeOf(build(withAdd)) shouldNotBe bareShape
            render(withAdd).bits() shouldNotBe bare
        }

        // A POSITIVE zero pre-add is NOT the absent one: `v + 0.0` turns a `-0.0` sample into
        // `+0.0`, so folding it away would not be the identity. A saw never renders a `-0.0`, so
        // this one has to be read off the GRAPH rather than off the samples, which is also the
        // honest shape of the claim, since the claim is "the node is still there".
        withClue("a POSITIVE zero pre-add is not the absent one and stays") {
            val stillThere = build(
                IgnitorDsl.Affine(
                    inner = saw,
                    pre = IgnitorDsl.Constant(0.0),
                    mul = IgnitorDsl.Constant(1.0),
                    add = IgnitorDsl.Constant(-0.0),
                ),
            )

            shapeOf(stillThere) shouldNotBe shapeOf(build(saw))
        }
    }

    "mul: the shape the OPTIMIZER really emits for a bare multiply is the shape the arm gates" {
        // The row above hand-builds the `-0.0` encoding, which proves the arm but not that the
        // arm can ever fire. Every registered tree renders optimized, so this is where the `mul`
        // row earns its place: the pass's own output for `x.mul(1.0)` must gate.
        val optimized = saw.mul(IgnitorDsl.Constant(1.0)).optimize()

        optimized.shouldBeInstanceOf<IgnitorDsl.Affine>()
        shapeOf(build(optimized)) shouldBe bareShape
        render(optimized).bits() shouldBe bare

        withClue("engagement: the same route at 0.5 keeps its node") {
            val half = saw.mul(IgnitorDsl.Constant(0.5)).optimize()

            half.shouldBeInstanceOf<IgnitorDsl.Affine>()
            shapeOf(build(half)) shouldNotBe bareShape
        }
    }

    "the unity fold never takes a CONTROL-RATE survivor: there the multiply's clamp does work" {
        // In a parameter position the survivor is a coefficient, and `safeOut` is what keeps a
        // non-finite one inside the engine's range. `param("res", +Inf).mul(1)` resolved to
        // SAFE_MAX and then to the filter's q ceiling; folded, it would stay +Inf and land on the
        // q fallback instead, which is a different filter.
        val res = IgnitorDsl.Param("res", Double.POSITIVE_INFINITY)

        withClue("Times form") {
            val coefficient = IgnitorDsl.Times(left = res, right = IgnitorDsl.Param("pregain", 1.0))

            build(coefficient).controlRateValueOrNull(freqHz)?.isFinite() shouldBe true
        }

        withClue("Affine form") {
            val coefficient = IgnitorDsl.Affine(
                inner = res,
                pre = IgnitorDsl.Constant(-0.0),
                mul = IgnitorDsl.Constant(1.0),
                add = IgnitorDsl.Constant(-0.0),
            )

            build(coefficient).controlRateValueOrNull(freqHz)?.isFinite() shouldBe true
        }

        withClue("engagement: a SIGNAL survivor still folds, which is the pregain case") {
            shapeOf(build(saw.mul(IgnitorDsl.Constant(1.0)))) shouldBe bareShape
        }
    }

    "a gated-off stage draws exactly like a tree that never had it, SIBLING knobs included" {
        // Recorded, and chosen. The leaf restriction keeps the QUERY from moving build-time draws;
        // it cannot keep the stage's siblings alive, because a stage that is not built has no
        // siblings. A `perlin` in a gated tremolo's rate therefore stops drawing and every later
        // drawing node shifts. The alternative is worse where it counts: refusing to gate a filter
        // whose cutoff is UNSET because its q happens to draw would build that filter with a NaN
        // cutoff, which `bilinearK`'s guard silently substitutes with 1 kHz.
        val crackle = IgnitorDsl.Crackle()

        val withGatedStage = IgnitorDsl.Plus(
            left = IgnitorDsl.Tremolo(
                inner = IgnitorDsl.Silence,
                rate = IgnitorDsl.PerlinNoise(),
                depth = IgnitorDsl.Constant(0.0),
            ),
            right = crackle,
        )
        val never = IgnitorDsl.Plus(left = IgnitorDsl.Silence, right = crackle)

        render(withGatedStage).bits() shouldBe render(never).bits()
    }

    "the four filters: only an UNSET cutoff is off, a number never is" {
        val unset = IgnitorDsl.Constant(SLOT_UNSET)

        val off = mapOf(
            "lowpass" to saw.lowpass(freq = unset),
            "highpass" to saw.highpass(freq = unset),
            "bandpass" to saw.bandpass(freq = unset),
            "notch" to saw.notch(freq = unset),
            "lowpass, passes = 4" to saw.lowpass(freq = unset, passes = 4),
        )

        for ((name, tree) in off) {
            withClue("$name with an unset cutoff") {
                shapeOf(build(tree)) shouldBe bareShape
                render(tree).bits() shouldBe bare
            }
        }

        // The reason the rule is "unset" and not a number: a lowpass up at the top of the band is
        // still a filter, and a numeric off value would silently delete one somebody meant.
        val on = mapOf(
            "lowpass" to saw.lowpass(freq = 2000.0),
            "highpass" to saw.highpass(freq = 2000.0),
            "bandpass" to saw.bandpass(freq = 2000.0),
            "notch" to saw.notch(freq = 2000.0),
            "lowpass at 20 kHz, which is NOT an off state" to saw.lowpass(freq = 20000.0),
        )

        for ((name, tree) in on) {
            withClue("$name is built") {
                shapeOf(build(tree)) shouldNotBe bareShape
                render(tree).bits() shouldNotBe bare
            }
        }
    }

    "an envelope is NOT gated on unset: the classic tail's ADSR is built by default" {
        // Inverted from the plan's sketch, and the spike is why: the voice strip's VCA ran (until it retired)
        // on EVERY voice with the voice envelope (`VOICE_ADSR_*`) when the pattern set nothing. What switches
        // the tail's envelope off is an explicit `adsrOff`, which `classic()` writes into `on`.
        val unsetAttack = IgnitorDsl.Adsr(inner = saw, attack = IgnitorDsl.Constant(SLOT_UNSET))

        shapeOf(build(unsetAttack)) shouldNotBe bareShape
        render(unsetAttack).bits() shouldNotBe bare
    }

    "the envelope: `on` at exactly 0.0 is not built; unset, any other number and a non-leaf are" {
        // The envelope row of the off-value table (step 3c). A FLAG, not an amount, so the house flag
        // rule decides it (`coerceFlag`, sprudel's `isTruthy`: non-zero is on) and a NEGATIVE value is
        // ON. And unset is ON: the second asymmetry after `mul`, because the classic envelope is built
        // by default. Each ON value is its own failure mode: -1.0 catches an `<= 0.0` test, the unset
        // sentinel and +Inf catch the gate's usual "non-finite is off", 0.5 a `< 1.0`.
        fun env(on: IgnitorDsl) = IgnitorDsl.Adsr(inner = saw, on = on)

        for (off in listOf(0.0, -0.0)) {
            withClue("on = $off must not be built") {
                shapeOf(build(env(IgnitorDsl.Constant(off)))) shouldBe bareShape
                render(env(IgnitorDsl.Constant(off))).bits() shouldBe bare
            }
        }

        val builtDefault = render(IgnitorDsl.Adsr(inner = saw)).bits()

        builtDefault shouldNotBe bare

        for (on in listOf(1.0, -1.0, 0.5, SLOT_UNSET, Double.POSITIVE_INFINITY)) {
            withClue("on = $on must be built, and render the default envelope") {
                shapeOf(build(env(IgnitorDsl.Constant(on)))) shouldNotBe bareShape
                render(env(IgnitorDsl.Constant(on))).bits() shouldBe builtDefault
            }
        }

        withClue("a non-leaf `on` has no build-time answer, so the envelope is built even where it is 0") {
            val expression = IgnitorDsl.Constant(4.0).mul(IgnitorDsl.Constant(0.0))

            shapeOf(build(env(expression))) shouldNotBe bareShape
        }
    }

    "the envelope's `on` as a SLOT: a pattern writing 0 switches it off, an unwritten one leaves it on" {
        // The shape `classic()` will place: a Param leaf the bag overrides per note.
        val instrument = IgnitorDsl.Adsr(inner = saw, on = IgnitorDsl.Param("adsrOn", 1.0))

        shapeOf(build(instrument, mapOf("adsrOn" to 0.0))) shouldBe bareShape
        render(instrument, mapOf("adsrOn" to 0.0)).bits() shouldBe bare

        shapeOf(build(instrument, emptyMap())) shouldNotBe bareShape
        render(instrument, emptyMap()).bits() shouldNotBe bare
    }

    "an OFF envelope keeps the release tail it would have had, the strip's `adsrOff` lifetime" {
        // The strip keeps the voice's lifetime when `adsrOff` switches its VCA to a gate, and step
        // 6's identity depends on the node doing the same: off drops the SAMPLES of the stage, not
        // the note's length. A leaf release only (see `offEnvelopeTail`).
        fun env(on: Double, release: IgnitorDsl) =
            IgnitorDsl.Adsr(inner = saw, release = release, on = IgnitorDsl.Constant(on))

        env(0.0, IgnitorDsl.Constant(2.0)).tail() shouldBe 2.0
        env(0.0, IgnitorDsl.Constant(2.0)).tail() shouldBe env(1.0, IgnitorDsl.Constant(2.0)).tail()

        withClue("a slot release reports its WRITTEN value, as on the ON path") {
            val slotted = IgnitorDsl.Adsr(
                inner = saw, release = IgnitorDsl.Param("release", 0.1), on = IgnitorDsl.Constant(0.0),
            )

            slotted.buildExciter(ignitorParams = mapOf("release" to 3.0), random = seed(), freqHz = freqHz)
                .releaseTailSec shouldBe 3.0
        }

        withClue("a non-finite release reports none, the ON path's rule") {
            env(0.0, IgnitorDsl.Constant(SLOT_UNSET)).tail() shouldBe null
        }

        withClue("a NON-LEAF release on an off envelope reports none: asking it would build it") {
            // The ON path folds this pointwise expression to 2.0; the OFF path builds no knob
            // subtree and says nothing. Pinned so the difference is a decision, not a surprise.
            val expression = IgnitorDsl.Constant(1.0).mul(IgnitorDsl.Constant(2.0))

            env(0.0, expression).tail() shouldBe null
            env(1.0, expression).tail() shouldBe 2.0
        }

        withClue("and the tail still competes with a sibling's like any other") {
            IgnitorDsl.Plus(
                left = IgnitorDsl.Adsr(inner = saw, release = IgnitorDsl.Constant(0.5)),
                right = env(0.0, IgnitorDsl.Constant(2.0)),
            ).tail() shouldBe 2.0
        }
    }

    "an OFF envelope builds none of its knob subtrees, so a drawing knob there takes no draws" {
        // The gate's recorded consequence, applied to the envelope: the stage does not exist, its
        // knobs with it. A `perlin` in TWO knobs of an off envelope (a stage time, built by the ON
        // path's `noMod`, and the de-click, built after the curves) stops drawing, and the crackle
        // after it renders exactly as in a tree that never had the envelope. Two knobs, so a build
        // of either one alone is red here.
        val crackle = IgnitorDsl.Crackle()

        fun envelope(attack: IgnitorDsl?, declick: IgnitorDsl?, on: Double) = IgnitorDsl.Plus(
            left = IgnitorDsl.Adsr(inner = IgnitorDsl.Silence, on = IgnitorDsl.Constant(on)).let {
                it.copy(attack = attack ?: it.attack, declick = declick ?: it.declick)
            },
            right = crackle,
        )
        val never = IgnitorDsl.Plus(left = IgnitorDsl.Silence, right = crackle)

        render(envelope(attack = IgnitorDsl.PerlinNoise(), declick = IgnitorDsl.PerlinNoise(), on = 0.0)).bits() shouldBe render(never).bits()

        withClue("engagement: ON, each knob's perlin draws on its own, and the crackle moves") {
            render(envelope(attack = IgnitorDsl.PerlinNoise(), declick = null, on = 1.0)).bits() shouldNotBe render(never).bits()
            render(envelope(attack = null, declick = IgnitorDsl.PerlinNoise(), on = 1.0)).bits() shouldNotBe render(never).bits()
        }
    }

    "the envelope's knobs under a NaN: all four stay finite, one of them by guard" {
        // The envelope is gated only by its `on` switch, for which unset means ON, so the gate is
        // not what stands between a NaN on one of its knobs and the DSP. One knob needed a guard of
        // its own and has one; see NAN_SAFE_ADSR_KNOBS for which survive by what.
        val unset = IgnitorDsl.Constant(SLOT_UNSET)

        val perKnob = mapOf(
            "attack" to IgnitorDsl.Adsr(inner = saw, attack = unset),
            "decay" to IgnitorDsl.Adsr(inner = saw, decay = unset),
            "sustain" to IgnitorDsl.Adsr(inner = saw, sustain = unset),
            "release" to IgnitorDsl.Adsr(inner = saw, release = unset),
        )

        val survives = perKnob.filterValues { tree -> render(tree).all { it.isFinite() } }.keys

        survives shouldBe NAN_SAFE_ADSR_KNOBS

        // What the guarded knob renders, so the substitution is pinned to a VALUE and not
        // merely to "finite": the same thing the knob's own default renders.
        withClue("a non-finite sustain renders what ADSR_SUSTAIN_LEVEL renders") {
            val atDefault = render(
                IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(ADSR_SUSTAIN_LEVEL)),
            ).bits()

            render(IgnitorDsl.Adsr(inner = saw, sustain = unset)).bits() shouldBe atDefault

            // The INFINITIES pin the substitution's PLACEMENT, which a NaN cannot: `coerceIn` is
            // the identity on a NaN, so before or after the coercion ends at the same value, but
            // it has a real answer for an infinity. `+Inf` used to sustain at the 1.0 rail and
            // `-Inf` at the 0.0 rail; substituting FIRST makes both read as unset instead, which
            // is the house rule the gate one file over applies to every knob it tests.
            render(IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(Double.POSITIVE_INFINITY)))
                .bits() shouldBe atDefault
            render(IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(Double.NEGATIVE_INFINITY)))
                .bits() shouldBe atDefault

            withClue("and the two rails are NOT what it renders, so the placement is what is pinned") {
                atDefault shouldNotBe render(
                    IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(1.0)),
                ).bits()
                atDefault shouldNotBe render(
                    IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(0.0)),
                ).bits()
            }
        }

        withClue("engagement: a DIFFERENT finite value still renders differently") {
            render(IgnitorDsl.Adsr(inner = saw, sustain = IgnitorDsl.Constant(0.2))).bits() shouldNotBe
                    render(IgnitorDsl.Adsr(inner = saw, sustain = unset)).bits()
        }
    }

    "a non-finite release cannot swallow a SIBLING's tail, in the order where it could" {
        // `maxTail` is `if (a >= b) a else b` and a NaN loses every comparison, so it wins ONLY as
        // the second argument: `maxTail(NaN, 2.0)` discards it, `maxTail(2.0, NaN)` returns it.
        // `buildRaw` accumulates the left operand first, so the swallowing shape is the one with
        // the non-finite release on the RIGHT. `VoiceFactory` then tests
        // `ignitorTailSec > resolvedAdsr.release`, false for a NaN, and cuts the voice to the
        // strip's release. The samples of a NaN-released envelope are fine, which is exactly why
        // this needs its own row.
        IgnitorDsl.Plus(left = longRelease, right = unsetRelease).tail() shouldBe 2.0

        withClue("the OTHER order is inert and is kept so nobody writes the row that way again") {
            // maxTail(null, NaN) = NaN, then maxTail(NaN, 2.0) = 2.0: the old code passed this.
            IgnitorDsl.Plus(left = unsetRelease, right = longRelease).tail() shouldBe 2.0
        }

        withClue("alone, it contributes nothing rather than a NaN") {
            unsetRelease.tail() shouldBe null
        }

        withClue("engagement: a finite release is still reported") {
            longRelease.tail() shouldBe 2.0
        }
    }

    "a non-finite release cannot swallow the INNER envelope's tail in a chain" {
        // The commoner shape, and the one a slotted tail makes: this arm builds its inner before
        // it accumulates its own release, so an outer envelope with a non-finite release lands as
        // `maxTail(2.0, NaN)` over whatever the chain below it reported.
        //
        // The filter sits BETWEEN the two envelopes on purpose. With it below the inner one the
        // `Lowpass` arm would only ever carry `null` upward and the row would say nothing about a
        // tail CROSSING an intervening effect, which is the whole shape of a tail. As written it
        // also pins that crossing: an edit to the `Lowpass` arm, or a new effect arm, that reached
        // for `noMod()` where it should use `inner.withMod()` would silently drop the inner
        // envelope's two seconds and cut every voice of that instrument short.
        val chained = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Adsr(inner = saw, release = IgnitorDsl.Constant(2.0)).lowpass(freq = 2000.0),
            release = IgnitorDsl.Constant(SLOT_UNSET),
        )

        chained.tail() shouldBe 2.0

        withClue("engagement: a finite outer release still wins when it is longer") {
            IgnitorDsl.Adsr(inner = chained, release = IgnitorDsl.Constant(5.0)).tail() shouldBe 5.0
        }
    }

    "every slot a `mul` door places has a FINITE default, because unset is not off for a multiply" {
        // The rule is stated in three places and guarded here: `mul` gates on exactly 1.0 and NOT
        // on unset, so a `mul` slot defaulting to SLOT_UNSET would silence every voice that does
        // not write it. `IgnitorDsl.pregain()` is the one door that places one today; this row
        // goes red the day its slot default changes to the sentinel, because the voice would
        // render silence where it now renders the bare saw.
        val placed = saw.pregain()

        shapeOf(build(placed)) shouldBe bareShape
        render(placed).bits() shouldBe bare

        withClue("engagement: the same door with the slot written is a real multiply") {
            render(placed, mapOf("pregain" to 0.5)).bits() shouldNotBe bare
        }
    }

    // ── The guardrail ────────────────────────────────────────────────────────────────────────

    "a slot whose DEFAULT is the unset sentinel never reaches the DSP" {
        // The hazard is one step long: the `Param` leaf reads a non-finite OVERRIDE as unset and
        // hands back the DEFAULT, so a slot defaulting to SLOT_UNSET resolves to NaN at the leaf,
        // and for a crush amount, a coarse amount, a DRIVE amount and a filter cutoff there is
        // nothing downstream that would scrub it. Without the gate's `!isFinite()` arm every one
        // of these renders NaN or a substituted cutoff into the orbit mix. `drive` is the sharp
        // one: `amt <= 0.0` is false for a NaN, so `10^(NaN * 1.2)` became the gain and every
        // sample of the voice went NaN, which is exactly the shape a step-3 distort slot
        // defaulting to the sentinel would have had.
        val unsetSlot = IgnitorDsl.Param(name = "gateKnob", default = SLOT_UNSET)

        val trees = mapOf(
            "crush" to IgnitorDsl.Crush(inner = saw, amount = unsetSlot),
            "coarse" to IgnitorDsl.Coarse(inner = saw, amount = unsetSlot),
            "distort" to IgnitorDsl.Distort(inner = saw, amount = unsetSlot),
            "drive" to IgnitorDsl.Drive(inner = saw, amount = unsetSlot),
            "tremolo" to IgnitorDsl.Tremolo(inner = saw, rate = IgnitorDsl.Constant(5.0), depth = unsetSlot),
            "onepole" to IgnitorDsl.OnePoleLowpass(inner = saw, freq = unsetSlot),
            "accelerate" to IgnitorDsl.Accelerate(inner = saw, semitones = unsetSlot),
            "pitch envelope" to IgnitorDsl.PitchEnvelope(inner = saw, semitones = unsetSlot),
            "fm" to IgnitorDsl.Fm(carrier = saw, modulator = IgnitorDsl.Sine(), depth = unsetSlot),
            "lowpass" to saw.lowpass(freq = unsetSlot),
            "highpass" to saw.highpass(freq = unsetSlot),
            "bandpass" to saw.bandpass(freq = unsetSlot),
            "notch" to saw.notch(freq = unsetSlot),
        )

        for ((name, tree) in trees) {
            val rendered = render(tree)

            withClue("$name with an unset slot default must stay finite") {
                rendered.all { it.isFinite() } shouldBe true
            }

            withClue("$name with an unset slot default must be the bare source") {
                shapeOf(build(tree)) shouldBe bareShape
                rendered.bits() shouldBe bare
            }
        }

        // Engagement: the SAME trees with the slot written are all still built.
        for ((name, tree) in trees) {
            val written = when (name) {
                "crush" -> 8.0
                "coarse" -> 4.0
                "distort" -> 0.5
                "drive" -> 0.5
                "tremolo" -> 0.5
                "accelerate" -> 12.0
                "pitch envelope" -> 12.0
                "fm" -> 200.0
                else -> 2000.0
            }

            withClue("$name with the slot written must build") {
                shapeOf(build(tree, mapOf("gateKnob" to written))) shouldNotBe bareShape
                render(tree, mapOf("gateKnob" to written)).bits() shouldNotBe bare
            }
        }
    }

    "a knob that is NOT a build-time constant is never gated, even at its off value" {
        // An LFO on the amount can move within the note, so no single build-time answer is right.
        // `Constant(4).mul(Constant(0))` is an expression, not a leaf: it resolves to 0, which is
        // coarse's off value, and the stage is built all the same.
        val expression = IgnitorDsl.Constant(4.0).mul(IgnitorDsl.Constant(0.0))

        shapeOf(build(IgnitorDsl.Coarse(inner = saw, amount = expression))) shouldNotBe bareShape
    }

    // ── The rng stream ───────────────────────────────────────────────────────────────────────

    "the gate query builds nothing, so a voice's build-time draws stay in place" {
        // `crackle` seeds its chaotic map with two draws AT CONSTRUCTION and `perlin` builds a
        // permutation table plus a start position the same way, so the ORDER in which the build
        // reaches them is audible. The arm builds the inner first and the knob second; a query
        // that built the knob in order to ask it would swap that, and the crackle would start
        // from a different pair of numbers for the rest of the note.
        //
        // The knob is `-(|perlin|)`, which is at or below 0 for every sample, and coarse's own
        // guard makes that an exact copy, so the render IS the crackle's stream, unfiltered.
        val crackle = IgnitorDsl.Crackle()
        val drawingKnob = IgnitorDsl.PerlinNoise().abs().neg()

        render(IgnitorDsl.Coarse(inner = crackle, amount = drawingKnob)).bits() shouldBe
                render(crackle).bits()
    }

    // ── The cross-voice-cache invariant ──────────────────────────────────────────────────────

    "one instrument, two bags: the two builds are structurally different graphs" {
        // `IgnitorBuildCache` is created fresh inside `buildExciter`, and `buildExciter` runs per
        // note-on (`IgnitorRegistry.createExciter` from `VoiceFactory`, and `KatalystSlots` for a
        // knob resolve), verified by caller search, 2026-09-20. So the gate's decision is
        // constant for a cache's whole lifetime and no key change is needed. This row is the
        // tripwire under that: a cache that ever spanned two voices would hand the written voice
        // the unwritten voice's graph, and the two shapes below would become one.
        val instrument = IgnitorDsl.Coarse(inner = saw, amount = IgnitorDsl.Param("coarse", 0.0))

        val unwritten = build(instrument, emptyMap())
        val written = build(instrument, mapOf("coarse" to 4.0))

        shapeOf(unwritten) shouldBe bareShape
        shapeOf(written) shouldNotBe shapeOf(unwritten)
        render(instrument, mapOf("coarse" to 4.0)).bits() shouldNotBe render(instrument, emptyMap()).bits()
    }
})
