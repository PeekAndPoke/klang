package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.ultra.common.TypedKey

/** Callback type for registering an IgnitorDsl with the audio backend. Returns the registered name. */
typealias IgnitorRegistrar = (name: String, dsl: IgnitorDsl) -> String

/**
 * Osc object for KlangScript — builds IgnitorDsl signal graphs.
 *
 * Provides factory methods for all oscillator primitives, noise sources, and super oscillators.
 * Returns [IgnitorDsl] instances that can be composed via extension methods (lowpass, adsr, mul, etc.)
 * and registered as named sounds via Osc.register.
 *
 * Usage in KlangScript:
 * ```
 * let pad = Osc.register("pad", Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5))
 * note("c3 e3 g3").sound(pad)
 * ```
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Osc")
object KlangScriptOsc {
    /** TypedKey for the ignitor registrar callback. Set on engine.attrs by the app when the player is available. */
    val REGISTRAR_KEY = TypedKey<IgnitorRegistrar>("IgnitorRegistrar")

    override fun toString(): String = "[Osc object]"

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

    /** Creates a white noise source (flat spectrum). */
    @KlangScript.Method
    fun whitenoise(): IgnitorDsl = IgnitorDsl.WhiteNoise

    /** Creates a brown noise source (1/f^2 spectrum, deeper). */
    @KlangScript.Method
    fun brownnoise(): IgnitorDsl = IgnitorDsl.BrownNoise

    /** Creates a pink noise source (1/f spectrum). */
    @KlangScript.Method
    fun pinknoise(): IgnitorDsl = IgnitorDsl.PinkNoise

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
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun supersaw(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperSaw(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
        )

    /**
     * Creates a supersine (multiple detuned sine oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun supersine(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperSine(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
        )

    /**
     * Creates a supersquare (multiple detuned square oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun supersquare(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperSquare(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
        )

    /**
     * Creates a supertri (multiple detuned triangle oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun supertri(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperTri(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
        )

    /**
     * Creates a superramp (multiple detuned ramp oscillators).
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun superramp(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperRamp(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
        )

    // ── Physical Models ──────────────────────────────────────────────────────

    /**
     * Creates a Karplus-Strong plucked string model.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     */
    @KlangScript.Method
    fun pluck(freq: IgnitorDslLike = IgnitorDsl.Freq): IgnitorDsl =
        IgnitorDsl.Pluck(freq = freq.toIgnitorDsl())

    /**
     * Creates a unison Karplus-Strong plucked string model.
     * @param freq frequency — omit for voice note frequency, or pass Hz for fixed frequency.
     * @param voices number of detuned voices (default 8).
     * @param freqSpread frequency spread between voices (default 0.2).
     * @param analog random per-voice pitch drift amount (default 0.0).
     */
    @KlangScript.Method
    fun superpluck(
        freq: IgnitorDslLike = IgnitorDsl.Freq,
        voices: IgnitorDslLike = 8.0,
        freqSpread: IgnitorDslLike = 0.2,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl =
        IgnitorDsl.SuperPluck(
            freq = freq.toIgnitorDsl(),
            voices = voices.toIgnitorDsl(),
            freqSpread = freqSpread.toIgnitorDsl(),
            analog = analog.toIgnitorDsl(),
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
}
