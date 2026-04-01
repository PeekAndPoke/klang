package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Integration tests for the Osc DSL in KlangScript.
 *
 * Validates that KlangScript code builds correct IgnitorDsl trees
 * through the full engine pipeline (parse → interpret → native interop).
 */
class StdLibOscTest : StringSpec({

    fun evalIgnitorDsl(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        val value = result.value
        value.shouldBeInstanceOf<IgnitorDsl>()
        return value
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Factory methods
    // ═════════════════════════════════════════════════════════════════════════════

    "Osc.sine() returns IgnitorDsl.Sine" {
        val dsl = evalIgnitorDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "Osc.sine(5) returns Sine with Constant freq" {
        val dsl = evalIgnitorDsl("Osc.sine(5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 5.0
    }

    "Osc.sine() with no args uses Constant(0.0) for freq (= voice frequency)" {
        val dsl = evalIgnitorDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        // Default 0.0 goes through toIgnitorDsl() → Constant(0.0), meaning "use voice freq"
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 0.0
    }

    "Osc.saw() returns Sawtooth" {
        evalIgnitorDsl("Osc.saw()").shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "Osc.square() returns Square" {
        evalIgnitorDsl("Osc.square()").shouldBeInstanceOf<IgnitorDsl.Square>()
    }

    "Osc.supersaw() returns SuperSaw" {
        evalIgnitorDsl("Osc.supersaw()").shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
    }

    "Osc.supersaw(10) returns SuperSaw with Constant freq" {
        val dsl = evalIgnitorDsl("Osc.supersaw(10)")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 10.0
    }

    "Osc.whitenoise() returns WhiteNoise" {
        evalIgnitorDsl("Osc.whitenoise()") shouldBe IgnitorDsl.WhiteNoise
    }

    "Osc.perlin() returns PerlinNoise" {
        evalIgnitorDsl("Osc.perlin()").shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
    }

    "Osc.perlin(3) returns PerlinNoise with Constant rate" {
        val dsl = evalIgnitorDsl("Osc.perlin(3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
        dsl.rate.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.rate as IgnitorDsl.Constant).value shouldBe 3.0
    }

    "Osc.dust(0.5) returns Dust with Constant density" {
        val dsl = evalIgnitorDsl("Osc.dust(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Dust>()
        dsl.density.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.density as IgnitorDsl.Constant).value shouldBe 0.5
    }

    "Osc.pluck() returns Pluck" {
        evalIgnitorDsl("Osc.pluck()").shouldBeInstanceOf<IgnitorDsl.Pluck>()
    }

    "Osc.silence() returns Silence" {
        evalIgnitorDsl("Osc.silence()") shouldBe IgnitorDsl.Silence
    }

    "Osc.constant(42) returns Constant" {
        val dsl = evalIgnitorDsl("Osc.constant(42)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Constant>()
        dsl.value shouldBe 42.0
    }

    "Osc.param creates named Param" {
        val dsl = evalIgnitorDsl("""Osc.param("cutoff", 1000, "Filter cutoff")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Param>()
        dsl.name shouldBe "cutoff"
        dsl.default shouldBe 1000.0
        dsl.description shouldBe "Filter cutoff"
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Chaining — filters, effects, envelope
    // ═════════════════════════════════════════════════════════════════════════════

    "lowpass chaining with default q" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.cutoffHz.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.cutoffHz as IgnitorDsl.Constant).value shouldBe 2000.0
    }

    "lowpass chaining with explicit q" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(2000, 2.0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.q.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.q as IgnitorDsl.Constant).value shouldBe 2.0
    }

    "lowpass with IgnitorDsl cutoff (audio-rate modulation)" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(Osc.perlin())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.cutoffHz.shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
    }

    "adsr chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().adsr(0.01, 0.1, 0.5, 0.3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "distort chaining produces Clip(Drive(...))" {
        val dsl = evalIgnitorDsl("Osc.saw().distort(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        val drive = dsl.inner
        drive.shouldBeInstanceOf<IgnitorDsl.Drive>()
        drive.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "detune chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().detune(7)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Detune>()
    }

    "tremolo chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().tremolo(5, 0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Tremolo>()
    }

    "vibrato chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().vibrato(5, 0.02)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Vibrato>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic
    // ═════════════════════════════════════════════════════════════════════════════

    "plus combines two ignitors" {
        val dsl = evalIgnitorDsl("Osc.sine().plus(Osc.saw())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "plus with number creates Constant" {
        val dsl = evalIgnitorDsl("Osc.sine().plus(1)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 1.0
    }

    "mul with number creates Constant" {
        val dsl = evalIgnitorDsl("Osc.sine().mul(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Mul>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 0.5
    }

    "div with number creates Constant" {
        val dsl = evalIgnitorDsl("Osc.sine().div(2)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Div>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 2.0
    }

    "minus creates Plus(self, Mul(other, Constant(-1)))" {
        val dsl = evalIgnitorDsl("Osc.sine().minus(Osc.saw())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Mul>()
        val mul = dsl.right as IgnitorDsl.Mul
        mul.left.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        mul.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (mul.right as IgnitorDsl.Constant).value shouldBe -1.0
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions
    // ═════════════════════════════════════════════════════════════════════════════

    "complex composition — supersaw with LFO-modulated lowpass and envelope" {
        val dsl = evalIgnitorDsl(
            """
            Osc.supersaw().lowpass(Osc.sine(5).plus(1).times(1000).plus(1000)).adsr(0.01, 0.3, 0.5, 0.5)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        val lowpass = dsl.inner
        lowpass.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        lowpass.inner.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
    }

    "variable assignment and reuse" {
        val dsl = evalIgnitorDsl(
            """
            let lfo = Osc.sine(5).plus(1).times(500).plus(500)
            Osc.saw().lowpass(lfo)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        // The LFO is a Plus(Times(Plus(Sine, Constant), Constant), Constant)
        dsl.cutoffHz.shouldBeInstanceOf<IgnitorDsl.Plus>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Osc.register
    // ═════════════════════════════════════════════════════════════════════════════

    "Osc.register returns registered name when registrar is set" {
        val registered = mutableListOf<Pair<String, IgnitorDsl>>()

        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        engine.attrs[KlangScriptOsc.REGISTRAR_KEY] = { name: String, dsl: IgnitorDsl ->
            registered.add(name to dsl)
            name
        }

        val result = engine.execute("""Osc.register("myPad", Osc.sine().lowpass(1000))""")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "myPad"

        registered.size shouldBe 1
        registered[0].first shouldBe "myPad"
        registered[0].second.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
    }

    "Osc.register throws when no registrar is set" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        try {
            engine.execute("""Osc.register("test", Osc.sine())""")
            error("Should have thrown")
        } catch (e: Exception) {
            e.message.shouldNotBeNull()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Analog drift
    // ═════════════════════════════════════════════════════════════════════════════

    "analog sets drift on oscillator" {
        val dsl = evalIgnitorDsl("Osc.sine().analog(0.3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.analog.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.analog as IgnitorDsl.Constant).value shouldBe 0.3
    }

    "analog is no-op on noise" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute("Osc.whitenoise().analog(0.5)")
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        result.value shouldBe IgnitorDsl.WhiteNoise
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch via KlangScript
    // ═════════════════════════════════════════════════════════════════════════════

    "drive chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().drive(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Drive>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "clip chaining" {
        val dsl = evalIgnitorDsl("""Osc.sine().clip("hard")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.shape shouldBe "hard"
    }

    "clip with default shape" {
        val dsl = evalIgnitorDsl("Osc.sine().clip()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        dsl.shape shouldBe "soft"
    }

    "bandpass chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().bandpass(1000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Bandpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "bandpass with explicit Q" {
        val dsl = evalIgnitorDsl("Osc.sine().bandpass(1000, 5.0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Bandpass>()
    }

    "notch chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().notch(1000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Notch>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "drive + clip chain" {
        val dsl = evalIgnitorDsl("""Osc.saw().drive(0.3).clip("fold")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Clip>()
        dsl.shape shouldBe "fold"
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
    }
})
