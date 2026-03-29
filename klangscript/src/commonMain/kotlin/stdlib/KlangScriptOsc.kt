package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.ExciterDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.ultra.common.TypedKey

/** Callback type for registering an ExciterDsl with the audio backend. Returns the registered name. */
typealias ExciterRegistrar = (name: String, dsl: ExciterDsl) -> String

/**
 * Osc object for KlangScript — builds ExciterDsl signal graphs.
 *
 * Provides factory methods for all oscillator primitives, noise sources, and super oscillators.
 * Returns [ExciterDsl] instances that can be composed via extension methods (lowpass, adsr, mul, etc.)
 * and registered as named sounds via [register].
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
    /** TypedKey for the exciter registrar callback. Set on engine.attrs by the app when the player is available. */
    val REGISTRAR_KEY = TypedKey<ExciterRegistrar>("ExciterRegistrar")

    override fun toString(): String = "[Osc object]"

    // ── Oscillator Primitives ────────────────────────────────────────────────

    /** Creates a sine wave oscillator. freq=0 uses voice frequency, or pass Hz for LFO. */
    @KlangScript.Method
    fun sine(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Sine(freq = freq.toExciterDsl())

    /** Creates a sawtooth wave oscillator (PolyBLEP anti-aliased). */
    @KlangScript.Method
    fun saw(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Sawtooth(freq = freq.toExciterDsl())

    /** Creates a square wave oscillator (PolyBLEP anti-aliased). */
    @KlangScript.Method
    fun square(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Square(freq = freq.toExciterDsl())

    /** Creates a triangle wave oscillator. */
    @KlangScript.Method
    fun triangle(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Triangle(freq = freq.toExciterDsl())

    /** Creates a ramp (reverse sawtooth) wave oscillator. */
    @KlangScript.Method
    fun ramp(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Ramp(freq = freq.toExciterDsl())

    /** Creates a naive sawtooth without anti-aliasing (brighter/harsher). */
    @KlangScript.Method
    fun zawtooth(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Zawtooth(freq = freq.toExciterDsl())

    /** Creates an impulse (click) oscillator. */
    @KlangScript.Method
    fun impulse(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Impulse(freq = freq.toExciterDsl())

    /** Creates a pulse wave with variable duty cycle. */
    @KlangScript.Method
    fun pulze(freq: ExciterDslLike = 0.0): ExciterDsl =
        ExciterDsl.Pulze(freq = freq.toExciterDsl())

    /** Creates a silent exciter (zero output). */
    @KlangScript.Method
    fun silence(): ExciterDsl = ExciterDsl.Silence

    // ── Noise Sources ────────────────────────────────────────────────────────

    /** Creates a white noise source (flat spectrum). */
    @KlangScript.Method
    fun whitenoise(): ExciterDsl = ExciterDsl.WhiteNoise

    /** Creates a brown noise source (1/f^2 spectrum, deeper). */
    @KlangScript.Method
    fun brownnoise(): ExciterDsl = ExciterDsl.BrownNoise

    /** Creates a pink noise source (1/f spectrum). */
    @KlangScript.Method
    fun pinknoise(): ExciterDsl = ExciterDsl.PinkNoise

    /** Creates a Perlin noise source (smooth organic noise). */
    @KlangScript.Method
    fun perlin(): ExciterDsl = ExciterDsl.PerlinNoise()

    /** Creates a Berlin noise source (piecewise-linear interpolated noise). */
    @KlangScript.Method
    fun berlin(): ExciterDsl = ExciterDsl.BerlinNoise()

    /** Creates a dust source (sparse random impulses). */
    @KlangScript.Method
    fun dust(): ExciterDsl = ExciterDsl.Dust()

    /** Creates a crackle source (noise bursts). */
    @KlangScript.Method
    fun crackle(): ExciterDsl = ExciterDsl.Crackle()

    // ── Super Oscillators ────────────────────────────────────────────────────

    /** Creates a supersaw (multiple detuned sawtooth oscillators). */
    @KlangScript.Method
    fun supersaw(): ExciterDsl = ExciterDsl.SuperSaw()

    /** Creates a supersine (multiple detuned sine oscillators). */
    @KlangScript.Method
    fun supersine(): ExciterDsl = ExciterDsl.SuperSine()

    /** Creates a supersquare (multiple detuned square oscillators). */
    @KlangScript.Method
    fun supersquare(): ExciterDsl = ExciterDsl.SuperSquare()

    /** Creates a supertri (multiple detuned triangle oscillators). */
    @KlangScript.Method
    fun supertri(): ExciterDsl = ExciterDsl.SuperTri()

    /** Creates a superramp (multiple detuned ramp oscillators). */
    @KlangScript.Method
    fun superramp(): ExciterDsl = ExciterDsl.SuperRamp()

    // ── Physical Models ──────────────────────────────────────────────────────

    /** Creates a Karplus-Strong plucked string model. */
    @KlangScript.Method
    fun pluck(): ExciterDsl = ExciterDsl.Pluck()

    /** Creates a unison Karplus-Strong plucked string model. */
    @KlangScript.Method
    fun superpluck(): ExciterDsl = ExciterDsl.SuperPluck()

    // ── Parameter Slot ───────────────────────────────────────────────────────

    /**
     * Creates a named parameter slot with a default value.
     *
     * Param slots are the leaf nodes of the exciter tree — they produce a constant signal
     * at [default] unless overridden by oscParam() at play time.
     *
     * @param name parameter name — used for oscParam() overrides and UI discovery
     * @param default constant value when no override is provided
     * @param description human-readable description for documentation
     */
    @KlangScript.Method
    fun param(name: String, default: Double, description: String = ""): ExciterDsl =
        ExciterDsl.Param(name, default, description)

    // ── Constant ────────────────────────────────────────────────────────────

    /**
     * Creates a fixed constant value that cannot be overridden by oscParams.
     *
     * Use when you want an explicit, locked value in the signal graph.
     *
     * @param value the constant value
     */
    @KlangScript.Method
    fun constant(value: Double): ExciterDsl =
        ExciterDsl.Constant(value)
}
