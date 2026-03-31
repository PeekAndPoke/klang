package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.ExciterDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Integration tests for the Osc DSL in KlangScript.
 *
 * Validates that KlangScript code builds correct ExciterDsl trees
 * through the full engine pipeline (parse → interpret → native interop).
 */
class StdLibOscTest : StringSpec({

    fun evalExciterDsl(code: String): ExciterDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        val value = result.value
        value.shouldBeInstanceOf<ExciterDsl>()
        return value
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Factory methods
    // ═════════════════════════════════════════════════════════════════════════════

    "Osc.sine() returns ExciterDsl.Sine" {
        val dsl = evalExciterDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<ExciterDsl.Sine>()
    }

    "Osc.sine(5) returns Sine with Constant freq" {
        val dsl = evalExciterDsl("Osc.sine(5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.freq.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.freq as ExciterDsl.Constant).value shouldBe 5.0
    }

    "Osc.sine() with no args uses Constant(0.0) for freq (= voice frequency)" {
        val dsl = evalExciterDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<ExciterDsl.Sine>()
        // Default 0.0 goes through toExciterDsl() → Constant(0.0), meaning "use voice freq"
        dsl.freq.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.freq as ExciterDsl.Constant).value shouldBe 0.0
    }

    "Osc.saw() returns Sawtooth" {
        evalExciterDsl("Osc.saw()").shouldBeInstanceOf<ExciterDsl.Sawtooth>()
    }

    "Osc.square() returns Square" {
        evalExciterDsl("Osc.square()").shouldBeInstanceOf<ExciterDsl.Square>()
    }

    "Osc.supersaw() returns SuperSaw" {
        evalExciterDsl("Osc.supersaw()").shouldBeInstanceOf<ExciterDsl.SuperSaw>()
    }

    "Osc.supersaw(10) returns SuperSaw with Constant freq" {
        val dsl = evalExciterDsl("Osc.supersaw(10)")
        dsl.shouldBeInstanceOf<ExciterDsl.SuperSaw>()
        dsl.freq.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.freq as ExciterDsl.Constant).value shouldBe 10.0
    }

    "Osc.whitenoise() returns WhiteNoise" {
        evalExciterDsl("Osc.whitenoise()") shouldBe ExciterDsl.WhiteNoise
    }

    "Osc.perlin() returns PerlinNoise" {
        evalExciterDsl("Osc.perlin()").shouldBeInstanceOf<ExciterDsl.PerlinNoise>()
    }

    "Osc.perlin(3) returns PerlinNoise with Constant rate" {
        val dsl = evalExciterDsl("Osc.perlin(3)")
        dsl.shouldBeInstanceOf<ExciterDsl.PerlinNoise>()
        dsl.rate.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.rate as ExciterDsl.Constant).value shouldBe 3.0
    }

    "Osc.dust(0.5) returns Dust with Constant density" {
        val dsl = evalExciterDsl("Osc.dust(0.5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Dust>()
        dsl.density.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.density as ExciterDsl.Constant).value shouldBe 0.5
    }

    "Osc.pluck() returns Pluck" {
        evalExciterDsl("Osc.pluck()").shouldBeInstanceOf<ExciterDsl.Pluck>()
    }

    "Osc.silence() returns Silence" {
        evalExciterDsl("Osc.silence()") shouldBe ExciterDsl.Silence
    }

    "Osc.constant(42) returns Constant" {
        val dsl = evalExciterDsl("Osc.constant(42)")
        dsl.shouldBeInstanceOf<ExciterDsl.Constant>()
        dsl.value shouldBe 42.0
    }

    "Osc.param creates named Param" {
        val dsl = evalExciterDsl("""Osc.param("cutoff", 1000, "Filter cutoff")""")
        dsl.shouldBeInstanceOf<ExciterDsl.Param>()
        dsl.name shouldBe "cutoff"
        dsl.default shouldBe 1000.0
        dsl.description shouldBe "Filter cutoff"
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Chaining — filters, effects, envelope
    // ═════════════════════════════════════════════════════════════════════════════

    "lowpass chaining with default q" {
        val dsl = evalExciterDsl("Osc.sine().lowpass(2000)")
        dsl.shouldBeInstanceOf<ExciterDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.cutoffHz.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.cutoffHz as ExciterDsl.Constant).value shouldBe 2000.0
    }

    "lowpass chaining with explicit q" {
        val dsl = evalExciterDsl("Osc.sine().lowpass(2000, 2.0)")
        dsl.shouldBeInstanceOf<ExciterDsl.Lowpass>()
        dsl.q.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.q as ExciterDsl.Constant).value shouldBe 2.0
    }

    "lowpass with ExciterDsl cutoff (audio-rate modulation)" {
        val dsl = evalExciterDsl("Osc.sine().lowpass(Osc.perlin())")
        dsl.shouldBeInstanceOf<ExciterDsl.Lowpass>()
        dsl.cutoffHz.shouldBeInstanceOf<ExciterDsl.PerlinNoise>()
    }

    "adsr chaining" {
        val dsl = evalExciterDsl("Osc.sine().adsr(0.01, 0.1, 0.5, 0.3)")
        dsl.shouldBeInstanceOf<ExciterDsl.Adsr>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
    }

    "distort chaining produces Clip(Drive(...))" {
        val dsl = evalExciterDsl("Osc.saw().distort(0.5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Clip>()
        val drive = dsl.inner
        drive.shouldBeInstanceOf<ExciterDsl.Drive>()
        drive.inner.shouldBeInstanceOf<ExciterDsl.Sawtooth>()
    }

    "detune chaining" {
        val dsl = evalExciterDsl("Osc.sine().detune(7)")
        dsl.shouldBeInstanceOf<ExciterDsl.Detune>()
    }

    "tremolo chaining" {
        val dsl = evalExciterDsl("Osc.sine().tremolo(5, 0.5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Tremolo>()
    }

    "vibrato chaining" {
        val dsl = evalExciterDsl("Osc.sine().vibrato(5, 0.02)")
        dsl.shouldBeInstanceOf<ExciterDsl.Vibrato>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic
    // ═════════════════════════════════════════════════════════════════════════════

    "plus combines two exciters" {
        val dsl = evalExciterDsl("Osc.sine().plus(Osc.saw())")
        dsl.shouldBeInstanceOf<ExciterDsl.Plus>()
        dsl.left.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.right.shouldBeInstanceOf<ExciterDsl.Sawtooth>()
    }

    "plus with number creates Constant" {
        val dsl = evalExciterDsl("Osc.sine().plus(1)")
        dsl.shouldBeInstanceOf<ExciterDsl.Plus>()
        dsl.right.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.right as ExciterDsl.Constant).value shouldBe 1.0
    }

    "mul with number creates Constant" {
        val dsl = evalExciterDsl("Osc.sine().mul(0.5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Mul>()
        dsl.right.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.right as ExciterDsl.Constant).value shouldBe 0.5
    }

    "div with number creates Constant" {
        val dsl = evalExciterDsl("Osc.sine().div(2)")
        dsl.shouldBeInstanceOf<ExciterDsl.Div>()
        dsl.right.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.right as ExciterDsl.Constant).value shouldBe 2.0
    }

    "minus creates Plus(self, Mul(other, Constant(-1)))" {
        val dsl = evalExciterDsl("Osc.sine().minus(Osc.saw())")
        dsl.shouldBeInstanceOf<ExciterDsl.Plus>()
        dsl.left.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.right.shouldBeInstanceOf<ExciterDsl.Mul>()
        val mul = dsl.right as ExciterDsl.Mul
        mul.left.shouldBeInstanceOf<ExciterDsl.Sawtooth>()
        mul.right.shouldBeInstanceOf<ExciterDsl.Constant>()
        (mul.right as ExciterDsl.Constant).value shouldBe -1.0
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions
    // ═════════════════════════════════════════════════════════════════════════════

    "complex composition — supersaw with LFO-modulated lowpass and envelope" {
        val dsl = evalExciterDsl(
            """
            Osc.supersaw().lowpass(Osc.sine(5).plus(1).times(1000).plus(1000)).adsr(0.01, 0.3, 0.5, 0.5)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<ExciterDsl.Adsr>()
        val lowpass = dsl.inner
        lowpass.shouldBeInstanceOf<ExciterDsl.Lowpass>()
        lowpass.inner.shouldBeInstanceOf<ExciterDsl.SuperSaw>()
    }

    "variable assignment and reuse" {
        val dsl = evalExciterDsl(
            """
            let lfo = Osc.sine(5).plus(1).times(500).plus(500)
            Osc.saw().lowpass(lfo)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<ExciterDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sawtooth>()
        // The LFO is a Plus(Times(Plus(Sine, Constant), Constant), Constant)
        dsl.cutoffHz.shouldBeInstanceOf<ExciterDsl.Plus>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Osc.register
    // ═════════════════════════════════════════════════════════════════════════════

    "Osc.register returns registered name when registrar is set" {
        val registered = mutableListOf<Pair<String, ExciterDsl>>()

        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        engine.attrs[KlangScriptOsc.REGISTRAR_KEY] = { name: String, dsl: ExciterDsl ->
            registered.add(name to dsl)
            name
        }

        val result = engine.execute("""Osc.register("myPad", Osc.sine().lowpass(1000))""")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "myPad"

        registered.size shouldBe 1
        registered[0].first shouldBe "myPad"
        registered[0].second.shouldBeInstanceOf<ExciterDsl.Lowpass>()
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
        val dsl = evalExciterDsl("Osc.sine().analog(0.3)")
        dsl.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.analog.shouldBeInstanceOf<ExciterDsl.Constant>()
        (dsl.analog as ExciterDsl.Constant).value shouldBe 0.3
    }

    "analog is no-op on noise" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute("Osc.whitenoise().analog(0.5)")
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        result.value shouldBe ExciterDsl.WhiteNoise
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch via KlangScript
    // ═════════════════════════════════════════════════════════════════════════════

    "drive chaining" {
        val dsl = evalExciterDsl("Osc.sine().drive(0.5)")
        dsl.shouldBeInstanceOf<ExciterDsl.Drive>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
    }

    "clip chaining" {
        val dsl = evalExciterDsl("""Osc.sine().clip("hard")""")
        dsl.shouldBeInstanceOf<ExciterDsl.Clip>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
        dsl.shape shouldBe "hard"
    }

    "clip with default shape" {
        val dsl = evalExciterDsl("Osc.sine().clip()")
        dsl.shouldBeInstanceOf<ExciterDsl.Clip>()
        dsl.shape shouldBe "soft"
    }

    "bandpass chaining" {
        val dsl = evalExciterDsl("Osc.sine().bandpass(1000)")
        dsl.shouldBeInstanceOf<ExciterDsl.Bandpass>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
    }

    "bandpass with explicit Q" {
        val dsl = evalExciterDsl("Osc.sine().bandpass(1000, 5.0)")
        dsl.shouldBeInstanceOf<ExciterDsl.Bandpass>()
    }

    "notch chaining" {
        val dsl = evalExciterDsl("Osc.sine().notch(1000)")
        dsl.shouldBeInstanceOf<ExciterDsl.Notch>()
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Sine>()
    }

    "drive + clip chain" {
        val dsl = evalExciterDsl("""Osc.saw().drive(0.3).clip("fold")""")
        dsl.shouldBeInstanceOf<ExciterDsl.Clip>()
        dsl.shape shouldBe "fold"
        dsl.inner.shouldBeInstanceOf<ExciterDsl.Drive>()
    }
})
