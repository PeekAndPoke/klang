package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Osc object for KlangScript — builds IgnitorDsl signal graphs.
 *
 * Provides factory methods for all oscillator primitives, noise sources, and super oscillators.
 * Returns [IgnitorDsl] instances that can be composed via extension methods (lowpass, adsr, mul, etc.)
 * and passed directly to `.sound()`:
 *
 * ```
 * let pad = Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5)
 * note("c3 e3 g3").sound(pad)
 * ```
 *
 * The playback denormalizes inline ignitor references at the wire boundary via the
 * player's ignitor registry — no explicit registration step is needed.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Osc")
object KlangScriptOsc {

    override fun toString(): String = "[Osc object]"

    /**
     * Canonical open parameter slots — `Osc.slot.analog`, `Osc.slot.voices`, etc.
     * Equivalent to the top-level [KlangScriptOscSlot] object.
     */
    @KlangScript.Property
    val slot: KlangScriptOscSlot = KlangScriptOscSlot

    // ── Oscillator Primitives ────────────────────────────────────────────────
    //
    // freq default: Freq (voice note frequency). Pass a value in Hz for a fixed frequency (e.g. Osc.sine(5) = 5 Hz LFO).

    /** Returns the voice's note frequency (e.g. 440 Hz for A4). Usable anywhere a frequency value is needed. */
    @KlangScript.Method
    fun freq(): IgnitorDsl = IgnitorDsl.Freq

    /**
     * Creates a sine wave oscillator.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency (e.g. 5 for a 5 Hz LFO).
     */
    @KlangScript.Method
    fun sine(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Sine(freq = freq.toIgnitorDsl())

    /**
     * Creates a sawtooth wave oscillator (PolyBLEP anti-aliased).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun saw(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Sawtooth(freq = freq.toIgnitorDsl())

    /**
     * Creates a square wave oscillator (PolyBLEP anti-aliased).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun square(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Square(freq = freq.toIgnitorDsl())

    /**
     * Creates a triangle wave oscillator.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun triangle(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Triangle(freq = freq.toIgnitorDsl())

    /**
     * Creates a ramp (reverse sawtooth) wave oscillator.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun ramp(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Ramp(freq = freq.toIgnitorDsl())

    /**
     * Creates a naive sawtooth without anti-aliasing (brighter/harsher).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun zawtooth(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Zawtooth(freq = freq.toIgnitorDsl())

    /**
     * Creates an impulse (click) oscillator.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun impulse(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Impulse(freq = freq.toIgnitorDsl())

    /**
     * Creates a pulse wave with variable duty cycle.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun pulze(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Pulze(freq = freq.toIgnitorDsl())

    /** Creates a silent ignitor (zero output). */
    @KlangScript.Method
    fun silence(): IgnitorDsl = IgnitorDsl.Silence

    // ── Noise Sources ────────────────────────────────────────────────────────

    /** Creates a white noise source (flat spectrum). Each call yields a fresh DSL instance. */
    @KlangScript.Method
    fun whitenoise(): IgnitorDsl = IgnitorDsl.WhiteNoise()

    /** Creates a brown noise source (1/f^2 spectrum, deeper). Each call yields a fresh DSL instance. */
    @KlangScript.Method
    fun brownnoise(): IgnitorDsl = IgnitorDsl.BrownNoise()

    /** Creates a pink noise source (1/f spectrum). Each call yields a fresh DSL instance. */
    @KlangScript.Method
    fun pinknoise(): IgnitorDsl = IgnitorDsl.PinkNoise()

    /** Creates a Perlin noise source (smooth organic noise). */
    @KlangScript.Method
    fun perlin(rate: IgnitorDslLike = 1.0): IgnitorDsl =
        IgnitorDsl.PerlinNoise(rate = rate.toIgnitorDsl())

    /** Creates a Berlin noise source (piecewise-linear interpolated noise). */
    @KlangScript.Method
    fun berlin(rate: IgnitorDslLike = 1.0): IgnitorDsl =
        IgnitorDsl.BerlinNoise(rate = rate.toIgnitorDsl())

    /** Creates a dust source (sparse random impulses). */
    @KlangScript.Method
    fun dust(density: IgnitorDslLike = 0.2): IgnitorDsl =
        IgnitorDsl.Dust(density = density.toIgnitorDsl())

    /** Creates a crackle source (noise bursts). */
    @KlangScript.Method
    fun crackle(density: IgnitorDslLike = 0.2): IgnitorDsl =
        IgnitorDsl.Crackle(density = density.toIgnitorDsl())

    // ── Super Oscillators ────────────────────────────────────────────────────

    /**
     * Creates a supersaw (multiple detuned sawtooth oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     */
    @KlangScript.Method
    fun supersaw(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
    ): IgnitorDsl =
        IgnitorDsl.SuperSaw(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
        )

    /**
     * Creates a supersine (multiple detuned sine oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     */
    @KlangScript.Method
    fun supersine(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
    ): IgnitorDsl =
        IgnitorDsl.SuperSine(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
        )

    /**
     * Creates a supersquare (multiple detuned square oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     */
    @KlangScript.Method
    fun supersquare(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
    ): IgnitorDsl =
        IgnitorDsl.SuperSquare(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
        )

    /**
     * Creates a supertri (multiple detuned triangle oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     */
    @KlangScript.Method
    fun supertri(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
    ): IgnitorDsl =
        IgnitorDsl.SuperTri(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
        )

    /**
     * Creates a superramp (multiple detuned ramp oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     */
    @KlangScript.Method
    fun superramp(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
    ): IgnitorDsl =
        IgnitorDsl.SuperRamp(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
        )

    // ── Physical Models ──────────────────────────────────────────────────────

    /**
     * Creates a Karplus-Strong plucked string model.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param decay loop decay per pass (default 0.996). Higher = longer sustain (0..1).
     * @param brightness initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright.
     * @param pickPosition relative pick position along the string (default 0.5). 0 = bridge, 1 = nut.
     * @param stiffness string stiffness (default 0.0). Higher = more inharmonic, bell-like.
     */
    @KlangScript.Method
    fun pluck(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        decay: IgnitorDslLike = 0.996,
        brightness: IgnitorDslLike = 0.5,
        pickPosition: IgnitorDslLike = 0.5,
        stiffness: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.Pluck(
            freq = freq.toIgnitorDsl(),
            decay = decay.toIgnitorDsl(),
            brightness = brightness.toIgnitorDsl(),
            pickPosition = pickPosition.toIgnitorDsl(),
            stiffness = stiffness.toIgnitorDsl(),
        )

    /**
     * Creates a unison Karplus-Strong plucked string model.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param decay loop decay per pass (default 0.996). Higher = longer sustain (0..1).
     * @param brightness initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright.
     * @param pickPosition relative pick position along the string (default 0.5). 0 = bridge, 1 = nut.
     * @param stiffness string stiffness (default 0.0). Higher = more inharmonic, bell-like.
     */
    @KlangScript.Method
    fun superpluck(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        decay: IgnitorDslLike = 0.996,
        brightness: IgnitorDslLike = 0.5,
        pickPosition: IgnitorDslLike = 0.5,
        stiffness: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperPluck(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            decay = decay.toIgnitorDsl(),
            brightness = brightness.toIgnitorDsl(),
            pickPosition = pickPosition.toIgnitorDsl(),
            stiffness = stiffness.toIgnitorDsl(),
        )

    // ── Parameter Slot ───────────────────────────────────────────────────────

    /**
     * Creates a named parameter slot with a default value.
     *
     * Param slots are the leaf nodes of the ignitor tree — they produce a constant signal
     * at [default] unless overridden by oscParam() at play time.
     *
     * @param name parameter name — used for oscParam() overrides and UI discovery
     * @param default constant value when no override is provided
     * @param description human-readable description for documentation
     */
    @KlangScript.Method
    fun param(name: String, default: Double, description: String = ""): IgnitorDsl =
        IgnitorDsl.Param(name, default, description)

    // ── Constant ────────────────────────────────────────────────────────────

    /**
     * Creates a fixed constant value that cannot be overridden by oscParams.
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
     * wraps to zero. Notes with no `:n` default to the first variant.
     *
     * Same selection mechanism used by sample banks (`bd:0`, `bd:1`, …). Nested
     * `variants(...)` all dispatch on the same `soundIndex`, so a single switching
     * axis can drive correlated changes throughout the tree.
     *
     * ```
     * let combined = Osc.variants(Osc.sine(), Osc.saw())
     * note("a b c:1 d:1").sound(combined)   // a/b → sine, c:1/d:1 → saw
     * ```
     *
     * @param children candidate ignitors, indexed from 0
     */
    @KlangScript.Method
    fun variants(vararg children: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Variants(children.toList())
}
