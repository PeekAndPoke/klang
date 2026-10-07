/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Ignitor` for KlangScript (short name `Ign`): builds IgnitorDsl signal graphs, the per-voice instrument.
 *
 * Provides factory methods for all oscillator primitives, noise sources, and super oscillators.
 * Returns [IgnitorDsl] instances that can be composed via extension methods (lowpass, adsr, mul, etc.)
 * and passed directly to `.sound()`:
 *
 * ```
 * let pad = Ignitor.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5).classic()
 * note("c3 e3 g3").sound(pad).adsrOff()
 * ```
 *
 * The playback denormalizes inline ignitor references at the wire boundary via the
 * player's ignitor registry — no explicit registration step is needed.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Ignitor")
object KlangScriptIgnitor {

    override fun toString(): String = "[Ignitor object]"

    /**
     * The canonical open slots of a custom sound: `Ignitor.slot.analog`, `Ignitor.slot.voices`,
     * `Ignitor.slot.lpf.freq` and the rest. Each is the same `IgnitorDsl.Param(name, default)` the built-in sounds
     * use, so a custom sound that hands one to a knob opts in to sprudel's modulation of it (the `analog`, `voices`,
     * `spread`, ... knobs on the oscillator builders). Without opting in, a custom sound ignores it. For
     * example, `Ignitor.sine(x => x.analog(Ignitor.slot.analog))` is a sine whose drift follows sprudel's
     * `analog`. `Ign.slot` is the same.
     */
    // The script path to KlangScriptIgnitorSlots; the Kotlin door is IgnitorDsl.Slots, the same objects.
    // (A member property's docs carry no samples in KSP, so the example is prose.)
    @KlangScript.Property
    val slot: KlangScriptIgnitorSlots = KlangScriptIgnitorSlots

    // ── Oscillator Primitives ────────────────────────────────────────────────
    //
    // Every oscillator door has the same shape: `freq` first (omit it for the playing note's
    // pitch, pass Hz for a fixed frequency, so `Ignitor.sine(5)` is a 5 Hz LFO), then an optional
    // `configure` lambda that receives the oscillator's BUILDER and returns it. The builder
    // carries exactly this oscillator's knobs and nothing else (see `IgnitorBuilders.kt`);
    // processing (`.lowpass()`, `.adsr()`, `.mul()`, ...) happens on the returned sound, outside
    // the lambda:
    //
    //     Ignitor.saw(x => x.analog(3).resetSamples(4)).lowpass(800)

    /** Returns the voice's note frequency (e.g. 440 Hz for A4). Usable anywhere a frequency value is needed. */
    @KlangScript.Method
    fun freq(): IgnitorDsl = IgnitorDsl.Freq

    /**
     * Creates a sine wave oscillator. The builder can add banks of sine PARTIALS at multiples of the sine's own
     * frequency, rendered in one pass: `harmonics(count, rolloff)` at `2f, 3f, 4f ...`, `octaves(count, rolloff)` at
     * `2f, 4f, 8f ...`, `suboctaves(count, rolloff)` at `f/2, f/4 ...`; `fundamental(gain)` levels the sine itself
     * and `analogSpread(0..1)` sets whether the partials drift as one oscillator or on their own. With no bank
     * knob set this is the plain sine it always was. The multiples follow the door's `freq`, so
     * `Ignitor.sine(Ignitor.freq().mul(2), x => x.harmonics(3))` is the even series `2f, 4f, 6f, 8f`.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency (e.g. 5 for a 5 Hz LFO).
     * @param configure receives the [OscSineBuilder] (knobs: `analog`, `fundamental`, `harmonics`, `octaves`, `suboctaves`, `analogSpread`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.sine(x => x.analog(3)).lowpass(2000)
     * Ignitor.sine(x => x.harmonics(7))                  // a bass the ear rebuilds on small speakers: f plus 2f .. 8f
     * Ignitor.sine(x => x.suboctaves(1, 0))              // the classic sub oscillator, f and f/2 at equal level
     * ```
     */
    @KlangScript.Method
    fun sine(freq: IgnitorDslLike? = null, configure: ((OscSineBuilder) -> OscSineBuilder)? = null): IgnitorDsl =
        OscSineBuilder(IgnitorDsl.Sine(freq = freq.orNoteFreq())).configuredBy("Ignitor.sine", configure).node

    /**
     * Creates a sawtooth wave oscillator (analog flyback shape, no PolyBLEP, softens with pitch).
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSawBuilder] (knobs: `analog`, `resetSamples`, `shapeMax`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.saw(x => x.resetSamples(4.0).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun saw(freq: IgnitorDslLike? = null, configure: ((OscSawBuilder) -> OscSawBuilder)? = null): IgnitorDsl =
        OscSawBuilder(IgnitorDsl.Saw(freq = freq.orNoteFreq())).configuredBy("Ignitor.saw", configure).node

    /**
     * Creates a square wave oscillator: a variable-duty pulse whose `duty` defaults to 50%.
     * `square`, `pulse` and `pulze` are one pulse oscillator; this door builds the flank-shaped [IgnitorDsl.Pulze],
     * [pulze] the raw, aliased one.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSquareBuilder] (knobs: `duty`, `analog`, `flankSamples`, `riseFlank`, `fallFlank`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.square(x => x.duty(0.3).flankSamples(4.0))
     * ```
     */
    @KlangScript.Method
    fun square(freq: IgnitorDslLike? = null, configure: ((OscSquareBuilder) -> OscSquareBuilder)? = null): IgnitorDsl =
        OscSquareBuilder(IgnitorDsl.Pulze(freq = freq.orNoteFreq())).configuredBy("Ignitor.square", configure).node

    /**
     * Creates a triangle wave oscillator. Its flanks are fixed fully open; `analog` and `phase` are its knobs.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscTriBuilder] (knobs: `analog`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.tri(x => x.analog(3))
     * ```
     */
    @KlangScript.Method
    fun tri(freq: IgnitorDslLike? = null, configure: ((OscTriBuilder) -> OscTriBuilder)? = null): IgnitorDsl =
        OscTriBuilder(IgnitorDsl.Tri(freq = freq.orNoteFreq())).configuredBy("Ignitor.tri", configure).node

    /**
     * Creates a ramp (reverse sawtooth) wave oscillator.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscRampBuilder] (knobs: `analog`, `resetSamples`, `shapeMax`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.ramp(x => x.resetSamples(4.0).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun ramp(freq: IgnitorDslLike? = null, configure: ((OscRampBuilder) -> OscRampBuilder)? = null): IgnitorDsl =
        OscRampBuilder(IgnitorDsl.Ramp(freq = freq.orNoteFreq())).configuredBy("Ignitor.ramp", configure).node

    /**
     * Creates a naive sawtooth without anti-aliasing (brighter, harsher).
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscZawtoothBuilder] (knobs: `analog`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.zawtooth(x => x.analog(3))
     * ```
     */
    @KlangScript.Method
    fun zawtooth(freq: IgnitorDslLike? = null, configure: ((OscZawtoothBuilder) -> OscZawtoothBuilder)? = null): IgnitorDsl =
        OscZawtoothBuilder(IgnitorDsl.Zawtooth(freq = freq.orNoteFreq())).configuredBy("Ignitor.zawtooth", configure).node

    /**
     * Creates a raw ramp ("zamp"): a naive reverse sawtooth without anti-aliasing (the raw [ramp]).
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscZampBuilder] (knobs: `analog`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.zamp(x => x.analog(3))
     * ```
     */
    @KlangScript.Method
    fun zamp(freq: IgnitorDslLike? = null, configure: ((OscZampBuilder) -> OscZampBuilder)? = null): IgnitorDsl =
        OscZampBuilder(IgnitorDsl.Zamp(freq = freq.orNoteFreq())).configuredBy("Ignitor.zamp", configure).node

    /**
     * Creates an impulse (click) oscillator.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscImpulseBuilder] (knobs: `analog`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.impulse(x => x.analog(3))
     * ```
     */
    @KlangScript.Method
    fun impulse(freq: IgnitorDslLike? = null, configure: ((OscImpulseBuilder) -> OscImpulseBuilder)? = null): IgnitorDsl =
        OscImpulseBuilder(IgnitorDsl.Impulse(freq = freq.orNoteFreq())).configuredBy("Ignitor.impulse", configure).node

    /**
     * Creates a raw pulse ("pulze"): a naive, aliased pulse with variable duty cycle (the raw counterpart of [square]).
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscPulzeBuilder] (knobs: `duty`, `analog`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.pulze(x => x.duty(0.3))
     * ```
     */
    @KlangScript.Method
    fun pulze(freq: IgnitorDslLike? = null, configure: ((OscPulzeBuilder) -> OscPulzeBuilder)? = null): IgnitorDsl =
        OscPulzeBuilder(IgnitorDsl.RawPulze(freq = freq.orNoteFreq())).configuredBy("Ignitor.pulze", configure).node

    /** Creates a silent ignitor (zero output). */
    @KlangScript.Method
    fun silence(): IgnitorDsl = IgnitorDsl.Silence

    // ── Noise Sources ────────────────────────────────────────────────────────

    /**
     * Creates a white noise source. Each call yields a fresh DSL instance.
     * @param color spectral tilt: 0 = flat white (default), <0 darkens toward pink/brown, >0 brightens (−1..1).
     */
    @KlangScript.Method
    fun whitenoise(color: IgnitorDslLike = 0.0): IgnitorDsl =
        IgnitorDsl.WhiteNoise(color = color.toIgnitorDsl())

    /**
     * Creates a brown noise source (1/f^2 spectrum, deeper). Each call yields a fresh DSL instance.
     * @param depth per-sample white-leak (default 0.02): lower = deeper/slower brown, higher = brighter.
     */
    @KlangScript.Method
    fun brownnoise(depth: IgnitorDslLike = 0.02): IgnitorDsl =
        IgnitorDsl.BrownNoise(depth = depth.toIgnitorDsl())

    /** Creates a pink noise source (1/f spectrum). Each call yields a fresh DSL instance. */
    @KlangScript.Method
    fun pinknoise(): IgnitorDsl = IgnitorDsl.PinkNoise()

    /**
     * Creates a Perlin noise source (smooth organic noise).
     * @param rate walk speed (default 1.0).
     * @param octaves fractal-Brownian-motion octaves: 1 = plain (default), higher = more detail (capped at 8).
     * @param persistence fBm amplitude falloff per octave (default 0.5; lower = quieter upper octaves).
     */
    @KlangScript.Method
    fun perlin(
        rate: IgnitorDslLike = 1.0,
        octaves: IgnitorDslLike = 1.0,
        persistence: IgnitorDslLike = 0.5,
    ): IgnitorDsl =
        IgnitorDsl.PerlinNoise(
            rate = rate.toIgnitorDsl(),
            octaves = octaves.toIgnitorDsl(),
            persistence = persistence.toIgnitorDsl(),
        )

    /**
     * Creates a Berlin noise source (piecewise-linear interpolated noise).
     * @param rate walk speed (default 1.0).
     * @param octaves fBm octaves: 1 = plain (default), higher = more detail (capped at 8).
     * @param persistence fBm amplitude falloff per octave (default 0.5).
     */
    @KlangScript.Method
    fun berlin(
        rate: IgnitorDslLike = 1.0,
        octaves: IgnitorDslLike = 1.0,
        persistence: IgnitorDslLike = 0.5,
    ): IgnitorDsl =
        IgnitorDsl.BerlinNoise(
            rate = rate.toIgnitorDsl(),
            octaves = octaves.toIgnitorDsl(),
            persistence = persistence.toIgnitorDsl(),
        )

    /**
     * Creates a dust source (sparse random impulses).
     * @param density impulse rate 0..1.
     * @param tail heavy-tailed amplitude exponent: 1 = uniform (default), >1 = mostly-tiny / rare-loud (vinyl pops).
     * @param bipolar random ±sign when > 0.5; default 0 = unipolar.
     */
    @KlangScript.Method
    fun dust(
        density: IgnitorDslLike = 0.2,
        tail: IgnitorDslLike = 1.0,
        bipolar: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.Dust(
            density = density.toIgnitorDsl(),
            tail = tail.toIgnitorDsl(),
            bipolar = bipolar.toIgnitorDsl(),
        )

    /**
     * Creates a crackle source (chaotic recurrence → bipolar pops).
     * @param chaos drives the chaotic map: ~1.0 sparse, 1.5 = clear crackle (default), ~2.0 dense/noisy.
     */
    @KlangScript.Method
    fun crackle(chaos: IgnitorDslLike = 1.5): IgnitorDsl =
        IgnitorDsl.Crackle(chaos = chaos.toIgnitorDsl())

    // ── Super Oscillators ────────────────────────────────────────────────────

    /**
     * Creates a supersaw (multiple detuned sawtooth voices).
     * Unison and character knobs live on the builder; the combined `phasePool(...)` call takes optional literals, so named
     * subsets work: `x.phasePool()`, `x.phasePool(kMin = 0.2)`. Base wrappers (`.lowpass()`, `.adsr()`, ...) go outside the lambda.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperSawBuilder] (knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.supersaw(x => x.voices(9).spread(0.1).spreadPower(1.5).analog(5.0)).lowpass(800)
     * ```
     */
    @KlangScript.Method
    fun supersaw(freq: IgnitorDslLike? = null, configure: ((OscSuperSawBuilder) -> OscSuperSawBuilder)? = null): IgnitorDsl =
        OscSuperSawBuilder(IgnitorDsl.SuperSaw(freq = freq.orNoteFreq())).configuredBy("Ignitor.supersaw", configure).node

    /**
     * Creates a supersine (multiple detuned sine oscillators).
     * Unison and character knobs live on the builder; the combined `phasePool(...)` call takes optional literals, so named
     * subsets work: `x.phasePool()`, `x.phasePool(kMin = 0.2)`. Base wrappers (`.lowpass()`, `.adsr()`, ...) go outside the lambda.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperSineBuilder] (knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.supersine(x => x.voices(9).spread(0.1).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun supersine(freq: IgnitorDslLike? = null, configure: ((OscSuperSineBuilder) -> OscSuperSineBuilder)? = null): IgnitorDsl =
        OscSuperSineBuilder(IgnitorDsl.SuperSine(freq = freq.orNoteFreq())).configuredBy("Ignitor.supersine", configure).node

    /**
     * Creates a supersquare (multiple detuned square oscillators).
     * Unison and character knobs live on the builder; the combined `phasePool(...)` call takes optional literals, so named
     * subsets work: `x.phasePool()`, `x.phasePool(kMin = 0.2)`. Base wrappers (`.lowpass()`, `.adsr()`, ...) go outside the lambda.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperSquareBuilder] (knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.supersquare(x => x.voices(9).spread(0.1).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun supersquare(freq: IgnitorDslLike? = null, configure: ((OscSuperSquareBuilder) -> OscSuperSquareBuilder)? = null): IgnitorDsl =
        OscSuperSquareBuilder(IgnitorDsl.SuperSquare(freq = freq.orNoteFreq())).configuredBy("Ignitor.supersquare", configure).node

    /**
     * Creates a supertri (multiple detuned triangle oscillators).
     * Unison and character knobs live on the builder; the combined `phasePool(...)` call takes optional literals, so named
     * subsets work: `x.phasePool()`, `x.phasePool(kMin = 0.2)`. Base wrappers (`.lowpass()`, `.adsr()`, ...) go outside the lambda.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperTriBuilder] (knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.supertri(x => x.voices(9).spread(0.1).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun supertri(freq: IgnitorDslLike? = null, configure: ((OscSuperTriBuilder) -> OscSuperTriBuilder)? = null): IgnitorDsl =
        OscSuperTriBuilder(IgnitorDsl.SuperTri(freq = freq.orNoteFreq())).configuredBy("Ignitor.supertri", configure).node

    /**
     * Creates a superramp (multiple detuned ramp oscillators, the mirror of [supersaw]).
     * Unison and character knobs live on the builder; the combined `phasePool(...)` call takes optional literals, so named
     * subsets work: `x.phasePool()`, `x.phasePool(kMin = 0.2)`. Base wrappers (`.lowpass()`, `.adsr()`, ...) go outside the lambda.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperRampBuilder] (knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`) and returns it.
     *
     * ```KlangScript
     * Ignitor.superramp(x => x.voices(9).spread(0.1).analog(5.0))
     * ```
     */
    @KlangScript.Method
    fun superramp(freq: IgnitorDslLike? = null, configure: ((OscSuperRampBuilder) -> OscSuperRampBuilder)? = null): IgnitorDsl =
        OscSuperRampBuilder(IgnitorDsl.SuperRamp(freq = freq.orNoteFreq())).configuredBy("Ignitor.superramp", configure).node

    // ── Physical Models ──────────────────────────────────────────────────────

    /**
     * Creates a Karplus-Strong plucked string model.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscPluckBuilder] (knobs: `decay`, `brightness`, `pickPosition`, `stiffness`, `analog`) and returns it.
     *
     * ```KlangScript
     * Ignitor.pluck(x => x.decay(0.99).brightness(0.45).pickPosition(0.5))
     * ```
     */
    @KlangScript.Method
    fun pluck(freq: IgnitorDslLike? = null, configure: ((OscPluckBuilder) -> OscPluckBuilder)? = null): IgnitorDsl =
        OscPluckBuilder(
            // Sealed constants, not the node's open `Slots.*` params: a custom pluck ignores sprudel's
            // per-note modulation unless the author opts in with `Ignitor.slot.*` (see [KlangScriptIgnitorSlots]).
            IgnitorDsl.Pluck(
                freq = freq.orNoteFreq(),
                decay = IgnitorDsl.Constant(0.996),
                brightness = IgnitorDsl.Constant(0.5),
                pickPosition = IgnitorDsl.Constant(0.5),
                stiffness = IgnitorDsl.Constant(0.0),
            ),
        ).configuredBy("Ignitor.pluck", configure).node

    /**
     * Creates a unison Karplus-Strong plucked string model.
     *
     * @param freq frequency, omit for the playing note's pitch, or pass Hz for a fixed frequency.
     * @param configure receives the [OscSuperPluckBuilder] (knobs: `voices`, `spread`, `decay`, `brightness`, `pickPosition`, `stiffness`, `analog`, `analogSpread`) and returns it.
     *
     * ```KlangScript
     * Ignitor.superpluck(x => x.voices(6).spread(0.15).decay(0.995))
     * ```
     */
    @KlangScript.Method
    fun superpluck(freq: IgnitorDslLike? = null, configure: ((OscSuperPluckBuilder) -> OscSuperPluckBuilder)? = null): IgnitorDsl =
        OscSuperPluckBuilder(
            // Sealed constants like [pluck]; unlike the super oscillators, the unison pair is sealed too.
            IgnitorDsl.SuperPluck(
                freq = freq.orNoteFreq(),
                voices = IgnitorDsl.Constant(8.0),
                spread = IgnitorDsl.Constant(0.2),
                decay = IgnitorDsl.Constant(0.996),
                brightness = IgnitorDsl.Constant(0.5),
                pickPosition = IgnitorDsl.Constant(0.5),
                stiffness = IgnitorDsl.Constant(0.0),
            ),
        ).configuredBy("Ignitor.superpluck", configure).node

    // ── Parameter Slot ───────────────────────────────────────────────────────

    /**
     * Creates a named parameter slot with a default value.
     *
     * Param slots are the leaf nodes of the ignitor tree — they produce a constant signal
     * at [default] unless the pattern writes the slot with `ignitorParam` at play time.
     *
     * `Ignitor.slot` holds the names a sprudel door already writes, so placing one of those wires that
     * door into your instrument. The one to know is `pregain`: how hard the pattern plays INTO
     * the instrument, written by `pregain(x)`, with the default 1.0 and no meaning of its own
     * beyond where you place it. `analog`, `voices`, `spread`, `duty`, `density`, `decay`,
     * `brightness`, `pickPosition`, `stiffness` and `rate` are the others. Reach for
     * `Ignitor.slot.<name>` rather than retyping the name and the default here, so one default serves
     * every instrument; a name of your own is what this door is for.
     *
     * A non-finite value written into a slot reads as UNSET: the leaf falls back to [default].
     *
     * @param name parameter name — used for `ignitorParam` overrides and UI discovery
     * @param default constant value when no override is provided
     * @param description human-readable description for documentation
     */
    @KlangScript.Method
    fun param(name: String, default: Double, description: String = ""): IgnitorDsl =
        IgnitorDsl.Param(name, default, description)

    // ── Constant ────────────────────────────────────────────────────────────

    /**
     * Creates a fixed constant value that cannot be overridden by ignitorParams.
     *
     * Use when you want an explicit, locked value in the signal graph.
     *
     * @param value the constant value
     */
    @KlangScript.Method
    fun constant(value: Double): IgnitorDsl =
        IgnitorDsl.Constant(value)

    // ── Dispatch / Selection ─────────────────────────────────────────────────

    /**
     * Creates a variants ignitor — selects one of [children] per note based on the
     * voice's `soundIndex`. Lets a single sound expose multiple flavours,
     * addressable via the `name:n` mini-notation or `.n(...)` pattern.
     *
     * Index wraps with floor-mod semantics: with N children, index `k` selects
     * `children[k.mod(N)]`, so negative indices wrap from the end and overflow
     * wraps to zero. Notes with no `:n` default to the first variant. With no children,
     * `Ignitor.variants()` plays silence.
     *
     * Same selection mechanism used by sample banks (`bd:0`, `bd:1`, …). Nested
     * `variants(...)` all dispatch on the same `soundIndex`, so a single switching
     * axis can drive correlated changes throughout the tree.
     *
     * ```
     * let combined = Ignitor.variants(Ignitor.sine(), Ignitor.saw()).classic()
     * note("a b c:1 d:1").sound(combined)   // a/b → sine, c:1/d:1 → saw
     * ```
     *
     * @param children candidate ignitors, indexed from 0
     */
    @KlangScript.Method
    fun variants(vararg children: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Variants(children.toList())
}

/**
 * The short name of [KlangScriptIgnitor]: the same object under a second name, so `Ign.sine()` is
 * `Ignitor.sine()` and `Ign.slot.lpf.freq` is `Ignitor.slot.lpf.freq`, for every member. Songs use it;
 * the docs spell `Ignitor` out.
 *
 * ```KlangScript
 * note("c3 e3 g3").sound(Ign.supersaw(x => x.voices(9)).lowpass(1200).classic())
 * ```
 *
 * @category object
 * @tags ignitor, ign, alias, instrument
 */
@KlangScript.Constant
val Ign: KlangScriptIgnitor = KlangScriptIgnitor

/** The door convention for `freq`: null means "the playing note's pitch" ([IgnitorDsl.Freq]). */
private fun IgnitorDslLike?.orNoteFreq(): IgnitorDsl = this?.toIgnitorDsl() ?: IgnitorDsl.Freq
