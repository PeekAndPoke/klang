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
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import io.peekandpoke.klang.audio_bridge.constants.ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.pregain
import kotlin.random.Random

/**
 * Which of the ADSR's five knobs survive a NON-FINITE value, and by WHAT.
 *
 * Two different mechanisms, and the difference is the point of the row that uses this:
 *
 *  - `sustainLevel` and `expK` survive BY GUARD, added 2026-09-20 in `AdsrIgnitor`'s `finiteOr`.
 *    Without it `coerceIn(0.0, 1.0)` is the identity on a NaN, the level multiplies every sample
 *    and `expK` reaches `adsrExpShape` the same way, so both put NaN into the orbit mix. That
 *    mattered the moment the unity-`mul` fold landed: before it, a `pregain` at 1.0 after an
 *    envelope kept `TimesIgnitor`'s scrub and the voice went silent instead.
 *  - `attackSec`, `decaySec` and `releaseSec` survive BY CONVERSION, and by accident: a time
 *    becomes a frame count through `(seconds * sampleRate).toInt()`, and `Double.toInt()` of a NaN
 *    is 0 on both platforms, so a NaN-timed stage simply has no frames. `declickSeconds` is the
 *    same shape (`> 0.0` fails for a NaN). Nobody should rely on it: `releaseSec` was NOT safe in
 *    its other half, the voice's release TAIL, which is its own row below.
 *
 * So the set is all five, and the row asserts that with the reason attached rather than a number.
 */
private val NAN_SAFE_ADSR_KNOBS: Set<String> = setOf(
    "attackSec", "decaySec", "sustainLevel", "releaseSec", "expK",
)

/**
 * THE GATE: a stage whose gating knob is a build-time constant at its OFF value is not built.
 *
 * The rule, why it exists and why it is restricted to the `Param` and `Constant` leaves are all in
 * `IgnitorDslRuntime`'s `gatedOff` KDoc; the off VALUES are one table, in
 * `docs/tasks/builtin-instruments.md` section 5b. This spec is what holds both to their word.
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
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    fun build(dsl: IgnitorDsl, params: Map<String, Double>? = null, rng: Random = seed()): Ignitor =
        dsl.buildExciter(oscParams = params, random = rng, freqHz = freqHz).ignitor

    /**
     * [blocks] blocks of [dsl], built with [params] and rendered end to end.
     *
     * ONE `Random` for the build and the context, which is what a voice gets: the build's
     * construction draws and the render's per-sample draws come off the same stream, so a row that
     * is about draw ORDER is about the stream a voice really has.
     */
    fun render(dsl: IgnitorDsl, params: Map<String, Double>? = null): DoubleArray {
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

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    /** The node right under the root memo: what the gate adds or does not add. */
    fun shapeOf(ignitor: Ignitor): Any = (ignitor as MemoizingIgnitor).inner::class

    val saw: IgnitorDsl = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    /** The build's release-tail finding for [this], which is what voice lifetime is ranked on. */
    fun IgnitorDsl.tail(): Double? = buildExciter(random = seed(), freqHz = freqHz).releaseTailSec

    val unsetRelease: IgnitorDsl = IgnitorDsl.Adsr(inner = saw, releaseSec = IgnitorDsl.Constant(SLOT_UNSET))
    val longRelease: IgnitorDsl = IgnitorDsl.Adsr(inner = saw, releaseSec = IgnitorDsl.Constant(2.0))

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
            // `levels` is `2^amount`, so amount 1 is exactly TWO levels: `levels < 2.0` is false,
            // the quantizer RUNS with `halfLevels = 1.0`, and a saw comes out a three-level
            // staircase. The boundary is `< 1.0`, not `<= 1.0`, and both halves say so.
            val atOne = IgnitorDsl.Crush(inner = saw, amount = IgnitorDsl.Constant(1.0))

            shapeOf(build(atOne)) shouldNotBe bareShape
            render(atOne).bits() shouldNotBe bare
        }
    }

    "distort: at or below 0.0 is not built, above it is" {
        // The LEGACY node: neither authoring door builds it (both spell `distort` as
        // `Shape(Drive(...))`), so this row guards an alignment, and `drive` below guards the
        // shape that actually ships.
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
        val shaped = IgnitorDsl.Shape(inner = saw, shape = "soft")

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
        // Inverted from the plan's sketch, and the spike is why: today the voice strip's VCA runs
        // on EVERY voice with `AdsrDef.defaultSynth` when the pattern sets nothing. What switches
        // the tail's envelope off is an explicit `adsrOff` slot, a later step's knob.
        val unsetAttack = IgnitorDsl.Adsr(inner = saw, attackSec = IgnitorDsl.Constant(SLOT_UNSET))

        shapeOf(build(unsetAttack)) shouldNotBe bareShape
        render(unsetAttack).bits() shouldNotBe bare
    }

    "the ungated envelope's knobs under a NaN: all five stay finite, two of them by guard" {
        // The envelope is NOT gated, so the gate is not what stands between a NaN on one of its
        // knobs and the DSP. Two knobs needed a guard of their own and now have one; see
        // NAN_SAFE_ADSR_KNOBS for which survive by what.
        val unset = IgnitorDsl.Constant(SLOT_UNSET)

        val perKnob = mapOf(
            "attackSec" to IgnitorDsl.Adsr(inner = saw, attackSec = unset),
            "decaySec" to IgnitorDsl.Adsr(inner = saw, decaySec = unset),
            "sustainLevel" to IgnitorDsl.Adsr(inner = saw, sustainLevel = unset),
            "releaseSec" to IgnitorDsl.Adsr(inner = saw, releaseSec = unset),
            "expK" to IgnitorDsl.Adsr(inner = saw, expK = unset),
        )

        val survives = perKnob.filterValues { tree -> render(tree).all { it.isFinite() } }.keys

        survives shouldBe NAN_SAFE_ADSR_KNOBS

        // What the two guarded knobs render, so the substitution is pinned to a VALUE and not
        // merely to "finite": the same thing the knob's own default renders.
        withClue("a non-finite sustainLevel renders what ADSR_SUSTAIN_LEVEL renders") {
            val atDefault = render(
                IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(ADSR_SUSTAIN_LEVEL)),
            ).bits()

            render(IgnitorDsl.Adsr(inner = saw, sustainLevel = unset)).bits() shouldBe atDefault

            // The INFINITIES pin the substitution's PLACEMENT, which a NaN cannot: `coerceIn` is
            // the identity on a NaN, so before or after the coercion ends at the same value, but
            // it has a real answer for an infinity. `+Inf` used to sustain at the 1.0 rail and
            // `-Inf` at the 0.0 rail; substituting FIRST makes both read as unset instead, which
            // is the house rule the gate one file over applies to every knob it tests.
            render(IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(Double.POSITIVE_INFINITY)))
                .bits() shouldBe atDefault
            render(IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(Double.NEGATIVE_INFINITY)))
                .bits() shouldBe atDefault

            withClue("and the two rails are NOT what it renders, so the placement is what is pinned") {
                atDefault shouldNotBe render(
                    IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(1.0)),
                ).bits()
                atDefault shouldNotBe render(
                    IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(0.0)),
                ).bits()
            }
        }

        withClue("a non-finite expK renders what ADSR_EXP_K renders") {
            render(IgnitorDsl.Adsr(inner = saw, expK = unset)).bits() shouldBe
                    render(IgnitorDsl.Adsr(inner = saw, expK = IgnitorDsl.Constant(ADSR_EXP_K))).bits()
        }

        withClue("engagement: a DIFFERENT finite value still renders differently, both knobs") {
            render(IgnitorDsl.Adsr(inner = saw, sustainLevel = IgnitorDsl.Constant(0.2))).bits() shouldNotBe
                    render(IgnitorDsl.Adsr(inner = saw, sustainLevel = unset)).bits()
            render(IgnitorDsl.Adsr(inner = saw, expK = IgnitorDsl.Constant(9.0))).bits() shouldNotBe
                    render(IgnitorDsl.Adsr(inner = saw, expK = unset)).bits()
        }
    }

    "a non-finite releaseSec cannot swallow a SIBLING's tail, in the order where it could" {
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

    "a non-finite releaseSec cannot swallow the INNER envelope's tail in a chain" {
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
            inner = IgnitorDsl.Adsr(inner = saw, releaseSec = IgnitorDsl.Constant(2.0)).lowpass(freq = 2000.0),
            releaseSec = IgnitorDsl.Constant(SLOT_UNSET),
        )

        chained.tail() shouldBe 2.0

        withClue("engagement: a finite outer release still wins when it is longer") {
            IgnitorDsl.Adsr(inner = chained, releaseSec = IgnitorDsl.Constant(5.0)).tail() shouldBe 5.0
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
