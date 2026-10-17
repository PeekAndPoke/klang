/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_LOOKAHEAD_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_LOOKAHEAD_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DISTORT_AMOUNT
import io.peekandpoke.klang.audio_bridge.constants.DISTORT_OVERSAMPLE
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/*
 * The Katalyst's builders, ONE set for both doors (`docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`, rollout S5): the chain's
 * [KatalystBuilder], its stage builders and the modulators' builders, with TYPED member knobs (every value a
 * [KatalystKnobDsl], a number through `.kat`). The KlangScript door (`klangscript-libs`, `KatalystBuilders.kt` there)
 * adds `internal`, renamed `@KlangScript.Function` variants (`scriptReverb`, the script word in `name = "reverb"`) that
 * convert their `Any` arguments (`toKatalystKnob`) and call these members; the user-facing text of each knob lives on
 * that variant (KSP reads KDoc only from its own module), a member carries a one-line pointer.
 *
 *     Kat { it.eq { e -> e.band(freq = 300.kat, q = 0.8.kat, db = 2.kat) }.reverb(wet = 0.15.kat, size = 3.kat) }   // Kotlin
 *     Kat(k => k.eq(e => e.band(freq = 300, q = 0.8, db = 2.0)).reverb(0.15, 3))                                   // KlangScript
 *
 * Every stage parameter is non-null with the bare stage's default SYMBOL (the constants in
 * `constants/BusEffectDefaults.kt`, the same ones the [KatalystStageDsl] data classes read), so an omitted argument is
 * exactly what the bare stage carries; the script door substitutes the same symbol for its `null`. The one nullable
 * parameter is the reverb's `lowpass`, whose bare-stage value IS `null` (the engine's sample-rate relation, no wire
 * constant), not a stand-in for a default.
 */

// ── The chain ────────────────────────────────────────────────────────────────

/**
 * Builder for a [KatalystDsl] chain, handed to the `configure` lambda of `Kat.build(...)` / `Kat(...)`. Every stage
 * knob APPENDS one stage, in written order (the order IS the chain), and returns a new builder. The text of each
 * knob is on its script variant (`klangscript-libs`, `KatalystBuilders.kt`).
 */
data class KatalystBuilder(val node: KatalystDsl = KatalystDsl(emptyList())) {

    /** Appends [stage] as it is: the one primitive every stage knob ends in. */
    fun append(stage: KatalystStageDsl): KatalystBuilder = copy(node = KatalystDsl(node.stages + stage))

    /**
     * Appends the classic block ([KatalystDsl.classic]), AT MOST ONCE per builder: a builder that already holds the
     * block as a contiguous run is returned unchanged. The script knob `classic` has the reason.
     */
    fun classic(): KatalystBuilder {
        if (node.stages.containsInOrder(KatalystDsl.classic.stages)) {
            return this
        }

        return copy(node = KatalystDsl(node.stages + KatalystDsl.classic.stages))
    }

    /** Appends a resonant body; [material] is a [BodyMaterials] index or a slot carrying one (script knob `body`). */
    fun body(
        wet: KatalystKnobDsl = BODY_WET.kat,
        material: KatalystKnobDsl = SLOT_UNSET.kat,
        configure: ((KatalystBodyBuilder) -> KatalystBodyBuilder)? = null,
    ): KatalystBuilder = append(
        KatalystBodyBuilder(KatalystStageDsl.Body(material = material, wet = wet)).configured(configure).node,
    )

    /** [body] with the material by NAME, through [BodyMaterials.indexOf], the script knob's lookup (unknown = `none`). */
    fun body(
        wet: KatalystKnobDsl = BODY_WET.kat,
        material: String,
        configure: ((KatalystBodyBuilder) -> KatalystBodyBuilder)? = null,
    ): KatalystBuilder = body(wet = wet, material = BodyMaterials.indexOf(material).kat, configure = configure)

    /** Appends a formant bank; [vowel] is a [VowelBands] index or a slot carrying one (script knob `vowel`). */
    fun vowel(
        wet: KatalystKnobDsl = VOWEL_WET.kat,
        vowel: KatalystKnobDsl = SLOT_UNSET.kat,
        configure: ((KatalystVowelBuilder) -> KatalystVowelBuilder)? = null,
    ): KatalystBuilder = append(
        KatalystVowelBuilder(KatalystStageDsl.Vowel(vowel = vowel, wet = wet)).configured(configure).node,
    )

    /** [vowel] by NAME (`"a"`, `"soprano:o"`), through [VowelBands.indexOf], the script knob's lookup (unknown = `none`). */
    fun vowel(
        wet: KatalystKnobDsl = VOWEL_WET.kat,
        vowel: String,
        configure: ((KatalystVowelBuilder) -> KatalystVowelBuilder)? = null,
    ): KatalystBuilder = vowel(wet = wet, vowel = VowelBands.indexOf(vowel).kat, configure = configure)

    /** Appends an orbit delay (script knob `delay`). */
    fun delay(
        wet: KatalystKnobDsl = DELAY_WET.kat,
        time: KatalystKnobDsl = DELAY_TIME_SECONDS.kat,
        feedback: KatalystKnobDsl = DELAY_FEEDBACK.kat,
        configure: ((KatalystDelayBuilder) -> KatalystDelayBuilder)? = null,
    ): KatalystBuilder = append(
        KatalystDelayBuilder(KatalystStageDsl.Delay(wet = wet, time = time, feedback = feedback)).configured(configure).node,
    )

    /**
     * Appends an orbit reverb (script knob `reverb`). [lowpass] `null` is the bare stage's own value, the engine's
     * fixed damping (no wire constant, see [KatalystStageDsl.Reverb]).
     */
    fun reverb(
        wet: KatalystKnobDsl = REVERB_WET.kat,
        size: KatalystKnobDsl = REVERB_SIZE.kat,
        lowpass: KatalystKnobDsl? = null,
    ): KatalystBuilder = append(KatalystStageDsl.Reverb(wet = wet, size = size, lowpass = lowpass))

    /** Appends an orbit phaser; a bare one is silent (wet 0) (script knob `phaser`). */
    fun phaser(
        wet: KatalystKnobDsl = PHASER_WET.kat,
        rate: KatalystKnobDsl = PHASER_RATE_HZ.kat,
        center: KatalystKnobDsl = PHASER_CENTER_HZ.kat,
        sweep: KatalystKnobDsl = PHASER_SWEEP_HZ.kat,
        configure: ((KatalystPhaserBuilder) -> KatalystPhaserBuilder)? = null,
    ): KatalystBuilder = append(
        KatalystPhaserBuilder(
            KatalystStageDsl.Phaser(rate = rate, wet = wet, center = center, sweep = sweep),
        ).configured(configure).node,
    )

    /** Appends the orbit compressor; [lookahead] is a plain number fixed with the chain (script knob `compressor`). */
    fun compressor(
        threshold: KatalystKnobDsl = COMPRESSOR_THRESHOLD_DB.kat,
        ratio: KatalystKnobDsl = COMPRESSOR_RATIO.kat,
        knee: KatalystKnobDsl = COMPRESSOR_KNEE_DB.kat,
        attack: KatalystKnobDsl = COMPRESSOR_ATTACK_SECONDS.kat,
        release: KatalystKnobDsl = COMPRESSOR_RELEASE_SECONDS.kat,
        lookahead: Double = COMPRESSOR_LOOKAHEAD_SECONDS,
    ): KatalystBuilder = append(
        KatalystStageDsl.Compressor(
            threshold = threshold,
            ratio = ratio,
            knee = knee,
            attack = attack,
            release = release,
            lookahead = lookahead,
        ),
    )

    /**
     * Appends a limiter: a [KatalystStageDsl.Compressor] with the limiter numbers as its defaults (the preset lives
     * HERE, once, for both doors; `constants/MasterLimiterDefaults.kt`). The stage does not know it was a limiter
     * (script knob `limiter`).
     */
    fun limiter(
        threshold: KatalystKnobDsl = LIMITER_THRESHOLD_DB.kat,
        ratio: KatalystKnobDsl = LIMITER_RATIO.kat,
        knee: KatalystKnobDsl = LIMITER_KNEE_DB.kat,
        attack: KatalystKnobDsl = AUTHORED_LIMITER_ATTACK_SECONDS.kat,
        release: KatalystKnobDsl = LIMITER_RELEASE_SECONDS.kat,
        lookahead: Double = AUTHORED_LIMITER_LOOKAHEAD_SECONDS,
    ): KatalystBuilder = compressor(
        threshold = threshold,
        ratio = ratio,
        knee = knee,
        attack = attack,
        release = release,
        lookahead = lookahead,
    )

    /**
     * Appends a distortion of the bus mix; [shape] is a NAME through [DistortionShapes.indexOf], the script knob's
     * lookup (unknown = `soft`), and [shape] and [oversample] are plain values fixed with the chain, the forms the
     * script offers (script knob `distort`).
     */
    fun distort(
        amount: KatalystKnobDsl = DISTORT_AMOUNT.kat,
        shape: String = DistortionShapes.names[DistortionShapes.SOFT_INDEX],
        oversample: Int = DISTORT_OVERSAMPLE,
    ): KatalystBuilder = append(
        KatalystStageDsl.Distort(amount = amount, shape = DistortionShapes.indexOf(shape).toInt(), oversample = oversample),
    )

    /** Appends a sidechain duck; an omitted [orbit] is the "never set" marker, no ducking (script knob `duck`). */
    fun duck(
        orbit: KatalystKnobDsl = SLOT_UNSET.kat,
        depth: KatalystKnobDsl = DUCK_DEPTH.kat,
        attack: KatalystKnobDsl = DUCK_ATTACK_SECONDS.kat,
    ): KatalystBuilder = append(KatalystStageDsl.Duck(orbit = orbit, depth = depth, attack = attack))

    /**
     * Appends the mix equalizer, its sections from a [KatalystEqBuilder] (a section refuses a Kat expression at build
     * time, Q49) (script knob `eq`).
     */
    fun eq(configure: ((KatalystEqBuilder) -> KatalystEqBuilder)? = null): KatalystBuilder =
        append(KatalystStageDsl.Eq(sections = KatalystEqBuilder().configured(configure).sections))

    /** Appends make-up gain, the group fader; unity is the stage's identity, not a tuned constant (script knob `gain`). */
    fun gain(gain: KatalystKnobDsl = 1.0.kat): KatalystBuilder = append(KatalystStageDsl.Gain(gain = gain))

    // ── Arithmetic: the Ignitor's operators on the bus ───────────────────────────────────────────────────────────
    //
    // One typed function per word (as the Ignitor's arithmetic since rollout S4), and the script's aliases (`add`,
    // `sub`, `mul`, `negate`, `power`) as one-line typed delegates to their main word, so a script ports word for word.
    // Plain functions, no operators: `k + x` would read as arithmetic on the builder.

    /** Appends a [KatalystStageDsl.Plus] stage (script knob `plus`, alias `add`). */
    fun plus(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Plus(other = other))

    /** Appends a [KatalystStageDsl.Minus] stage (script knob `minus`, alias `sub`). */
    fun minus(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Minus(other = other))

    /** Appends a [KatalystStageDsl.Times] stage (script knob `times`, alias `mul`). */
    fun times(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Times(other = other))

    /** Appends a [KatalystStageDsl.Div] stage (script knob `div`). */
    fun div(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Div(other = other))

    /** Flips the polarity: a [times] by -1 (script knob `neg`, alias `negate`). */
    fun neg(): KatalystBuilder = times((-1.0).kat)

    /** Alias for [plus], the script's `add`. */
    fun add(other: KatalystKnobDsl): KatalystBuilder = plus(other)

    /** Alias for [minus], the script's `sub`. */
    fun sub(other: KatalystKnobDsl): KatalystBuilder = minus(other)

    /** Alias for [times], the script's `mul`. */
    fun mul(other: KatalystKnobDsl): KatalystBuilder = times(other)

    /** Alias for [neg], the script's `negate`. */
    fun negate(): KatalystBuilder = neg()

    /** Alias for [pow], the script's `power`. */
    fun power(exp: KatalystKnobDsl): KatalystBuilder = pow(exp)

    /** Appends the [KatalystStageDsl.Abs] stage (script knob `abs`). */
    fun abs(): KatalystBuilder = append(KatalystStageDsl.Abs)

    /** Appends a [KatalystStageDsl.Pow] stage (script knob `pow`, alias `power`). */
    fun pow(exp: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Pow(exp = exp))

    /**
     * The bus, but at least [other]: a floor is the per-sample MAXIMUM, so this builds [KatalystStageDsl.Max]. Do not
     * "correct" the crossing (guardrail, `CLAUDE.md`) (script knob `min`).
     */
    fun min(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Max(other = other))

    /**
     * The bus, but at most [other]: a cap is the per-sample MINIMUM, so this builds [KatalystStageDsl.Min]. Do not
     * "correct" the crossing (guardrail, `CLAUDE.md`) (script knob `max`).
     */
    fun max(other: KatalystKnobDsl): KatalystBuilder = append(KatalystStageDsl.Min(other = other))

    // ── Composition: groups of stages as values (rollout S6) ──────────────────────────────────────────────────────
    //
    // The script variants (`scriptSerial`, `scriptParallel`, `scriptBlend`, `scriptBands`) wrap every stage, branch and
    // band processor in the script's check (`runStage`) before handing it here, so these members only invoke them.

    /** Runs the chain through [stages] in series, first to last: `serial(a, b)` is `b(a(this))` (script knob `serial`). */
    fun serial(vararg stages: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder =
        stages.fold(this) { builder, stage -> stage(builder) }

    /**
     * Runs the bus through [branches] side by side and sums them, each branch given an EMPTY builder; no branch is the
     * chain as it is, one branch its stages in place (script knob `parallel`).
     */
    fun parallel(vararg branches: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder {
        val built = branches.map { branch -> branch(KatalystBuilder()).node }

        return when (built.size) {
            0 -> this
            1 -> copy(node = KatalystDsl(node.stages + built[0].stages))
            else -> append(KatalystStageDsl.Parallel(branches = built))
        }
    }

    /**
     * A dry/wet blend, the linear law: the bus times `1 - wet` beside [branch]'s stages (from an empty builder) times
     * [wet]. A plain number, as the script takes it (recorded asymmetry with the Ignitor's, which takes a slot or a
     * signal); one that is not finite reads as 0, the dry bus (script knob `blend`).
     */
    fun blend(wet: Double, branch: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder {
        // NaN-guard on a value the author can write: an unset blend is no blend. The one guard, for both doors.
        val share = if (wet.isFinite()) wet else 0.0
        val processed = branch(KatalystBuilder())

        return append(
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl.of(KatalystStageDsl.Gain(gain = (1.0 - share).kat)),
                    processed.gain(share.kat).node,
                ),
            ),
        )
    }

    /**
     * Splits the bus into frequency bands from this position, the bands from a [KatalystBandsBuilder] (`band`, `cut`),
     * the Linkwitz-Riley crossover in `BandsBuilders.kt`; no [configure], or no `cut`, is the one band, its stages in
     * place (script knob `bands`).
     */
    fun bands(configure: ((KatalystBandsBuilder) -> KatalystBandsBuilder)? = null): KatalystBuilder =
        copy(node = KatalystDsl(node.stages + KatalystBandsBuilder().configured(configure).split()))
}

/**
 * True when [other] appears in this list as a contiguous run, which is what "the classic block is already in this
 * builder" means. Content equality, not identity: a chain hand-built from the same stages is the same chain
 * everywhere else in the Katalyst, so it must be here too.
 *
 * A plain double loop over two short lists, run once per `classic()` call at construction time and never on an audio
 * path.
 */
private fun List<KatalystStageDsl>.containsInOrder(other: List<KatalystStageDsl>): Boolean {
    if (other.isEmpty() || other.size > size) {
        return false
    }

    for (start in 0..(size - other.size)) {
        var matches = true

        for (i in other.indices) {
            if (this[start + i] != other[i]) {
                matches = false
                break
            }
        }

        if (matches) {
            return true
        }
    }

    return false
}

// ── The stage builders ───────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Body] stage. Knob: `floor`. */
data class KatalystBodyBuilder(val node: KatalystStageDsl.Body) {

    /** Minimum dry share kept in the mix; the text is on the script knob `floor` (`scriptFloor` in `klangscript-libs`). */
    fun floor(floor: KatalystKnobDsl): KatalystBodyBuilder = copy(node = node.copy(floor = floor))
}

/** Builder for a [KatalystStageDsl.Vowel] stage. Knob: `floor`. */
data class KatalystVowelBuilder(val node: KatalystStageDsl.Vowel) {

    /** Minimum dry share kept between the formants; the text is on the script knob `floor` (`scriptFloor` in `klangscript-libs`). */
    fun floor(floor: KatalystKnobDsl): KatalystVowelBuilder = copy(node = node.copy(floor = floor))
}

/** Builder for a [KatalystStageDsl.Delay] stage. Knob: `cap`. */
data class KatalystDelayBuilder(val node: KatalystStageDsl.Delay) {

    /** Level the recirculating signal saturates toward; the text is on the script knob `cap` (`scriptCap` in `klangscript-libs`). */
    fun cap(cap: KatalystKnobDsl): KatalystDelayBuilder = copy(node = node.copy(cap = cap))
}

/** Builder for a [KatalystStageDsl.Phaser] stage. Knob: `floor`. */
data class KatalystPhaserBuilder(val node: KatalystStageDsl.Phaser) {

    /** Minimum dry coefficient of the wet/dry law; the text is on the script knob `floor` (`scriptFloor` in `klangscript-libs`). */
    fun floor(floor: KatalystKnobDsl): KatalystPhaserBuilder = copy(node = node.copy(floor = floor))
}

// ── The modulators' builders ─────────────────────────────────────────────────
//
// Handed to the `configure` lambda of the modulator doors (`Kat.sine(freq, configure)` and the rest): the Ignitor's
// oscillator builders' shape with only the knobs a bus LFO keeps (`phase` on every periodic shape, `duty` on the
// square). The noises (`Kat.perlin`, `Kat.berlin`) are flat and have no builder.

/** Builder for a [KatalystKnobDsl.Sine], handed to the `configure` lambda of `Kat.sine(...)`. Knob: `phase`. */
data class KatalystSineBuilder(val node: KatalystKnobDsl.Sine) {

    /** Where in its cycle the sine is read; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: KatalystKnobDsl): KatalystSineBuilder = copy(node = node.copy(phase = phase))
}

/** Builder for a [KatalystKnobDsl.Saw], handed to the `configure` lambda of `Kat.saw(...)`. Knob: `phase`. */
data class KatalystSawBuilder(val node: KatalystKnobDsl.Saw) {

    /** Where in its cycle the saw is read; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: KatalystKnobDsl): KatalystSawBuilder = copy(node = node.copy(phase = phase))
}

/** Builder for a [KatalystKnobDsl.Ramp], handed to the `configure` lambda of `Kat.ramp(...)`. Knob: `phase`. */
data class KatalystRampBuilder(val node: KatalystKnobDsl.Ramp) {

    /** Where in its cycle the ramp is read; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: KatalystKnobDsl): KatalystRampBuilder = copy(node = node.copy(phase = phase))
}

/** Builder for a [KatalystKnobDsl.Tri], handed to the `configure` lambda of `Kat.tri(...)`. Knob: `phase`. */
data class KatalystTriBuilder(val node: KatalystKnobDsl.Tri) {

    /** Where in its cycle the triangle is read; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: KatalystKnobDsl): KatalystTriBuilder = copy(node = node.copy(phase = phase))
}

/** Builder for a [KatalystKnobDsl.Square], handed to the `configure` lambda of `Kat.square(...)`. Knobs: `duty`, `phase`. */
data class KatalystSquareBuilder(val node: KatalystKnobDsl.Square) {

    /** The share of each cycle spent high; the text is on the script knob `duty` (`scriptDuty` in `klangscript-libs`). */
    fun duty(width: KatalystKnobDsl): KatalystSquareBuilder = copy(node = node.copy(duty = width))

    /** Where in its cycle the square is read; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: KatalystKnobDsl): KatalystSquareBuilder = copy(node = node.copy(phase = phase))
}
