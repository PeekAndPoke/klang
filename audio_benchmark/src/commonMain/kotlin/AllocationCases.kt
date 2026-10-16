/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.audio_bridge.unaryMinus
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.toKlangMap
import io.peekandpoke.klang.script.stdlib.band
import io.peekandpoke.klang.script.stdlib.bands
import io.peekandpoke.klang.script.stdlib.cut
import io.peekandpoke.klang.script.stdlib.parallel
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.adsr
import io.peekandpoke.klang.sprudel.lang.analog
import io.peekandpoke.klang.sprudel.lang.body
import io.peekandpoke.klang.sprudel.lang.compressor
import io.peekandpoke.klang.sprudel.lang.delay
import io.peekandpoke.klang.sprudel.lang.lpf
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.phaser
import io.peekandpoke.klang.sprudel.lang.reverb
import kotlin.math.abs
import kotlin.random.Random

/**
 * The fixed case list of the allocation check (`KLANG_BENCH_MODE=alloc`, `console/full-check.sh`), the same on
 * both platforms. Each voice case and the first engine cases guard one fix of `docs/tasks/in-progress/engine-follow-ups.md`
 * against a regression that brings a heap number per sample or per knob read back; the cases with an authored
 * Katalyst chain guard its stages (`docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`). The
 * budgets and what each one guards are in `audio_benchmark/alloc-budgets.txt`.
 *
 * Every case renders at 48 kHz in 128-frame blocks at middle C (261.63 Hz, not an integer, so a frequency read that
 * boxes shows). A "voice" case is one voice through [VoiceFactory], offered to its cylinder and committed each
 * block. An "engine" case runs [PlaybackEngineDispatcher], so the master stage (DC blockers, the house limiter, the
 * clip) runs too. An engine case with a [Case.chain] runs an authored Katalyst on its orbit, registered and referenced
 * as a song's is (`docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`).
 *
 * The list is fixed and learnable: no random cases. A change to it starts a new scoreboard table.
 */
object AllocationCases {

    /** The sample rate of every case. */
    const val SAMPLE_RATE: Int = 48_000

    /** Middle C, 261.63 Hz, the frequency of every case. */
    const val FREQ_HZ: Double = 261.6255653005986

    /** The block size of every case: the engine's pinned 128 frames. */
    private const val BLOCK_FRAMES: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES

    /** The gate of a sustained note, in seconds: longer than any window. */
    private const val SUSTAIN_SEC: Double = 1.0e9

    /** The name an authored case's tree is registered under. */
    private const val AUTHORED: String = "zzalloc"

    /** The name an authored case's Katalyst chain is registered under. */
    private const val CHAIN: String = "zzallocbus"

    /** The orbit of the trigger voice a [Case.duckTrigger] case adds, which its duck listens to. */
    private const val TRIGGER_ORBIT: Int = 1

    private const val PLAYBACK_ID: String = "alloc"

    /**
     * One case.
     *
     * [sound] is a built-in sound, or [AUTHORED] when [dsl] is set. [params] are the `classic()` slots, [katp] the
     * orbit's (`delay.wet`, ...): the slots of the classic chain an engine case's orbit runs, or of [chain] when it is
     * set. An engine case with a [chain] registers it ([KlangCommLink.Cmd.RegisterKatalyst]) and its notes reference
     * it by name ([VoiceData.katalyst]). The note's gate is [gateSec] seconds. An engine case with
     * [newNoteEveryBlocks] above 0 starts a new note that often instead of one note at the start, and with [katpNext]
     * set its notes alternate between [katp] and [katpNext], so the orbit's owner moves the chain's slots at every
     * note. A [duckTrigger] case adds a sustained saw on orbit [TRIGGER_ORBIT], with the classic chain, for the case's
     * duck to listen to. A voice case with [ownersTakingTurns] runs two such voices, received as the engine
     * receives them ([receivedByEngine]), taking turns as their orbit's owner block by block. A [sprudel] case runs
     * sprudel's per-event path instead of the engine ([SprudelRig]); it renders no sound, so [makesSound] is false
     * and the driver's silence check asks its rig for [Rig.outputs] instead of [Rig.peak].
     */
    class Case(
        val name: String,
        val engine: Boolean,
        val sound: String,
        val dsl: IgnitorDsl? = null,
        val params: Map<String, Double>? = null,
        val katp: Map<String, Double>? = null,
        val chain: KatalystDsl? = null,
        val katpNext: Map<String, Double>? = null,
        val duckTrigger: Boolean = false,
        val gateSec: Double = SUSTAIN_SEC,
        val newNoteEveryBlocks: Int = 0,
        val ownersTakingTurns: Boolean = false,
        val sprudel: Boolean = false,
        val makesSound: Boolean = !sprudel,
    ) {
        /**
         * [params], [katp] and [katpNext] as the engine takes them (`KlangMap`s), made once per case: every note of a
         * case hands over the same instance, so the chain's identity gate sees what it always saw.
         */
        internal val paramsMap: KlangMap<Double>? = params?.toKlangMap()
        internal val katpMap: KlangMap<Double>? = katp?.toKlangMap()
        internal val katpNextMap: KlangMap<Double>? = katpNext?.toKlangMap()
    }

    private fun c(v: Double) = IgnitorDsl.Constant(v)

    private fun saw() = IgnitorDsl.Saw(analog = c(0.0))

    private val full = mapOf(
        "distort.amount" to 0.5, "lpf.freq" to 1200.0, "lpf.q" to 1.3, "lpf.env" to 12.0, "tremolo.depth" to 0.3,
        "tremolo.rate" to 4.3, "hpf.freq" to 80.0, "analog" to 0.2,
    )

    private val env3 = mapOf(
        "adsr.attack" to 0.0, "adsr.decay" to 1000.0, "adsr.sustain" to 0.5,
        "penv.semitones" to 24.0, "penv.attack" to 0.0, "penv.decay" to 1000.0, "penv.sustain" to 0.0,
        "fm.ratio" to 1.4, "fm.depth" to 300.0, "fm.attack" to 0.0, "fm.decay" to 1000.0, "fm.sustain" to 0.3,
    )

    /**
     * The orbit delay of the stage cases: a tap of 0.2501 s, 12,004.8 samples, so the tap's fraction is not a whole
     * number (a fraction of 0 travels as a small integer on V8 and boxes nothing), and a feedback of 0.45.
     */
    private val delay = mapOf("delay.wet" to 0.3, "delay.time" to 0.2501, "delay.feedback" to 0.45)

    /** The three orbit stages of `E-stages`: [delay], the reverb, the phaser with a floor below 1. */
    private val stages = delay + mapOf(
        "reverb.wet" to 0.3, "reverb.size" to 6.3,
        "phaser.wet" to 0.5, "phaser.rate" to 0.5, "phaser.sweep" to 800.0, "phaser.floor" to 0.5,
    )

    /** A chain through the typed Kotlin door (`Kat.build`), the builder a Kotlin song uses: [build] appends to an empty chain. */
    internal fun katalyst(build: (KatalystBuilder) -> KatalystBuilder): KatalystDsl = Kat.build(configure = build)

    /** A chain slot (`Katalyst.param`) named `arith.<name>`. */
    private fun slot(name: String, default: Double) = KatalystKnobDsl.Param(name = "arith.$name", default = default)

    /**
     * The eight arithmetic stages in one chain, with the operands [times] to [floor] (a number, or a [slot]). The values
     * are not integers (an integral double travels as a small integer on V8 and boxes nothing); the cap and the floor
     * cut into the saw's peaks; `abs` last folds what is left (the master's DC blocker takes the offset it makes).
     * The doors cross on purpose: `max` builds the `Min` stage (the cap), `min` the `Max` stage (the floor).
     */
    private fun arithmetic(
        times: KatalystKnobDsl,
        plus: KatalystKnobDsl,
        minus: KatalystKnobDsl,
        div: KatalystKnobDsl,
        pow: KatalystKnobDsl,
        cap: KatalystKnobDsl,
        floor: KatalystKnobDsl,
    ): KatalystDsl = katalyst {
        it.times(times).plus(plus).minus(minus).div(div).pow(pow).max(cap).min(floor).abs()
    }

    /** `E-arith`: every operand a constant, the plain law loops. */
    val arithmeticChain: KatalystDsl =
        arithmetic(times = 0.9.kat, plus = 0.013.kat, minus = 0.007.kat, div = 1.3.kat, pow = 0.7.kat, cap = 0.45.kat, floor = -0.45.kat)

    /** `E-arith-glide`: every operand a slot, its default `E-arith`'s number. */
    private val arithmeticSlots: KatalystDsl = arithmetic(
        times = slot("times", 0.9),
        plus = slot("plus", 0.013),
        minus = slot("minus", 0.007),
        div = slot("div", 1.3),
        pow = slot("pow", 0.7),
        cap = slot("cap", 0.45),
        floor = slot("floor", -0.45),
    )

    /** The first note's slot values, [arithmeticChain]'s numbers. */
    private val arithmeticA = mapOf(
        "arith.times" to 0.9, "arith.plus" to 0.013, "arith.minus" to 0.007, "arith.div" to 1.3, "arith.pow" to 0.7,
        "arith.cap" to 0.45, "arith.floor" to -0.45,
    )

    /**
     * The second note's: every other operand moves, so its stage glides (the divisor as its reciprocal, through the
     * multiply branch). The cap is unset (non-finite), so the `Min` stage lets go and its next operand arrives at once:
     * it never glides, and no case runs its ramp loop.
     */
    private val arithmeticB = mapOf(
        "arith.times" to 0.7, "arith.plus" to -0.011, "arith.minus" to 0.004, "arith.div" to 0.8, "arith.pow" to 1.3,
        "arith.cap" to Double.NaN, "arith.floor" to -0.3,
    )

    /**
     * The notes of `E-arith-glide`: one every 32 blocks, held for 30 of them, so every note owns the orbit for its gate
     * and the glide (19 blocks at 48 kHz, `KnobGlide`) runs in 19 blocks of every 32.
     */
    private const val GLIDE_NOTE_BLOCKS: Int = 32

    private const val GLIDE_GATE_SEC: Double = 30.0 * BLOCK_FRAMES / SAMPLE_RATE

    // ── The knob expressions and the modulators (step 6 of docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md) ──
    //
    // Every chain below moves at least one knob every block through the chain's hook (`KatalystChain.process`: each
    // modulated writer ticks its knobs, refills its holder and configures its stage). The rates and the ranges are not
    // integers (an integral double travels as a small integer on V8 and boxes nothing), and every range keeps the
    // orbit sounding. The `*Constant` twins hold each modulated knob at a fixed number in its range, for the timing
    // rows' comparison (`EffectBenchmark`).

    /** (a) One sine on the group fader: the plainest modulated knob, one modulator and one `range` per block. */
    internal val sineChain: KatalystDsl = katalyst { it.gain(Kat.sine(0.7.kat).range(from = 0.45.kat, to = 0.85.kat)) }

    /**
     * (b) A square whose duty is a SLOT EXPRESSION (`kat.duty`, written by the case's `katp`), on the group fader: the
     * square's shape, and the expression's arithmetic run every block inside the modulated knob (a slot leaf loaded
     * once per new map, then the `plus` per tick).
     */
    internal val squareChain: KatalystDsl = katalyst {
        it.gain(Kat.square(1.3.kat) { b -> b.duty(Kat.param("kat.duty", 0.3).plus(0.05.kat)) }.range(from = 0.45.kat, to = 0.85.kat))
    }

    /** (c) A saw whose freq is a sine: its edges are rebaked every block (`setSawShape`, the `dt` moves). */
    internal val sawFmChain: KatalystDsl = katalyst {
        it.gain(Kat.saw(Kat.sine(0.3.kat).range(from = 0.6.kat, to = 1.9.kat)).range(from = 0.45.kat, to = 0.85.kat))
    }

    /** (d) A perlin at one octave on the group fader and one at eight on a `times` stage: `fbm` once per tick each. */
    internal val perlinChain: KatalystDsl = katalyst {
        it.gain(Kat.perlin(0.0031.kat).range(from = 0.45.kat, to = 0.85.kat)).times(Kat.perlin(0.0031.kat, octaves = 8.0.kat).range(from = 0.8.kat, to = 1.15.kat))
    }

    /** (e) All seven modulator kinds in one chain, the fader and six `times` stages, each near unity. */
    internal val allKindsChain: KatalystDsl = katalyst {
        it.gain(Kat.sine(0.7.kat).range(from = 0.45.kat, to = 0.85.kat))
            .times(Kat.saw(0.53.kat).range(from = 0.85.kat, to = 1.1.kat))
            .times(Kat.ramp(0.41.kat).range(from = 0.85.kat, to = 1.1.kat))
            .times(Kat.tri(0.67.kat).range(from = 0.85.kat, to = 1.1.kat))
            .times(Kat.square(0.37.kat).range(from = 0.85.kat, to = 1.1.kat))
            .times(Kat.perlin(0.0031.kat).range(from = 0.85.kat, to = 1.1.kat))
            .times(Kat.berlin(0.0027.kat).range(from = 0.85.kat, to = 1.1.kat))
    }

    /** [allKindsChain] with each knob a constant in its range. */
    internal val allKindsConstant: KatalystDsl = katalyst {
        it.gain(0.65.kat).times(0.97.kat).times(0.97.kat).times(0.97.kat).times(0.97.kat).times(0.97.kat).times(0.97.kat)
    }

    /**
     * The compressor with all five knobs moving and the duck's depth and attack moving (the settings holders, step 2:
     * each writer refills one `CompressorSettings` or `DuckSettings` per block and hands it to its stage; the attack
     * reaches `Ducking.attackSeconds` every block). The duck listens to the trigger orbit, and its depth stays above 0.
     */
    internal val dynamicsChain: KatalystDsl = katalyst {
        it.compressor(
            threshold = Kat.sine(0.51.kat).range(from = -26.5.kat, to = -12.5.kat),
            ratio = Kat.tri(0.43.kat).range(from = 2.5.kat, to = 5.5.kat),
            knee = Kat.saw(0.37.kat).range(from = 0.5.kat, to = 5.5.kat),
            attack = Kat.sine(0.61.kat).range(from = 0.0021.kat, to = 0.0093.kat),
            release = Kat.ramp(0.29.kat).range(from = 0.051.kat, to = 0.187.kat),
        ).duck(
            orbit = TRIGGER_ORBIT.kat,
            depth = Kat.sine(0.45.kat).range(from = 0.25.kat, to = 0.75.kat),
            attack = Kat.tri(0.33.kat).range(from = 0.011.kat, to = 0.047.kat),
        )
    }

    /**
     * A body and a vowel whose wet and floor move every block (step 5c: the blend glides in place, through
     * `WetDryMix`'s `coerceIn`). The body's wet crosses 0, so the bypass, its landing and the return from rest run too.
     */
    internal val resonatorChain: KatalystDsl = katalyst {
        it.body(wet = Kat.sine(0.85.kat).range(from = -0.2.kat, to = 0.7.kat), material = "wood") { b ->
            b.floor(Kat.sine(0.65.kat).range(from = 0.25.kat, to = 0.6.kat))
        }.vowel(wet = Kat.tri(0.95.kat).range(from = 0.3.kat, to = 0.8.kat), vowel = "a") { b -> b.floor(Kat.sine(0.55.kat).range(from = 0.1.kat, to = 0.45.kat)) }
    }

    /**
     * A delay whose time and a reverb whose size cross their gates (`MIN_ACTIVE_DELAY_SECONDS`, `MIN_ACTIVE_SIZE`)
     * every cycle (step 5b: the feed fades out below the gate, the stage drains, and above it the feed fades back in
     * from 0). Above the gate the delay's time steps through its tap crossfade every block. The reverb's lowpass moves
     * too, written into the primitive `Reverb.lowpass` every block (step 5b).
     */
    internal val gatesChain: KatalystDsl = katalyst {
        it.delay(wet = 0.35.kat, time = Kat.sine(0.3.kat).range(from = -0.04.kat, to = 0.26.kat), feedback = 0.45.kat)
            .reverb(wet = 0.3.kat, size = Kat.sine(0.2.kat).range(from = -1.5.kat, to = 6.5.kat), lowpass = Kat.sine(0.27.kat).range(from = 2100.5.kat, to = 6300.5.kat))
    }

    /**
     * A slot EXPRESSION on the fader of a chain whose orbit sees a new param map every block ([TurnsRig]): the
     * expression is evaluated on every resolve, the most a slot expression ever costs (a song's map changes per note).
     */
    internal val remapExpressionChain: KatalystDsl = katalyst {
        it.gain(Kat.param("kat.g", 0.6).times(1.1.kat).plus(0.013.kat))
    }

    /**
     * The mixed orbit profile: every stage family a song's orbit runs but `eq`, `distort` and the arithmetic, one knob
     * each moving, spread over the seven kinds (the phaser's center among them, its `applied` short-circuit defeated
     * every block), and a slot expression on the phaser's sweep (`kat.sweep`, resolved once). The duck listens to the
     * trigger orbit.
     */
    internal val mixedChain: KatalystDsl = mixed(
        bodyWet = Kat.sine(0.85.kat).range(from = 0.2.kat, to = 0.7.kat),
        vowelWet = Kat.tri(0.95.kat).range(from = 0.3.kat, to = 0.8.kat),
        delayTime = Kat.saw(0.31.kat).range(from = 0.21.kat, to = 0.26.kat),
        reverbSize = Kat.perlin(0.0031.kat).range(from = 3.5.kat, to = 6.5.kat),
        phaserCenter = Kat.sine(0.23.kat).range(from = 450.0.kat, to = 1650.0.kat),
        threshold = Kat.ramp(0.51.kat).range(from = -26.5.kat, to = -12.5.kat),
        gain = Kat.berlin(0.0027.kat).range(from = 0.75.kat, to = 0.95.kat),
        duckDepth = Kat.square(0.45.kat).range(from = 0.25.kat, to = 0.75.kat),
    )

    /** [mixedChain] with each moving knob a constant in its range (the sweep's slot expression stays). */
    internal val mixedConstant: KatalystDsl = mixed(
        bodyWet = 0.45.kat,
        vowelWet = 0.55.kat,
        delayTime = 0.235.kat,
        reverbSize = 5.1.kat,
        phaserCenter = 1050.0.kat,
        threshold = -19.5.kat,
        gain = 0.85.kat,
        duckDepth = 0.5.kat,
    )

    private fun mixed(
        bodyWet: KatalystKnobDsl,
        vowelWet: KatalystKnobDsl,
        delayTime: KatalystKnobDsl,
        reverbSize: KatalystKnobDsl,
        phaserCenter: KatalystKnobDsl,
        threshold: KatalystKnobDsl,
        gain: KatalystKnobDsl,
        duckDepth: KatalystKnobDsl,
    ): KatalystDsl = katalyst {
        it.body(wet = bodyWet, material = "wood")
            .vowel(wet = vowelWet, vowel = "a")
            .delay(wet = 0.3.kat, time = delayTime, feedback = 0.45.kat)
            .reverb(wet = 0.3.kat, size = reverbSize)
            .phaser(wet = 0.5.kat, rate = 0.55.kat, center = phaserCenter, sweep = Kat.param("kat.sweep", 600.0).times(1.3.kat))
            .compressor(threshold = threshold, ratio = 3.5.kat)
            .gain(gain)
            .duck(orbit = TRIGGER_ORBIT.kat, depth = duckDepth)
    }

    /** The cases, in scoreboard order. */
    val all: List<Case> = listOf(
        Case(name = "saw", engine = false, sound = AUTHORED, dsl = saw()),
        Case(name = "sawC", engine = false, sound = "saw"),
        Case(name = "C-offrel", engine = false, sound = "saw", params = mapOf("adsr.release" to 1000.0), gateSec = 0.01),
        Case(name = "C-env3", engine = false, sound = "saw", params = env3),
        Case(name = "super7", engine = false, sound = "supersaw", params = mapOf("voices" to 7.0, "spread" to 0.3)),
        Case(
            name = "super7a",
            engine = false,
            sound = "supersaw",
            params = mapOf("voices" to 7.0, "spread" to 0.3, "analog" to 0.3),
        ),
        // Feedback 1: at the default 0.996 the strings are silent long before the window ends, which the check refuses.
        // What the check sees at the end is the strings' DC, which feedback 1 keeps (a constant line is the loop's fixed
        // point); the drift path it guards runs every block whatever the signal.
        Case(
            name = "spluck-a",
            engine = false,
            sound = "superpluck",
            params = mapOf("voices" to 8.0, "analog" to 0.3, "feedback" to 1.0),
        ),
        Case(
            name = "sgpad-vib",
            engine = false,
            sound = "sgpad",
            params = mapOf("vibrato.rate" to 5.0, "vibrato.semitones" to 0.5),
        ),
        Case(name = "whitecolor", engine = false, sound = AUTHORED, dsl = IgnitorDsl.WhiteNoise(color = c(0.3))),
        Case(name = "C-dist", engine = false, sound = "saw", params = mapOf("distort.amount" to 0.5)),
        Case(
            name = "tube4x",
            engine = false,
            sound = AUTHORED,
            dsl = saw().distort(amount = 0.5.ign, shape = "tube", oversample = 4.ign),
        ),
        Case(name = "C-full", engine = false, sound = "saw", params = full),
        // A new owner, and so a new param map instance, on the orbit every block: the chain re-resolves its 27 slot
        // knobs each block, a map lookup per knob (engine follow-up 2, the decoder's map type).
        Case(name = "C-remap", engine = false, sound = "saw", katp = delay, ownersTakingTurns = true),
        // The same turns with an authored chain whose fader is a slot expression: one evaluation per new map, every
        // block (knob expressions step 6).
        Case(
            name = "C-kat-remap",
            engine = false,
            sound = "saw",
            chain = remapExpressionChain,
            katp = mapOf("kat.g" to 0.62),
            ownersTakingTurns = true,
        ),
        Case(name = "E-sawC-sus", engine = true, sound = "saw"),
        Case(name = "E-noisesaw-sus", engine = true, sound = AUTHORED, dsl = saw() + IgnitorDsl.WhiteNoise()),
        Case(name = "E-delay", engine = true, sound = "saw", katp = delay),
        Case(name = "E-stages", engine = true, sound = "saw", katp = stages),
        // A short classic saw note every block through the dispatcher: the per-note lookups (the registry's sound name,
        // the dispatcher's playback id), engine follow-up 2, step 3.
        Case(name = "E-C-n1", engine = true, sound = "saw", gateSec = 0.01, newNoteEveryBlocks = 1),
        Case(name = "E-arith", engine = true, sound = "saw", chain = arithmeticChain),
        Case(
            name = "E-arith-glide",
            engine = true,
            sound = "saw",
            chain = arithmeticSlots,
            katp = arithmeticA,
            katpNext = arithmeticB,
            gateSec = GLIDE_GATE_SEC,
            newNoteEveryBlocks = GLIDE_NOTE_BLOCKS,
        ),
        // The bus distort at its default 2x oversampling.
        Case(name = "E-distort", engine = true, sound = "saw", chain = katalyst { it.distort(amount = 0.3.kat) }),
        // A compressor without lookahead, then a limiter with one (its delay ring); the threshold sits under the saw.
        Case(
            name = "E-comp",
            engine = true,
            sound = "saw",
            chain = katalyst { it.compressor(threshold = -18.0.kat, ratio = 4.0.kat).limiter(threshold = -6.0.kat, lookahead = 0.002) },
        ),
        // Two bells and a tap, the sections the Katalyst `eq` door writes (the crossovers are E-bands').
        Case(
            name = "E-eq",
            engine = true,
            sound = "saw",
            chain = katalyst {
                it.eq { e -> e.band(freq = 180.0.kat, db = (-3.5).kat).band(freq = 2500.0.kat, q = 1.3.kat, db = 4.5.kat).tap(freq = 800.0.kat, gain = 0.6.kat) }
            },
        ),
        Case(name = "E-gain", engine = true, sound = "saw", chain = katalyst { it.gain(0.7.kat) }),
        // The duck listens to a second saw on its own orbit; its envelope follows that orbit's mix every block.
        Case(
            name = "E-duck",
            engine = true,
            sound = "saw",
            chain = katalyst { it.duck(orbit = TRIGGER_ORBIT.kat, depth = 0.6.kat) },
            duckTrigger = true,
        ),
        // Parallel distortion: a distorted branch beside an empty one, which gets the distort's phase twin.
        Case(
            name = "E-parallel",
            engine = true,
            sound = "saw",
            chain = katalyst { it.parallel({ b -> b.distort(amount = 0.4.kat) }, { b -> b }) },
        ),
        // Three bands with the mids distorted (the `bands` door's own example): the crossovers, the all-pass matching
        // and the phase twins of the other two bands.
        Case(
            name = "E-bands",
            engine = true,
            sound = "saw",
            chain = katalyst { it.bands { b -> b.cut(150.0).band { m -> m.distort(amount = 0.15.kat) }.cut(5000.0) } },
        ),
        // A vowel sung through a body: the one resonator class, both kinds.
        Case(
            name = "E-body-vowel",
            engine = true,
            sound = "saw",
            chain = katalyst { it.body(wet = 0.5.kat, material = "wood").vowel(wet = 0.5.kat, vowel = "a") },
        ),
        // The knob expressions and the modulators (step 6 of the knob expressions task): each chain moves a knob every
        // block through the chain's hook.
        Case(name = "E-kat-sine", engine = true, sound = "saw", chain = sineChain),
        Case(
            name = "E-kat-square",
            engine = true,
            sound = "saw",
            chain = squareChain,
            katp = mapOf("kat.duty" to 0.35),
        ),
        Case(name = "E-kat-sawfm", engine = true, sound = "saw", chain = sawFmChain),
        Case(name = "E-kat-perlin", engine = true, sound = "saw", chain = perlinChain),
        Case(name = "E-kat-all7", engine = true, sound = "saw", chain = allKindsChain),
        Case(name = "E-kat-dyn", engine = true, sound = "saw", chain = dynamicsChain, duckTrigger = true),
        Case(name = "E-kat-res", engine = true, sound = "saw", chain = resonatorChain),
        Case(name = "E-kat-gates", engine = true, sound = "saw", chain = gatesChain),
        Case(
            name = "E-kat-mix",
            engine = true,
            sound = "saw",
            chain = mixedChain,
            katp = mapOf("kat.sweep" to 650.0),
            duckTrigger = true,
        ),
        Case(
            name = "E-C-rel05-n16",
            engine = true,
            sound = "saw",
            params = mapOf("adsr.release" to 0.5),
            gateSec = 0.01,
            newNoteEveryBlocks = 16,
        ),
        // Sprudel's per-event path (engine follow-up 2, step 4): one note with voice and bus doors per step, through
        // `toVoiceData` and on to the worklet. It guards the param bags' KlangMap storage and its copies.
        Case(
            name = "S-doors",
            engine = false,
            sound = "",
            sprudel = true,
        ),
    )

    /** The case named [name], or null. */
    fun byName(name: String): Case? = all.firstOrNull { it.name == name }

    /** Renders a case block by block. */
    abstract class Rig {
        /** Renders one block. */
        abstract fun step()

        /** Renders [blocks] blocks. */
        fun run(blocks: Int) {
            for (b in 0 until blocks) {
                step()
            }
        }

        /**
         * The peak of the last block rendered, both channels: the proof that the case still sounds at the end of its
         * window (a case that went silent would measure nothing and read 0 B). Read after the window, never in it.
         */
        abstract fun peak(): Double

        /**
         * For a case that makes no sound ([Case.makesSound] false): how many outputs its last step produced, the
         * proof that it still runs its path at the end of its window. Read after the window, never in it.
         */
        open fun outputs(): Int = 0
    }

    /** The largest absolute sample of the first [frames] frames of [left] and [right]. */
    private fun peakOf(left: DoubleArray, right: DoubleArray, frames: Int): Double {
        var peak = 0.0

        for (i in 0 until frames) {
            peak = maxOf(peak, abs(left[i]), abs(right[i]))
        }

        return peak
    }

    /** A new rig for [case], built and ready for its first block. */
    fun rig(case: Case): Rig = when {
        case.sprudel -> SprudelRig()
        case.engine -> EngineRig(case)
        case.ownersTakingTurns -> TurnsRig(case)
        else -> VoiceRig(case)
    }

    private fun voiceData(case: Case, katp: KlangMap<Double>? = case.katpMap) = VoiceData.empty.copy(
        freqHz = FREQ_HZ,
        sound = case.sound,
        ignitorParams = case.paramsMap,
        katalystParams = katp,
        katalyst = if (case.chain != null) CHAIN else null,
    )

    /**
     * Sprudel's per-event path, no engine: one step queries one cycle of a one-note pattern with voice and bus doors
     * (both param bags, the door writes and fills, `clone`, the merges, `toVoiceData`'s copies) and hands every event
     * on as the browser does ([sentToWorklet]). Bytes per step are bytes per event.
     */
    private class SprudelRig : Rig() {
        private val pattern: SprudelPattern = note("c")
            .lpf(freq = 1200, q = 1.3, env = 12)
            .adsr(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.4)
            .analog(0.2)
            .reverb(wet = 0.3, size = 5, lowpass = 4321)
            .delay(wet = 0.2, time = 0.25, feedback = 0.4)
            .phaser(wet = 0.5, rate = 0.5)
            .body(wet = 0.4, material = "wood")
            .compressor(threshold = -12, ratio = 3)

        private var cycle = 0
        private var sent = 0
        private var last: Any? = null

        override fun step() {
            val events = pattern.queryArc(cycle.toDouble(), cycle + 1.0)

            sent = 0
            last = null

            for (i in events.indices) {
                val voice = ScheduledVoice(
                    playbackId = PLAYBACK_ID,
                    data = events[i].toVoiceData(),
                    startTime = 0.0,
                    gateEndTime = 1.0,
                    playbackStartTime = 0.0,
                )

                last = sentToWorklet(voice)
                sent++
            }

            cycle++
        }

        override fun peak(): Double = 0.0

        override fun outputs(): Int = if (last != null) sent else 0
    }

    /** One voice through [VoiceFactory], offered to its cylinder and committed each block. */
    private class VoiceRig(case: Case) : Rig() {
        private val registry = IgnitorRegistry().apply {
            registerDefaults()

            if (case.dsl != null) {
                register(case.sound, case.dsl)
            }
        }

        private val factory = VoiceFactory(
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private val voice: Voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = PLAYBACK_ID,
                data = voiceData(case),
                startTime = 0.0,
                gateEndTime = case.gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(PLAYBACK_ID, registry, PhasePools(Random(1))),
        ) { null } ?: error("the voice of case ${case.name} was not built")

        private val ctx = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = BLOCK_FRAMES, sampleRate = SAMPLE_RATE),
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private var block = 0

        /** The cylinder of the last block: its mix is cleared at the start of the next one, so [peak] can read it. */
        private var cylinder: Cylinder? = null

        override fun step() {
            val last = cylinder

            if (last != null) {
                last.mixBuffer.left.fill(0.0)
                last.mixBuffer.right.fill(0.0)
            }

            ctx.blockStart = (block * BLOCK_FRAMES).toDouble()
            voice.render(ctx)

            val next = ctx.cylinders.offer(id = voice.cylinderId, voice = voice, blockStart = 0.0)

            next.commitOwner()
            cylinder = next
            block++
        }

        override fun peak(): Double {
            val mix = cylinder?.mixBuffer ?: return 0.0

            return peakOf(left = mix.left, right = mix.right, frames = BLOCK_FRAMES)
        }
    }

    /**
     * Two voices of [case] taking turns as their orbit's owner, only the owner rendering each block: a voice offers
     * itself inside its render, and of two rendering voices the later one would own the orbit every block. Each voice
     * carries its own copy of the case's orbit slots, received as the engine receives them ([receivedByEngine]), so the
     * orbit sees a new map instance every block and its chain re-resolves every slot knob. With a [Case.chain] the
     * orbit runs it: the rig's cylinders resolve its name in a registry of their own, requested once.
     */
    private class TurnsRig(case: Case) : Rig() {
        private val registry = IgnitorRegistry().apply { registerDefaults() }

        private val factory = VoiceFactory(
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private val voices: Array<Voice> = Array(2) { index ->
            val data = voiceData(case).copy(katalystParams = case.katp?.toKlangMap())
            val scheduled = receivedByEngine(
                ScheduledVoice(
                    playbackId = PLAYBACK_ID,
                    data = data,
                    startTime = 0.0,
                    gateEndTime = case.gateSec,
                    playbackStartTime = 0.0,
                )
            )

            factory.makeVoice(
                scheduled = scheduled,
                backendStartTimeSec = 0.0,
                playbackCtx = PlaybackCtx(PLAYBACK_ID, registry, PhasePools(Random(1 + index))),
            ) { null } ?: error("voice $index of case ${case.name} was not built")
        }

        private val ctx = Voice.RenderContext(
            cylinders = Cylinders(
                blockFrames = BLOCK_FRAMES,
                sampleRate = SAMPLE_RATE,
                katalysts = KatalystRegistry().apply {
                    if (case.chain != null) {
                        register(name = CHAIN, dsl = case.chain)
                    }
                },
            ),
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private var block = 0
        private var cylinder: Cylinder? = null

        init {
            if (case.chain != null) {
                ctx.cylinders.requestChain(orbit = 0, name = CHAIN)
            }
        }

        override fun step() {
            val last = cylinder

            if (last != null) {
                last.mixBuffer.left.fill(0.0)
                last.mixBuffer.right.fill(0.0)
            }

            ctx.blockStart = (block * BLOCK_FRAMES).toDouble()

            val owner = voices[block % 2]

            owner.render(ctx)

            val next = ctx.cylinders.offer(id = owner.cylinderId, voice = owner, blockStart = 0.0)

            next.commitOwner()
            cylinder = next
            block++
        }

        override fun peak(): Double {
            val mix = cylinder?.mixBuffer ?: return 0.0

            return peakOf(left = mix.left, right = mix.right, frames = BLOCK_FRAMES)
        }
    }

    /** The engine: [PlaybackEngineDispatcher], its master stage included. */
    private class EngineRig(private val case: Case) : Rig() {
        private val clock = BackendClock(SAMPLE_RATE)
        private val commLink = KlangCommLink(capacity = 4096)

        private val context = AudioBackendContext.create(
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            commLink = commLink.backend,
            clock = clock,
            performanceTimeMs = { 0.0 },
            // A fixed seed, as the offline renderer has: without it the pools seed from the clock.
            phasePoolSeed = 1,
        )

        private val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also {
            it.setBackendStartTime(0.0)
        }

        private val out = StereoBuffer(BLOCK_FRAMES)
        private var block = 0

        /** The notes started so far: with [Case.katpNext], an odd note carries it, an even one [Case.katp]. */
        private var notes = 0

        init {
            if (case.dsl != null) {
                dispatcher.handle(KlangCommLink.Cmd.RegisterIgnitor(playbackId = PLAYBACK_ID, name = case.sound, dsl = case.dsl))
            }

            if (case.chain != null) {
                dispatcher.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = PLAYBACK_ID, name = CHAIN, dsl = case.chain))
            }

            if (case.duckTrigger) {
                val trigger = VoiceData.empty.copy(freqHz = FREQ_HZ, sound = "saw", cylinder = TRIGGER_ORBIT)

                schedule(data = trigger, startSec = 0.0, gateSec = SUSTAIN_SEC)
            }

            if (case.newNoteEveryBlocks == 0) {
                schedule(data = voiceData(case), startSec = 0.0, gateSec = case.gateSec)
            }
        }

        private fun schedule(data: VoiceData, startSec: Double, gateSec: Double) {
            dispatcher.handle(
                KlangCommLink.Cmd.ScheduleVoice(
                    playbackId = PLAYBACK_ID,
                    voice = ScheduledVoice(
                        playbackId = PLAYBACK_ID,
                        data = data,
                        startTime = startSec,
                        gateEndTime = startSec + gateSec,
                        playbackStartTime = 0.0,
                    ),
                )
            )
        }

        override fun step() {
            if (case.newNoteEveryBlocks > 0 && block % case.newNoteEveryBlocks == 0) {
                val katp = if (case.katpNextMap != null && notes % 2 == 1) case.katpNextMap else case.katpMap

                // Two blocks ahead, so the note is admitted (a start in the past is refused).
                schedule(
                    data = voiceData(case = case, katp = katp),
                    startSec = (block + 2) * BLOCK_FRAMES / SAMPLE_RATE.toDouble(),
                    gateSec = case.gateSec,
                )
                notes++
            }

            dispatcher.renderBlock(cursorFrame = clock.cursorFrame, out = out)
            block++
        }

        override fun peak(): Double = peakOf(left = out.left, right = out.right, frames = BLOCK_FRAMES)
    }
}
